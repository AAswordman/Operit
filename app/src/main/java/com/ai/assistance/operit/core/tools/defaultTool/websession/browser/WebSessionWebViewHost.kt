package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import kotlin.math.min
import kotlin.math.roundToInt

internal class WebSessionWebViewHost {
    private var container: FrameLayout? = null
    private var activeWebView: WebView? = null
    private var viewportWidthCssPx: Int? = null
    private var viewportHeightCssPx: Int? = null
    private val containerLayoutChangeListener =
        View.OnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (
                right - left != oldRight - oldLeft ||
                    bottom - top != oldBottom - oldTop
            ) {
                applyWebViewLayout(view as FrameLayout)
            }
        }

    fun attachContainer(target: FrameLayout) {
        if (container !== target) {
            container?.removeOnLayoutChangeListener(containerLayoutChangeListener)
            target.addOnLayoutChangeListener(containerLayoutChangeListener)
        }
        container = target
        reattach()
    }

    fun detachContainer(target: FrameLayout) {
        if (container === target) {
            target.removeOnLayoutChangeListener(containerLayoutChangeListener)
            container = null
        }
    }

    fun setActiveWebView(webView: WebView?, viewportWidth: Int?, viewportHeight: Int?) {
        activeWebView = webView
        viewportWidthCssPx = viewportWidth
        viewportHeightCssPx = viewportHeight
        reattach()
    }

    fun setViewportSize(width: Int?, height: Int?) {
        viewportWidthCssPx = width
        viewportHeightCssPx = height
        reattach()
    }

    fun currentWebView(): WebView? = activeWebView

    fun clear() {
        container?.removeAllViews()
        container?.removeOnLayoutChangeListener(containerLayoutChangeListener)
        container = null
        activeWebView = null
        viewportWidthCssPx = null
        viewportHeightCssPx = null
    }

    private fun reattach() {
        val target = container ?: return
        val webView = activeWebView

        if (webView == null) {
            target.removeAllViews()
            return
        }

        val parent = webView.parent
        if (parent is ViewGroup && parent !== target) {
            parent.removeView(webView)
        }

        if (target.childCount != 1 || target.getChildAt(0) !== webView) {
            target.removeAllViews()
            target.addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        applyWebViewLayout(target)
    }

    private fun applyWebViewLayout(target: FrameLayout) {
        val webView = activeWebView ?: return
        if (webView.parent !== target) {
            return
        }

        val requestedWidth = viewportWidthCssPx
        val requestedHeight = viewportHeightCssPx
        if (requestedWidth == null || requestedHeight == null) {
            webView.pivotX = 0f
            webView.pivotY = 0f
            webView.scaleX = 1f
            webView.scaleY = 1f
            webView.translationX = 0f
            webView.translationY = 0f
            updateLayoutParams(
                webView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            return
        }

        // WebView lays out CSS pixels as density-independent pixels. Giving it the requested
        // CSS viewport in physical pixels makes window.innerWidth/innerHeight match the tool
        // arguments; the view transform below only fits that logical viewport on the phone.
        val density =
            webView.resources.displayMetrics.density
                .takeIf { it.isFinite() && it > 0f }
                ?: 1f
        val layoutWidth = (requestedWidth * density).roundToInt().coerceAtLeast(1)
        val layoutHeight = (requestedHeight * density).roundToInt().coerceAtLeast(1)
        updateLayoutParams(webView, layoutWidth, layoutHeight)

        val availableWidth = target.width
        val availableHeight = target.height
        if (availableWidth <= 0 || availableHeight <= 0) {
            return
        }

        val previewScale =
            min(
                availableWidth.toFloat() / layoutWidth.toFloat(),
                availableHeight.toFloat() / layoutHeight.toFloat()
            )
        webView.pivotX = 0f
        webView.pivotY = 0f
        webView.scaleX = previewScale
        webView.scaleY = previewScale
        webView.translationX = (availableWidth - layoutWidth * previewScale) / 2f
        webView.translationY = (availableHeight - layoutHeight * previewScale) / 2f
    }

    private fun updateLayoutParams(webView: WebView, width: Int, height: Int) {
        val current = webView.layoutParams as? FrameLayout.LayoutParams
        if (current?.width == width && current.height == height && current.gravity == Gravity.TOP) {
            return
        }
        webView.layoutParams =
            FrameLayout.LayoutParams(width, height).apply {
                gravity = Gravity.TOP
            }
    }
}
