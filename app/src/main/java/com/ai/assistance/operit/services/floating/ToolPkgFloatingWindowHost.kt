package com.ai.assistance.operit.services.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.core.tools.packTool.floating.ToolPkgFloatingWindowOptions
import com.ai.assistance.operit.core.tools.packTool.floating.ToolPkgFloatingWindowSurface
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * 插件悬浮窗的 Android 宿主：WindowManager overlay + ComposeView + ServiceLifecycleOwner，
 * 模式取自 PermissionRequestOverlay / UIDebuggerWindowManager。
 *
 * 所有窗口操作编组到主线程执行（已在主线程则内联），保证控制器可在任意线程调用。
 * 内容组合由渲染层通过 [content] 注入；[updateState] 递增修订号触发重组，
 * 渲染层组合函数从自己的状态源读取最新数据。
 */
class ToolPkgFloatingWindowHost(
    private val context: Context,
    override val id: String,
    initialOptions: ToolPkgFloatingWindowOptions,
    private val content: @Composable (revision: Int, options: ToolPkgFloatingWindowOptions) -> Unit,
    private val onGeometryChanged: (x: Int, y: Int) -> Unit,
) : ToolPkgFloatingWindowSurface {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var options by mutableStateOf(initialOptions)
    private var contentRevision by mutableIntStateOf(0)

    private var composeView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var added = false
    private var closed = false

    private val density: Float
        get() = context.resources.displayMetrics.density

    init {
        onMain {
            val owner = ServiceLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            lifecycleOwner = owner

            val view = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    WindowContent()
                }
            }
            composeView = view

            try {
                windowManager.addView(view, createLayoutParams())
                added = true
            } catch (error: Exception) {
                AppLogger.e(TAG, "Error adding floating window $id", error)
                destroyLifecycle(owner)
                composeView = null
                lifecycleOwner = null
                throw error
            }
        }
    }

    @Composable
    private fun WindowContent() {
        val currentOptions = options
        if (currentOptions.draggable == true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentOptions) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMain {
                                val view = composeView ?: return@onMain
                                val params = view.layoutParams as? WindowManager.LayoutParams
                                    ?: return@onMain
                                params.x += dragAmount.x.roundToInt()
                                params.y += dragAmount.y.roundToInt()
                                windowManager.updateViewLayout(view, params)
                                onGeometryChanged(params.x, params.y)
                            }
                        }
                    }
            ) {
                content(contentRevision, currentOptions)
            }
        } else {
            content(contentRevision, currentOptions)
        }
    }

    override fun applyOptions(newOptions: ToolPkgFloatingWindowOptions) = onMain {
        if (closed) return@onMain
        options = newOptions
        val view = composeView ?: return@onMain
        applyToLayoutParams(view) { params ->
            params.x = newOptions.x ?: params.x
            params.y = newOptions.y ?: params.y
            params.width = newOptions.width?.toPx() ?: params.width
            params.height = newOptions.height?.toPx() ?: params.height
            params.flags = flagsFor(newOptions)
        }
    }

    override fun show() = onMain {
        if (closed) return@onMain
        composeView?.visibility = View.VISIBLE
    }

    override fun hide() = onMain {
        if (closed) return@onMain
        // INVISIBLE 保留组合树与窗口会话（对齐"隐藏不重置 UI 会话"语义）
        composeView?.visibility = View.INVISIBLE
    }

    override fun close() = onMain {
        if (closed) return@onMain
        closed = true
        val view = composeView
        val owner = lifecycleOwner
        if (view != null && added) {
            try {
                windowManager.removeView(view)
            } catch (error: Exception) {
                AppLogger.e(TAG, "Error removing floating window $id", error)
            }
        }
        if (owner != null) destroyLifecycle(owner)
        composeView = null
        lifecycleOwner = null
        added = false
    }

    override fun updateState(state: JSONObject) = onMain {
        if (closed) return@onMain
        // 渲染层组合函数在重组时读取最新 state；此处仅触发修订
        contentRevision++
    }

    override fun isAlive(): Boolean = !closed && added && onMain {
        composeView?.isAttachedToWindow == true
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = windowType()
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = options.x ?: 0
            y = options.y ?: 0
            width = options.width?.toPx() ?: WindowManager.LayoutParams.WRAP_CONTENT
            height = options.height?.toPx() ?: WindowManager.LayoutParams.WRAP_CONTENT
            flags = flagsFor(options)
        }
    }

    private fun applyToLayoutParams(
        view: View,
        mutate: (WindowManager.LayoutParams) -> Unit
    ) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        mutate(params)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (error: Exception) {
            AppLogger.e(TAG, "Error updating floating window $id layout", error)
        }
    }

    /**
     * flags 映射：clickThrough -> FLAG_NOT_TOUCHABLE；不可聚焦 -> FLAG_NOT_FOCUSABLE。
     * alwaysOnTop=false 无法把 overlay 降到普通窗口之下（Android overlay 天性置顶），
     * 选项照常记录，但布局上不生效，capabilities 上报 alwaysOnTop=true。
     */
    private fun flagsFor(options: ToolPkgFloatingWindowOptions): Int {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (options.clickThrough == true) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (options.focusable != true) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return flags
    }

    private fun windowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun Int.toPx(): Int = (this * density).roundToInt()

    private fun destroyLifecycle(owner: ServiceLifecycleOwner) {
        owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        var result: Any? = null
        var failure: Throwable? = null
        val done = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            try {
                result = block()
            } catch (error: Throwable) {
                failure = error
            } finally {
                done.countDown()
            }
        }
        done.await()
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    companion object {
        private const val TAG = "ToolPkgFloatHost"
    }
}
