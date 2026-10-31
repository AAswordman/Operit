package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.slider.WorkbenchSlider
import com.ai.assistance.operit.data.model.theme.*
import com.ai.assistance.operit.ui.theme.script.ThemeScriptImage
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

internal fun studioHex(value: Int): String = String.format(Locale.ROOT, "#%08X", value)

@Composable
internal fun StudioColorChip(value: Color?, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) {
        val tile = 5.dp.toPx()
        for (x in 0..(size.width / tile).toInt()) for (y in 0..(size.height / tile).toInt()) drawRect(if ((x + y) % 2 == 0) Color(0xFF686C78) else Color(0xFFADB1BE), Offset(x * tile, y * tile), androidx.compose.ui.geometry.Size(tile, tile))
        if (value != null) drawRect(value)
    }
}

@Composable
internal fun StudioColorRow(state: ThemeStudioState, label: String, field: String, onPick: (String, String, String?) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        for (mode in listOf("light", "dark")) {
            val value = state.color(field, mode)
            val available = state.target.kind != StudioKind.Icon || state.icon(forMode = mode) != null
            Row(Modifier.width(102.dp).padding(start = 4.dp).border(1.dp, if (state.mode == mode) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)).clickable(enabled = available) { onPick(field, mode, value) }.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioColorChip(value?.let { Color(android.graphics.Color.parseColor(it)) })
                Text(value?.removePrefix("#") ?: stringResource(if (available) R.string.studio_inherit else R.string.studio_unbound), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
    HorizontalDivider(color = StudioLine.copy(alpha = .6f))
}

@Composable
internal fun StudioColorHead() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.studio_color), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.studio_light), Modifier.width(102.dp), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.studio_dark), Modifier.width(102.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun StudioColorPicker(
    state: ThemeStudioState?,
    title: String,
    initial: String?,
    seed: Color,
    onChange: (String?) -> Unit,
    onClose: () -> Unit,
) {
    val start = remember { initial?.let(android.graphics.Color::parseColor) ?: seed.toArgb() }
    val initialHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start, it) } }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var alpha by remember { mutableStateOf(android.graphics.Color.alpha(start).toFloat()) }
    var hex by remember { mutableStateOf(studioHex(start)) }
    var channels by remember { mutableStateOf(listOf(android.graphics.Color.red(start), android.graphics.Color.green(start), android.graphics.Color.blue(start), android.graphics.Color.alpha(start)).map(Int::toString)) }
    var invalid by remember { mutableStateOf(false) }
    var chooseSample by remember { mutableStateOf(false) }
    var sample by remember { mutableStateOf<String?>(null) }
    fun current() = android.graphics.Color.HSVToColor(alpha.roundToInt().coerceIn(0, 255), floatArrayOf(hue, saturation, brightness))
    fun syncInputs() {
        val value = current(); hex = studioHex(value)
        channels = listOf(android.graphics.Color.red(value), android.graphics.Color.green(value), android.graphics.Color.blue(value), android.graphics.Color.alpha(value)).map(Int::toString)
        invalid = false
    }
    fun setColor(value: Int, hexInput: String? = null, channelInput: List<String>? = null) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(value, hsv)
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; alpha = android.graphics.Color.alpha(value).toFloat()
        syncInputs()
        if (hexInput != null) hex = hexInput
        if (channelInput != null) channels = channelInput
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.fillMaxWidth().height(170.dp).pointerInput(hue) {
                awaitEachGesture {
                    fun update(position: Offset) { saturation = (position.x / size.width).coerceIn(0f, 1f); brightness = 1f - (position.y / size.height).coerceIn(0f, 1f); syncInputs() }
                    val down = awaitFirstDown(); down.consume(); update(down.position)
                    drag(down.id) { change -> change.consume(); update(change.position) }
                }
            }) {
                drawRect(Color.hsv(hue, 1f, 1f)); drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent))); drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(saturation * size.width, (1f - brightness) * size.height), style = Stroke(2.dp.toPx()))
            }
            Text(stringResource(R.string.studio_hue), style = MaterialTheme.typography.labelSmall)
            WorkbenchSlider(hue, { hue = it; syncInputs() }, valueRange = 0f..359.9f)
            Text(stringResource(R.string.studio_alpha), style = MaterialTheme.typography.labelSmall)
            WorkbenchSlider(alpha, { alpha = it; syncInputs() }, valueRange = 0f..255f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioColorChip(Color(start)); Text("→"); StudioColorChip(Color(current()))
                OutlinedTextField(hex, { value ->
                    hex = value
                    invalid = !Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(value)
                    if (!invalid) setColor(android.graphics.Color.parseColor(value), hexInput = value)
                }, singleLine = true, isError = invalid, label = { Text("ARGB") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("R", "G", "B", "A").forEachIndexed { index, label ->
                    OutlinedTextField(channels[index], { value ->
                        channels = channels.mapIndexed { i, old -> if (i == index) value else old }
                        val values = channels.map(String::toIntOrNull)
                        invalid = values.any { it == null || it !in 0..255 }
                        if (!invalid) setColor(android.graphics.Color.argb(requireNotNull(values[3]), requireNotNull(values[0]), requireNotNull(values[1]), requireNotNull(values[2])), channelInput = channels)
                    }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Color.White, Color(0xFFFAF2E1), Color(0xFF556A53), Color(0xFFE2D6EE), Color.Black, Color.Transparent).forEach { color -> Box(Modifier.clickable { setColor(color.toArgb()) }) { StudioColorChip(color) } }
            }
            if (state != null) TextButton(onClick = { chooseSample = true }) { Text(stringResource(R.string.studio_sample_image)) }
        }
    }, confirmButton = { Button(onClick = { onChange(studioHex(current())); onClose() }, enabled = !invalid) { Text(stringResource(R.string.studio_apply_color)) } }, dismissButton = {
        Row { TextButton(onClick = { onChange(null); onClose() }) { Text(stringResource(R.string.studio_inherit)) }; TextButton(onClick = onClose) { Text(stringResource(R.string.studio_cancel)) } }
    })
    if (chooseSample && state != null) StudioAssetPicker(state, fontsOnly = false, title = stringResource(R.string.studio_sample_image), onClose = { chooseSample = false }, onChoose = { id -> chooseSample = false; sample = id })
    if (state != null) sample?.let { id -> StudioImageSampler(state.images.getValue(id), onClose = { sample = null }, onPicked = { value -> setColor(value); sample = null }) }
}

