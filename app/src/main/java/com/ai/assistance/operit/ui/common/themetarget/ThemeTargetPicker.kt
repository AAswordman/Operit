package com.ai.assistance.operit.ui.common.themetarget

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.ui.theme.script.text.ThemeBasicTextField as BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

internal data class ThemeTargetOption(val target: ActivePrompt, val name: String) {
    val key: String
        get() =
            when (target) {
                is ActivePrompt.CharacterCard -> "character:${target.id}"
                is ActivePrompt.CharacterGroup -> "group:${target.id}"
            }
}

/** 主题编辑与导入共用列表，目标范围及选择后的业务行为由调用方提供。 */
@Composable
internal fun ThemeTargetPicker(
    targets: List<ThemeTargetOption>,
    selectedTarget: ActivePrompt?,
    enabled: Boolean,
    onSelect: (ActivePrompt) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered =
        remember(targets, query) {
            targets.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemeTargetSearchField(query, { query = it }, enabled)
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            if (filtered.isEmpty())
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.theme_target_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            items(filtered, key = { it.key }) { card ->
                val selected = card.target == selectedTarget
                val shape = RoundedCornerShape(16.dp)
                Surface(
                    color =
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = shape,
                    border =
                        BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                    modifier =
                        Modifier.fillMaxWidth().clip(shape).selectable(
                            selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                        ) {
                            onSelect(card.target)
                        },
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemeTargetAvatar(card.target)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                card.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(
                                    if (card.target is ActivePrompt.CharacterGroup)
                                        R.string.theme_target_group_label
                                    else R.string.theme_target_character_label
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier.size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected)
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeTargetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle =
            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty())
                        Text(
                            stringResource(R.string.theme_target_search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    innerTextField()
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeTargetPickerSheet(
    targets: List<ThemeTargetOption>,
    selectedTarget: ActivePrompt,
    enabled: Boolean,
    onDismissRequest: () -> Unit,
    onSelect: (ActivePrompt) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.theme_select_target),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
            }
            ThemeTargetPicker(targets, selectedTarget, enabled, onSelect)
        }
    }
}

@Composable
internal fun ThemeTargetAvatar(target: ActivePrompt, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val avatar by
        remember(preferences, target) {
                when (target) {
                    is ActivePrompt.CharacterCard ->
                        preferences.getAiAvatarForCharacterCardFlow(target.id)
                    is ActivePrompt.CharacterGroup ->
                        preferences.getAiAvatarForCharacterGroupFlow(target.id)
                }
            }
            .collectAsState(initial = null)
    ThemeTargetImage(avatar, modifier, target is ActivePrompt.CharacterGroup)
}

@Composable
internal fun ThemeTargetImage(uri: String?, modifier: Modifier = Modifier, group: Boolean = false) {
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (uri.isNullOrBlank())
            Icon(
                if (group) Icons.Default.Groups else Icons.Default.Person,
                null,
                Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
