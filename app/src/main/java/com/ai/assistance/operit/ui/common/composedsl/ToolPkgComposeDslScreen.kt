package com.ai.assistance.operit.ui.common.composedsl

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.javascript.JsJavaBridgeDelegates
import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionErrorMessage
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslRenderResult
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.common.markdown.DefaultXmlRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.main.LocalTopBarTitleContent
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.components.LocalSetScreenSoftInputMode
import com.ai.assistance.operit.ui.main.components.LocalSetUseScreenImePadding
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig
import com.ai.assistance.operit.ui.theme.getSystemFontFamily
import com.ai.assistance.operit.ui.theme.loadCustomFontFamily
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ToolPkgComposeDslScreen"
private val composeDslFilePickerMainHandler by lazy { Handler(Looper.getMainLooper()) }

internal data class ComposeDslFilePickerRequest(
    val routeInstanceId: String,
    val executionContextKey: String,
    val mimeTypes: List<String>,
    val allowMultiple: Boolean,
    val persistPermission: Boolean
)

private data class ComposeDslPendingFilePickerLaunch(
    val request: ComposeDslFilePickerRequest,
    val onComplete: (Result<String>) -> Unit
)

internal object ComposeDslFilePickerHostRegistry {
    private val launchers =
        ConcurrentHashMap<String, (ComposeDslFilePickerRequest, (Result<String>) -> Unit) -> Unit>()

    fun bind(
        executionContextKey: String,
        launcher: (ComposeDslFilePickerRequest, (Result<String>) -> Unit) -> Unit
    ) {
        if (executionContextKey.isBlank()) {
            return
        }
        launchers[executionContextKey] = launcher
    }

    fun unbind(executionContextKey: String) {
        if (executionContextKey.isBlank()) {
            return
        }
        launchers.remove(executionContextKey)
    }

    fun openPicker(
        payloadJson: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val payload =
            runCatching { JSONObject(payloadJson) }
                .getOrElse { error ->
                    onError(error.message?.trim().orEmpty().ifBlank { "Invalid file picker payload" })
                    return
                }
        val executionContextKey = payload.optString("executionContextKey").trim()
        if (executionContextKey.isBlank()) {
            onError("compose file picker executionContextKey is required")
            return
        }
        val launcher = launchers[executionContextKey]
        if (launcher == null) {
            onError("compose file picker host is unavailable")
            return
        }
        val options = payload.optJSONObject("options")
        val mimeTypesJson = options?.optJSONArray("mimeTypes")
        val mimeTypes =
            buildList {
                if (mimeTypesJson != null) {
                    for (index in 0 until mimeTypesJson.length()) {
                        val value = mimeTypesJson.optString(index).trim()
                        if (value.isNotEmpty()) {
                            add(value)
                        }
                    }
                }
            }.ifEmpty { listOf("*/*") }
        val request =
            ComposeDslFilePickerRequest(
                routeInstanceId = payload.optString("routeInstanceId").trim(),
                executionContextKey = executionContextKey,
                mimeTypes = mimeTypes,
                allowMultiple = options?.optBoolean("allowMultiple", false) == true,
                persistPermission = options?.optBoolean("persistPermission", true) != false
            )
        composeDslFilePickerMainHandler.post {
            launcher(request) { result ->
                result.fold(
                    onSuccess = onSuccess,
                    onFailure = { error ->
                        onError(error.message?.trim().orEmpty().ifBlank { "compose file picker failed" })
                    }
                )
            }
        }
    }
}

private fun Cursor.getColumnIndexOrNull(name: String): Int? {
    val index = getColumnIndex(name)
    return if (index >= 0) index else null
}

private fun queryComposeDslPickedFileMeta(
    context: android.content.Context,
    uri: Uri,
    copiedFile: File
): JSONObject {
    val resolver = context.contentResolver
    var name: String? = null
    var size: Long? = null
    runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndexOrNull(OpenableColumns.DISPLAY_NAME)?.let { columnIndex ->
                    if (!cursor.isNull(columnIndex)) {
                        name = cursor.getString(columnIndex)?.trim()?.ifBlank { null }
                    }
                }
                cursor.getColumnIndexOrNull(OpenableColumns.SIZE)?.let { columnIndex ->
                    if (!cursor.isNull(columnIndex)) {
                        size = cursor.getLong(columnIndex)
                    }
                }
            }
        }
    }
    return JSONObject()
        .put("uri", uri.toString())
        .put("path", copiedFile.absolutePath)
        .put("name", name ?: JSONObject.NULL)
        .put("mimeType", resolver.getType(uri) ?: JSONObject.NULL)
        .put("size", size ?: copiedFile.length())
}

private fun buildComposeDslFilePickerResultJson(
    context: android.content.Context,
    copiedFiles: List<Pair<Uri, File>>,
    cancelled: Boolean
): String {
    val files = JSONArray()
    copiedFiles.forEach { (uri, copiedFile) ->
        files.put(queryComposeDslPickedFileMeta(context, uri, copiedFile))
    }
    return JSONObject()
        .put("cancelled", cancelled)
        .put("files", files)
        .toString()
}

private fun sanitizeComposeDslPickedFileName(rawName: String): String {
    val sanitized =
        rawName
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim()
            .ifBlank { "picked_file" }
    return sanitized.takeLast(120)
}

private fun extractComposeDslPickedFileExtension(fileName: String): String {
    val dotIndex = fileName.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= fileName.length - 1) {
        return ""
    }
    val extension =
        fileName.substring(dotIndex + 1)
            .trim()
            .take(16)
    return extension
        .takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
        .orEmpty()
}

private fun stageComposeDslPickedFile(
    context: android.content.Context,
    uri: Uri,
    displayName: String?
): File {
    val stagingDir = File(OperitPaths.cleanOnExitDir(), "compose_file_picker").apply { mkdirs() }
    val safeBaseName = sanitizeComposeDslPickedFileName(displayName ?: uri.lastPathSegment ?: "picked_file")
    val extension = extractComposeDslPickedFileExtension(safeBaseName)
    val targetFileName =
        buildString {
            append("picked_")
            append(System.currentTimeMillis())
            append("_")
            append(UUID.randomUUID().toString().replace("-", "").take(12))
            if (extension.isNotEmpty()) {
                append(".")
                append(extension)
            }
        }
    val targetFile = File(stagingDir, targetFileName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        targetFile.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw IllegalStateException("无法打开所选文件")
    return targetFile
}

private fun ToolPkgComposeDslNode.containsNodeType(typeToken: String): Boolean {
    if (normalizeToken(type) == typeToken) {
        return true
    }
    if (children.any { child -> child.containsNodeType(typeToken) }) {
        return true
    }
    return slots.values.any { slotChildren ->
        slotChildren.any { child -> child.containsNodeType(typeToken) }
    }
}

private fun buildComposeDslExecutionContextKey(
    containerPackageName: String,
    uiModuleId: String,
    routeInstanceId: String
): String =
    "toolpkg_compose_dsl:${containerPackageName.trim().ifBlank { "default" }}:${uiModuleId.trim().ifBlank { "default" }}:${routeInstanceId.trim().ifBlank { "default" }}"

internal fun normalizeToken(raw: String): String =
    raw.lowercase(Locale.ROOT)
        .replace("-", "")
        .replace("_", "")
        .trim()

private fun buildZeroArgGetterByToken(
    ownerClass: Class<*>,
    returnTypeMatcher: (Class<*>) -> Boolean
): Map<String, java.lang.reflect.Method> =
    ownerClass.methods
        .asSequence()
        .filter { method ->
            method.name.startsWith("get") &&
                method.parameterCount == 0 &&
                returnTypeMatcher(method.returnType)
        }
        .onEach { method -> method.isAccessible = true }
        .associateBy { method -> normalizeToken(method.name.removePrefix("get")) }

private val typographyGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(androidx.compose.material3.Typography::class.java) { returnType ->
        returnType == androidx.compose.ui.text.TextStyle::class.java
    }
}
private val horizontalAlignmentGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Alignment::class.java) { returnType ->
        Alignment.Horizontal::class.java.isAssignableFrom(returnType)
    }
}
private val verticalAlignmentGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Alignment::class.java) { returnType ->
        Alignment.Vertical::class.java.isAssignableFrom(returnType)
    }
}
private val boxAlignmentGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Alignment::class.java) { returnType ->
        returnType == Alignment::class.java
    }
}
private val horizontalArrangementGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Arrangement::class.java) { returnType ->
        Arrangement.Horizontal::class.java.isAssignableFrom(returnType)
    }
}
private val verticalArrangementGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Arrangement::class.java) { returnType ->
        Arrangement.Vertical::class.java.isAssignableFrom(returnType)
    }
}
private val fontWeightGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(FontWeight.Companion::class.java) { returnType ->
        FontWeight::class.java.isAssignableFrom(returnType)
    }
}

