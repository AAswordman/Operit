package com.ai.assistance.operit.ui.features.models.token.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.ai.assistance.operit.util.logging.AppLogger
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** WebView配置相关工具类 */
object WebViewConfig {
    /** 创建一个预配置的WebView实例 */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun createWebView(context: Context): WebView {
        // Initialize the WebView
        return WebView(context).apply {
            // Configure WebView settings
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                allowContentAccess = true
                allowFileAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true

                // 显式允许混合内容（对于支付场景很重要）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                // 允许通用第三方应用访问
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true

                // 设置一个常见的移动浏览器User-Agent，以避免被某些服务（如Google登录）阻止
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                // 启用缩放控制
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // 对于Android 8.0及以上版本的安全Webview优化
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        safeBrowsingEnabled = true
                    } catch (e: AbstractMethodError) {
                        AppLogger.w("WebViewConfig", "Safe browsing not supported on this WebView implementation: ${e.message}")
                    } catch (e: NoSuchMethodError) {
                        AppLogger.w("WebViewConfig", "Safe browsing method missing on this WebView implementation: ${e.message}")
                    } catch (e: Throwable) {
                        AppLogger.w("WebViewConfig", "Failed to enable safe browsing: ${e.message}")
                    }
                }
            }

            // 配置Cookie
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // 确保WebView可以处理跳转到外部应用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }

            // Enable WebView debugging
            WebView.setWebContentsDebuggingEnabled(true)

            // 为了确保正确处理滚动，设置嵌套滚动启用
            isNestedScrollingEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            isLongClickable = true

            // 设置WebView布局参数，确保它可以正常滚动
            layoutParams =
                    ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )

            // 解决WebView和父布局之间的触摸冲突
            setOnTouchListener {
                v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }

            // Add console logger
            setWebChromeClient(
                    object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            val newWebView = WebView(view?.context ?: return false)
                            newWebView.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    w: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    request?.url?.let { uri ->
                                        val url = uri.toString()
                                        
                                        // 处理特殊协议，仍然需要外部跳转
                                        if (url.startsWith("alipays:") || 
                                            url.startsWith("alipay:") || 
                                            url.startsWith("weixin:") ||
                                            url.startsWith("weixins:")) {
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                view?.context?.startActivity(intent)
                                                return true
                                            } catch (e: Exception) {
                                                AppLogger.e("WebViewConfig", "无法在新窗口打开外部应用: ${e.message}")
                                            }
                                            return true
                                        }

                                        // 对于普通链接，强制在当前WebView（发起者）中加载，而不是打开外部浏览器
                                        // 这样实现了"在内置webview打开"的需求
                                        view?.post {
                                            view.loadUrl(url)
                                        }
                                        return true
                                    }
                                    return false
                                }
                            }
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            transport?.webView = newWebView
                            resultMsg?.sendToTarget()
                            return true
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            AppLogger.d(
                                    "WebViewConsole",
                                    "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                            )
                            return true
                        }

                        // 支持HTML5视频全屏播放
                        override fun onShowCustomView(
                                view: android.view.View,
                                callback: CustomViewCallback
                        ) {
                            super.onShowCustomView(view, callback)
                        }

                        override fun onHideCustomView() {
                            super.onHideCustomView()
                        }

                        // 支持地理位置请求
                        override fun onGeolocationPermissionsShowPrompt(
                                origin: String,
                                callback: android.webkit.GeolocationPermissions.Callback
                        ) {
                            callback.invoke(origin, true, false)
                        }

                        // 处理JavaScript警告和错误
                        override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: android.webkit.JsResult?
                        ): Boolean {
                            AppLogger.d("WebViewJS", "Alert: $message")
                            result?.confirm()
                            return true
                        }

                        override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: android.webkit.JsResult?
                        ): Boolean {
                            AppLogger.d("WebViewJS", "Confirm: $message")
                            result?.confirm()
                            return true
                        }

                        override fun onJsPrompt(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                defaultValue: String?,
                                result: android.webkit.JsPromptResult?
                        ): Boolean {
                            AppLogger.d("WebViewJS", "Prompt: $message, Default: $defaultValue")
                            result?.confirm(defaultValue)
                            return true
                        }
                    }
            )
        }
    }
}
