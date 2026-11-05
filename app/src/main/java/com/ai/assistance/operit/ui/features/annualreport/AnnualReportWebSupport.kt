package com.ai.assistance.operit.ui.features.annualreport

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.ai.assistance.operit.data.annualreport.AnnualReportCollector
import com.ai.assistance.operit.data.annualreport.PersonalMarket
import com.ai.assistance.operit.data.annualreport.ReportDataError
import com.ai.assistance.operit.data.annualreport.UserReport
import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private const val TAG = "AnnualReportWeb"

// 默认配置就会把 null 字段原样写成 null，前端据此判断某项数据今年是否存在。
private val reportJson: Json = Json

private fun textResponse(status: Int, reason: String, mimeType: String, body: String): WebResourceResponse =
    WebResourceResponse(
        mimeType,
        "utf-8",
        status,
        reason,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)),
    )

/** 错误原样交给页面展示：异常的 toString 带着类型和原因，方便定位。 */
private fun errorJson(error: Exception): WebResourceResponse =
    textResponse(500, "Internal Error", "application/json", reportJson.encodeToString(ReportDataError.serializer(), ReportDataError(error.toString())))

/**
 * 用户数据只在本机生成一次，页面重试时才重新采集。
 * 采集在协程里进行，PathHandler 在 WebView 的后台线程上等它完成再响应。
 */
internal class AnnualReportDataSource(
    private val collector: AnnualReportCollector,
    private val scope: CoroutineScope,
    private val year: Int,
) {
    private var userReport: Deferred<UserReport>? = null

    @Synchronized
    fun start(): Deferred<UserReport> {
        val current = userReport
        // 进行中或已成功的采集直接复用；失败的 Deferred 处于 cancelled 状态，此时重新采集。
        if (current != null && !current.isCancelled) return current
        val next = scope.async { collector.collect(year) }
        userReport = next
        return next
    }

    fun userJson(): WebResourceResponse =
        try {
            val report = runBlocking { start().await() }
            textResponse(200, "OK", "application/json", reportJson.encodeToString(UserReport.serializer(), report))
        } catch (error: Exception) {
            AppLogger.e(TAG, "生成年度报告用户数据失败", error)
            errorJson(error)
        }

    fun marketJson(): WebResourceResponse =
        try {
            val market = runBlocking { collector.collectPersonalMarket() }
            textResponse(200, "OK", "application/json", reportJson.encodeToString(PersonalMarket.serializer(), market))
        } catch (error: Exception) {
            AppLogger.e(TAG, "读取个人插件市场数据失败", error)
            errorJson(error)
        }
}

/** /report-data/ 路径：页面通过 fetch 取用户数据，开发时由 vite 中间件提供同样的路径。 */
internal class ReportDataPathHandler(private val source: AnnualReportDataSource) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse =
        when (path) {
            "user.json" -> source.userJson()
            "market.json" -> source.marketJson()
            else -> textResponse(404, "Not Found", "text/plain", "未知的数据路径：$path")
        }
}

/** 只开放 assets/annual-report/ 目录，assets 里的其他文件对页面不可见。 */
internal class ScopedAssetsPathHandler(context: Context, private val root: String) : WebViewAssetLoader.PathHandler {
    private val assets = context.applicationContext.assets

    override fun handle(path: String): WebResourceResponse {
        if (path.isEmpty() || path.split('/').any { it == ".." }) {
            return textResponse(403, "Forbidden", "text/plain", "拒绝访问：$path")
        }
        val mimeType =
            MIME_TYPES[path.substringAfterLast('.', "").lowercase()]
                ?: return textResponse(404, "Not Found", "text/plain", "不支持的资源类型：$path")
        val stream: InputStream =
            try {
                assets.open("$root/$path")
            } catch (missing: FileNotFoundException) {
                AppLogger.e(TAG, "年度报告资源缺失：$root/$path", missing)
                return if (path == "index.html") {
                    textResponse(
                        404,
                        "Not Found",
                        "text/html",
                        "<meta charset=\"utf-8\"><p style=\"font:16px sans-serif;padding:24px\">这个安装包没有带上年度报告页面。" +
                            "开发构建请先在仓库根目录运行 <code>npm run build:annualreport</code>。</p>",
                    )
                } else {
                    textResponse(404, "Not Found", "text/plain", "缺少资源：$path")
                }
            }
        val encoding = if (mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "image/svg+xml") "utf-8" else null
        return WebResourceResponse(mimeType, encoding, stream)
    }

    private companion object {
        val MIME_TYPES =
            mapOf(
                "html" to "text/html",
                "js" to "text/javascript",
                "css" to "text/css",
                "json" to "application/json",
                "svg" to "image/svg+xml",
                "png" to "image/png",
                "woff2" to "font/woff2",
            )
    }
}

/** 页面完全离线：资源加载器之外的请求一律拒绝，也不允许跳转到别的页面。 */
internal class OfflineWebViewClient(private val assetLoader: WebViewAssetLoader) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val handled = assetLoader.shouldInterceptRequest(request.url)
        if (handled != null) return handled
        AppLogger.w(TAG, "拦截了年度报告页面的外部请求：${request.url}")
        return textResponse(403, "Forbidden", "text/plain", "")
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
}

/** 注入页面的窄接口，方法都在 JavaBridge 线程调用，切回主线程执行。 */
internal class AnnualReportBridge(private val activity: AnnualReportActivity) {
    @JavascriptInterface
    fun close() {
        activity.runOnUiThread { activity.finish() }
    }

    @JavascriptInterface
    fun haptic(kind: String) {
        activity.runOnUiThread { activity.performHaptic(kind) }
    }

    @JavascriptInterface
    fun capturePoster(action: String, left: Double, top: Double, width: Double, height: Double, ratio: Double) {
        activity.runOnUiThread { activity.capturePoster(action, left, top, width, height, ratio) }
    }
}
