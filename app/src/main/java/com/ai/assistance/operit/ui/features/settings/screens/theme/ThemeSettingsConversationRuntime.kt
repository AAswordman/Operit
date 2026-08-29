package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NinePatchBubbleAutoParams(
    val cropLeftRatio: Float,
    val cropTopRatio: Float,
    val cropRightRatio: Float,
    val cropBottomRatio: Float,
    val repeatXStartRatio: Float,
    val repeatXEndRatio: Float,
    val repeatYStartRatio: Float,
    val repeatYEndRatio: Float,
)

private enum class ThemeSettingsBubbleTarget {
    USER,
    AI,
}

private enum class ThemeSettingsAvatarPickerMode(val uniqueName: String) {
    USER("user_avatar"),
    AI("ai_avatar");

    companion object {
        fun fromKey(key: String): ThemeSettingsAvatarPickerMode =
            when (key) {
                "user" -> USER
                "ai" -> AI
                else -> error("Unsupported avatar picker mode: $key")
            }
    }
}

internal data class ThemeSettingsConversationRuntimeState(
    val context: android.content.Context,
    val scope: CoroutineScope,
    val editorSession: ThemeEditorSession,
)

internal data class ThemeSettingsConversationRuntime(
    val onPickBubbleUserImage: () -> Unit,
    val onPickBubbleAiImage: () -> Unit,
    val onClearBubbleUserImage: () -> Unit,
    val onClearBubbleAiImage: () -> Unit,
    val avatarImagePicker: ManagedActivityResultLauncher<String, Uri?>,
    val onAvatarPickerModeChange: (String) -> Unit,
)

private fun isNinePatchMarker(colorInt: Int): Boolean {
    val alpha = (colorInt ushr 24) and 0xFF
    if (alpha < 0x80) return false
    val red = (colorInt ushr 16) and 0xFF
    val green = (colorInt ushr 8) and 0xFF
    val blue = colorInt and 0xFF
    return red < 32 && green < 32 && blue < 32
}

private fun buildStretchRange(marked: List<Int>, innerSize: Int): Pair<Float, Float>? {
    if (marked.isEmpty() || innerSize <= 0) return null
    val start = marked.first().toFloat() / innerSize.toFloat()
    val endExclusive = (marked.last() + 1).toFloat() / innerSize.toFloat()
    return start.coerceIn(0f, 1f) to endExclusive.coerceIn(0f, 1f)
}

private suspend fun parseNinePatchBubbleParams(
    context: android.content.Context,
    uri: Uri,
): NinePatchBubbleAutoParams? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()?.let { bitmap ->
            val width = bitmap.width
            val height = bitmap.height
            if (width < 3 || height < 3) return@let null

            val innerWidth = width - 2
            val innerHeight = height - 2
            if (innerWidth <= 0 || innerHeight <= 0) return@let null

            val topMarkers = mutableListOf<Int>()
            val leftMarkers = mutableListOf<Int>()
            for (x in 0 until innerWidth) {
                if (isNinePatchMarker(bitmap.getPixel(x + 1, 0))) {
                    topMarkers.add(x)
                }
            }
            for (y in 0 until innerHeight) {
                if (isNinePatchMarker(bitmap.getPixel(0, y + 1))) {
                    leftMarkers.add(y)
                }
            }

            val xRange = buildStretchRange(topMarkers, innerWidth) ?: (0.35f to 0.65f)
            val yRange = buildStretchRange(leftMarkers, innerHeight) ?: (0.35f to 0.65f)

            NinePatchBubbleAutoParams(
                cropLeftRatio = (1f / width.toFloat()).coerceIn(0f, 0.45f),
                cropTopRatio = (1f / height.toFloat()).coerceIn(0f, 0.45f),
                cropRightRatio = (1f / width.toFloat()).coerceIn(0f, 0.45f),
                cropBottomRatio = (1f / height.toFloat()).coerceIn(0f, 0.45f),
                repeatXStartRatio = xRange.first,
                repeatXEndRatio = xRange.second,
                repeatYStartRatio = yRange.first,
                repeatYEndRatio = yRange.second,
            )
        }
    }

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    }.getOrNull()
}

