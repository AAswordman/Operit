package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.ai.assistance.operit.util.logging.AppLogger
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** WebViewHandler - 处理WebView的所有配置和功能 包括安全设置、CORS支持、文件上传下载、缓存控制等 */
class WebViewHandler(private val context: Context) {

    enum class WebViewMode {
        WORKSPACE // 用于代码/网页预览，需要自适应屏幕
    }

    data class WebViewOptions(
        val preferDesktopSite: Boolean = true,
        val enableWorkspaceCorsProxy: Boolean = true,
        val supportZoom: Boolean = true,
        val useWideViewPort: Boolean = true,
        val loadWithOverviewMode: Boolean = true
    )

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 用于覆盖URL加载行为的回调
    var urlLoadingOverrider: ((view: WebView?, request: WebResourceRequest?) -> Boolean)? = null

    // 用于报告页面加载进度的回调
    var onProgressChanged: ((progress: Int) -> Unit)? = null

    @Composable
    fun observeProgress(): State<Int> {
        return produceState(initialValue = 0) {
            onProgressChanged = {
                value = it
            }
        }
    }

    // 用于报告页面标题的回调
    var onTitleReceived: ((title: String?) -> Unit)? = null
    var onPageStarted: ((tabId: String) -> Unit)? = null
    var onPageFinished: ((tabId: String) -> Unit)? = null
    var onCanGoBackChanged: ((Boolean) -> Unit)? = null
    var onCanGoForwardChanged: ((Boolean) -> Unit)? = null


