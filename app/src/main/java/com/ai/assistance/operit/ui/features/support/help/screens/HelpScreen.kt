package com.ai.assistance.operit.ui.features.support.help.screens

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.models.token.webview.WebViewConfig

@Composable
fun HelpScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val helpUrl = "https://operit.app"
    val focusRequester = remember { FocusRequester() }

    // 创建WebView实例
    val webView = remember {
        WebViewConfig.createWebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    isLoading = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // 让WebView处理所有链接
                    return false
                }
            }
        }
    }
    // 确保WebView获取焦点，避免滑动时被父级拦截
    DisposableEffect(webView) {
        webView.post {
            webView.requestFocus()
            webView.requestFocusFromTouch()
        }
        onDispose { }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        webView.loadUrl(helpUrl)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // WebView
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
        )
        
        // 加载指示器
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.help_loading_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
