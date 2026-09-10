package com.ai.assistance.operit.ui.features.toolbox.screens.autoglm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AutoGlmToolScreen(
    viewModel: AutoGlmViewModel = viewModel(factory = AutoGlmViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    var task by remember { mutableStateOf("") }
    var useVirtualScreen by remember { mutableStateOf(false) }

    AutoGlmToolContent(
        uiState = uiState,
        task = task,
        useVirtualScreen = useVirtualScreen,
        onUseVirtualScreenChange = { useVirtualScreen = it },
        onTaskChange = { task = it },
        onExecute = { viewModel.executeTask(it, useVirtualScreen) },
        onCancel = { viewModel.cancelTask() }
    )
}

@Composable
private fun AutoGlmToolContent(
    uiState: AutoGlmUiState,
    task: String,
    useVirtualScreen: Boolean,
    onUseVirtualScreenChange: (Boolean) -> Unit,
    onTaskChange: (String) -> Unit,
    onExecute: (String) -> Unit,
    onCancel: () -> Unit
) {
    val logScrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            OutlinedTextField(
                value = task,
                onValueChange = onTaskChange,
                label = { Text("Enter Task", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            )
            {
                Text(
                    stringResource(R.string.autoglm_virtual_screen),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Switch(
                    checked = useVirtualScreen,
                    onCheckedChange = { onUseVirtualScreenChange(it) },
                    enabled = !uiState.isLoading
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (uiState.isLoading) {
                        onCancel()
                    } else {
                        onExecute(task)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (uiState.isLoading) "Cancel" else "Execute",
                    color = if (uiState.isLoading) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Execution Log",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            LaunchedEffect(uiState.log) {
                logScrollState.animateScrollTo(logScrollState.maxValue)
            }

            Text(
                text = uiState.log,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(logScrollState)
            )
        }
    }
}
