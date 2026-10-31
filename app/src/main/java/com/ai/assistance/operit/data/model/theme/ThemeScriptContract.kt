package com.ai.assistance.operit.data.model.theme

import kotlinx.serialization.json.Json

/** 主题脚本只输出外观描述；现有主题参数和原生内容不交给脚本持有。 */
object ThemeScriptContract {
    const val API_VERSION = 1
    const val MAX_BLUR_DP = 8f
    const val MAX_SOURCE_BYTES = 65536
    const val MAX_RESULT_BYTES = 131072
    const val MAX_BUNDLE_BYTES = 16 * 1024 * 1024
    const val MAX_ASSET_BYTES = 4 * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 65536
    val surfaceIds = setOf(
        "background", "userMessage", "assistantMessage", "composer", "userAvatar", "assistantAvatar",
        "appBar", "navigation", "navigationItem", "button", "iconButton", "input",
        "settingsCard", "dialog", "sheet", "menu", "listItem", "status", "toolCard",
    )
    val iconIds = setOf(
        "composer.settings", "composer.attachment", "composer.microphone", "composer.send",
        "composer.stop", "composer.fullscreen", "composer.queue", "composer.expand",
        "appBar.back", "appBar.menu", "appBar.workspace", "appBar.floating", "navigation.item",
    )
    val colorTokens = setOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
        "surfaceTint", "inverseSurface", "inverseOnSurface", "error", "onError", "errorContainer",
        "onErrorContainer", "outline", "outlineVariant", "scrim", "surfaceBright", "surfaceDim",
        "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    )
    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private val assetName = Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")
    private val color = Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")

    fun validate(manifest: ThemeScriptManifest) {
        require(manifest.apiVersion == API_VERSION) { "Unsupported theme script API: ${manifest.apiVersion}" }
        require(isAssetPath(manifest.entry) && manifest.entry.endsWith(".js")) { "Theme script entry must be assets/*.js" }
        require(json.encodeToString(ThemeScriptManifest.serializer(), manifest).toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST_BYTES) { "Theme script manifest is too large" }
        require(manifest.assets.size <= 48) { "Theme script declares too many assets" }
        require(manifest.assets.keys.all { assetName.matches(it) }) { "Invalid theme script asset name" }
        require(manifest.assets.values.all(::isAssetPath)) { "Invalid theme script asset path" }
        require(manifest.entry !in manifest.assets.values) { "Script entry cannot be a drawing asset" }
        val info = manifest.studio
        require(info.name.length <= 128 && info.author.length <= 128 && info.version.length <= 32 && info.description.length <= 2048 && info.license.length <= 8192) { "Theme project metadata is too large" }
        require(info.id.isEmpty() || Regex("[A-Za-z][A-Za-z0-9._-]{0,95}").matches(info.id)) { "Invalid theme project identifier" }
        require(info.folders.size <= 96 && info.folders.distinct().size == info.folders.size && info.folders.all(ThemeAssetPaths::isFolder)) { "Invalid theme project folders" }
        val files = (manifest.assets.values + manifest.entry).toSet()
        require(files.none { file -> files.any { other -> other.startsWith("$file/") } || info.folders.any { folder -> folder == file || folder.startsWith("$file/") } }) { "A resource file conflicts with a folder" }
        validate(manifest.appearance, manifest)
    }

    fun isAssetPath(path: String): Boolean =
        ThemeAssetPaths.isFile(path)

    fun references(manifest: ThemeScriptManifest): Set<String> {
        validate(manifest)
        return manifest.assets.values.toSet() + manifest.entry
    }

