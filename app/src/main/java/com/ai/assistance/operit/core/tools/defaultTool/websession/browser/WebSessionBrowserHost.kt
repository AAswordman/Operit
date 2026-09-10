package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiStateStore
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserScreen
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionFloatingTheme
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionMinimizedIndicator
import com.ai.assistance.operit.util.AppLogger
import kotlin.math.roundToInt
import org.json.JSONTokener

internal class WebSessionBrowserHost(
    private val appContext: Context,
    private val store: WebSessionHistoryStore,
    private val userscriptStore: WebSessionUserscriptUiStateStore,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onNavigate(url: String)
        fun onBack()
        fun onForward()
        fun onRefreshOrStop()
        fun onSelectTab(sessionId: String)
        fun onCloseTab(sessionId: String)
        fun onNewTab()
        fun onMinimize()
        fun onCloseCurrentTab()
        fun onCloseAllTabs()
        fun onToggleBookmark(url: String, title: String)
        fun onRemoveBookmark(url: String)
        fun onSelectSessionHistory(index: Int)
        fun onOpenUrl(url: String)
        fun onClearHistory()
        fun onToggleDesktopMode()
        fun onOpenUserscripts()
        fun onImportUserscript()
        fun onInstallUserscriptFromUrl(url: String)
        fun onConfirmUserscriptInstall()
        fun onCancelUserscriptInstall()
        fun onSetUserscriptEnabled(scriptId: Long, enabled: Boolean)
        fun onDeleteUserscript(scriptId: Long)
        fun onCheckUserscriptUpdate(scriptId: Long)
        fun onInvokeUserscriptMenu(commandId: String)
        fun onPauseDownload(taskId: String)
        fun onResumeDownload(taskId: String)
        fun onCancelDownload(taskId: String)
        fun onRetryDownload(taskId: String)
        fun onDeleteDownload(taskId: String, deleteFile: Boolean)
        fun onOpenDownloadedFile(taskId: String)
        fun onOpenDownloadLocation(taskId: String)
        fun onConfirmExternalOpen(requestId: String)
        fun onCancelExternalOpen(requestId: String)
        fun onHandlePendingDialog(accept: Boolean, promptText: String?)
    }

    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val webViewHost = WebSessionWebViewHost()

    private var rootView: DeceptiveMinimizedLayout? = null
    private var composeView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: WebSessionOverlayLifecycleOwner? = null

    private var indicatorView: ComposeView? = null
    private var indicatorParams: WindowManager.LayoutParams? = null
    private var indicatorLifecycleOwner: WebSessionOverlayLifecycleOwner? = null
    private var selectionActionsView: View? = null
    private var selectionActionsParams: WindowManager.LayoutParams? = null

    private var isExpanded: Boolean = false
    private var hostState by mutableStateOf(WebSessionBrowserHostState())
    fun ensureCreated(initialExpanded: Boolean = false) {
        if (rootView != null) {
            if (isExpanded != initialExpanded) {
                setExpanded(initialExpanded)
            }
            return
        }

        val lifecycleOwner =
            WebSessionOverlayLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

        val root =
            DeceptiveMinimizedLayout(appContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setOnClickListener {}
            }
        installViewTreeOwners(root, lifecycleOwner)

        val compose =
            ComposeView(appContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                alpha = 1f
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                installViewTreeOwners(this, lifecycleOwner)
                setContent {
                    val bookmarks by store.bookmarksFlow.collectAsState(initial = emptyList())
                    val history by store.historyFlow.collectAsState(initial = emptyList())
                    val userscriptUiState by userscriptStore.state.collectAsState()

                    WebSessionFloatingTheme {
                        WebSessionBrowserScreen(
                            hostState = hostState,
                            bookmarks = bookmarks,
                            globalHistory = history,
                            userscriptUiState = userscriptUiState,
                            webViewHost = webViewHost,
                            onHostStateChange = { transform ->
                                updateHostState(transform)
                            },
                            onNavigate = callbacks::onNavigate,
                            onBack = callbacks::onBack,
                            onForward = callbacks::onForward,
                            onRefreshOrStop = callbacks::onRefreshOrStop,
                            onSelectTab = callbacks::onSelectTab,
                            onCloseTab = callbacks::onCloseTab,
                            onNewTab = callbacks::onNewTab,
                            onMinimize = callbacks::onMinimize,
                            onCloseCurrentTab = callbacks::onCloseCurrentTab,
                            onCloseAllTabs = callbacks::onCloseAllTabs,
                            onToggleBookmark = callbacks::onToggleBookmark,
                            onRemoveBookmark = callbacks::onRemoveBookmark,
                            onSelectSessionHistory = callbacks::onSelectSessionHistory,
                            onOpenUrl = callbacks::onOpenUrl,
                            onClearHistory = callbacks::onClearHistory,
                            onToggleDesktopMode = callbacks::onToggleDesktopMode,
                            onOpenUserscripts = callbacks::onOpenUserscripts,
                            onImportUserscript = callbacks::onImportUserscript,
                            onInstallUserscriptFromUrl = callbacks::onInstallUserscriptFromUrl,
                            onConfirmUserscriptInstall = callbacks::onConfirmUserscriptInstall,
                            onCancelUserscriptInstall = callbacks::onCancelUserscriptInstall,
                            onSetUserscriptEnabled = callbacks::onSetUserscriptEnabled,
                            onDeleteUserscript = callbacks::onDeleteUserscript,
                            onCheckUserscriptUpdate = callbacks::onCheckUserscriptUpdate,
                            onInvokeUserscriptMenu = callbacks::onInvokeUserscriptMenu,
                            onPauseDownload = callbacks::onPauseDownload,
                            onResumeDownload = callbacks::onResumeDownload,
                            onCancelDownload = callbacks::onCancelDownload,
                            onRetryDownload = callbacks::onRetryDownload,
                            onDeleteDownload = callbacks::onDeleteDownload,
                            onOpenDownloadedFile = callbacks::onOpenDownloadedFile,
                            onOpenDownloadLocation = callbacks::onOpenDownloadLocation,
                            onConfirmExternalOpen = callbacks::onConfirmExternalOpen,
                            onCancelExternalOpen = callbacks::onCancelExternalOpen,
                            onHandlePendingDialog = callbacks::onHandlePendingDialog
                        )
                    }
                }
            }

        root.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        overlayLifecycleOwner = lifecycleOwner
        rootView = root
        composeView = compose
        overlayParams = createOverlayLayoutParams(initialExpanded)
        windowManager.addView(root, overlayParams)
        setExpanded(initialExpanded)
    }

    fun destroy() {
        hideTextSelectionActionsOverlay()
        hideIndicator()
        overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        rootView?.let { root ->
            try {
                windowManager.removeView(root)
            } catch (_: Exception) {
            }
        }

        overlayLifecycleOwner = null
        composeView = null
        rootView = null
        overlayParams = null
        indicatorParams = null
        webViewHost.clear()
    }

    fun updateHostProjection(
        browserState: WebSessionBrowserState,
        downloadUiState: BrowserDownloadUiState,
        externalOpenPrompt: ExternalOpenPromptState?
    ) {
        hostState =
            hostState.copy(
                browserState = browserState,
                downloadUiState =
                    downloadUiState.copy(
                        selectedFilter = hostState.downloadUiState.selectedFilter
                    ),
                externalOpenPrompt = externalOpenPrompt,
                urlDraft =
                    if (hostState.isEditingUrl) {
                        hostState.urlDraft
                    } else {
                        browserState.currentUrl
                    }
            )
        updateIndicatorLayoutForCurrentState()
    }

    fun attachActiveWebView(webView: WebView?, viewportWidth: Int? = null, viewportHeight: Int? = null) {
        updateHostState {
            if (it.viewportWidthPx == viewportWidth && it.viewportHeightPx == viewportHeight) {
                it
            } else {
                it.copy(viewportWidthPx = viewportWidth, viewportHeightPx = viewportHeight)
            }
        }
        webViewHost.setActiveWebView(webView, viewportWidth, viewportHeight)
    }

    fun showTextSelectionActionsOverlay(anchorX: Double, anchorY: Double) {
        if (!isExpanded) {
            return
        }

        val currentView = selectionActionsView
        if (currentView != null) {
            updateTextSelectionActionsLayout(currentView, anchorX, anchorY)
            return
        }

        val actionsView = createTextSelectionActionsView()
        val params = createTextSelectionActionsLayoutParams()
        selectionActionsParams = params
        try {
            windowManager.addView(actionsView, params)
            selectionActionsView = actionsView
            actionsView.post {
                updateTextSelectionActionsLayout(actionsView, anchorX, anchorY)
            }
        } catch (e: Exception) {
            selectionActionsParams = null
            AppLogger.e("WebSessionBrowserHost", "Failed to show WebView text selection actions", e)
        }
    }

    fun performTextSelectionHaptic() {
        rootView?.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    fun hideTextSelectionActionsOverlay() {
        val view = selectionActionsView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            AppLogger.e("WebSessionBrowserHost", "Failed to hide WebView text selection actions", e)
        }
        selectionActionsView = null
        selectionActionsParams = null
    }

    fun isExpanded(): Boolean = isExpanded

    fun setViewportSize(width: Int, height: Int) {
        updateHostState {
            it.copy(
                viewportWidthPx = width,
                viewportHeightPx = height
            )
        }
        webViewHost.setViewportSize(width, height)
    }

    fun clearViewportSizeOverride() {
        updateHostState { it.copy(viewportWidthPx = null, viewportHeightPx = null) }
        webViewHost.setViewportSize(null, null)
    }

    fun currentViewportSize(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        val width = hostState.viewportWidthPx ?: metrics.widthPixels
        val height = hostState.viewportHeightPx ?: metrics.heightPixels
        return width to height
    }

    fun currentBrowserAreaSize(): Pair<Int, Int> =
        hostState.browserAreaWidthPx.coerceAtLeast(0) to hostState.browserAreaHeightPx.coerceAtLeast(0)

    fun setExpanded(expanded: Boolean) {
        val params = overlayParams ?: return
        val root = rootView ?: return
        val compose = composeView ?: return

        if (!expanded) {
            hideTextSelectionActionsOverlay()
        }

        isExpanded = expanded
        hostState =
            if (expanded) {
                hostState
            } else {
                hostState.copy(
                    sheetRoute = WebSessionBrowserSheetRoute.NONE,
                    isEditingUrl = false,
                    urlDraft = hostState.browserState.currentUrl
                )
            }

        if (expanded) {
            root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            root.setMinimizedMeasure(false)
            root.setBackgroundColor(AndroidColor.TRANSPARENT)
            compose.alpha = 1f

            applyExpandedLayoutParams(params)
            hideIndicator()
        } else {
            if (indicatorView == null) {
                showIndicator()
            }

            root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            val displayMetrics = appContext.resources.displayMetrics
            root.setMinimizedMeasure(
                enabled = true,
                fakeWidth = displayMetrics.widthPixels,
                fakeHeight = displayMetrics.heightPixels
            )
            root.setBackgroundColor(AndroidColor.TRANSPARENT)
            compose.alpha = 0.01f

            params.width = 1
            params.height = 1
            params.gravity = Gravity.TOP or Gravity.START
            params.flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            indicatorParams?.let {
                params.x = it.x
                params.y = it.y
            }
        }

        overlayParams = params
        if (root.windowToken != null) {
            windowManager.updateViewLayout(root, params)
        }
        updateIndicatorLayoutForCurrentState()
    }

    private fun applyExpandedLayoutParams(params: WindowManager.LayoutParams) {
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    fun showSheet(route: WebSessionBrowserSheetRoute) {
        updateHostState { it.copy(sheetRoute = route) }
    }

    private fun updateHostState(transform: (WebSessionBrowserHostState) -> WebSessionBrowserHostState) {
        val updated = transform(hostState)
        if (updated == hostState) {
            return
        }
        hostState = updated
    }

    private fun copyActiveWebViewSelection() {
        evaluateActiveWebViewSelectedText { selectedText ->
            if (selectedText.isEmpty()) {
                Toast.makeText(appContext, appContext.getString(R.string.no_text_to_copy), Toast.LENGTH_SHORT).show()
                return@evaluateActiveWebViewSelectedText
            }
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("web_selection", selectedText))
            clearActiveWebViewSelection()
        }
    }

    private fun evaluateActiveWebViewSelectedText(onResult: (String) -> Unit) {
        val webView = webViewHost.currentWebView() ?: return
        webView.evaluateJavascript(activeWebViewSelectionTextScript()) { rawValue ->
            try {
                val selectedText = JSONTokener(rawValue).nextValue() as String
                onResult(selectedText)
            } catch (e: Exception) {
                AppLogger.e("WebSessionBrowserHost", "Failed to read selected WebView text", e)
            }
        }
    }

    private fun activeWebViewSelectionTextScript(): String =
        """
        (function() {
            return String(window.__operitTextSelection.getText());
        })();
        """.trimIndent()

    private fun selectAllActiveWebViewText() {
        val webView = webViewHost.currentWebView() ?: return
        webView.evaluateJavascript(
            """
            (function() {
                window.__operitTextSelection.selectAll();
            })();
            """.trimIndent(),
            null
        )
    }

    private fun dismissTextSelectionActions() {
        clearActiveWebViewSelection()
    }

    private fun clearActiveWebViewSelection() {
        val webView = webViewHost.currentWebView() ?: return
        webView.evaluateJavascript(
            """
            (function() {
                window.__operitTextSelection.clear();
            })();
            """.trimIndent(),
            null
        )
        hideTextSelectionActionsOverlay()
    }

    private fun createTextSelectionActionsView(): View {
        val density = appContext.resources.displayMetrics.density
        val cornerRadius = (12f * density)
        val horizontalPadding = (4f * density).roundToInt()
        val verticalPadding = (2f * density).roundToInt()

        return LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            background =
                GradientDrawable().apply {
                    setColor(0xF2242424.toInt())
                    setCornerRadius(cornerRadius)
                    setStroke(dp(1), 0x33FFFFFF)
                }
            elevation = 10f * density

            addView(
                createTextSelectionActionButton(appContext.getString(R.string.copy)) {
                    copyActiveWebViewSelection()
                }
            )
            addView(
                createTextSelectionActionButton(appContext.getString(android.R.string.selectAll)) {
                    selectAllActiveWebViewText()
                }
            )
            addView(
                createTextSelectionActionButton(appContext.getString(R.string.cancel)) {
                    dismissTextSelectionActions()
                }
            )
        }
    }

    private fun createTextSelectionActionButton(
        label: String,
        onClick: () -> Unit
    ): TextView =
        TextView(appContext).apply {
            text = label
            setTextColor(AndroidColor.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
            setMinWidth(0)
            setMinHeight(dp(38))
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun createTextSelectionActionsLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

    private fun updateTextSelectionActionsLayout(
        actionsView: View,
        anchorX: Double,
        anchorY: Double
    ) {
        val params = selectionActionsParams ?: return
        val webView = webViewHost.currentWebView() ?: return
        if (actionsView.measuredWidth == 0 || actionsView.measuredHeight == 0) {
            actionsView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }

        val location = IntArray(2)
        webView.getLocationOnScreen(location)
        val scale = webView.scale * webView.scaleX
        val width = actionsView.measuredWidth
        val height = actionsView.measuredHeight
        val metrics = appContext.resources.displayMetrics
        val margin = dp(10)
        val screenX = (location[0] + anchorX * scale).roundToInt()
        val selectionTop = (location[1] + anchorY * scale).roundToInt()
        val aboveY = selectionTop - height - margin
        val belowY = selectionTop + dp(28)
        val maxX = (metrics.widthPixels - width - margin).coerceAtLeast(margin)
        val maxY = (metrics.heightPixels - height - margin).coerceAtLeast(margin)

        params.x = (screenX - width / 2).coerceIn(margin, maxX)
        params.y =
            if (aboveY >= margin) {
                aboveY
            } else {
                belowY.coerceIn(margin, maxY)
            }

        selectionActionsParams = params
        if (actionsView.windowToken != null) {
            windowManager.updateViewLayout(actionsView, params)
        }
    }

    fun syncIndicatorWithMinimizedWindow() {
        if (isExpanded) {
            return
        }
        val params = overlayParams ?: return
        val indicator = indicatorParams ?: return
        val root = rootView ?: return

        params.x = indicator.x
        params.y = indicator.y
        overlayParams = params

        if (root.windowToken != null) {
            windowManager.updateViewLayout(root, params)
        }
    }

    private fun showIndicator() {
        if (indicatorView != null) {
            return
        }

        val lifecycleOwner =
            WebSessionOverlayLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

        val params = indicatorParams ?: createIndicatorLayoutParams().also { indicatorParams = it }

        val indicator =
            ComposeView(appContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                installViewTreeOwners(this, lifecycleOwner)
                setContent {
                    WebSessionFloatingTheme {
                        WebSessionMinimizedIndicator(
                            contentDescription =
                                appContext.getString(R.string.web_session_accessibility_minimized_indicator),
                            activeDownloadCount = hostState.browserState.activeDownloadCount,
                            hasFailedDownloads = hostState.browserState.hasFailedDownloads,
                            externalOpenPrompt = hostState.externalOpenPrompt,
                            onToggleFullscreen = { setExpanded(true) },
                            onDragBy = { dx, dy -> moveIndicatorBy(dx, dy) },
                            onConfirmExternalOpen = callbacks::onConfirmExternalOpen,
                            onCancelExternalOpen = callbacks::onCancelExternalOpen
                        )
                    }
                }
            }

        indicatorLifecycleOwner = lifecycleOwner
        indicatorView = indicator
        windowManager.addView(indicator, params)
    }

    private fun hideIndicator() {
        val view = indicatorView ?: return
        indicatorLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        indicatorLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        indicatorLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        indicatorView = null
        indicatorLifecycleOwner = null
    }

    private fun moveIndicatorBy(dx: Int, dy: Int) {
        val indicator = indicatorView ?: return
        val params = indicatorParams ?: return
        val maxX = (appContext.resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (appContext.resources.displayMetrics.heightPixels - params.height).coerceAtLeast(0)

        params.x = (params.x + dx).coerceIn(0, maxX)
        params.y = (params.y + dy).coerceIn(0, maxY)
        indicatorParams = params
        windowManager.updateViewLayout(indicator, params)
        syncIndicatorWithMinimizedWindow()
    }

    private fun updateIndicatorLayoutForCurrentState() {
        if (isExpanded) {
            return
        }
        val indicator = indicatorView ?: return
        val params = indicatorParams ?: return
        val newWidth = indicatorWidthPx()
        val newHeight = indicatorHeightPx()
        if (params.width == newWidth && params.height == newHeight) {
            return
        }
        params.width = newWidth
        params.height = newHeight
        indicatorParams = params
        if (indicator.windowToken != null) {
            windowManager.updateViewLayout(indicator, params)
        }
        syncIndicatorWithMinimizedWindow()
    }

    private fun installViewTreeOwners(
        view: View,
        lifecycleOwner: WebSessionOverlayLifecycleOwner
    ) {
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
    }

    private fun createOverlayLayoutParams(expanded: Boolean): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        return if (expanded) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                x = 0
                y = 0
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        } else {
            WindowManager.LayoutParams(
                1,
                1,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(16)
                y = dp(16)
            }
        }
    }

    private fun createIndicatorLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        return WindowManager.LayoutParams(
            indicatorWidthPx(),
            indicatorHeightPx(),
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(16)
        }
    }

    private fun indicatorWidthPx(): Int =
        if (hostState.externalOpenPrompt != null) {
            dp(248)
        } else {
            dp(40).coerceAtLeast(1)
        }

    private fun indicatorHeightPx(): Int =
        if (hostState.externalOpenPrompt != null) {
            dp(86)
        } else {
            dp(40).coerceAtLeast(1)
        }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).roundToInt()
}

