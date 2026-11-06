package com.ai.assistance.operit.ui.common.displays

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.ai.assistance.operit.R
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PhoneAgent UI 自动化进度悬浮卡片。
 * 在屏幕底部显示当前步骤进度、状态文案，以及一个可点击的取消按钮。
 */
class UIAutomationProgressOverlay private constructor(private val context: Context) {
    private val TAG = "UIAutomationProgressOverlay"

    companion object {
        @Volatile
        private var instance: UIAutomationProgressOverlay? = null

        fun getInstance(context: Context): UIAutomationProgressOverlay {
            return instance ?: synchronized(this) {
                instance ?: UIAutomationProgressOverlay(context.applicationContext).also { instance = it }
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var borderView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var borderLayoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    data class ProgressInfo(
        val currentStep: Int,
        val totalSteps: Int,
        val statusText: String
    )

    private var progressInfo by mutableStateOf<ProgressInfo?>(null)
    private var isPaused by mutableStateOf(false)
    private var cancelCallback: (() -> Unit)? = null
    private var takeOverToggleCallback: ((Boolean) -> Unit)? = null
    private var borderEnabled = false

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post {
                try {
                    action()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing on main thread", e)
                }
            }
        }
    }

    private fun ensureOverlay() {
        try {
            ensureOverlayInfrastructure()

            if (overlayView == null) {
                val params = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    // 不获取输入焦点，但允许点击卡片区域
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = (16 * context.resources.displayMetrics.density).toInt()
                }

                layoutParams = params

                overlayView = ComposeView(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)

                    setContent {
                        val info = progressInfo
                        val paused = isPaused
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (info != null) {
                                ProgressCard(
                                    info = info,
                                    isPaused = paused,
                                    onCancel = { cancelCallback?.invoke() },
                                    onToggleTakeOver = { newPaused ->
                                        isPaused = newPaused
                                        takeOverToggleCallback?.invoke(newPaused)
                                    },
                                    onDragBy = { dy -> moveOverlayBy(dy) }
                                )
                            }
                        }
                    }
                }

                windowManager?.addView(overlayView, params)
            }

