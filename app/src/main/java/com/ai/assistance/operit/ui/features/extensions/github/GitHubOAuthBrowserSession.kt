package com.ai.assistance.operit.ui.features.extensions.github

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Clears the app-owned WebView session before a user signs in to GitHub again. */
suspend fun clearGitHubOAuthBrowserSession() {
    withContext(Dispatchers.Main.immediate) {
        WebStorage.getInstance().deleteAllData()
        suspendCancellableCoroutine { continuation ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }
}
