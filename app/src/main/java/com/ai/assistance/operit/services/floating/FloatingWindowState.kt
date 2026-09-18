package com.ai.assistance.operit.services.floating

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatingMode

class FloatingWindowState(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
    private val screenWidthDp: Dp
    private val screenHeightDp: Dp
    private val maxWindowWidth: Float
    private val maxWindowHeight: Float

    // Window position
    var x: Int = 200
    var y: Int = 200

    // Window size
    val windowWidth = mutableStateOf(300.dp)
    val windowHeight = mutableStateOf(400.dp)
    val windowScale = mutableStateOf(0.8f)
    var lastWindowScale: Float = 0.8f

    // Mode state
    val currentMode = mutableStateOf(FloatingMode.WINDOW)
    var previousMode: FloatingMode = FloatingMode.WINDOW
    val ballSize = mutableStateOf(60.dp)
    val isAtEdge = mutableStateOf(false)

    // DragonBones pet mode lock state
    var isPetModeLocked = mutableStateOf(false)

    // Transition state
    var lastWindowPositionX: Int = 0
    var lastWindowPositionY: Int = 0
    var lastBallPositionX: Int = 0
    var lastBallPositionY: Int = 0
    var isTransitioning = false
    val transitionDebounceTime = 500L // 防抖时间
    
    // Ball explosion animation state
    val ballExploding = mutableStateOf(false)

    // Whether system-level cross-window blur is actually active for fullscreen
    val fullscreenSystemBlurActive = mutableStateOf(false)

    init {
        val displayMetrics = context.resources.displayMetrics
        screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).dp
        screenHeightDp = (displayMetrics.heightPixels / displayMetrics.density).dp
        // 小屏或分屏下 80% 屏幕尺寸可能小于窗口最小尺寸，读写必须使用合法的同一范围。
        maxWindowWidth = (screenWidthDp.value * 0.8f).coerceAtLeast(200f)
        maxWindowHeight = (screenHeightDp.value * 0.8f).coerceAtLeast(250f)
        restoreState()
    }

    fun saveState() {
        // 拒绝将非法状态持久化，否则下一次启动会再次把 NaN 传入布局测量。
        require(windowWidth.value.value.isFinite()) { "Non-finite window_width" }
        require(windowHeight.value.value.isFinite()) { "Non-finite window_height" }
        require(windowScale.value.isFinite()) { "Non-finite window_scale" }
        require(lastWindowScale.isFinite()) { "Non-finite last_window_scale" }
        prefs.edit().apply {
            putInt("window_x", x)
            putInt("window_y", y)
            putFloat(
                "window_width",
                windowWidth.value.value.coerceIn(200f, maxWindowWidth)
            )
            putFloat(
                "window_height",
                windowHeight.value.value.coerceIn(250f, maxWindowHeight)
            )
            putString("current_mode", currentMode.value.name)
            putString("previous_mode", previousMode.name)
            putFloat("window_scale", windowScale.value.coerceIn(0.3f, 1.0f))
            putFloat("last_window_scale", lastWindowScale.coerceIn(0.3f, 1.0f))
            apply()
        }
    }

    fun restoreState() {
        val defaultX = 200
        val defaultY = 200
        x = prefs.getInt("window_x", defaultX)
        y = prefs.getInt("window_y", defaultY)

        val defaultWidth = (screenWidthDp.value * 0.8f).coerceAtLeast(200f)
        val defaultHeight = (screenHeightDp.value * 0.5f).coerceAtLeast(250f)
        // coerceIn 不会拒绝 NaN；明确拒绝非法配置，让服务记录原因并终止启动，
        // 避免将其交给 Compose 测量后产生进程级未捕获异常。
        val storedWidth = prefs.getFloat("window_width", defaultWidth)
        val storedHeight = prefs.getFloat("window_height", defaultHeight)
        require(storedWidth.isFinite()) { "Non-finite stored window_width: $storedWidth" }
        require(storedHeight.isFinite()) { "Non-finite stored window_height: $storedHeight" }
        windowWidth.value = storedWidth.coerceIn(200f, maxWindowWidth).dp
        windowHeight.value = storedHeight.coerceIn(250f, maxWindowHeight).dp

        val modeName = prefs.getString("current_mode", FloatingMode.WINDOW.name)
        currentMode.value = try {
            FloatingMode.valueOf(modeName ?: FloatingMode.WINDOW.name)
        } catch (_: Exception) {
            FloatingMode.WINDOW
        }

        val prevModeName = prefs.getString("previous_mode", FloatingMode.WINDOW.name)
        previousMode = try {
            FloatingMode.valueOf(prevModeName ?: FloatingMode.WINDOW.name)
        } catch (_: Exception) {
            FloatingMode.WINDOW
        }

        val storedScale = prefs.getFloat("window_scale", 0.8f)
        val storedLastScale = prefs.getFloat("last_window_scale", 0.8f)
        require(storedScale.isFinite()) { "Non-finite stored window_scale: $storedScale" }
        require(storedLastScale.isFinite()) { "Non-finite stored last_window_scale: $storedLastScale" }
        windowScale.value = storedScale.coerceIn(0.3f, 1.0f)
        lastWindowScale = storedLastScale.coerceIn(0.3f, 1.0f)
    }
}