            if (borderEnabled) {
                ensureBorderOverlay()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating UIAutomationProgressOverlay", e)
            overlayView = null
            borderView = null
            lifecycleOwner = null
            windowManager = null
        }
    }

    private fun ensureOverlayInfrastructure() {
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        if (lifecycleOwner == null) {
            lifecycleOwner = ServiceLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }
    }

    private fun ensureBorderOverlay() {
        if (borderView != null) return

        val wm = windowManager ?: return
        ensureOverlayInfrastructure()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = automationPassthroughAlpha(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        borderLayoutParams = params
        borderView = ComposeView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FullscreenRainbowBorder()
            }
            visibility = if (progressInfo != null) View.VISIBLE else View.GONE
            alpha = if (progressInfo != null) 1f else 0f
        }

        wm.addView(borderView, params)
    }

    fun setBorderEnabled(enabled: Boolean) {
        runOnMainThread {
            borderEnabled = enabled
            if (enabled) {
                try {
                    ensureOverlay()
                    borderView?.visibility = if (progressInfo != null) View.VISIBLE else View.GONE
                    borderView?.alpha = if (progressInfo != null) 1f else 0f
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error enabling rainbow border overlay", e)
                }
            } else {
                borderView?.let { view ->
                    try {
                        windowManager?.removeView(view)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error removing rainbow border overlay", e)
                    }
                }
                borderView = null
                borderLayoutParams = null
            }
        }
    }

    fun show(
        totalSteps: Int,
        initialStatus: String,
        onCancel: () -> Unit,
        onToggleTakeOver: (Boolean) -> Unit
    ) {
        runOnMainThread {
            ensureOverlay()
            cancelCallback = onCancel
            takeOverToggleCallback = onToggleTakeOver
            isPaused = false
            progressInfo = ProgressInfo(currentStep = 1, totalSteps = totalSteps, statusText = initialStatus)
            if (borderEnabled) {
                ensureBorderOverlay()
                borderView?.visibility = View.VISIBLE
                borderView?.alpha = 1f
            }
            overlayView?.visibility = View.VISIBLE
            overlayView?.alpha = 1f
        }
    }

    fun updateProgress(currentStep: Int, totalSteps: Int, statusText: String) {
        runOnMainThread {
            if (overlayView == null) return@runOnMainThread
            val safeCurrent = if (currentStep <= 0) 1 else currentStep
            progressInfo = ProgressInfo(currentStep = safeCurrent, totalSteps = totalSteps, statusText = statusText)
        }
    }

    fun hide() {
        runOnMainThread {
            try {
                progressInfo = null
                cancelCallback = null
                takeOverToggleCallback = null
                isPaused = false

                overlayView?.let { view ->
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

                    try {
                        windowManager?.removeView(view)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error removing progress overlay view", e)
                    }
                }

                borderView?.let { view ->
                    try {
                        windowManager?.removeView(view)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error removing progress border overlay view", e)
                    }
                }

                overlayView = null
                borderView = null
                lifecycleOwner = null
                windowManager = null
                layoutParams = null
                borderLayoutParams = null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error hiding UIAutomationProgressOverlay", e)
            }
        }
    }

    /** Keep the current phase visible while allowing an injected gesture through the card. */
    suspend fun setInputPassthrough(enabled: Boolean) = withContext(Dispatchers.Main.immediate) {
        val view = overlayView ?: return@withContext
        val params = layoutParams ?: return@withContext
        params.flags = if (enabled) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.alpha = if (enabled) automationPassthroughAlpha(context) else 1f
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating progress input passthrough", e)
        }
    }

    /** Screenshot capture hides the card; the border represents the entire active tool call. */
    suspend fun setOverlayVisible(visible: Boolean) {
        try {
            withContext(Dispatchers.Main) {
                val view = overlayView ?: return@withContext
                val wm = windowManager ?: return@withContext
                val params = (view.layoutParams as? WindowManager.LayoutParams) ?: layoutParams ?: return@withContext

                val shouldHide = !visible || progressInfo == null

                if (shouldHide) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }

                view.alpha = if (shouldHide) 0f else 1f
                view.visibility = if (shouldHide) View.GONE else View.VISIBLE

                try {
                    wm.updateViewLayout(view, params)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error updating overlay layout", e)
                }

                layoutParams = params
                borderView?.let { border ->
                    val active = borderEnabled && progressInfo != null
                    border.alpha = if (active) 1f else 0f
                    border.visibility = if (active) View.VISIBLE else View.GONE
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error setting overlay visibility", e)
        }
    }

    private fun moveOverlayBy(dy: Float) {
        runOnMainThread {
            val wm = windowManager ?: return@runOnMainThread
            val view = overlayView ?: return@runOnMainThread
            val params = (view.layoutParams as? WindowManager.LayoutParams) ?: layoutParams ?: return@runOnMainThread

            val displayMetrics = context.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val viewHeight = if (view.height > 0) view.height else params.height
            if (viewHeight <= 0) return@runOnMainThread

            val statusBarHeight = getStatusBarHeight()
            val bottomMarginPx = (16 * displayMetrics.density).toInt()

            val currentTop = screenHeight - params.y - viewHeight
            var newTop = currentTop + dy.toInt()
            val minTop = statusBarHeight
            val maxTop = (screenHeight - viewHeight - bottomMarginPx).coerceAtLeast(minTop)
            newTop = newTop.coerceIn(minTop, maxTop)

            params.y = screenHeight - newTop - viewHeight

            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error moving progress overlay", e)
            }

            layoutParams = params
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}

@Composable
private fun FullscreenRainbowBorder() {
    RainbowBorderOverlay()
}

@Composable
private fun ProgressCard(
    info: UIAutomationProgressOverlay.ProgressInfo,
    isPaused: Boolean,
    onCancel: () -> Unit,
    onToggleTakeOver: (Boolean) -> Unit,
    onDragBy: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragBy(dragAmount.y)
                    }
                )
            }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Smartphone,
                        contentDescription = stringResource(R.string.common_phone_agent),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = statusIconFor(info.statusText),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${stringResource(R.string.common_phone_agent)} ${info.currentStep}/${info.totalSteps}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = info.statusText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { onToggleTakeOver(!isPaused) }) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) stringResource(R.string.common_resume_agent) else stringResource(R.string.common_take_over),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.common_cancel),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun statusIconFor(statusText: String): ImageVector {
    val text = statusText.lowercase()
    return when {
        text.contains("execut") -> Icons.Filled.PlayArrow
        text.contains("done") || text.contains("success") -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Info
    }
}
