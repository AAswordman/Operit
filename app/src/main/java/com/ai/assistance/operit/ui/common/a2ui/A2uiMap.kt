package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

internal fun cardMapMarkers(raw: Any?): List<CardChartPoint> {
    val markers = raw as? List<*> ?: error("Expected markers")
    require(markers.size <= 32)
    return markers.map { item ->
        val marker = item as? Map<*, *> ?: error("Invalid marker")
        val latitude = (marker["latitude"] as? Number)?.toDouble() ?: error("Missing latitude")
        val longitude = (marker["longitude"] as? Number)?.toDouble() ?: error("Missing longitude")
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        CardChartPoint((marker["label"] as? String).orEmpty().take(120), longitude, latitude)
    }
}

@Composable
internal fun CardMap(title: String, raw: Any?, route: Boolean, modifier: Modifier, onOpenUrl: (String) -> Unit) {
    val points = remember(raw) { runCatching { cardMapMarkers(raw) } }
    var selected by remember(raw) { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.a2ui_map_schematic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (points.isFailure) Text(stringResource(R.string.a2ui_bad_chart), color = MaterialTheme.colorScheme.error)
        else {
            val markers = points.getOrThrow()
            CardChart(if (route) "line" else "scatter", "", listOf(CardChartSeries("", markers)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                markers.forEachIndexed { index, marker ->
                    FilterChip(selected == index, onClick = { selected = index }, label = { Text(marker.label.ifBlank { "${index + 1}" }) })
                }
            }
            markers.getOrNull(selected)?.let { marker ->
                Text("${marker.y}, ${marker.x}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onOpenUrl("https://www.google.com/maps/search/?api=1&query=${marker.y},${marker.x}") },
                    enabled = LocalCardBrowse.current) {
                    Text(stringResource(R.string.a2ui_open_map))
                }
            }
        }
    }
}
