package com.ai.assistance.operit.ui.features.extensions.packages.dialogs

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.PackageToolParameter
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeTool
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun MCPPackageDetailsDialog(
    server: MCPLocalServer.PluginMetadata,
    installedPath: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var introText by remember(server.id, installedPath) { mutableStateOf<String?>(null) }
    var tools by remember(server.id) { mutableStateOf<List<PackageTool>>(emptyList()) }
    var isLoading by remember(server.id) { mutableStateOf(true) }
    var loadError by remember(server.id) { mutableStateOf<String?>(null) }
    var selectedTool by remember(server.id) { mutableStateOf<PackageTool?>(null) }

    LaunchedEffect(server.id, installedPath) {
        isLoading = true
        loadError = null
        tools = emptyList()
        try {
            introText =
                withContext(Dispatchers.IO) {
                    server.description.trim()
                }
            tools =
                withContext(Dispatchers.IO) {
                    loadMcpPackageTools(context = context, serverId = server.id)
                }
        } catch (e: Exception) {
            AppLogger.e("MCPPackageDetailsDialog", "Failed to load MCP package details for ${server.id}", e)
            introText = server.description.trim()
            loadError = context.getString(R.string.tools_load_error, e.message ?: "")
        } finally {
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: ${server.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                            )
                        ) {
                            Text(
                                text = introText?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.mcp_no_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.pkg_tool_list),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    when {
                        isLoading -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        !loadError.isNullOrBlank() -> {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = loadError.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        tools.isEmpty() -> {
                            item {
                                MCPEmptyToolsCard(message = stringResource(R.string.mcp_no_available_tools))
                            }
                        }

                        else -> {
                            items(items = tools, key = { tool -> tool.name }) { tool ->
                                MCPPackageToolCard(
                                    serverId = server.id,
                                    tool = tool,
                                    onExecute = { selectedTool = it }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(R.string.pkg_close))
                    }
                }
            }
        }
    }

    selectedTool?.let { tool ->
        MCPToolExecutionDialog(
            serverId = server.id,
            tool = tool,
            onDismiss = { selectedTool = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MCPPackageToolCard(
    serverId: String,
    tool: PackageTool,
    onExecute: (PackageTool) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = tool.description.resolve(context).ifBlank {
                            context.getString(R.string.mcp_no_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ID: $serverId:${tool.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { onExecute(tool) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.run),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (tool.parameters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tool.parameters.forEach { param ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = buildString {
                                        append(param.name)
                                        append(" [")
                                        append(param.type)
                                        append(']')
                                        if (param.required) {
                                            append(" *")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MCPToolExecutionDialog(
    serverId: String,
    tool: PackageTool,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val scope = rememberCoroutineScope()

    var paramValues by remember(tool) { mutableStateOf(tool.parameters.associate { it.name to "" }) }
    var executionResult by remember(tool) { mutableStateOf<ToolResult?>(null) }
    var executing by remember(tool) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.script_execution),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ID: $serverId:${tool.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.weight(1f, fill = true).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        )
                    ) {
                        Text(
                            text = tool.description.resolve(context).ifBlank {
                                context.getString(R.string.mcp_no_description)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }

                    if (tool.parameters.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.script_params_config),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            tool.parameters.forEach { param ->
                                val expectsStructuredValue =
                                    param.type.equals("object", ignoreCase = true) ||
                                        param.type.equals("array", ignoreCase = true)
                                OutlinedTextField(
                                    value = paramValues[param.name].orEmpty(),
                                    onValueChange = { value ->
                                        paramValues = paramValues.toMutableMap().apply {
                                            put(param.name, value)
                                        }
                                    },
                                    label = {
                                        Text(
                                            buildString {
                                                append(param.name)
                                                append(" [")
                                                append(param.type)
                                                append(']')
                                                if (param.required) {
                                                    append(" *")
                                                }
                                            }
                                        )
                                    },
                                    modifier =
                                        if (expectsStructuredValue) {
                                            Modifier.fillMaxWidth()
                                        } else {
                                            Modifier.fillMaxWidth(0.48f)
                                        },
                                    singleLine = !expectsStructuredValue,
                                    minLines = if (expectsStructuredValue) 3 else 1,
                                    maxLines = if (expectsStructuredValue) 6 else 1,
                                    placeholder = { Text(param.description.resolve(context)) }
                                )
                            }
                        }
                    }

                    executionResult?.let { result ->
                        Text(
                            text = stringResource(R.string.execution_result),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (result.success) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f)
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (result.success) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (result.success) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Error
                                        },
                                        contentDescription = null,
                                        tint = if (result.success) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (result.success) {
                                            stringResource(R.string.execution_result)
                                        } else {
                                            stringResource(R.string.error_title)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (result.success) {
                                        result.result.toString()
                                    } else {
                                        result.error.orEmpty()
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (result.success) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    FilledTonalButton(
                        onClick = {
                            executing = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val missingParams =
                                        tool.parameters
                                            .filter { it.required }
                                            .map { it.name }
                                            .filter { paramValues[it].isNullOrBlank() }

                                    val result =
                                        if (missingParams.isNotEmpty()) {
                                            ToolResult(
                                                toolName = "$serverId:${tool.name}",
                                                success = false,
                                                result = StringResultData(""),
                                                error = context.getString(
                                                    R.string.script_missing_params,
                                                    missingParams.joinToString(", ")
                                                )
                                            )
                                        } else {
                                            val toolParameters =
                                                tool.parameters.mapNotNull { param ->
                                                    val value = paramValues[param.name].orEmpty()
                                                    if (value.isBlank() && !param.required) {
                                                        null
                                                    } else {
                                                        ToolParameter(param.name, value)
                                                    }
                                                }
                                            toolHandler.executeTool(
                                                AITool(
                                                    name = "$serverId:${tool.name}",
                                                    parameters = toolParameters
                                                )
                                            )
                                        }

                                    withContext(Dispatchers.Main) {
                                        executionResult = result
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e(
                                        "MCPToolExecutionDialog",
                                        "Failed to execute MCP tool $serverId:${tool.name}",
                                        e
                                    )
                                    withContext(Dispatchers.Main) {
                                        executionResult =
                                            ToolResult(
                                                toolName = "$serverId:${tool.name}",
                                                success = false,
                                                result = StringResultData(""),
                                                error = context.getString(
                                                    R.string.script_execution_error,
                                                    e.message ?: ""
                                                )
                                            )
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        executing = false
                                    }
                                }
                            }
                        },
                        enabled = !executing
                    ) {
                        if (executing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(stringResource(R.string.script_execute))
                    }
                }
            }
        }
    }
}

@Composable
private fun MCPEmptyToolsCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private suspend fun loadMcpPackageTools(
    context: Context,
    serverId: String
): List<PackageTool> {
    val session = MCPManager.getInstance(context).getOrCreateSession(serverId)
        ?: return emptyList()
    return session.listTools()
        .mapNotNull { tool ->
            runCatching { tool.toPackageTool() }
                .onFailure { throwable ->
                    AppLogger.e("MCPPackageDetailsDialog", "Failed to parse MCP tool for $serverId", throwable)
                }
                .getOrNull()
        }
        .sortedBy { tool -> tool.name }
}

private fun McpRuntimeTool.toPackageTool(): PackageTool? {
    val toolName = name.trim()
    if (toolName.isEmpty()) {
        return null
    }

    val schema = JSONObject(inputSchema)

    val requiredParams =
        schema.optJSONArray("required")
            ?.let { requiredArray ->
                buildSet {
                    for (index in 0 until requiredArray.length()) {
                        val paramName = requiredArray.optString(index).trim()
                        if (paramName.isNotEmpty()) {
                            add(paramName)
                        }
                    }
                }
            }
            .orEmpty()

    val parameters =
        buildList {
            val properties = schema.optJSONObject("properties") ?: return@buildList
            val names = properties.keys().asSequence().toList().sorted()
            names.forEach { paramName ->
                val paramObject = properties.optJSONObject(paramName) ?: JSONObject()
                add(
                    PackageToolParameter(
                        name = paramName,
                        description = LocalizedText.of(paramObject.optString("description", "")),
                        type = paramObject.optString("type", "string"),
                        required = requiredParams.contains(paramName)
                    )
                )
            }
        }

    return PackageTool(
        name = toolName,
        description = LocalizedText.of(description),
        parameters = parameters,
        script = ""
    )
}