private val colorSchemeFieldByToken: Map<String, java.lang.reflect.Field> by lazy {
    androidx.compose.material3.ColorScheme::class.java.declaredFields
        .onEach { it.isAccessible = true }
        .associateBy { field ->
            normalizeToken(field.name)
        }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun ToolPkgComposeDslToolScreen(
    navController: NavController,
    routeInstanceId: String,
    containerPackageName: String,
    uiModuleId: String,
    fallbackTitle: String
) {
    val context = LocalContext.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val setTopBarTitleContent = LocalTopBarTitleContent.current
    val setScreenSoftInputMode = LocalSetScreenSoftInputMode.current
    val setUseScreenImePadding = LocalSetUseScreenImePadding.current
    val scope = rememberCoroutineScope()
    val renderMutex = remember { Mutex() }
    val currentLanguage =
        (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales.get(0)
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
        )?.toLanguageTag()
            ?.trim()
            ?.ifBlank { null }
            ?: "en"

    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val executionContextKey = remember(routeInstanceId, containerPackageName, uiModuleId) {
        buildComposeDslExecutionContextKey(
            containerPackageName = containerPackageName,
            uiModuleId = uiModuleId,
            routeInstanceId = routeInstanceId
        )
    }
    var pendingFilePickerLaunch by remember(executionContextKey) {
        mutableStateOf<ComposeDslPendingFilePickerLaunch?>(null)
    }
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val filePickerLauncher: ActivityResultLauncher<Intent>? =
        if (activityResultRegistryOwner != null) {
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val pending = pendingFilePickerLaunch
                pendingFilePickerLaunch = null
                if (pending == null) {
                    return@rememberLauncherForActivityResult
                }
                if (result.resultCode != android.app.Activity.RESULT_OK) {
                    pending.onComplete(Result.success(buildComposeDslFilePickerResultJson(context, emptyList(), true)))
                    return@rememberLauncherForActivityResult
                }
                val data = result.data
                val selectedUris =
                    buildList {
                        data?.data?.let(::add)
                        val clipData = data?.clipData
                        if (clipData != null) {
                            for (index in 0 until clipData.itemCount) {
                                clipData.getItemAt(index)?.uri?.let(::add)
                            }
                        }
                    }.distinctBy { uri -> uri.toString() }
                if (selectedUris.isEmpty()) {
                    pending.onComplete(Result.success(buildComposeDslFilePickerResultJson(context, emptyList(), true)))
                    return@rememberLauncherForActivityResult
                }
                if (pending.request.persistPermission) {
                    val flags =
                        (data?.flags ?: 0) and
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (flags != 0) {
                        selectedUris.forEach { uri ->
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(uri, flags)
                            }
                        }
                    }
                }
                runCatching {
                    val copiedFiles =
                        selectedUris.map { uri ->
                            val copiedFile = stageComposeDslPickedFile(context, uri, null)
                            uri to copiedFile
                        }
                    buildComposeDslFilePickerResultJson(
                        context = context,
                        copiedFiles = copiedFiles,
                        cancelled = false
                    )
                }.fold(
                    onSuccess = { resultJson ->
                        pending.onComplete(Result.success(resultJson))
                    },
                    onFailure = { error ->
                        AppLogger.e(TAG, "compose_dsl file picker staging failed", error)
                        pending.onComplete(
                            Result.failure(
                                IllegalStateException(
                                    error.message?.trim().orEmpty().ifBlank { "复制所选文件到临时目录失败" }
                                )
                            )
                        )
                    }
                )
            }
        } else {
            null
        }
    val jsEngine = remember(packageManager, executionContextKey) {
        packageManager.getToolPkgExecutionEngine(
            contextKey = executionContextKey,
            containerPackageName = containerPackageName
        )
    }

    var script by remember(containerPackageName, uiModuleId) { mutableStateOf<String?>(null) }
    var scriptScreenPath by remember(containerPackageName, uiModuleId) { mutableStateOf<String?>(null) }
    var renderResult by remember(containerPackageName, uiModuleId) {
        mutableStateOf<ToolPkgComposeDslRenderResult?>(null)
    }
    var errorMessage by remember(containerPackageName, uiModuleId) { mutableStateOf<String?>(null) }
    var isLoading by remember(containerPackageName, uiModuleId) { mutableStateOf(true) }
    var isDispatching by remember(containerPackageName, uiModuleId) { mutableStateOf(false) }
    var dispatchingCount by remember(containerPackageName, uiModuleId) { mutableStateOf(0) }
    var hasDispatchedInitialOnLoad by
        rememberSaveable(routeInstanceId, containerPackageName, uiModuleId) {
            mutableStateOf(false)
        }
    var nextDispatchTicket by remember(containerPackageName, uiModuleId) { mutableStateOf(1L) }
    var pendingTreeRerenderJob by remember(containerPackageName, uiModuleId) { mutableStateOf<Job?>(null) }
    val nextTextInputSyncTicket =
        remember(containerPackageName, uiModuleId) { AtomicLong(1L) }
    val pendingTextInputSyncs =
        remember(containerPackageName, uiModuleId) {
            linkedMapOf<Long, CompletableDeferred<Unit>>()
        }
    val settledDispatchTickets = remember(containerPackageName, uiModuleId) { mutableSetOf<Long>() }
    val requiresWebViewImeResize =
        remember(renderResult?.tree) {
            renderResult?.tree?.containsNodeType("webview") == true
        }
    val topBarTitleNodes =
        remember(renderResult?.tree) {
            renderResult?.tree?.slots?.get("topBarTitle").orEmpty()
        }

    fun buildModuleSpec(screenPath: String?): Map<String, Any?> =
        mapOf(
            "id" to uiModuleId,
            "runtime" to "compose_dsl",
            "screen" to (screenPath ?: ""),
            "title" to fallbackTitle,
            "toolPkgId" to containerPackageName
        )

    fun buildActionRuntimeOptions(): Map<String, Any?> {
        val runtimeOptions =
            mutableMapOf<String, Any?>(
                "packageName" to containerPackageName,
                "containerPackageName" to containerPackageName,
                "toolPkgId" to containerPackageName,
                "__operit_ui_package_name" to containerPackageName,
                "__operit_ui_toolpkg_id" to containerPackageName,
                "uiModuleId" to uiModuleId,
                "__operit_ui_module_id" to uiModuleId,
                "__operit_toolpkg_runtime_kind" to "ui",
                "routeInstanceId" to routeInstanceId,
                "__operit_route_instance_id" to routeInstanceId,
                "executionContextKey" to executionContextKey,
                "__operit_compose_execution_context_key" to executionContextKey,
                "__operit_package_lang" to currentLanguage
            )
        val currentScreenPath = scriptScreenPath?.trim().orEmpty()
        if (currentScreenPath.isNotEmpty()) {
            runtimeOptions["__operit_script_screen"] = currentScreenPath
        }
        return runtimeOptions
    }

    fun updateDebugSnapshot(
        phase: String,
        rawRenderResult: Any? = null,
        parsedRenderResult: ToolPkgComposeDslRenderResult? = renderResult,
        error: String? = errorMessage
    ) {
        ToolPkgComposeDslDebugSnapshotStore.update(
            ToolPkgComposeDslDebugSnapshot(
                routeInstanceId = routeInstanceId,
                containerPackageName = containerPackageName,
                uiModuleId = uiModuleId,
                fallbackTitle = fallbackTitle,
                scriptScreenPath = scriptScreenPath,
                scriptSource = script,
                phase = phase,
                rawRenderResultText = rawRenderResult?.toString(),
                renderResult = parsedRenderResult,
                errorMessage = error,
                isLoading = isLoading,
                isDispatching = isDispatching,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun applyBlockingRenderResult(
        phase: String,
        rawResult: Any?
    ) {
        val parsed = ToolPkgComposeDslParser.parseRenderResult(rawResult) ?: return
        composeDslWebViewMainHandler.post {
            renderResult = parsed
            errorMessage = null
            updateDebugSnapshot(
                phase = phase,
                rawRenderResult = rawResult,
                parsedRenderResult = parsed,
                error = null
            )
        }
    }

    val webViewHostContext =
        remember(routeInstanceId, executionContextKey, jsEngine) {
            ComposeDslWebViewHostContext(
                routeInstanceId = routeInstanceId,
                executionContextKey = executionContextKey,
                jsEngine = jsEngine,
                runtimeOptionsProvider = ::buildActionRuntimeOptions,
                applyRenderResult = ::applyBlockingRenderResult
            )
        }

    suspend fun rerenderComposeDslTreeInternal(source: String) {
        val rawResult =
            withContext(Dispatchers.IO) {
                jsEngine.rerenderComposeDslTree(
                    runtimeOptions = buildActionRuntimeOptions()
                )
            }
        val parsed = ToolPkgComposeDslParser.parseRenderResult(rawResult)
        if (parsed == null) {
            val rawText = rawResult?.toString()?.trim().orEmpty()
            AppLogger.e(
                TAG,
                "compose_dsl tree rerender failed: source=$source, raw=${rawText.ifBlank { "<empty>" }}"
            )
            updateDebugSnapshot(
                phase = "tree_rerender_invalid:$source",
                rawRenderResult = rawResult,
                parsedRenderResult = renderResult,
                error = errorMessage
            )
            return
        }
        renderResult = parsed
        errorMessage = null
        updateDebugSnapshot(
            phase = "tree_rerender:$source",
            rawRenderResult = rawResult,
            parsedRenderResult = parsed,
            error = null
        )
    }

    fun requestComposeDslTreeRerender(immediate: Boolean = false) {
        pendingTreeRerenderJob?.cancel()
        pendingTreeRerenderJob =
            scope.launch {
                if (!immediate) {
                    withFrameNanos { }
                }
                renderMutex.withLock {
                    rerenderComposeDslTreeInternal(
                        source = if (immediate) "immediate" else "next_frame"
                    )
                }
            }
    }

    fun hasPendingTextInputSyncs(): Boolean = pendingTextInputSyncs.isNotEmpty()

    suspend fun awaitPendingTextInputSyncs() {
        val pendingCompletions = pendingTextInputSyncs.values.toList()
        pendingCompletions.forEach { completion ->
            runCatching { completion.await() }
        }
    }

    fun flushTextInputSyncsAndRerender() {
        scope.launch {
            awaitPendingTextInputSyncs()
            requestComposeDslTreeRerender(true)
        }
    }

    fun dispatchActionInternal(
        actionId: String,
        payload: Any? = null,
        onSettled: (() -> Unit)? = null,
        flushPendingTextInputs: Boolean = true
    ): Boolean {
        val normalizedActionId = actionId.trim()
        if (normalizedActionId.isBlank()) {
            onSettled?.invoke()
            return false
        }
        if (flushPendingTextInputs && hasPendingTextInputSyncs()) {
            scope.launch {
                awaitPendingTextInputSyncs()
                dispatchActionInternal(
                    actionId = normalizedActionId,
                    payload = payload,
                    onSettled = onSettled,
                    flushPendingTextInputs = false
                )
            }
            return true
        }
        pendingTreeRerenderJob?.cancel()
        pendingTreeRerenderJob = null
        AppLogger.d(
            TAG,
            "compose_dsl dispatchAction: routeInstanceId=$routeInstanceId, package=$containerPackageName, uiModuleId=$uiModuleId, actionId=$normalizedActionId, payload=$payload"
        )
        val dispatchTicket = nextDispatchTicket
        nextDispatchTicket += 1

        dispatchingCount += 1
        isDispatching = dispatchingCount > 0

        val dispatched =
            jsEngine.dispatchComposeDslActionAsync(
                actionId = normalizedActionId,
                payload = payload,
                runtimeOptions = buildActionRuntimeOptions(),
                onIntermediateResult = { intermediateResult ->
                    if (settledDispatchTickets.contains(dispatchTicket)) {
                        return@dispatchComposeDslActionAsync
                    }
                    val parsedIntermediate =
                        ToolPkgComposeDslParser.parseRenderResult(intermediateResult)
                    if (parsedIntermediate != null) {
                        renderResult = parsedIntermediate
                        errorMessage = null
                        updateDebugSnapshot(
                            phase = "dispatch_intermediate",
                            rawRenderResult = intermediateResult,
                            parsedRenderResult = parsedIntermediate,
                            error = null
                        )
                    }
                },
                onFinalResult = { finalResult ->
                    if (settledDispatchTickets.contains(dispatchTicket)) {
                        return@dispatchComposeDslActionAsync
                    }
                    val parsedFinal =
                        ToolPkgComposeDslParser.parseRenderResult(finalResult)
                    if (parsedFinal != null) {
                        renderResult = parsedFinal
                        errorMessage = null
                        updateDebugSnapshot(
                            phase = "dispatch_final",
                            rawRenderResult = finalResult,
                            parsedRenderResult = parsedFinal,
                            error = null
                        )
                    }
                },
                onComplete = {
                    dispatchingCount = (dispatchingCount - 1).coerceAtLeast(0)
                    isDispatching = dispatchingCount > 0
                    updateDebugSnapshot(
                        phase = "dispatch_complete",
                        parsedRenderResult = renderResult,
                        error = errorMessage
                    )
                    settledDispatchTickets.add(dispatchTicket)
                    if (settledDispatchTickets.size > 64) {
                        val latestTickets = settledDispatchTickets.toList().sortedDescending().take(32).toSet()
                        settledDispatchTickets.retainAll(latestTickets)
                    }
                    onSettled?.invoke()
                },
                onError = { error ->
                    errorMessage = "compose_dsl runtime error: $error"
                    updateDebugSnapshot(
                        phase = "dispatch_error",
                        parsedRenderResult = renderResult,
                        error = errorMessage
                    )
                    AppLogger.e(
                        TAG,
                        "compose_dsl async action failed: actionId=$normalizedActionId, error=$error"
                    )
                }
            )

        if (!dispatched) {
            dispatchingCount = (dispatchingCount - 1).coerceAtLeast(0)
            isDispatching = dispatchingCount > 0
            updateDebugSnapshot(
                phase = "dispatch_not_started",
                parsedRenderResult = renderResult,
                error = errorMessage
            )
            settledDispatchTickets.add(dispatchTicket)
            onSettled?.invoke()
        }
        return dispatched
    }

    fun dispatchAction(actionId: String, payload: Any? = null) {
        dispatchActionInternal(actionId = actionId, payload = payload)
    }

    fun dispatchTextInputAction(actionId: String, text: String) {
        val normalizedActionId = actionId.trim()
        if (normalizedActionId.isBlank()) {
            return
        }
        val syncTicket = nextTextInputSyncTicket.getAndIncrement()
        val completion = CompletableDeferred<Unit>()
        pendingTextInputSyncs[syncTicket] = completion
        dispatchActionInternal(
            actionId = normalizedActionId,
            payload =
                mapOf(
                    "__composeTextFieldPayload" to true,
                    "__no_render" to true,
                    "value" to text
                ),
            onSettled = {
                pendingTextInputSyncs.remove(syncTicket)
                if (!completion.isCompleted) {
                    completion.complete(Unit)
                }
                if (!hasPendingTextInputSyncs()) {
                    requestComposeDslTreeRerender(false)
                }
            },
            flushPendingTextInputs = false
        )
    }

    suspend fun dispatchActionAwait(actionId: String, payload: Any? = null) {
        val completion = CompletableDeferred<Unit>()
        dispatchActionInternal(
            actionId = actionId,
            payload = payload,
            onSettled = {
                if (!completion.isCompleted) {
                    completion.complete(Unit)
                }
            }
        )
        completion.await()
    }

    SideEffect {
        if (!isCurrentScreen) {
            setTopBarTitleContent(null)
            return@SideEffect
        }

        if (topBarTitleNodes.isNotEmpty()) {
            setTopBarTitleContent(
                TopBarTitleContent {
                    CompositionLocalProvider(
                        LocalComposeDslActionHandler provides ::dispatchAction,
                        LocalComposeDslTextInputActionHandler provides ::dispatchTextInputAction,
                        LocalComposeDslFlushTextInputHandler provides ::flushTextInputSyncsAndRerender,
                        LocalComposeDslSuspendingActionHandler provides ::dispatchActionAwait,
                        LocalComposeDslRouteInstanceId provides routeInstanceId,
                        LocalComposeDslWebViewHost provides webViewHostContext
                    ) {
                        renderComposeDslNodes(
                            nodes = topBarTitleNodes,
                            onAction = ::dispatchAction,
                            nodePath = "0:topBarTitle"
                        )
                    }
                }
            )
        } else {
            setTopBarTitleContent(null)
        }

        if (requiresWebViewImeResize) {
            setScreenSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setUseScreenImePadding(true)
        } else {
            setScreenSoftInputMode(null)
            setUseScreenImePadding(false)
        }
    }

    suspend fun render() {
        var snapshotPhase = "render_start"
        var snapshotRawResult: Any? = null
        var snapshotParsedResult: ToolPkgComposeDslRenderResult? = null
        var snapshotError: String? = null
        renderMutex.withLock {
            try {
                pendingTreeRerenderJob?.cancel()
                pendingTreeRerenderJob = null
                pendingTextInputSyncs.values.forEach { completion ->
                    if (!completion.isCompleted) {
                        completion.complete(Unit)
                    }
                }
                pendingTextInputSyncs.clear()
                isLoading = true
                dispatchingCount = 0
                isDispatching = false
                errorMessage = null

                val scriptText: String? =
                    if (script == null) {
                        val loaded =
                            withContext(Dispatchers.IO) {
                                Pair(
                                    packageManager.getToolPkgComposeDslScript(
                                        containerPackageName = containerPackageName,
                                        uiModuleId = uiModuleId
                                    ),
                                    packageManager.getToolPkgComposeDslScreenPath(
                                        containerPackageName = containerPackageName,
                                        uiModuleId = uiModuleId
                                    )
                                )
                            }
                        if (scriptScreenPath.isNullOrBlank() && !loaded.second.isNullOrBlank()) {
                            scriptScreenPath = loaded.second
                        }
                        loaded.first
                    } else {
                        script
                    }

                if (scriptText.isNullOrBlank()) {
                    renderResult = null
                    errorMessage =
                        "compose_dsl script not found: package=$containerPackageName, module=$uiModuleId"
                    snapshotPhase = "render_missing_script"
                    snapshotParsedResult = null
                    snapshotError = errorMessage
                    return
                }
                if (script == null) {
                    script = scriptText
                }

                val rawResult =
                    withContext(Dispatchers.IO) {
                        jsEngine.executeComposeDslScript(
                            script = scriptText,
                            runtimeOptions =
                                mapOf(
                                    "packageName" to containerPackageName,
                                    "toolPkgId" to containerPackageName,
                                    "uiModuleId" to uiModuleId,
                                    "__operit_ui_module_id" to uiModuleId,
                                    "__operit_toolpkg_runtime_kind" to "ui",
                                    "routeInstanceId" to routeInstanceId,
                                    "__operit_route_instance_id" to routeInstanceId,
                                    "executionContextKey" to executionContextKey,
                                    "__operit_compose_execution_context_key" to executionContextKey,
                                    "__operit_package_lang" to currentLanguage,
                                    "__operit_script_screen" to (scriptScreenPath ?: ""),
                                    "moduleSpec" to buildModuleSpec(scriptScreenPath),
                                    "state" to (renderResult?.state ?: emptyMap<String, Any?>()),
                                    "memo" to (renderResult?.memo ?: emptyMap<String, Any?>())
                                )
                        )
                    }
                snapshotRawResult = rawResult

                val rawText = rawResult?.toString()?.trim().orEmpty()
                val parsed = ToolPkgComposeDslParser.parseRenderResult(rawResult)
                if (parsed == null) {
                    val normalizedError =
                        extractJsExecutionErrorMessage(rawResult)
                            ?: if (rawText.isNotBlank()) {
                                "Invalid compose_dsl result: $rawText"
                            } else {
                                "Invalid compose_dsl result"
                            }
                    renderResult = null
                    errorMessage = normalizedError
                    snapshotPhase = "render_invalid_result"
                    snapshotParsedResult = null
                    snapshotError = normalizedError
                    AppLogger.e(TAG, normalizedError)
                    return
                }

                renderResult = parsed
                errorMessage = null
                snapshotPhase = "render_success"
                snapshotParsedResult = parsed
                snapshotError = null
            } catch (e: Exception) {
                renderResult = null
                errorMessage = "compose_dsl runtime error: ${e.message}"
                snapshotPhase = "render_exception"
                snapshotParsedResult = null
                snapshotError = errorMessage
                AppLogger.e(TAG, "compose_dsl render failed", e)
            } finally {
                isLoading = false
                updateDebugSnapshot(
                    phase = snapshotPhase,
                    rawRenderResult = snapshotRawResult,
                    parsedRenderResult = snapshotParsedResult,
                    error = snapshotError
                )
            }
        }
    }

    LaunchedEffect(routeInstanceId, containerPackageName, uiModuleId) {
        scope.launch {
            render()
        }
    }

    DisposableEffect(executionContextKey) {
        ComposeDslFilePickerHostRegistry.bind(executionContextKey) { request, onComplete ->
            val launcher = filePickerLauncher
            if (launcher == null) {
                onComplete(Result.failure(IllegalStateException("compose file picker requires an activity result registry owner")))
                return@bind
            }
            pendingFilePickerLaunch = ComposeDslPendingFilePickerLaunch(request = request, onComplete = onComplete)
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (request.mimeTypes.size == 1) request.mimeTypes.first() else "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.allowMultiple)
                    if (request.mimeTypes.size > 1) {
                        putExtra(Intent.EXTRA_MIME_TYPES, request.mimeTypes.toTypedArray())
                    }
                }
            launcher.launch(intent)
        }
        onDispose {
            pendingFilePickerLaunch?.onComplete?.invoke(
                Result.failure(IllegalStateException("compose file picker disposed"))
            )
            pendingFilePickerLaunch = null
            ComposeDslFilePickerHostRegistry.unbind(executionContextKey)
            pendingTreeRerenderJob?.cancel()
            pendingTreeRerenderJob = null
            pendingTextInputSyncs.values.forEach { completion ->
                if (!completion.isCompleted) {
                    completion.complete(Unit)
                }
            }
            pendingTextInputSyncs.clear()
            setTopBarTitleContent(null)
            ToolPkgComposeDslDebugSnapshotStore.clear(routeInstanceId)
            ComposeDslWebViewHostRegistry.clearExecutionContext(executionContextKey)
            packageManager.releaseToolPkgExecutionEngine(executionContextKey, jsEngine)
        }
    }

    CustomScaffold { paddingValues ->
        val rootNode = renderResult?.tree
        val rootOnLoadActionId =
            remember(rootNode) {
                rootNode?.let { node ->
                    ToolPkgComposeDslParser.extractActionId(node.props["onLoad"])
                }
            }
        val contentModifier =
            Modifier
                .padding(paddingValues)
                .fillMaxSize()

        LaunchedEffect(rootNode, rootOnLoadActionId, hasDispatchedInitialOnLoad) {
            if (rootNode == null || rootOnLoadActionId.isNullOrBlank() || hasDispatchedInitialOnLoad) {
                return@LaunchedEffect
            }
            withFrameNanos { }
            if (hasDispatchedInitialOnLoad) {
                return@LaunchedEffect
            }
            hasDispatchedInitialOnLoad = true
            dispatchAction(actionId = rootOnLoadActionId, payload = null)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    Column(
                        modifier =
                            Modifier.align(Alignment.Center)
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    hasDispatchedInitialOnLoad = false
                                    render()
                                }
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
                rootNode != null -> {
                    CompositionLocalProvider(
                        LocalComposeDslActionHandler provides ::dispatchAction,
                        LocalComposeDslTextInputActionHandler provides ::dispatchTextInputAction,
                        LocalComposeDslFlushTextInputHandler provides ::flushTextInputSyncsAndRerender,
                        LocalComposeDslSuspendingActionHandler provides ::dispatchActionAwait,
                        LocalComposeDslRouteInstanceId provides routeInstanceId,
                        LocalComposeDslWebViewHost provides webViewHostContext
                    ) {
                        // Let compose_dsl content own its own scrolling behavior.
                        // Wrapping the whole screen in an outer verticalScroll changes
                        // root measurement semantics and breaks full-screen layouts.
                        Box(modifier = contentModifier) {
                            renderComposeDslNode(
                                node = rootNode,
                                onAction = ::dispatchAction,
                                nodePath = "0"
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun RenderToolPkgComposeDslNode(
    node: ToolPkgComposeDslNode,
    modifier: Modifier = Modifier,
    onAction: (String, Any?) -> Unit = { _, _ -> },
    onTextInputAction: (String, String) -> Unit = { _, _ -> },
    onFlushTextInput: () -> Unit = { }
) {
    CompositionLocalProvider(
        LocalComposeDslActionHandler provides onAction,
        LocalComposeDslTextInputActionHandler provides onTextInputAction,
        LocalComposeDslFlushTextInputHandler provides onFlushTextInput,
        LocalComposeDslSuspendingActionHandler provides { actionId, payload ->
            onAction(actionId, payload)
        },
        LocalComposeDslRouteInstanceId provides "",
        LocalComposeDslWebViewHost provides null
    ) {
        Box(modifier = modifier) {
            renderComposeDslNode(
                node = node,
                onAction = onAction,
                nodePath = "0"
            )
        }
    }
}

internal val LocalComposeDslActionHandler = staticCompositionLocalOf<(String, Any?) -> Unit> {
    { _, _ -> }
}
internal val LocalComposeDslXmlStream = staticCompositionLocalOf<Stream<String>?> { null }
internal val LocalComposeDslTextInputActionHandler =
    staticCompositionLocalOf<(String, String) -> Unit> {
        { _, _ -> }
    }
internal val LocalComposeDslFlushTextInputHandler = staticCompositionLocalOf<() -> Unit> {
    { }
}
internal val LocalComposeDslSuspendingActionHandler =
    staticCompositionLocalOf<suspend (String, Any?) -> Unit> {
        { _, _ -> }
    }
internal val LocalComposeDslRouteInstanceId = staticCompositionLocalOf { "" }
private data class ComposeDslDebugNodeInfo(
    val routeInstanceId: String,
    val nodePath: String,
    val nodeType: String,
    val nodeKey: String?
)

private val LocalComposeDslDebugNodeInfo = staticCompositionLocalOf<ComposeDslDebugNodeInfo?> {
    null
}

internal typealias ComposeDslModifierResolver =
    @Composable (Modifier, Map<String, Any?>) -> Modifier

@Composable
internal fun renderComposeDslNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver = { base, props ->
        defaultComposeDslModifierResolver(base, props)
    }
) {
    val routeInstanceId = LocalComposeDslRouteInstanceId.current
    val nodeKey = node.props["key"]?.toString()?.trim()?.ifBlank { null }
    CompositionLocalProvider(
        LocalComposeDslDebugNodeInfo provides
            ComposeDslDebugNodeInfo(
                routeInstanceId = routeInstanceId,
                nodePath = nodePath,
                nodeType = node.type,
                nodeKey = nodeKey
            )
    ) {
        val normalizedType = normalizeToken(node.type)
        if (normalizedType == "canvas") {
            renderCanvasNode(node, onAction, modifierResolver)
            return@CompositionLocalProvider
        }
        if (normalizedType == "webview") {
            renderWebViewNode(node, onAction, modifierResolver)
            return@CompositionLocalProvider
        }
        if (normalizedType == "markdown") {
            renderMarkdownNode(node, onAction, nodePath, modifierResolver)
            return@CompositionLocalProvider
        }
        val renderer = composeDslGeneratedNodeRendererRegistry[normalizedType]
        if (renderer != null) {
            renderer(node, onAction, nodePath, modifierResolver)
            return@CompositionLocalProvider
        }
        Text(
            text = "Unsupported node: ${node.type}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun renderMarkdownNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
    val textColor = props.colorOrNull("color") ?: MaterialTheme.colorScheme.onSurface
    val fontSize = props.floatOrNull("fontSize")?.sp ?: Unspecified
    val streamTagName = props.stringOrNull("streamTagName")
    val xmlStream = LocalComposeDslXmlStream.current
    val markdownStream =
        remember(xmlStream, streamTagName) {
            if (xmlStream == null || streamTagName == null) {
                null
            } else {
                createXmlTagBodyCharStream(xmlStream, streamTagName)
            }
        }

    if (markdownStream != null) {
        StreamMarkdownRenderer(
            markdownStream = markdownStream,
            modifier = modifier,
            textColor = textColor,
            fontSize = fontSize,
            xmlRenderer = remember { DefaultXmlRenderer() },
            enableDialogs = props.bool("enableDialogs", true),
            fillMaxWidth = props.bool("fillMaxWidth", true)
        )
        return
    }

    MarkdownTextComposable(
        text = props.string("text"),
        textColor = textColor,
        modifier = modifier,
        fontSize = fontSize,
        enableDialogs = props.bool("enableDialogs", true)
    )
}

private fun createXmlTagBodyCharStream(
    xmlStream: Stream<String>,
    tagName: String
): Stream<Char> = stream {
    val endTag = "</$tagName>"
    var startTagClosed = false
    var reachedEndTag = false
    val tailBuffer = StringBuilder()

    xmlStream.collect { chunk ->
        chunk.forEach { ch ->
            if (reachedEndTag) {
                return@forEach
            }

            if (!startTagClosed) {
                if (ch == '>') {
                    startTagClosed = true
                }
                return@forEach
            }

            tailBuffer.append(ch)

            while (tailBuffer.length > endTag.length) {
                emit(tailBuffer[0])
                tailBuffer.deleteCharAt(0)
            }

            if (tailBuffer.length == endTag.length && tailBuffer.toString() == endTag) {
                tailBuffer.setLength(0)
                reachedEndTag = true
            }
        }
    }

    if (!reachedEndTag && tailBuffer.isNotEmpty()) {
        tailBuffer.toString().forEach { emit(it) }
    }
}

@Composable
internal fun applyComposeDslNodeDebugLayoutModifier(modifier: Modifier): Modifier {
    val nodeInfo = LocalComposeDslDebugNodeInfo.current ?: return modifier
    if (nodeInfo.routeInstanceId.isBlank()) {
        return modifier
    }
    return modifier.onGloballyPositioned { coordinates ->
        val rootBounds = coordinates.boundsInRoot()
        val windowPosition = coordinates.positionInWindow()
        ToolPkgComposeDslDebugSnapshotStore.updateLayout(
            ToolPkgComposeDslLayoutSnapshot(
                routeInstanceId = nodeInfo.routeInstanceId,
                nodePath = nodeInfo.nodePath,
                nodeType = nodeInfo.nodeType,
                nodeKey = nodeInfo.nodeKey,
                rootX = rootBounds.left,
                rootY = rootBounds.top,
                width = rootBounds.width,
                height = rootBounds.height,
                windowX = windowPosition.x,
                windowY = windowPosition.y,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }
}

internal typealias ComposeDslNodeRenderer =
    @Composable (ToolPkgComposeDslNode, (String, Any?) -> Unit, String, ComposeDslModifierResolver) -> Unit

private data class CanvasCommand(
    val type: String,
    val values: Map<String, Any?>,
    val unit: String,
    val color: Color,
    val brush: Brush?,
    val alpha: Float?,
    val strokeWidth: Float
)

private fun canvasNumberFromValue(value: Any?): Float? {
    return when (value) {
        is Number -> value.toFloat()
        is Map<*, *> -> {
            val raw = value["value"]
            when (raw) {
                is Number -> raw.toFloat()
                else -> raw?.toString()?.toFloatOrNull()
            }
        }
        else -> value?.toString()?.toFloatOrNull()
    }
}

private fun canvasUnitFromValue(value: Any?): String? {
    val map = value as? Map<*, *> ?: return null
    val token =
        map["unit"]?.toString()
            ?: map["__unit"]?.toString()
    return token?.trim()?.lowercase(Locale.ROOT).orEmpty().ifBlank { null }
}

@Composable
private fun parseCanvasCommands(raw: Any?): List<CanvasCommand> {
    val list = raw as? List<*> ?: return emptyList()
    @Composable
    fun parseCanvasBrush(value: Any?): Brush? {
        val map = value as? Map<*, *> ?: return null
        val type = map["type"]?.toString()?.trim()?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("canvas brush type is required")
        require(type == "verticalgradient") { "unsupported canvas brush type: $type" }
        val colorsRaw = map["colors"] as? List<*>
            ?: throw IllegalArgumentException("canvas brush colors are required")
        require(colorsRaw.isNotEmpty()) { "canvas brush colors are empty" }
        val colors = colorsRaw.mapIndexed { index, entry ->
            val resolved = resolveColorValue(entry)
                ?: throw IllegalArgumentException("canvas brush color not resolved at $index")
            resolved
        }
        return Brush.verticalGradient(colors)
    }
    return list.mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        val type = map["type"]?.toString()?.trim().orEmpty()
        if (type.isBlank()) return@mapNotNull null
        @Suppress("UNCHECKED_CAST")
        val values = map.entries.associate { (k, v) -> k.toString() to v } as Map<String, Any?>
        val unit =
            canvasUnitFromValue(values["unit"])
                ?: values["unit"]?.toString()?.trim()?.lowercase(Locale.ROOT)
                ?: "fraction"
        val alpha = canvasNumberFromValue(values["alpha"])
        val strokeWidth = canvasNumberFromValue(values["strokeWidth"]) ?: 1f
        val resolvedColor = resolveColorValue(values["color"])
        val color = resolvedColor ?: Color.Unspecified
        val brush = parseCanvasBrush(values["brush"])
        CanvasCommand(
            type = type.lowercase(Locale.ROOT),
            values = values,
            unit = unit,
            color = color,
            brush = brush,
            alpha = alpha,
            strokeWidth = strokeWidth
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun renderCanvasNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val commands = parseCanvasCommands(props["commands"])
    val textMeasurer = rememberTextMeasurer()
    val onTransformActionId = ToolPkgComposeDslParser.extractActionId(props["onTransform"])
    val onSizeChangedActionId = ToolPkgComposeDslParser.extractActionId(props["onSizeChanged"])
    var lastSize by remember { mutableStateOf(IntSize.Zero) }

    val transform = props["transform"] as? Map<*, *>
    val transformScale = (transform?.get("scale") as? Number)?.toFloat()
    val transformOffsetX = (transform?.get("offsetX") as? Number)?.toFloat()
    val transformOffsetY = (transform?.get("offsetY") as? Number)?.toFloat()
    val transformPivotX = (transform?.get("pivotX") as? Number)?.toFloat()
    val transformPivotY = (transform?.get("pivotY") as? Number)?.toFloat()
    var localScale by remember(transformScale, transformOffsetX, transformOffsetY) {
        mutableStateOf(transformScale ?: 1f)
    }
    var localOffset by remember(transformScale, transformOffsetX, transformOffsetY) {
        mutableStateOf(
            androidx.compose.ui.geometry.Offset(
                transformOffsetX ?: 0f,
                transformOffsetY ?: 0f
            )
        )
    }

    var modifier =
        applyScopedCommonModifier(Modifier, props, modifierResolver)
            .onSizeChanged { size ->
                if (onSizeChangedActionId != null && size != lastSize) {
                    lastSize = size
                    onAction(
                        onSizeChangedActionId,
                        mapOf(
                            "width" to size.width,
                            "height" to size.height
                        )
                    )
                }
            }

    if (onTransformActionId != null) {
        modifier =
            modifier.pointerInput(onTransformActionId) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    localScale = (localScale * zoom).coerceIn(0.6f, 2f)
                    localOffset = localOffset + pan
                    onAction(
                        onTransformActionId,
                        mapOf(
                            "__no_render" to true,
                            "centroidX" to centroid.x,
                            "centroidY" to centroid.y,
                            "panX" to pan.x,
                            "panY" to pan.y,
                            "zoom" to zoom,
                            "rotation" to rotation
                        )
                    )
                }
            }
    }

    Canvas(modifier = modifier) {
        val widthPx = size.width
        val heightPx = size.height

        fun resolve(value: Any?, defaultUnit: String, axis: String): Float {
            val unit = canvasUnitFromValue(value) ?: defaultUnit
            val numeric = canvasNumberFromValue(value) ?: 0f
            return when (unit) {
                "fraction" -> if (axis == "x") numeric * widthPx else numeric * heightPx
                "dp" -> numeric.dp.toPx()
                else -> numeric
            }
        }

        fun drawCommands() {
            commands.forEach { command ->
            val values = command.values
            val unit = command.unit
            val strokeWidth = command.strokeWidth
            val color = if (command.alpha != null) command.color.copy(alpha = command.alpha) else command.color
            val brush = command.brush
            val brushAlpha = command.alpha ?: 1f

            fun resolveStyle(): androidx.compose.ui.graphics.drawscope.DrawStyle {
                val token = values["style"]?.toString()?.trim()?.lowercase(Locale.ROOT)
                return if (token == "stroke") Stroke(width = strokeWidth) else Fill
            }

            fun buildPath(raw: Any?): Path? {
                val list = raw as? List<*> ?: return null
                val path = Path()
                list.forEach { opRaw ->
                    val op = opRaw as? Map<*, *> ?: return@forEach
                    val opType = op["type"]?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    when (opType) {
                        "moveto" -> {
                            val x = resolve(op["x"], unit, "x")
                            val y = resolve(op["y"], unit, "y")
                            path.moveTo(x, y)
                        }
                        "lineto" -> {
                            val x = resolve(op["x"], unit, "x")
                            val y = resolve(op["y"], unit, "y")
                            path.lineTo(x, y)
                        }
                        "cubicto" -> {
                            val x1 = resolve(op["x1"], unit, "x")
                            val y1 = resolve(op["y1"], unit, "y")
                            val x2 = resolve(op["x2"], unit, "x")
                            val y2 = resolve(op["y2"], unit, "y")
                            val x3 = resolve(op["x3"], unit, "x")
                            val y3 = resolve(op["y3"], unit, "y")
                            path.cubicTo(x1, y1, x2, y2, x3, y3)
                        }
                        "quadto" -> {
                            val x1 = resolve(op["x1"], unit, "x")
                            val y1 = resolve(op["y1"], unit, "y")
                            val x2 = resolve(op["x2"], unit, "x")
                            val y2 = resolve(op["y2"], unit, "y")
                            path.quadraticBezierTo(x1, y1, x2, y2)
                        }
                        "close" -> path.close()
                    }
                }
                return path
            }

            when (command.type) {
                "line" -> {
                    val x1 = resolve(values["x1"], unit, "x")
                    val y1 = resolve(values["y1"], unit, "y")
                    val x2 = resolve(values["x2"], unit, "x")
                    val y2 = resolve(values["y2"], unit, "y")
                    drawLine(color = color, start = androidx.compose.ui.geometry.Offset(x1, y1), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = strokeWidth)
                }
                "rect" -> {
                    val x = resolve(values["x"], unit, "x")
                    val y = resolve(values["y"], unit, "y")
                    val w = resolve(values["width"], unit, "x")
                    val h = resolve(values["height"], unit, "y")
                    val filled = (values["filled"] as? Boolean) ?: true
                    val style = if (filled) Fill else Stroke(width = strokeWidth)
                    if (brush != null) {
                        drawRect(
                            brush = brush,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            alpha = brushAlpha,
                            style = style
                        )
                    } else {
                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            style = style
                        )
                    }
                }
                "roundrect" -> {
                    val x = resolve(values["x"], unit, "x")
                    val y = resolve(values["y"], unit, "y")
                    val w = resolve(values["width"], unit, "x")
                    val h = resolve(values["height"], unit, "y")
                    val radius = canvasNumberFromValue(values["radius"]) ?: 0f
                    val filled = (values["filled"] as? Boolean) ?: true
                    val style = if (filled) Fill else Stroke(width = strokeWidth)
                    if (brush != null) {
                        drawRoundRect(
                            brush = brush,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                            alpha = brushAlpha,
                            style = style
                        )
                    } else {
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                            style = style
                        )
                    }
                }
                "drawroundrect" -> {
                    val x = resolve(values["x"], unit, "x")
                    val y = resolve(values["y"], unit, "y")
                    val w = resolve(values["width"], unit, "x")
                    val h = resolve(values["height"], unit, "y")
                    val radius = canvasNumberFromValue(values["cornerRadius"])
                        ?: canvasNumberFromValue(values["radius"])
                        ?: 0f
                    val style = resolveStyle()
                    if (brush != null) {
                        drawRoundRect(
                            brush = brush,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                            alpha = brushAlpha,
                            style = style
                        )
                    } else {
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                            style = style
                        )
                    }
                }
                "circle" -> {
                    val cx = resolve(values["cx"], unit, "x")
                    val cy = resolve(values["cy"], unit, "y")
                    val r = resolve(values["radius"], unit, "x")
                    val filled = (values["filled"] as? Boolean) ?: true
                    drawCircle(
                        color = color,
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        style = if (filled) Fill else Stroke(width = strokeWidth)
                    )
                }
                "text" -> {
                    val x = resolve(values["x"], unit, "x")
                    val y = resolve(values["y"], unit, "y")
                    val text = values["text"]?.toString().orEmpty()
                    if (text.isNotBlank()) {
                        val fontSize = canvasNumberFromValue(values["fontSize"]) ?: 10f
                        val minWidthRaw = values["minWidth"]
                        val maxWidthRaw = values["maxWidth"]
                        val minWidth = canvasNumberFromValue(minWidthRaw)
                        val maxWidth = canvasNumberFromValue(maxWidthRaw)
                        val minHeightRaw = values["minHeight"]
                        val maxHeightRaw = values["maxHeight"]
                        val minHeight = canvasNumberFromValue(minHeightRaw)
                        val maxHeight = canvasNumberFromValue(maxHeightRaw)
                        val maxLines = (values["maxLines"] as? Number)?.toInt() ?: Int.MAX_VALUE
                        val overflowToken = values["overflow"]?.toString()?.trim()?.lowercase(Locale.ROOT)
                        val overflow =
                            if (overflowToken == "ellipsis") TextOverflow.Ellipsis else TextOverflow.Clip
                        val layout = textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = TextStyle(color = color, fontSize = fontSize.sp),
                            maxLines = maxLines,
                            overflow = overflow,
                            constraints = if (minWidth != null || maxWidth != null || minHeight != null || maxHeight != null) {
                                androidx.compose.ui.unit.Constraints(
                                    minWidth = minWidth?.let { resolve(minWidthRaw, unit, "x").toInt() } ?: 0,
                                    maxWidth = maxWidth?.let { resolve(maxWidthRaw, unit, "x").toInt() }
                                        ?: androidx.compose.ui.unit.Constraints.Infinity,
                                    minHeight = minHeight?.let { resolve(minHeightRaw, unit, "y").toInt() } ?: 0,
                                    maxHeight = maxHeight?.let { resolve(maxHeightRaw, unit, "y").toInt() }
                                        ?: androidx.compose.ui.unit.Constraints.Infinity
                                )
                            } else {
                                androidx.compose.ui.unit.Constraints()
                            }
                        )
                        drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(x, y))
                    }
                }
                "drawtext" -> {
                    val x = resolve(values["x"], unit, "x")
                    val y = resolve(values["y"], unit, "y")
                    val text = values["text"]?.toString().orEmpty()
                    if (text.isNotBlank()) {
                        val fontSize = canvasNumberFromValue(values["fontSize"]) ?: 10f
                        val minWidthRaw = values["minWidth"]
                        val maxWidthRaw = values["maxWidth"]
                        val minWidth = canvasNumberFromValue(minWidthRaw)
                        val maxWidth = canvasNumberFromValue(maxWidthRaw)
                        val minHeightRaw = values["minHeight"]
                        val maxHeightRaw = values["maxHeight"]
                        val minHeight = canvasNumberFromValue(minHeightRaw)
                        val maxHeight = canvasNumberFromValue(maxHeightRaw)
                        val maxLines = (values["maxLines"] as? Number)?.toInt() ?: Int.MAX_VALUE
                        val overflowToken = values["overflow"]?.toString()?.trim()?.lowercase(Locale.ROOT)
                        val overflow =
                            if (overflowToken == "ellipsis") TextOverflow.Ellipsis else TextOverflow.Clip
                        val layout = textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = TextStyle(color = color, fontSize = fontSize.sp),
                            maxLines = maxLines,
                            overflow = overflow,
                            constraints = if (minWidth != null || maxWidth != null || minHeight != null || maxHeight != null) {
                                androidx.compose.ui.unit.Constraints(
                                    minWidth = minWidth?.let { resolve(minWidthRaw, unit, "x").toInt() } ?: 0,
                                    maxWidth = maxWidth?.let { resolve(maxWidthRaw, unit, "x").toInt() }
                                        ?: androidx.compose.ui.unit.Constraints.Infinity,
                                    minHeight = minHeight?.let { resolve(minHeightRaw, unit, "y").toInt() } ?: 0,
                                    maxHeight = maxHeight?.let { resolve(maxHeightRaw, unit, "y").toInt() }
                                        ?: androidx.compose.ui.unit.Constraints.Infinity
                                )
                            } else {
                                androidx.compose.ui.unit.Constraints()
                            }
                        )
                        drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(x, y))
                    }
                }
                "drawpath" -> {
                    val path = buildPath(values["path"]) ?: return@forEach
                    drawPath(path = path, color = color, style = resolveStyle())
                }
            }
            }
        }

        val activeScale = if (onTransformActionId != null) localScale else transformScale
        val activeOffset = if (onTransformActionId != null) localOffset else null
        if (activeScale != null || activeOffset != null) {
            val pivot =
                androidx.compose.ui.geometry.Offset(
                    transformPivotX ?: (widthPx / 2f),
                    transformPivotY ?: (heightPx / 2f)
                )
            withTransform(
                {
                    val offsetToUse =
                        activeOffset ?: androidx.compose.ui.geometry.Offset(0f, 0f)
                    translate(offsetToUse.x, offsetToUse.y)
                    if (activeScale != null) {
                        scale(activeScale, activeScale, pivot)
                    }
                }
            ) {
                drawCommands()
            }
        } else {
            drawCommands()
        }
    }
}

@Composable
internal fun applyCommonModifier(
    base: Modifier,
    props: Map<String, Any?>
): Modifier {
    var modifier = base

    val explicitWidth = props.floatOrNull("width")
    if (explicitWidth != null) {
        modifier = modifier.width(explicitWidth.dp)
    }
    val explicitHeight = props.floatOrNull("height")
    if (explicitHeight != null) {
        modifier = modifier.height(explicitHeight.dp)
    }

    if (props.bool("fillMaxSize", false)) {
        modifier = modifier.fillMaxSize()
    } else if (props.bool("fillMaxHeight", false)) {
        modifier = modifier.fillMaxHeight()
    } else if (props.bool("fillMaxWidth", false)) {
        modifier = modifier.fillMaxWidth()
    }

    props.commonPaddingSpecOrNull()?.let { paddingSpec ->
        modifier = paddingSpec.applyTo(modifier)
    }

    val backgroundBrush = props["backgroundBrush"]
    if (backgroundBrush != null) {
        val shape = shapeFromValue(props["backgroundShape"]) ?: shapeFromValue(props["shape"])
        val brush = parseBrush(backgroundBrush)
        if (brush != null) {
            if (shape != null) {
                modifier = modifier.clip(shape).background(brush, shape = shape)
            } else {
                modifier = modifier.background(brush)
            }
        }
    } else {
        val backgroundColor =
            resolveColorValue(
                props["backgroundColor"]
                    ?: props["background"]
            )
        if (backgroundColor != null) {
            val shape = shapeFromValue(props["backgroundShape"]) ?: shapeFromValue(props["shape"])
            val alpha = props.floatOrNull("backgroundAlpha") ?: props.floatOrNull("alpha")
            val resolvedColor = if (alpha != null) backgroundColor.copy(alpha = alpha) else backgroundColor
            modifier =
                if (shape != null) {
                    modifier.background(resolvedColor, shape = shape)
                } else {
                    modifier.background(resolvedColor)
                }
        }
    }

    val zIndex = props.floatOrNull("zIndex")
    if (zIndex != null) {
        modifier = modifier.zIndex(zIndex)
    }

    modifier = applyProxyModifierOps(modifier, props["modifier"])

    return modifier
}

private fun paddingSpecFromValue(raw: Any?): ComposeDslPaddingSpec? {
    return when (raw) {
        is Number -> {
            val all = raw.toFloat()
            ComposeDslPaddingSpec(start = all, top = all, end = all, bottom = all)
        }
        is Map<*, *> -> {
            val all = raw["all"].floatArg()
            if (all != null) {
                return ComposeDslPaddingSpec(start = all, top = all, end = all, bottom = all)
            }
            val horizontal = raw["horizontal"].floatArg()
            val vertical = raw["vertical"].floatArg()
            val start = raw["start"].floatArg()
            val top = raw["top"].floatArg()
            val end = raw["end"].floatArg()
            val bottom = raw["bottom"].floatArg()
            when {
                start != null || top != null || end != null || bottom != null ->
                    ComposeDslPaddingSpec(
                        start = start ?: 0f,
                        top = top ?: 0f,
                        end = end ?: 0f,
                        bottom = bottom ?: 0f
                    )
                horizontal != null || vertical != null ->
                    ComposeDslPaddingSpec(
                        start = horizontal ?: 0f,
                        top = vertical ?: 0f,
                        end = horizontal ?: 0f,
                        bottom = vertical ?: 0f
                    )
                else -> null
            }
        }
        else -> null
    }
}

@Composable
private fun parseBrush(value: Any?): Brush? {
    val map = value as? Map<*, *> ?: return null
    val type = map["type"]?.toString()?.trim()?.lowercase(Locale.ROOT)
        ?: return null
    require(type == "verticalgradient") { "unsupported brush type: $type" }
    val colorsRaw = map["colors"] as? List<*>
        ?: throw IllegalArgumentException("brush colors are required")
    require(colorsRaw.isNotEmpty()) { "brush colors are empty" }
    val colors = colorsRaw.mapIndexed { index, entry ->
        resolveColorValue(entry)
            ?: throw IllegalArgumentException("brush color not resolved at $index")
    }
    return Brush.verticalGradient(colors)
}

private data class ComposeDslModifierOp(
    val name: String,
    val args: List<Any?>
)

internal data class ComposeDslModifierWeightSpec(
    val weight: Float,
    val fill: Boolean
)

private data class ComposeDslModifierAxisBounds(
    val min: Float?,
    val max: Float?
)

private data class ComposeDslModifierSizeBounds(
    val minWidth: Float?,
    val minHeight: Float?,
    val maxWidth: Float?,
    val maxHeight: Float?
)

internal data class ComposeDslPaddingSpec(
    val start: Float,
    val top: Float,
    val end: Float,
    val bottom: Float
) {
    fun applyTo(modifier: Modifier): Modifier {
        return modifier.padding(
            start = start.dp,
            top = top.dp,
            end = end.dp,
            bottom = bottom.dp
        )
    }

    fun toPaddingValues(): PaddingValues {
        return PaddingValues(
            start = start.dp,
            top = top.dp,
            end = end.dp,
            bottom = bottom.dp
        )
    }
}

private data class ComposeDslModifierWrapWidthSpec(
    val align: Alignment.Horizontal,
    val unbounded: Boolean
)

private data class ComposeDslModifierWrapHeightSpec(
    val align: Alignment.Vertical,
    val unbounded: Boolean
)

private data class ComposeDslModifierWrapSizeSpec(
    val align: Alignment,
    val unbounded: Boolean
)

private data class ComposeDslModifierShadowSpec(
    val elevation: Float,
    val shape: androidx.compose.ui.graphics.Shape?,
    val clip: Boolean
)

private data class ComposeDslModifierPositionedSnapshot(
    val rootX: Float,
    val rootY: Float,
    val width: Float,
    val height: Float,
    val windowX: Float,
    val windowY: Float
)

private enum class ComposeDslShapeKind {
    Rounded,
    Cut,
    Circle,
    Pill
}

private data class ComposeDslShapeCorners(
    val topStart: Float?,
    val topEnd: Float?,
    val bottomEnd: Float?,
    val bottomStart: Float?
) {
    fun hasAnyValue(): Boolean {
        return topStart != null || topEnd != null || bottomEnd != null || bottomStart != null
    }
}

internal fun Map<String, Any?>.paddingValuesOrNull(key: String): PaddingValues? {
    return paddingSpecFromValue(this[key])?.toPaddingValues()
}

@Composable
private fun applyProxyModifierOps(
    base: Modifier,
    rawModifier: Any?
): Modifier {
    val ops = extractModifierOps(rawModifier)
    if (ops.isEmpty()) {
        return base
    }
    var modifier = base
    ops.forEach { op ->
        modifier = applySingleModifierOp(modifier, op)
    }
    return modifier
}

private fun extractModifierOps(rawModifier: Any?): List<ComposeDslModifierOp> {
    val container = rawModifier as? Map<*, *> ?: return emptyList()
    val list = container["__modifierOps"] as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val name = map["name"]?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            return@mapNotNull null
        }
        val args = (map["args"] as? List<*>)?.toList() ?: emptyList()
        ComposeDslModifierOp(name = name, args = args)
    }
}

private fun Map<String, Any?>.modifierOpByToken(token: String): ComposeDslModifierOp? {
    return extractModifierOps(this["modifier"])
        .lastOrNull { op -> normalizeToken(op.name) == token }
}

internal fun Map<String, Any?>.modifierWeightSpecOrNull(): ComposeDslModifierWeightSpec? {
    val explicitWeight = floatOrNull("weight")
    if (explicitWeight != null) {
        return ComposeDslModifierWeightSpec(
            weight = explicitWeight,
            fill = bool("weightFill", true)
        )
    }
    val op = modifierOpByToken("weight") ?: return null
    val weight = op.args.getOrNull(0).floatArg() ?: return null
    val fill =
        when {
            op.args.size > 1 -> op.args[1].boolArg() ?: true
            else -> true
        }
    return ComposeDslModifierWeightSpec(weight = weight, fill = fill)
}

internal fun Map<String, Any?>.hasModifierOp(token: String): Boolean {
    return modifierOpByToken(token) != null
}

internal fun Map<String, Any?>.scopeAlignToken(): String? {
    stringOrNull("align")?.let { return it }
    return modifierOpByToken("align")
        ?.args
        ?.firstOrNull()
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun Map<String, Any?>.commonPaddingSpecOrNull(): ComposeDslPaddingSpec? {
    paddingSpecFromValue(this["padding"])?.let { return it }

    val allPadding = floatOrNull("padding")
    if (allPadding != null) {
        return ComposeDslPaddingSpec(
            start = allPadding,
            top = allPadding,
            end = allPadding,
            bottom = allPadding
        )
    }

    val start = floatOrNull("paddingStart")
    val top = floatOrNull("paddingTop")
    val end = floatOrNull("paddingEnd")
    val bottom = floatOrNull("paddingBottom")
    if (start != null || top != null || end != null || bottom != null) {
        return ComposeDslPaddingSpec(
            start = start ?: 0f,
            top = top ?: 0f,
            end = end ?: 0f,
            bottom = bottom ?: 0f
        )
    }

    val horizontal = floatOrNull("paddingHorizontal")
    val vertical = floatOrNull("paddingVertical")
    if (horizontal != null || vertical != null) {
        return ComposeDslPaddingSpec(
            start = horizontal ?: 0f,
            top = vertical ?: 0f,
            end = horizontal ?: 0f,
            bottom = vertical ?: 0f
        )
    }

    return null
}

internal fun Map<String, Any?>.withoutCommonPaddingProps(): Map<String, Any?> {
    if (
        !containsKey("padding") &&
        !containsKey("paddingStart") &&
        !containsKey("paddingTop") &&
        !containsKey("paddingEnd") &&
        !containsKey("paddingBottom") &&
        !containsKey("paddingHorizontal") &&
        !containsKey("paddingVertical")
    ) {
        return this
    }
    return this - setOf(
        "padding",
        "paddingStart",
        "paddingTop",
        "paddingEnd",
        "paddingBottom",
        "paddingHorizontal",
        "paddingVertical"
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun applySingleModifierOp(
    modifier: Modifier,
    op: ComposeDslModifierOp
): Modifier {
    val onAction = LocalComposeDslActionHandler.current
    val nodeInfo = LocalComposeDslDebugNodeInfo.current
    val token = normalizeToken(op.name)
    return when (token) {
        "fillmaxsize" -> {
            val fraction = op.args.getOrNull(0).floatArg() ?: 1f
            modifier.fillMaxSize(fraction.coerceAtLeast(0f))
        }
        "fillmaxwidth" -> {
            val fraction = op.args.getOrNull(0).floatArg() ?: 1f
            modifier.fillMaxWidth(fraction.coerceAtLeast(0f))
        }
        "fillmaxheight" -> {
            val fraction = op.args.getOrNull(0).floatArg() ?: 1f
            modifier.fillMaxHeight(fraction.coerceAtLeast(0f))
        }
        "width" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.width(value.dp)
        }
        "height" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.height(value.dp)
        }
        "requiredwidth" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.requiredWidth(value.dp)
        }
        "requiredheight" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.requiredHeight(value.dp)
        }
        "size" -> {
            val width = op.args.getOrNull(0).floatArg() ?: return modifier
            val height = op.args.getOrNull(1).floatArg()
            if (height != null) {
                modifier.size(width.dp, height.dp)
            } else {
                modifier.size(width.dp)
            }
        }
        "requiredsize" -> {
            val width = op.args.getOrNull(0).floatArg() ?: return modifier
            val height = op.args.getOrNull(1).floatArg()
            if (height != null) {
                modifier.requiredSize(width.dp, height.dp)
            } else {
                modifier.requiredSize(width.dp)
            }
        }
        "padding" -> applyPaddingModifierOp(modifier, op.args)
        "offset" -> applyOffsetModifierOp(modifier, op.args)
        "widthin" -> applyWidthInModifierOp(modifier, op.args)
        "heightin" -> applyHeightInModifierOp(modifier, op.args)
        "sizein" -> applySizeInModifierOp(modifier, op.args)
        "requiredwidthin" -> applyRequiredWidthInModifierOp(modifier, op.args)
        "requiredheightin" -> applyRequiredHeightInModifierOp(modifier, op.args)
        "requiredsizein" -> applyRequiredSizeInModifierOp(modifier, op.args)
        "defaultminsize" -> applyDefaultMinSizeModifierOp(modifier, op.args)
        "wrapcontentwidth" -> applyWrapContentWidthModifierOp(modifier, op.args)
        "wrapcontentheight" -> applyWrapContentHeightModifierOp(modifier, op.args)
        "wrapcontentsize" -> applyWrapContentSizeModifierOp(modifier, op.args)
        "aspectratio" -> {
            val ratio = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.aspectRatio(ratio, true)
        }
        "alpha" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.alpha(value)
        }
        "rotate" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.rotate(value)
        }
        "scale" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.scale(value)
        }
        "zindex" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.zIndex(value)
        }
        "background" -> {
            val brush = parseBrush(op.args.getOrNull(0))
            val shape = shapeFromModifierArg(op.args.getOrNull(1))
            if (brush != null) {
                if (shape != null) {
                    modifier.background(brush = brush, shape = shape)
                } else {
                    modifier.background(brush = brush)
                }
            } else {
                val color = colorFromModifierArg(op.args.getOrNull(0)) ?: return modifier
                if (shape != null) {
                    modifier.background(color = color, shape = shape)
                } else {
                    modifier.background(color = color)
                }
            }
        }
        "border" -> {
            val width = op.args.getOrNull(0).floatArg() ?: 1f
            val brush = parseBrush(op.args.getOrNull(1))
            val shape = shapeFromModifierArg(op.args.getOrNull(2))
            if (brush != null) {
                if (shape != null) {
                    modifier.border(width = width.dp, brush = brush, shape = shape)
                } else {
                    modifier.border(
                        width = width.dp,
                        brush = brush,
                        shape = RoundedCornerShape(0.dp)
                    )
                }
            } else {
                val color = colorFromModifierArg(op.args.getOrNull(1)) ?: return modifier
                if (shape != null) {
                    modifier.border(width = width.dp, color = color, shape = shape)
                } else {
                    modifier.border(width = width.dp, color = color)
                }
            }
        }
        "clip" -> {
            val shape = shapeFromModifierArg(op.args.getOrNull(0)) ?: return modifier
            modifier.clip(shape)
        }
        "cliptobounds" -> modifier.clipToBounds()
        "shadow" -> {
            val spec = shadowSpecFromModifierArgs(op.args) ?: return modifier
            modifier.shadow(
                elevation = spec.elevation.dp,
                shape = spec.shape ?: RoundedCornerShape(0.dp),
                clip = spec.clip
            )
        }
        "clickable" -> {
            val actionId = ToolPkgComposeDslParser.extractActionId(op.args.getOrNull(0))
            if (actionId.isNullOrBlank()) {
                modifier
            } else {
                modifier.clickable {
                    dispatchComposeDslModifierAction(
                        source = "clickable",
                        actionId = actionId,
                        payload = null,
                        onAction = onAction,
                        nodeInfo = nodeInfo
                    )
                }
            }
        }
        "combinedclickable" -> applyCombinedClickableModifierOp(modifier, op.args, onAction, nodeInfo)
        "tapgestures" -> applyTapGesturesModifierOp(modifier, op.args, onAction, nodeInfo)
        "draggestures" -> applyDragGesturesModifierOp(modifier, op.args, onAction, nodeInfo)
        "transformgestures" -> applyTransformGesturesModifierOp(modifier, op.args, onAction, nodeInfo)
        "onsizechanged" -> applyOnSizeChangedModifierOp(modifier, op.args, onAction, nodeInfo)
        "ongloballypositioned" ->
            applyOnGloballyPositionedModifierOp(modifier, op.args, onAction, nodeInfo)
        "imepadding" -> modifier.imePadding()
        "statusbarspadding" -> modifier.statusBarsPadding()
        "navigationbarspadding" -> modifier.navigationBarsPadding()
        "systembarspadding" -> modifier.systemBarsPadding()
        "safedrawingpadding" -> modifier.safeDrawingPadding()
        else -> modifier
    }
}

