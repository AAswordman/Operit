package com.ai.assistance.operit.core.tools.builtin.web

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.logging.AppLogger
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.runtime.ToolExecutor
import com.ai.assistance.operit.core.tools.VisitWebResultData
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import org.json.JSONObject

/** Tool for web page visiting and content extraction */
class StandardWebVisitTool(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "WebVisitTool"
        private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

        private const val USER_AGENT_MOBILE_ANDROID =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val MAX_INLINE_VISIT_CONTENT_CHARS = 12_000
        private const val MAX_INLINE_VISIT_CONTENT_PREVIEW_CHARS = 8_000

        // Cache to store visit results
        private val visitCache = ConcurrentHashMap<String, VisitWebResultData>()

        fun getCachedVisitResult(visitKey: String): VisitWebResultData? {
            if (visitKey.isBlank()) return null
            return visitCache[visitKey]
        }
    }

    // 创建OkHttpClient实例，配置超时
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

    // 存储WebView引用，用于在不同方法间共享
    private var webViewReference: WebView? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var overlayLifecycleOwner: ServiceLifecycleOwner? = null

    private var indicatorComposeView: ComposeView? = null
    private var indicatorLayoutParams: WindowManager.LayoutParams? = null

    private val isExpandedState = mutableStateOf(false)
    private val isExpandedByUserState = mutableStateOf(false)

    override fun invoke(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value
        val visitKey = tool.parameters.find { it.name == "visit_key" }?.value
        val linkNumberStr = tool.parameters.find { it.name == "link_number" }?.value
        val includeImageLinksParam = tool.parameters.find { it.name == "include_image_links" }?.value
        val headersParam = tool.parameters.find { it.name == "headers" }?.value
        val userAgentParam = tool.parameters.find { it.name == "user_agent" }?.value
        val userAgentPresetParam = tool.parameters.find { it.name == "user_agent_preset" }?.value

        val targetUrl = when {
            !visitKey.isNullOrBlank() && !linkNumberStr.isNullOrBlank() -> {
                val linkNumber = linkNumberStr.toIntOrNull()
                if (linkNumber == null) {
                    return ToolResult(tool.name, false, StringResultData(""), "Invalid link number.")
                }

                val cachedVisit = visitCache[visitKey]
                if (cachedVisit == null) {
                    return ToolResult(tool.name, false, StringResultData(""), "Invalid visit key.")
                }

                val link = cachedVisit.links.getOrNull(linkNumber - 1)
                if (link == null) {
                    return ToolResult(tool.name, false, StringResultData(""), "Link number out of bounds.")
                }
                link.url
            }
            !url.isNullOrBlank() -> url
            else -> {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Either 'url' or both 'visit_key' and 'link_number' must be provided."
                )
            }
        }

        fun parseHeaders(headersJson: String?): Map<String, String> {
            if (headersJson.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(headersJson)
                val keys = obj.keys()
                val result = mutableMapOf<String, String>()
                while (keys.hasNext()) {
                    val rawName = keys.next()
                    val rawValue = obj.optString(rawName, "")
                    val name = sanitizeHeaderName(rawName) ?: continue
                    val value = sanitizeHeaderValue(rawValue)
                    result[name] = value
                }
                result
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun resolveUserAgent(preset: String?, override: String?): String {
            if (!override.isNullOrBlank()) return override
            return when (preset?.trim()?.lowercase()) {
                "desktop", "pc", "default", "windows" -> USER_AGENT
                "android", "mobile_android", "mobile" -> USER_AGENT_MOBILE_ANDROID
                else -> USER_AGENT
            }
        }

        fun parseBoolean(raw: String?): Boolean {
            return when (raw?.trim()?.lowercase()) {
                "true", "1", "yes", "y", "on" -> true
                else -> false
            }
        }

        val includeImageLinks = parseBoolean(includeImageLinksParam)

        val targetUri = runCatching { android.net.Uri.parse(targetUrl) }.getOrNull()
        val targetScheme = targetUri?.scheme?.lowercase()
        if (targetScheme != "http" && targetScheme != "https") {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Unsupported URL scheme: ${targetScheme ?: "unknown"}"
            )
        }

        val headers = parseHeaders(headersParam)
        val resolvedUserAgent = resolveUserAgent(userAgentPresetParam, userAgentParam)

        return try {
            val pageContent = visitWebPage(targetUrl, headers, resolvedUserAgent, includeImageLinks)
            ToolResult(toolName = tool.name, success = true, result = pageContent, error = null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error visiting web page", e)

            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error visiting web page: ${e.message}"
            )
        }
    }

    @Composable
    private fun SearchingIndicator(
            onToggleFullscreen: () -> Unit,
            onDragBy: (dx: Int, dy: Int) -> Unit
    ) {
        val transition = rememberInfiniteTransition(label = "indicator")
        val primaryColor = MaterialTheme.colorScheme.primary

        val bobbingDp =
                transition.animateFloat(
                        initialValue = -2f,
                        targetValue = 2f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 900, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "bobbing"
                )

        val wiggleDeg =
                transition.animateFloat(
                        initialValue = -8f,
                        targetValue = 8f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 1200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "wiggle"
                )

        val pulse =
                transition.animateFloat(
                        initialValue = 0.96f,
                        targetValue = 1.04f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 1100, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "pulse"
                )

        Surface(
                shape = CircleShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier =
                        Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        onDragBy(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                                    }
                                }
                                // The indicator window is removed immediately after expanding.
                                // Avoid starting a ripple animation on a view that is about to detach.
                                .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                ) {
                                    onToggleFullscreen()
                                }
        ) {
            Box(
                    modifier =
                            Modifier
                                    .fillMaxSize()
                                    .scale(pulse.value)
                                    .drawBehind {
                                        val radius = size.minDimension / 2f
                                        drawCircle(
                                                brush =
                                                        Brush.radialGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.White.copy(alpha = 0.40f),
                                                                                primaryColor.copy(alpha = 0.16f),
                                                                                Color.Transparent
                                                                        ),
                                                                center = Offset(size.width * 0.30f, size.height * 0.28f),
                                                                radius = radius * 1.15f
                                                        ),
                                                radius = radius
                                        )

                                        drawCircle(
                                                color = Color.White.copy(alpha = 0.20f),
                                                radius = radius * 0.22f,
                                                center = Offset(size.width * 0.28f, size.height * 0.28f)
                                        )

                                        drawCircle(
                                                color = Color.White.copy(alpha = 0.08f),
                                                radius = radius * 0.90f,
                                                center = Offset(size.width * 0.55f, size.height * 0.62f)
                                        )
                                    }
                                    .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier =
                                Modifier
                                        .size(22.dp)
                                        .offset(y = bobbingDp.value.dp)
                                        .rotate(wiggleDeg.value)
                )
            }
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val url = tool.parameters.find { it.name == "url" }?.value
        val visitKey = tool.parameters.find { it.name == "visit_key" }?.value
        val linkNumber = tool.parameters.find { it.name == "link_number" }?.value

        val isUrlVisit = !url.isNullOrBlank()
        val isKeyVisit = !visitKey.isNullOrBlank() && !linkNumber.isNullOrBlank()

        return if (isUrlVisit || isKeyVisit) {
            ToolValidationResult(valid = true)
        } else {
            ToolValidationResult(valid = false, errorMessage = "Either 'url' or both 'visit_key' and 'link_number' must be provided.")
        }
    }

    /** Visit web page and extract content */
    private fun visitWebPage(url: String, headers: Map<String, String>, userAgent: String, includeImageLinks: Boolean): VisitWebResultData {
        // Use WebView to visit the page and extract content
        val extractedJson = runBlocking { loadWebPageAndExtractContent(url, headers, userAgent, includeImageLinks) }

        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val result = json.decodeFromString<ExtractedWebData>(extractedJson)

            // 将内容解析为结构化数据
            val lines = result.content.split("\n")
            var title = result.title
            val metadata = mutableMapOf<String, String>()
            var contentStartIndex = 0

            // 寻找标题，假设格式是"# 标题"
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("# ")) {
                    title = line.substring(2).trim()
                    contentStartIndex = i + 1
                    break
                }
            }

            // 寻找元数据部分
            var inMetadata = false
            var metadataEndIndex = contentStartIndex

            for (i in contentStartIndex until lines.size) {
                val line = lines[i].trim()

                if (line == "---METADATA---") {
                    inMetadata = true
                    metadataEndIndex = i + 1
                    continue
                }

                if (inMetadata) {
                    if (line == "---CONTENT---") {
                        metadataEndIndex = i + 1
                        break
                    }

                    // 解析元数据，格式为"key: value"
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            metadata[key] = value
                        }
                    }
                }
            }

            // 提取实际内容
            val content =
                    if (metadataEndIndex < lines.size) {
                        lines.subList(metadataEndIndex, lines.size).joinToString("\n")
                    } else {
                        // 如果没有找到元数据/内容分隔符，使用整个内容
                        result.content
                    }

            val visitKey = UUID.randomUUID().toString()
            val resultData = VisitWebResultData(
                    url = url,
                    title = title,
                    content = content,
                    metadata = metadata,
                    links = result.links.map { VisitWebResultData.LinkData(it.url, it.text) },
                    imageLinks = if (includeImageLinks) result.imageLinks else emptyList(),
                    visitKey = visitKey
            )
            val compactResultData = persistVisitContentIfNeeded(resultData)
            visitCache[visitKey] = compactResultData
            compactResultData
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing extracted web content", e)
            // Fallback for old format or error
            persistVisitContentIfNeeded(
                    VisitWebResultData(url = url, title = "Error", content = extractedJson)
            )
        }
    }

    private fun persistVisitContentIfNeeded(resultData: VisitWebResultData): VisitWebResultData {
        val fullContent = resultData.content
        if (fullContent.length <= MAX_INLINE_VISIT_CONTENT_CHARS) {
            return resultData
        }

        val outputFile = writeVisitResultToFile(resultData)
        val preview = buildInlineContentPreview(fullContent, outputFile.absolutePath)

        return resultData.copy(
                content = preview,
                contentSavedTo = outputFile.absolutePath,
                contentTruncated = true,
                originalContentLength = fullContent.length
        )
    }

    private fun writeVisitResultToFile(resultData: VisitWebResultData): File {
        val outputDir = OperitPaths.cleanOnExitInternalDir(context)
        val host =
                runCatching { android.net.Uri.parse(resultData.url).host.orEmpty() }
                        .getOrDefault("")
                        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                        .trim('_')
                        .ifBlank { "page" }
        val file =
                File(
                        outputDir,
                        "visit_web_${host}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.txt"
                )
        file.writeText(buildFullVisitResultText(resultData), Charsets.UTF_8)
        return file
    }

    private fun buildInlineContentPreview(fullContent: String, savedPath: String): String {
        val preview = fullContent.take(MAX_INLINE_VISIT_CONTENT_PREVIEW_CHARS).trimEnd()
        val note = "\n\n[Content truncated. Full content saved to file: $savedPath]"
        return preview + note
    }

    private fun buildFullVisitResultText(resultData: VisitWebResultData): String {
        val sb = StringBuilder()

        resultData.visitKey?.let { sb.appendLine("Visit key: $it") }
        if (resultData.title.isNotBlank()) {
            sb.appendLine("Title: ${resultData.title}")
        }
        sb.appendLine("URL: ${resultData.url}")

        if (resultData.metadata.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Metadata:")
            resultData.metadata.forEach { (key, value) ->
                sb.appendLine("$key: $value")
            }
        }

        if (resultData.links.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Results:")
            resultData.links.forEachIndexed { index, link ->
                sb.appendLine("[${index + 1}] ${link.text}")
                sb.appendLine("    URL: ${link.url}")
            }
        }

        if (resultData.imageLinks.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Images:")
            resultData.imageLinks.forEachIndexed { index, link ->
                val name = link.substringAfterLast('/').substringBefore('?').ifBlank { "image" }
                sb.appendLine("[${index + 1}] $name")
                sb.appendLine("    URL: $link")
            }
        }

        sb.appendLine()
        sb.appendLine("Content:")
        sb.append(resultData.content)

        return sb.toString()
    }

    /** 使用WebView加载页面并提取内容 */
    private suspend fun loadWebPageAndExtractContent(url: String, headers: Map<String, String>, userAgent: String, includeImageLinks: Boolean): String {
        return suspendCancellableCoroutine { continuation ->
            AppLogger.d(TAG, "Starting to load web page: $url")

            // 确保UI操作在主线程上执行
            Handler(Looper.getMainLooper()).post {
                try {
                    val windowManager =
                            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    overlayWindowManager = windowManager

                    // 创建生命周期管理器
                    val lifecycleOwner =
                            ServiceLifecycleOwner().apply {
                                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                                handleLifecycleEvent(Lifecycle.Event.ON_START)
                                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                            }

                    overlayLifecycleOwner = lifecycleOwner
                    isExpandedState.value = false
                    isExpandedByUserState.value = false

                    if (indicatorLayoutParams == null) {
                        indicatorLayoutParams = createIndicatorLayoutParams()
                    }

                    val params = setupWindowParams()
                    overlayLayoutParams = params

                    val composeView =
                            ComposeView(context).apply {
                                // 设置视图组合策略
                                setViewCompositionStrategy(
                                        ViewCompositionStrategy.DisposeOnDetachedFromWindow
                                )

                                // 使用自定义的生命周期所有者
                                setViewTreeLifecycleOwner(lifecycleOwner)
                                setViewTreeViewModelStoreOwner(lifecycleOwner)
                                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                            }
                    overlayComposeView = composeView

                    composeView.setContent {
                        WebVisitUI(
                                url = url,
                                headers = headers,
                                userAgent = userAgent,
                                includeImageLinks = includeImageLinks,
                                onWebViewCreated = { webView ->
                                    // 存储WebView引用以便清理
                                    webViewReference = webView
                                },
                                onContentExtracted = { content ->
                                    AppLogger.d(TAG, "Content extracted, length: ${content.length}")

                                    // 在完成内容提取后进行资源清理
                                    try {
                                        // 清理生命周期资源
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_PAUSE
                                        )
                                        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_DESTROY
                                        )

                                        // 清理WebView
                                        cleanupWebView(webViewReference)

                                        removeIndicatorWindow()

                                        // 清理Compose View并移除窗口
                                        CoroutineScope(Dispatchers.Main).launch {
                                            // 首先设置内容为空，释放组合资源
                                            composeView.setContent {}

                                            // 使用try-catch以确保即使移除窗口失败也能继续
                                            try {
                                                windowManager.removeView(composeView)
                                                AppLogger.d(TAG, "ComposeView removed from window")
                                            } catch (e: Exception) {
                                                AppLogger.e(
                                                        TAG,
                                                        "Error removing ComposeView: ${e.message}"
                                                )
                                            }

                                            if (overlayComposeView === composeView) {
                                                overlayComposeView = null
                                                overlayWindowManager = null
                                                overlayLayoutParams = null
                                                overlayLifecycleOwner = null
                                            }

                                            // 恢复协程
                                            if (continuation.isActive) {
                                                continuation.resume(content)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.e(TAG, "Error cleaning up resources: ${e.message}")
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                    "Error extracting content: ${e.message}"
                                            )
                                        }
                                    }
                                },
                                isExpanded = isExpandedState,
                                onMinimizeRequested = {
                                    setExpanded(false)
                                }
                        )
                    }

                    try {
                        windowManager.addView(composeView, params)
                        AppLogger.d(TAG, "Web browser window added")

                        showIndicatorWindow()

                        // 添加取消回调以处理协程取消
                        continuation.invokeOnCancellation {
                            // 确保在主线程上执行UI操作
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    // 清理生命周期资源
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

                                    // 清理WebView资源
                                    cleanupWebView(webViewReference)

                                    removeIndicatorWindow()

                                    // 移除视图
                                    composeView.setContent {}
                                    windowManager.removeView(composeView)
                                    AppLogger.d(TAG, "Web browser window removed on cancellation")
                                    if (overlayComposeView === composeView) {
                                        overlayComposeView = null
                                        overlayWindowManager = null
                                        overlayLayoutParams = null
                                        overlayLifecycleOwner = null
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e(TAG, "Error removing view on cancellation: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error showing web browser: ${e.message}")
                        continuation.resume("Error displaying web browser: ${e.message}")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error in loadWebPageAndExtractContent: ${e.message}")
                    continuation.resume("General error: ${e.message}")
                }
            }
        }
    }

    /** Setup window parameters for the WebView dialog */
    private fun setupWindowParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics

        return WindowManager.LayoutParams().apply {
            // Set window type
            type =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }

            // Important: Do not set FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE
            // as they would prevent interaction with the WebView
            flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            // Make window transparent to properly display the Compose UI
            format = PixelFormat.TRANSLUCENT

            val compactSizePx = (displayMetrics.density * 1f).toInt().coerceAtLeast(1)
            width = compactSizePx
            height = compactSizePx

            gravity = Gravity.TOP or Gravity.START

            val indicatorParams = indicatorLayoutParams
            if (indicatorParams != null) {
                x = indicatorParams.x
                y = indicatorParams.y
            }
        }
    }

    private fun createIndicatorLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val sizePx = (density * 40f).roundToInt().coerceAtLeast(1)

        return WindowManager.LayoutParams(
                sizePx,
                sizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val safeMargin = (density * 16f).roundToInt()
            x = safeMargin
            y = safeMargin
        }
    }

    private fun showIndicatorWindow() {
        val windowManager = overlayWindowManager ?: return
        val lifecycleOwner = overlayLifecycleOwner ?: return
        if (indicatorComposeView != null) return

        val params = indicatorLayoutParams ?: createIndicatorLayoutParams().also { indicatorLayoutParams = it }

        val view =
                ComposeView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setViewCompositionStrategy(
                            ViewCompositionStrategy.DisposeOnDetachedFromWindow
                    )

                    // 使用自定义的生命周期所有者
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setContent {
                        MaterialTheme {
                            SearchingIndicator(
                                    onToggleFullscreen = { setExpanded(true) },
                                    onDragBy = { dx, dy -> moveIndicatorBy(dx, dy) }
                            )
                        }
                    }
                }

        indicatorComposeView = view
        windowManager.addView(view, params)
    }

    private fun hideIndicatorWindow() {
        val windowManager = overlayWindowManager
        val view = indicatorComposeView
        if (windowManager != null && view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing indicator view: ${e.message}")
            }
        }
        indicatorComposeView = null
    }

    private fun removeIndicatorWindow() {
        val windowManager = overlayWindowManager
        val view = indicatorComposeView
        if (windowManager != null && view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing indicator view: ${e.message}")
            }
        }
        indicatorComposeView = null
        indicatorLayoutParams = null
    }

    private fun moveIndicatorBy(dx: Int, dy: Int) {
        val windowManager = overlayWindowManager ?: return
        val view = indicatorComposeView ?: return
        val params = indicatorLayoutParams ?: return

        val displayMetrics = context.resources.displayMetrics
        val maxX = (displayMetrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - params.height).coerceAtLeast(0)

        params.x = (params.x + dx).coerceIn(0, maxX)
        params.y = (params.y + dy).coerceIn(0, maxY)

        indicatorLayoutParams = params
        windowManager.updateViewLayout(view, params)
        syncWebOverlayToIndicator()
    }

    private fun syncWebOverlayToIndicator() {
        if (isExpandedState.value) return
        val windowManager = overlayWindowManager ?: return
        val view = overlayComposeView ?: return
        val overlayParams = overlayLayoutParams ?: return
        val indicatorParams = indicatorLayoutParams ?: return

        overlayParams.x = indicatorParams.x
        overlayParams.y = indicatorParams.y
        overlayLayoutParams = overlayParams
        if (view.windowToken != null) {
            windowManager.updateViewLayout(view, overlayParams)
        }
    }

    private fun setExpanded(expanded: Boolean) {
        isExpandedState.value = expanded
        isExpandedByUserState.value = expanded
        updateOverlayWindowLayout()
    }

    private fun updateOverlayWindowLayout() {
        val windowManager = overlayWindowManager ?: return
        val view = overlayComposeView ?: return
        val params = (overlayLayoutParams ?: (view.layoutParams as? WindowManager.LayoutParams)) ?: return

        val displayMetrics = context.resources.displayMetrics
        val expanded = isExpandedState.value

        if (expanded) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.CENTER
            params.x = 0
            params.y = 0
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            hideIndicatorWindow()
        } else {
            val compactSizePx = (displayMetrics.density * 1f).toInt().coerceAtLeast(1)
            params.width = compactSizePx
            params.height = compactSizePx
            params.gravity = Gravity.TOP or Gravity.START
            params.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            if (indicatorComposeView == null) {
                showIndicatorWindow()
            }

            val indicatorParams = indicatorLayoutParams
            if (indicatorParams != null) {
                params.x = indicatorParams.x
                params.y = indicatorParams.y
            }
        }

        overlayLayoutParams = params
        if (view.windowToken != null) {
            windowManager.updateViewLayout(view, params)
        }
    }

    /** 清理WebView资源的辅助方法 */
    private fun cleanupWebView(webView: WebView?) {
        webView?.apply {
            // 先停止加载
            stopLoading()

            // 清理WebView状态
            clearHistory()
            clearCache(true)
            clearFormData()
            clearSslPreferences()

            // 清理JavaScript状态
            evaluateJavascript("javascript:void(0);", null)

            // 加载空白页以释放资源
            loadUrl("about:blank")

            // 暂停WebView
            onPause()

            // 移除回调和消息
            handler?.removeCallbacksAndMessages(null)

            // 销毁WebView
            destroy()
        }

        // 请求GC回收资源
        System.gc()
    }

    /** Compose UI for the web browser */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WebVisitUI(
            url: String,
            headers: Map<String, String>,
            userAgent: String,
            includeImageLinks: Boolean,
            onWebViewCreated: (WebView) -> Unit,
            onContentExtracted: (String) -> Unit,
            isExpanded: State<Boolean>,
            onMinimizeRequested: () -> Unit
    ) {
        // 页面状态
        val isLoading = remember { mutableStateOf(true) } // 页面是否正在加载
        val pageLoaded = remember { mutableStateOf(false) } // 页面是否已加载完成
        val currentUrl = remember { mutableStateOf(url) } // 当前URL
        val pageTitle = remember { mutableStateOf("") } // 页面标题
        val hasSslError = remember { mutableStateOf(false) }

        // 内容状态
        val pageContent = remember { mutableStateOf("") } // 提取的页面内容
        val hasExtractedContent = remember { mutableStateOf(false) } // 是否已提取内容

        val extractionRequested = remember { mutableStateOf(false) }
        val loadToken = remember { mutableStateOf(0) }
        val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
        val visitWebWaitSeconds by displayPreferencesManager.visitWebWaitSeconds.collectAsState(initial = 0)

        // 自动模式状态
        val autoModeEnabled = remember { mutableStateOf(true) } // 是否启用自动模式
        val autoCountdownActive = remember { mutableStateOf(false) } // 倒计时是否激活
        val autoCountdownSeconds = remember { mutableStateOf(0) } // 倒计时秒数

        LaunchedEffect(autoCountdownActive.value) {
            if (autoCountdownActive.value) {
                autoCountdownSeconds.value = visitWebWaitSeconds

                for (i in visitWebWaitSeconds downTo 1) {
                    autoCountdownSeconds.value = i
                    delay(1000)

                    if (!autoCountdownActive.value) {
                        break
                    }
                }

                if (autoCountdownActive.value) {
                    autoCountdownActive.value = false
                    if (pageContent.value.isNotEmpty()) {
                        onContentExtracted(pageContent.value)
                    }
                }
            }
        }

        LaunchedEffect(loadToken.value) {
            if (loadToken.value <= 0) return@LaunchedEffect
            delay(10_000)
            if (!pageLoaded.value &&
                            !hasExtractedContent.value &&
                            !extractionRequested.value
            ) {
                AppLogger.w(TAG, "Page load timeout (10s): ${currentUrl.value}")
                onContentExtracted(context.getString(R.string.webvisit_load_timeout))
            }
        }

        MaterialTheme {
            Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
            ) {
                Card(
                        modifier =
                                Modifier.fillMaxWidth(0.99f).fillMaxHeight(0.97f),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val statusText =
                                when {
                                    !pageLoaded.value -> context.getString(R.string.webvisit_status_loading_page)
                                    isLoading.value -> context.getString(R.string.webvisit_status_waiting_page)
                                    autoCountdownActive.value -> context.getString(R.string.webvisit_status_extracting, autoCountdownSeconds.value)
                                    hasExtractedContent.value -> context.getString(R.string.webvisit_status_extracted_waiting)
                                    else -> context.getString(R.string.webvisit_status_waiting_extraction)
                                }

                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = context.getString(R.string.webvisit_title),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                            )

                            Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                        text = statusText,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // URL info
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (hasSslError.value) {
                                        Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color(0xFFFFE6CC)
                                        ) {
                                            Text(
                                                    text = stringResource(R.string.web_ssl_error_badge),
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF7A3E00),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Text(
                                            text = currentUrl.value,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                if (pageTitle.value.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                            text = pageTitle.value,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // WebView
                        AndroidView(
                                factory = { context ->
                                    createOptimizedWebView(context, url, userAgent, headers) { webView ->
                                        // Register created WebView instance for later cleanup
                                        onWebViewCreated(webView)

                                        // Configure WebViewClient
                                        webView.webViewClient =
                                                object : WebViewClient() {
                                                    // 追踪上一个URL，用于检测重定向
                                                    private var lastLoadedUrl: String = ""

                                                    override fun onPageStarted(
                                                            view: WebView,
                                                            startedUrl: String,
                                                            favicon: android.graphics.Bitmap?
                                                    ) {
                                                        super.onPageStarted(view, startedUrl, favicon)
                                                        currentUrl.value = startedUrl
                                                        hasSslError.value = false
                                                        isLoading.value = true
                                                        pageLoaded.value = false
                                                        loadToken.value = loadToken.value + 1
                                                    }

                                                    override fun onPageFinished(
                                                            view: WebView,
                                                            loadedUrl: String
                                                    ) {
                                                        super.onPageFinished(view, loadedUrl)
                                                        AppLogger.d(TAG, "Loaded URL: $loadedUrl")

                                                        // 检查是否是重定向或新页面
                                                        val isNewPage = lastLoadedUrl != loadedUrl
                                                        lastLoadedUrl = loadedUrl

                                                        currentUrl.value = loadedUrl
                                                        pageTitle.value = view.title ?: ""
                                                        pageLoaded.value = true

                                                        // 如果是重定向或新页面，重置提取状态
                                                        if (isNewPage) {
                                                            AppLogger.d(TAG, "页面发生重定向或加载了新页面，重置提取状态")
                                                            isLoading.value = true
                                                            hasExtractedContent.value = false
                                                            extractionRequested.value = false
                                                            pageContent.value = ""
                                                            autoCountdownActive.value = false
                                                            autoModeEnabled.value = true
                                                        }

                                                        // 页面无论显示什么内容都按普通网页处理，不把站点页面转换为交互式验证流程。
                                                        triggerContentExtraction(view)
                                                    }

                                                    private fun triggerContentExtraction(view: WebView) {
                                                        Handler(Looper.getMainLooper()).postDelayed({
                                                            if (!view.isAttachedToWindow) {
                                                                AppLogger.d(TAG, "WebView不再附加到窗口，跳过提取")
                                                                return@postDelayed
                                                            }

                                                            if (!hasExtractedContent.value && !extractionRequested.value && autoModeEnabled.value) {
                                                                extractionRequested.value = true
                                                                isLoading.value = true
                                                                extractPageContent(view, includeImageLinks) { content ->
                                                                    isLoading.value = false
                                                                    hasExtractedContent.value = true
                                                                    pageContent.value = content

                                                                    if (autoModeEnabled.value) {
                                                                        autoCountdownActive.value = true
                                                                        autoScrollToBottom(view, visitWebWaitSeconds) {
                                                                            AppLogger.d(TAG, "页面滚动完成")
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }, 800)
                                                    }

                                                    override fun shouldOverrideUrlLoading(
                                                            view: WebView,
                                                            request: WebResourceRequest
                                                    ): Boolean {
                                                        val uri = request.url
                                                        val newUrl = uri.toString()
                                                        AppLogger.d(TAG, "URL变化: $newUrl")

                                                        val scheme = uri.scheme?.lowercase()
                                                        if (scheme != "http" && scheme != "https") {
                                                            AppLogger.w(TAG, "Blocked navigation to unsupported URL scheme: $newUrl")
                                                            return true
                                                        }

                                                        currentUrl.value = newUrl

                                                        isLoading.value = true
                                                        pageLoaded.value = false

                                                        if (headers.isNotEmpty() && request.isForMainFrame) {
                                                            view.loadUrl(newUrl, headers)
                                                            return true
                                                        }

                                                        return false
                                                    }

                                                    override fun onReceivedError(
                                                            view: WebView,
                                                            errorCode: Int,
                                                            description: String,
                                                            failingUrl: String
                                                    ) {
                                                        AppLogger.e(
                                                                TAG,
                                                                "WebView error: $errorCode - $description"
                                                        )
                                                        super.onReceivedError(
                                                                view,
                                                                errorCode,
                                                                description,
                                                                failingUrl
                                                        )
                                                    }

                                                    override fun onReceivedSslError(
                                                            view: WebView,
                                                            handler: SslErrorHandler,
                                                            error: android.net.http.SslError
                                                    ) {
                                                        AppLogger.w(
                                                                TAG,
                                                                "visit_web SSL error, proceeding anyway. " +
                                                                        "url=${error.url}, primaryError=${error.primaryError}"
                                                        )
                                                        hasSslError.value = true
                                                        handler.proceed()
                                                    }
                                                }

                                        return@createOptimizedWebView webView
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                update = { webView ->
                                    // 存储最新的WebView引用
                                    webViewReference = webView
                                    // 只更新重要属性以避免重新创建
                                    webView.requestFocus()
                                }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 按钮区域 - 根据当前状态显示不同按钮
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左侧按钮 - 根据状态变化
                            FilledTonalButton(
                                    onClick = {
                                        if (autoCountdownActive.value) {
                                            // 如果正在倒计时，则取消倒计时
                                            autoCountdownActive.value = false
                                        } else if (!hasExtractedContent.value &&
                                                        autoModeEnabled.value
                                        ) {
                                            // 如果尚未提取内容，且处于自动模式，则切换到手动模式
                                            autoModeEnabled.value = false
                                        } else {
                                            // 其他情况为取消操作
                                            onContentExtracted(context.getString(R.string.webvisit_cancelled))
                                        }
                                    }
                            ,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                            ) {
                                Text(
                                        when {
                                            autoCountdownActive.value -> context.getString(R.string.webvisit_button_cancel_countdown)
                                            !hasExtractedContent.value &&
                                                    autoModeEnabled.value -> context.getString(R.string.webvisit_button_cancel_auto)
                                            else -> context.getString(R.string.webvisit_button_cancel)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isExpanded.value) {
                                FilledTonalIconButton(
                                        onClick = onMinimizeRequested,
                                        modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = context.getString(R.string.webvisit_button_minimize)
                                    )
                                }
                            }

                            // 右侧按钮 - 根据状态变化
                            Button(
                                    onClick = {
                                        if (autoCountdownActive.value) {
                                            // 如果正在倒计时，立即继续
                                            autoCountdownActive.value = false
                                            if (pageContent.value.isNotEmpty()) {
                                                onContentExtracted(pageContent.value)
                                            }
                                        } else if (hasExtractedContent.value) {
                                            // 如果已提取内容，直接继续
                                            onContentExtracted(pageContent.value)
                                        } else {
                                            // 否则提取内容
                                            webViewReference?.let { webView ->
                                                // 显示正在提取提示
                                                isLoading.value = true

                                                AppLogger.d(TAG, "触发内容提取，当前页面: ${currentUrl.value}")

                                                extractionRequested.value = true
                                                extractPageContent(webView, includeImageLinks) { content ->
                                                    isLoading.value = false
                                                    hasExtractedContent.value = true
                                                    pageContent.value = content

                                                    // 如果是自动模式，开始倒计时
                                                    if (autoModeEnabled.value) {
                                                        autoCountdownActive.value = true
                                                    }
                                                }
                                            }
                                                    ?: onContentExtracted(context.getString(R.string.webvisit_webview_unavailable))
                                        }
                                    },
                                    enabled = pageLoaded.value,
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                            ) {
                                Text(
                                        when {
                                            autoCountdownActive.value ->
                                                    context.getString(R.string.webvisit_button_continue_now, autoCountdownSeconds.value)
                                            hasExtractedContent.value -> context.getString(R.string.webvisit_button_continue)
                                            !autoModeEnabled.value -> context.getString(R.string.webvisit_button_manual_extract)
                                            else -> context.getString(R.string.webvisit_button_extract)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 帮助文本
                        val helperText =
                                when {
                                    !pageLoaded.value -> stringResource(R.string.webvisit_help_loading_page)
                                    autoCountdownActive.value -> stringResource(R.string.webvisit_help_countdown)
                                    !hasExtractedContent.value && !autoModeEnabled.value ->
                                            stringResource(R.string.webvisit_help_manual_mode)
                                    else -> ""
                                }

                        if (helperText.isNotEmpty()) {
                            Text(
                                    text = helperText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    /** 创建优化的WebView实例，配置所有必要的设置 */
    private fun createOptimizedWebView(
            context: Context,
            url: String,
            userAgent: String,
            headers: Map<String, String>,
            configure: (WebView) -> WebView
    ): WebView {
        return WebView(context).apply {
            layoutParams =
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.0f
                    )

            // 配置WebView设置
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = userAgent

                // 启用App Cache
//                try {
//                    val enableMethod = this::class.java.getMethod("setAppCacheEnabled", Boolean::class.javaPrimitiveType)
//                    enableMethod.invoke(this, true)
//                } catch (_: Exception) {
//                    // AppCache API removed on newer Android versions
//                }
//                try {
//                    val pathMethod = this::class.java.getMethod("setAppCachePath", String::class.java)
//                    pathMethod.invoke(this, context.cacheDir.absolutePath)
//                } catch (_: Exception) {
//                    // AppCache API removed on newer Android versions
//                }
                allowFileAccess = true

                // 设置默认缓存模式，而不是完全禁用
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                // 减少自动媒体加载
                mediaPlaybackRequiresUserGesture = true

                // 在Android 5.0及以上版本，默认阻止混合内容，这里明确设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
            }

            // 启用交互
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            isLongClickable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isScreenReaderFocusable = true
            }
            requestFocus()

            // 配置Cookie策略，确保第三方Cookie也被接受
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(this, true)
            }

            // 改善内存管理
            setWillNotDraw(false)

            // 应用额外配置
            val configured = configure(this)

            if (headers.isNotEmpty()) {
                configured.loadUrl(url, headers)
            } else {
                configured.loadUrl(url)
            }
        }
    }

    /** 从加载的WebView提取页面内容 */
    private fun extractPageContent(webView: WebView, includeImageLinks: Boolean, callback: (String) -> Unit) {
        // 检查WebView是否有效
        if (!webView.isAttachedToWindow) {
            AppLogger.e(TAG, "WebView is not attached to window, cannot extract content")
            callback("Error: WebView is not attached to window")
            return
        }

        // 记录提取开始
        AppLogger.d(TAG, "Starting to extract content from web page")

        // 用于提取页面内容的JavaScript
        val extractionScript =
                """
            (function() {
                try {
                    console.log("Starting content extraction script");

                    // 提取链接
                    var links = [];
                    var linkNodes = document.querySelectorAll('a');
                    for (var i = 0; i < linkNodes.length; i++) {
                        var node = linkNodes[i];
                        var href = node.href;
                        var text = node.innerText.trim();
                        if (href && text) {
                            links.push({url: href, text: text});
                        }
                    }
                    
                    // 图片链接（可选）
                    var imageLinks = [];
                    if (${includeImageLinks.toString().lowercase()}) {
                        var normalizeImageUrl = function(u) {
                            if (!u) return null;
                            try {
                                u = String(u).trim();
                            } catch (e) {
                                return null;
                            }
                            if (!u) return null;
                            // download_file only supports http(s)
                            if (u.indexOf('http://') !== 0 && u.indexOf('https://') !== 0) return null;
                            // drop non-downloadable / noisy sources
                            if (u.indexOf('data:') === 0 || u.indexOf('blob:') === 0) return null;
                            var clean = u.split('#')[0];
                            var pathOnly = clean.split('?')[0].toLowerCase();
                            // exclude common icon/svg noise
                            if (pathOnly.endsWith('.svg')) return null;
                            return clean;
                        };
                        var imgNodes = document.querySelectorAll('img');
                        var imgSeen = {};
                        for (var j = 0; j < imgNodes.length; j++) {
                            var imgNode = imgNodes[j];
                            var imgUrlRaw = imgNode.currentSrc || imgNode.src || imgNode.getAttribute('data-src');
                            var imgUrl = normalizeImageUrl(imgUrlRaw);
                            if (imgUrl && !imgSeen[imgUrl]) {
                                imgSeen[imgUrl] = true;
                                imageLinks.push(imgUrl);
                            }
                        }
                    }

                    // 页面基本信息
                    var result = {
                        title: document.title || "No Title",
                        url: window.location.href,
                        content: "",
                        links: links,
                        imageLinks: imageLinks
                    };
                    
                    // 直接获取整个文档的HTML和文本内容
                    var fullHtml = document.documentElement.outerHTML;
                    var fullText = document.body.innerText;
                    
                    // 添加元数据
                    var metadata = {};
                    
                    // 提取所有meta标签的信息
                    var metaTags = document.querySelectorAll('meta');
                    for (var i = 0; i < metaTags.length; i++) {
                        var name = metaTags[i].getAttribute('name') || 
                                   metaTags[i].getAttribute('property') || 
                                   metaTags[i].getAttribute('itemprop');
                        var content = metaTags[i].getAttribute('content');
                        
                        if (name && content) {
                            metadata[name] = content;
                        }
                    }
                    
                    // 添加特别关注的元数据
                    var importantMetadata = ['description', 'keywords', 'author', 'og:title', 'og:description'];
                    var metaStr = "---METADATA---\n";
                    importantMetadata.forEach(function(key) {
                        if (metadata[key]) {
                            metaStr += key + ": " + metadata[key] + "\n";
                        }
                    });
                    
                    // 组合最终结果
                    result.content = "# " + result.title + "\n\n" +
                                      metaStr + "\n" +
                                      "---CONTENT---\n" +
                                      fullText;
                                      
                    console.log("Content extraction complete");
                    return JSON.stringify(result);
                } catch(e) {
                    return JSON.stringify({error: "Error extracting content: " + e.toString()});
                }
            })();
        """.trimIndent()

        try {
            webView.evaluateJavascript(extractionScript) { resultContent ->
                try {
                    AppLogger.d(
                            TAG,
                            "Content extraction result received. Length: ${resultContent.length}"
                    )

                    // 处理JavaScript结果，去掉引号包装
                    val processedContent =
                            if (resultContent.startsWith("\"") && resultContent.endsWith("\"")) {
                                // 解码JavaScript字符串到Kotlin字符串
                                resultContent
                                        .substring(1, resultContent.length - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                                        .replace("\\n", "\n")
                            } else {
                                resultContent
                            }

                    callback(processedContent)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error processing extracted content", e)
                    callback("Error processing content: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception during JavaScript evaluation", e)
            callback("Error evaluating JavaScript: ${e.message}")
        }
    }

    private fun sanitizeHeaderName(name: String?): String? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("\r") || trimmed.contains("\n")) return null
        if (trimmed.contains(":")) return null
        return trimmed
    }

    private fun sanitizeHeaderValue(value: String?): String {
        return value
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?: ""
    }

    /** 自动滚动页面到底部以触发可能的懒加载内容 */
    private fun autoScrollToBottom(webView: WebView, scrollWaitSeconds: Int, onScrollComplete: () -> Unit) {
        val scrollWaitMillis = scrollWaitSeconds.coerceAtLeast(0) * 1000L
        val scrollScript =
                """
            (function() {
                try {
                    // 初始位置
                    var initialHeight = document.body.scrollHeight;
                    var scrollAttempts = 0;
                    var maxScrollAttempts = 3;
                    var scrollInterval;
                    var lastScrollHeight = 0;
                    
                    function smoothScroll() {
                        // 检查是否到达尝试次数上限
                        if (scrollAttempts >= maxScrollAttempts) {
                            clearInterval(scrollInterval);
                            console.log('自动滚动完成 - 达到最大尝试次数');
                            return true; // 滚动完成
                        }
                        
                        // 获取当前文档高度
                        var currentHeight = document.body.scrollHeight;
                        
                        // 如果两次滚动后高度没有变化，认为已经滚动到底部
                        if (currentHeight === lastScrollHeight && scrollAttempts > 0) {
                            scrollAttempts++;
                            console.log('内容高度未变化，尝试次数: ' + scrollAttempts);
                        } else {
                            // 更新上次高度
                            lastScrollHeight = currentHeight;
                        }
                        
                        // 执行滚动
                        var currentPosition = window.pageYOffset || document.documentElement.scrollTop;
                        var targetPosition = currentHeight - window.innerHeight;
                        var distance = targetPosition - currentPosition;
                        
                        if (Math.abs(distance) < 10) {
                            // 已接近底部，增加尝试次数
                            scrollAttempts++;
                            console.log('已接近底部，尝试次数: ' + scrollAttempts);
                            
                            // 额外触发一次滚动确保触发所有加载
                            window.scrollTo(0, targetPosition + 1);
                            
                            // 短暂等待后滚回正常位置
                            setTimeout(function() {
                                window.scrollTo(0, targetPosition);
                            }, 100);
                        } else {
                            // 平滑滚动到目标位置
                            window.scrollTo({
                                top: targetPosition,
                                behavior: 'smooth'
                            });
                            console.log('滚动到位置: ' + targetPosition + '，总高度: ' + currentHeight);
                        }
                        
                        return scrollAttempts >= maxScrollAttempts;
                    }
                    
                    // 立即执行一次初始滚动
                    var isComplete = smoothScroll();
                    if (isComplete) {
                        return 'scroll-complete';
                    }
                    
                    // 设置定时滚动
                    scrollInterval = setInterval(function() {
                        var isComplete = smoothScroll();
                        if (isComplete) {
                            clearInterval(scrollInterval);
                            console.log('自动滚动完成');
                            return 'scroll-complete';
                        }
                    }, 1000); // 每秒滚动一次
                    
                    // 无论如何，最多滚动8秒
                    setTimeout(function() {
                        clearInterval(scrollInterval);
                        console.log('自动滚动超时完成');
                    }, 8000);
                    
                    return 'scrolling-started';
                } catch(e) {
                    console.error('滚动过程出错: ' + e);
                    return 'scroll-error: ' + e;
                }
            })();
        """.trimIndent()

        try {
            AppLogger.d(TAG, "开始执行自动滚动脚本")
            webView.evaluateJavascript(scrollScript) { result -> AppLogger.d(TAG, "滚动脚本初始执行结果: $result") }

            // 等待滚动完成后执行回调
            Handler(Looper.getMainLooper())
                    .postDelayed(
                            {
                                AppLogger.d(TAG, "自动滚动等待完成，继续处理")
                                onScrollComplete()
                            },
                            scrollWaitMillis
                    )
        } catch (e: Exception) {
            AppLogger.e(TAG, "执行滚动脚本时出错", e)
            // 出错时也要继续后续流程
            onScrollComplete()
        }
    }
}

@kotlinx.serialization.Serializable
private data class ExtractedWebData(
    val title: String,
    val url: String,
    val content: String,
    val links: List<LinkInfo> = emptyList(),
    val imageLinks: List<String> = emptyList(),
    val error: String? = null
) {
    @kotlinx.serialization.Serializable
    data class LinkInfo(val url: String, val text: String)
}

/** 服务生命周期所有者 - 为Compose UI提供生命周期支持 必须在主线程上初始化 */
private class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreField = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        // 确保在主线程上初始化
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 在主线程上，直接初始化
            savedStateRegistryController.performRestore(null)
        } else {
            // 如果不在主线程上，则记录警告（我们应该确保在主线程上创建实例）
            AppLogger.w(
                    "ServiceLifecycleOwner",
                    "Initializing not on main thread. This may cause issues."
            )
            // 在实际使用时应该避免这种情况，代码已经通过 Handler(Looper.getMainLooper()).post 确保在主线程上创建
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreField

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        // 确保生命周期事件在主线程上处理
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.handleLifecycleEvent(event)
        } else {
            // 如果不在主线程上，使用Handler将调用转到主线程
            Handler(Looper.getMainLooper()).post { lifecycleRegistry.handleLifecycleEvent(event) }
        }
    }
}
