package com.ai.assistance.operit.services.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.floating.ToolPkgFloatingWindowController
import com.ai.assistance.operit.core.tools.packTool.floating.ToolPkgFloatingWindowSurfaceFactory
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 插件悬浮窗的生产装配：懒建控制器（Android 依赖注入）、能力查询、权限跳转、
 * 审计调度。JS 桥接层（JsEngine）经 [command] 进入。
 *
 * 线程纪律（防死锁，控制器锁内会阻塞等待主线程与 JS 引擎执行器）：
 * - 主线程绝不等待控制器锁：拖拽几何回报投递到专用单线程执行器
 * - 审计跑在专用 HandlerThread，不占主线程
 * - JS 桥接在工作线程里调用 [command]（照 invokeToolPkgIpcAsync 模式）
 */
object ToolPkgFloatingWindowRuntime {
    private const val TAG = "ToolPkgFloatRuntime"
    private const val AUDIT_INTERVAL_MS = 5_000L

    @Volatile
    private var controllerInstance: ToolPkgFloatingWindowController? = null

    private val geometryExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "toolpkg-float-geometry")
    }
    private val auditThread = HandlerThread("toolpkg-float-audit").apply { start() }
    private val auditHandler = Handler(auditThread.looper)
    @Volatile
    private var auditScheduled = false

    /**
     * 执行一条悬浮窗命令并返回 JSON 文本。
     * 结果序列化：JSONObject/JSONArray 原样、Boolean 输出 true/false、null 输出 "null"。
     * 抛出的异常由调用方（JsEngine 桥接）转成 Promise reject。
     */
    fun command(engineContext: Context, owner: String, operation: String, argsJson: String): String {
        val appContext = engineContext.applicationContext
        val controller = obtainController(appContext)
        val args = if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        val result = controller.command(owner, operation, args)
        scheduleAudit(controller)
        return serializeResult(result)
    }

    private fun obtainController(appContext: Context): ToolPkgFloatingWindowController {
        controllerInstance?.let { return it }
        synchronized(this) {
            controllerInstance?.let { return it }
            val factory = ToolPkgFloatingWindowSurfaceFactory { owner, id, route, options, state ->
                ToolPkgFloatingWindowSession.open(
                    context = appContext,
                    owner = owner,
                    id = id,
                    routeId = route,
                    options = options,
                    state = state,
                    onGeometryChanged = { x, y ->
                        geometryExecutor.execute {
                            controllerInstance?.reportGeometry(owner, id, x, y)
                        }
                    },
                )
            }
            val controller = ToolPkgFloatingWindowController(
                surfaceFactory = factory,
                capabilitiesProvider = { capabilities(appContext) },
                requestPermissionAction = { requestOverlayPermission(appContext) },
                enabledOwnersProvider = { enabledOwners() },
            )
            controllerInstance = controller
            return controller
        }
    }

    /** 对齐 Operit2 capabilities 契约；无副作用、不弹权限提示。 */
    private fun capabilities(appContext: Context): JSONObject {
        val granted = Settings.canDrawOverlays(appContext)
        return JSONObject()
            .put("scope", if (granted) "system" else "unsupported")
            .put("systemOverlay", granted)
            .put("applicationOverlay", false)
            .put("supported", granted)
            .put("permissionGranted", granted)
            .put("positioning", true)
            .put("alwaysOnTop", true)
            .put("clickThrough", true)
            .put("focusable", true)
    }

    /** 已授权直接 true；未授权跳系统设置页并返回是否已发起（插件随后复查 capabilities）。 */
    private fun requestOverlayPermission(appContext: Context): Boolean {
        if (Settings.canDrawOverlays(appContext)) {
            return true
        }
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${appContext.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        } catch (error: Exception) {
            AppLogger.e(TAG, "Error requesting overlay permission", error)
            false
        }
    }

    private fun enabledOwners(): Set<String> {
        val packageManager = PackageManager.peekInstance() ?: return emptySet()
        return packageManager.getEnabledPackageNames().toSet()
    }

    /**
     * 有窗口存在时启动周期审计（对齐 Operit2 的 5 秒 reconcile），
     * 最后一个窗口关闭后自然停止。暂不订阅包目录变更事件，最坏延迟一个周期。
     */
    private fun scheduleAudit(controller: ToolPkgFloatingWindowController) {
        if (auditScheduled || !controller.hasWindows()) {
            return
        }
        auditScheduled = true
        auditHandler.post(object : Runnable {
            override fun run() {
                val current = controllerInstance
                if (current == null) {
                    auditScheduled = false
                    return
                }
                try {
                    current.audit()
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Floating window audit failed", error)
                }
                if (current.hasWindows()) {
                    auditHandler.postDelayed(this, AUDIT_INTERVAL_MS)
                } else {
                    auditScheduled = false
                }
            }
        })
    }

    private fun serializeResult(result: Any?): String = when (result) {
        null -> "null"
        is Boolean -> result.toString()
        is JSONObject -> result.toString()
        is JSONArray -> result.toString()
        else -> result.toString()
    }
}
