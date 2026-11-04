package com.ai.assistance.operit.ui.features.models.token.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.models.token.model.TabConfig
import com.ai.assistance.operit.ui.features.models.token.model.UrlConfig

@Composable
fun UrlConfigDialog(
    currentConfig: UrlConfig,
    onSave: (UrlConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentConfig.name) }
    var signInUrl by remember { mutableStateOf(currentConfig.signInUrl) }
    var tab1Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(0)?.title ?: "") }
    var tab1Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(0)?.url ?: "") }
    var tab2Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(1)?.title ?: "") }
    var tab2Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(1)?.url ?: "") }
    var tab3Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(2)?.title ?: "") }
    var tab3Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(2)?.url ?: "") }
    var tab4Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(3)?.title ?: "") }
    var tab4Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(3)?.url ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.custom_url_config))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.config_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = signInUrl,
                    onValueChange = { signInUrl = it },
                    label = { Text(stringResource(R.string.signin_url)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.tab_config),
                    style = MaterialTheme.typography.titleMedium
                )

                // Tab 1
                Text(stringResource(R.string.tab_number, 1), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab1Title,
                    onValueChange = { tab1Title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab1Url,
                    onValueChange = { tab1Url = it },
                    label = { Text(stringResource(R.string.url_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tab 2
                Text(stringResource(R.string.tab_number, 2), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab2Title,
                    onValueChange = { tab2Title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab2Url,
                    onValueChange = { tab2Url = it },
                    label = { Text(stringResource(R.string.url_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tab 3
                Text(stringResource(R.string.tab_number, 3), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab3Title,
                    onValueChange = { tab3Title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab3Url,
                    onValueChange = { tab3Url = it },
                    label = { Text(stringResource(R.string.url_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tab 4
                Text(stringResource(R.string.tab_number, 4), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab4Title,
                    onValueChange = { tab4Title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab4Url,
                    onValueChange = { tab4Url = it },
                    label = { Text(stringResource(R.string.url_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newConfig = UrlConfig(
                        name = name,
                        signInUrl = signInUrl,
                        tabs = listOf(
                            TabConfig(tab1Title, tab1Url),
                            TabConfig(tab2Title, tab2Url),
                            TabConfig(tab3Title, tab3Url),
                            TabConfig(tab4Title, tab4Url)
                        )
                    )
                    onSave(newConfig)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
} 