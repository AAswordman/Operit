package com.ai.assistance.operit.ui.common.oktoast

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ai.assistance.operit.R

/** 原生轻量提示卡片可附着 Activity 或已有悬浮窗口，不依赖 Compose 生命周期所有者。 */
internal class OKToastView(context: Context, kind: OKToast.Kind, message: String, appearance: OKToast.Appearance, maxContentHeight: Int, isRequestError: Boolean, onDismiss: (() -> Unit)?) : LinearLayout(context) {
    init {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = appearance.dark
        val accent = when (kind) {
            OKToast.Kind.Info -> if (dark) 0xFF9BCAFF else 0xFF235D9F
            OKToast.Kind.Success -> if (dark) 0xFF86D9AF else 0xFF246B49
            OKToast.Kind.Warning -> if (dark) 0xFFFFD58D else 0xFF895C14
            OKToast.Kind.Error -> if (dark) 0xFFFFB4AB else 0xFFB3261E
        }.toInt()
        if (onDismiss != null) setOnClickListener { onDismiss() }
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(appearance.surface)
            setStroke(dp(1).coerceAtLeast(1), Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        val statusLabel = context.getString(when (kind) {
            OKToast.Kind.Info -> R.string.oktoast_info
            OKToast.Kind.Success -> R.string.oktoast_success
            OKToast.Kind.Warning -> R.string.oktoast_warning
            OKToast.Kind.Error -> R.string.oktoast_error
        })
        contentDescription = "$statusLabel：$message"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(StatusIcon(context, kind, accent), LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        val text = TextView(context).apply {
            this.text = message
            textSize = 14f
            setTextColor(appearance.text)
            contentDescription = "$statusLabel：$message"
            gravity = if (isRequestError) Gravity.START else Gravity.CENTER
            textAlignment = if (isRequestError) TEXT_ALIGNMENT_VIEW_START else TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
            if (onDismiss != null) {
                // 点击正文关闭，拖动仍交给 ScrollView，避免滚动长错误时误关闭。
                setOnClickListener { onDismiss() }
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
                    }
                    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                        if (action == AccessibilityNodeInfo.ACTION_DISMISS) {
                            onDismiss()
                            return true
                        }
                        return super.performAccessibilityAction(host, action, args)
                    }
                }
            }
        }
        val scroller = object : ScrollView(context) {
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            private var downX = 0f
            private var downY = 0f
            private var dragged = false

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        dragged = false
                    }
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_UP -> {
                        if (kotlin.math.abs(event.x - downX) > touchSlop ||
                            kotlin.math.abs(event.y - downY) > touchSlop) dragged = true
                    }
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_POINTER_DOWN -> dragged = true
                }
                val handled = super.onTouchEvent(event)
                // ScrollView 会消费空白区域的触摸，短按也应关闭；滚动或取消不触发。
                if (event.actionMasked == MotionEvent.ACTION_UP && !dragged && onDismiss != null) {
                    performClick()
                }
                return handled
            }

            override fun performClick(): Boolean {
                val handled = super.performClick()
                onDismiss?.invoke()
                return onDismiss != null || handled
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxContentHeight else minOf(MeasureSpec.getSize(heightMeasureSpec), maxContentHeight), MeasureSpec.AT_MOST))
            }
        }.apply {
            isFillViewport = false
            addView(text, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        val textLayout = if (isRequestError) LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            else LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        addView(scroller, textLayout)
    }
}

private class StatusIcon(context: Context, private val kind: OKToast.Kind, private val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 24f, height / 24f)
        paint.color = color
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        if (kind == OKToast.Kind.Warning) {
            path.reset()
            path.moveTo(12f, 3f); path.lineTo(22f, 21f); path.lineTo(2f, 21f); path.close()
            canvas.drawPath(path, paint)
        } else canvas.drawCircle(12f, 12f, 9f, paint)
        when (kind) {
            OKToast.Kind.Success -> {
                path.reset()
                path.moveTo(7f, 12f); path.lineTo(10f, 15f); path.lineTo(17f, 8f)
                canvas.drawPath(path, paint)
            }
            OKToast.Kind.Info -> {
                canvas.drawLine(12f, 11f, 12f, 16f, paint)
                canvas.drawPoint(12f, 7f, paint)
            }
            OKToast.Kind.Warning, OKToast.Kind.Error -> {
                canvas.drawLine(12f, 8f, 12f, 13f, paint)
                canvas.drawPoint(12f, 17f, paint)
            }
        }
        canvas.restore()
    }
}