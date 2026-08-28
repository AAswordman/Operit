package com.ai.assistance.operit.ui.features.settings.sections

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.AvatarPicker
import com.ai.assistance.operit.ui.features.settings.components.ChatStyleOption
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession

@Composable
internal fun ThemeSettingsAvatarSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    userAvatarUriInput: String?,
    aiAvatarUriInput: String?,
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
                label = stringResource(id = R.string.ai_avatar_label),
                avatarUri = aiAvatarUriInput,
                onAvatarChange = {
                    onAvatarPickerModeChange("ai")
                    avatarImagePicker.launch("image/*")
                },
                onAvatarReset = {
                    editorSession.setOptionalString("custom_ai_avatar_uri", null)
                },
            )

            Text(
                text = stringResource(R.string.theme_avatar_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
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
                                editorSession.setFloat(
                                    "avatar_corner_radius",
                                    (avatarCornerRadiusInput - 1f).coerceIn(0f, 16f),
                                )
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
                                editorSession.setFloat(
                                    "avatar_corner_radius",
                                    (avatarCornerRadiusInput + 1f).coerceIn(0f, 16f),
                                )
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
