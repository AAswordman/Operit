package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.protocol.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.EnhancedCodeBlock
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.common.a2ui.submission.CardSubmissionStore
import com.ai.assistance.operit.ui.common.a2ui.submission.CardSubmissionNotice
import com.ai.assistance.operit.ui.common.a2ui.submission.rememberCardSubmissionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

val LocalA2uiOwner = staticCompositionLocalOf<A2uiOwner?> { null }
val LocalA2uiSubmit = staticCompositionLocalOf<((A2uiOwner, String) -> Boolean)?> { null }
/** Solid color the card is drawn on (a plain chat bubble); unspecified for images, glass or no bubble. */
val LocalA2uiBackdrop = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun A2uiCard(source: String, closed: Boolean, streaming: Boolean, blockIndex: Int, ink: Color = Color.Unspecified) {
    val owner = LocalA2uiOwner.current
    // A completed revision owns independent state. Streaming cards are display-only.
    val revision = remember(source, closed) { if (closed) A2uiProtocol.fingerprint(source) else "streaming" }
    key(owner?.chatId, owner?.timestamp, owner?.variant, revision, blockIndex) {
        A2uiCardContent(source, closed, streaming, owner, blockIndex, ink)
    }
}

@Composable
private fun A2uiCardContent(source: String, closed: Boolean, streaming: Boolean, owner: A2uiOwner?, blockIndex: Int, ink: Color) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val submit by rememberUpdatedState(LocalA2uiSubmit.current)
    val currentOwner by rememberUpdatedState(owner)
    val storageSource = "$blockIndex:$source"
    val storageFiles = remember(owner?.chatId, owner?.timestamp, owner?.variant, storageSource) {
        mutableMapOf<String, java.io.File>()
    }
    fun stateFile(surfaceId: String): java.io.File? = currentOwner?.let { identity ->
        storageFiles.getOrPut(surfaceId) { A2uiStateStore.file(context, identity, storageSource, surfaceId) }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var validated by remember { mutableStateOf(false) }
    var expectedSurfaces by remember { mutableStateOf(emptySet<String>()) }
    var processed by remember { mutableStateOf(emptyList<String>()) }
    var components by remember { mutableStateOf<Map<String, Map<String, JsonObject>>>(emptyMap()) }
    var templates by remember { mutableStateOf<Map<String, Map<String, JsonObject>>>(emptyMap()) }
    val surfaceErrors = remember { mutableStateMapOf<String, String>() }
    val iconWarnings = remember { mutableStateMapOf<String, Boolean>() }
    val loggedErrors = remember { mutableSetOf<String>() }
    val ready = remember { mutableStateMapOf<String, Boolean>() }
    val saved = remember { mutableStateMapOf<String, JsonElement>() }
    val results = remember { mutableStateMapOf<String, Boolean>() }
    var pending by remember { mutableStateOf<A2uiClientEventMessage?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    val storageFailures by A2uiStateStore.failures.collectAsState()
    val processor = remember {
        A2uiMessageProcessor(
            listOf(operitA2uiCatalog),
            listOf(A2uiActionInterceptor { action ->
                if (action is A2uiEventAction && action.eventName in A2uiProtocol.actions + CARD_CHANGE_EVENT) action else null
            }),
        )
    }
    val surfaces by processor.activeSurfaces.collectAsState()
    val invalidText = stringResource(R.string.a2ui_invalid)
    val failedText = stringResource(R.string.a2ui_action_failed)
    val sentText = stringResource(R.string.a2ui_sent)
    val copiedText = stringResource(R.string.a2ui_copied)
    val localSavedText = stringResource(R.string.a2ui_local_saved)
    val requiredText = stringResource(R.string.a2ui_required)
    val tooLargeText = stringResource(R.string.a2ui_state_limit)
    val submitFailedText = stringResource(R.string.a2ui_submit_failed)

    fun logIssue(phase: String, surfaceId: String = "", category: String = "") {
        val safeCategory = category.filter { it.isLetterOrDigit() || it == '_' }.take(64)
        if (loggedErrors.size < 64 && loggedErrors.add("$phase:$surfaceId:$safeCategory")) {
            AppLogger.w("A2ui", "phase=$phase block=$blockIndex source=${A2uiProtocol.fingerprint(source).take(12)} surface=${surfaceId.take(80)} category=$safeCategory")
        }
    }

    fun safely(action: () -> Unit) {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { logIssue("local_action", category = e.javaClass.simpleName); notice = failedText }
    }

    fun model(surface: A2uiCoreSurfaceModel): JsonElement {
        val raw = A2uiProtocol.toJson(surface.dataModel[A2uiDataPath("/")]) as? JsonObject ?: JsonObject(emptyMap())
        return JsonObject(raw.filterKeys { it != A2uiProtocol.MARKER }).also {
            // Reserve the independent Agent quota; a progress update must never evict form data.
            require((if (CardLiveStore.isLive(surface.id)) CardLiveStore.userData(it) else it)
                .toString().length <= A2uiProtocol.MAX_STATE)
        }
    }
    fun restore(surface: A2uiCoreSurfaceModel, data: JsonElement) {
        val restored = if (CardLiveStore.isLive(surface.id)) JsonObject(data.jsonObject +
            ("agent" to A2uiProtocol.toJson(surface.dataModel[A2uiDataPath("/agent")]))) else data
        surface.dataModel.update(A2uiDataPath("/"), A2uiProtocol.toValue(restored))
        surface.dataModel.update(A2uiDataPath("/${A2uiProtocol.MARKER}"), "ready")
    }
    fun persist(surface: A2uiCoreSurfaceModel, data: JsonElement) {
        val file = stateFile(surface.id) ?: return
        A2uiStateStore.save(file, buildJsonObject {
            put("data", if (CardLiveStore.isLive(surface.id)) CardLiveStore.userData(data.jsonObject) else data)
            saved[surface.id]?.let { put("saved", if (CardLiveStore.isLive(surface.id)) CardLiveStore.userData(it.jsonObject) else it) }
            put("result", results[surface.id] == true)
        })
    }
    LaunchedEffect(processor) {
        // Start collecting errors/events before replay, otherwise a parser failure could be missed.
        launch(start = CoroutineStart.UNDISPATCHED) {
            processor.outboundEvents.collect { event ->
                when (event) {
                    is A2uiClientErrorMessage -> {
                        // AndroidX already marks the failing component. Preserve the rest of the
                        // surface, but freeze its actions if data/binding integrity is uncertain.
                        surfaceErrors[event.surfaceId] = event.code
                        if (pending?.surfaceId == event.surfaceId) pending = null
                        logIssue("engine", event.surfaceId, event.code)
                    }
                    is A2uiClientEventMessage -> {
                        if (!closed || !validated || error != null || ready[event.surfaceId] != true ||
                            event.surfaceId in surfaceErrors || pending != null ||
                            (results[event.surfaceId] == true && event.type in setOf("local_action", CARD_CHANGE_EVENT))) return@collect
                        val surface = surfaces.firstOrNull { it.id == event.surfaceId } as? A2uiCoreSurfaceModel ?: return@collect
                        if (event.componentId !in components[event.surfaceId].orEmpty()) return@collect
                        try {
                            val component = components[event.surfaceId]?.get(event.componentId)
                            val change = event.type == CARD_CHANGE_EVENT
                            // A legitimate change may itself set disabled=true. Input callbacks
                            // checked enabled before writing; late ordinary clicks check it now.
                            if (!change && component?.get("disabled")?.let { CardRepeat.evaluate(it, model(surface).jsonObject) }
                                    == JsonPrimitive(true)) return@collect
                            if (!change && component?.get("loading")?.let { CardRepeat.evaluate(it, model(surface).jsonObject) }
                                    == JsonPrimitive(true)) return@collect
                            if (!change && (component?.get("checks") as? JsonArray).orEmpty().any {
                                    CardRepeat.evaluate(it.jsonObject.getValue("condition"), model(surface).jsonObject) != JsonPrimitive(true)
                                }) return@collect
                            if (change && component?.get("onChange") == null) return@collect
                            val actionContext = if (event.type == "local_action" || change)
                                resolveLocalCardContext(surface, event.componentId, component, event.context, change)
                                else event.context
                            val payload = A2uiProtocol.toJson(actionContext)
                            require(payload.toString().length <= A2uiProtocol.MAX_STATE)
                            when (if (change) "local_action" else event.type) {
                                "local_action" -> when (actionContext["op"]) {
                                    "set", "add" -> {
                                        val path = actionContext["path"] as? String ?: error("Missing path")
                                        A2uiProtocol.validatePath(path)
                                        require(!CardLiveStore.isLive(surface.id) || !CardLiveStore.protectedPath(path)) {
                                            "User actions cannot write Agent data"
                                        }
                                        val next = if (actionContext["op"] == "add") {
                                            val old = surface.dataModel[A2uiDataPath(path)] as? Number ?: 0
                                            val delta = actionContext["value"] as? Number ?: error("Missing number")
                                            (old.toDouble() + delta.toDouble()).also { require(it.isFinite() && kotlin.math.abs(it) <= 1e9) }
                                        } else actionContext["value"]
                                        A2uiProtocol.toJson(next)
                                        val before = model(surface)
                                        var candidate = CardRowState.userWrite(before.jsonObject, path, A2uiProtocol.toJson(next))
                                        templates[surface.id]?.let { definition ->
                                            candidate = CardRowState.prepare(definition, candidate, CardLiveStore.isLive(surface.id))
                                            A2uiProtocol.validateExpanded(CardRepeat.expand(definition, candidate), candidate)
                                        }
                                        require((if (CardLiveStore.isLive(surface.id)) CardLiveStore.userData(candidate) else candidate)
                                            .toString().length <= A2uiProtocol.MAX_STATE) { "Data model too large" }
                                        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { restore(surface, candidate) }
                                    }
                                    "row_set" -> {
                                        val definition = templates[surface.id] ?: error("No row templates")
                                        val candidate = CardRowState.set(definition, model(surface).jsonObject,
                                            actionContext["repeat"] as? String ?: error("Missing Repeat"),
                                            actionContext["key"] as? String ?: error("Missing key"),
                                            actionContext["field"] as? String ?: error("Missing field"),
                                            A2uiProtocol.toJson(actionContext["value"]), CardLiveStore.isLive(surface.id),
                                            (actionContext["parents"] as? List<*>)?.map { it as? String ?: error("Invalid parent key") }.orEmpty())
                                        A2uiProtocol.validateExpanded(CardRepeat.expand(definition, candidate), candidate)
                                        restore(surface, candidate)
                                    }
                                    "save" -> { saved[surface.id] = model(surface); results[surface.id] = true; notice = localSavedText }
                                    "edit" -> results[surface.id] = false
                                    "cancel" -> { saved[surface.id]?.let { restore(surface, it) }; results[surface.id] = saved.containsKey(surface.id) }
                                    else -> error("Unknown local action")
                                }
                                "ai_submit", "tool_action" -> {
                                    val missing = (event.context["required"] as? List<*>).orEmpty().any {
                                        val path = it as? String ?: error("Invalid required path")
                                        A2uiProtocol.validatePath(path)
                                        surface.dataModel[A2uiDataPath(path)]?.toString().isNullOrBlank()
                                    }
                                    if (missing) notice = requiredText else pending = event
                                }
                                "open_url" -> {
                                    val url = event.context["url"] as? String ?: error("Missing URL")
                                    require(A2uiProtocol.safeUrl(url))
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                "copy" -> {
                                    val text = event.context["text"] as? String ?: error("Missing text")
                                    require(text.length <= 8192)
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("", text))
                                    notice = copiedText
                                }
                            }
                            persist(surface, model(surface))
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { logIssue("action", event.surfaceId, e.javaClass.simpleName); notice = failedText }
                    }
                }
            }
        }
        processor.collectMessages()
    }
    LaunchedEffect(source, closed) {
        try {
            val batch = withContext(Dispatchers.Default) { A2uiProtocol.parse(source, closed) }
            expectedSurfaces = batch.surfaceIds
            if (!closed && batch.componentsBySurface.values.any { graph ->
                    graph.values.any { it["component"]?.jsonPrimitive?.content == "Repeat" }
                }) return@LaunchedEffect
            templates = batch.componentsBySurface.filterValues { graph ->
                graph.values.any { it["component"]?.jsonPrimitive?.content == "Repeat" }
            }
            components = batch.componentsBySurface.mapValues { (id, graph) ->
                if (id in templates) CardRepeat.expand(graph, CardRowState.prepare(graph,
                    CardRepeat.initialData(batch.lines, id), CardLiveStore.isLive(id))) else graph
            }
            require(batch.lines.take(processed.size) == processed) { "Card changed during streaming" }
            val parser = A2uiMessageParser()
            // Parse everything before feeding anything: malformed updates never reach the renderer.
            val messages = withContext(Dispatchers.Default) {
                (if (templates.isNotEmpty()) CardRepeat.lowerLines(batch) else batch.lines)
                    .drop(processed.size).map { parser.parse(it) }
            }
            messages.forEach { processor.processMessage(it) }
            processed = batch.lines
            if (closed) {
                batch.surfaceIds.forEach { id ->
                    val record = stateFile(id)?.let { A2uiStateStore.read(it) }
                    val live = if (CardLiveStore.isLive(id)) currentOwner?.let { identity ->
                        CardLiveStore.observe(identity.chatId, source, id)?.value
                            ?: CardLiveStore.read(requireNotNull(stateFile(id)).parentFile!!, source, id)
                    } else null
                    val initialAgent = CardRepeat.initialData(batch.lines, id)["agent"]
                    val agent = live?.get("agent") ?: initialAgent
                    record?.get("data")?.let { old ->
                        val data = if (CardLiveStore.isLive(id) && agent != null)
                            JsonObject(CardLiveStore.userData(old.jsonObject) + ("agent" to agent)) else old
                        val initialized = templates[id]?.let { CardRowState.prepare(it, data.jsonObject, CardLiveStore.isLive(id)) } ?: data
                        processor.processMessage(A2uiUpdateDataModelMessage(id, "/", A2uiProtocol.toValue(initialized)))
                    }
                    if (live != null) processor.processMessage(A2uiUpdateDataModelMessage(id, "/agent", A2uiProtocol.toValue(live.getValue("agent"))))
                    record?.get("saved")?.let { saved[id] = it }
                    results[id] = record?.get("result")?.jsonPrimitive?.booleanOrNull == true
                    processor.processMessage(A2uiUpdateDataModelMessage(id, "/${A2uiProtocol.MARKER}", "ready"))
                }
            }
            validated = closed
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            logIssue("protocol", category = "${A2uiProtocol.diagnosticCode(e)}_line_${A2uiProtocol.diagnosticLine(e) ?: 0}")
            error = e.message?.take(240) ?: invalidText
        }
    }
    surfaces.forEach { candidate ->
        val surface = candidate as A2uiCoreSurfaceModel
        LaunchedEffect(surface, validated) {
            if (!validated) return@LaunchedEffect
            snapshotFlow {
                surface.dataModel[A2uiDataPath("/${A2uiProtocol.MARKER}")] == "ready" ||
                    surface.id in surfaceErrors
            }.first { it }
            if (surface.id in surfaceErrors) return@LaunchedEffect
            // Repeat registries still contain the initial rows until their first restored expansion.
            if (surface.id !in templates) ready[surface.id] = true
            if (CardLiveStore.isLive(surface.id)) currentOwner?.let { identity ->
                CardLiveStore.observe(identity.chatId, source, surface.id)?.let { updates ->
                    launch {
                        updates.collect { record ->
                            if (record["error"]?.jsonPrimitive?.booleanOrNull == true) notice = failedText
                            try {
                                surface.dataModel.update(A2uiDataPath("/agent"), A2uiProtocol.toValue(record.getValue("agent")))
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { notice = failedText }
                        }
                    }
                }
            }
            templates[surface.id]?.let { definition ->
                launch {
                    snapshotFlow {
                        runCatching {
                            val current = model(surface).jsonObject
                            CardRepeat.expand(definition, CardRowState.prepare(definition, current,
                                CardLiveStore.isLive(surface.id))) }.getOrNull()
                    }.collect { items ->
                        try {
                            require(items != null)
                            val before = model(surface).jsonObject
                            val data = CardRowState.prepare(definition, before, CardLiveStore.isLive(surface.id))
                            val next = CardRepeat.expand(definition, data)
                            A2uiProtocol.validateExpanded(next, data)
                            if (components[surface.id] != next || before != data) {
                                val payloads = CardRepeat.payloads(next)
                                // Replace synchronously without deleting the surface/data model.
                                // Registry.update alone would retain every removed row forever.
                                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                    if (before != data) restore(surface, data)
                                    surface.componentRegistry.close()
                                    surface.componentRegistry.update(payloads)
                                    components = components + (surface.id to next)
                                }
                            }
                            ready[surface.id] = true
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            // Replace failed lists, keeping outside filters usable for correction.
                            val fallback = components[surface.id].orEmpty()
                                .filterKeys { it in definition }.toMutableMap()
                            definition.filterValues { it["component"]?.jsonPrimitive?.content == "Repeat" }.keys
                                .filter { it in components[surface.id].orEmpty() }.forEach { id ->
                                fallback[id] = buildJsonObject { put("id", id); put("component", "Text"); put("text", invalidText) }
                            }
                            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                surface.componentRegistry.close()
                                surface.componentRegistry.update(CardRepeat.payloads(fallback))
                                components = components + (surface.id to fallback)
                            }
                            if (pending?.surfaceId == surface.id) pending = null
                            ready[surface.id] = true
                        }
                    }
                }
            }
            snapshotFlow { runCatching { model(surface) }.getOrNull() }.collect { data ->
                if (data != null && error == null && surface.id !in surfaceErrors) persist(surface, data)
                else if (data == null) notice = tooLargeText
            }
        }
    }
    // Embedded like a Markdown block: no host frame, background or padding. A root Card draws its
    // own frame, so only authors who need visual grouping get one.
    MaterialTheme(
        colorScheme = cardColorScheme(MaterialTheme.colorScheme, ink, LocalA2uiBackdrop.current),
        shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(20.dp)),
        typography = MaterialTheme.typography.copy(bodyLarge = MaterialTheme.typography.bodyMedium),
    ) {
    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        var measuredHeight by rememberSaveable { mutableIntStateOf(0) }
        var measuredWidth by rememberSaveable { mutableIntStateOf(0) }
        var measuredDensity by rememberSaveable { mutableFloatStateOf(0f) }
        var measuredFontScale by rememberSaveable { mutableFloatStateOf(0f) }
        val restored = validated &&
            expectedSurfaces.all { id -> ready[id] == true && surfaces.any { it.id == id } }
        val waiting = closed && error == null && surfaceErrors.isEmpty() && !restored
        val compatibleHeight = measuredWidth == constraints.maxWidth &&
            measuredDensity == density.density && measuredFontScale == density.fontScale
        if (waiting) {
            // Save only geometry across LazyColumn disposal, never processors or WebViews.
            // Replaying defaults and loading disk state must not collapse this slot or paint stale UI.
            Spacer(Modifier.fillMaxWidth().height(
                if (compatibleHeight && measuredHeight > 0) with(density) { measuredHeight.toDp() } else 48.dp
            ))
        } else {
        Column(Modifier.fillMaxWidth().onSizeChanged {
            if (closed && restored && error == null && surfaceErrors.isEmpty() && it.height > 0) {
                measuredHeight = it.height
                measuredWidth = constraints.maxWidth
                measuredDensity = density.density
                measuredFontScale = density.fontScale
            }
        }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error != null) {
                Text(invalidText, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.a2ui_regenerate), style = MaterialTheme.typography.bodySmall)
            } else {
                if (!closed) Text(stringResource(if (streaming) R.string.a2ui_generating else R.string.a2ui_interrupted))
                if (surfaceErrors.keys.any { id -> surfaces.none { it.id == id } }) {
                    Text(invalidText, color = MaterialTheme.colorScheme.error)
                }
                surfaces.forEach { surface ->
                    key(surface.id) {
                        val readOnly = results[surface.id] == true
                        val awaitable = CardSubmissionStore.isAwaitable(surface.id)
                        val submissionStatus = rememberCardSubmissionStatus(owner, source, surface.id)
                        val accepting = !awaitable || submissionStatus == CardSubmissionStore.Status.Waiting
                        LaunchedEffect(surface.id, submissionStatus) {
                            if (awaitable && !accepting && pending?.surfaceId == surface.id) pending = null
                        }
                        if (awaitable) CardSubmissionNotice(submissionStatus) {
                            currentOwner?.let { identity ->
                                if (CardSubmissionStore.current.cancel(identity, source, surface.id)) pending = null
                            }
                        }
                        val enabled = closed && ready[surface.id] == true && pending == null && !readOnly && accepting && surface.id !in surfaceErrors
                        CompositionLocalProvider(
                            LocalCardInteractive provides enabled,
                            LocalCardBrowse provides (closed && ready[surface.id] == true && pending == null && accepting && surface.id !in surfaceErrors),
                            LocalCardReadOnly provides readOnly,
                            LocalCardValueResolver provides { path ->
                                A2uiProtocol.toJson((surface as A2uiCoreSurfaceModel).dataModel[A2uiDataPath(path)])
                            },
                            LocalCardMutationGuard provides { update ->
                                val core = surface as A2uiCoreSurfaceModel
                                updateCardField({ logIssue("field_update", surface.id); notice = failedText }) {
                                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                        require(accepting && ready[surface.id] == true && results[surface.id] != true &&
                                            pending == null && surface.id !in surfaceErrors)
                                        val before = model(core)
                                        try {
                                            update()
                                            val after = model(core).jsonObject
                                            templates[surface.id]?.let { definition ->
                                                val initialized = CardRowState.prepare(definition, after, CardLiveStore.isLive(surface.id))
                                                A2uiProtocol.validateExpanded(CardRepeat.expand(definition, initialized), initialized)
                                                if (initialized != after) restore(core, initialized)
                                            }
                                        } catch (e: Exception) { restore(core, before); throw e }
                                    }
                                }
                            },
                            LocalCardIconWarning provides {
                                iconWarnings[surface.id] = true
                                logIssue("icon", surface.id, "unknown_token")
                            },
                        ) {
                            CardPopoverHost {
                            CardSurface(surface, modifier = Modifier.fillMaxWidth(), errorContent = {
                                LaunchedEffect(it) {
                                    surfaceErrors[surface.id] = it.code
                                    logIssue("render", surface.id, it.code)
                                }
                                Text(invalidText, color = MaterialTheme.colorScheme.error)
                            })
                            }
                        }
                        if (surface.id in surfaceErrors) Text(
                            stringResource(R.string.a2ui_partial_error), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (readOnly) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.a2ui_saved), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            if (!awaitable) TextButton(enabled = pending == null && surface.id !in surfaceErrors, onClick = { safely { results[surface.id] = false; persist(surface as A2uiCoreSurfaceModel, model(surface)) } }) {
                                Text(stringResource(R.string.a2ui_edit))
                            }
                        } else {
                            if (saved.containsKey(surface.id)) TextButton(onClick = { safely {
                                restore(surface as A2uiCoreSurfaceModel, saved.getValue(surface.id))
                                results[surface.id] = true
                                persist(surface, model(surface))
                            } }, enabled = enabled) { Text(stringResource(R.string.a2ui_cancel_edit)) }
                        }
                    }
                }
            }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (owner != null && surfaces.any {
                stateFile(it.id) in storageFailures
            }) Text(stringResource(R.string.a2ui_save_failed), color = MaterialTheme.colorScheme.error)
            pending?.let { event ->
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when {
                    CardSubmissionStore.isAwaitable(event.surfaceId) -> R.string.a2ui_confirm_wait
                    event.type == "tool_action" -> R.string.a2ui_confirm_tool
                    else -> R.string.a2ui_confirm_send
                }))
                val graph = components[event.surfaceId].orEmpty()
                val definition = (graph[event.componentId]?.get("action") as? JsonObject)?.get("event")
                    ?.jsonObject?.get("context")
                val rows = remember(event, definition, graph) {
                    cardSubmissionRows(A2uiProtocol.toJson(event.context), definition, graph.values)
                }
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { (label, value) ->
                        Column {
                            if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val text = when {
                                value is JsonPrimitive && value.booleanOrNull != null && !value.isString ->
                                    stringResource(if (value.boolean) R.string.a2ui_yes else R.string.a2ui_no)
                                value is JsonPrimitive -> value.content.takeUnless { value is JsonNull }.orEmpty()
                                else -> value.toString()
                            }
                            Text(text.ifBlank { stringResource(R.string.a2ui_empty_value) }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { safely {
                        check(pending === event && error == null && event.surfaceId !in surfaceErrors) { "Card is no longer ready" }
                        // Clear pending first: a double click cannot enqueue the same receipt twice.
                        pending = null
                        val identity = currentOwner
                        val surface = surfaces.firstOrNull { it.id == event.surfaceId } as? A2uiCoreSurfaceModel
                            ?: error("Surface missing")
                        require(event.componentId in components[event.surfaceId].orEmpty()) { "Card item no longer exists" }
                        val submittedState = model(surface) // Validate before any model request.
                        val request = buildJsonObject {
                            put("card", event.surfaceId); put("action", event.type)
                            put("data", A2uiProtocol.toJson(event.context))
                        }
                        val awaitable = CardSubmissionStore.isAwaitable(surface.id)
                        val accepted = identity != null && if (awaitable) {
                            submit != null && CardSubmissionStore.current.submit(identity, source, surface.id,
                                event.type, A2uiProtocol.toJson(event.context).jsonObject)
                        } else {
                            submit?.invoke(identity, resources.getString(R.string.a2ui_request) + "\n" + request.toString()) == true
                        }
                        if (accepted) {
                            saved[surface.id] = submittedState
                            results[surface.id] = true
                            persist(surface, submittedState)
                        }
                        notice = when {
                            accepted && awaitable -> null
                            accepted -> sentText
                            awaitable -> resources.getString(R.string.a2ui_wait_inactive)
                            else -> submitFailedText
                        }
                    } }) { Text(stringResource(if (CardSubmissionStore.isAwaitable(event.surfaceId)) R.string.a2ui_submit_wait else R.string.a2ui_send)) }
                    TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) }
                }
                }
                }
            }
            // Source is a diagnostic tool, so it only appears when this block has a problem.
            val hasIssue = error != null || surfaceErrors.isNotEmpty() || iconWarnings.isNotEmpty()
            if (hasIssue) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (iconWarnings.isNotEmpty()) Text(stringResource(R.string.a2ui_icon_missing), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { showSource = !showSource }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Code, stringResource(R.string.a2ui_source), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hasIssue && showSource) {
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                EnhancedCodeBlock(source.take(A2uiProtocol.MAX_SOURCE), "json")
            }
        }
        }
        }
    }
    }
    }
}
