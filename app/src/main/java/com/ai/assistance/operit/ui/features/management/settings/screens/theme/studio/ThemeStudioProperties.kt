package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.data.model.theme.*
import com.ai.assistance.operit.ui.theme.script.ThemeScriptImage

private data class StudioColorRequest(val title: String, val initial: String?, val seed: Color, val apply: (String?) -> Unit)

@Composable
internal fun StudioProperties(state: ThemeStudioState) {
    val context = LocalContext.current
    val title = state.target.label()
    val layerSeed = StudioAccent
    var colorRequest by remember { mutableStateOf<StudioColorRequest?>(null) }
    var fontPicker by remember { mutableStateOf(false) }
    var bindIcon by remember { mutableStateOf(false) }
    var layerType by remember { mutableStateOf<String?>(null) }
    var addLayerMenu by remember { mutableStateOf(false) }
    var replaceLayer by remember { mutableStateOf<Int?>(null) }
    var nineIndex by remember { mutableStateOf<Int?>(null) }
    var paletteQuery by remember(state.target.id) { mutableStateOf("") }
    fun color(field: String, mode: String, initial: String?) {
        val label = context.getString(if (mode == "light") R.string.studio_light else R.string.studio_dark)
        colorRequest = StudioColorRequest("$title · $label", initial, Color.White) { value -> state.setColor(field, value, mode) }
    }
    fun layerColor(index: Int, field: String, initial: String?) {
        colorRequest = StudioColorRequest(title, initial, layerSeed) { value -> state.updateLayer(index) { if (field == "color") it.copy(color = value) else it.copy(strokeColor = value) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 2.dp)) {
        if (state.property == StudioProperty.States) {
            StudioSection(stringResource(R.string.studio_preview_state)) {
                StudioChoices(state.previewState, (listOf("always") + state.target.states).map { it to studioStateLabel(it) }) { state.previewState = it }
                StudioHint(stringResource(R.string.studio_state_inherit_hint))
            }
            StudioSection(stringResource(R.string.studio_state_colors)) {
                StudioColorHead()
                StudioColorRow(state, stringResource(R.string.studio_fill_color), "fillColor", ::color)
                if (state.target.supports(StudioSurfaceControl.ContentColor)) StudioColorRow(state, stringResource(R.string.studio_content_color), "contentColor", ::color)
                if (state.target.supports(StudioSurfaceControl.Stroke)) StudioColorRow(state, stringResource(R.string.studio_stroke_color), "strokeColor", ::color)
                if (state.previewState != "always") {
                    val active = state.surface().states[state.previewState] ?: ThemeAppearanceState()
                    StudioSlider(state, stringResource(R.string.studio_opacity), active.opacity, 0f..1f) { value -> state.updateSurface { it.copy(states = it.states + (state.previewState to (it.states[state.previewState] ?: ThemeAppearanceState()).copy(opacity = value))) } }
                    if (state.target.supports(StudioSurfaceControl.Stroke)) StudioSlider(state, stringResource(R.string.studio_stroke_width), active.strokeWidth ?: state.surface().strokeWidth, 0f..16f, "dp") { value -> state.updateSurface { it.copy(states = it.states + (state.previewState to (it.states[state.previewState] ?: ThemeAppearanceState()).copy(strokeWidth = value))) } }
                    TextButton(onClick = { state.updateSurface { it.copy(states = it.states - state.previewState) } }) { Text(stringResource(R.string.studio_clear_state)) }
                }
            }
        } else if (state.target.kind == StudioKind.Palette) {
            StudioSection(stringResource(R.string.studio_palette)) {
                OutlinedTextField(paletteQuery, { paletteQuery = it }, singleLine = true, placeholder = { Text(stringResource(R.string.studio_search_colors)) }, modifier = Modifier.fillMaxWidth())
                StudioColorHead()
                ThemeScriptContract.colorTokens.filter { token -> token.contains(paletteQuery, true) || context.getString(ThemeStudioPaletteLabels.getValue(token)).contains(paletteQuery, true) }.forEach { token -> StudioColorRow(state, stringResource(ThemeStudioPaletteLabels.getValue(token)), token, ::color) }
            }
        } else if (state.target.kind == StudioKind.Typography) {
            StudioTextProperties(state, ::color) { fontPicker = true }
        } else if (state.target.kind == StudioKind.Icon) {
            val icon = state.icon()
            StudioSection(stringResource(R.string.studio_icon_binding)) {
                TextButton(onClick = { bindIcon = true }) { Text(icon?.asset?.let { state.bundle.manifest.assets.getValue(it).substringAfterLast('/') } ?: stringResource(R.string.studio_choose_icon)) }
                if (icon != null) {
                    StudioAssetThumbnail(state.images.getValue(icon.asset), Modifier.size(72.dp))
                    StudioSlider(state, stringResource(R.string.studio_icon_size), icon.size, 8f..64f, "dp") { value -> state.setIcon(requireNotNull(state.icon()).copy(size = value)) }
                    StudioSlider(state, stringResource(R.string.studio_opacity), icon.opacity, 0f..1f) { value -> state.setIcon(requireNotNull(state.icon()).copy(opacity = value)) }
                    StudioColorHead(); StudioColorRow(state, stringResource(R.string.studio_icon_tint), "tintColor", ::color)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.studio_use_content_tint)); Switch(icon.useContentTint, { state.setIcon(icon.copy(useContentTint = it, tintColor = if (it) null else icon.tintColor)) }) }
                    TextButton(onClick = { state.setIcon(null) }) { Text(stringResource(R.string.studio_use_builtin)) }
                }
            }
        } else if (state.property == StudioProperty.Layers) {
            val surface = state.surface()
            StudioSection(stringResource(R.string.studio_fill_color)) { StudioColorHead(); StudioColorRow(state, stringResource(R.string.studio_fill_color), "fillColor", ::color) }
            surface.layers.forEachIndexed { index, layer ->
                key(layer.id, index) {
                    StudioSection((index + 1).toString() + " · " + (layer.asset?.let { state.bundle.manifest.assets.getValue(it).substringAfterLast('/') } ?: layer.type)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { state.moveLayer(index, -1) }, enabled = index > 0) { Text(stringResource(R.string.studio_layer_down)) }
                            TextButton(onClick = { state.moveLayer(index, 1) }, enabled = index < surface.layers.lastIndex) { Text(stringResource(R.string.studio_layer_up)) }
                            TextButton(onClick = { state.deleteLayer(index) }) { Text(stringResource(R.string.studio_delete)) }
                        }
                        StudioChoices(layer.placement, listOf("background" to stringResource(R.string.studio_background_layer), "foreground" to stringResource(R.string.studio_foreground_layer))) { value -> state.updateLayer(index) { it.copy(placement = value) } }
                        StudioSlider(state, stringResource(R.string.studio_opacity), layer.opacity, 0f..1f) { value -> state.updateLayer(index) { it.copy(opacity = value) } }
                        if (layer.type in setOf("image", "nineSlice")) {
                            StudioAssetThumbnail(state.images.getValue(requireNotNull(layer.asset)), Modifier.fillMaxWidth().height(86.dp))
                            TextButton(onClick = { replaceLayer = index }) { Text(stringResource(R.string.studio_replace_image)) }
                            if (layer.type == "image") {
                                StudioChoices(layer.fit, listOf("crop" to stringResource(R.string.studio_fit_crop), "fit" to stringResource(R.string.studio_fit_contain), "fill" to stringResource(R.string.studio_fit_fill), "tile" to stringResource(R.string.studio_fit_tile))) { value -> state.updateLayer(index) { it.copy(fit = value) } }
                                StudioChoices(layer.alignment, listOf("center" to stringResource(R.string.studio_align_center), "top" to stringResource(R.string.studio_top), "bottom" to stringResource(R.string.studio_bottom), "start" to stringResource(R.string.studio_start), "end" to stringResource(R.string.studio_end))) { value -> state.updateLayer(index) { it.copy(alignment = value) } }
                            }
                            StudioChoices(layer.sampling, listOf("smooth" to stringResource(R.string.studio_sampling_smooth), "nearest" to stringResource(R.string.studio_sampling_nearest))) { value -> state.updateLayer(index) { it.copy(sampling = value) } }
                            if (Build.VERSION.SDK_INT >= 31) StudioSlider(state, stringResource(R.string.studio_blur), layer.blur, 0f..ThemeScriptContract.MAX_BLUR_DP, "dp") { value -> state.updateLayer(index) { it.copy(blur = value) } }
                            if (state.images[layer.asset] is ThemeScriptImage.Bitmap) {
                                StudioChoices(layer.type, listOf("image" to stringResource(R.string.studio_image_layer), "nineSlice" to stringResource(R.string.studio_nine_slice))) { value -> state.updateLayer(index) { it.copy(type = value) } }
                                if (layer.type == "nineSlice") TextButton(onClick = { nineIndex = index }) { Text(stringResource(R.string.studio_nine_slice)) }
                            }
                        } else {
                            StudioSingleColor(stringResource(R.string.studio_fill_color), layer.color) { layerColor(index, "color", layer.color) }
                            StudioSingleColor(stringResource(R.string.studio_stroke_color), layer.strokeColor) { layerColor(index, "strokeColor", layer.strokeColor) }
                            StudioSlider(state, stringResource(R.string.studio_stroke_width), layer.strokeWidth, 0f..16f, "dp") { value -> state.updateLayer(index) { it.copy(strokeWidth = value) } }
                            if (layer.type == "rect") StudioSlider(state, stringResource(R.string.studio_corner_radius), layer.radius, 0f..128f, "dp") { value -> state.updateLayer(index) { it.copy(radius = value) } }
                        }
                        if (state.target.states.isNotEmpty()) StudioChoices(layer.state, (listOf("always") + state.target.states).map { it to studioStateLabel(it) }) { value -> state.updateLayer(index) { it.copy(state = value) } }
                        StudioSlider(state, stringResource(R.string.studio_layer_x), layer.bounds.x * 100, -100f..100f, "%") { value -> state.updateLayer(index) { it.copy(bounds = it.bounds.copy(x = value / 100)) } }
                        StudioSlider(state, stringResource(R.string.studio_layer_y), layer.bounds.y * 100, -100f..100f, "%") { value -> state.updateLayer(index) { it.copy(bounds = it.bounds.copy(y = value / 100)) } }
                        StudioSlider(state, stringResource(R.string.studio_layer_width), layer.bounds.width * 100, 1f..100f, "%") { value -> state.updateLayer(index) { it.copy(bounds = it.bounds.copy(width = value / 100)) } }
                        StudioSlider(state, stringResource(R.string.studio_layer_height), layer.bounds.height * 100, 1f..100f, "%") { value -> state.updateLayer(index) { it.copy(bounds = it.bounds.copy(height = value / 100)) } }
                    }
                }
            }
            Box {
                TextButton(onClick = { addLayerMenu = true }) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.studio_add_layer)) }
                WorkbenchDropdownMenu(expanded = addLayerMenu, onDismissRequest = { addLayerMenu = false }) {
                    listOf("image" to R.string.studio_image_layer, "nineSlice" to R.string.studio_nine_slice, "rect" to R.string.studio_rect_layer, "ellipse" to R.string.studio_ellipse_layer, "line" to R.string.studio_line_layer).forEach { (type, label) ->
                        ActionSheetItem(label = stringResource(label), icon = if (type in setOf("image", "nineSlice")) Icons.Default.Image else Icons.Default.Palette, onClick = {
                            addLayerMenu = false
                            if (type in setOf("image", "nineSlice")) layerType = type
                            else state.updateSurface { it.copy(layers = it.layers + ThemeScriptLayer(type, color = "#80D1B5FF", strokeWidth = if (type == "line") 2f else 0f)) }
                        })
                    }
                }
            }
            StudioHint(stringResource(R.string.studio_layer_opacity_hint))
        } else {
            StudioSurfaceProperties(state, onColor = ::color, onFont = { fontPicker = true })
        }
    }
    colorRequest?.let { request -> StudioColorPicker(state, request.title, request.initial, request.seed, onChange = request.apply, onClose = { colorRequest = null }) }
    if (fontPicker) StudioAssetPicker(state, fontsOnly = true, title = stringResource(R.string.studio_choose_font), onClose = { fontPicker = false }) { id -> state.updateText { it.copy(family = "asset:$id") }; fontPicker = false }
    if (bindIcon) StudioAssetPicker(state, fontsOnly = false, title = stringResource(R.string.studio_choose_icon), onClose = { bindIcon = false }) { id -> state.setIcon(state.icon()?.copy(asset = id) ?: ThemeAppearanceIcon(id)); bindIcon = false }
    layerType?.let { type -> StudioAssetPicker(state, fontsOnly = false, title = stringResource(R.string.studio_choose_image), onClose = { layerType = null }, allowed = { id -> type != "nineSlice" || state.images[id] is ThemeScriptImage.Bitmap }) { id ->
        state.updateSurface { surface -> surface.copy(layers = surface.layers + ThemeScriptLayer(type, asset = id, fit = "crop")) }; layerType = null
    } }
    replaceLayer?.let { index ->
        StudioAssetPicker(state, fontsOnly = false, title = stringResource(R.string.studio_choose_image), onClose = { replaceLayer = null }, allowed = { id -> state.surface().layers[index].type != "nineSlice" || state.images[id] is ThemeScriptImage.Bitmap }) { id ->
            state.updateLayer(index) { it.copy(asset = id, slices = ThemeScriptInsets()) }
            replaceLayer = null
        }
    }
    nineIndex?.let { index -> StudioNineSliceEditor(state, index, onClose = { nineIndex = null }) }
}