private fun logComposeDslModifierAction(
    source: String,
    actionId: String,
    nodeInfo: ComposeDslDebugNodeInfo?
) {
    AppLogger.d(
        TAG,
        "compose_dsl $source triggered: routeInstanceId=${nodeInfo?.routeInstanceId.orEmpty()}, nodePath=${nodeInfo?.nodePath.orEmpty()}, nodeType=${nodeInfo?.nodeType.orEmpty()}, nodeKey=${nodeInfo?.nodeKey.orEmpty()}, actionId=$actionId"
    )
}

private fun dispatchComposeDslModifierAction(
    source: String,
    actionId: String,
    payload: Any?,
    onAction: (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
) {
    logComposeDslModifierAction(source = source, actionId = actionId, nodeInfo = nodeInfo)
    onAction(actionId, payload)
}

private suspend fun dispatchComposeDslModifierActionAwait(
    source: String,
    actionId: String,
    payload: Any?,
    onAction: suspend (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
) {
    logComposeDslModifierAction(source = source, actionId = actionId, nodeInfo = nodeInfo)
    onAction(actionId, payload)
}

private fun modifierOptionsArg(args: List<Any?>): Map<*, *>? {
    return args.firstOrNull() as? Map<*, *>
}

private fun modifierOptionActionId(options: Map<*, *>?, key: String): String? {
    return ToolPkgComposeDslParser.extractActionId(options?.get(key))
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun applyCombinedClickableModifierOp(
    modifier: Modifier,
    args: List<Any?>,
    onAction: (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
): Modifier {
    val options = modifierOptionsArg(args) ?: return modifier
    val onClickActionId = modifierOptionActionId(options, "onClick") ?: return modifier
    val onLongClickActionId = modifierOptionActionId(options, "onLongClick")
    val onDoubleClickActionId = modifierOptionActionId(options, "onDoubleClick")
    return modifier.combinedClickable(
        onClick = {
            dispatchComposeDslModifierAction(
                source = "combinedClickable.onClick",
                actionId = onClickActionId,
                payload = null,
                onAction = onAction,
                nodeInfo = nodeInfo
            )
        },
        onLongClick =
            onLongClickActionId?.let { actionId ->
                {
                    dispatchComposeDslModifierAction(
                        source = "combinedClickable.onLongClick",
                        actionId = actionId,
                        payload = null,
                        onAction = onAction,
                        nodeInfo = nodeInfo
                    )
                }
            },
        onDoubleClick =
            onDoubleClickActionId?.let { actionId ->
                {
                    dispatchComposeDslModifierAction(
                        source = "combinedClickable.onDoubleClick",
                        actionId = actionId,
                        payload = null,
                        onAction = onAction,
                        nodeInfo = nodeInfo
                    )
                }
            }
    )
}

@Composable
private fun applyTapGesturesModifierOp(
    modifier: Modifier,
    args: List<Any?>,
    onAction: (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
): Modifier {
    val onActionAwait = LocalComposeDslSuspendingActionHandler.current
    val options = modifierOptionsArg(args) ?: return modifier
    val onPressActionId = modifierOptionActionId(options, "onPress")
    val onTapActionId = modifierOptionActionId(options, "onTap")
    val onDoubleTapActionId = modifierOptionActionId(options, "onDoubleTap")
    val onLongPressActionId = modifierOptionActionId(options, "onLongPress")
    if (
        onPressActionId == null &&
        onTapActionId == null &&
        onDoubleTapActionId == null &&
        onLongPressActionId == null
    ) {
        return modifier
    }
    return modifier.pointerInput(
        onPressActionId,
        onTapActionId,
        onDoubleTapActionId,
        onLongPressActionId
    ) {
        val onPressHandler: suspend PressGestureScope.(Offset) -> Unit =
            if (onPressActionId != null) {
                val pressActionId = onPressActionId
                { offset: Offset ->
                    dispatchComposeDslModifierActionAwait(
                        source = "tapGestures.onPress",
                        actionId = pressActionId,
                        payload = mapOf("x" to offset.x, "y" to offset.y),
                        onAction = onActionAwait,
                        nodeInfo = nodeInfo
                    )
                }
            } else {
                { _: Offset -> }
            }
        detectTapGestures(
            onPress = onPressHandler,
            onTap =
                onTapActionId?.let { actionId ->
                    { offset ->
                        dispatchComposeDslModifierAction(
                            source = "tapGestures.onTap",
                            actionId = actionId,
                            payload = mapOf("x" to offset.x, "y" to offset.y),
                            onAction = onAction,
                            nodeInfo = nodeInfo
                        )
                    }
                },
            onDoubleTap =
                onDoubleTapActionId?.let { actionId ->
                    { offset ->
                        dispatchComposeDslModifierAction(
                            source = "tapGestures.onDoubleTap",
                            actionId = actionId,
                            payload = mapOf("x" to offset.x, "y" to offset.y),
                            onAction = onAction,
                            nodeInfo = nodeInfo
                        )
                    }
                },
            onLongPress =
                onLongPressActionId?.let { actionId ->
                    { offset ->
                        dispatchComposeDslModifierAction(
                            source = "tapGestures.onLongPress",
                            actionId = actionId,
                            payload = mapOf("x" to offset.x, "y" to offset.y),
                            onAction = onAction,
                            nodeInfo = nodeInfo
                        )
                    }
                }
        )
    }
}

@Composable
private fun applyDragGesturesModifierOp(
    modifier: Modifier,
    args: List<Any?>,
    onAction: (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
): Modifier {
    val options = modifierOptionsArg(args) ?: return modifier
    val onDragStartActionId = modifierOptionActionId(options, "onDragStart")
    val onDragActionId = modifierOptionActionId(options, "onDrag")
    val onDragEndActionId = modifierOptionActionId(options, "onDragEnd")
    val onDragCancelActionId = modifierOptionActionId(options, "onDragCancel")
    if (
        onDragStartActionId == null &&
        onDragActionId == null &&
        onDragEndActionId == null &&
        onDragCancelActionId == null
    ) {
        return modifier
    }
    return modifier.pointerInput(
        onDragStartActionId,
        onDragActionId,
        onDragEndActionId,
        onDragCancelActionId
    ) {
        detectDragGestures(
            onDragStart = { offset ->
                val actionId = onDragStartActionId
                if (actionId != null) {
                    dispatchComposeDslModifierAction(
                        source = "dragGestures.onDragStart",
                        actionId = actionId,
                        payload = mapOf("x" to offset.x, "y" to offset.y),
                        onAction = onAction,
                        nodeInfo = nodeInfo
                    )
                }
            },
            onDragEnd = {
                val actionId = onDragEndActionId
                if (actionId != null) {
                    dispatchComposeDslModifierAction(
                        source = "dragGestures.onDragEnd",
                        actionId = actionId,
                        payload = null,
                        onAction = onAction,
                        nodeInfo = nodeInfo
                    )
                }
            },
            onDragCancel = {
                val actionId = onDragCancelActionId
                if (actionId != null) {
                    dispatchComposeDslModifierAction(
                        source = "dragGestures.onDragCancel",
                        actionId = actionId,
                        payload = null,
                        onAction = onAction,
                        nodeInfo = nodeInfo
                    )
                }
            },
            onDrag = { change, dragAmount ->
                change.consume()
                val actionId = onDragActionId
                if (actionId != null) {
                    dispatchComposeDslModifierAction(
                        source = "dragGestures.onDrag",
                        actionId = actionId,
                        payload =
                            mapOf(
                                "x" to change.position.x,
                                "y" to change.position.y,
                                "deltaX" to dragAmount.x,
                                "deltaY" to dragAmount.y
                            ),
                        onAction = onAction,
                        nodeInfo = nodeInfo
                    )
                }
            }
        )
    }
}

@Composable
private fun applyTransformGesturesModifierOp(
    modifier: Modifier,
    args: List<Any?>,
    onAction: (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
): Modifier {
    val options = modifierOptionsArg(args) ?: return modifier
    val onGestureActionId = modifierOptionActionId(options, "onGesture") ?: return modifier
    val panZoomLock = options["panZoomLock"].boolArg() ?: false
    return modifier.pointerInput(onGestureActionId, panZoomLock) {
        detectTransformGestures(panZoomLock = panZoomLock) { centroid, pan, zoom, rotation ->
            dispatchComposeDslModifierAction(
                source = "transformGestures.onGesture",
                actionId = onGestureActionId,
                payload =
                    mapOf(
                        "centroidX" to centroid.x,
                        "centroidY" to centroid.y,
                        "panX" to pan.x,
                        "panY" to pan.y,
                        "zoom" to zoom,
                        "rotation" to rotation
                    ),
                onAction = onAction,
                nodeInfo = nodeInfo
            )
        }
    }
}

@Composable
private fun applyOnSizeChangedModifierOp(
    modifier: Modifier,
    args: List<Any?>,
    onAction: (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
): Modifier {
    val actionId = ToolPkgComposeDslParser.extractActionId(args.firstOrNull()) ?: return modifier
    var lastSize by remember { mutableStateOf<IntSize?>(null) }
    return modifier.onSizeChanged { size ->
        if (lastSize == size) {
            return@onSizeChanged
        }
        lastSize = size
        dispatchComposeDslModifierAction(
            source = "onSizeChanged",
            actionId = actionId,
            payload = mapOf("width" to size.width, "height" to size.height),
            onAction = onAction,
            nodeInfo = nodeInfo
        )
    }
}

@Composable
private fun applyOnGloballyPositionedModifierOp(
    modifier: Modifier,
    args: List<Any?>,
    onAction: (String, Any?) -> Unit,
    nodeInfo: ComposeDslDebugNodeInfo?
): Modifier {
    val actionId = ToolPkgComposeDslParser.extractActionId(args.firstOrNull()) ?: return modifier
    var lastSnapshot by remember { mutableStateOf<ComposeDslModifierPositionedSnapshot?>(null) }
    return modifier.onGloballyPositioned { coordinates ->
        val rootBounds = coordinates.boundsInRoot()
        val windowPosition = coordinates.positionInWindow()
        val snapshot =
            ComposeDslModifierPositionedSnapshot(
                rootX = rootBounds.left,
                rootY = rootBounds.top,
                width = rootBounds.width,
                height = rootBounds.height,
                windowX = windowPosition.x,
                windowY = windowPosition.y
            )
        if (lastSnapshot == snapshot) {
            return@onGloballyPositioned
        }
        lastSnapshot = snapshot
        dispatchComposeDslModifierAction(
            source = "onGloballyPositioned",
            actionId = actionId,
            payload =
                mapOf(
                    "rootX" to snapshot.rootX,
                    "rootY" to snapshot.rootY,
                    "width" to snapshot.width,
                    "height" to snapshot.height,
                    "windowX" to snapshot.windowX,
                    "windowY" to snapshot.windowY
                ),
            onAction = onAction,
            nodeInfo = nodeInfo
        )
    }
}

private fun applyPaddingModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    if (args.isEmpty()) {
        return modifier
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        val all = first["all"].floatArg()
        if (all != null) {
            return modifier.padding(all.dp)
        }
        val horizontal = first["horizontal"].floatArg() ?: 0f
        val vertical = first["vertical"].floatArg() ?: 0f
        val start = first["start"].floatArg()
        val top = first["top"].floatArg()
        val end = first["end"].floatArg()
        val bottom = first["bottom"].floatArg()
        return if (start != null || top != null || end != null || bottom != null) {
            modifier.padding(
                start = (start ?: 0f).dp,
                top = (top ?: 0f).dp,
                end = (end ?: 0f).dp,
                bottom = (bottom ?: 0f).dp
            )
        } else {
            modifier.padding(horizontal = horizontal.dp, vertical = vertical.dp)
        }
    }

    val firstNumber = first.floatArg()
    val secondNumber = args.getOrNull(1).floatArg()
    val thirdNumber = args.getOrNull(2).floatArg()
    val fourthNumber = args.getOrNull(3).floatArg()

    return when {
        firstNumber != null && secondNumber == null -> modifier.padding(firstNumber.dp)
        firstNumber != null && secondNumber != null && thirdNumber == null ->
            modifier.padding(horizontal = firstNumber.dp, vertical = secondNumber.dp)
        firstNumber != null && secondNumber != null && thirdNumber != null && fourthNumber != null ->
            modifier.padding(
                start = firstNumber.dp,
                top = secondNumber.dp,
                end = thirdNumber.dp,
                bottom = fourthNumber.dp
            )
        else -> modifier
    }
}

private fun applyOffsetModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    if (args.isEmpty()) {
        return modifier
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        val x = first["x"].floatArg() ?: 0f
        val y = first["y"].floatArg() ?: 0f
        return modifier.offset(x.dp, y.dp)
    }
    val x = first.floatArg() ?: 0f
    val y = args.getOrNull(1).floatArg() ?: 0f
    return modifier.offset(x.dp, y.dp)
}

private fun applyWidthInModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val bounds = axisBoundsFromModifierArgs(args, "min", "max", "minWidth", "maxWidth")
    return modifier.widthIn(
        min = bounds.min?.dp ?: Dp.Unspecified,
        max = bounds.max?.dp ?: Dp.Unspecified
    )
}

private fun applyHeightInModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val bounds = axisBoundsFromModifierArgs(args, "min", "max", "minHeight", "maxHeight")
    return modifier.heightIn(
        min = bounds.min?.dp ?: Dp.Unspecified,
        max = bounds.max?.dp ?: Dp.Unspecified
    )
}

private fun applySizeInModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val bounds = sizeBoundsFromModifierArgs(args)
    return modifier.sizeIn(
        minWidth = bounds.minWidth?.dp ?: Dp.Unspecified,
        minHeight = bounds.minHeight?.dp ?: Dp.Unspecified,
        maxWidth = bounds.maxWidth?.dp ?: Dp.Unspecified,
        maxHeight = bounds.maxHeight?.dp ?: Dp.Unspecified
    )
}

private fun applyRequiredWidthInModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val bounds = axisBoundsFromModifierArgs(args, "min", "max", "minWidth", "maxWidth")
    return modifier.requiredWidthIn(
        min = bounds.min?.dp ?: Dp.Unspecified,
        max = bounds.max?.dp ?: Dp.Unspecified
    )
}

private fun applyRequiredHeightInModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val bounds = axisBoundsFromModifierArgs(args, "min", "max", "minHeight", "maxHeight")
    return modifier.requiredHeightIn(
        min = bounds.min?.dp ?: Dp.Unspecified,
        max = bounds.max?.dp ?: Dp.Unspecified
    )
}

private fun applyRequiredSizeInModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val bounds = sizeBoundsFromModifierArgs(args)
    return modifier.requiredSizeIn(
        minWidth = bounds.minWidth?.dp ?: Dp.Unspecified,
        minHeight = bounds.minHeight?.dp ?: Dp.Unspecified,
        maxWidth = bounds.maxWidth?.dp ?: Dp.Unspecified,
        maxHeight = bounds.maxHeight?.dp ?: Dp.Unspecified
    )
}

