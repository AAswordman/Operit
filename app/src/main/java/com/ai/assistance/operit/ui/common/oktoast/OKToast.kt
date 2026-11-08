package com.ai.assistance.operit.ui.common.oktoast

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.PopupWindow
import androidx.annotation.StringRes
import com.ai.assistance.operit.util.logging.AppLogger
import java.lang.ref.WeakReference

/** 应用内提示统一入口，状态必须由调用方根据操作结果明确选择。 */
object OKToast {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1
    enum class Kind { Info, Success, Warning, Error }
    internal enum class Placement { Bottom, Top }
    data class Appearance(val dark: Boolean, val surface: Int, val text: Int)
    private var appearance = Appearance(false, android.graphics.Color.WHITE, android.graphics.Color.BLACK)

    fun useAppearance(dark: Boolean, surface: Int, text: Int) {
        appearance = Appearance(dark, surface, text)
    }

    class Notice internal constructor(internal val message: String, internal val kind: Kind, internal val duration: Int, internal val placement: Placement = Placement.Bottom) {
        fun show() { main.post { enqueue(this) } }
        /** 只关闭本条提示；排队中的提示也可撤销，不影响请求执行。 */
        fun cancel() {
            main.post {
                queue.remove(this)
                if (currentNotice === this) dismissCurrent(animate = true)
            }
        }
    }

