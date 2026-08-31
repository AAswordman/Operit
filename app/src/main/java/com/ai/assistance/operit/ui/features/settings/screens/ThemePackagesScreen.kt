package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.packages.PublishedThemeInstallationV2
import com.ai.assistance.operit.data.theme.packages.ThemeInstanceV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageDefaultV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageInstallerV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageParameterIdsV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageReferenceV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageSelectionRepositoryV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterValueV2
import com.ai.assistance.operit.data.theme.packages.ThemeRuntimeRepositoryV2
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PRESET_PRIMARY_COLORS =
    listOf(
        0xFF6750A4L,
        0xFF0061A4L,
        0xFF006E1CL,
        0xFF984061L,
        0xFFB3261EL,
        0xFF8F4C00L,
        0xFF4A5C92L,
        0xFF00687AL,
        0xFF9C4146L,
        0xFF7D5260L,
    )

@Composable
fun ThemePackagesScreen(
    onGoBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val installer = remember(context) { ThemePackageInstallerV2.getInstance(context) }
    val selectionRepository =
        remember(context) { ThemePackageSelectionRepositoryV2.getInstance(context) }
    val activeInstance by selectionRepository.selectionFlow.collectAsState(
        initial = ThemeInstanceV2.defaultBundled(),
    )
    var installed by remember { mutableStateOf<List<PublishedThemeInstallationV2>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val catalog = installer.catalog()
            withContext(Dispatchers.Main) { installed = catalog.installations }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true
            scope.launch {
                val message =
                    try {
                        val staged = withContext(Dispatchers.IO) { stageImport(context, uri) }
                        try {
                            val coordinate = installer.import(staged)
                            reload()
                            context.getString(
                                R.string.theme_packages_import_success,
                                coordinate.packageId.value,
                            )
                        } finally {
                            withContext(Dispatchers.IO) { staged.delete() }
                        }
                    } catch (error: Throwable) {
                        error.message
                            ?: context.getString(R.string.theme_packages_import_failed)
                    }
                busy = false
                snackbar.showSnackbar(message)
            }
        }

    val backgroundPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            scope.launch {
                updateParameter(
                    context,
                    ThemePackageParameterIdsV2.BACKGROUND_IMAGE,
                    ThemeParameterValueV2.StringValue(uri.toString()),
                )
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.theme_packages_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::reload, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.theme_packages_refresh),
                    )
                }
            }

            installed.forEach { installedPackage ->
                val coordinate = installedPackage.coordinate
                val isDefault = ThemePackageDefaultV2.isDefault(coordinate)
                ThemeEntryCard(
                    title = installedPackage.manifest.displayName.resolve(Locale.getDefault().language),
                    subtitle =
                        if (isDefault) {
                            stringResource(R.string.theme_packages_builtin)
                        } else {
                            "${coordinate.packageId.value} · v${coordinate.version.value}"
                        },
                    selected = activeInstance.reference.coordinate == coordinate,
                    onActivate = {
                        scope.launch {
                            selectionRepository.replace(
                                ThemeInstanceV2(
                                    reference = ThemePackageReferenceV2(coordinate),
                                ),
                            )
                        }
                    },
                    trailing =
                        if (isDefault) {
                            null
                        } else {
                            {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val message =
                                                try {
                                                    installer.uninstall(coordinate)
                                                    reload()
                                                    context.getString(R.string.theme_packages_uninstalled)
                                                } catch (error: Throwable) {
                                                    error.message
                                                        ?: context.getString(
                                                            R.string.theme_packages_uninstall_failed,
                                                        )
                                                }
                                            snackbar.showSnackbar(message)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(
                                            R.string.theme_packages_uninstall,
                                        ),
                                    )
                                }
                            }
                        },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 参数编辑区由激活主题包声明的参数驱动，而不是硬编码某个包的参数表。
            val activeDefinitions =
                remember(activeInstance.reference.coordinate) {
                    ThemeRuntimeRepositoryV2
                        .require(activeInstance.reference.coordinate)
                        .parameterDefinitions
                }
            activeDefinitions.values.forEach { definition ->
                when (definition.type) {
                    com.ai.assistance.operit.data.theme.packages.ThemeParameterTypeV2.COLOR -> {
                        PrimaryColorSection(
                            title = definition.label.resolve(Locale.getDefault().language),
                            activeArgb =
                                (activeInstance.parameterValues[definition.id]
                                        as? ThemeParameterValueV2.ColorValue)
                                    ?.argb
                                    ?: ((definition.defaultValue
                                            as? com.ai.assistance.operit.data.theme.packages.ThemeParameterDefaultV2.ColorValue)
                                        ?.argb),
                            onPick = { argb ->
                                scope.launch {
                                    updateParameter(
                                        context,
                                        definition.id,
                                        ThemeParameterValueV2.ColorValue(argb),
                                    )
                                }
                            },
                            onReset = {
                                scope.launch {
                                    clearParameter(context, definition.id)
                                }
                            },
                        )
                    }

                    com.ai.assistance.operit.data.theme.packages.ThemeParameterTypeV2.STRING -> {
                        if (definition.id == ThemePackageParameterIdsV2.BACKGROUND_IMAGE) {
                            BackgroundImageSection(
                                title = definition.label.resolve(Locale.getDefault().language),
                                currentUri =
                                    (activeInstance.parameterValues[definition.id]
                                            as? ThemeParameterValueV2.StringValue)
                                        ?.value,
                                onPick = { backgroundPicker.launch(arrayOf("image/*")) },
                                onClear = {
                                    scope.launch {
                                        clearParameter(context, definition.id)
                                    }
                                },
                            )
                        }
                    }

                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { importLauncher.launch("*/*") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.theme_packages_import))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeEntryCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onActivate: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onActivate),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onActivate)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun PrimaryColorSection(
    title: String,
    activeArgb: Long?,
    onPick: (Long) -> Unit,
    onReset: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PRESET_PRIMARY_COLORS.forEach { argb ->
            val isSelected = activeArgb == argb
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(Color(argb.toInt()), CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            shape = CircleShape,
                        )
                        .clickable { onPick(argb) },
            )
        }
    }
    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.theme_packages_follow_system_color))
    }
}