private fun applyDefaultMinSizeModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val bounds = defaultMinSizeFromModifierArgs(args)
    return modifier.defaultMinSize(
        minWidth = bounds.minWidth?.dp ?: Dp.Unspecified,
        minHeight = bounds.minHeight?.dp ?: Dp.Unspecified
    )
}

private fun applyWrapContentWidthModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val spec = wrapWidthSpecFromModifierArgs(args)
    return modifier.wrapContentWidth(
        align = spec.align,
        unbounded = spec.unbounded
    )
}

private fun applyWrapContentHeightModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val spec = wrapHeightSpecFromModifierArgs(args)
    return modifier.wrapContentHeight(
        align = spec.align,
        unbounded = spec.unbounded
    )
}

private fun applyWrapContentSizeModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    val spec = wrapSizeSpecFromModifierArgs(args)
    return modifier.wrapContentSize(
        align = spec.align,
        unbounded = spec.unbounded
    )
}

@Composable
private fun colorFromModifierArg(value: Any?): Color? {
    return resolveColorValue(value)
}

private fun shapeFromValue(value: Any?): androidx.compose.ui.graphics.Shape? {
    val shapeValue = value as? Map<*, *> ?: return null
    val shapeKind = shapeKindFromValue(shapeValue)
    return when (shapeKind) {
        ComposeDslShapeKind.Circle -> CircleShape
        ComposeDslShapeKind.Pill -> RoundedCornerShape(percent = 50)
        ComposeDslShapeKind.Cut -> {
            val corners = shapeCornersFromValue(shapeValue)
            if (!corners.hasAnyValue()) {
                null
            } else {
                CutCornerShape(
                    topStart = (corners.topStart ?: 0f).dp,
                    topEnd = (corners.topEnd ?: 0f).dp,
                    bottomEnd = (corners.bottomEnd ?: 0f).dp,
                    bottomStart = (corners.bottomStart ?: 0f).dp
                )
            }
        }
        ComposeDslShapeKind.Rounded -> {
            val corners = shapeCornersFromValue(shapeValue)
            if (!corners.hasAnyValue()) {
                null
            } else {
                RoundedCornerShape(
                    topStart = (corners.topStart ?: 0f).dp,
                    topEnd = (corners.topEnd ?: 0f).dp,
                    bottomEnd = (corners.bottomEnd ?: 0f).dp,
                    bottomStart = (corners.bottomStart ?: 0f).dp
                )
            }
        }
    }
}

