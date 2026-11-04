package com.ai.assistance.operit.ui.common.displays

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.ai.assistance.operit.util.logging.AppLogger
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import java.util.UUID
import kotlinx.coroutines.delay

/**
 * 显示UI操作视觉反馈的悬浮窗
 * 可以显示点击、滑动、文本输入等操作的位置指示器
 * 使用单例模式避免多实例创建导致窗口叠加
 */
class UIOperationOverlay private constructor(private val context: Context) {
    private val TAG = "UIOperationOverlay"
    
    companion object {
        @Volatile
        private var instance: UIOperationOverlay? = null
        
        fun getInstance(context: Context): UIOperationOverlay {
            return instance ?: synchronized(this) {
                instance ?: UIOperationOverlay(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    
    // 为每个操作定义一个带唯一ID的数据类
    data class TapEvent(val x: Int, val y: Int, val id: UUID = UUID.randomUUID())
    data class SwipeEvent(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val id: UUID = UUID.randomUUID())
    data class TextInputEvent(val x: Int, val y: Int, val text: String, val id: UUID = UUID.randomUUID())
    
    // UI状态：使用列表来管理可能同时发生的多个视觉效果
    private val tapEvents = mutableStateListOf<TapEvent>()
    private val swipeEvents = mutableStateListOf<SwipeEvent>()
    private val textInputEvents = mutableStateListOf<TextInputEvent>()
    
    // 自动隐藏计时器
    private val handler = Handler(Looper.getMainLooper())
    
    // 自动清理延迟（毫秒）
    private val AUTO_CLEANUP_DELAY_MS = 500L

    // 隐藏悬浮窗前的额外展示时间（毫秒），用于让动画有机会完整显示
    private val HIDE_DELAY_MS = 600L
    
    // 操作类型
    sealed class OperationType {
        object None : OperationType()
        object Tap : OperationType()
        object Swipe : OperationType()
        object TextInput : OperationType()
    }
    
    private val statusBarHeight: Int by lazy {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }
    
    /**
     * 检查是否有悬浮窗权限
     */
    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission() {
        if (!hasOverlayPermission()) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error requesting overlay permission", e)
            }
        }
    }
    
    /**
     * 确保在主线程上执行操作
     */
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post {
                try {
                    action()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing action on main thread", e)
                }
            }
        }
    }
    
