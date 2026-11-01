package com.ai.assistance.operit.ui.features.automation.websession.browser

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.ui.WebSessionUserscriptUiState

@Composable
internal fun WebSessionUserscriptSheet(
    state: WebSessionUserscriptUiState,
    currentPageMenuCommands: List<UserscriptPageMenuCommand>,
    onInstallFromUrl: (String) -> Unit,
    onImportLocal: () -> Unit,
    onConfirmInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onSetScriptEnabled: (Long, Boolean) -> Unit,
    onDeleteScript: (Long) -> Unit,
    onCheckUpdate: (Long) -> Unit,
    onInvokeMenuCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var installUrl by rememberSaveable { mutableStateOf("") }

    WebSessionSheetScaffold(
        title = stringResource(R.string.web_session_userscripts),
        subtitle = stringResource(R.string.web_session_userscripts_subtitle),
        modifier = modifier
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.supportState.isSupported) {
                WebSessionEmptyState(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.web_session_userscript_unsupported),
                    message =
                        state.supportState.reason
                            ?: stringResource(R.string.web_session_userscript_unsupported_summary)
                )
                return@Column
            }

            WebSessionSectionLabel(text = stringResource(R.string.web_session_userscript_install))
            WebSessionItemCard {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = installUrl,
                        onValueChange = { installUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.web_session_userscript_install_from_url)) },
                        placeholder = { Text(text = "https://example.com/script.user.js") }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onInstallFromUrl(installUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.web_session_userscript_install_action))
                        }
                        FilledTonalButton(
                            onClick = onImportLocal,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.web_session_userscript_import_local))
                        }
                    }
                }
            }

            state.pendingInstall?.let { preview ->
                WebSessionSectionLabel(
                    text =
                        stringResource(
                            if (preview.isUpdate) {
                                R.string.web_session_userscript_update_preview
                            } else {
                                R.string.web_session_userscript_install_preview
                            }
                        )
                )
                PendingInstallCard(
                    preview = preview,
                    onConfirmInstall = onConfirmInstall,
                    onCancelInstall = onCancelInstall
                )
            }

            if (currentPageMenuCommands.isNotEmpty()) {
                WebSessionSectionLabel(text = stringResource(R.string.web_session_userscript_page_menu))
                currentPageMenuCommands.forEach { command ->
                    WebSessionItemCard(onClick = { onInvokeMenuCommand(command.commandId) }) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Extension,
                                    contentDescription = null,
                                    modifier = Modifier.padding(9.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = command.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.web_session_userscript_page_menu_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            WebSessionSectionLabel(text = stringResource(R.string.web_session_userscript_library))
            if (state.installedScripts.isEmpty()) {
                WebSessionEmptyState(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.web_session_userscript_none)
                )
            } else {
                state.installedScripts.forEach { script ->
                    InstalledUserscriptCard(
                        script = script,
                        runtimeStatus = state.currentPageStatuses[script.id],
                        onSetScriptEnabled = onSetScriptEnabled,
                        onDeleteScript = onDeleteScript,
                        onCheckUpdate = onCheckUpdate
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingInstallCard(
    preview: UserscriptInstallPreview,
    onConfirmInstall: () -> Unit,
    onCancelInstall: () -> Unit
) {
    val previewBlockedReasons = preview.blockedReasons.filterNot { it.startsWith("Unknown grants:") }
    WebSessionItemCard(highlighted = true) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = preview.metadata.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            PreviewMetaLine(
                label = stringResource(R.string.web_session_userscript_version),
                value = preview.metadata.version
            )
            PreviewMetaLine(
                label = stringResource(R.string.web_session_userscript_source),
                value = preview.sourceDisplay ?: preview.sourceUrl ?: "-"
            )
            PreviewMetaLine(
                label = stringResource(R.string.web_session_userscript_grants),
                value = preview.metadata.grants.joinToString().ifBlank { "-" }
            )
            PreviewMetaLine(
                label = stringResource(R.string.web_session_userscript_connects),
                value = preview.metadata.connects.joinToString().ifBlank { "*" }
            )
            PreviewMetaLine(
                label = stringResource(R.string.web_session_userscript_requires),
                value = preview.metadata.requires.joinToString { it.url }.ifBlank { "-" }
            )
            PreviewMetaLine(
                label = stringResource(R.string.web_session_userscript_resources),
                value = preview.metadata.resources.joinToString { "${it.name} -> ${it.url}" }.ifBlank { "-" }
            )
            PreviewMetaLine(
                label = stringResource(R.string.web_session_userscript_update_url),
                value = preview.metadata.updateUrl ?: preview.metadata.downloadUrl ?: preview.sourceUrl ?: "-"
            )
            preview.metadata.website?.takeIf { it.isNotBlank() }?.let { website ->
                PreviewMetaLine(
                    label = stringResource(R.string.web_session_userscript_website),
                    value = website
                )
            }
            preview.metadata.supportUrl?.takeIf { it.isNotBlank() }?.let { supportUrl ->
                PreviewMetaLine(
                    label = stringResource(R.string.web_session_userscript_support_url),
                    value = supportUrl
                )
            }
            preview.metadata.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                PreviewMetaLine(
                    label = stringResource(R.string.web_session_userscript_tags),
                    value = tags.joinToString()
                )
            }
            preview.metadata.sandbox?.takeIf { it.isNotBlank() }?.let { sandbox ->
                PreviewMetaLine(
                    label = stringResource(R.string.web_session_userscript_sandbox),
                    value = sandbox
                )
            }
            preview.metadata.runIn?.takeIf { it.isNotBlank() }?.let { runIn ->
                PreviewMetaLine(
                    label = stringResource(R.string.web_session_userscript_run_in),
                    value = runIn
                )
            }
            preview.metadata.webRequestRules.takeIf { it.isNotEmpty() }?.let { rules ->
                PreviewMetaLine(
                    label = stringResource(R.string.web_session_userscript_web_request),
                    value = rules.joinToString()
                )
            }
            formatIconSummary(
                preview.metadata.icons.icon,
                preview.metadata.icons.icon64,
                preview.metadata.icons.defaultIcon
            )?.let { icons ->
                PreviewMetaLine(
                    label = stringResource(R.string.web_session_userscript_icons),
                    value = icons
                )
            }
            if (preview.unknownGrants.isNotEmpty()) {
                Text(
                    text =
                        stringResource(
                            R.string.web_session_userscript_unknown_grants,
                            preview.unknownGrants.joinToString()
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (previewBlockedReasons.isNotEmpty()) {
                Text(
                    text =
                        stringResource(
                            R.string.web_session_userscript_blocked_reasons,
                            previewBlockedReasons.joinToString()
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onConfirmInstall,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            stringResource(
                                if (preview.isUpdate) {
                                    R.string.web_session_userscript_update_action
                                } else {
                                    R.string.web_session_userscript_confirm_install
                                }
                            )
                    )
                }
                TextButton(
                    onClick = onCancelInstall,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.web_session_userscript_cancel_install))
                }
            }
        }
    }
}

@Composable
private fun InstalledUserscriptCard(
    script: UserscriptListItem,
    runtimeStatus: UserscriptPageRuntimeStatus?,
    onSetScriptEnabled: (Long, Boolean) -> Unit,
    onDeleteScript: (Long) -> Unit,
    onCheckUpdate: (Long) -> Unit
) {
    var expanded by rememberSaveable(script.id) { mutableStateOf(false) }
    val scriptBlockedReasons = script.blockedReasons.filterNot { it.startsWith("Unknown grants:") }

    WebSessionItemCard(onClick = { expanded = !expanded }) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    RuntimeStatusLine(
                        status = runtimeStatus ?: fallbackStatusFor(script),
                        script = script
                    )
                }
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = script.enabled,
                    onCheckedChange = { enabled -> onSetScriptEnabled(script.id, enabled) }
                )
            }

            if (expanded) {
                script.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (script.grants.isNotEmpty()) {
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_grants),
                        value = script.grants.joinToString()
                    )
                }

                val matchSummary =
                    buildList {
                        addAll(script.matches)
                        addAll(script.includes)
                    }.joinToString()
                if (matchSummary.isNotBlank()) {
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_matches),
                        value = matchSummary
                    )
                }

                if (script.connects.isNotEmpty()) {
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_connects),
                        value = script.connects.joinToString()
                    )
                }

                script.website?.takeIf { it.isNotBlank() }?.let { website ->
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_website),
                        value = website
                    )
                }

                script.supportUrl?.takeIf { it.isNotBlank() }?.let { supportUrl ->
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_support_url),
                        value = supportUrl
                    )
                }

                script.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_tags),
                        value = tags.joinToString()
                    )
                }

                script.sandbox?.takeIf { it.isNotBlank() }?.let { sandbox ->
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_sandbox),
                        value = sandbox
                    )
                }

                script.runIn?.takeIf { it.isNotBlank() }?.let { runIn ->
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_run_in),
                        value = runIn
                    )
                }

                script.webRequestRules.takeIf { it.isNotEmpty() }?.let { rules ->
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_web_request),
                        value = rules.joinToString()
                    )
                }

                formatIconSummary(
                    script.icons.icon,
                    script.icons.icon64,
                    script.icons.defaultIcon
                )?.let { icons ->
                    PreviewMetaLine(
                        label = stringResource(R.string.web_session_userscript_icons),
                        value = icons
                    )
                }

                if (script.unknownGrants.isNotEmpty()) {
                    Text(
                        text =
                            stringResource(
                                R.string.web_session_userscript_unknown_grants,
                                script.unknownGrants.joinToString()
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (scriptBlockedReasons.isNotEmpty()) {
                    Text(
                        text =
                            stringResource(
                                R.string.web_session_userscript_blocked_reasons,
                                scriptBlockedReasons.joinToString()
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onCheckUpdate(script.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.web_session_userscript_check_update),
                            modifier = Modifier.widthIn(max = 120.dp),
                            maxLines = 1
                        )
                    }
                    TextButton(
                        onClick = { onDeleteScript(script.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.web_session_userscript_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusLine(
    status: UserscriptPageRuntimeStatus,
    script: UserscriptListItem
) {
    val statusLabel =
        when (status.state) {
            UserscriptPageRuntimeState.DISABLED -> stringResource(R.string.web_session_userscript_status_disabled)
            UserscriptPageRuntimeState.UNSUPPORTED -> stringResource(R.string.web_session_userscript_status_unsupported)
            UserscriptPageRuntimeState.NOT_MATCHED -> stringResource(R.string.web_session_userscript_status_not_matched)
            UserscriptPageRuntimeState.QUEUED -> stringResource(R.string.web_session_userscript_status_queued)
            UserscriptPageRuntimeState.RUNNING -> stringResource(R.string.web_session_userscript_status_running)
            UserscriptPageRuntimeState.SUCCESS -> stringResource(R.string.web_session_userscript_status_success)
            UserscriptPageRuntimeState.ERROR -> stringResource(R.string.web_session_userscript_status_error)
        }
    val metaSummary =
        listOfNotNull(
            script.version.takeIf { it.isNotBlank() }?.let { "v$it" },
            script.sourceDisplay
        ).joinToString(" • ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(7.dp)
                    .background(
                        color = statusColor(status.state),
                        shape = CircleShape
                    )
        )
        Text(
            text = listOfNotNull(statusLabel, metaSummary.takeIf { it.isNotBlank() }).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun statusColor(state: UserscriptPageRuntimeState) =
    when (state) {
        UserscriptPageRuntimeState.DISABLED,
        UserscriptPageRuntimeState.NOT_MATCHED -> MaterialTheme.colorScheme.outline
        UserscriptPageRuntimeState.UNSUPPORTED,
        UserscriptPageRuntimeState.ERROR -> MaterialTheme.colorScheme.error
        UserscriptPageRuntimeState.QUEUED -> MaterialTheme.colorScheme.tertiary
        UserscriptPageRuntimeState.RUNNING -> MaterialTheme.colorScheme.primary
        UserscriptPageRuntimeState.SUCCESS -> MaterialTheme.colorScheme.secondary
    }

private fun fallbackStatusFor(script: UserscriptListItem): UserscriptPageRuntimeStatus =
    when {
        !script.enabled ->
            UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.DISABLED)
        script.blockedReasons.isNotEmpty() ->
            UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.UNSUPPORTED)
        else ->
            UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.NOT_MATCHED)
    }

private fun formatIconSummary(vararg values: String?): String? =
    values
        .filterNotNull()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString()

@Composable
private fun PreviewMetaLine(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