    fun decodeResult(text: String, manifest: ThemeScriptManifest): ThemeScriptAppearance {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_RESULT_BYTES) { "Theme script result exceeds size limit" }
        checkJsonDepth(text)
        val result = json.decodeFromString<ThemeScriptAppearance>(text)
        validate(result, manifest)
        return result
    }

    fun decodeManifest(text: String): ThemeScriptManifest {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST_BYTES) { "Theme script manifest is too large" }
        checkJsonDepth(text)
        return json.decodeFromString<ThemeScriptManifest>(text).also(::validate)
    }

    private fun checkJsonDepth(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEach { character ->
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) { "Theme JSON is nested too deeply" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid theme JSON nesting" } }
            }
        }
    }

    fun validate(result: ThemeScriptAppearance, manifest: ThemeScriptManifest) {
        require(manifest.apiVersion == API_VERSION) { "Unsupported theme manifest" }
        require(result.apiVersion == API_VERSION) { "Unsupported theme appearance API" }
        require(result.modes.keys.all { it in setOf("light", "dark") }) { "Unknown theme appearance mode" }
        if (result.modes.isNotEmpty()) {
            validate(result.copy(modes = emptyMap()), manifest)
            result.modes.keys.forEach { mode -> validate(result.forMode(mode), manifest) }
            return
        }
        require(result.surfaces.keys.all { it in surfaceIds }) { "Unknown theme script surface" }
        require(result.icons.keys.all { it in iconIds }) { "Unknown theme icon" }
        result.icons.values.forEach { icon ->
            require(icon.asset in manifest.assets.keys && manifest.assets.getValue(icon.asset).substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "svg")) { "Icon references an undeclared asset" }
            require(icon.size.isFinite() && icon.size in 8f..64f) { "Icon size must be 8..64 dp" }
            require(icon.opacity.isFinite() && icon.opacity in 0f..1f) { "Invalid icon opacity" }
            require(icon.tintColor == null || color.matches(icon.tintColor)) { "Invalid icon tint" }
        }
        listOf(result.palette.light, result.palette.dark).forEach { palette ->
            require(palette.keys.all { it in colorTokens } && palette.values.all(color::matches)) { "Invalid theme palette" }
        }
        validateText(result.typography, manifest)
        require(result.surfaces.values.sumOf { it.layers.size } <= 128) { "Theme script declares too many layers" }
        result.surfaces.forEach { (id, surface) ->
            validateText(surface.textStyle, manifest)
            require(surface.opacity.isFinite() && surface.opacity in 0f..1f) { "Invalid surface opacity" }
            require(surface.states.keys.all { it in setOf("pressed", "focused", "selected", "disabled", "error") }) { "Unknown surface state" }
            surface.states.values.forEach { state ->
                require(listOfNotNull(state.fillColor, state.contentColor, state.strokeColor).all(color::matches)) { "Invalid state color" }
                require(state.opacity.isFinite() && state.opacity in 0f..1f) { "Invalid state opacity" }
                state.strokeWidth?.let { require(it.isFinite() && it in 0f..16f) { "Invalid state stroke" } }
            }
            surface.shape?.let {
                require(it.type in setOf("rounded", "cut", "circle") && it.radius.isFinite() && it.radius in 0f..128f) { "Invalid surface shape" }
            }
            require(listOfNotNull(surface.fillColor, surface.contentColor, surface.strokeColor).all(color::matches)) { "Invalid surface color" }
            require(surface.strokeWidth.isFinite() && surface.strokeWidth in 0f..16f) { "Invalid surface stroke" }
            surface.elevation?.let { require(it.isFinite() && it in 0f..24f) { "Invalid surface elevation" } }
            require(surface.minHeight.isFinite() && surface.minHeight in 0f..256f) { "Invalid surface height" }
            surface.size?.let { require(id in setOf("userAvatar", "assistantAvatar") && it.isFinite() && it in 16f..96f) { "Invalid avatar size" } }
            require(surface.messageWidth.isFinite() && surface.messageWidth in 0.3f..1f) { "Invalid message width" }
            require(id in setOf("userMessage", "assistantMessage") || surface.messageWidth == 1f) { "Width fraction belongs to messages" }
            val inset = surface.padding
            require(listOf(inset.start, inset.top, inset.end, inset.bottom).all { it.isFinite() && it in 0f..48f }) {
                "Theme script padding is outside 0..48 dp"
            }
            require(id != "background" || inset == ThemeScriptInsets()) { "Background cannot add content padding" }
            surface.layers.forEach { layer ->
                require(layer.type in setOf("rect", "ellipse", "line", "image", "nineSlice")) { "Unknown theme layer type" }
                require(layer.placement in setOf("background", "foreground")) { "Unknown layer placement" }
                require(layer.state in setOf("always", "pressed", "focused", "selected", "disabled", "error")) { "Unknown layer state" }
                require(layer.fit in setOf("fill", "fit", "crop", "tile")) { "Unknown image fit" }
                require(layer.sampling in setOf("smooth", "nearest")) { "Unknown image sampling" }
                require(layer.alignment in setOf("center", "top", "bottom", "start", "end")) { "Unknown image alignment" }
                require(layer.blur.isFinite() && layer.blur in 0f..MAX_BLUR_DP) { "Invalid layer blur" }
                require(layer.opacity.isFinite() && layer.opacity in 0f..1f) { "Invalid layer opacity" }
                require(layer.radius.isFinite() && layer.radius in 0f..128f) { "Invalid layer radius" }
                require(layer.strokeWidth.isFinite() && layer.strokeWidth in 0f..16f) { "Invalid layer stroke" }
                val b = layer.bounds
                // 平移与尺寸独立；满幅素材越过区域边缘时由绘制容器裁剪。
                require(listOf(b.x, b.y).all { it.isFinite() && it in -1f..1f }) { "Layer position must be normalized" }
                require(listOf(b.width, b.height).all { it.isFinite() && it > 0f && it <= 1f }) { "Layer size must be normalized" }
                require(layer.color == null || color.matches(layer.color)) { "Invalid layer color" }
                require(layer.strokeColor == null || color.matches(layer.strokeColor)) { "Invalid stroke color" }
                require(layer.id == null || assetName.matches(layer.id)) { "Invalid layer identifier" }
                require(listOf(layer.slices.start, layer.slices.top, layer.slices.end, layer.slices.bottom).all { it.isFinite() && it in 0f..2048f }) { "Invalid nine-slice insets" }
                if (layer.type in setOf("image", "nineSlice")) {
                    require(layer.asset in manifest.assets.keys && manifest.assets.getValue(requireNotNull(layer.asset)).substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "svg")) { "Layer references an undeclared asset" }
                    require(layer.color == null && layer.strokeColor == null) { "Image layers cannot declare paint colors" }
                } else {
                    require(layer.asset == null) { "Only image layers can declare an asset" }
                    require(layer.color != null || layer.strokeColor != null) { "Paint layer has no color" }
                }
            }
        }
    }
    private fun validateText(text: ThemeAppearanceText, manifest: ThemeScriptManifest) {
        require(text.color == null || color.matches(text.color)) { "Invalid text color" }
        val family = text.family
        if (family != null && family.startsWith("asset:")) {
            val path = requireNotNull(manifest.assets[family.removePrefix("asset:")]) { "Font references an undeclared asset" }
            require(path.substringAfterLast('.').lowercase() in setOf("ttf", "otf")) { "Expected a TTF or OTF font resource" }
        } else require(family == null || family in setOf("default", "sans-serif", "serif", "monospace", "cursive")) { "Unsupported font family" }
        require(text.scale.isFinite() && text.scale in 0.5f..2f) { "Invalid text scale" }
        require(text.lineHeight.isFinite() && text.lineHeight in 0.8f..2f) { "Invalid line height" }
        require(text.letterSpacing.isFinite() && text.letterSpacing in -2f..8f) { "Invalid letter spacing" }
    }
}