@Composable
private fun StudioSingleColor(label: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        StudioColorChip(value?.let { Color(android.graphics.Color.parseColor(it)) }); Text(label, Modifier.weight(1f)); StudioHint(value ?: stringResource(R.string.studio_inherit))
    }
}

@Composable
private fun StudioTextProperties(state: ThemeStudioState, onColor: (String, String, String?) -> Unit, onFont: () -> Unit) {
    val text = if (state.target.kind == StudioKind.Typography) state.appearance.typography else state.surface().textStyle
    StudioSection(stringResource(R.string.studio_font)) {
        StudioColorHead()
        StudioColorRow(state, stringResource(R.string.studio_content_color), "textColor", onColor)
        StudioChoices(text.family, listOf(null to stringResource(R.string.studio_inherit), "default" to stringResource(R.string.studio_system_font), "sans-serif" to stringResource(R.string.studio_font_sans), "serif" to stringResource(R.string.studio_font_serif), "monospace" to stringResource(R.string.studio_font_mono), "cursive" to stringResource(R.string.studio_font_cursive))) { value -> state.updateText { it.copy(family = value) } }
        TextButton(onClick = onFont) { Text(text.family?.takeIf { it.startsWith("asset:") }?.removePrefix("asset:")?.let { state.bundle.manifest.assets.getValue(it).substringAfterLast('/') } ?: stringResource(R.string.studio_choose_font)) }
        StudioSlider(state, stringResource(R.string.studio_font_scale), text.scale, .5f..2f) { value -> state.updateText { it.copy(scale = value) } }
        StudioSlider(state, stringResource(R.string.studio_line_height), text.lineHeight, .8f..2f) { value -> state.updateText { it.copy(lineHeight = value) } }
        StudioSlider(state, stringResource(R.string.studio_letter_spacing), text.letterSpacing, -2f..8f, "sp") { value -> state.updateText { it.copy(letterSpacing = value) } }
    }
}

