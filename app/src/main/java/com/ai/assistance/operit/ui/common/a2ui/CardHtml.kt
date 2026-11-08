package com.ai.assistance.operit.ui.common.a2ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.*
import android.widget.FrameLayout
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt
import java.util.UUID
import com.ai.assistance.operit.R

internal object CardHtml : A2uiComponent {
    override val name = "Html"
    override val description = "An offline HTML/CSS visual, without author scripts, network or app access."
    private val html = A2uiProperty.dynamicString("html")
    private val css = A2uiProperty.dynamicString("css")
    private val label = A2uiProperty.dynamicString("label")
    private val maxHeight = A2uiProperty.dynamicNumber("maxHeight")
    override val properties = listOf(html, css, label, maxHeight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val source = properties.bind(html).orEmpty()
        val styles = properties.bind(css).orEmpty()
        val description = properties.bind(label)
        val limit = (properties.bind(maxHeight)?.toInt() ?: 720).coerceIn(120, 900)
        val contentKey = remember(source, styles) { A2uiProtocol.fingerprint("$source\u0000$styles") }
        var approvedContent by rememberSaveable { mutableStateOf<String?>(null) }
        val loaded = approvedContent == contentKey
        var measured by rememberSaveable(source, styles) { mutableIntStateOf(120) }
        var visible by remember { mutableStateOf(false) }
        val owner = LocalLifecycleOwner.current
        var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, _ ->
                active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        Box(modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            visible = coordinates.isAttached && bounds.width > 0f && bounds.height > 0f
        }) {
            if (!loaded) {
                OutlinedButton(onClick = { approvedContent = contentKey }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.a2ui_html_click_to_load))
                }
            } else {
                // Keep the measured slot stable during offscreen disposal, so chat does not jump.
                Box(Modifier.fillMaxWidth().height(measured.coerceIn(120, limit).dp)) {
                    HtmlVisual(source, styles, description, measured.coerceIn(120, limit), visible && active) {
                        measured = it
                    }
                }
            }
        }
    }
}

@Composable
private fun HtmlVisual(source: String, styles: String, description: String?, height: Int, active: Boolean, onHeight: (Int) -> Unit) {
        val scheme = MaterialTheme.colorScheme
        fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)
        val heightToken = remember(source, styles, scheme) { UUID.randomUUID().toString() }
        val document = remember(source, styles, scheme) {
            CardHtmlDocument.document(source, styles, hex(scheme.onSurface), hex(scheme.surface),
                hex(scheme.primary), scheme.surface.luminance() < .5f, heightToken)
        }
        val zoom = (LocalDensity.current.fontScale * 100).roundToInt()
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(height.dp),
            // Keep the Compose interop node stable when viewport visibility changes. Removing it
            // during lazy layout reuse can leave the parent placing a detached LayoutNode.
            factory = { context -> HtmlVisualHost(context) },
            update = { host ->
                if (!active) {
                    host.releaseVisual()
                    return@AndroidView
                }
                val view = host.visual()
                view.contentDescription = description
                view.settings.textZoom = zoom
                view.onContentHeight = onHeight
                if (view.document != document) {
                    view.document = document
                    view.heightToken = heightToken
                    view.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
                }
            },
            // 持有 WebView 的承载器不参与组合复用，避免标签切换时重置旧组合。
            onReset = null,
            onRelease = { it.releaseVisual() },
        )
}

/** Only this empty, lightweight host survives offscreen; the WebView itself is destroyed. */
internal class HtmlVisualHost(context: Context) : FrameLayout(context) {
    private var webView: OfflineCardWebView? = null
    private val releaseHandler = Handler(Looper.getMainLooper())

    fun visual(): OfflineCardWebView = webView ?: OfflineCardWebView(context).also {
        webView = it
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun releaseVisual() {
        val view = webView ?: return
        webView = null
        view.onContentHeight = {}
        view.heightToken = null
        // 先移除并解绑旧页面，迟到的高度通知不再触碰当前组合。
        removeView(view)
        // 销毁会触发平台视图回调，等当前 Compose 提交结束后再执行，避免重入。
        releaseHandler.post {
            view.stopLoading()
            view.onPause()
            view.destroy()
        }
    }
}

/** No JavascriptInterface is ever attached to generated content. */
internal class OfflineCardWebView(context: Context) : WebView(context) {
    var document: String? = null
    var onContentHeight: (Int) -> Unit = {}
    var heightToken: String? = null
    init {
        settings.apply {
            // CSP permits only our nonce-bound height observer, never authored scripts.
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            setSupportZoom(false)
        }
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                val prefix = heightToken?.let { "$it:" } ?: return
                if (title?.startsWith(prefix) == true) {
                    title.removePrefix(prefix).toIntOrNull()?.takeIf { it >= 0 }?.let(onContentHeight)
                }
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            @Deprecated("Legacy callback")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(canScrollVertically(1) || canScrollVertically(-1))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }
}
