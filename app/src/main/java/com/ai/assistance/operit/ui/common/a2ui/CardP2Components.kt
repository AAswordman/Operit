package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.displays.JLatexMathCompatibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.noties.jlatexmath.JLatexMathDrawable

internal object CardStepper : A2uiComponent {
    override val name = "Stepper"
    override val description = "Connected steps. Completed steps remain selectable."
    private val labels = A2uiProperty.dynamicStringList("labels")
    private val current = A2uiProperty.dynamicNumber("current")
    private val label = A2uiProperty.dynamicString("label")
    private val disabled = A2uiProperty.dynamicBoolean("disabled")
    override val properties = listOf(labels, current, label, disabled, A2uiProperty.any("onChange"))
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val steps = properties.bind(labels).orEmpty()
        val rawIndex = properties.bind(current)?.toDouble()
        if (steps.size !in 1..32 || rawIndex == null || !rawIndex.isFinite() ||
            rawIndex % 1.0 != 0.0 || rawIndex < 0 || rawIndex >= steps.size) {
            Text(stringResource(R.string.a2ui_invalid), modifier, color = MaterialTheme.colorScheme.error)
            return
        }
        val index = rawIndex.toInt()
        val update = properties.bindUpdater(current)
        val enabled = LocalCardInteractive.current && properties.bind(disabled) != true && update != null
        val change = cardChangeHandler(properties, "current")
        Column(modifier.fillMaxWidth()) {
            Text(properties.bind(label).orEmpty(), style = MaterialTheme.typography.titleSmall)
            steps.take(32).forEachIndexed { i, text ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(i == index,
                    enabled = enabled, role = Role.Tab,
                    onClick = { if (enabled) change(index, i, i) { update?.invoke(i) } }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.size(32.dp), shape = MaterialTheme.shapes.extraLarge,
                        color = if (i <= index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(contentAlignment = Alignment.Center) { Text(if (i < index) "✓" else "${i + 1}") }
                    }
                    Text(text, Modifier.weight(1f).padding(vertical = 8.dp),
                        color = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                if (i < steps.lastIndex) VerticalDivider(Modifier.padding(start = 15.dp).height(12.dp))
            }
        }
    }
}

internal object CardFormula : A2uiComponent {
    override val name = "Formula"
    override val description = "Bounded native mathematical notation; no macros or external resources."
    private val text = A2uiProperty.dynamicString("text")
    private val label = A2uiProperty.dynamicString("label")
    override val properties = listOf(text, label)
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val source = properties.bind(text).orEmpty()
        val title = properties.bind(label).orEmpty()
        val color = MaterialTheme.colorScheme.onSurface.toArgb()
        val size = with(LocalDensity.current) { 20.sp.toPx() }
        var finished by remember(source, color, size) { mutableStateOf(false) }
        var drawable by remember(source, color, size) { mutableStateOf<JLatexMathDrawable?>(null) }
        LaunchedEffect(source, color, size) {
            drawable = withContext(Dispatchers.Default) {
                if (!validCardFormula(source)) null else runCatching {
                    JLatexMathCompatibility.ensureRegistered()
                    JLatexMathDrawable.builder(source).textSize(size).padding(2).color(color).build()
                        .takeIf { it.intrinsicWidth in 1..4096 && it.intrinsicHeight in 1..2048 }
                }.getOrNull()
            }
            finished = true
        }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            val image = drawable
            when {
                image != null -> Box(Modifier.horizontalScroll(rememberScrollState())) {
                    val density = LocalDensity.current
                    Canvas(Modifier.size(with(density) { image.intrinsicWidth.toDp() },
                        with(density) { image.intrinsicHeight.toDp() })
                        .semantics { contentDescription = title.ifBlank { source } }) {
                        image.setBounds(0, 0, image.intrinsicWidth, image.intrinsicHeight)
                        drawContext.canvas.nativeCanvas.let(image::draw)
                    }
                }
                finished -> Text(stringResource(R.string.a2ui_invalid), color = MaterialTheme.colorScheme.error)
                else -> CircularProgressIndicator(Modifier.size(20.dp))
            }
        }
    }
}
