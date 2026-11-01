package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ThemeWorkbenchAvatarEditor(
    editorSession: ThemeEditorSession,
    scope: CoroutineScope,
    displayPreferencesManager: DisplayPreferencesManager,
    userAvatarUriInput: String?,
    aiAvatarUriInput: String?,
    globalUserAvatarUriInput: String?,
    onGlobalUserAvatarUriInputChange: (String?) -> Unit,
    globalUserNameInput: String,
    onGlobalUserNameInputChange: (String) -> Unit,
    avatarShapeInput: String,
    avatarCornerRadiusInput: Float,
    avatarImagePicker: ManagedActivityResultLauncher<String, Uri?>,
    onAvatarPickerModeChange: (String) -> Unit,
) {
    var globalOpened by remember { mutableStateOf(false) }
    fun pick(mode: String) {
        onAvatarPickerModeChange(mode)
        avatarImagePicker.launch("image/*")
    }
    Text(
        stringResource(R.string.avatar_shape_title),
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
    )
    ThemeWorkbenchSegments(
        listOf(
            UserPreferencesManager.AVATAR_SHAPE_CIRCLE to
                stringResource(R.string.avatar_shape_circle),
            UserPreferencesManager.AVATAR_SHAPE_SQUARE to
                stringResource(R.string.avatar_shape_square),
        ),
        avatarShapeInput,
        onSelected = { editorSession.setString("avatar_shape", it) },
    )
    if (avatarShapeInput == UserPreferencesManager.AVATAR_SHAPE_SQUARE)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.avatar_corner_radius) +
                    " · ${avatarCornerRadiusInput.toInt()} dp",
                style = MaterialTheme.typography.bodySmall,
            )
            ThemeWorkbenchSlider(
                avatarCornerRadiusInput,
                { editorSession.setFloat("avatar_corner_radius", it) },
                valueRange = 0f..16f,
                steps = 15,
            )
        }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeWorkbenchAvatarCard(
            stringResource(R.string.theme_workbench_role_user_avatar),
            userAvatarUriInput ?: globalUserAvatarUriInput,
            stringResource(R.string.theme_workbench_restore_global_avatar),
            Modifier.weight(1f),
            avatarShapeInput,
            avatarCornerRadiusInput,
            onPick = { pick("user") },
            onReset = { editorSession.setOptionalString("custom_user_avatar_uri", null) },
        )
        ThemeWorkbenchAvatarCard(
            stringResource(R.string.theme_workbench_role_ai_avatar),
            aiAvatarUriInput,
            stringResource(R.string.theme_workbench_restore_default),
            Modifier.weight(1f),
            avatarShapeInput,
            avatarCornerRadiusInput,
            onPick = { pick("ai") },
            onReset = { editorSession.setOptionalString("custom_ai_avatar_uri", null) },
        )
    }
    Spacer(Modifier.height(8.dp))
    ThemeWorkbenchRow(
        stringResource(R.string.global_user_avatar_label),
        subtitle = stringResource(R.string.theme_workbench_global_avatar_hint),
        trailing = {
            ThemeWorkbenchAvatar(
                globalUserAvatarUriInput,
                avatarShapeInput,
                avatarCornerRadiusInput,
                Modifier.size(28.dp),
            )
        },
        onClick = { globalOpened = true },
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.global_user_name_title),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            globalUserNameInput,
            onGlobalUserNameInputChange,
            Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            placeholder = { Text(stringResource(R.string.global_user_name_label)) },
        )
        Text(
            stringResource(R.string.theme_workbench_global_identity_hint),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (globalOpened)
        ThemeWorkbenchDialog(
            stringResource(R.string.global_user_avatar_label),
            { globalOpened = false },
        ) {
            ThemeWorkbenchAvatarCard(
                stringResource(R.string.global_user_avatar_label),
                globalUserAvatarUriInput,
                stringResource(R.string.theme_workbench_restore_default),
                Modifier.fillMaxWidth().padding(8.dp),
                avatarShapeInput,
                avatarCornerRadiusInput,
                onPick = { pick("global_user") },
                onReset = {
                    onGlobalUserAvatarUriInputChange(null)
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(globalUserAvatarUri = "")
                    }
                },
            )
        }
}

@Composable
private fun ThemeWorkbenchAvatarCard(
    title: String,
    uri: String?,
    resetLabel: String,
    modifier: Modifier,
    shape: String,
    corner: Float,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.clickable(onClick = onPick)) {
                ThemeWorkbenchAvatar(uri, shape, corner, Modifier.size(48.dp))
            }
            Text(title, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onPick) { Text(stringResource(R.string.change)) }
            TextButton(onClick = onReset) {
                Text(resetLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ThemeWorkbenchAvatar(uri: String?, shape: String, corner: Float, modifier: Modifier) {
    val outline =
        if (shape == UserPreferencesManager.AVATAR_SHAPE_CIRCLE) CircleShape
        else RoundedCornerShape(corner.dp)
    Surface(
        modifier.clip(outline),
        shape = outline,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (uri != null)
            AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    null,
                    Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
    }
}