private fun isNinePatchPngUri(context: android.content.Context, uri: Uri): Boolean {
    val displayName = resolveDisplayName(context, uri)?.lowercase()
    if (displayName != null && displayName.endsWith(".9.png")) {
        return true
    }
    val pathName = uri.lastPathSegment?.lowercase()
    return pathName?.endsWith(".9.png") == true
}

@Composable
internal fun rememberThemeSettingsConversationRuntime(
    state: ThemeSettingsConversationRuntimeState,
): ThemeSettingsConversationRuntime {
    val context = state.context
    var bubbleImagePickerTarget by remember { mutableStateOf(ThemeSettingsBubbleTarget.USER) }
    val bubbleImageCropLauncher =
        rememberLauncherForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                val croppedUri = result.uriContent
                if (croppedUri != null) {
                    val target = bubbleImagePickerTarget
                    state.scope.launch {
                        val operationGeneration =
                            state.editorSession.beginAssetOperation() ?: return@launch
                        val uniqueName =
                            when (target) {
                                ThemeSettingsBubbleTarget.AI -> "bubble_ai"
                                ThemeSettingsBubbleTarget.USER -> "bubble_user"
                            }
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, croppedUri, uniqueName)
                        if (internalUri != null) {
                            val internalUriString = internalUri.toString()
                            if (!state.editorSession.registerStagedAsset(internalUriString, operationGeneration)) {
                                return@launch
                            }
                            state.editorSession.update { values ->
                                when (target) {
                                    ThemeSettingsBubbleTarget.AI ->
                                        values
                                            .withString(
                                                NativeThemePreferenceSchemaV1.bubbleAiImageUri,
                                                internalUriString,
                                            )
                                            .withBoolean(
                                                NativeThemePreferenceSchemaV1.bubbleAiUseImage,
                                                !values.requiredBoolean(
                                                    NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass,
                                                ) &&
                                                    !values.requiredBoolean(
                                                        NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass,
                                                    ),
                                            )

                                    ThemeSettingsBubbleTarget.USER ->
                                        values
                                            .withString(
                                                NativeThemePreferenceSchemaV1.bubbleUserImageUri,
                                                internalUriString,
                                            )
                                            .withBoolean(
                                                NativeThemePreferenceSchemaV1.bubbleUserUseImage,
                                                !values.requiredBoolean(
                                                    NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass,
                                                ) &&
                                                    !values.requiredBoolean(
                                                        NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass,
                                                    ),
                                            )
                                }
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.chat_style_bubble_image_saved),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.theme_copy_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            } else if (result.error != null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.theme_image_crop_failed, result.error!!.message),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    fun launchBubbleImageCrop(uri: Uri) {
        val cropOptions =
            CropImageContractOptions(
                uri,
                CropImageOptions().apply {
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                    outputCompressQuality = 90
                    fixAspectRatio = false
                    cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                    activityTitle = context.getString(R.string.theme_crop_image)
                    showCropOverlay = true
                    showProgressBar = true
                    multiTouchEnabled = true
                    autoZoomEnabled = true
                },
            )
        bubbleImageCropLauncher.launch(cropOptions)
    }

    val bubbleImagePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                if (!isNinePatchPngUri(context, uri)) {
                    launchBubbleImageCrop(uri)
                    return@rememberLauncherForActivityResult
                }

                val target = bubbleImagePickerTarget
                state.scope.launch {
                    val operationGeneration =
                        state.editorSession.beginAssetOperation() ?: return@launch
                    val uniqueName =
                        when (target) {
                            ThemeSettingsBubbleTarget.AI -> "bubble_ai"
                            ThemeSettingsBubbleTarget.USER -> "bubble_user"
                        }
                    val internalUri =
                        FileUtils.copyFileToInternalStorage(context, uri, uniqueName)
                    if (internalUri == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_copy_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }

                    val autoParams =
                        parseNinePatchBubbleParams(context, uri)
                            ?: parseNinePatchBubbleParams(context, internalUri)
                    if (autoParams == null) {
                        internalUri.path?.let { path -> File(path).delete() }
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_copy_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }

                    val internalUriString = internalUri.toString()
                    if (!state.editorSession.registerStagedAsset(internalUriString, operationGeneration)) {
                        return@launch
                    }
                    val renderMode = NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH
                    state.editorSession.update { values ->
                        val updated =
                            values.withString(
                                NativeThemePreferenceSchemaV1.bubbleImageRenderMode,
                                renderMode,
                            )
                        when (target) {
                            ThemeSettingsBubbleTarget.AI ->
                                updated
                                    .withString(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageUri,
                                        internalUriString,
                                    )
                                    .withBoolean(
                                        NativeThemePreferenceSchemaV1.bubbleAiUseImage,
                                        !values.requiredBoolean(
                                            NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass,
                                        ) &&
                                            !values.requiredBoolean(
                                                NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass,
                                            ),
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageCropLeft,
                                        autoParams.cropLeftRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageCropTop,
                                        autoParams.cropTopRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageCropRight,
                                        autoParams.cropRightRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageCropBottom,
                                        autoParams.cropBottomRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatStart,
                                        autoParams.repeatXStartRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatEnd,
                                        autoParams.repeatXEndRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYStart,
                                        autoParams.repeatYStartRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYEnd,
                                        autoParams.repeatYEndRatio,
                                    )
                                    .withFloat(NativeThemePreferenceSchemaV1.bubbleAiImageScale, 1f)

                            ThemeSettingsBubbleTarget.USER ->
                                updated
                                    .withString(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageUri,
                                        internalUriString,
                                    )
                                    .withBoolean(
                                        NativeThemePreferenceSchemaV1.bubbleUserUseImage,
                                        !values.requiredBoolean(
                                            NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass,
                                        ) &&
                                            !values.requiredBoolean(
                                                NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass,
                                            ),
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageCropLeft,
                                        autoParams.cropLeftRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageCropTop,
                                        autoParams.cropTopRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageCropRight,
                                        autoParams.cropRightRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageCropBottom,
                                        autoParams.cropBottomRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatStart,
                                        autoParams.repeatXStartRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatEnd,
                                        autoParams.repeatXEndRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYStart,
                                        autoParams.repeatYStartRatio,
                                    )
                                    .withFloat(
                                        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYEnd,
                                        autoParams.repeatYEndRatio,
                                    )
                                    .withFloat(NativeThemePreferenceSchemaV1.bubbleUserImageScale, 1f)
                        }
                    }

                    Toast.makeText(
                        context,
                        context.getString(R.string.chat_style_bubble_image_saved),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    var avatarPickerMode by remember { mutableStateOf(ThemeSettingsAvatarPickerMode.USER) }
    val cropAvatarLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                val pickerMode = avatarPickerMode
                state.scope.launch {
                    val operationGeneration =
                        state.editorSession.beginAssetOperation() ?: return@launch
                    val internalUri =
                        FileUtils.copyFileToInternalStorage(
                            context,
                            croppedUri,
                            pickerMode.uniqueName,
                        )
                    if (internalUri != null) {
                        if (!state.editorSession.registerStagedAsset(internalUri.toString(), operationGeneration)) {
                            return@launch
                        }
                        when (pickerMode) {
                            ThemeSettingsAvatarPickerMode.USER -> {
                                AppLogger.d("ThemeSettings", "User avatar saved to: $internalUri")
                                state.editorSession.setOptionalString(
                                    NativeThemePreferenceSchemaV1.customUserAvatarUri,
                                    internalUri.toString(),
                                )
                            }
                            ThemeSettingsAvatarPickerMode.AI -> {
                                AppLogger.d("ThemeSettings", "AI avatar saved to: $internalUri")
                                state.editorSession.setOptionalString(
                                    NativeThemePreferenceSchemaV1.customAiAvatarUri,
                                    internalUri.toString(),
                                )
                            }
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.avatar_updated),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_copy_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        } else if (result.error != null) {
            Toast.makeText(
                context,
                context.getString(R.string.avatar_crop_failed, result.error!!.message),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun launchAvatarCrop(uri: Uri) {
        val cropOptions =
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
                    toolbarColor = Color.Gray.toArgb()
                    toolbarTitleColor = Color.White.toArgb()
                },
            )
        cropAvatarLauncher.launch(cropOptions)
    }

    val avatarImagePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                launchAvatarCrop(uri)
            }
        }

    return ThemeSettingsConversationRuntime(
        onPickBubbleUserImage = {
            bubbleImagePickerTarget = ThemeSettingsBubbleTarget.USER
            bubbleImagePickerLauncher.launch("image/*")
        },
        onPickBubbleAiImage = {
            bubbleImagePickerTarget = ThemeSettingsBubbleTarget.AI
            bubbleImagePickerLauncher.launch("image/*")
        },
        onClearBubbleUserImage = {
            state.editorSession.update { values ->
                values
                    .withString(NativeThemePreferenceSchemaV1.bubbleUserImageUri, null)
                    .withBoolean(NativeThemePreferenceSchemaV1.bubbleUserUseImage, false)
            }
        },
        onClearBubbleAiImage = {
            state.editorSession.update { values ->
                values
                    .withString(NativeThemePreferenceSchemaV1.bubbleAiImageUri, null)
                    .withBoolean(NativeThemePreferenceSchemaV1.bubbleAiUseImage, false)
            }
        },
        avatarImagePicker = avatarImagePicker,
        onAvatarPickerModeChange = {
            avatarPickerMode = ThemeSettingsAvatarPickerMode.fromKey(it)
        },
    )
}

internal data class BubbleFontPicker(
    val onPickBubbleUserFont: () -> Unit,
    val onPickBubbleAiFont: () -> Unit,
)

@Composable
internal fun rememberBubbleFontPicker(shared: ThemeSettingsShared): BubbleFontPicker {
    val context = shared.context
    var targetName by remember { mutableStateOf("bubble_user_font") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val selectedTargetName = targetName
            shared.scope.launch {
                val operationGeneration =
                    shared.editorSession.beginAssetOperation() ?: return@launch
                val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                if (extension != null && (extension == "ttf" || extension == "otf" || extension == "ttc")) {
                    val internalUri =
                        FileUtils.copyFileToInternalStorage(context, uri, selectedTargetName)
                    if (internalUri != null) {
                        val isUser = selectedTargetName == "bubble_user_font"
                        val internalUriString = internalUri.toString()
                        if (!shared.editorSession.registerStagedAsset(internalUriString, operationGeneration)) {
                            return@launch
                        }
                        shared.editorSession.update { values ->
                            if (isUser) {
                                values
                                    .withString(
                                        NativeThemePreferenceSchemaV1.bubbleUserCustomFontPath,
                                        internalUriString,
                                    )
                                    .withString(
                                        NativeThemePreferenceSchemaV1.bubbleUserFontType,
                                        NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                                    )
                            } else {
                                values
                                    .withString(
                                        NativeThemePreferenceSchemaV1.bubbleAiCustomFontPath,
                                        internalUriString,
                                    )
                                    .withString(
                                        NativeThemePreferenceSchemaV1.bubbleAiFontType,
                                        NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                                    )
                            }
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.font_file_saved, extension),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.font_file_save_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.unsupported_font_format),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    return BubbleFontPicker(
        onPickBubbleUserFont = {
            targetName = "bubble_user_font"
            launcher.launch("*/*")
        },
        onPickBubbleAiFont = {
            targetName = "bubble_ai_font"
            launcher.launch("*/*")
        },
    )
}