private fun shapeFromModifierArg(value: Any?): androidx.compose.ui.graphics.Shape? {
    return shapeFromValue(value)
}

private fun shapeKindFromValue(value: Map<*, *>): ComposeDslShapeKind {
    return when (
        normalizeToken(
            value["type"]?.toString()
                ?: value["kind"]?.toString()
                ?: ""
        )
    ) {
        "cut", "cutcorner", "cutcorners" -> ComposeDslShapeKind.Cut
        "circle" -> ComposeDslShapeKind.Circle
        "pill", "capsule", "stadium" -> ComposeDslShapeKind.Pill
        else -> ComposeDslShapeKind.Rounded
    }
}

private fun shapeCornersFromValue(value: Map<*, *>): ComposeDslShapeCorners {
    val uniformRadius = value["cornerRadius"].floatArg() ?: value["radius"].floatArg()
    return ComposeDslShapeCorners(
        topStart = shapeCornerValue(value, "topStart", "topLeft", uniformRadius),
        topEnd = shapeCornerValue(value, "topEnd", "topRight", uniformRadius),
        bottomEnd = shapeCornerValue(value, "bottomEnd", "bottomRight", uniformRadius),
        bottomStart = shapeCornerValue(value, "bottomStart", "bottomLeft", uniformRadius)
    )
}

