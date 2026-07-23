package com.ai.assistance.operit.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AccessibilityProviderInstaller
import com.ai.assistance.operit.provider.IAccessibilityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import kotlin.coroutines.resume
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * UI层次结构管理器
 * 负责与独立的无障碍服务提供者App进行通信，获取UI层次结构。
 */
object UIHierarchyManager {
    private const val TAG = "UIHierarchyManager"
    private const val BIND_SERVICE_TIMEOUT_MS = 3000L // 3秒超时

    // 新的无障碍服务提供者应用的包名
    private const val PROVIDER_PACKAGE_NAME = "com.ai.assistance.operit.provider"
    // 无障碍服务提供者APK的文件名
    private const val PROVIDER_APK_NAME = "accessibility.apk"
    // 用于绑定的自定义Action，必须与服务提供者应用中的声明一致
    private const val PROVIDER_ACTION = "com.ai.assistance.operit.provider.IAccessibilityProvider"
    // TODO: 如果你不在Google Play上发布，可以将其更改为直接下载的URL
    private const val PROVIDER_MARKET_URL = "market://details?id=$PROVIDER_PACKAGE_NAME"

    @Volatile
    private var accessibilityProvider: IAccessibilityProvider? = null

    private val _isBound = MutableStateFlow(false)
    val isBound = _isBound.asStateFlow()

    private val bindingMutex = Mutex()