    /**
     * 初始化和显示悬浮窗
     */
    private fun initOverlay() {
        if (overlayView != null) return
        
        if (!hasOverlayPermission()) {
            AppLogger.e(TAG, "Cannot show overlay without permission")
            requestOverlayPermission()
            return
        }
        
        // 确保在主线程上初始化UI组件
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { initOverlay() }
            return
        }
        
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                // 关键flags：彻底不可聚焦，不阻挡触摸事件传递到下层应用
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                // 设置足够高的层级确保可见性
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    windowAnimations = android.R.style.Animation_Toast
                }
            }
            
            lifecycleOwner = ServiceLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            
            overlayView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                
                // 设置生命周期所有者
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                
                setContent {
                    MaterialTheme {
                        OperationFeedbackContent(
                            tapEvents = tapEvents,
                            swipeEvents = swipeEvents,
                            textInputEvents = textInputEvents
                        )
                    }
                }
            }
            
            windowManager?.addView(overlayView, params)
            AppLogger.d(TAG, "Overlay view added successfully")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error adding overlay view", e)
            // 清理资源
            lifecycleOwner = null
            overlayView = null
            windowManager = null
        }
    }
    
    /**
     * 显示点击操作反馈
     */
    fun showTap(x: Int, y: Int, autoHideDelayMs: Long = 1500) {
        AppLogger.d(TAG, "Showing tap at ($x, $y)")
        
        val newTapEvent = TapEvent(x, y - statusBarHeight)
        
        runOnMainThread {
            initOverlay()
            tapEvents.add(newTapEvent)
            // 为这个特定的事件安排移除
            handler.postDelayed({ 
                tapEvents.remove(newTapEvent)
                scheduleAutoCleanup()
            }, autoHideDelayMs)
        }
    }
    
    /**
     * 显示滑动操作反馈
     */
    fun showSwipe(startX: Int, startY: Int, endX: Int, endY: Int, autoHideDelayMs: Long = 1500) {
        AppLogger.d(TAG, "Showing swipe from ($startX, $startY) to ($endX, $endY)")
        
        val newSwipeEvent = SwipeEvent(startX, startY - statusBarHeight, endX, endY - statusBarHeight)

        runOnMainThread {
            initOverlay()
            swipeEvents.add(newSwipeEvent)
            handler.postDelayed({ 
                swipeEvents.remove(newSwipeEvent)
                scheduleAutoCleanup()
            }, autoHideDelayMs)
        }
    }
    
    /**
     * 显示文本输入操作反馈
     */
    fun showTextInput(x: Int, y: Int, text: String, autoHideDelayMs: Long = 2000) {
        AppLogger.d(TAG, "Showing text input at ($x, $y): $text")
        
        val newTextInputEvent = TextInputEvent(x, y - statusBarHeight, text)
        
        runOnMainThread {
            initOverlay()
            textInputEvents.add(newTextInputEvent)
            handler.postDelayed({ 
                textInputEvents.remove(newTextInputEvent)
                scheduleAutoCleanup()
            }, autoHideDelayMs)
        }
    }
    
    /**
     * 安排自动清理：如果所有事件列表都为空，则延迟移除窗口
     */
    private fun scheduleAutoCleanup() {
        handler.postDelayed({
            if (tapEvents.isEmpty() && swipeEvents.isEmpty() && textInputEvents.isEmpty()) {
                AppLogger.d(TAG, "All events cleared, auto-cleaning overlay window")
                hide()
            }
        }, AUTO_CLEANUP_DELAY_MS)
    }
    
    /**
     * 隐藏所有反馈悬浮窗（延迟一点时间，让动画有机会显示）
     */
    fun hide() {
        runOnMainThread {
            handler.postDelayed({
                dismissOverlayNow()
            }, HIDE_DELAY_MS)
        }
    }

    /**
     * 立即移除反馈悬浮窗，避免下一次注入点击/滑动时被旧反馈层遮挡。
     */
    fun hideImmediately() {
        runOnMainThread {
            dismissOverlayNow()
        }
    }

    private fun dismissOverlayNow() {
        try {
            // 清除所有待处理的移除任务
            handler.removeCallbacksAndMessages(null)
            // 清空所有事件列表
            tapEvents.clear()
            swipeEvents.clear()
            textInputEvents.clear()

            // 彻底移除视图
            if (overlayView != null) {
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error removing overlay view", e)
                }

                overlayView = null
                lifecycleOwner = null
                windowManager = null
                AppLogger.d(TAG, "Overlay view dismissed")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error dismissing overlay view", e)
        }
    }
}

/**
 * 操作反馈UI内容
 */
@Composable
private fun OperationFeedbackContent(
    tapEvents: List<UIOperationOverlay.TapEvent>,
    swipeEvents: List<UIOperationOverlay.SwipeEvent>,
    textInputEvents: List<UIOperationOverlay.TextInputEvent>
) {
    // 获取屏幕密度用于坐标转换
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // 添加一个半透明背景以确保内容可见
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            tapEvents.forEach { event ->
                key(event.id) {
                    TapIndicator(event.x, event.y, density)
                }
            }

            swipeEvents.forEach { event ->
                key(event.id) {
                    SwipeIndicator(event.startX, event.startY, event.endX, event.endY, density)
                }
            }

            textInputEvents.forEach { event ->
                key(event.id) {
                    TextInputIndicator(event.x, event.y, event.text, density)
                }
            }
        }
    }
}

/**
 * 点击指示器UI
 */
@Composable
private fun TapIndicator(
    x: Int, 
    y: Int,
    density: androidx.compose.ui.unit.Density
) {
    val progress = remember { Animatable(0f) }

    // This LaunchedEffect will run once when the composable enters the composition.
    // The `key` in the parent composable ensures this is a new composition each time.
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    // 波纹扩散并消失
    val radius = lerp(10.dp, 50.dp, p)
    val alpha = (1f - p).coerceIn(0f, 1f)
    val strokeWidth = lerp(6.dp, 0.dp, p)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = x.toFloat()
        val centerY = y.toFloat()

        // 扩散的涟漪效果
        drawCircle(
            color = Color(0xFF00BCD4), // 鲜艳的青色
            radius = with(density) { radius.toPx() },
            center = Offset(centerX, centerY),
            style = Stroke(width = with(density) { strokeWidth.toPx() }),
            alpha = alpha
        )
    }
}

/**
 * 滑动指示器UI
 */