private fun shapeCornerValue(
    value: Map<*, *>,
    logicalKey: String,
    physicalKey: String,
    fallback: Float?
): Float? {
    return value[logicalKey].floatArg() ?: value[physicalKey].floatArg() ?: fallback
}

private fun Any?.floatArg(): Float? {
    return canvasNumberFromValue(this)
}

private fun Any?.boolArg(): Boolean? {
    return when (this) {
        is Boolean -> this
        is Number -> this.toInt() != 0
        null -> null
        else -> {
            when (this.toString().trim().lowercase(Locale.ROOT)) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
        }
    }
}

private fun axisBoundsFromModifierArgs(
    args: List<Any?>,
    minKey: String,
    maxKey: String,
    minFallbackKey: String,
    maxFallbackKey: String
): ComposeDslModifierAxisBounds {
    if (args.isEmpty()) {
        return ComposeDslModifierAxisBounds(min = null, max = null)
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        return ComposeDslModifierAxisBounds(
            min = first[minKey].floatArg() ?: first[minFallbackKey].floatArg(),
            max = first[maxKey].floatArg() ?: first[maxFallbackKey].floatArg()
        )
    }
    return ComposeDslModifierAxisBounds(
        min = first.floatArg(),
        max = args.getOrNull(1).floatArg()
    )
}

