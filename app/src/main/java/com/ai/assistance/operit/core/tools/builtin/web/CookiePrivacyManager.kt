package com.ai.assistance.operit.core.tools.builtin.web

import android.webkit.CookieManager
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Clears cookies shared by network tools and WebView-based browser surfaces. */
internal object CookiePrivacyManager {
    suspend fun clearAllCookies() {
        StandardHttpTools.clearSharedCookies()

        withContext(Dispatchers.Main.immediate) {
            val cookieManager = CookieManager.getInstance()
            suspendCancellableCoroutine<Unit> { continuation ->
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }
}