    // 通过JavaScript接口处理Blob/Base64数据下载
    private inner class BlobDownloadInterface {
        @JavascriptInterface
        fun downloadBlob(base64Data: String, fileName: String, mimeType: String) {
            try {
                // 从Base64字符串中提取数据部分
                val data =
                        if (base64Data.contains(",")) {
                            base64Data.substring(base64Data.indexOf(",") + 1)
                        } else {
                            base64Data
                        }

                // 解码Base64数据
                val bytes = Base64.decode(data, Base64.DEFAULT)

                // 创建下载目录
                val downloadsDir =
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS
                        )

                // 确保文件名有效
                val cleanFileName = sanitizeFileName(fileName)

                // 创建文件
                val file = File(downloadsDir, cleanFileName)
                FileOutputStream(file).use { it.write(bytes) }

                // 在主线程显示通知
                Handler(Looper.getMainLooper()).post {
                    OKToast.success(context, context.getString(R.string.download_success, cleanFileName), OKToast.LENGTH_SHORT).show()

                    // 通知媒体扫描器更新文件
                    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    intent.data = Uri.fromFile(file)
                    context.sendBroadcast(intent)
                }

                // 可选：打开文件
                openDownloadedFile(file, mimeType)
            } catch (e: Exception) {
                AppLogger.e("WebViewHandler", "Blob数据下载失败", e)
                Handler(Looper.getMainLooper()).post {
                    OKToast.error(context, context.getString(R.string.download_failed, e.localizedMessage), OKToast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            AppLogger.d("WebViewHandler", "JS: $message")
        }
    }

    // 配置WebView的所有设置
    fun configureWebView(
        webView: WebView,
        mode: WebViewMode,
        tabId: String,
        options: WebViewOptions = WebViewOptions()
    ): WebView {
        return webView.apply {
            // 明确设置布局参数，确保WebView填满其父容器
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

            // 为支持 backdrop-filter 等高级CSS效果，需要开启硬件加速
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // 配置WebViewClient处理页面加载和错误
            webViewClient = createWebViewClient(mode, tabId, options)

            // 配置WebChromeClient处理文件选择等高级功能
            webChromeClient = createWebChromeClient()

            // 配置下载监听器
            setDownloadListener(createDownloadListener())

            // 添加JavaScript接口处理Blob下载
            addJavascriptInterface(BlobDownloadInterface(), "NativeBridge")

            // 配置WebView设置
            settings.apply {
                // JavaScript支持
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true

                // 跨域和混合内容支持
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowContentAccess = true
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true

                // DOM存储和数据库
                domStorageEnabled = true
                databaseEnabled = true

                // 缓存设置
                cacheMode = WebSettings.LOAD_DEFAULT

                // 视图设置
                useWideViewPort = options.useWideViewPort
                loadWithOverviewMode = options.loadWithOverviewMode

                // 缩放设置
                setSupportZoom(options.supportZoom)
                builtInZoomControls = options.supportZoom
                displayZoomControls = false

                // 编码设置
                defaultTextEncodingName = "UTF-8"

                // 本地预览页默认保持移动端UA，避免 Flutter/Web 应用在内置浏览器里走错布局或渲染路径。
                userAgentString =
                    if (options.preferDesktopSite) {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0 OperitWebView/1.0"
                    } else {
                        WebSettings.getDefaultUserAgent(context)
                    }
            }

            // 注入Blob下载辅助JavaScript
            injectBlobDownloadHelper(this)
        }
    }

    // 创建WebViewClient
    private fun notifyNavigationState(view: WebView?) {
        onCanGoBackChanged?.invoke(view?.canGoBack() == true)
        onCanGoForwardChanged?.invoke(view?.canGoForward() == true)
    }

    private fun createWebViewClient(
        mode: WebViewMode,
        tabId: String,
        options: WebViewOptions
    ): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onPageStarted?.invoke(tabId)
                notifyNavigationState(view)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinished?.invoke(tabId)

                // 注入Blob下载辅助代码
                view?.let { injectBlobDownloadHelper(it) }

                if (mode == WebViewMode.WORKSPACE && options.enableWorkspaceCorsProxy) {
                    view?.let { injectWorkspaceCorsProxyHelper(it) }
                }

                notifyNavigationState(view)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // 如果设置了URL加载覆盖器，则使用它
                return urlLoadingOverrider?.invoke(view, request)
                    ?: super.shouldOverrideUrlLoading(view, request)
            }

            // 处理SSL错误
            override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: android.net.http.SslError?
            ) {
                // 创建警告对话框
                val builder = android.app.AlertDialog.Builder(context)
                var message = context.getString(R.string.webview_ssl_cert_error)
                val errorDetail =
                        when (error?.primaryError) {
                            android.net.http.SslError.SSL_UNTRUSTED ->
                                    context.getString(R.string.webview_ssl_error_untrusted)
                            android.net.http.SslError.SSL_EXPIRED ->
                                    context.getString(R.string.webview_ssl_error_expired)
                            android.net.http.SslError.SSL_IDMISMATCH ->
                                    context.getString(R.string.webview_ssl_error_idmismatch)
                            android.net.http.SslError.SSL_NOTYETVALID ->
                                    context.getString(R.string.webview_ssl_error_notyetvalid)
                            android.net.http.SslError.SSL_DATE_INVALID ->
                                    context.getString(R.string.webview_ssl_error_date_invalid)
                            else -> context.getString(R.string.webview_ssl_error_unknown)
                        }
                message += "\n\n" + errorDetail
                message += "\n\n" + (error?.url ?: "")

                builder.setTitle(context.getString(R.string.webview_security_warning_title))
                builder.setMessage(message)

                builder.setPositiveButton(context.getString(R.string.continue_action)) { _, _ ->
                    handler?.proceed()
                }
                builder.setNegativeButton(context.getString(R.string.cancel)) { _, _ -> handler?.cancel() }

                // 在UI线程上显示对话框
                Handler(Looper.getMainLooper()).post { builder.create().show() }
            }