private fun sizeBoundsFromModifierArgs(args: List<Any?>): ComposeDslModifierSizeBounds {
    if (args.isEmpty()) {
        return ComposeDslModifierSizeBounds(
            minWidth = null,
            minHeight = null,
            maxWidth = null,
            maxHeight = null
        )
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        return ComposeDslModifierSizeBounds(
            minWidth = first["minWidth"].floatArg() ?: first["min"].floatArg(),
            minHeight = first["minHeight"].floatArg() ?: first["min"].floatArg(),
            maxWidth = first["maxWidth"].floatArg() ?: first["max"].floatArg(),
            maxHeight = first["maxHeight"].floatArg() ?: first["max"].floatArg()
        )
    }
    return ComposeDslModifierSizeBounds(
        minWidth = first.floatArg(),
        minHeight = args.getOrNull(1).floatArg(),
        maxWidth = args.getOrNull(2).floatArg(),
        maxHeight = args.getOrNull(3).floatArg()
    )
}

private fun defaultMinSizeFromModifierArgs(args: List<Any?>): ComposeDslModifierSizeBounds {
    if (args.isEmpty()) {
        return ComposeDslModifierSizeBounds(
            minWidth = null,
            minHeight = null,
            maxWidth = null,
            maxHeight = null
        )
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        val all = first["all"].floatArg()
        val minWidth = first["minWidth"].floatArg() ?: all
        val minHeight = first["minHeight"].floatArg() ?: all
        return ComposeDslModifierSizeBounds(
            minWidth = minWidth,
            minHeight = minHeight,
            maxWidth = null,
            maxHeight = null
        )
    }
    val width = first.floatArg()
    val height = args.getOrNull(1).floatArg()
    return ComposeDslModifierSizeBounds(
        minWidth = width,
        minHeight = height ?: width,
        maxWidth = null,
        maxHeight = null
    )
}

private fun wrapWidthSpecFromModifierArgs(args: List<Any?>): ComposeDslModifierWrapWidthSpec {
    if (args.isEmpty()) {
        return ComposeDslModifierWrapWidthSpec(
            align = Alignment.CenterHorizontally,
            unbounded = false
        )
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        return ComposeDslModifierWrapWidthSpec(
            align =
                first["align"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let(::horizontalAlignmentFromToken)
                    ?: Alignment.CenterHorizontally,
            unbounded = first["unbounded"].boolArg() ?: false
        )
    }
    return ComposeDslModifierWrapWidthSpec(
        align =
            first?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::horizontalAlignmentFromToken)
                ?: Alignment.CenterHorizontally,
        unbounded = args.getOrNull(1).boolArg() ?: false
    )
}

private fun wrapHeightSpecFromModifierArgs(args: List<Any?>): ComposeDslModifierWrapHeightSpec {
    if (args.isEmpty()) {
        return ComposeDslModifierWrapHeightSpec(
            align = Alignment.CenterVertically,
            unbounded = false
        )
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        return ComposeDslModifierWrapHeightSpec(
            align =
                first["align"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let(::verticalAlignmentFromToken)
                    ?: Alignment.CenterVertically,
            unbounded = first["unbounded"].boolArg() ?: false
        )
    }
    return ComposeDslModifierWrapHeightSpec(
        align =
            first?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::verticalAlignmentFromToken)
                ?: Alignment.CenterVertically,
        unbounded = args.getOrNull(1).boolArg() ?: false
    )
}

private fun wrapSizeSpecFromModifierArgs(args: List<Any?>): ComposeDslModifierWrapSizeSpec {
    if (args.isEmpty()) {
        return ComposeDslModifierWrapSizeSpec(
            align = Alignment.Center,
            unbounded = false
        )
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        return ComposeDslModifierWrapSizeSpec(
            align =
                first["align"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let(::boxAlignmentFromToken)
                    ?: Alignment.Center,
            unbounded = first["unbounded"].boolArg() ?: false
        )
    }
    return ComposeDslModifierWrapSizeSpec(
        align =
            first?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::boxAlignmentFromToken)
                ?: Alignment.Center,
        unbounded = args.getOrNull(1).boolArg() ?: false
    )
}

