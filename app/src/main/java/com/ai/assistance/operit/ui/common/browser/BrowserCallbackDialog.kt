package com.ai.assistance.operit.ui.common.browser

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.models.token.webview.WebViewConfig
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.delay

/**
 * Presents one host-owned browser flow and reports navigation to its registered callback destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserCallbackDialog(
    title: String,
    authorizationUrl: String,
    completionRedirectUri: Uri,
    expiresAt: Long,
    onCompletion: (Uri) -> Unit,
    onCancelled: () -> Unit,
    onFailure: (Throwable) -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebViewConfig.createWebView(context).apply {
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
        }
    }
    var isLoading by remember { mutableStateOf(true) }
    var isCompleting by remember { mutableStateOf(false) }
    var completionHandled by remember { mutableStateOf(false) }

    fun captureCompletion(uri: Uri): Boolean {
        if (!matchesCallbackDestination(uri, completionRedirectUri)) {
            return false
        }
        if (!completionHandled) {
            completionHandled = true
            isCompleting = true
            onCompletion(uri)
        }
        return true
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                return captureCompletion(uri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
                val uri = url?.let(Uri::parse) ?: return
                if (captureCompletion(uri)) {
                    view?.stopLoading()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!isCompleting) {
                    isLoading = false
                }
            }
        }
        onDispose {
            releaseBrowserCallbackWebView(webView)
        }
    }

    LaunchedEffect(authorizationUrl) {
        webView.loadUrl(authorizationUrl)
    }

    LaunchedEffect(expiresAt) {
        val remainingMillis = expiresAt - System.currentTimeMillis()
        if (remainingMillis <= 0L) {
            if (!completionHandled) {
                completionHandled = true
                onFailure(IllegalStateException("Browser callback timed out"))
            }
            return@LaunchedEffect
        }
        delay(remainingMillis)
        if (!completionHandled) {
            completionHandled = true
            onFailure(IllegalStateException("Browser callback timed out"))
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isCompleting) {
                onCancelled()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CustomScaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(
                                onClick = onCancelled,
                                enabled = !isCompleting
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { webView.reload() },
                                enabled = !isCompleting
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AndroidView(
                        factory = { webView },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isLoading || isCompleting) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** Checks whether a browser URL reached the scheme, host, port, and path registered by the caller. */
private fun matchesCallbackDestination(current: Uri, expected: Uri): Boolean {
    return current.scheme == expected.scheme &&
        current.host == expected.host &&
        current.port == expected.port &&
        current.path == expected.path
}

/** Releases the WebView that was allocated for one browser callback flow. */
private fun releaseBrowserCallbackWebView(webView: WebView) {
    try {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    } catch (error: Exception) {
        AppLogger.e("BrowserCallback", "Failed to release browser callback WebView", error)
    }
}
