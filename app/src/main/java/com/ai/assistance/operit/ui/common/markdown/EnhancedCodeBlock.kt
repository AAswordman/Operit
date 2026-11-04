package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ai.assistance.operit.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CodeBlockPreviewType {
    MERMAID,
    HTML
}

/**
 * 增强型代码块组件
 *
 * 具有以下功能：
 * 1. 代码语法高亮
 * 2. 复制按钮
 * 3. 行号显示
 * 4. 夜间模式风格
 * 5. 行渲染保护（使用key机制避免重复渲染）
 * 6. 记忆优化（减少不必要的重组）
 * 7. 高效处理流式更新
 * 8. Mermaid图表支持
 */
@Composable
fun EnhancedCodeBlock(code: String, language: String = "", modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showCopiedToast by remember { mutableStateOf(false) }
    var autoWrapEnabled by remember { mutableStateOf(true) }
    val markdownRenderMode = LocalMarkdownRenderMode.current
    val preferStreamingBody = markdownRenderMode == MarkdownRenderMode.STREAMING

    // 检测是否为Mermaid代码
    val isMermaid = language.equals("mermaid", ignoreCase = true)

    // 检测是否为HTML代码
    val isHtml = language.equals("html", ignoreCase = true) || language.equals("htm", ignoreCase = true)

    // Mermaid渲染状态
    var showRenderedMermaid by remember { mutableStateOf(false) }

    // HTML预览状态
    var showRenderedHtml by remember { mutableStateOf(false) }

    var showFullscreenPreview by remember { mutableStateOf(false) }
    var fullscreenPreviewType by remember { mutableStateOf<CodeBlockPreviewType?>(null) }

    val configuration = LocalConfiguration.current
    val maxScrollableHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp * 0.5f).coerceIn(240.dp, 560.dp)
    }

    val isPreviewMode = (isMermaid && showRenderedMermaid) || (isHtml && showRenderedHtml)

    // 处理复制事件
    val handleCopy: () -> Unit = {
        clipboardManager.setText(AnnotatedString(code))
        scope.launch {
            showCopiedToast = true
            delay(1500)
            showCopiedToast = false
        }
    }

    // 处理Mermaid渲染切换
    val handleToggleMermaid: () -> Unit = { showRenderedMermaid = !showRenderedMermaid }

    // 处理HTML预览切换
    val handleToggleHtml: () -> Unit = { showRenderedHtml = !showRenderedHtml }

    // 暗色代码块背景颜色
    val codeBlockBackground = Color(0xFF1E1E1E) // VS Code 暗色主题背景色
    val toolbarBackground = Color(0xFF252526) // 比背景稍亮一点的颜色

    // 直接从 `code` prop 派生行列表。
    // 这种方法比使用 LaunchedEffect 和 mutableStateListOf 更稳定，
    // 可以防止因状态更新时序问题而导致的双重渲染。
    val codeLines = remember(code) { code.lines() }

    // 缓存已计算过的行，避免重复创建
    val lineCache = remember { mutableMapOf<String, AnnotatedString>() }
    val highlightedLines =
        remember(codeLines, language, preferStreamingBody) {
            if (preferStreamingBody) {
                emptyList()
            } else {
                codeLines.map { line ->
                    val cacheKey = "$language:$line"
                    lineCache[cacheKey] ?: highlightSyntaxLine(line, language).also {
                        lineCache[cacheKey] = it
                    }
                }
            }
        }

    // 无障碍朗读描述：只朗读块类型
    val accessibilityDesc = if (language.isNotEmpty()) {
        "$language ${stringResource(R.string.code_block)}"
    } else {
        stringResource(R.string.code_block)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { contentDescription = accessibilityDesc },
        color = codeBlockBackground,
        shape = RoundedCornerShape(4.dp)
    ) {
        Column {
            // 顶部工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(toolbarBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语言标记（如果有）
                if (language.isNotEmpty()) {
                    Text(
                        text = language,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // 工具栏按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mermaid渲染按钮（如果是Mermaid图表则显示）
                    if (isMermaid) {
                        IconButton(
                            onClick = handleToggleMermaid,
                            modifier = Modifier.size(28.dp),
                            enabled = !preferStreamingBody,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription =
                                    if (showRenderedMermaid) stringResource(R.string.common_show_code) else stringResource(R.string.common_render_mermaid),
                                tint =
                                    if (preferStreamingBody)
                                        Color(0xFF666666)
                                    else if (showRenderedMermaid)
                                        MaterialTheme.colorScheme.primary
                                    else Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // HTML预览按钮（如果是HTML则显示）
                    if (isHtml) {
                        IconButton(
                            onClick = handleToggleHtml,
                            modifier = Modifier.size(28.dp),
                            enabled = !preferStreamingBody,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = if (showRenderedHtml) stringResource(R.string.common_show_code) else stringResource(R.string.common_preview_html),
                                tint =
                                    if (preferStreamingBody)
                                        Color(0xFF666666)
                                    else if (showRenderedHtml)
                                        MaterialTheme.colorScheme.primary
                                    else Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isPreviewMode) {
                        IconButton(
                            onClick = {
                                fullscreenPreviewType =
                                    when {
                                        isMermaid && showRenderedMermaid -> CodeBlockPreviewType.MERMAID
                                        isHtml && showRenderedHtml -> CodeBlockPreviewType.HTML
                                        else -> null
                                    }
                                if (fullscreenPreviewType != null) {
                                    showFullscreenPreview = true
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.common_fullscreen_preview),
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { autoWrapEnabled = !autoWrapEnabled },
                        modifier = Modifier.size(28.dp),
                        enabled = !isPreviewMode
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = if (autoWrapEnabled) stringResource(R.string.common_disable_autowrap) else stringResource(R.string.common_enable_autowrap),
                            tint = if (!autoWrapEnabled) MaterialTheme.colorScheme.primary else Color(
                                0xFFAAAAAA
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // 复制按钮
                    IconButton(onClick = handleCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.common_copy_code),
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 代码内容
            if (isMermaid && showRenderedMermaid) {
                // 渲染Mermaid图表
                MermaidRenderer(code = code, modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp))
            } else if (isHtml && showRenderedHtml) {
                HtmlPreviewRenderer(
                    code = code,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                )
            } else {
                CanvasMonospaceCodeBlockBody(
                    code = code,
                    codeLines = codeLines,
                    highlightedLines = if (preferStreamingBody) null else highlightedLines,
                    autoWrapEnabled = autoWrapEnabled,
                    maxScrollableHeight = maxScrollableHeight,
                )
            }

            if (showCopiedToast) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(4.dp),
                    color = Color(0xFF0366D6),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_copied),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showFullscreenPreview) {
        Dialog(
            onDismissRequest = {
                showFullscreenPreview = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (fullscreenPreviewType) {
                        CodeBlockPreviewType.MERMAID -> MermaidRenderer(code = code, modifier = Modifier.fillMaxSize())
                        CodeBlockPreviewType.HTML -> HtmlPreviewRenderer(code = code, modifier = Modifier.fillMaxSize())
                        null -> Unit
                    }

                    IconButton(
                        onClick = { showFullscreenPreview = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_exit_fullscreen),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** Mermaid图表渲染组件 */
@Composable
fun MermaidRenderer(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 创建HTML模板
    val htmlContent =
        remember(code) {
            """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>Mermaid Diagram</title>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10.6.1/dist/mermaid.min.js"></script>
            <style>
                body { 
                    background-color: #1E1E1E; 
                    margin: 0; 
                    padding: 16px;
                    touch-action: pan-x pan-y;
                    overflow: auto;
                    -webkit-overflow-scrolling: touch;
                    height: 100vh;
                }
                #diagram-wrapper {
                    display: block;
                    margin: 0 auto;
                }
                #diagram { 
                    display: inline-block;
                    touch-action: manipulation;
                }
                .mermaid { 
                    font-family: 'Courier New', Courier, monospace;
                    font-size: 14px;
                }
                
                /* 添加自定义缩放控件 */
                .zoom-controls {
                    position: fixed;
                    bottom: 10px;
                    right: 10px;
                    display: flex;
                    flex-direction: column;
                    background-color: rgba(30, 30, 30, 0.7);
                    border-radius: 4px;
                    padding: 4px;
                }
                .zoom-btn {
                    background: #383838;
                    color: #AAA;
                    border: none;
                    width: 28px;
                    height: 28px;
                    margin: 2px;
                    font-size: 18px;
                    border-radius: 3px;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .zoom-btn:hover {
                    background: #505050;
                    color: #FFF;
                }
            </style>
        </head>
        <body>
            <div id="diagram-wrapper">
                <div id="diagram">
                    <pre class="mermaid">
                        ${code.trim()}
                    </pre>
                </div>
            </div>
            <div class="zoom-controls">
                <button class="zoom-btn" onclick="zoomIn()">+</button>
                <button class="zoom-btn" onclick="zoomOut()">-</button>
                <button class="zoom-btn" onclick="resetZoom()">↺</button>
            </div>
            <script>
                mermaid.initialize({
                    startOnLoad: true,
                    theme: 'dark',
                    securityLevel: 'loose',
                    flowchart: { htmlLabels: true }
                });
                
                // 自定义缩放功能
                let scale = 1.0;
                const wrapper = document.getElementById('diagram-wrapper');
                const diagram = document.getElementById('diagram');
                const scroller = document.scrollingElement || document.documentElement;
                let baseWidth = 0;
                let baseHeight = 0;
                
                function zoomIn() {
                    scale = Math.min(scale + 0.2, 3.0);
                    applyZoom();
                }
                
                function zoomOut() {
                    scale = Math.max(scale - 0.2, 0.5);
                    applyZoom();
                }
                
                function resetZoom() {
                    scale = 1.0;
                    applyZoom();
                }
                
                function applyZoom() {
                    if (!baseWidth || !baseHeight) {
                        baseWidth = diagram.scrollWidth || diagram.getBoundingClientRect().width;
                        baseHeight = diagram.scrollHeight || diagram.getBoundingClientRect().height;
                    }
                    wrapper.style.width = (baseWidth * scale) + 'px';
                    wrapper.style.height = (baseHeight * scale) + 'px';
                    diagram.style.transform = 'scale(' + scale + ')';
                    diagram.style.transformOrigin = '0 0';
                }

                function captureBaseSize() {
                    baseWidth = 0;
                    baseHeight = 0;
                    wrapper.style.width = '';
                    wrapper.style.height = '';
                    diagram.style.transform = '';
                    diagram.style.transformOrigin = '';

                    let tries = 0;
                    function tick() {
                        const w = diagram.scrollWidth;
                        const h = diagram.scrollHeight;
                        if (w > 0 && h > 0) {
                            baseWidth = w;
                            baseHeight = h;
                            applyZoom();
                            return;
                        }
                        tries++;
                        if (tries < 60) {
                            requestAnimationFrame(tick);
                        } else {
                            baseWidth = diagram.getBoundingClientRect().width;
                            baseHeight = diagram.getBoundingClientRect().height;
                            applyZoom();
                        }
                    }
                    requestAnimationFrame(tick);
                }

                window.addEventListener('load', function() {
                    setTimeout(captureBaseSize, 0);
                });
                
                // 添加触摸拖动支持
                let isDragging = false;
                let startX, startY, scrollLeft, scrollTop;
                
                document.addEventListener('mousedown', function(e) {
                    if (e.target.closest('.zoom-controls')) return;
                    
                    isDragging = true;
                    startX = e.clientX;
                    startY = e.clientY;
                    scrollLeft = scroller.scrollLeft;
                    scrollTop = scroller.scrollTop;
                });
                
                document.addEventListener('mousemove', function(e) {
                    if (!isDragging) return;
                    e.preventDefault();
                    
                    const x = e.clientX;
                    const y = e.clientY;
                    const moveX = (x - startX);
                    const moveY = (y - startY);
                    
                    scroller.scrollTo(scrollLeft - moveX, scrollTop - moveY);
                });
                
                document.addEventListener('mouseup', function() {
                    isDragging = false;
                });
                
                // 触摸支持
                document.addEventListener('touchstart', function(e) {
                    if (e.target.closest('.zoom-controls')) return;
                    if (e.touches.length === 1) {
                        isDragging = true;
                        startX = e.touches[0].clientX;
                        startY = e.touches[0].clientY;
                        scrollLeft = scroller.scrollLeft;
                        scrollTop = scroller.scrollTop;
                    }
                }, {passive: false});
                
                document.addEventListener('touchmove', function(e) {
                    if (!isDragging) return;
                    
                    if (e.touches.length === 1) {
                        const x = e.touches[0].clientX;
                        const y = e.touches[0].clientY;
                        const moveX = (x - startX);
                        const moveY = (y - startY);
                        
                        scroller.scrollTo(scrollLeft - moveX, scrollTop - moveY);
                    }
                }, {passive: false});
                
                document.addEventListener('touchend', function() {
                    isDragging = false;
                });
                
                // 双指捏合缩放支持
                let initialDistance = 0;
                let initialScale = 1.0;
                
                document.addEventListener('touchstart', function(e) {
                    if (e.touches.length === 2) {
                        initialDistance = Math.hypot(
                            e.touches[0].pageX - e.touches[1].pageX,
                            e.touches[0].pageY - e.touches[1].pageY
                        );
                        initialScale = scale;
                    }
                }, {passive: false});
                
                document.addEventListener('touchmove', function(e) {
                    if (e.touches.length === 2) {
                        e.preventDefault(); // 防止默认缩放
                        
                        const distance = Math.hypot(
                            e.touches[0].pageX - e.touches[1].pageX,
                            e.touches[0].pageY - e.touches[1].pageY
                        );
                        
                        const delta = distance / initialDistance;
                        scale = Math.min(Math.max(initialScale * delta, 0.5), 3.0);
                        applyZoom();
                    }
                }, {passive: false});
            </script>
        </body>
        </html>
        """.trimIndent()
        }

    // 记住WebView实例以便重用
    val webView = remember {
        WebView(context).apply {
            // 基本设置
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // 允许DOM存储
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            // 禁用WebView内置缩放：Mermaid 使用页面内自定义缩放/拖拽，避免出现二级缩放
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            // 设置混合内容模式
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 设置WebViewClient来拦截事件
            webViewClient =
                object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        url: String
                    ): Boolean {
                        // 拦截所有URL导航，保持在当前WebView内
                        return true
                    }

                    override fun onPageFinished(view: android.webkit.WebView, url: String) {
                        super.onPageFinished(view, url)
                        // 页面加载完成后，可以在这里执行JavaScript
                        view.evaluateJavascript(
                            """
                        // 防止长按文本选择
                        document.body.style.webkitUserSelect = 'none';
                        document.body.style.userSelect = 'none';
                    """.trimIndent(),
                            null
                        )
                    }
                }

            // 处理触摸事件
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            // 设置背景颜色
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
        }
    }

    DisposableEffect(webView) {
        onDispose {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    // 每次代码变化时更新内容
    LaunchedEffect(htmlContent) {
        webView.loadDataWithBaseURL(
            "https://mermaid.js.org/",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    // 渲染WebView
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    AndroidView(
        factory = { webView },
        modifier = modifier.nestedScroll(nestedScrollInterop)
    )
}

@Composable
fun HtmlPreviewRenderer(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val htmlContent = remember(code) { code.trim() }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            webViewClient =
                object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        url: String
                    ): Boolean {
                        return true
                    }
                }

            setBackgroundColor(android.graphics.Color.WHITE)

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(htmlContent) {
        webView.loadDataWithBaseURL(
            null,
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    AndroidView(
        factory = { webView },
        modifier = modifier.nestedScroll(nestedScrollInterop)
    )
}

/** 处理单行代码的语法高亮 */
private fun highlightSyntaxLine(line: String, language: String): AnnotatedString {
    // 夜间模式语法高亮颜色
    val keywordColor = Color(0xFF569CD6) // 蓝色 - 关键字
    val stringColor = Color(0xFFCE9178) // 橙红色 - 字符串
    val commentColor = Color(0xFF6A9955) // 绿色 - 注释
    val numberColor = Color(0xFFB5CEA8) // 淡绿色 - 数字
    val typeColor = Color(0xFF4EC9B0) // 青色 - 类型
    val functionColor = Color(0xFFDCDCAA) // 黄色 - 函数
    val textColor = Color(0xFFD4D4D4) // 浅灰色 - 普通文本

    return buildAnnotatedString {
        when (language.lowercase()) {
            "kotlin", "java", "swift", "typescript", "javascript", "dart" -> {
                // 关键字列表
                val keywords =
                    setOf(
                        "fun",
                        "val",
                        "var",
                        "class",
                        "interface",
                        "object",
                        "return",
                        "if",
                        "else",
                        "when",
                        "for",
                        "while",
                        "do",
                        "break",
                        "continue",
                        "package",
                        "import",
                        "public",
                        "private",
                        "protected",
                        "internal",
                        "const",
                        "final",
                        "static",
                        "abstract",
                        "override",
                        "suspend",
                        "true",
                        "false",
                        "null",
                        "function",
                        "let",
                        "const",
                        "export",
                        "import",
                        "async",
                        "await",
                        "void",
                        "int",
                        "double"
                    )

                val types =
                    setOf(
                        "String",
                        "Int",
                        "Double",
                        "Float",
                        "Boolean",
                        "List",
                        "Map",
                        "Set",
                        "Array",
                        "Number",
                        "Object",
                        "Promise",
                        "void",
                        "any",
                        "never"
                    )

                // 处理注释行
                if (line.trim().startsWith("//")) {
                    withStyle(SpanStyle(color = commentColor)) { append(line) }
                    return@buildAnnotatedString
                }

                // 处理包含内联注释的行
                val commentIndex = line.indexOf("//")
                if (commentIndex > 0) {
                    // 处理注释前的代码
                    processCodePart(
                        line.substring(0, commentIndex),
                        keywords,
                        types,
                        keywordColor,
                        typeColor,
                        functionColor,
                        stringColor,
                        numberColor,
                        textColor
                    )

                    // 处理注释部分
                    withStyle(SpanStyle(color = commentColor)) {
                        append(line.substring(commentIndex))
                    }
                } else {
                    // 处理完整行的代码
                    processCodePart(
                        line,
                        keywords,
                        types,
                        keywordColor,
                        typeColor,
                        functionColor,
                        stringColor,
                        numberColor,
                        textColor
                    )
                }
            }

            "mermaid" -> {
                // Mermaid语法高亮
                // 关键字列表
                val mermaidKeywords =
                    setOf(
                        "graph",
                        "flowchart",
                        "sequenceDiagram",
                        "classDiagram",
                        "stateDiagram",
                        "pie",
                        "gantt",
                        "journey",
                        "gitGraph",
                        "LR",
                        "RL",
                        "TB",
                        "BT",
                        "TD",
                        "class",
                        "subgraph",
                        "end",
                        "title",
                        "participant",
                        "actor",
                        "note",
                        "activate",
                        "deactivate",
                        "loop",
                        "alt",
                        "else",
                        "opt",
                        "par",
                        "state",
                        "section"
                    )

                // 特殊语法
                val arrows = setOf("-->", "---", "===", "---|", "--o", "--x", "===>", "->", "=>")
                val arrowPattern = arrows.joinToString("|") { Regex.escape(it) }
                val arrowRegex = Regex("(\\s*)(${arrowPattern})(\\s*)")

                // 是否有箭头
                val arrowMatch = arrowRegex.find(line)
                if (arrowMatch != null) {
                    // 处理箭头前的部分
                    val beforeArrow = line.substring(0, arrowMatch.range.first)
                    val arrow = arrowMatch.groupValues[2]
                    val afterArrow = line.substring(arrowMatch.range.last + 1)

                    // 正常文本颜色
                    withStyle(SpanStyle(color = textColor)) { append(beforeArrow) }
                    // 箭头颜色（突出显示）
                    withStyle(SpanStyle(color = functionColor)) { append(arrow) }
                    // 之后的文本
                    withStyle(SpanStyle(color = textColor)) { append(afterArrow) }
                } else if (line.trim().startsWith("%")) {
                    // 注释行
                    withStyle(SpanStyle(color = commentColor)) { append(line) }
                } else {
                    // 检查是否有关键字
                    var hasKeyword = false
                    for (keyword in mermaidKeywords) {
                        if (line.contains(keyword, ignoreCase = true)) {
                            val regex = Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE)
                            val parts = regex.split(line)
                            if (parts.size > 1) {
                                hasKeyword = true
                                append(parts[0])
                                for (i in 1 until parts.size) {
                                    withStyle(SpanStyle(color = keywordColor)) { append(keyword) }
                                    append(parts[i])
                                }
                                break
                            }
                        }
                    }

                    // 如果没有关键字，则使用默认颜色
                    if (!hasKeyword) {
                        withStyle(SpanStyle(color = textColor)) { append(line) }
                    }
                }
            }

            else -> {
                // 对于未知语言，使用默认颜色
                withStyle(SpanStyle(color = textColor)) { append(line) }
            }
        }
    }
}

/** 处理代码段，保留空格和标点符号 */
private fun AnnotatedString.Builder.processCodePart(
    code: String,
    keywords: Set<String>,
    types: Set<String>,
    keywordColor: Color,
    typeColor: Color,
    functionColor: Color,
    stringColor: Color,
    numberColor: Color,
    textColor: Color
) {
    var inString = false
    var currentWord = ""
    var currentStringContent = ""

    fun appendWord() {
        if (currentWord.isEmpty()) return

        when {
            currentWord in keywords ->
                withStyle(SpanStyle(color = keywordColor)) { append(currentWord) }

            currentWord in types -> withStyle(SpanStyle(color = typeColor)) { append(currentWord) }
            currentWord.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(")) -> {
                val functionName = currentWord.substring(0, currentWord.indexOfFirst { it == '(' })
                val params = currentWord.substring(functionName.length)
                withStyle(SpanStyle(color = functionColor)) { append(functionName) }
                withStyle(SpanStyle(color = textColor)) { append(params) }
            }

            currentWord.matches(Regex("\\d+(\\.\\d+)?")) ->
                withStyle(SpanStyle(color = numberColor)) { append(currentWord) }

            else -> withStyle(SpanStyle(color = textColor)) { append(currentWord) }
        }
        currentWord = ""
    }

    for (i in code.indices) {
        val c = code[i]

        // 处理字符串
        if (c == '"' || c == '\'') {
            if (!inString) {
                // 开始字符串
                appendWord()
                inString = true
                currentStringContent = c.toString()
            } else {
                // 结束字符串
                currentStringContent += c
                withStyle(SpanStyle(color = stringColor)) { append(currentStringContent) }
                currentStringContent = ""
                inString = false
            }
            continue
        }

        if (inString) {
            currentStringContent += c
            continue
        }

        // 处理非字符串内容
        when {
            c.isLetterOrDigit() || c == '_' -> currentWord += c
            c.isWhitespace() -> {
                appendWord()
                append(c.toString())
            }

            else -> {
                appendWord()
                withStyle(SpanStyle(color = textColor)) { append(c.toString()) }
            }
        }
    }

    // 处理最后一个单词
    appendWord()

    // 如果有未闭合的字符串
    if (currentStringContent.isNotEmpty()) {
        withStyle(SpanStyle(color = stringColor)) { append(currentStringContent) }
    }
}
