package com.ai.assistance.operit.ui.features.settings.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.AvatarPicker
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun GlobalIdentitySection(
    context: Context,
    displayPreferencesManager: DisplayPreferencesManager,
    scope: CoroutineScope,
) {
    val globalUserAvatarUri by
        displayPreferencesManager.globalUserAvatarUri.collectAsState(initial = null)
    val globalUserName by displayPreferencesManager.globalUserName.collectAsState(initial = null)
    var globalUserNameInput by remember(globalUserName) { mutableStateOf(globalUserName ?: "") }

    val cropLauncher =
        rememberLauncherForActivityResult(CropImageContract()) { result ->
            if (!result.isSuccessful) {
                result.error?.let { error ->
                    AppLogger.e("GlobalIdentity", "Avatar crop failed", error)
                }
                return@rememberLauncherForActivityResult
            }
            val croppedUri = result.uriContent ?: return@rememberLauncherForActivityResult
            scope.launch {
                val internalUri =
                    FileUtils.copyFileToInternalStorage(context, croppedUri, "global_user_avatar")
                if (internalUri == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.theme_copy_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                try {
                    displayPreferencesManager.saveDisplaySettings(
                        globalUserAvatarUri = internalUri.toString(),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.avatar_updated),
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (error: CancellationException) {
                    internalUri.path?.let { path -> java.io.File(path).delete() }
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("GlobalIdentity", "Failed to save global avatar", error)
                    Toast.makeText(
                        context,
                        context.getString(R.string.theme_save_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            cropLauncher.launch(
                CropImageContractOptions(
                    uri,
                    CropImageOptions().apply {
                        guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                        outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                        outputCompressQuality = 90
                        fixAspectRatio = true
                        aspectRatioX = 1
                        aspectRatioY = 1
                        cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                        activityTitle = context.getString(R.string.crop_avatar)
                    },
                ),
            )
        }

    fun saveGlobalUserName(value: String) {
        scope.launch {
            try {
                displayPreferencesManager.saveDisplaySettings(globalUserName = value)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("GlobalIdentity", "Failed to save global user name", error)
                Toast.makeText(
                    context,
                    context.getString(R.string.theme_save_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.global_user_name_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = globalUserNameInput,
                onValueChange = { globalUserNameInput = it },
                label = { Text(stringResource(R.string.global_user_name_label)) },
                placeholder = { Text(stringResource(R.string.global_user_name_placeholder)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
            )
            IconButton(
                onClick = {
                    saveGlobalUserName("")
                    globalUserNameInput = ""
                },
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(R.string.clear_action),
                )
            }
            IconButton(
                onClick = {
                    saveGlobalUserName(globalUserNameInput)
                },
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.save_action),
                )
            }
        }
        Text(
            text = stringResource(R.string.global_user_name_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        AvatarPicker(
            label = stringResource(R.string.global_user_avatar_label),
            avatarUri = globalUserAvatarUri,
            onAvatarChange = { imagePickerLauncher.launch("image/*") },
            onAvatarReset = {
                scope.launch {
                    displayPreferencesManager.clearGlobalUserAvatar()
                }
            },
        )
    }
}
