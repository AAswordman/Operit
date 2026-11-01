package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.OrientationReloadPolicy
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.support.onboarding.screens.WelcomeScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.loading.McpLoadingScreenWithState
import com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.loading.McpLoadingState
import com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.loading.LocalMcpLoadingState
import com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.loading.McpLoadingStateRegistry
import com.ai.assistance.operit.ui.theme.OperitTheme
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.core.diagnostics.AnrMonitor
import com.ai.assistance.operit.util.android.LocaleUtils
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ai.assistance.operit.data.mcp.MCPRepository
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopWidgetHost
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RESTORE_COMPLETED_NOTICE = "operit.restore_completed_notice"
        const val ACTION_OPEN_SETTINGS_SHORTCUT = "com.ai.assistance.operit.action.OPEN_SETTINGS_SHORTCUT"
    }

    private val TAG = "MainActivity"

    // ======== 屏幕方向变更状态 ========
    private var showOrientationChangeDialog by mutableStateOf(false)
    private var orientationReloadPolicy = OrientationReloadPolicy.ASK
    private var lastOrientation: Int? = null

    // ======== 工具和管理器 ========
    private lateinit var toolHandler: AIToolHandler
    private lateinit var agreementPreferences: AgreementPreferences
    private var updateCheckPerformed = false
    private var isRestoringActivityState = false
    private lateinit var anrMonitor: AnrMonitor
    private lateinit var mcpRepository: MCPRepository

    // ======== 模型上下文协议手动加载状态 ========
    private val mcpLoadingState = McpLoadingState()

    // ======== 双击返回退出相关变量 ========
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000 // 两次点击的时间间隔，单位为毫秒

    // UpdateManager实例
    private lateinit var updateManager: UpdateManager


    // 存储待处理的分享文件URIs
    private var pendingSharedFileUris: List<Uri>? = null

    private var pendingSharedText: String? = null
    private var pendingShortcutNavItem by mutableStateOf<NavItem?>(null)
    private var pendingShortcutRequestId by mutableStateOf(0L)
    private var currentMainNavItem by mutableStateOf<NavItem>(NavItem.AiChat)
    private var pendingRouteId by mutableStateOf<String?>(null)
    private var pendingRouteArgs by mutableStateOf<Map<String, Any?>>(emptyMap())
    private var pendingRouteRequestId by mutableStateOf(0L)


    private fun processPendingSharedText() {
        if (pendingSharedFileUris != null) {
            AppLogger.d(TAG, "Pending shared text will be processed with shared files")
            return
        }

        val text = pendingSharedText?.trim()
        if (text.isNullOrBlank()) {
            AppLogger.d(TAG, "No pending shared text to process")
            return
        }

        SharedFileHandler.setSharedText(text)
        AppLogger.d(TAG, "Successfully passed shared text to SharedFileHandler")
        pendingSharedText = null
    }

    private fun restoreRuntimeTaskViewVisibilityIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (AIForegroundService.isRunning.get()) return

        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.appTasks?.forEach { task ->
                try {
                    task.setExcludeFromRecents(false)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "恢复最近任务可见性失败", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "恢复运行时任务视图可见性失败", e)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // 获取当前设置的语言
        val code = LocaleUtils.getCurrentLanguage(newBase)
        val locale = LocaleUtils.getLocaleForLanguageCode(code, newBase)
        val config = LocaleUtils.createLocaleOverrideConfiguration(locale)

        // 设置语言配置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleUtils.setDefaultLocales(locale)
        } else {
            @Suppress("DEPRECATION")
            Locale.setDefault(locale)
        }

        // 使用createConfigurationContext创建新的本地化上下文
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
        AppLogger.d(TAG, "MainActivity应用语言设置: $code")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRestoringActivityState = savedInstanceState != null
        lastOrientation = resources.configuration.orientation
        AppLogger.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")

        // Set window background to solid color to prevent system theme leaking through
        window.setBackgroundDrawableResource(android.R.color.black)

        // Handle the intent that started the activity
        handleIntent(intent)
        restoreRuntimeTaskViewVisibilityIfNeeded()

        (application as OperitApplication).initializeMainApplication()

        // 语言设置已在Application中初始化，这里无需重复

        initializeComponents()
        anrMonitor.start()
        configureDisplaySettings()
        lifecycleScope.launch {
            DisplayPreferencesManager.getInstance(this@MainActivity).orientationReloadPolicy.collect { policy ->
                orientationReloadPolicy = policy
                if (policy != OrientationReloadPolicy.ASK) showOrientationChangeDialog = false
            }
        }

        // 仅为手动加载入口提供状态与日志，不在应用启动时运行服务。
        mcpLoadingState.setAppContext(this)
        McpLoadingStateRegistry.bind(mcpLoadingState, lifecycleScope)

        // 设置跳过加载的回调
        mcpLoadingState.setOnSkipCallback {
            AppLogger.d(TAG, "用户跳过了模型上下文协议加载面板")
            OKToast.info(this, getString(R.string.plugin_loading_skipped), OKToast.LENGTH_SHORT).show()
        }

        // 设置初始界面
        setAppContent()

        // 初始化并设置更新管理器
        setupUpdateManager()

        // 只在首次创建时执行检查（非配置变更）
        if (savedInstanceState == null) {
            // 进行必要的初始检查
            performInitialChecks()
        }

        // 设置双击返回退出
        setupBackPressHandler()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // 重要：更新当前Intent
        AppLogger.d(TAG, "onNewIntent: Received intent with action: ${intent?.action}")
        restoreRuntimeTaskViewVisibilityIfNeeded()
        val handledShortcutIntent = handleIntent(intent)

        if (handledShortcutIntent) {
            return
        }
        
        // 如果是分享或打开内容，立即处理
        if (intent?.action == Intent.ACTION_VIEW ||
            intent?.action == Intent.ACTION_SEND ||
            intent?.action == Intent.ACTION_SEND_MULTIPLE
        ) {
            processPendingSharedFiles()
            processPendingSharedText()
        }
    }

    private fun handleIntent(intent: Intent?): Boolean {
        if (intent?.action == ACTION_OPEN_SETTINGS_SHORTCUT) {
            pendingShortcutNavItem = NavItem.Settings
            pendingShortcutRequestId = System.currentTimeMillis()
            currentMainNavItem = NavItem.Settings
            AppLogger.d(TAG, "Shortcut requested opening settings")
            return true
        }

        val pendingWidgetRouteId =
            intent?.getStringExtra(ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ID)?.trim().orEmpty()
        if (pendingWidgetRouteId.isNotBlank()) {
            pendingRouteId = pendingWidgetRouteId
            pendingRouteArgs =
                parseRouteArgsJson(
                    intent?.getStringExtra(ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ARGS_JSON)
                )
            pendingRouteRequestId = System.currentTimeMillis()
            AppLogger.d(TAG, "Shortcut requested opening route: $pendingWidgetRouteId")
            return true
        }

        // Handle opened and shared files
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                // Handle "Open with" action
                intent.data?.let { uri ->
                    if (uri.scheme == "http" || uri.scheme == "https") {
                        pendingSharedText = uri.toString()
                        AppLogger.d(TAG, "Received link to open: $uri")
                    } else {
                        pendingSharedFileUris = listOf(uri)
                        AppLogger.d(TAG, "Received file to open: $uri")
                    }
                }
            }
            Intent.ACTION_SEND -> {
                // Handle "Share" action
                @Suppress("DEPRECATION")
                val uri = if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                uri?.let {
                    pendingSharedFileUris = listOf(it)
                    AppLogger.d(TAG, "Received shared file: $it")
                }

                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    pendingSharedText = sharedText
                    AppLogger.d(TAG, "Received shared text")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    pendingSharedFileUris = uris
                    AppLogger.d(TAG, "Received shared files: ${uris.size}")
                }

                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    pendingSharedText = sharedText
                    AppLogger.d(TAG, "Received shared text")
                }
            }
        }
        return false
    }

    private suspend fun prepareStartupChatIfNeeded() {
        // Android 恢复原界面时继续原聊天，避免“启动新聊天”掩盖已保存的草稿。
        if (isRestoringActivityState) return
        try {
            val displayPreferencesManager =
                DisplayPreferencesManager.getInstance(this@MainActivity)
            if (!displayPreferencesManager.startWithNewChat.first()) {
                return
            }

            val chatHistoryManager = ChatHistoryManager.getInstance(this@MainActivity)
            val newChat = chatHistoryManager.createNewChat(
                setAsCurrentChat = false
            )
            chatHistoryManager.setCurrentChatId(newChat.id)
            AppLogger.d(TAG, "启动时已创建新的空白聊天")
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动时创建空白聊天失败", e)
        }
    }

    private fun parseRouteArgsJson(raw: String?): Map<String, Any?> {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) {
            return emptyMap()
        }
        return try {
            val json = JSONObject(text)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.opt(key)
                    put(key, if (value == JSONObject.NULL) null else value)
                }
            }
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to parse pending route args json", error)
            emptyMap()
        }
    }

    // ======== 设置初始占位内容 ========

    // ======== 执行初始化检查 ========
    private fun performInitialChecks() {
        lifecycleScope.launch {
            prepareStartupChatIfNeeded()
        }
    }

    // ======== 处理待处理的分享文件 ========
    private fun processPendingSharedFiles() {
        val uris = pendingSharedFileUris
        if (uris == null) {
            AppLogger.d(TAG, "No pending shared files to process")
            return
        }
        
        AppLogger.d(TAG, "Processing ${uris.size} pending shared file(s)")
        uris.forEachIndexed { index, uri ->
            AppLogger.d(TAG, "  [$index] URI: $uri")
        }
        
        lifecycleScope.launch {
            try {
                // Pass the URIs to the chat screen via SharedFileHandler
                val sharedText = pendingSharedText?.trim()
                SharedFileHandler.setSharedFiles(uris, sharedText)
                AppLogger.d(TAG, "Successfully passed shared files to SharedFileHandler")
                pendingSharedFileUris = null
                pendingSharedText = null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to process shared files", e)
                OKToast.error(
                    this@MainActivity,
                    getString(R.string.chat_process_shared_files_failed, e.message ?: ""),
                    OKToast.LENGTH_LONG
                ).show()
                pendingSharedFileUris = null
            }
        }
    }

    // 配置双击返回退出的处理器
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - backPressedTime > backPressedInterval) {
                            // 第一次点击，显示提示
                            backPressedTime = currentTime
                            OKToast.info(this@MainActivity, getString(R.string.press_back_again_to_exit), OKToast.LENGTH_SHORT).show()
                        } else {
                            // 第二次点击，退出应用
                            finish()
                        }
                    }
                }
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "onDestroy called")

        McpLoadingStateRegistry.unbind(mcpLoadingState)

        // 确保隐藏加载界面
        mcpLoadingState.hide()

        // 主界面销毁时，确保关闭虚拟屏幕 Overlay 并断开 Shower WebSocket 连接
        try {
            VirtualDisplayOverlay.hideAll()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error hiding VirtualDisplayOverlay in MainActivity.onDestroy", e)
        }

        anrMonitor.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLogger.d(TAG, "onConfigurationChanged: new orientation=${newConfig.orientation}, last orientation=${lastOrientation}")

        // 屏幕方向变化时，确保加载界面不可见
        mcpLoadingState.hide()

        // 仅当方向确实发生变化时才处理
        if (newConfig.orientation != lastOrientation) {
            // 记录变化前的方向
            val orientationBeforeChange = lastOrientation
            // 更新最后的方向记录
            lastOrientation = newConfig.orientation

            // 检查是否是“转回去”的操作
            if (showOrientationChangeDialog && newConfig.orientation == orientationBeforeChange) {
                // 如果是，隐藏弹窗并结束
                showOrientationChangeDialog = false
                return
            }
            
            when (orientationReloadPolicy) {
                OrientationReloadPolicy.ALWAYS -> {
                    showOrientationChangeDialog = false
                    recreate()
                }
                OrientationReloadPolicy.ASK -> showOrientationChangeDialog = true
                OrientationReloadPolicy.NEVER -> showOrientationChangeDialog = false
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        val message = intent?.getStringExtra(EXTRA_RESTORE_COMPLETED_NOTICE) ?: return
        intent.removeExtra(EXTRA_RESTORE_COMPLETED_NOTICE)
        window.decorView.post { OKToast.success(this, message, OKToast.LENGTH_LONG).show() }
    }

    // ======== 初始化组件 ========
    private fun initializeComponents() {
        // 初始化工具处理器（工具注册已在Application中完成）
        toolHandler = AIToolHandler.getInstance(this)

        // 初始化MCP仓库
        mcpRepository = MCPRepository(this)

        anrMonitor = AnrMonitor(this, lifecycleScope)

        // 初始化协议偏好管理器
        agreementPreferences = AgreementPreferences(this)

    }

    // ======== 显示与性能配置 ========
    private fun configureDisplaySettings() {
        // 1. 请求持续的高性能模式 (API 31+)
        // 这会提示系统为应用提供持续的高性能，避免CPU/GPU降频。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setSustainedPerformanceMode(true)
                AppLogger.d(TAG, "已成功请求持续高性能模式。")
            } catch (e: Exception) {
                // 在某些设备上，此模式可能不可用或不支持。
                AppLogger.w(TAG, "请求持续高性能模式失败。", e)
            }
        }

        // 2. 设置应用以最高刷新率运行
        // 高刷新率优化：通过设置窗口属性确保流畅
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 为Android 11+设备优化高刷新率
            val highestMode = getHighestRefreshRate()
            if (highestMode > 0) {
                window.attributes.preferredDisplayModeId = highestMode
                AppLogger.d(TAG, "设置窗口首选显示模式ID: $highestMode")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 为Android 6.0-10设备优化高刷新率
            val refreshRate = getDeviceRefreshRate()
            if (refreshRate > 60f) {
                window.attributes.preferredRefreshRate = refreshRate
                AppLogger.d(TAG, "设置窗口首选刷新率: $refreshRate Hz")
            }
        }

        // 启用硬件加速以提高渲染性能
        window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    // ======== 设置应用内容 ========
    private fun setAppContent() {
        setContent {
            OperitTheme {
                Box {
                    val onboardingCompleted by agreementPreferences.onboardingCompletedFlow.collectAsState()
                    if (!onboardingCompleted) {
                        WelcomeScreen(
                            onStart = {
                                agreementPreferences.markOnboardingCompleted()
                            }
                        )
                    } else {
                            // 处理待处理的分享文件
                            processPendingSharedFiles()
                            processPendingSharedText()
                            val shortcutNavItem = pendingShortcutNavItem
                            val shortcutNavRequestId = pendingShortcutRequestId
                            val routeNavRequest = pendingRouteId
                            val routeNavArgs = pendingRouteArgs
                            val routeNavRequestId = pendingRouteRequestId
                            val initialNavItem = when {
                                shortcutNavItem != null -> shortcutNavItem
                                else -> currentMainNavItem
                            }

                            CompositionLocalProvider(LocalMcpLoadingState provides mcpLoadingState) {
                                // 主应用界面 (始终存在于底层)
                                OperitApp(
                                        initialNavItem = initialNavItem,
                                        toolHandler = toolHandler,
                                        shortcutNavRequest = shortcutNavItem,
                                        shortcutNavRequestId = shortcutNavRequestId,
                                        routeNavRequest = routeNavRequest,
                                        routeNavArgs = routeNavArgs,
                                        routeNavRequestId = routeNavRequestId,
                                        onShortcutNavHandled = { handledRequestId ->
                                            if (pendingShortcutRequestId == handledRequestId) {
                                                pendingShortcutNavItem = null
                                                pendingShortcutRequestId = 0L
                                            }
                                        },
                                        onCurrentNavItemChanged = { navItem ->
                                            currentMainNavItem = navItem
                                        },
                                        onRouteNavHandled = { handledRequestId ->
                                            if (pendingRouteRequestId == handledRequestId) {
                                                pendingRouteId = null
                                                pendingRouteArgs = emptyMap()
                                                pendingRouteRequestId = 0L
                                            }
                                        }
                                )
                            }
                        }
                    }
                    // 用户手动启动服务时显示进度，开屏保持隐藏。
                    McpLoadingScreenWithState(
                            loadingState = mcpLoadingState,
                            modifier = Modifier.zIndex(10f) // 确保加载界面在最上层
                    )
                }

                // 方向改变时显示对话框
                if (showOrientationChangeDialog) {
                    OrientationChangeDialog(
                        onConfirm = {
                            showOrientationChangeDialog = false
                            // 重新创建Activity以重新加载页面
                            recreate()
                        },
                        onDismiss = {
                            showOrientationChangeDialog = false
                        }
                    )
                }
            }
        }


    private fun getHighestRefreshRate(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayModes = display?.supportedModes ?: return 0
            var maxRefreshRate = 60f // Default to 60Hz
            var highestModeId = 0

            for (mode in displayModes) {
                if (mode.refreshRate > maxRefreshRate) {
                    maxRefreshRate = mode.refreshRate
                    highestModeId = mode.modeId
                }
            }
            AppLogger.d(TAG, "Selected display mode with refresh rate: $maxRefreshRate Hz")
            return highestModeId
        }
        return 0
    }

    private fun getDeviceRefreshRate(): Float {
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val display =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION") windowManager.defaultDisplay
                }

        var refreshRate = 60f // Default refresh rate

        if (display != null) {
            try {
                @Suppress("DEPRECATION") val modes = display.supportedModes
                for (mode in modes) {
                    if (mode.refreshRate > refreshRate) {
                        refreshRate = mode.refreshRate
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting refresh rate", e)
            }
        }

        AppLogger.d(TAG, "Selected refresh rate: $refreshRate Hz")
        return refreshRate
    }

    // ======== 设置更新管理器 ========
    private fun setupUpdateManager() {
        // 获取UpdateManager实例
        updateManager = UpdateManager.getInstance(this)

        // 观察更新状态（beta / 非 beta 都提示新版本）
        updateManager.updateStatus.observe(
            this,
            Observer { status ->
                when (status) {
                    is UpdateStatus.Available -> showUpdateNotification(status.newVersion)
                    is UpdateStatus.PatchAvailable -> showUpdateNotification(status.newVersion)
                    else -> Unit
                }
            }
        )

        // 自动检查更新（仅提示，不自动下载）
        lifecycleScope.launch {
            // 延迟几秒，等待应用完全启动
            delay(3000)
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        if (updateCheckPerformed) return
        updateCheckPerformed = true

        val appVersion =
            try {
                packageManager.getPackageInfo(packageName, 0).versionName
                    ?: getString(R.string.unknown_value)
            } catch (e: PackageManager.NameNotFoundException) {
                getString(R.string.unknown_value)
            }

        // 使用UpdateManager检查更新
        lifecycleScope.launch {
            try {
                updateManager.checkForUpdatesSilently(appVersion)
                // 不需要显式处理更新状态，因为我们已经设置了观察者
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新检查失败: ${e.message}")
            }
        }
    }

    private fun showUpdateNotification(newVersion: String) {
        val currentVersion =
            try {
                packageManager.getPackageInfo(packageName, 0).versionName
                    ?: getString(R.string.unknown_value)
            } catch (e: Exception) {
                getString(R.string.unknown_value)
            }

        AppLogger.d(TAG, "发现新版本: $newVersion，当前版本: $currentVersion")

        // 显示更新提示
        OKToast.info(
            this,
            getString(R.string.main_update_available_toast, newVersion),
            OKToast.LENGTH_LONG
        ).show()
    }

}

@Composable
private fun OrientationChangeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.dialog_title_orientation_change)) },
        text = { Text(text = stringResource(id = R.string.dialog_message_orientation_change)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.dialog_button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_button_dismiss))
            }
        }
    )
}