@Composable
private fun BackgroundImageSection(
    title: String,
    currentUri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPick) {
            Text(stringResource(R.string.theme_packages_pick_image))
        }
        if (currentUri != null) {
            OutlinedButton(onClick = onClear) {
                Text(stringResource(R.string.theme_packages_clear))
            }
        }
    }
}

private suspend fun updateParameter(
    context: android.content.Context,
    parameterId: String,
    value: ThemeParameterValueV2,
) {
    val repository = ThemePackageSelectionRepositoryV2.getInstance(context)
    val current = repository.selectionFlow.first()
    repository.replace(
        current.copy(
            parameterValues = current.parameterValues + (parameterId to value),
        ),
    )
}

private suspend fun clearParameter(
    context: android.content.Context,
    parameterId: String,
) {
    val repository = ThemePackageSelectionRepositoryV2.getInstance(context)
    val current = repository.selectionFlow.first()
    repository.replace(
        current.copy(
            parameterValues = current.parameterValues - parameterId,
        ),
    )
}

private fun stageImport(
    context: android.content.Context,
    uri: Uri,
): java.io.File {
    val name =
        queryDisplayName(context, uri)
            ?: uri.lastPathSegment
            ?: "theme.otheme"
    if (!ThemePackageInstallerV2.isThemePackageFileName(name)) {
        error(context.getString(R.string.theme_packages_not_theme_file))
    }
    val staged =
        java.io.File(context.cacheDir, "theme-import-${System.currentTimeMillis()}.otheme")
    context.contentResolver.openInputStream(uri)?.use { input ->
        staged.outputStream().use { output -> input.copyTo(output) }
    } ?: error(context.getString(R.string.theme_packages_import_failed))
    return staged
}

private fun queryDisplayName(
    context: android.content.Context,
    uri: Uri,
): String? =
    context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }
