package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.data.model.theme.*
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.theme.resolveThemeColorScheme
import com.ai.assistance.operit.ui.theme.script.*
import com.ai.assistance.operit.data.themesharing.ThemeTransferRepository
import com.ai.assistance.operit.data.themesharing.ThemeTransferSource
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 工作室只编辑不可变资源束；应用时才把新引用交给当前角色草稿。 */
internal class ThemeStudioState(
    private val context: Context,
    val session: ThemeEditorSession,
    projectKey: String,
    val targetName: String,
) {
    var project by mutableStateOf<ThemeScriptBundle?>(null); private set
    var images by mutableStateOf<Map<String, ThemeScriptImage>>(emptyMap()); private set
    var busy by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var workspace by mutableStateOf(StudioWorkspace.Design)
    var scene by mutableStateOf(StudioScene.Chat)
    var previewInputStyle by mutableStateOf(session.currentValues.requiredString("input_style"))
    var mode by mutableStateOf("light")
    var target by mutableStateOf(ThemeStudioTargets.first { it.id == "assistantMessage" })
    var property by mutableStateOf(StudioProperty.Appearance)
    var previewState by mutableStateOf("always")
    var previewCollapsed by mutableStateOf(false)
    var currentFolder by mutableStateOf("assets")
    var resourceFilter by mutableStateOf("all")
    var resourceQuery by mutableStateOf("")
    var resourceList by mutableStateOf(false)
    var sourceFile by mutableStateOf("theme.json"); private set
    var sourceText by mutableStateOf(""); private set
    var sourceDirty by mutableStateOf(false); private set
    private var baseline: ThemeScriptBundle? = null
    private var gesture = false
    private var gestureSaved = false
    private data class Revision(val bundle: ThemeScriptBundle, val images: Map<String, ThemeScriptImage>)
    private val undoStack = mutableStateListOf<Revision>()
    private val redoStack = mutableStateListOf<Revision>()
    private val json = Json(ThemeScriptContract.json) { prettyPrint = true }
    private val fontDirectory = File(context.cacheDir, "theme-fonts")
    private val draftDirectory = File(context.filesDir, "theme-studio")
    private val draftId = MessageDigest.getInstance("SHA-256").digest(projectKey.toByteArray()).joinToString("") { "%02x".format(it) }
    private val draftFile get() = File(draftDirectory, "draft_$draftId.zip")
    val canUndo get() = undoStack.isNotEmpty() && !sourceDirty
    val canRedo get() = redoStack.isNotEmpty() && !sourceDirty
    val hasChanges get() = sourceDirty || project != baseline
    val bundle get() = requireNotNull(project) { "Theme studio has not loaded" }
    val appearance get() = bundle.manifest.appearance.forMode(mode)
    val imageAssets get() = bundle.manifest.assets.filterValues { it.substringAfterLast('.').lowercase() !in setOf("ttf", "otf") }
    val fontAssets get() = bundle.manifest.assets.filterValues { it.substringAfterLast('.').lowercase() in setOf("ttf", "otf") }
    val folders: List<String> get() {
        val result = mutableSetOf("assets")
        fun addParents(path: String) {
            var folder = path
            while (folder != "assets" && folder.startsWith("assets/")) { result.add(folder); folder = folder.substringBeforeLast('/') }
        }
        bundle.manifest.studio.folders.forEach(::addParents)
        (bundle.manifest.assets.values + bundle.manifest.entry).forEach { addParents(it.substringBeforeLast('/')) }
        return result.sorted()
    }

    suspend fun load() {
        task {
            val loaded = withContext(Dispatchers.IO) {
                when {
                    draftFile.isFile -> ThemeScriptBundleStore.read(draftFile)
                    session.currentValues.string(ThemePreferenceValues.SCRIPT_URI_KEY) != null -> {
                        val ref = requireNotNull(session.currentValues.string(ThemePreferenceValues.SCRIPT_URI_KEY))
                        ThemeScriptBundleStore.read(ThemeScriptBundleStore.fileForReference(context.filesDir, ref))
                    }
                    else -> ThemeScriptBundle(
                        ThemeScriptManifest(1, "assets/main.js", studio = ThemeStudioMetadata(name = targetName, id = "theme_" + UUID.randomUUID().toString().replace("-", ""), folders = listOf("assets/images", "assets/icons", "assets/fonts"))),
                        ThemeScriptDefaults.SOURCE, emptyMap(),
                    )
                }
            }
            val prepared = withContext(Dispatchers.IO) { ThemeScriptAssets.prepare(loaded, fontDirectory).also { ThemeScriptAssets.validateAppearance(loaded.manifest.appearance, it) } }
            project = loaded
            images = prepared
            baseline = loaded
            refreshSource()
        }
    }

    fun select(next: StudioTarget) {
        if (target == next) return
        target = next
        scene = next.scene
        previewState = "always"
        if (property !in next.properties) property = StudioProperty.Appearance
    }
    fun selectScene(next: StudioScene) {
        scene = next
        if (target.scene != next) select(ThemeStudioTargets.first { it.id == when (next) { StudioScene.Chat -> "assistantMessage"; StudioScene.Components -> "settingsCard"; StudioScene.Navigation -> "navigation" } })
    }
    fun report(failure: Exception) {
        AppLogger.e("ThemeStudio", "主题工作室操作失败", failure)
        error = failure.message.orEmpty().take(4096)
    }
    fun clearError() { error = null }
    private suspend fun task(action: suspend () -> Unit): Boolean {
        busy = true; error = null
        return try { action(); true } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { report(failure); false } finally { busy = false }
    }
    private fun rememberEdit() {
        undoStack.add(Revision(bundle, images))
        if (undoStack.size > 24) undoStack.removeAt(0)
        redoStack.clear()
    }
    fun edit(history: Boolean = true, allowSource: Boolean = false, decoded: Map<String, ThemeScriptImage> = images, change: (ThemeScriptBundle) -> ThemeScriptBundle): Boolean {
        return try {
            check(allowSource || !sourceDirty) { "Synchronize the source file before editing design properties" }
            val next = change(bundle)
            ThemeScriptContract.validate(next.manifest)
            require(next.assets.keys == next.manifest.assets.values.toSet()) { "Resource paths do not match the manifest" }
            require(next.source.isNotBlank() && next.source.toByteArray().size <= ThemeScriptContract.MAX_SOURCE_BYTES) { "Theme script is empty or too large" }
            val nextImages = decoded.filterKeys { it in next.manifest.assets.keys }
            ThemeScriptAssets.validateAppearance(next.manifest.appearance, nextImages)
            if (next != project) {
                if (history || (gesture && !gestureSaved)) {
                    rememberEdit()
                    if (gesture) gestureSaved = true
                }
                project = next
                images = nextImages
                if (sourceFile !in setOf("theme.json", "manifest.json")) sourceFile = next.manifest.entry
            }
            error = null
            true
        } catch (failure: Exception) { report(failure); false }
    }
    private fun restore(revision: Revision) {
        project = revision.bundle
        images = revision.images
        if (sourceFile !in setOf("theme.json", "manifest.json")) sourceFile = revision.bundle.manifest.entry
        while (currentFolder != "assets" && currentFolder !in folders) currentFolder = currentFolder.substringBeforeLast('/')
        refreshSource()
        error = null
    }
    fun undo() {
        if (!canUndo) return
        endGesture()
        redoStack.add(Revision(bundle, images))
        restore(undoStack.removeAt(undoStack.lastIndex))
    }
    fun redo() {
        if (!canRedo) return
        endGesture()
        undoStack.add(Revision(bundle, images))
        restore(redoStack.removeAt(redoStack.lastIndex))
    }
    fun beginGesture() { if (!gesture) { gesture = true; gestureSaved = false } }
    fun endGesture() { gesture = false; gestureSaved = false }
    fun updateAppearance(change: (ThemeScriptAppearance) -> ThemeScriptAppearance) = edit(history = !gesture) { it.copy(manifest = it.manifest.copy(appearance = change(it.manifest.appearance))) }
    fun surface(id: String = target.id, forMode: String = mode): ThemeScriptSurface = bundle.manifest.appearance.forMode(forMode).surfaces[id] ?: ThemeScriptSurface()
    fun updateSurface(id: String = target.id, forMode: String = mode, change: (ThemeScriptSurface) -> ThemeScriptSurface) = updateAppearance { base ->
        val variant = base.modes[forMode] ?: ThemeAppearanceMode()
        val updated = change(base.forMode(forMode).surfaces[id] ?: ThemeScriptSurface())
        base.copy(modes = base.modes + (forMode to variant.copy(surfaces = variant.surfaces + (id to updated))))
    }
    fun updateText(forMode: String = mode, change: (ThemeAppearanceText) -> ThemeAppearanceText) {
        if (target.kind == StudioKind.Typography) updateAppearance { base ->
            val variant = base.modes[forMode] ?: ThemeAppearanceMode()
            base.copy(modes = base.modes + (forMode to variant.copy(typography = change(base.forMode(forMode).typography))))
        } else updateSurface(forMode = forMode) { it.copy(textStyle = change(it.textStyle)) }
    }
    fun icon(id: String = target.id, forMode: String = mode) = bundle.manifest.appearance.forMode(forMode).icons[id]
    fun setIcon(value: ThemeAppearanceIcon?, id: String = target.id, forMode: String = mode) = updateAppearance { base ->
        val variants = base.modes.toMutableMap()
        for (name in listOf("light", "dark")) {
            val variant = variants[name] ?: ThemeAppearanceMode()
            val icons = base.forMode(name).icons.toMutableMap()
            if (name == forMode) { if (value == null) icons.remove(id) else icons[id] = value }
            variants[name] = variant.copy(icons = icons)
        }
        base.copy(icons = base.icons - id, modes = variants)
    }
    fun setPalette(token: String, color: String?, forMode: String) = updateAppearance { base ->
        val colors = if (forMode == "light") base.palette.light else base.palette.dark
        val next = if (color == null) colors - token else colors + (token to color)
        base.copy(palette = if (forMode == "light") base.palette.copy(light = next) else base.palette.copy(dark = next))
    }
    fun color(field: String, forMode: String): String? {
        if (target.kind == StudioKind.Palette) return (if (forMode == "light") bundle.manifest.appearance.palette.light else bundle.manifest.appearance.palette.dark)[field]
        if (target.kind == StudioKind.Icon) return icon(forMode = forMode)?.tintColor
        if (target.kind == StudioKind.Typography) return bundle.manifest.appearance.forMode(forMode).typography.color
        if (field == "textColor") return surface(forMode = forMode).textStyle.color
        val value = surface(forMode = forMode).forState(if (property == StudioProperty.States) previewState else "always")
        return when (field) { "fillColor" -> value.fillColor; "contentColor" -> value.contentColor; "strokeColor" -> value.strokeColor; else -> error("Unknown color field") }
    }
    fun setColor(field: String, value: String?, forMode: String) {
        when (target.kind) {
            StudioKind.Palette -> setPalette(field, value, forMode)
            StudioKind.Icon -> icon(forMode = forMode)?.let { setIcon(it.copy(tintColor = value, useContentTint = if (value == null) it.useContentTint else false), forMode = forMode) }
            StudioKind.Surface -> updateSurface(forMode = forMode) { surface ->
                if (property == StudioProperty.States && previewState != "always") {
                    val before = surface.states[previewState] ?: ThemeAppearanceState()
                    val next = when (field) { "fillColor" -> before.copy(fillColor = value); "contentColor" -> before.copy(contentColor = value); "strokeColor" -> before.copy(strokeColor = value); else -> error("Unknown state color") }
                    surface.copy(states = surface.states + (previewState to next))
                } else when (field) { "fillColor" -> surface.copy(fillColor = value); "contentColor" -> surface.copy(contentColor = value); "strokeColor" -> surface.copy(strokeColor = value); "textColor" -> surface.copy(textStyle = surface.textStyle.copy(color = value)); else -> error("Unknown surface color") }
            }
            StudioKind.Typography -> updateText(forMode) { it.copy(color = value) }
        }
        mode = forMode
    }
    fun updateLayer(index: Int, change: (ThemeScriptLayer) -> ThemeScriptLayer) = updateSurface { surface -> surface.copy(layers = surface.layers.mapIndexed { i, value -> if (i == index) change(value) else value }) }
    fun addLayer(asset: String) = updateSurface { it.copy(layers = it.layers + ThemeScriptLayer("image", id = "layer_" + UUID.randomUUID().toString().replace("-", ""), asset = asset, fit = "crop")) }
    fun deleteLayer(index: Int) = updateSurface { it.copy(layers = it.layers.filterIndexed { i, _ -> i != index }) }
    fun moveLayer(index: Int, delta: Int) = updateSurface { value -> val layers = value.layers.toMutableList(); val other = index + delta; require(other in layers.indices); val item = layers.removeAt(index); layers.add(other, item); value.copy(layers = layers) }
    fun metadata(value: ThemeStudioMetadata) = edit { it.copy(manifest = it.manifest.copy(studio = value)) }
    fun createFolder(name: String) {
        edit { current ->
            val path = currentFolder + "/" + name.trim()
            require(!name.contains('/') && ThemeAssetPaths.isFolder(path) && path !in folders && path !in current.assets) { "Folder name is invalid or already exists" }
            current.copy(manifest = current.manifest.copy(studio = current.manifest.studio.copy(folders = (current.manifest.studio.folders + path).distinct().sorted())))
        }
    }
    fun renameFolder(path: String, name: String) {
        val destination = path.substringBeforeLast('/') + "/" + name.trim()
        edit { current ->
            require(path != "assets" && !name.contains('/') && ThemeAssetPaths.isFolder(destination) && destination !in folders) { "Folder name is invalid or already exists" }
            fun remap(value: String): String = if (value == path || value.startsWith("$path/")) destination + value.removePrefix(path) else value
            val manifest = current.manifest.copy(entry = remap(current.manifest.entry), assets = current.manifest.assets.mapValues { (_, value) -> remap(value) }, studio = current.manifest.studio.copy(folders = current.manifest.studio.folders.map(::remap).distinct().sorted()))
            current.copy(manifest = manifest, assets = current.assets.mapKeys { (key, _) -> remap(key) })
        }.also { if (it && (currentFolder == path || currentFolder.startsWith("$path/"))) currentFolder = destination + currentFolder.removePrefix(path) }
    }
    fun deleteFolder(path: String) = edit { current ->
        require(path != "assets") { "The resource root cannot be deleted" }
        require(current.manifest.assets.values.none { it.startsWith("$path/") } && !current.manifest.entry.startsWith("$path/")) { "Only empty folders can be deleted" }
        current.copy(manifest = current.manifest.copy(studio = current.manifest.studio.copy(folders = current.manifest.studio.folders.filterNot { it == path || it.startsWith("$path/") })))
    }
    fun moveAsset(id: String, folder: String, filename: String) = edit { current ->
        require(folder == "assets" || ThemeAssetPaths.isFolder(folder)) { "Invalid destination folder" }
        val old = current.manifest.assets.getValue(id)
        val path = folder + "/" + filename.trim()
        require(!filename.contains('/') && path.substringAfterLast('.').lowercase() == old.substringAfterLast('.').lowercase() && path !in folders && ThemeAssetPaths.isFile(path) && path != current.manifest.entry && (path == old || path !in current.assets)) { "Resource name is invalid or already exists" }
        val paths = current.manifest.assets + (id to path)
        val bytes = (current.assets + (path to current.assets.getValue(old))).filterKeys { it in paths.values }
        current.copy(manifest = current.manifest.copy(assets = paths), assets = bytes)
    }
    fun assetUsed(id: String): Boolean {
        val base = bundle.manifest.appearance
        val appearances = listOf(base.copy(modes = emptyMap())) + base.modes.keys.map(base::forMode)
        return appearances.any { appearance -> appearance.icons.values.any { it.asset == id } || appearance.typography.family == "asset:$id" || appearance.surfaces.values.any { it.textStyle.family == "asset:$id" || it.layers.any { layer -> layer.asset == id } } }
    }
    fun deleteAsset(id: String) = edit { current ->
        require(!assetUsed(id)) { "Remove the resource bindings before deleting this file" }
        val paths = current.manifest.assets - id
        current.copy(manifest = current.manifest.copy(assets = paths), assets = current.assets.filterKeys { it in paths.values })
    }
    suspend fun importResource(uri: Uri) = task {
        val added = withContext(Dispatchers.IO) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                require(cursor.moveToFirst()) { "Resource name is missing" }; cursor.getString(0)
            } ?: error("Cannot read resource name")
            val path = "$currentFolder/$name"
            require(ThemeAssetPaths.isFile(path) && name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "svg", "ttf", "otf")) { "Expected PNG, JPEG, WebP, SVG, TTF or OTF" }
            require(path !in bundle.assets && path != bundle.manifest.entry) { "A resource with this name already exists" }
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { ThemeScriptBundleStore.readLimited(it, ThemeScriptContract.MAX_ASSET_BYTES) }
            val id = "asset_" + UUID.randomUUID().toString().replace("-", "")
            bundle.copy(manifest = bundle.manifest.copy(assets = bundle.manifest.assets + (id to path)), assets = bundle.assets + (path to bytes)).also(ThemeScriptBundleStore::validate)
        }
        val prepared = withContext(Dispatchers.IO) { ThemeScriptAssets.prepare(added, fontDirectory, bundle, images) }
        edit(decoded = prepared) { added }
    }
    fun setSource(value: String) { sourceText = value; sourceDirty = true }
    fun refreshSource() {
        sourceText = when (sourceFile) {
            "theme.json" -> json.encodeToString(bundle.manifest.appearance)
            "manifest.json" -> json.encodeToString(bundle.manifest)
            else -> bundle.source
        }
        sourceDirty = false
    }
    fun selectFile(value: String): Boolean { if (sourceDirty && !synchronizeSource()) return false; sourceFile = value; refreshSource(); return true }
    fun synchronizeSource(): Boolean {
        val accepted = edit(allowSource = true) { current ->
            val updated = when (sourceFile) {
                "theme.json" -> current.copy(manifest = current.manifest.copy(appearance = ThemeScriptContract.decodeResult(sourceText, current.manifest)))
                "manifest.json" -> {
                    val next = ThemeScriptContract.decodeManifest(sourceText)
                    val bytes = next.assets.map { (id, path) ->
                        require(id in current.manifest.assets) { "Import resource files before declaring new identifiers" }
                        path to current.assets.getValue(current.manifest.assets.getValue(id))
                    }.toMap()
                    current.copy(manifest = next, assets = bytes)
                }
                else -> current.copy(source = sourceText)
            }
            ThemeScriptBundleStore.validate(updated)
            updated
        }
        if (accepted) refreshSource()
        return accepted
    }
    fun formatSource() {
        try { sourceText = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), json.parseToJsonElement(sourceText)); sourceDirty = true; error = null } catch (failure: Exception) { report(failure) }
    }
    fun enterWorkspace(next: StudioWorkspace) { if (sourceDirty && !synchronizeSource()) return; workspace = next; if (next == StudioWorkspace.Source) refreshSource() }
    suspend fun loadPreset(id: String) = task {
        val next = withContext(Dispatchers.IO) { ThemeScriptBundleStore.readPreset(context.assets, id) }
        val prepared = withContext(Dispatchers.IO) { ThemeScriptAssets.prepare(next, fontDirectory, bundle, images) }
        edit(decoded = prepared) { next }
    }
    suspend fun saveProjectDraft(): Boolean {
        if (sourceDirty && !synchronizeSource()) return false
        return task {
            withContext(Dispatchers.IO) {
                val temporary = ThemeScriptBundleStore.create(draftDirectory, bundle)
                try { Files.move(temporary.toPath(), draftFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } finally { temporary.delete() }
            }
            baseline = bundle
        }
    }
    private suspend fun validateProject(snapshot: ThemePreferenceSnapshot) {
        val current = bundle
        val prepared = withContext(Dispatchers.IO) {
            ThemeScriptBundleStore.validate(current)
            ThemeScriptAssets.prepare(current, fontDirectory)
        }
        for (previewMode in listOf("light", "dark")) {
            val values = session.currentValues.withBoolean("use_system_theme", false).withString("theme_mode", previewMode)
            val colors = resolveThemeColorScheme(context, snapshot.copy(values = values))
            val result = ThemeScriptResolver.resolve(current, themeScriptContext(values, colors, previewMode == "dark", current.manifest.appearance))
            ThemeScriptAssets.validateAppearance(result, prepared)
        }
    }
    suspend fun importSource(uri: Uri) = task {
        val text = withContext(Dispatchers.IO) {
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { ThemeScriptBundleStore.readLimited(it, ThemeScriptContract.MAX_SOURCE_BYTES) }
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        }
        if (selectFile(bundle.manifest.entry)) setSource(text)
    }
    suspend fun importProject(uri: Uri) = task {
        val next = withContext(Dispatchers.IO) {
            val cache = File(context.cacheDir, "theme-transfer").also { require(it.isDirectory || it.mkdirs()) }
            val prepared = ThemeTransferRepository(context).readArchive(uri, cache)
            try { ThemeScriptBundleStore.fromFiles(requireNotNull(prepared.document.script) { "This theme has no studio project" }, prepared.assets) } finally { prepared.close() }
        }
        val prepared = withContext(Dispatchers.IO) { ThemeScriptAssets.prepare(next, fontDirectory, bundle, images) }
        if (edit(decoded = prepared) { next }) { currentFolder = "assets"; refreshSource() }
    }
    suspend fun exportProject(uri: Uri, snapshot: ThemePreferenceSnapshot): Boolean {
        if (sourceDirty && !synchronizeSource()) return false
        return task {
            validateProject(snapshot)
            val avatar = DisplayPreferencesManager.getInstance(context).globalUserAvatarUri.first()
            withContext(Dispatchers.IO) {
                val info = bundle.manifest.studio
                val repository = ThemeTransferRepository(context)
                val source = ThemeTransferSource(targetName, session.currentValues, session.currentLayout, avatar)
                val cache = File(context.cacheDir, "theme-transfer").also { require(it.isDirectory || it.mkdirs()) }
                val archive = repository.createArchive(source, info.name.ifBlank { targetName }, info.author, cache, scriptBundle = bundle)
                try { repository.saveArchive(archive, uri) } finally { archive.delete() }
            }
        }
    }
    suspend fun applyToRole(snapshot: ThemePreferenceSnapshot): Boolean {
        if (sourceDirty && !synchronizeSource()) return false
        var staged: File? = null
        return task {
            try {
                validateProject(snapshot)
                val file = withContext(Dispatchers.IO) { ThemeScriptBundleStore.create(context.filesDir, bundle).also { staged = it } }
                val reference = Uri.fromFile(file).toString()
                session.registerStagedAsset(reference)
                session.setString(ThemePreferenceValues.SCRIPT_URI_KEY, reference)
                staged = null
                withContext(Dispatchers.IO) { draftFile.delete() }
                baseline = bundle
            } finally { staged?.delete() }
        }
    }
}