    fun info(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Info, duration)
    fun info(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = info(context, context.getText(text), duration)
    fun success(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Success, duration)
    fun success(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = success(context, context.getText(text), duration)
    fun warning(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Warning, duration)
    fun warning(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = warning(context, context.getText(text), duration)
    fun error(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Error, duration)
    fun error(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = error(context, context.getText(text), duration)
    /** 请求错误沿用上方卡片，普通操作提示仍在窗口下方显示。 */
    fun requestError(context: Context, text: CharSequence, duration: Int = LENGTH_LONG) = Notice(text.toString(), Kind.Error, duration, Placement.Top)
    fun typed(context: Context, text: CharSequence, kind: Kind, duration: Int = LENGTH_SHORT) = Notice(text.toString(), kind, duration)

    private val main = Handler(Looper.getMainLooper())
    private val queue = RetainedNoticeQueue<Notice> { it.placement == Placement.Top }
    private var currentNotice: Notice? = null
    private var activityHost: WeakReference<View>? = null
    private val windowHosts = mutableListOf<WeakReference<View>>()
    private var popup: PopupWindow? = null
    private var popupPlacement = Placement.Bottom
    private var lastMessage = ""
    private var lastKind = Kind.Info
    private var lastAt = 0L
    private var initialized = false
    private val dismiss = Runnable { dismissCurrent(animate = true) }

    fun initialize(application: Application) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (initialized) return
        initialized = true
        val dark = application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        appearance = Appearance(dark, if (dark) 0xFF25272B.toInt() else 0xFFFFFBFF.toInt(), if (dark) 0xFFF0F0F4.toInt() else 0xFF202126.toInt())
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                parkNoticesForHostChange()
                activityHost = WeakReference(activity.window.decorView)
                activity.window.decorView.post { showNext() }
            }
            override fun onActivityPaused(activity: Activity) {
                if (activityHost?.get() === activity.window.decorView) {
                    activityHost = null
                    parkNoticesForHostChange()
                }
            }
            override fun onActivityDestroyed(activity: Activity) = Unit
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
    }

    /** 对话框、面板和悬浮窗口使用自己的根视图及窗口 token，返回值用于对称注销。 */
    fun registerWindow(view: View): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        parkNoticesForHostChange()
        val reference = WeakReference(view.rootView)
        windowHosts.add(reference)
        view.post { showNext() }
        return {
            windowHosts.remove(reference)
            parkNoticesForHostChange()
            main.post { showNext() }
        }
    }

    private fun host(): View? {
        windowHosts.removeAll { it.get() == null }
        val hosts = buildList {
            activityHost?.let { add(it) }
            addAll(windowHosts)
        }
        return hosts.mapNotNull { it.get() }.lastOrNull { it.isAttachedToWindow && it.isShown }
    }

    private fun parkNoticesForHostChange() {
        currentNotice?.let { queue.restore(it) }
        queue.retainForHostChange()
        dismissCurrent(animate = false)
    }

    private fun enqueue(notice: Notice) {
        if (notice.message.isBlank()) return
        if (host() == null && notice.placement != Placement.Top) return
        val now = SystemClock.uptimeMillis()
        if (notice.kind != Kind.Error && notice.message == lastMessage && notice.kind == lastKind && now - lastAt < 800L) return
        lastMessage = notice.message
        lastKind = notice.kind
        lastAt = now
        // 请求错误不受普通操作提示的队列淘汰影响。
        queue.add(notice)
        if (popup == null) showNext()
    }

    private fun showNext() {
        if (popup != null || queue.isEmpty) return
        val anchor = host()
        if (anchor == null) { queue.retainForHostChange(); return }
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val bounds = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(bounds)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        if (!bounds.intersect(location[0], location[1], location[0] + anchor.width, location[1] + anchor.height)) return
        // 按当前窗口可见区域定位，键盘、分屏与悬浮窗口都使用各自的可用范围。
        val maxWidth = (bounds.width() * 0.9f).toInt().coerceAtLeast(1)
        val maxHeight = (bounds.height() * 0.25f).toInt().coerceAtLeast(1)
        val notice = queue.removeFirst()
        val isRequestError = notice.placement == Placement.Top
        // 成功提示复用请求错误的点击关闭路径，让正文、空白区域与无障碍操作保持一致。
        val canDismiss = isRequestError || notice.kind == Kind.Success
        val card = OKToastView(
            context, notice.kind, notice.message, appearance, maxHeight, isRequestError,
            onDismiss = if (canDismiss) ({ notice.cancel() }) else null,
        )
        card.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, if (isRequestError) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
        )
        val x = bounds.centerX() - card.measuredWidth / 2
        val targetY = if (isRequestError) {
            bounds.top + bounds.height() * 0.08f
        } else {
            bounds.top + bounds.height() * 0.9f - card.measuredHeight / 2f
        }
        val y = targetY.toInt().coerceIn(bounds.top, (bounds.bottom - card.measuredHeight).coerceAtLeast(bounds.top))
        val window = PopupWindow(card, card.measuredWidth, card.measuredHeight, false).apply {
            setIsLaidOutInScreen(true)
            isTouchable = true
            isOutsideTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = 10 * density
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            // 应用子窗口不获取输入焦点，也不盖住系统权限和其他应用。
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            setOnDismissListener {
                if (popup === this) {
                    popup = null
                    currentNotice = null
                    main.removeCallbacks(dismiss)
                    showNext()
                }
            }
        }
        popupPlacement = notice.placement
        currentNotice = notice
        popup = window
        try {
            window.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, x, y)
        } catch (error: WindowManager.BadTokenException) {
            popup = null
            currentNotice = null
            queue.restore(notice)
            queue.retainForHostChange()
            AppLogger.w("OKToast", "提示宿主窗口已失效", error)
            return
        }
        card.announceForAccessibility(card.contentDescription)
        if (ValueAnimator.areAnimatorsEnabled()) {
            card.alpha = 0f
            card.translationY = (if (isRequestError) -8 else 8) * density
            card.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
        // 请求错误至少显示五秒，长文本递增到十二秒；普通操作提示沿用原时长。
        val base = if (isRequestError) RequestErrorDuration.baseMillis(notice.message)
            else if (notice.duration == LENGTH_LONG) 3500 else 2000
        val accessibility = requireNotNull(context.getSystemService(AccessibilityManager::class.java))
        val timeout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val flags = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or
                (if (canDismiss) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0)
            accessibility.getRecommendedTimeoutMillis(base, flags)
        } else base
        val displayMillis = if (isRequestError) RequestErrorDuration.limitMillis(timeout) else timeout
        main.postDelayed(dismiss, displayMillis.toLong())
    }

    private fun dismissCurrent(animate: Boolean) {
        main.removeCallbacks(dismiss)
        currentNotice = null
        val window = popup ?: return
        val card = window.contentView
        card.animate().cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled() && card.isAttachedToWindow) {
            card.animate().alpha(0f).translationY((if (popupPlacement == Placement.Top) -6 else 6) * card.resources.displayMetrics.density).setDuration(140)
                .withEndAction { if (popup === window) window.dismiss() }.start()
        } else {
            popup = null
            window.setOnDismissListener(null)
            window.dismiss()
            if (animate) showNext()
        }
    }
}