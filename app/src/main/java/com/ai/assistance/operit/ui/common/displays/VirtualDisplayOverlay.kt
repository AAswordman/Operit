package com.ai.assistance.operit.ui.common.displays

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.core.tools.agent.PhoneAgentJobRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.*
import kotlin.random.Random
import com.ai.assistance.operit.ui.floating.ui.ball.rememberParticleSystem
import androidx.core.graphics.drawable.toBitmap
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.R
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.res.stringResource

private data class AutomationActionFeedback(
    val type: Type,
    val startX: Int,
    val startY: Int,
    val endX: Int = startX,
    val endY: Int = startY,
    val id: Long = System.nanoTime()
) {
    enum class Type { TAP, SWIPE }
}

class VirtualDisplayOverlay private constructor(private val context: Context, private val agentId: String) {

    companion object {
        private val instances = ConcurrentHashMap<String, VirtualDisplayOverlay>()

        fun getInstance(context: Context): VirtualDisplayOverlay = getInstance(context, "default")

        fun getInstance(context: Context, agentId: String): VirtualDisplayOverlay {
            val key = if (agentId.isBlank()) "default" else agentId
            return instances.getOrPut(key) { VirtualDisplayOverlay(context.applicationContext, key) }
        }

        fun hide(agentId: String) {
            val key = if (agentId.isBlank()) "default" else agentId
            instances.remove(key)?.hide()
        }

        fun hideAll() {
            val overlays = instances.values.toList()
            instances.clear()
            overlays.forEach { overlay ->
                try {
                    overlay.hide()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun mapOffsetToRemote(offset: Offset, overlaySize: IntSize, videoSize: Pair<Int, Int>?): Pair<Int, Int>? {
        val (vw, vh) = videoSize ?: return null
        if (overlaySize.width <= 0 || overlaySize.height <= 0) return null
        val normX = (offset.x / overlaySize.width.toFloat()).coerceIn(0f, 1f)
        val normY = (offset.y / overlaySize.height.toFloat()).coerceIn(0f, 1f)
        val devX = (normX * (vw - 1)).roundToInt()
        val devY = (normY * (vh - 1)).roundToInt()
        return devX to devY
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var displayId: Int? = null
    @Volatile
    private var surfaceView: ShowerSurfaceView? = null
    private var isFullscreen by mutableStateOf(false)
    private var isSnapped by mutableStateOf(false)
    private var snappedToRight by mutableStateOf(false)
    private var animator: android.animation.ValueAnimator? = null
    private var lastWindowX: Int = 0
    private var lastWindowY: Int = 0
    private var lastWindowWidth: Int = 0
    private var lastWindowHeight: Int = 0
    private var previewPath by mutableStateOf<String?>(null)
    private var currentAppPackageName by mutableStateOf<String?>(null)
    private var controlsVisible by mutableStateOf(false)
    private var rainbowBorderVisible by mutableStateOf(false)
    private var automationCurrentStep by mutableStateOf<Int?>(null)
    private var automationTotalSteps by mutableStateOf<Int?>(null)
    private var automationStatusText by mutableStateOf<String?>(null)
    private var automationIsPaused by mutableStateOf(false)
    private var automationVisible by mutableStateOf(false)
    private var actionFeedback by mutableStateOf<AutomationActionFeedback?>(null)
    private val clearActionFeedback = Runnable { actionFeedback = null }
    private var automationOnTogglePauseResume: ((Boolean) -> Unit)? = null
    private var automationOnExit: (() -> Unit)? = null
    // Fixed left control panel width (in px) added on top of the original video width.
    // The video region itself still uses 0.4x of the screen width; this extra width
    // is only used to host controls without changing the video size.
    private val automationPanelWidthPx: Int by lazy {
        (56 * context.resources.displayMetrics.density).roundToInt()
    }
    private val automationPanelWidthDp = 56.dp

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post {
                try {
                    action()
                } catch (e: Exception) {
                    AppLogger.e("VirtualDisplayOverlay", "Error on main thread", e)
                }
            }
        }
    }

    private fun ensureOverlay() {
        if (overlayView != null) return
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
            }
            layoutParams = params
            lifecycleOwner = ServiceLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            overlayView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    val id = displayId
                    val fullscreen = isFullscreen
                    val path = previewPath
                    val pkg = currentAppPackageName
                    if (id != null) {
                        OverlayCard(id = id, isFullscreen = fullscreen, previewPath = path, currentAppPackageName = pkg)
                    }
                }
            }
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            AppLogger.e("VirtualDisplayOverlay", "Error creating overlay", e)
            overlayView = null
            lifecycleOwner = null
            windowManager = null
            layoutParams = null
        }
    }

    fun show(displayId: Int) {
        runOnMainThread {
            val hadView = overlayView != null
            this.displayId = displayId
            AppLogger.d("VirtualDisplayOverlay", "show: agentId=$agentId displayId=$displayId")
            ensureOverlay()
            overlayView?.visibility = View.VISIBLE
            val uiAccessibilityModeEnabled = try {
                UserPreferencesManager.getInstance(context).isUiAccessibilityModeEnabled()
            } catch (_: Exception) {
                false
            }
            if (!hadView) {
                isFullscreen = false
                val isFirstShow = (lastWindowWidth == 0 || lastWindowHeight == 0)
                isSnapped = !uiAccessibilityModeEnabled
                if (isFirstShow) {
                    snappedToRight = true
                }
                controlsVisible = false
            } else if (uiAccessibilityModeEnabled) {
                if (!isFullscreen && isSnapped) {
                    isSnapped = false
                }
            }
            updateLayoutParams()
        }
    }

    fun updateCurrentAppPackageName(packageName: String?) {
        runOnMainThread {
            currentAppPackageName = packageName
        }
    }

    /** Show the exact remote coordinate used by an automated tap. */
    fun showTapFeedback(x: Int, y: Int, durationMs: Long = 1200L) {
        showActionFeedback(
            AutomationActionFeedback(
                type = AutomationActionFeedback.Type.TAP,
                startX = x,
                startY = y
            ),
            durationMs
        )
    }

    /** Show the exact remote path used by an automated swipe. */
    fun showSwipeFeedback(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 1400L
    ) {
        showActionFeedback(
            AutomationActionFeedback(
                type = AutomationActionFeedback.Type.SWIPE,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY
            ),
            durationMs
        )
    }

    private fun showActionFeedback(feedback: AutomationActionFeedback, durationMs: Long) {
        runOnMainThread {
            handler.removeCallbacks(clearActionFeedback)
            actionFeedback = feedback
            handler.postDelayed(clearActionFeedback, durationMs.coerceAtLeast(300L))
        }
    }

    suspend fun captureCurrentFramePng(): ByteArray? = surfaceView?.captureCurrentFramePng()

    fun showAutomationControls(
        totalSteps: Int,
        initialStatus: String,
        onTogglePauseResume: (Boolean) -> Unit,
        onExit: () -> Unit
    ) {
        runOnMainThread {
            automationTotalSteps = totalSteps
            automationCurrentStep = 1
            automationStatusText = initialStatus
            automationIsPaused = false
            automationOnTogglePauseResume = onTogglePauseResume
            automationOnExit = onExit
            automationVisible = true
        }
    }

    fun updateAutomationProgress(currentStep: Int, totalSteps: Int, statusText: String) {
        runOnMainThread {
            automationTotalSteps = totalSteps
            automationCurrentStep = if (currentStep <= 0) 1 else currentStep
            automationStatusText = statusText
        }
    }

    fun hideAutomationControls() {
        runOnMainThread {
            handler.removeCallbacks(clearActionFeedback)
            actionFeedback = null
            automationVisible = false
            automationOnTogglePauseResume = null
            automationOnExit = null
            automationCurrentStep = null
            automationTotalSteps = null
            automationStatusText = null
            automationIsPaused = false
        }
    }

    fun hide(cancelAutomation: Boolean = false, cancelReason: String = "User closed virtual screen") {
        Companion.instances.remove(agentId, this)
        runOnMainThread {
            try {
                handler.removeCallbacks(clearActionFeedback)
                actionFeedback = null
                if (cancelAutomation) {
                    PhoneAgentJobRegistry.cancelAgent(agentId, cancelReason)
                }
                ShowerController.shutdown(agentId)
                overlayView?.let { view ->
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    try {
                        windowManager?.removeView(view)
                    } catch (e: Exception) {
                        AppLogger.e("VirtualDisplayOverlay", "Error removing overlay view", e)
                    }
                }
                overlayView = null
                lifecycleOwner = null
                layoutParams = null
                windowManager = null
                displayId = null
                currentAppPackageName = null
                surfaceView = null
            } catch (e: Exception) {
                AppLogger.e("VirtualDisplayOverlay", "Error hiding overlay", e)
            }
        }
    }

    fun setShowerBorderVisible(visible: Boolean) {
        runOnMainThread {
            rainbowBorderVisible = visible
        }
    }

    private fun toggleFullScreen() {
        runOnMainThread {
            isFullscreen = !isFullscreen
            if (isFullscreen) {
                controlsVisible = false
            }
            isSnapped = false
            updateLayoutParams()
        }
    }

    private fun snapToEdge(forceSnap: Boolean = false) {
        runOnMainThread {
            val params = layoutParams ?: return@runOnMainThread
            val metrics = context.resources.displayMetrics
            val statusBarHeight = getStatusBarHeight()
            if (!isSnapped || forceSnap) {
                isSnapped = true
                lastWindowX = params.x
                lastWindowY = params.y

                val screenWidth = metrics.widthPixels
                val centerX = params.x + params.width / 2
                snappedToRight = centerX >= screenWidth / 2

                val visibleWidthPx = (36 * metrics.density).roundToInt()
                val extraOffscreenWidthPx = (12 * metrics.density).roundToInt()
                val snappedWidthPx = (visibleWidthPx + extraOffscreenWidthPx).coerceAtLeast(1)
                val snappedHeightPx = (48 * metrics.density).roundToInt().coerceAtLeast(1)
                val targetX = if (snappedToRight) {
                    screenWidth - visibleWidthPx
                } else {
                    -extraOffscreenWidthPx
                }
                val maxY = metrics.heightPixels - snappedHeightPx
                val targetY = params.y.coerceIn(statusBarHeight, maxY)
                animateToPosition(targetX, targetY, isSnapping = true)
            }
        }
    }

    private fun animateToDefaultPosition() {
        val metrics = context.resources.displayMetrics
        val statusBarHeight = getStatusBarHeight()
        val params = layoutParams ?: return
        val videoWidth = (lastWindowWidth * 0.4f).toInt()
        val videoHeight = (lastWindowHeight * 0.4f).toInt()
        val restoredWidth = videoWidth + automationPanelWidthPx
        val restoredHeight = videoHeight

        val maxX = metrics.widthPixels - restoredWidth
        val maxY = metrics.heightPixels - restoredHeight

        val targetX = lastWindowX.coerceIn(0, maxX)
        val targetY = lastWindowY.coerceIn(statusBarHeight, maxY)

        animateToPosition(targetX, targetY, isSnapping = false)
    }

    private fun animateToPosition(targetX: Int, targetY: Int, isSnapping: Boolean) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val startX = params.x
        val startY = params.y
        val startWidth = params.width
        val startHeight = params.height
        val metrics = context.resources.displayMetrics
        val visibleWidthPx = (36 * metrics.density).roundToInt()
        val extraOffscreenWidthPx = (12 * metrics.density).roundToInt()
        val snappedWidth = (visibleWidthPx + extraOffscreenWidthPx).coerceAtLeast(1)
        val snappedHeight = (48 * metrics.density).roundToInt().coerceAtLeast(1)
        val (endWidth, endHeight) = if (isSnapping) {
            snappedWidth to snappedHeight
        } else {
            val videoWidth = (lastWindowWidth * 0.4f).toInt()
            val videoHeight = (lastWindowHeight * 0.4f).toInt()
            (videoWidth + automationPanelWidthPx) to videoHeight
        }
        animator?.cancel()
        animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                params.width = (startWidth + (endWidth - startWidth) * fraction).toInt()
                params.height = (startHeight + (endHeight - startHeight) * fraction).toInt()
                try {
                    wm.updateViewLayout(view, params)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            start()
        }
    }

    private fun updateLayoutParams() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val metrics = context.resources.displayMetrics
        val statusBarHeight = getStatusBarHeight()
        if (lastWindowWidth == 0 || lastWindowHeight == 0) {
            lastWindowWidth = metrics.widthPixels
            lastWindowHeight = (metrics.heightPixels - statusBarHeight).coerceAtLeast(1)
            val videoWidth = (lastWindowWidth * 0.4f).toInt()
            val videoHeight = (lastWindowHeight * 0.4f).toInt()
            params.width = videoWidth + automationPanelWidthPx
            params.height = videoHeight
            params.x = ((metrics.widthPixels - params.width) / 2f).toInt()
            params.y = statusBarHeight + ((lastWindowHeight - params.height) / 2f).toInt()
            lastWindowX = params.x
            lastWindowY = params.y
        }
        if (isFullscreen) {
            params.width = metrics.widthPixels
            params.height = metrics.heightPixels
            params.x = 0
            params.y = 0
        } else if (isSnapped) {
            val visibleWidthPx = (36 * metrics.density).roundToInt()
            val extraOffscreenWidthPx = (12 * metrics.density).roundToInt()
            val snappedWidthPx = (visibleWidthPx + extraOffscreenWidthPx).coerceAtLeast(1)
            val snappedHeightPx = (48 * metrics.density).roundToInt().coerceAtLeast(1)
            params.width = snappedWidthPx
            params.height = snappedHeightPx

            val screenWidth = metrics.widthPixels
            val maxY = metrics.heightPixels - snappedHeightPx
            params.y = lastWindowY.coerceIn(statusBarHeight, maxY)
            params.x = if (snappedToRight) {
                screenWidth - visibleWidthPx
            } else {
                -extraOffscreenWidthPx
            }
        } else {
            // Small window: keep the video region at 0.4x of the screen width, and
            // add a fixed-width left control panel. This keeps the video size and
            // aspect ratio identical to the previous behavior.
            val videoWidth = (lastWindowWidth * 0.4f).toInt()
            val videoHeight = (lastWindowHeight * 0.4f).toInt()
            params.width = videoWidth + automationPanelWidthPx
            params.height = videoHeight
            params.x = lastWindowX
            params.y = lastWindowY
        }
        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            AppLogger.e("VirtualDisplayOverlay", "Error updating layout params", e)
        }
    }

    private fun moveWindowBy(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        if (isFullscreen) return
        if (isSnapped) {
            val metrics = context.resources.displayMetrics
            val statusBarHeight = getStatusBarHeight()
            val snappedSize = params.height
            val maxY = metrics.heightPixels - snappedSize
            params.y = (params.y + dy.toInt()).coerceIn(statusBarHeight, maxY)
            val screenWidth = metrics.widthPixels
            val visibleWidthPx = (36 * metrics.density).roundToInt()
            val extraOffscreenWidthPx = (12 * metrics.density).roundToInt()
            params.x = if (snappedToRight) {
                screenWidth - visibleWidthPx
            } else {
                -extraOffscreenWidthPx
            }
            // 保留 lastWindowX 为缩小前的小窗 X，只更新垂直位置用于恢复时的 Y
            lastWindowY = params.y
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                AppLogger.e("VirtualDisplayOverlay", "Error moving snapped overlay via moveWindowBy", e)
            }
            return
        }
        val metrics = context.resources.displayMetrics
        val statusBarHeight = getStatusBarHeight()
        val newX = params.x + dx.toInt()
        val newY = params.y + dy.toInt()

        val maxX = metrics.widthPixels - params.width
        val maxY = metrics.heightPixels - params.height

        params.x = newX.coerceIn(0, maxX)
        params.y = newY.coerceIn(statusBarHeight, maxY)

        lastWindowX = params.x
        lastWindowY = params.y
        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            AppLogger.e("VirtualDisplayOverlay", "Error moving overlay via moveWindowBy", e)
        }
    }

    @Composable
    @OptIn(ExperimentalComposeUiApi::class)
    private fun OverlayCard(id: Int, isFullscreen: Boolean, previewPath: String?, currentAppPackageName: String?) {
        var overlaySize by remember { mutableStateOf(IntSize.Zero) }
        val touchForwardMutex = remember { Mutex() }
        val touchForwardScope = rememberCoroutineScope()
        val snapped = isSnapped

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> overlaySize = size }
                .pointerInput(id, isFullscreen, snapped) {
                    if (snapped) {
                        // Minimized: only drag and tap to restore are available
                        detectDragGestures {
                            change, dragAmount ->
                            change.consume()
                            moveWindowBy(dragAmount.x, dragAmount.y)
                        }
                    } else if (!isFullscreen) {
                        detectDragGestures(
                            onDragStart = { controlsVisible = true },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                moveWindowBy(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                }
                .then(
                    when {
                        snapped -> {
                            Modifier.pointerInput(id, isFullscreen, overlaySize, snapped) {
                                detectTapGestures(
                                    onTap = {
                                        isSnapped = false
                                        animateToDefaultPosition()
                                    }
                                )
                            }
                        }

                        !isFullscreen -> {
                            Modifier.pointerInput(id, isFullscreen, overlaySize, snapped) {
                                detectTapGestures(
                                    onTap = {
                                        controlsVisible = true
                                    }
                                )
                            }
                        }

                        else -> Modifier
                    }
                )
                .clip(RoundedCornerShape(0.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var hasShowerDisplay by remember { mutableStateOf(ShowerController.getVideoSize(agentId) != null) }
                LaunchedEffect(Unit) {
                    var lastReady = hasShowerDisplay
                    while (true) {
                        val ready = ShowerController.getVideoSize(agentId) != null
                        if (lastReady && !ready) {
                            AppLogger.w(
                                "VirtualDisplayOverlay",
                                "OverlayCard: Shower 虚拟屏已关闭/断开，取消自动化任务 agentId=$agentId"
                            )
                            PhoneAgentJobRegistry.cancelAgent(agentId, "Virtual screen closed")
                        }
                        if (hasShowerDisplay != ready) {
                            AppLogger.d(
                                "VirtualDisplayOverlay",
                                "OverlayCard: hasShowerDisplay changed from $hasShowerDisplay to $ready, videoSize=${ShowerController.getVideoSize(agentId)}"
                            )
                            hasShowerDisplay = ready
                        }
                        lastReady = ready
                        delay(500)
                    }
                }
                if (hasShowerDisplay) {
                    val density = LocalDensity.current
                    val remoteVideoSize = ShowerController.getVideoSize(agentId)

                    // Always keep a single ShowerSurfaceView attached; only adjust its layout
                    val videoModifierBase = when {
                        // 全屏模式：保持原来的视频 fillMaxSize 布局
                        isFullscreen -> Modifier.fillMaxSize()
                        // 保持 ShowerSurfaceView 附着但几乎不可见，仅用于维持渲染管线
                        snapped -> Modifier
                            .size(1.dp)
                            .align(if (snappedToRight) Alignment.CenterEnd else Alignment.CenterStart)
                        else -> {
                            // 小窗模式：左侧为固定宽度控制栏，右侧为原始大小的视频区域
                            val videoWidthPx = (overlaySize.width - automationPanelWidthPx).coerceAtLeast(1)
                            val videoWidthDp = with(density) { videoWidthPx.toDp() }
                            Modifier
                                .fillMaxHeight()
                                // Right video region: keep the same size as before (matches 0.4x screen width)
                                .width(videoWidthDp)
                                .align(Alignment.CenterEnd)
                        }
                    }

                    val videoModifier =
                        if (!snapped && !isFullscreen) {
                            videoModifierBase.pointerInput(id, overlaySize) {
                                detectTapGestures(
                                    onTap = {
                                        controlsVisible = true
                                    }
                                )
                            }
                        } else {
                            videoModifierBase
                        }

                    Box(
                        modifier = videoModifier
                    ) {
                        AndroidView(
                            modifier = if (isFullscreen && !snapped) {
                                Modifier
                                    .fillMaxSize()
                                    .pointerInteropFilter { event ->
                                        val action = event.actionMasked
                                        if (event.pointerCount != 1) return@pointerInteropFilter false
                                        if (
                                            action != MotionEvent.ACTION_DOWN &&
                                            action != MotionEvent.ACTION_MOVE &&
                                            action != MotionEvent.ACTION_UP &&
                                            action != MotionEvent.ACTION_CANCEL
                                        ) {
                                            return@pointerInteropFilter false
                                        }

                                        val os = overlaySize
                                        val vs = ShowerController.getVideoSize(agentId) ?: return@pointerInteropFilter false
                                        if (os.width <= 0 || os.height <= 0) return@pointerInteropFilter false

                                        val copied = MotionEvent.obtain(event)
                                        val vw = vs.first
                                        val vh = vs.second
                                        val xPrec = copied.xPrecision * (vw - 1).toFloat() / os.width.toFloat()
                                        val yPrec = copied.yPrecision * (vh - 1).toFloat() / os.height.toFloat()

                                        touchForwardScope.launch(Dispatchers.IO) {
                                            try {
                                                touchForwardMutex.withLock {
                                                    if (action == MotionEvent.ACTION_MOVE) {
                                                        for (i in 0 until copied.historySize) {
                                                            val hx = copied.getHistoricalX(0, i)
                                                            val hy = copied.getHistoricalY(0, i)
                                                            val pt = mapOffsetToRemote(Offset(hx, hy), os, vs) ?: continue
                                                            ShowerController.injectTouchEvent(
                                                                agentId = agentId,
                                                                action = MotionEvent.ACTION_MOVE,
                                                                x = pt.first.toFloat(),
                                                                y = pt.second.toFloat(),
                                                                downTime = copied.downTime,
                                                                eventTime = copied.getHistoricalEventTime(i),
                                                                pressure = copied.getHistoricalPressure(0, i),
                                                                size = copied.getHistoricalSize(0, i),
                                                                metaState = copied.metaState,
                                                                xPrecision = xPrec,
                                                                yPrecision = yPrec,
                                                                deviceId = copied.deviceId,
                                                                edgeFlags = copied.edgeFlags
                                                            )
                                                        }
                                                    }

                                                    val cx = copied.getX(0)
                                                    val cy = copied.getY(0)
                                                    val pt = mapOffsetToRemote(Offset(cx, cy), os, vs)
                                                    if (pt != null) {
                                                        ShowerController.injectTouchEvent(
                                                            agentId = agentId,
                                                            action = action,
                                                            x = pt.first.toFloat(),
                                                            y = pt.second.toFloat(),
                                                            downTime = copied.downTime,
                                                            eventTime = copied.eventTime,
                                                            pressure = copied.getPressure(0),
                                                            size = copied.getSize(0),
                                                            metaState = copied.metaState,
                                                            xPrecision = xPrec,
                                                            yPrecision = yPrec,
                                                            deviceId = copied.deviceId,
                                                            edgeFlags = copied.edgeFlags
                                                        )
                                                    }
                                                }
                                            } finally {
                                                copied.recycle()
                                            }
                                        }

                                        true
                                    }
                            } else {
                                Modifier.fillMaxSize()
                            },
                            factory = { ctx ->
                                ShowerSurfaceView(ctx).also { view ->
                                    try {
                                        view.bindController(ShowerController.getInstance(agentId))
                                    } catch (_: Exception) {
                                    }
                                    surfaceView = view
                                }
                            },
                            update = { view ->
                                surfaceView = view
                                try {
                                    view.bindController(ShowerController.getInstance(agentId))
                                } catch (_: Exception) {
                                }
                            }
                        )
                        val feedback = actionFeedback
                        if (feedback != null && remoteVideoSize != null && !snapped) {
                            AutomationActionFeedbackIndicator(
                                feedback = feedback,
                                remoteWidth = remoteVideoSize.first,
                                remoteHeight = remoteVideoSize.second,
                                density = density
                            )
                        }
                        if (rainbowBorderVisible && !snapped) {
                            RainbowStatusBorderOverlay()
                        }

                        if (!snapped) {
                            // Auto-hide controls in both fullscreen and small-window modes
                            LaunchedEffect(controlsVisible) {
                                if (controlsVisible) {
                                    delay(3000)
                                    controlsVisible = false
                                }
                            }

                            if (isFullscreen) {
                                // Fullscreen: top-right Windows-like controls over the video only
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // Minimize (映射到贴边，类似最小化到侧边)
                                        IconButton(
                                            onClick = {
                                                toggleFullScreen()
                                                snapToEdge()
                                            },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Minimize,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_minimize),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                        // Restore (退出全屏)
                                        IconButton(
                                            onClick = { toggleFullScreen() },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FullscreenExit,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_exit_fullscreen),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                        // Close
                                        IconButton(
                                            onClick = { hide(cancelAutomation = true) },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_close),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Small window: dim and show center controls only over the video region
                                if (controlsVisible) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                                    )
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(onClick = { snapToEdge() }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Minimize,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_minimize_to_ball),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        IconButton(onClick = { toggleFullScreen() }) {
                                            Icon(
                                                imageVector = Icons.Filled.Fullscreen,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_toggle_fullscreen),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        IconButton(onClick = { hide(cancelAutomation = true) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_close),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (snapped) {
                        val handleShape = RoundedCornerShape(12.dp)
                        val arrowIcon = if (snappedToRight) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight

                        val density = LocalDensity.current
                        val iconSizeDp = 30.dp
                        val iconShape = RoundedCornerShape(8.dp)
                        val offscreenPaddingDp = 12.dp
                        val iconSizePx = with(density) { iconSizeDp.roundToPx().coerceAtLeast(1) }
                        val appIconBitmap: ImageBitmap? by produceState<ImageBitmap?>(
                            initialValue = null,
                            key1 = currentAppPackageName,
                            key2 = iconSizePx
                        ) {
                            val pkg = currentAppPackageName
                            value = if (pkg.isNullOrBlank()) {
                                null
                            } else {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val drawable = context.packageManager.getApplicationIcon(pkg)
                                        drawable.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = Color.Gray.copy(alpha = 0.85f),
                                    shape = handleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val visibleRegionModifier = if (snappedToRight) {
                                Modifier.fillMaxSize().padding(end = offscreenPaddingDp)
                            } else {
                                Modifier.fillMaxSize().padding(start = offscreenPaddingDp)
                            }

                            Box(
                                modifier = visibleRegionModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                if (appIconBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(iconSizeDp)
                                            .clip(iconShape)
                                    ) {
                                        Image(
                                            bitmap = appIconBitmap!!,
                                            contentDescription = stringResource(R.string.vd_overlay_a11y_restore),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(iconSizeDp)
                                            .clip(iconShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = arrowIcon,
                                            contentDescription = stringResource(R.string.vd_overlay_a11y_restore),
                                            modifier = Modifier.fillMaxSize(),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (!isFullscreen) {
                            // 小窗模式：左侧为固定宽度控制栏，右侧为原始大小的视频区域
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(automationPanelWidthDp)
                                    .align(Alignment.CenterStart)
                            ) {
                                if (automationVisible) {
                                    val step = automationCurrentStep
                                    val total = automationTotalSteps
                                    if (step != null && total != null) {
                                        AutomationControlBar(
                                            currentStep = step,
                                            totalSteps = total,
                                            isPaused = automationIsPaused,
                                            onTogglePauseResume = { newPaused ->
                                                automationIsPaused = newPaused
                                                automationOnTogglePauseResume?.invoke(newPaused)
                                            },
                                            onExit = { automationOnExit?.invoke() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    AppLogger.d(
                        "VirtualDisplayOverlay",
                        "OverlayCard: Shower 虚拟屏尚未就绪, id=$id, hasShowerDisplay=$hasShowerDisplay, videoSize=${ShowerController.getVideoSize(agentId)}"
                    )
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!snapped) {
                            LaunchedEffect(controlsVisible) {
                                if (controlsVisible) {
                                    delay(3000)
                                    controlsVisible = false
                                }
                            }
                        }

                        if (snapped) {
                            val handleShape = RoundedCornerShape(12.dp)
                            val arrowIcon = if (snappedToRight) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = Color.Gray.copy(alpha = 0.85f),
                                        shape = handleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = arrowIcon,
                                    contentDescription = stringResource(R.string.vd_overlay_a11y_restore),
                                    modifier = Modifier.size(30.dp),
                                    tint = Color.White
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.virtual_display_not_ready),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }

                            if (!isFullscreen) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(automationPanelWidthDp)
                                        .align(Alignment.CenterStart)
                                ) {
                                    if (automationVisible) {
                                        val step = automationCurrentStep
                                        val total = automationTotalSteps
                                        if (step != null && total != null) {
                                            AutomationControlBar(
                                                currentStep = step,
                                                totalSteps = total,
                                                isPaused = automationIsPaused,
                                                onTogglePauseResume = { newPaused ->
                                                    automationIsPaused = newPaused
                                                    automationOnTogglePauseResume?.invoke(newPaused)
                                                },
                                                onExit = { automationOnExit?.invoke() }
                                            )
                                        }
                                    }
                                }
                            }

                            if (isFullscreen) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                toggleFullScreen()
                                                snapToEdge()
                                            },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Minimize,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_minimize),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                        IconButton(
                                            onClick = { toggleFullScreen() },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FullscreenExit,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_exit_fullscreen),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                        IconButton(
                                            onClick = { hide(cancelAutomation = true) },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_close),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.Black.copy(alpha = 0.35f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = { snapToEdge() },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Minimize,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_minimize),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                        IconButton(
                                            onClick = { toggleFullScreen() },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Fullscreen,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_toggle_fullscreen),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                        IconButton(
                                            onClick = { hide(cancelAutomation = true) },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = Color.White,
                                                containerColor = Color.Transparent
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_close),
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }

                                if (controlsVisible) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                                    )
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(onClick = { snapToEdge() }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Minimize,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_minimize_to_ball),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        IconButton(onClick = { toggleFullScreen() }) {
                                            Icon(
                                                imageVector = Icons.Filled.Fullscreen,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_toggle_fullscreen),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        IconButton(onClick = { hide(cancelAutomation = true) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.vd_overlay_a11y_close),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AutomationControlBar(
        currentStep: Int,
        totalSteps: Int,
        isPaused: Boolean,
        onTogglePauseResume: (Boolean) -> Unit,
        onExit: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 只保留简单的步骤进度文案，例如 "3/20"
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$currentStep/$totalSteps",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // 垂直排列：上面是暂停/继续，下面是退出任务的 X
                IconButton(onClick = { onTogglePauseResume(!isPaused) }) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) {
                            stringResource(R.string.vd_overlay_a11y_resume_automation)
                        } else {
                            stringResource(R.string.vd_overlay_a11y_pause_automation)
                        },
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.vd_overlay_a11y_stop_automation),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}

@Composable
private fun AutomationActionFeedbackIndicator(
    feedback: AutomationActionFeedback,
    remoteWidth: Int,
    remoteHeight: Int,
    density: androidx.compose.ui.unit.Density
) {
    val progress = remember(feedback.id) { Animatable(0f) }

    LaunchedEffect(feedback.id) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRemoteX = (remoteWidth - 1).coerceAtLeast(1).toFloat()
        val maxRemoteY = (remoteHeight - 1).coerceAtLeast(1).toFloat()

        fun mapPoint(x: Int, y: Int): Offset {
            return Offset(
                x = size.width * x.coerceIn(0, maxRemoteX.toInt()) / maxRemoteX,
                y = size.height * y.coerceIn(0, maxRemoteY.toInt()) / maxRemoteY
            )
        }

        val start = mapPoint(feedback.startX, feedback.startY)
        val end = mapPoint(feedback.endX, feedback.endY)
        val p = progress.value
        val alpha = (1f - p).coerceIn(0f, 1f)

        when (feedback.type) {
            AutomationActionFeedback.Type.TAP -> {
                drawCircle(
                    color = Color(0xFF00E5FF),
                    center = start,
                    radius = with(density) { (14.dp + 44.dp * p).toPx() },
                    style = Stroke(width = with(density) { (5.dp * alpha).toPx() }),
                    alpha = alpha
                )
                drawCircle(
                    color = Color.White,
                    center = start,
                    radius = with(density) { 7.dp.toPx() },
                    alpha = alpha.coerceAtLeast(0.25f)
                )
            }

            AutomationActionFeedback.Type.SWIPE -> {
                val current = Offset(
                    x = start.x + (end.x - start.x) * (p / 0.72f).coerceIn(0f, 1f),
                    y = start.y + (end.y - start.y) * (p / 0.72f).coerceIn(0f, 1f)
                )
                val strokeWidth = with(density) { 7.dp.toPx() }
                drawLine(
                    color = Color(0xFFFF9800),
                    start = start,
                    end = current,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                    alpha = alpha
                )
                drawCircle(
                    color = Color(0xFFFFE0B2),
                    center = current,
                    radius = with(density) { 12.dp.toPx() },
                    alpha = alpha
                )
                val angle = atan2(end.y - start.y, end.x - start.x)
                val arrowLength = with(density) { 22.dp.toPx() }
                val arrowAngle = 0.55f
                val left = Offset(
                    current.x - arrowLength * cos(angle - arrowAngle),
                    current.y - arrowLength * sin(angle - arrowAngle)
                )
                val right = Offset(
                    current.x - arrowLength * cos(angle + arrowAngle),
                    current.y - arrowLength * sin(angle + arrowAngle)
                )
                drawLine(
                    color = Color(0xFFFF9800),
                    start = current,
                    end = left,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                    alpha = alpha
                )
                drawLine(
                    color = Color(0xFFFF9800),
                    start = current,
                    end = right,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                    alpha = alpha
                )
            }
        }
    }
}

@Composable
private fun RainbowStatusBorderOverlay() {
    RainbowBorderOverlay()
}
