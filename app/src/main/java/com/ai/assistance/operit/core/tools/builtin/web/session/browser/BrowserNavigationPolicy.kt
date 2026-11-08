package com.ai.assistance.operit.core.tools.builtin.web.session.browser

/** 自动开窗按文档限流；外部请求的抑制跨重定向保留，用户主动操作仍可重试。 */
internal class BrowserNavigationPolicy {
    companion object {
        const val MAX_POPUP_SESSIONS = 8
        const val USER_ACTIVATION_WINDOW_MS = 2_000L
    }

    private var automaticPopupOpened = false
    private var externalOpenRequested = false
    private var userActivationAt: Long? = null

    fun onDocumentStarted() {
        automaticPopupOpened = false
        userActivationAt = null
        // 页面自行重载或重定向不能绕过已经处理的外部请求。
    }

    fun onExplicitNavigation() {
        onDocumentStarted()
        externalOpenRequested = false
    }

    fun onUserInteraction(nowMillis: Long) {
        userActivationAt = nowMillis
    }

    fun onExternalRequestResolved() {
        userActivationAt = null
    }

    private fun hasUserActivation(nowMillis: Long): Boolean {
        val activation = userActivationAt ?: return false
        return nowMillis >= activation && nowMillis - activation <= USER_ACTIVATION_WINDOW_MS
    }

    fun tryCreatePopup(
        hasUserGesture: Boolean,
        isActivePage: Boolean,
        livePopupCount: Int,
        nowMillis: Long,
    ): Boolean {
        if (!isActivePage || livePopupCount >= MAX_POPUP_SESSIONS) return false
        val userInitiated = hasUserGesture || hasUserActivation(nowMillis)
        if (!userInitiated && automaticPopupOpened) return false
        // 异步登录脚本可能没有手势标记，保留每个文档一次自动开窗。
        if (!userInitiated) automaticPopupOpened = true
        userActivationAt = null
        return true
    }

    fun tryRequestExternalOpen(
        isMainFrame: Boolean,
        hasUserGesture: Boolean,
        isActivePage: Boolean,
        hasPendingRequest: Boolean,
        nowMillis: Long,
    ): Boolean {
        if (!isActivePage || hasPendingRequest) return false
        val userInitiated = hasUserGesture || hasUserActivation(nowMillis)
        if (!isMainFrame && !userInitiated) return false
        if (!userInitiated && externalOpenRequested) return false
        // 确认或取消后，页面不能自动重新弹出；再次点击仍可发起新的确认。
        externalOpenRequested = true
        userActivationAt = null
        return true
    }
}