@Composable
private fun shadowSpecFromModifierArgs(args: List<Any?>): ComposeDslModifierShadowSpec? {
    if (args.isEmpty()) {
        return null
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        val elevation = first["elevation"].floatArg() ?: return null
        val shape = shapeFromModifierArg(first["shape"])
        val clip = first["clip"].boolArg() ?: (elevation > 0f)
        return ComposeDslModifierShadowSpec(
            elevation = elevation,
            shape = shape,
            clip = clip
        )
    }
    val elevation = first.floatArg() ?: return null
    val shape = shapeFromModifierArg(args.getOrNull(1))
    val clip = args.getOrNull(2).boolArg() ?: (elevation > 0f)
    return ComposeDslModifierShadowSpec(
        elevation = elevation,
        shape = shape,
        clip = clip
    )
}

internal fun Map<String, Any?>.string(key: String, defaultValue: String = ""): String {
    return this[key]?.toString().orEmpty().ifBlank { defaultValue }
}

internal fun Map<String, Any?>.stringOrNull(key: String): String? {
    val value = this[key]?.toString()?.trim().orEmpty()
    return if (value.isBlank()) null else value
}

internal fun Map<String, Any?>.bool(key: String, defaultValue: Boolean): Boolean {
    val value = this[key] ?: return defaultValue
    return when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> value.toString().equals("true", ignoreCase = true)
    }
}

internal fun Map<String, Any?>.int(key: String, defaultValue: Int): Int {
    val value = this[key] ?: return defaultValue
    return when (value) {
        is Number -> value.toInt()
        else -> value.toString().toIntOrNull() ?: defaultValue
    }
}

internal fun Map<String, Any?>.floatOrNull(key: String): Float? {
    val value = this[key] ?: return null
    return canvasNumberFromValue(value)
}

internal fun Map<String, Any?>.dp(key: String, defaultValue: Dp = 0.dp): Dp {
    return (floatOrNull(key) ?: defaultValue.value).dp
}

internal fun popupPropertiesFromValue(value: Any?): PopupProperties {
    val map = value as? Map<*, *> ?: return PopupProperties()
    return PopupProperties(
        focusable = (map["focusable"] as? Boolean) ?: false,
        dismissOnBackPress = (map["dismissOnBackPress"] as? Boolean) ?: true,
        dismissOnClickOutside = (map["dismissOnClickOutside"] as? Boolean) ?: true,
        clippingEnabled = (map["clippingEnabled"] as? Boolean) ?: true,
        usePlatformDefaultWidth = (map["usePlatformDefaultWidth"] as? Boolean) ?: false
    )
}

@Composable
internal fun Map<String, Any?>.textStyle(key: String): androidx.compose.ui.text.TextStyle {
    val typography = MaterialTheme.typography
    val token = normalizeToken(string(key))
    val getter = typographyGetterByToken[token]
    return (getter?.invoke(typography) as? androidx.compose.ui.text.TextStyle) ?: typography.bodyMedium
}

internal fun Map<String, Any?>.horizontalAlignment(key: String): Alignment.Horizontal {
    return horizontalAlignmentFromToken(stringOrNull(key))
}

internal fun horizontalAlignmentFromToken(raw: String?): Alignment.Horizontal {
    val token = normalizeToken(raw.orEmpty())
    return when (token) {
        "center", "centerhorizontally" -> Alignment.CenterHorizontally
        "end", "right" -> Alignment.End
        else -> Alignment.Start
    }
}

internal fun Map<String, Any?>.verticalAlignment(key: String): Alignment.Vertical {
    return verticalAlignmentFromToken(stringOrNull(key))
}

internal fun verticalAlignmentFromToken(raw: String?): Alignment.Vertical {
    val token = normalizeToken(raw.orEmpty())
    return when (token) {
        "center", "centervertically" -> Alignment.CenterVertically
        "end", "bottom" -> Alignment.Bottom
        else -> Alignment.Top
    }
}

internal fun Map<String, Any?>.textOverflow(key: String): TextOverflow {
    return when (normalizeToken(string(key))) {
        "ellipsis" -> TextOverflow.Ellipsis
        else -> TextOverflow.Clip
    }
}

internal fun Map<String, Any?>.boxAlignment(key: String): Alignment {
    return boxAlignmentFromToken(stringOrNull(key))
}

internal fun boxAlignmentFromToken(raw: String?): Alignment {
    val token = normalizeToken(raw.orEmpty())
    return when (token) {
        "center" -> Alignment.Center
        "topcenter", "centertop" -> Alignment.TopCenter
        "topend", "endtop", "topright", "righttop" -> Alignment.TopEnd
        "centerstart", "startcenter", "centerleft", "leftcenter" -> Alignment.CenterStart
        "centerend", "endcenter", "centerright", "rightcenter" -> Alignment.CenterEnd
        "bottomstart", "startbottom", "bottomleft", "leftbottom" -> Alignment.BottomStart
        "bottomcenter", "centerbottom" -> Alignment.BottomCenter
        "bottomend", "endbottom", "bottomright", "rightbottom", "end" -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }
}

internal fun Map<String, Any?>.contentScale(key: String): ContentScale {
    return when (normalizeToken(string(key))) {
        "crop" -> ContentScale.Crop
        "fillbounds", "fill" -> ContentScale.FillBounds
        "fillwidth" -> ContentScale.FillWidth
        "fillheight" -> ContentScale.FillHeight
        "inside" -> ContentScale.Inside
        "none" -> ContentScale.None
        else -> ContentScale.Fit
    }
}

internal fun Map<String, Any?>.imageModelOrNull(): Any? {
    val rawValue =
        stringOrNull("url")
            ?: stringOrNull("uri")
            ?: stringOrNull("path")
            ?: stringOrNull("fileUri")
            ?: stringOrNull("src")
            ?: return null
    val normalized = rawValue.trim()
    if (normalized.isEmpty()) {
        return null
    }
    return when {
        normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true) -> normalized
        normalized.startsWith("content://", ignoreCase = true) ||
            normalized.startsWith("file://", ignoreCase = true) ||
            normalized.startsWith("android.resource://", ignoreCase = true) -> Uri.parse(normalized)
        normalized.startsWith("/") -> File(normalized)
        Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(normalized) -> File(normalized)
        else -> normalized
    }
}

internal fun Map<String, Any?>.webViewMixedContentMode(key: String): Int {
    return when (normalizeToken(string(key))) {
        "neverallow" -> WebSettings.MIXED_CONTENT_NEVER_ALLOW
        "compatibilitymode" -> WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        else -> WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
}

internal fun Map<String, Any?>.webViewCacheMode(key: String): Int {
    return when (normalizeToken(string(key))) {
        "nocache" -> WebSettings.LOAD_NO_CACHE
        "cacheelsenetwork" -> WebSettings.LOAD_CACHE_ELSE_NETWORK
        "cacheonly" -> WebSettings.LOAD_CACHE_ONLY
        else -> WebSettings.LOAD_DEFAULT
    }
}

internal fun Map<String, Any?>.horizontalArrangement(key: String, spacing: Dp): Arrangement.Horizontal {
    val token = normalizeToken(string(key))
    return when (token) {
        "start" -> Arrangement.Start
        "center" -> Arrangement.Center
        "end" -> Arrangement.End
        "spacebetween" -> Arrangement.SpaceBetween
        "spacearound" -> Arrangement.SpaceAround
        "spaceevenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.spacedBy(spacing)
    }
}

internal fun Map<String, Any?>.verticalArrangement(key: String, spacing: Dp): Arrangement.Vertical {
    val token = normalizeToken(string(key))
    return when (token) {
        "top", "start" -> Arrangement.Top
        "center" -> Arrangement.Center
        "bottom", "end" -> Arrangement.Bottom
        "spacebetween" -> Arrangement.SpaceBetween
        "spacearound" -> Arrangement.SpaceAround
        "spaceevenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.spacedBy(spacing)
    }
}

internal fun Map<String, Any?>.fontWeightOrNull(key: String): FontWeight? {
    val token = normalizeToken(string(key))
    val getter =
        fontWeightGetterByToken[token]
            ?: fontWeightGetterByToken[token.replace("ultra", "extra")]
            ?: fontWeightGetterByToken[token.replace("demi", "semi")]
            ?: fontWeightGetterByToken[if (token == "regular") "normal" else token]
            ?: fontWeightGetterByToken[if (token == "heavy") "black" else token]
    return getter?.invoke(FontWeight.Companion) as? FontWeight
}

@Composable
internal fun resolveComposeDslFontFamily(raw: String?): androidx.compose.ui.text.font.FontFamily? {
    val normalized = raw?.trim().orEmpty()
    if (normalized.isBlank()) {
        return null
    }
    return when (normalizeToken(normalized)) {
        "default" -> androidx.compose.ui.text.font.FontFamily.Default
        "serif" -> getSystemFontFamily("serif")
        "sansserif", "sans", "sans-serif" -> getSystemFontFamily("sans-serif")
        "monospace", "mono" -> getSystemFontFamily("monospace")
        "cursive" -> getSystemFontFamily("cursive")
        else -> loadCustomFontFamily(LocalContext.current, normalized)
    }
}

@Composable
internal fun Map<String, Any?>.fontFamilyOrNull(key: String): androidx.compose.ui.text.font.FontFamily? {
    return resolveComposeDslFontFamily(stringOrNull(key))
}

@Composable
internal fun Map<String, Any?>.resolvedTextStyle(
    key: String,
    includeColor: Boolean = false
): androidx.compose.ui.text.TextStyle {
    var nextStyle = textStyle(key)
    fontWeightOrNull("fontWeight")?.let { fontWeight ->
        nextStyle = nextStyle.copy(fontWeight = fontWeight)
    }
    floatOrNull("fontSize")?.let { fontSize ->
        nextStyle = nextStyle.copy(fontSize = fontSize.sp)
    }
    fontFamilyOrNull("fontFamily")?.let { fontFamily ->
        nextStyle = nextStyle.copy(fontFamily = fontFamily)
    }
    if (includeColor) {
        colorOrNull("color")?.let { color ->
            nextStyle = nextStyle.copy(color = color)
        }
    }
    return nextStyle
}

@Composable
internal fun composeDslTextFieldStyleFromValue(value: Any?): androidx.compose.ui.text.TextStyle? {
    val map = value as? Map<*, *> ?: return null
    val fontSize = (map["fontSize"] as? Number)?.toFloat() ?: 14f
    val fontWeight =
        map["fontWeight"]?.toString()?.let { token ->
            mapOf<String, Any?>("fontWeight" to token).fontWeightOrNull("fontWeight")
        } ?: FontWeight.SemiBold
    val color =
        resolveColorValue(map["color"]) ?: MaterialTheme.colorScheme.primary
    val fontFamily = resolveComposeDslFontFamily(map["fontFamily"]?.toString())
    return androidx.compose.ui.text.TextStyle(
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        fontFamily = fontFamily
    )
}

@Composable
internal fun Map<String, Any?>.colorOrNull(key: String): Color? {
    val value = this[key] ?: return null
    return resolveColorValue(value)
}

@Composable
internal fun resolveColorToken(raw: String): Color? {
    val token =
        normalizeToken(raw)
    val scheme = MaterialTheme.colorScheme
    val schemeColor =
        colorSchemeFieldByToken[token]?.let { field ->
            when (field.type) {
                java.lang.Long.TYPE -> Color(field.getLong(scheme).toULong())
                java.lang.Long::class.java -> Color((field.get(scheme) as Long).toULong())
                else -> field.get(scheme) as? Color
            }
        }
    if (schemeColor != null) {
        return schemeColor
    }
    return try {
        Color(AndroidColor.parseColor(raw))
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun resolveColorValue(value: Any?): Color? {
    return when (value) {
        is Color -> value
        is Number -> Color(value.toLong().toULong())
        is Map<*, *> -> {
            val token = value["__colorToken"]?.toString()?.trim().orEmpty()
            if (token.isBlank()) {
                null
            } else {
                val base = resolveColorToken(token) ?: return null
                val alpha = canvasNumberFromValue(value["alpha"])
                if (alpha != null) base.copy(alpha = alpha) else base
            }
        }
        else -> value?.toString()?.let { raw -> resolveColorToken(raw) }
    }
}

internal fun iconFromName(name: String): ImageVector {
    val iconKey = name.trim()
    if (iconKey.isEmpty()) {
        return Icons.Default.Info
    }
    return runCatching {
        com.ai.assistance.operit.ui.common.icons.MaterialIconNameResolver.resolveOrNull(iconKey)
    }.getOrNull() ?: Icons.Default.Info
}

@Composable
internal fun Map<String, Any?>.shapeOrNull(): androidx.compose.ui.graphics.Shape? {
    return shapeFromValue(this["shape"])
}

@Composable
internal fun Map<String, Any?>.borderOrNull(): BorderStroke? {
    val borderValue = this["border"]
    if (borderValue is Map<*, *>) {
        val width = (borderValue["width"] as? Number)?.toFloat() ?: 1f
        val alpha = (borderValue["alpha"] as? Number)?.toFloat() ?: 1f

        val colorValue = borderValue["color"]
        if (colorValue != null) {
            val color = resolveColorValue(colorValue) ?: MaterialTheme.colorScheme.outline
            return BorderStroke(width.dp, color.copy(alpha = alpha))
        }
    }
    return null
}
