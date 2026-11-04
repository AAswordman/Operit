package com.ai.assistance.operit.ui.features.extensions.packages.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@Composable
internal fun PluginListPreferenceError(onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.plugin_list_preferences_invalid), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.plugin_list_preferences_reset_hint), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onReset) { Text(stringResource(R.string.plugin_list_preferences_reset)) }
        }
    }
}
