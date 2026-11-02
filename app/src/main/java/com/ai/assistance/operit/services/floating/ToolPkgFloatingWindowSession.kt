package com.ai.assistance.operit.services.floating

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslRenderResult
import com.ai.assistance.operit.core.tools.packTool.floating.ToolPkgFloatingWindowOptions
import com.ai.assistance.operit.core.tools.packTool.floating.ToolPkgFloatingWindowSurface
import com.ai.assistance.operit.ui.common.composedsl.RenderToolPkgComposeDslNode
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONObject

/**
 * 插件悬浮窗的渲染会话：解析插件注册的 UiRoute，持有专用 JS 引擎租约，
 * 首次渲染建立 compose_dsl 会话，后续 state 更新走 rerenderComposeDslTree，
 * 动作分发走 dispatchComposeDslActionAsync。
 *
 * 渲染链对齐 ToolPkgDesktopWidgetDslRenderer（脚本/state 经 runtimeOptions 注入）
 * 与 XmlRenderPluginRegistry（引擎租约 + 节点渲染复用）。
 */
class ToolPkgFloatingWindowSession private constructor(
    private val context: android.content.Context,
    private val owner: String,
    override val id: String,
    private val route: PackageManager.ToolPkgUiRoute,
    initialOptions: ToolPkgFloatingWindowOptions,
    private val initialState: JSONObject,
    onGeometryChanged: (x: Int, y: Int) -> Unit,
) : ToolPkgFloatingWindowSurface {

    private val packageManager =
        PackageManager.getInstance(
            context,
            com.ai.assistance.operit.core.tools.AIToolHandler.getInstance(context)
        )

    val executionContextKey = "toolpkg_floating:$owner:$id"

    private val jsEngine: JsEngine = packageManager.acquireToolPkgExecutionEngine(
        contextKey = executionContextKey,
        containerPackageName = route.containerPackageName
    )

    private val screenPath: String =
        packageManager.getToolPkgComposeDslScreenPath(
            containerPackageName = route.containerPackageName,
            uiModuleId = route.uiModuleId
        ).orEmpty()

    private var renderResult by mutableStateOf<ToolPkgComposeDslRenderResult?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var hasDispatchedInitialOnLoad by mutableStateOf(false)

    private val host: ToolPkgFloatingWindowHost = ToolPkgFloatingWindowHost(
        context = context,
        id = id,
        initialOptions = initialOptions,
        content = ::Content,
        onGeometryChanged = onGeometryChanged,
    )

    init {
        try {
            val raw = jsEngine.executeComposeDslScript(
                script = packageManager.getToolPkgComposeDslScript(
                    containerPackageName = route.containerPackageName,
                    uiModuleId = route.uiModuleId
                ) ?: throw IllegalStateException(
                    "Floating window render script not found: ${route.uiModuleId}"
                ),
                runtimeOptions = buildRuntimeOptions(initialState)
            )
            val parsed = ToolPkgComposeDslParser.parseRenderResult(raw)
            if (parsed == null) {
                throw IllegalStateException(
                    "Floating window route failed to render: " +
                        raw?.toString()?.trim().orEmpty().ifBlank { "invalid render result" }
                )
            }
            renderResult = parsed
        } catch (error: Exception) {
            // 首渲染失败：窗口在 host 构造时已 addView，必须一并关闭再抛错，
            // 否则会留下一个空窗口泄漏在屏幕上。
            host.close()
            releaseEngine()
            if (error is IllegalStateException || error is IllegalArgumentException) throw error
            throw IllegalStateException("Floating window route failed to render: ${error.message}", error)
        }
    }

    companion object {
        private const val TAG = "ToolPkgFloatSession"

        /**
         * 打开一个渲染会话：route 必须是 owner 已注册的 UiRoute（routeId 大小写不敏感，
         * 对齐桌面小组件匹配规则）；任何一步失败都会释放引擎租约并抛错。
         */
        fun open(
            context: android.content.Context,
            owner: String,
            id: String,
            routeId: String,
            options: ToolPkgFloatingWindowOptions,
            state: JSONObject,
            onGeometryChanged: (x: Int, y: Int) -> Unit,
        ): ToolPkgFloatingWindowSession {
            val packageManager = PackageManager.getInstance(
                context,
                com.ai.assistance.operit.core.tools.AIToolHandler.getInstance(context)
            )
            val route = packageManager
                .getToolPkgUiRoutes(resolveContext = context)
                .firstOrNull { candidate ->
                    candidate.containerPackageName.equals(owner, ignoreCase = true) &&
                        candidate.routeId.equals(routeId, ignoreCase = true)
                }
                ?: throw IllegalArgumentException(
                    "Floating window route not registered by this plugin: $routeId"
                )
            return ToolPkgFloatingWindowSession(
                context = context,
                owner = owner,
                id = id,
                route = route,
                initialOptions = options,
                initialState = state,
                onGeometryChanged = onGeometryChanged,
            )
        }
    }

    @Composable
    fun Content(revision: Int, windowOptions: ToolPkgFloatingWindowOptions) {
        val rootNode = renderResult?.tree
        val rootOnLoadActionId = remember(rootNode) {
            rootNode?.let { ToolPkgComposeDslParser.extractActionId(it.props["onLoad"]) }
        }
        LaunchedEffect(rootNode, rootOnLoadActionId, hasDispatchedInitialOnLoad) {
            if (rootNode == null || rootOnLoadActionId.isNullOrBlank() || hasDispatchedInitialOnLoad) {
                return@LaunchedEffect
            }
            hasDispatchedInitialOnLoad = true
            dispatchAction(rootOnLoadActionId, null)
        }
        when {
            errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            rootNode == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            else -> RenderToolPkgComposeDslNode(
                node = rootNode,
                modifier = Modifier.fillMaxSize(),
                onAction = { actionId, payload -> dispatchAction(actionId, payload) },
                onTextInputAction = { actionId, text -> dispatchAction(actionId, text) },
            )
        }
    }

    override fun applyOptions(options: ToolPkgFloatingWindowOptions) = host.applyOptions(options)

    override fun show() = host.show()

    override fun hide() = host.hide()

    override fun close() {
        host.close()
        releaseEngine()
    }

    /** 以浅合并后的完整 state 重渲染（renderResult 变更自动触发重组）。 */
    override fun updateState(state: JSONObject) {
        val raw = try {
            jsEngine.rerenderComposeDslTree(
                runtimeOptions = buildRuntimeOptions(state)
            )
        } catch (error: Exception) {
            AppLogger.e(TAG, "Floating window $id rerender failed", error)
            errorMessage = "compose_dsl rerender error: ${error.message}"
            throw IllegalStateException(
                "Floating window $id state update failed: ${error.message}", error
            )
        }
        val parsed = ToolPkgComposeDslParser.parseRenderResult(raw)
        if (parsed == null) {
            errorMessage = "compose_dsl rerender error: invalid render result"
            throw IllegalStateException(
                "Floating window state update returned an invalid render result"
            )
        }
        errorMessage = null
        renderResult = parsed
    }

    override fun isAlive(): Boolean = host.isAlive()

    private fun dispatchAction(actionId: String, payload: Any?) {
        val normalizedActionId = actionId.trim()
        if (normalizedActionId.isBlank()) {
            return
        }
        jsEngine.dispatchComposeDslActionAsync(
            actionId = normalizedActionId,
            payload = payload,
            runtimeOptions = buildRuntimeOptions(currentState()),
            onIntermediateResult = { intermediate ->
                ToolPkgComposeDslParser.parseRenderResult(intermediate)?.let { parsed ->
                    renderResult = parsed
                    errorMessage = null
                }
            },
            onFinalResult = { final ->
                ToolPkgComposeDslParser.parseRenderResult(final)?.let { parsed ->
                    renderResult = parsed
                    errorMessage = null
                }
            },
            onComplete = {},
            onError = { error ->
                errorMessage = "compose_dsl runtime error: $error"
                AppLogger.e(TAG, "Floating window $id action failed: $error")
            },
        )
    }

    private fun currentState(): JSONObject {
        return renderResult?.state?.let(::mapToJsonObject) ?: JSONObject()
    }

    private fun buildRuntimeOptions(state: JSONObject): Map<String, Any?> {
        return mapOf(
            "packageName" to route.containerPackageName,
            "containerPackageName" to route.containerPackageName,
            "toolPkgId" to route.toolPkgId,
            "__operit_ui_package_name" to route.containerPackageName,
            "__operit_ui_toolpkg_id" to route.toolPkgId,
            "uiModuleId" to route.uiModuleId,
            "__operit_ui_module_id" to route.uiModuleId,
            "__operit_toolpkg_runtime_kind" to "ui",
            "executionContextKey" to executionContextKey,
            "__operit_execution_context_key" to executionContextKey,
            "__operit_compose_execution_context_key" to executionContextKey,
            "__operit_script_screen" to screenPath,
            "moduleSpec" to route.moduleSpec,
            "state" to jsonObjectToMap(state),
            "memo" to emptyMap<String, Any?>(),
        )
    }

    private fun releaseEngine() {
        try {
            packageManager.releaseToolPkgExecutionEngine(executionContextKey, jsEngine)
        } catch (error: Exception) {
            AppLogger.e(TAG, "Error releasing floating window engine $executionContextKey", error)
        }
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        for (key in json.keys()) {
            map[key] = json.opt(key)
        }
        return map
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, value ?: JSONObject.NULL)
        }
        return json
    }
}