    @Volatile
    private var connectionContinuation: ((Boolean) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLogger.d(TAG, "无障碍服务提供者已连接")
            accessibilityProvider = IAccessibilityProvider.Stub.asInterface(service)
            _isBound.value = true
            connectionContinuation?.invoke(true)
            connectionContinuation = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLogger.d(TAG, "无障碍服务提供者已断开")
            accessibilityProvider = null
            _isBound.value = false
            connectionContinuation?.invoke(false)
            connectionContinuation = null
        }
    }

    /**
     * 从应用内assets目录中提取无障碍服务提供者APK文件。
     * @param context Context
     * @return 提取出的APK文件，如果失败则返回null。
     */
    private fun extractProviderApkFromAssets(context: Context): File? {
        return try {
            val apkFile = File(context.cacheDir, PROVIDER_APK_NAME)
            // 如果文件已存在且大小匹配，可以跳过提取，但为了简单起见，这里总是覆盖
            context.assets.open(PROVIDER_APK_NAME).use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            AppLogger.d(TAG, "无障碍服务APK已提取到: ${apkFile.absolutePath}")
            apkFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "从assets提取无障碍服务APK失败", e)
            null
        }
    }

    /**
     * 启动安装流程来安装提供者应用
     */
    fun launchProviderInstall(context: Context) {
        hierarchyScope.launch(Dispatchers.IO) {
            val apkFile = extractProviderApkFromAssets(context)
            if (apkFile == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_apk_extract_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val apkUri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(installIntent)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "启动安装界面失败", e)
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_operation_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 检查无障碍服务提供者是否需要更新
     */
    fun isUpdateNeeded(context: Context): Boolean {
        return AccessibilityProviderInstaller.isUpdateNeeded(context)
    }

    /**
     * 确保服务已绑定，如果未绑定则尝试自动重新绑定。
     * @return a boolean indicating if the service is ready.
     */
    private suspend fun ensureBound(context: Context): Boolean {
        if (!_isBound.value || accessibilityProvider == null) {
            AppLogger.w(TAG, "服务未绑定或提供者为null，尝试自动重新绑定...")
            val bound = bindToService(context)
            if (!bound) {
                AppLogger.e(TAG, "自动重新绑定失败")
                return false
            }
        }
        // A final check to protect against race conditions where the service disconnects
        // right after the check or during the binding process.
        return _isBound.value && accessibilityProvider != null
    }

    /**
     * 检查无障碍服务提供者应用是否已安装
     */
    fun isProviderAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PROVIDER_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 绑定到外部无障碍服务。
     * 这是一个挂起函数，它会等待服务连接成功或失败。
     * @return a boolean indicating if the binding was successful.
     */
    suspend fun bindToService(context: Context): Boolean {
        return bindingMutex.withLock {
            AppLogger.d(TAG, "bindToService invoked. Thread: ${Thread.currentThread().name}, Context: ${context.javaClass.name}")

            // 只有在已完全绑定（bound且provider不为空）或者应用未安装的情况下才直接返回
            // 如果 _isBound 为 true 但 provider 为 null，则认为是状态不一致，需要重新绑定
            if ((_isBound.value && accessibilityProvider != null) || !isProviderAppInstalled(context)) {
                if (!_isBound.value) AppLogger.w(TAG, "无法绑定：服务已绑定或提供者应用未安装")
                return@withLock _isBound.value
            }

            val implicitIntent = Intent(PROVIDER_ACTION).setPackage(PROVIDER_PACKAGE_NAME)
            val resolveInfo: ResolveInfo? = context.packageManager.resolveService(implicitIntent, PackageManager.MATCH_ALL)

            if (resolveInfo == null) {
                AppLogger.e(TAG, "无法解析服务: $PROVIDER_ACTION. 请确认提供者应用已正确安装。")
                return@withLock false
            }

            AppLogger.d(TAG, "服务解析成功: ${resolveInfo.serviceInfo.packageName}/${resolveInfo.serviceInfo.name}")

            val explicitIntent = Intent(PROVIDER_ACTION).apply {
                component = ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }

            val result = withTimeoutOrNull(BIND_SERVICE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    connectionContinuation = { success ->
                        AppLogger.d(TAG, "connectionContinuation called with success=$success")
                        if (continuation.isActive) {
                            continuation.resume(success)
                        }
                    }
                    try {
                        AppLogger.d(TAG, "尝试使用 ApplicationContext 绑定...")
                        var bound = context.applicationContext.bindService(explicitIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                        AppLogger.d(TAG, "ApplicationContext 绑定结果: $bound")

                        if (!bound) {
                            AppLogger.w(TAG, "ApplicationContext绑定失败，尝试使用原始Context (${context.javaClass.simpleName}) ...")
                            bound = context.bindService(explicitIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                            AppLogger.d(TAG, "原始 Context 绑定结果: $bound")
                        }

                        if (!bound) {
                            AppLogger.e(TAG, "bindService返回false，绑定失败。可能是权限问题或后台启动限制。")
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                            connectionContinuation = null
                        }
                    } catch (e: SecurityException) {
                        AppLogger.e(TAG, "绑定服务时出现安全异常", e)
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                        connectionContinuation = null
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "绑定服务时出现未知异常", e)
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                        connectionContinuation = null
                    }
                }
            }

            if (result == null) {
                AppLogger.e(TAG, "绑定服务超时 (${BIND_SERVICE_TIMEOUT_MS}ms). 无障碍服务提供者可能未响应或崩溃.")
                connectionContinuation = null
                _isBound.value = false
                try {
                    context.applicationContext.unbindService(serviceConnection)
                } catch (e: Exception) {
                    // Ignore
                }
                // 尝试解绑原始 context 以防万一
                try {
                    if (context != context.applicationContext) {
                        context.unbindService(serviceConnection)
                    }
                } catch (e: Exception) {}
                return@withLock false
            }

            AppLogger.d(TAG, "bindToService 成功完成")
            result
        }
    }

    /**
     * 解绑服务
     */
    fun unbindFromService(context: Context) {
        if (_isBound.value) {
            try {
                context.applicationContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                AppLogger.e(TAG, "解绑服务失败", e)
            }
            _isBound.value = false
            accessibilityProvider = null
            AppLogger.d(TAG, "服务已解绑")
        }
    }

    /**
     * 从外部服务获取UI层次结构。
     * 如果服务未绑定，会尝试自动重新绑定一次。
     */
    suspend fun getUIHierarchy(context: Context): String {
        if (!ensureBound(context)) {
            AppLogger.e(TAG, "绑定失败，无法获取UI层次结构")
            return ""
        }
        return try {
            accessibilityProvider?.uiHierarchy ?: ""
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "从提供者获取UI层次结构失败", e)
            // Consider re-binding or notifying the user
            ""
        }
    }

    /**
     * 从UI层次结构的XML中解析出窗口信息（包名）。
     * 活动名称现在通过 getCurrentActivityName() 函数单独获取。
     * @param xmlHierarchy UI层次结构的XML字符串
     * @return 一个Pair，第一个元素是包名，第二个是null（活动名称需单独获取）。
     */
    fun extractWindowInfo(xmlHierarchy: String): Pair<String?, String?> {
        if (xmlHierarchy.isEmpty()) {
            return Pair(null, null)
        }
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlHierarchy))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "node") {
                            // 只获取根节点的包名，活动名称通过单独的函数获取
                            val rootPackage = parser.getAttributeValue(null, "package")
                            return Pair(rootPackage, null)
                        }
                    }
                }
                eventType = parser.next()
            }
            
            return Pair(null, null)

        } catch (e: Exception) {
            AppLogger.e(TAG, "解析窗口信息时出错", e)
            return Pair(null, null)
        }
    }

    /**
     * 请求远程服务在指定坐标执行点击。
     */
    suspend fun performClick(context: Context, x: Int, y: Int): Boolean {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法执行点击")
            return false
        }
        return try {
            accessibilityProvider?.performClick(x, y) ?: false
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "请求点击操作失败", e)
            false
        }
    }

    suspend fun performLongPress(context: Context, x: Int, y: Int): Boolean {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法执行长按")
            return false
        }
        return try {
            accessibilityProvider?.performLongPress(x, y) ?: false
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "长按点击操作失败", e)
            false
        }
    }

    /**
     * 请求远程服务执行滑动。
     */
    suspend fun performSwipe(context: Context, startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法执行滑动")
                return false
            }
        return try {
            accessibilityProvider?.performSwipe(startX, startY, endX, endY, duration) ?: false
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "请求滑动操作失败", e)
            false
        }
    }

    /**
     * 请求远程服务执行全局操作。
     */
    suspend fun performGlobalAction(context: Context, actionId: Int): Boolean {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法执行全局操作")
            return false
        }
        return try {
            accessibilityProvider?.performGlobalAction(actionId) ?: false
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "请求全局操作失败", e)
            false
        }
    }

    /**
     * 请求远程服务查找有焦点的节点的ID。
     */
    suspend fun findFocusedNodeId(context: Context): String? {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法查找焦点节点")
            return null
        }
        return try {
            accessibilityProvider?.findFocusedNodeId()
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "请求查找焦点节点ID失败", e)
            null
        }
    }

    /**
     * 请求远程服务在指定ID的节点上设置文本。
     */
    suspend fun setTextOnNode(context: Context, nodeId: String, text: String): Boolean {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法设置文本")
            return false
        }
        return try {
            accessibilityProvider?.setTextOnNode(nodeId, text) ?: false
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "请求设置文本失败", e)
            false
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求设置文本时远程服务发生异常", e)
            false
        }
    }

    /**
     * 请求远程服务截取屏幕截图。
     */
    suspend fun takeScreenshot(context: Context, path: String, format: String): Boolean {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法截取屏幕截图")
            return false
        }
        return try {
            accessibilityProvider?.takeScreenshot(path, format) ?: false
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "请求截取屏幕截图失败", e)
            false
        }
    }

    /**
     * 检查远程无障碍服务是否已在系统设置中启用。
     */
    suspend fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法检查无障碍服务状态")
            return false
        }
        return try {
            accessibilityProvider?.isAccessibilityServiceEnabled ?: false
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "检查无障碍服务状态失败", e)
            false
        }
    }

    /**
     * 从远程服务获取当前Activity名称。
     */
    suspend fun getCurrentActivityName(context: Context): String? {
        if (!ensureBound(context)) {
            AppLogger.w(TAG, "绑定失败，无法获取Activity名称")
            return null
        }
        return try {
            accessibilityProvider?.currentActivityName
        } catch (e: RemoteException) {
            AppLogger.e(TAG, "从提供者获取Activity名称失败", e)
            null
        }
    }
}