private class WebSessionOverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreField = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            savedStateRegistryController.performRestore(null)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreField

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.handleLifecycleEvent(event)
        } else {
            Handler(Looper.getMainLooper()).post {
                lifecycleRegistry.handleLifecycleEvent(event)
            }
        }
    }
}

private class DeceptiveMinimizedLayout(context: Context) : FrameLayout(context) {
    var minimizedMeasureEnabled: Boolean = false
    var fakeWidthPx: Int = 1
    var fakeHeightPx: Int = 1

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun setMinimizedMeasure(enabled: Boolean, fakeWidth: Int = fakeWidthPx, fakeHeight: Int = fakeHeightPx) {
        minimizedMeasureEnabled = enabled
        fakeWidthPx = fakeWidth.coerceAtLeast(1)
        fakeHeightPx = fakeHeight.coerceAtLeast(1)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!minimizedMeasureEnabled) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val childWidthSpec =
            View.MeasureSpec.makeMeasureSpec(fakeWidthPx, View.MeasureSpec.EXACTLY)
        val childHeightSpec =
            View.MeasureSpec.makeMeasureSpec(fakeHeightPx, View.MeasureSpec.EXACTLY)

        for (i in 0 until childCount) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec)
        }

        setMeasuredDimension(1, 1)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!minimizedMeasureEnabled) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, fakeWidthPx, fakeHeightPx)
        }
    }
}
