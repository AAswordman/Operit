package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

internal data class ExtensionDetailStat(val label: String, val value: String, val icon: ImageVector? = null)
internal data class ExtensionDetailFact(val label: String, val value: String, val monospace: Boolean = false)

/** Shared structure for installed and market detail pages; actions keep their existing owners. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtensionDetailHeader(
    title: String,
    subtitle: String?,
    badges: List<String>,
    logo: Painter?,
    logoEnabled: Boolean = true,
    actions: @Composable () -> Unit,
) {
    val disabledLogoFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (logoEnabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(80.dp),
        ) {
            if (logo != null) {
                Image(logo, contentDescription = null, contentScale = ContentScale.Fit,
                    colorFilter = if (logoEnabled) null else disabledLogoFilter,
                    alpha = if (logoEnabled) 1f else 0.38f,
                    modifier = Modifier.fillMaxSize().padding(6.dp))
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(title.take(1), style = MaterialTheme.typography.headlineLarge,
                        color = if (logoEnabled) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badges.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { badge ->
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text(badge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            actions()
        }
    }
}

/** A declared UI keeps its Open slot while disabled, so activation controls do not move. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtensionDetailActions(
    displayName: String,
    checked: Boolean,
    showOpen: Boolean,
    openEnabled: Boolean,
    toggleEnabled: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    updateAction: (@Composable () -> Unit)? = null,
) {
    val enabledLabel = stringResource(R.string.extension_detail_enabled)
    val disabledLabel = stringResource(R.string.extension_detail_disabled)
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    // Reserve both labels' width so the switch also stays put in languages with different label lengths.
    val labelWidth = with(LocalDensity.current) {
        maxOf(measurer.measure(enabledLabel, labelStyle).size.width,
            measurer.measure(disabledLabel, labelStyle).size.width).toDp()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showOpen) {
            Button(onClick = onOpen, enabled = openEnabled, shape = RoundedCornerShape(999.dp)) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.open))
            }
        }
        if (updateAction != null) updateAction() else {
            Surface(
                modifier = Modifier.testTag("extension-detail-activation"),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (checked) enabledLabel else disabledLabel, modifier = Modifier.width(labelWidth),
                        style = labelStyle, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(5.dp))
                    ExtensionSwitch(checked = checked, onCheckedChange = onToggle,
                        modifier = Modifier.semantics { contentDescription = displayName }, enabled = toggleEnabled)
                }
            }
        }
    }
}

/** Keep every statistic; narrow windows scroll instead of hiding a fifth item or clipping a date. */
@Composable
internal fun ExtensionDetailStats(stats: List<ExtensionDetailStat>) {
    if (stats.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = maxOf(64.dp, (maxWidth - (stats.size - 1).dp) / stats.size)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                stats.forEachIndexed { index, stat ->
                    Column(Modifier.width(itemWidth).padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stat.label, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            stat.icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface) }
                            Text(stat.value, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center)
                        }
                    }
                    if (index < stats.lastIndex) {
                        Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
internal fun ExtensionDetailSection(title: String, count: Int? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            count?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        content()
    }
}

/** Both detail entry points start with a readable preview, including very long plain-text manifests. */
@Composable
internal fun ExtensionDetailDescription(
    title: String,
    body: String,
    stateKey: String,
    previewLines: Int = 4,
    markdown: Boolean = false,
    codeBlock: Boolean = false,
) {
    var expanded by rememberSaveable(stateKey, body) { mutableStateOf(false) }
    var overflow by remember(stateKey, body) { mutableStateOf(false) }
    val preview = remember(body, markdown) {
        if (markdown) body
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
            .replace("**", "").replace("`", "")
        else body
    }
    ExtensionDetailSection(title) {
        val bodyModifier = if (codeBlock) {
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)).padding(12.dp)
        } else Modifier.fillMaxWidth()
        Column(bodyModifier.testTag("extension-description-body-$stateKey")) {
            if (expanded && markdown) {
                MarkdownTextComposable(body, textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth())
            } else {
                Text(if (expanded) body else preview,
                    style = if (codeBlock) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontFamily = if (codeBlock) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else previewLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                    modifier = Modifier.fillMaxWidth())
            }
            if (expanded || overflow) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(vertical = 4.dp)) {
                    Text(stringResource(if (expanded) R.string.extension_description_collapse else R.string.extension_description_expand))
                }
            }
        }
    }
}

@Composable
internal fun ExtensionDetailFacts(title: String, facts: List<ExtensionDetailFact>) {
    if (facts.isEmpty()) return
    ExtensionDetailSection(title) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                facts.forEachIndexed { index, fact ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(fact.label, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fact.value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (fact.monospace) FontFamily.Monospace else null,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (index < facts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtensionDetailContents(items: List<ExtensionDetailStat>) {
    ExtensionDetailSection(stringResource(R.string.extension_detail_contents)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(item.value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(item.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExtensionDetailEmpty(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