@Composable
private fun StudioSurfaceProperties(state: ThemeStudioState, onColor: (String, String, String?) -> Unit, onFont: () -> Unit) {
    val s = state.surface()
    val target = state.target
    StudioSection(stringResource(R.string.studio_colors)) {
        StudioColorHead()
        StudioColorRow(state, stringResource(R.string.studio_fill_color), "fillColor", onColor)
        if (target.supports(StudioSurfaceControl.ContentColor)) StudioColorRow(state, stringResource(R.string.studio_content_color), "contentColor", onColor)
        if (target.supports(StudioSurfaceControl.Stroke)) StudioColorRow(state, stringResource(R.string.studio_stroke_color), "strokeColor", onColor)
        StudioSlider(state, stringResource(R.string.studio_opacity), s.opacity, 0f..1f) { value -> state.updateSurface { it.copy(opacity = value) } }
    }
    if (target.supports(StudioSurfaceControl.Shape)) {
        StudioSection(stringResource(R.string.studio_shape_border)) {
            val shape = when { s.shape == null -> "inherit"; s.shape.type == "rounded" && s.shape.radius == 0f -> "rect"; else -> s.shape.type }
            StudioChoices(shape, listOf("inherit" to stringResource(R.string.studio_inherit), "rounded" to stringResource(R.string.studio_rounded), "rect" to stringResource(R.string.studio_square), "cut" to stringResource(R.string.studio_cut), "circle" to stringResource(R.string.studio_circle))) { value ->
                state.updateSurface { it.copy(shape = when (value) { "inherit" -> null; "rect" -> ThemeAppearanceShape("rounded", 0f); else -> ThemeAppearanceShape(value, it.shape?.radius?.takeIf { radius -> radius > 0f } ?: 16f) }) }
            }
            val configuredShape = s.shape
            if (configuredShape != null && configuredShape.type in setOf("rounded", "cut")) {
                StudioSlider(state, stringResource(R.string.studio_corner_radius), configuredShape.radius, 0f..128f, "dp") { value -> state.updateSurface { it.copy(shape = configuredShape.copy(radius = value)) } }
            }
            if (target.supports(StudioSurfaceControl.Stroke)) {
                StudioSlider(state, stringResource(R.string.studio_stroke_width), s.strokeWidth, 0f..16f, "dp") { value -> state.updateSurface { it.copy(strokeWidth = value) } }
                StudioHint(stringResource(R.string.studio_stroke_hint))
            }
            if (target.supports(StudioSurfaceControl.MinHeight)) StudioSlider(state, stringResource(R.string.studio_min_height), s.minHeight, 0f..256f, "dp") { value -> state.updateSurface { it.copy(minHeight = value) } }
            if (target.supports(StudioSurfaceControl.Elevation)) StudioSlider(state, stringResource(R.string.studio_elevation), s.elevation ?: 0f, 0f..24f, "dp") { value -> state.updateSurface { it.copy(elevation = value) } }
        }
    }
    if (target.supports(StudioSurfaceControl.AvatarSize)) StudioSection(stringResource(R.string.studio_avatar_size)) {
        StudioSlider(state, stringResource(R.string.studio_avatar_size), s.size ?: 27f, 16f..96f, "dp") { value -> state.updateSurface { it.copy(size = value) } }
    }
    if (target.supports(StudioSurfaceControl.Padding)) {
        StudioSection(stringResource(R.string.studio_padding)) {
            var linked by remember(target.id) { mutableStateOf(true) }
            StudioChoices(linked, listOf(true to stringResource(R.string.studio_link_padding), false to stringResource(R.string.studio_separate_padding))) { linked = it }
            listOf("top" to R.string.studio_top, "end" to R.string.studio_end, "bottom" to R.string.studio_bottom, "start" to R.string.studio_start).forEach { (side, label) ->
                val current = when (side) { "top" -> s.padding.top; "end" -> s.padding.end; "bottom" -> s.padding.bottom; else -> s.padding.start }
                StudioSlider(state, stringResource(label), current, 0f..48f, "dp") { value -> state.updateSurface {
                    val padding = if (linked) ThemeScriptInsets(value, value, value, value) else when (side) { "top" -> it.padding.copy(top = value); "end" -> it.padding.copy(end = value); "bottom" -> it.padding.copy(bottom = value); else -> it.padding.copy(start = value) }
                    it.copy(padding = padding)
                } }
            }
        }
    }
    if (target.supports(StudioSurfaceControl.PopupOffset)) {
        StudioSection(stringResource(R.string.studio_popup_position)) {
            listOf("up" to R.string.studio_popup_up, "down" to R.string.studio_popup_down, "left" to R.string.studio_popup_left).forEach { (direction, label) ->
                val current = when (direction) { "up" -> s.popupOffset.up; "down" -> s.popupOffset.down; else -> s.popupOffset.left }
                StudioSlider(state, stringResource(label), current, 0f..ThemeScriptContract.MAX_POPUP_OFFSET_DP, "dp") { value -> state.updateSurface {
                    val offset = when (direction) { "up" -> it.popupOffset.copy(up = value); "down" -> it.popupOffset.copy(down = value); else -> it.popupOffset.copy(left = value) }
                    it.copy(popupOffset = offset)
                } }
            }
            StudioHint(stringResource(R.string.studio_popup_position_hint))
        }
    }
    if (target.supports(StudioSurfaceControl.MessageWidth)) StudioSection(stringResource(R.string.studio_message_layout)) {
        StudioSlider(state, stringResource(R.string.studio_message_width), s.messageWidth * 100f, 30f..100f, "%") { value -> state.updateSurface { it.copy(messageWidth = value / 100f) } }
    }
    if (target.supports(StudioSurfaceControl.Typography)) StudioTextProperties(state, onColor, onFont)
    if (target.id == "background") StudioHint(stringResource(R.string.studio_background_image_hint))
}