            // 重写此方法以拦截和修改请求
            override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
            ): WebResourceResponse? {
                // 添加必要的CORS头
                if (request?.url?.scheme == "http" || request?.url?.scheme == "https") {
                    val url = request.url.toString()
                    if (url.contains("localhost") || url.contains("127.0.0.1")) {
                        // 对本地请求不做特殊处理
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    // 创建WebChromeClient
    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged?.invoke(newProgress)
                notifyNavigationState(view)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onTitleReceived?.invoke(title)
            }

            // 处理文件选择
            override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
            ): Boolean {
                // 保存回调引用
                this@WebViewHandler.filePathCallback?.onReceiveValue(null)
                this@WebViewHandler.filePathCallback = filePathCallback

                try {
                    // 创建文件选择Intent
                    val intent =
                            fileChooserParams?.createIntent()
                                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "*/*"
                                    }

                    // 如果需要多选
                    if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }

                    // 启动文件选择器
                    val chooserIntent =
                            Intent.createChooser(intent, context.getString(R.string.webview_choose_file))

                    // 这里需要一种方式来启动Activity并获取结果
                    // 由于我们没有Activity的直接引用，需要外部传入处理方法
                    // 这里使用接口回调的方法传递给外部实现
                    onFileChooserRequest?.invoke(chooserIntent) { resultCode, data ->
                        handleFileChooserResult(resultCode, data)
                    }

                    return true
                } catch (e: Exception) {
                    AppLogger.e("WebViewHandler", "无法打开文件选择器", e)
                    filePathCallback?.onReceiveValue(null)
                    this@WebViewHandler.filePathCallback = null
                    return false
                }
            }

            // 处理权限请求
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
    }

    // 创建下载监听器
    private fun createDownloadListener(): DownloadListener {
        return DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            try {
                // 检查是否是Blob URL
                if (url.startsWith("blob:")) {
                    // 注入JavaScript将Blob转换为Base64并通过JavaScript接口传递
                    injectBlobDownloaderScript(url)
                    return@DownloadListener
                }

                // 处理常规下载
                handleRegularDownload(url, userAgent, contentDisposition, mimetype, contentLength)
            } catch (e: Exception) {
                AppLogger.e("WebViewHandler", "下载失败: ${e.message}", e)
                OKToast.error(context, context.getString(R.string.download_failed, e.message), OKToast.LENGTH_LONG).show()
            }
        }
    }

    // 处理常规下载（非Blob）
    private fun handleRegularDownload(
            url: String,
            userAgent: String,
            contentDisposition: String?,
            mimetype: String,
            contentLength: Long
    ) {
        try {
            // 解析文件名
            var filename =
                    contentDisposition?.let { parseFilename(it) } ?: url.substringAfterLast('/')

            // 如果无法识别文件名，使用时间戳创建一个
            if (filename.isNullOrEmpty() || filename == "/") {
                filename = "download_${System.currentTimeMillis()}"
                // 根据MIME类型添加合适的扩展名
                when (mimetype) {
                    "application/pdf" -> filename += ".pdf"
                    "image/jpeg" -> filename += ".jpg"
                    "image/png" -> filename += ".png"
                    "text/html" -> filename += ".html"
                    "application/zip" -> filename += ".zip"
                    "application/vnd.android.package-archive" -> filename += ".apk"
                    else -> filename += determineExtensionFromMimeType(mimetype)
                }
            }

            // 清理文件名（移除不安全字符）
            filename = sanitizeFileName(filename)

            // 使用DownloadManager处理下载
            val request = android.app.DownloadManager.Request(Uri.parse(url))

            // 设置下载参数
            request.setMimeType(mimetype)
            request.addRequestHeader("User-Agent", userAgent)
            request.setTitle(filename)
            request.setDescription(context.getString(R.string.download_file_description))
            request.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            // 添加cookie（如果需要）
            val cookieManager = CookieManager.getInstance()
            val cookie = cookieManager.getCookie(url)
            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie)
            }

            // 设置下载目标
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

            // 获取下载管理器服务
            val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as
                            android.app.DownloadManager

            // 将下载请求加入队列
            val downloadId = downloadManager.enqueue(request)

            // 显示下载开始消息
            OKToast.info(context, context.getString(R.string.download_start, filename), OKToast.LENGTH_SHORT).show()

            // 注册下载完成的广播接收器
            val onDownloadComplete =
                    object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val id =
                                    intent.getLongExtra(
                                            android.app.DownloadManager.EXTRA_DOWNLOAD_ID,
                                            -1
                                    )
                            if (id == downloadId) {
                                // 下载完成，查询下载状态
                                val query =
                                        android.app.DownloadManager.Query()
                                                .setFilterById(downloadId)
                                val cursor = downloadManager.query(query)

                                if (cursor.moveToFirst()) {
                                    val columnIndex =
                                            cursor.getColumnIndex(
                                                    android.app.DownloadManager.COLUMN_STATUS
                                            )
                                    if (columnIndex >= 0) {
                                        val status = cursor.getInt(columnIndex)
                                        if (status == android.app.DownloadManager.STATUS_SUCCESSFUL
                                        ) {
                                            // 获取文件路径
                                            val uriColumnIndex =
                                                    cursor.getColumnIndex(
                                                            android.app.DownloadManager
                                                                    .COLUMN_LOCAL_URI
                                                    )
                                            if (uriColumnIndex >= 0) {
                                                val uriString = cursor.getString(uriColumnIndex)
                                                if (uriString != null) {
                                                    val fileUri = Uri.parse(uriString)
                                                    OKToast.success(
                                                                    context,
                                                                    context.getString(R.string.download_complete, filename),
                                                                    OKToast.LENGTH_SHORT
                                                            )
                                                            .show()
                                                }
                                            }
                                        } else {
                                            OKToast.error(
                                                            context,
                                                            context.getString(R.string.download_incomplete, filename),
                                                            OKToast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }
                                }
                                cursor.close()

                                // 注销广播接收器
                                context.unregisterReceiver(this)
                            }
                        }
                    }

            // 注册广播接收器
            context.registerReceiver(
                    onDownloadComplete,
                    android.content.IntentFilter(
                            android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE
                    )
            )
        } catch (e: Exception) {
            throw e
        }
    }

    // 在WebView中注入辅助代码，用于处理Blob下载
    private fun injectBlobDownloadHelper(webView: WebView) {
        val js =
                """
        if (!window.blobDownloaderInjected) {
            window.blobDownloaderInjected = true;
            
            // 拦截所有a标签的下载
            document.addEventListener('click', function(e) {
                const a = e.target.closest('a');
                if (a && a.href && a.href.startsWith('blob:')) {
                    e.preventDefault();
                    downloadBlobUrl(a.href, a.download || 'download_' + Date.now());
                    return false;
                }
            });
            
            // 监听自定义下载事件
            window.addEventListener('download-blob', function(e) {
                if (e.detail && e.detail.url && e.detail.url.startsWith('blob:')) {
                    downloadBlobUrl(e.detail.url, e.detail.filename || 'download_' + Date.now());
                }
            });
            
            // 在全局提供下载Blob URL的函数
            window.downloadBlobUrl = function(blobUrl, filename) {
                try {
                    NativeBridge.log('正在下载Blob: ' + blobUrl + ', 文件名: ' + filename);
                    
                    // 获取Blob数据
                    fetch(blobUrl)
                        .then(response => response.blob())
                        .then(blob => {
                            const reader = new FileReader();
                            reader.onload = function() {
                                // 将读取到的数据传给原生接口
                                NativeBridge.downloadBlob(reader.result, filename, blob.type);
                            };
                            reader.onerror = function() {
                                NativeBridge.log('读取Blob失败: ' + reader.error);
                            };
                            reader.readAsDataURL(blob);
                        })
                        .catch(error => {
                            NativeBridge.log('获取Blob数据失败: ' + error);
                        });
                } catch (error) {
                    NativeBridge.log('下载Blob过程出错: ' + error);
                }
            };
        }
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun injectWorkspaceCorsProxyHelper(webView: WebView) {
        val js =
            """
        (function() {
            if (window.__operitCorsProxyInjected) { return; }
            window.__operitCorsProxyInjected = true;
            const proxyBase = 'http://localhost:${LocalWebServer.WORKSPACE_PORT}/api/proxy?url=';
            function shouldProxy(url) {
                try {
                    const resolved = new URL(url, window.location.href);
                    if (resolved.protocol !== 'http:' && resolved.protocol !== 'https:') return false;
                    if (resolved.hostname === 'localhost' || resolved.hostname === '127.0.0.1') return false;
                    return true;
                } catch (e) {
                    return false;
                }
            }
            function toProxyUrl(url) {
                const resolved = new URL(url, window.location.href);
                return proxyBase + encodeURIComponent(resolved.toString());
            }

            const originalFetch = window.fetch.bind(window);
            window.fetch = function(input, init) {
                try {
                    if (input instanceof Request && shouldProxy(input.url)) {
                        const proxyRequest = new Request(toProxyUrl(input.url), input);
                        return originalFetch(proxyRequest);
                    }
                    if (typeof input === 'string' && shouldProxy(input)) {
                        return originalFetch(toProxyUrl(input), init);
                    }
                } catch (e) {}
                return originalFetch(input, init);
            };

            const originalOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
                try {
                    if (shouldProxy(url)) {
                        url = toProxyUrl(url);
                    }
                } catch (e) {}
                return originalOpen.call(this, method, url, async !== undefined ? async : true, user, password);
            };
        })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    // 注入特定Blob URL的下载脚本
    private fun injectBlobDownloaderScript(blobUrl: String) {
        val webView = currentWebView ?: return
        val timestamp = System.currentTimeMillis()
        val filename = "download_$timestamp"

        val js =
                """
        (function() {
            try {
                fetch('$blobUrl')
                    .then(response => response.blob())
                    .then(blob => {
                        const reader = new FileReader();
                        reader.onload = function() {
                            NativeBridge.downloadBlob(reader.result, '$filename', blob.type);
                        };
                        reader.readAsDataURL(blob);
                    })
                    .catch(error => {
                        NativeBridge.log('获取Blob数据失败: ' + error);
                    });
            } catch(error) {
                NativeBridge.log('下载Blob过程出错: ' + error);
            }
        })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    // 从Content-Disposition头解析文件名
    private fun parseFilename(contentDisposition: String): String? {
        try {
            // 支持多种Content-Disposition格式
            val filenamePattern =
                    "filename\\s*=\\s*\"?([^\";]*)\"?".toRegex(RegexOption.IGNORE_CASE)
            val match = filenamePattern.find(contentDisposition)
            return match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            AppLogger.e("WebViewHandler", "解析文件名出错: ${e.message}")
            return null
        }
    }

    // 根据MIME类型确定文件扩展名
    private fun determineExtensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.startsWith("image/") -> {
                val subType = mimeType.substringAfter("/")
                ".$subType"
            }
            mimeType.startsWith("audio/") -> {
                val subType = mimeType.substringAfter("/")
                ".$subType"
            }
            mimeType.startsWith("video/") -> {
                val subType = mimeType.substringAfter("/")
                ".$subType"
            }
            mimeType.contains("javascript") -> ".js"
            mimeType.contains("json") -> ".json"
            mimeType.contains("xml") -> ".xml"
            mimeType.contains("csv") -> ".csv"
            mimeType.contains("plain") -> ".txt"
            else -> ".bin"
        }
    }

    // 清理文件名，去除不安全字符
    private fun sanitizeFileName(fileName: String): String {
        val sanitized = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        return if (sanitized.isBlank()) {
            "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
        } else {
            sanitized
        }
    }

    // 打开下载的文件
    private fun openDownloadedFile(file: File, mimeType: String) {
        try {
            val uri =
                    FileProvider.getUriForFile(
                            context,
                            context.applicationContext.packageName + ".fileprovider",
                            file
                    )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("WebViewHandler", "无法打开文件", e)
        }
    }

    // 处理文件选择结果
    fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val results =
                    when {
                        data.clipData != null -> {
                            val clipData = data.clipData!!
                            Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                        }
                        data.data != null -> arrayOf(data.data!!)
                        else -> arrayOf()
                    }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // 外部回调接口，用于处理文件选择请求
    var onFileChooserRequest: ((Intent, (Int, Intent?) -> Unit) -> Unit)? = null

    // 当前WebView引用，用于JavaScript注入
    var currentWebView: WebView? = null
}
