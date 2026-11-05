package com.ai.assistance.operit.ui.features.annualreport

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.PixelCopy
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.ai.assistance.operit.data.annualreport.AnnualReportCollector
import com.ai.assistance.operit.ui.features.chat.components.saveShareImageToGallery
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 年度报告的全屏宿主。页面是打包进 assets/annual-report/ 的本地 HTML，完全离线：
 * 资源由 WebViewAssetLoader 提供，用户数据由 /report-data/ 路径在本机生成后返回，其他请求一律拒绝。
 * 入口只通过 [AnnualReportLauncher] 启动。
 */
class AnnualReportActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var dataSource: AnnualReportDataSource

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val year = intent.getIntExtra(EXTRA_YEAR, 0)
        require(year > 0) { "年度报告缺少年份参数" }
        val theme = intent.getStringExtra(EXTRA_THEME)
        require(theme == THEME_LIGHT || theme == THEME_DARK) { "年度报告缺少有效的主题参数：$theme" }
        val paper = if (theme == THEME_DARK) PAPER_DARK else PAPER_LIGHT

        window.setBackgroundDrawable(ColorDrawable(paper))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 一打开就开始采集，页面先播项目篇，读到个人篇时数据通常已经就绪。
        dataSource = AnnualReportDataSource(AnnualReportCollector(this), lifecycleScope, year)
        dataSource.start()

        val assetLoader =
            WebViewAssetLoader.Builder()
                .setDomain(ASSET_DOMAIN)
                .addPathHandler("/annual-report/", ScopedAssetsPathHandler(this, ASSET_ROOT))
                .addPathHandler("/report-data/", ReportDataPathHandler(dataSource))
                .build()

        webView =
            WebView(this).apply {
                setBackgroundColor(paper)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.textZoom = 100
                webViewClient = OfflineWebViewClient(assetLoader)
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val line = "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                AppLogger.e(TAG, "[page] $line")
                            } else {
                                AppLogger.d(TAG, "[page] $line")
                            }
                            return true
                        }
                    }
                addJavascriptInterface(AnnualReportBridge(this@AnnualReportActivity), BRIDGE_NAME)
            }
        setContentView(webView)
        webView.loadUrl("https://$ASSET_DOMAIN/annual-report/index.html?theme=$theme&year=$year")
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.destroy()
        super.onDestroy()
    }

    internal fun performHaptic(kind: String) {
        val constant =
            when (kind) {
                "tick" -> HapticFeedbackConstants.CLOCK_TICK
                "tap" -> HapticFeedbackConstants.KEYBOARD_TAP
                "success" -> HapticFeedbackConstants.CONTEXT_CLICK
                "heavy" -> HapticFeedbackConstants.LONG_PRESS
                else -> {
                    AppLogger.e(TAG, "页面请求了未知的触感类型：$kind")
                    return
                }
            }
        webView.performHapticFeedback(constant)
    }

    /** 截取海报区域。坐标是页面的 CSS 像素，按页面给出的像素比换算成窗口坐标。 */
    internal fun capturePoster(action: String, left: Double, top: Double, width: Double, height: Double, ratio: Double) {
        val origin = IntArray(2)
        webView.getLocationInWindow(origin)
        val decor = window.decorView
        val rect =
            Rect(
                (origin[0] + left * ratio).roundToInt().coerceIn(0, decor.width),
                (origin[1] + top * ratio).roundToInt().coerceIn(0, decor.height),
                (origin[0] + (left + width) * ratio).roundToInt().coerceIn(0, decor.width),
                (origin[1] + (top + height) * ratio).roundToInt().coerceIn(0, decor.height),
            )
        if (rect.width() <= 0 || rect.height() <= 0) {
            notifyPoster(action, false, "海报不在屏幕范围内，没法截图")
            return
        }
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            window,
            rect,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    lifecycleScope.launch { deliverPoster(action, bitmap) }
                } else {
                    AppLogger.e(TAG, "海报截图失败，PixelCopy 结果码 $result")
                    notifyPoster(action, false, "截图失败（错误码 $result）")
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    private suspend fun deliverPoster(action: String, bitmap: Bitmap) {
        try {
            val file =
                withContext(Dispatchers.IO) {
                    val directory = File(cacheDir, "annual-report").apply { mkdirs() }
                    File(directory, "poster-${System.currentTimeMillis()}.png").also { target ->
                        target.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
                    }
                }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            when (action) {
                "save" -> {
                    val saved = saveShareImageToGallery(this, uri)
                    notifyPoster(action, saved, if (saved) "已保存到相册" else "保存失败，请检查存储权限")
                }
                "share" -> {
                    val send =
                        Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, "分享年度报告"))
                    notifyPoster(action, true, "已打开分享")
                }
                else -> {
                    AppLogger.e(TAG, "页面请求了未知的海报操作：$action")
                    notifyPoster(action, false, "未知的操作：$action")
                }
            }
        } catch (error: Exception) {
            AppLogger.e(TAG, "处理海报失败", error)
            notifyPoster(action, false, "处理海报失败：$error")
        } finally {
            bitmap.recycle()
        }
    }

    private fun notifyPoster(action: String, ok: Boolean, message: String) {
        webView.evaluateJavascript(
            "window.__annualReportPosterResult(${JSONObject.quote(action)}, $ok, ${JSONObject.quote(message)})",
            null,
        )
    }

    companion object {
        private const val TAG = "AnnualReportActivity"
        const val EXTRA_YEAR = "annual_report_year"
        const val EXTRA_THEME = "annual_report_theme"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private const val ASSET_DOMAIN = "appassets.androidplatform.net"
        private const val ASSET_ROOT = "annual-report"
        private const val BRIDGE_NAME = "OperitAnnualReport"
        private const val PAPER_LIGHT = 0xFFF6F0E2.toInt()
        private const val PAPER_DARK = 0xFF1B1C28.toInt()
    }
}