@Composable
internal fun StudioImageSampler(image: ThemeScriptImage, onClose: () -> Unit, onPicked: (Int) -> Unit) {
    val bitmap = remember(image) { when (image) {
        is ThemeScriptImage.Bitmap -> image.image.asAndroidBitmap()
        is ThemeScriptImage.Vector -> Bitmap.createBitmap(image.picture.width, image.picture.height, Bitmap.Config.ARGB_8888).also { AndroidCanvas(it).drawPicture(image.picture) }
        is ThemeScriptImage.Font -> error("Cannot sample a font resource")
    } }
    var picked by remember { mutableStateOf<Int?>(null) }
    StudioFullSheet(stringResource(R.string.studio_sample_image), onClose) {
        StudioHint(stringResource(R.string.studio_sample_hint))
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(bitmap) {
            detectTapGestures { point ->
                val scale = min(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                val left = (size.width - bitmap.width * scale) / 2f; val top = (size.height - bitmap.height * scale) / 2f
                val x = floor((point.x - left) / scale).toInt(); val y = floor((point.y - top) / scale).toInt()
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) picked = bitmap.getPixel(x, y)
            }
        }) {
            val scale = min(size.width / bitmap.width, size.height / bitmap.height)
            val w = (bitmap.width * scale).roundToInt(); val h = (bitmap.height * scale).roundToInt()
            drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()), dstSize = IntSize(w, h))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioColorChip(picked?.let(::Color)); Text(picked?.let(::studioHex).orEmpty(), Modifier.weight(1f))
            Button(onClick = { onPicked(requireNotNull(picked)) }, enabled = picked != null) { Text(stringResource(R.string.studio_use_color)) }
        }
    }
}