@Composable
private fun SwipeIndicator(
    startX: Int, 
    startY: Int, 
    endX: Int, 
    endY: Int,
    density: androidx.compose.ui.unit.Density
) {
    val progress = remember { Animatable(0f) }

    // 使用LaunchedEffect来驱动一个基于Animatable的、可控的动画
    // `key(Unit)` 确保动画只在 Composable 首次进入组合时运行一次
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    // 将整个动画分为两个阶段：
    // 1. 滑动动画（p: 0.0 -> 0.8），彗星沿轨迹移动
    val swipeProgress = (p / 0.8f).coerceIn(0f, 1f)
    // 2. 淡出动画（p: 0.6 -> 1.0），整体效果逐渐消失
    val alpha = ((1f - p) / 0.4f).coerceIn(0f, 1f)
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        with(density) {
            val startOffset = Offset(startX.toFloat(), startY.toFloat())

            // 计算彗星头的当前位置
            val currentOffset = Offset(
                x = startX + (endX - startX) * swipeProgress,
                y = startY + (endY - startY) * swipeProgress
            )
            
            // 1. 绘制彗星的尾巴（轨迹）
            drawLine(
                color = Color(0xFFFFA726), // 鲜艳的橙色
                start = startOffset,
                end = currentOffset,
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round,
                alpha = alpha
            )
            
            // 2. 绘制彗星的头部
            drawCircle(
                color = Color(0xFFFFE0B2), // 更亮的头部颜色
                radius = 15.dp.toPx(),
                center = currentOffset,
                alpha = alpha
            )
            
            // 3. 绘制一个在滑动开始时可见，然后迅速消失的起点光环
            drawCircle(
                color = Color(0xFFEF6C00),
                radius = 12.dp.toPx(),
                center = startOffset,
                alpha = alpha * (1 - swipeProgress) // 随着滑动进程快速淡出
            )
        }
    }
}

/**
 * 文本输入指示器UI
 */
@Composable
private fun TextInputIndicator(
    x: Int, 
    y: Int, 
    text: String,
    density: androidx.compose.ui.unit.Density
) {
    // 使用状态动画
    val fadeIn by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "fadeIn"
    )
    
    // 使用无限循环动画实现呼吸效果
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // 绘制气泡背景和指向箭头，使用像素坐标
    Canvas(modifier = Modifier.fillMaxSize()) {
        with(density) {
            val centerX = x.toFloat()
            val centerY = y.toFloat() - 60.dp.toPx() // 稍微上移一点
            val bubbleWidth = 220.dp.toPx()
            val bubbleHeight = 50.dp.toPx()
            val cornerRadius = 12.dp.toPx()
            
            // 1. 绘制背景阴影提升可见性
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(centerX - bubbleWidth/2 + 4.dp.toPx(), 
                               centerY - bubbleHeight/2 + 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                alpha = fadeIn * pulseAlpha
            )
            
            // 2. 绘制气泡背景
            drawRoundRect(
                color = Color(0xEE000000), // 更不透明的黑色
                topLeft = Offset(centerX - bubbleWidth/2, centerY - bubbleHeight/2),
                size = androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                alpha = fadeIn * pulseAlpha
            )
            
            // 3. 绘制底部箭头指向输入位置
            val arrowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX, centerY + bubbleHeight/2 + 10.dp.toPx())
                lineTo(centerX - 10.dp.toPx(), centerY + bubbleHeight/2)
                lineTo(centerX + 10.dp.toPx(), centerY + bubbleHeight/2)
                close()
            }
            
            drawPath(
                path = arrowPath,
                color = Color(0xEE000000),
                alpha = fadeIn * pulseAlpha
            )
            
            // 4. 绘制亮边框增加可见性
            drawRoundRect(
                color = Color(0x77FFFFFF),
                topLeft = Offset(centerX - bubbleWidth/2, centerY - bubbleHeight/2),
                size = androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                style = Stroke(width = 1.5.dp.toPx()),
                alpha = fadeIn * pulseAlpha
            )
        }
    }
    
    // 单独绘制文本内容，位置精确调整
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        with(density) {
            val bubbleWidth = 220.dp.toPx()
            val targetY = y.toFloat() - 85.dp.toPx()
            
            // 使用精确的像素计算，然后转换为dp
            Text(
                text = "\"$text\"",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(
                        (x.toFloat() - bubbleWidth/2).toDp(),
                        targetY.toDp()
                    )
                    .width(bubbleWidth.toDp())
                    .alpha(fadeIn * pulseAlpha),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
