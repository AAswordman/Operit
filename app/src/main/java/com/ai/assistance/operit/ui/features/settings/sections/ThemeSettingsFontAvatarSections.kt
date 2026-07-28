package com.ai.assistance.operit.ui.features.settings.sections

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.AvatarPicker
import com.ai.assistance.operit.ui.features.settings.components.ChatStyleOption
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsFontSection(
    cardColors: CardColors,
    context: Context,
    editorSession: ThemeEditorSession,
    useCustomFontInput: Boolean,
    fontTypeInput: String,
    systemFontNameInput: String,
    customFontPathInput: String?,
    fontScaleInput: Float,
    onPickFont: () -> Unit,
) {
    ThemeSettingsSectionTitle(
        title = stringResource(R.string.theme_font_settings),
        icon = Icons.Default.TextFields,
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.enable_custom_font),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = context.getString(R.string.use_system_or_custom_font),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useCustomFontInput,
                    onCheckedChange = { editorSession.setBoolean("use_custom_font", it) },
                )
            }

            if (useCustomFontInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = context.getString(R.string.font_type_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = fontTypeInput == UserPreferencesManager.FONT_TYPE_SYSTEM,
                        onClick = {
                            editorSession.setString(
                                "font_type",
                                UserPreferencesManager.FONT_TYPE_SYSTEM,
                            )
                        },
                        label = { Text(context.getString(R.string.system_font)) },
                    )

                    FilterChip(
                        selected = fontTypeInput == UserPreferencesManager.FONT_TYPE_FILE,
                        onClick = {
                            editorSession.setString(
                                "font_type",
                                UserPreferencesManager.FONT_TYPE_FILE,
                            )
                        },
                        label = { Text(context.getString(R.string.custom_font_file)) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (fontTypeInput) {
                    UserPreferencesManager.FONT_TYPE_SYSTEM -> {
                        Text(
                            text = context.getString(R.string.select_system_font),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                UserPreferencesManager.SYSTEM_FONT_DEFAULT to
                                    stringResource(R.string.theme_font_default),
                                UserPreferencesManager.SYSTEM_FONT_SERIF to
                                    stringResource(R.string.theme_font_serif),
                                UserPreferencesManager.SYSTEM_FONT_SANS_SERIF to
                                    stringResource(R.string.theme_font_sans_serif),
                                UserPreferencesManager.SYSTEM_FONT_MONOSPACE to
                                    stringResource(R.string.theme_font_monospace),
                                UserPreferencesManager.SYSTEM_FONT_CURSIVE to
                                    stringResource(R.string.theme_font_cursive),
                            ).forEach { (fontName, displayName) ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = systemFontNameInput == fontName,
                                        onClick = {
                                            editorSession.setString("system_font_name", fontName)
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }

                    UserPreferencesManager.FONT_TYPE_FILE -> {
                        Text(
                            text = context.getString(R.string.custom_font_file_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        Text(
                            text = context.getString(R.string.font_file_support_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.Button(
                                onClick = onPickFont,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(context.getString(R.string.select_font_file))
                            }

                            if (!customFontPathInput.isNullOrEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        editorSession.setOptionalString("custom_font_path", null)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(context.getString(R.string.clear_font))
                                }
                            }
                        }

                        if (!customFontPathInput.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text =
                                    context.getString(
                                        R.string.current_font_file_path,
                                        customFontPathInput.substringAfterLast("/"),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text =
                        context.getString(
                            R.string.font_size_scale_label,
                            String.format("%.1f", fontScaleInput),
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Slider(
                    value = fontScaleInput,
                    onValueChange = { editorSession.setFloat("font_scale", it) },
                    valueRange = 0.8f..1.5f,
                    steps = 6,
                )
            }
        }
    }
}

@Composable
internal fun ThemeSettingsAvatarSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    scope: CoroutineScope,
    displayPreferencesManager: DisplayPreferencesManager,
    userAvatarUriInput: String?,
    globalUserAvatarUriInput: String?,
    onGlobalUserAvatarUriInputChange: (String?) -> Unit,
    globalUserNameInput: String?,
    onGlobalUserNameInputChange: (String?) -> Unit,
    avatarShapeInput: String,
    avatarCornerRadiusInput: Float,
    avatarImagePicker: ManagedActivityResultLauncher<String, Uri?>,
    onAvatarPickerModeChange: (String) -> Unit,
) {
    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.avatar_customization_title),
        icon = Icons.Default.Person,
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AvatarPicker(
                    label = stringResource(id = R.string.user_avatar_label),
                    avatarUri = userAvatarUriInput,
                    onAvatarChange = {
                        onAvatarPickerModeChange("user")
                        avatarImagePicker.launch("image/*")
                    },
                    onAvatarReset = {
                        editorSession.setOptionalString("custom_user_avatar_uri", null)
                    },
                )

                AvatarPicker(
                    label = stringResource(id = R.string.global_user_avatar_label),
                    avatarUri = globalUserAvatarUriInput,
                    onAvatarChange = {
                        onAvatarPickerModeChange("global_user")
                        avatarImagePicker.launch("image/*")
                    },
                    onAvatarReset = {
                        onGlobalUserAvatarUriInputChange(null)
                        scope.launch {
                            displayPreferencesManager.saveDisplaySettings(globalUserAvatarUri = "")
                        }
                    },
                )
            }

            Text(
                text = stringResource(R.string.theme_avatar_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(id = R.string.global_user_name_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
                value = globalUserNameInput ?: "",
                onValueChange = { onGlobalUserNameInputChange(it) },
                label = { Text(stringResource(id = R.string.global_user_name_label)) },
                placeholder = { Text(stringResource(id = R.string.global_user_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                trailingIcon = {
                    if (globalUserNameInput.isNullOrEmpty()) {
                        IconButton(
                            onClick = {
                                onGlobalUserNameInputChange("")
                                scope.launch {
                                    displayPreferencesManager.saveDisplaySettings(globalUserName = "")
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(id = R.string.clear_action),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    displayPreferencesManager.saveDisplaySettings(
                                        globalUserName = globalUserNameInput,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = stringResource(id = R.string.save_action),
                            )
                        }
                    }
                },
            )

            Text(
                text = stringResource(id = R.string.global_user_name_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(id = R.string.avatar_shape_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatStyleOption(
                    title = stringResource(id = R.string.avatar_shape_circle),
                    selected = avatarShapeInput == UserPreferencesManager.AVATAR_SHAPE_CIRCLE,
                    modifier = Modifier.weight(1f),
                ) {
                    editorSession.setString(
                        "avatar_shape",
                        UserPreferencesManager.AVATAR_SHAPE_CIRCLE,
                    )
                }
                ChatStyleOption(
                    title = stringResource(id = R.string.avatar_shape_square),
                    selected = avatarShapeInput == UserPreferencesManager.AVATAR_SHAPE_SQUARE,
                    modifier = Modifier.weight(1f),
                ) {
                    editorSession.setString(
                        "avatar_shape",
                        UserPreferencesManager.AVATAR_SHAPE_SQUARE,
                    )
                }
            }

            AnimatedVisibility(
                visible = avatarShapeInput == UserPreferencesManager.AVATAR_SHAPE_SQUARE,
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(id = R.string.avatar_corner_radius),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        OutlinedButton(
                            onClick = {
                                val newValue =
                                    (avatarCornerRadiusInput - 1f).coerceIn(0f, 16f)
                                editorSession.setFloat("avatar_corner_radius", newValue)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription =
                                    stringResource(id = R.string.avatar_corner_decrease),
                            )
                        }

                        Text(
                            text = "${avatarCornerRadiusInput.toInt()} dp",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )

                        OutlinedButton(
                            onClick = {
                                val newValue =
                                    (avatarCornerRadiusInput + 1f).coerceIn(0f, 16f)
                                editorSession.setFloat("avatar_corner_radius", newValue)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription =
                                    stringResource(id = R.string.avatar_corner_increase),
                            )
                        }
                    }
                }
            }
        }
    }
}
