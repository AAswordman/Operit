package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsAvatarSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsChatStyleSection
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorTargetV1
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
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

internal data class ThemeSettingsChatRuntimeState(
    val context: android.content.Context,
    val scope: CoroutineScope,
    val editorSession: ThemeEditorSession,
)

internal data class ThemeSettingsChatRuntime(
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
internal fun rememberThemeSettingsChatRuntime(
    state: ThemeSettingsChatRuntimeState,
): ThemeSettingsChatRuntime {
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
                                            .withString("bubble_ai_image_uri", internalUriString)
                                            .withBoolean(
                                                "bubble_ai_use_image",
                                                !values.requiredBoolean("bubble_ai_bubble_liquid_glass") &&
                                                    !values.requiredBoolean("bubble_ai_bubble_water_glass"),
                                            )

                                    ThemeSettingsBubbleTarget.USER ->
                                        values
                                            .withString("bubble_user_image_uri", internalUriString)
                                            .withBoolean(
                                                "bubble_user_use_image",
                                                !values.requiredBoolean("bubble_user_bubble_liquid_glass") &&
                                                    !values.requiredBoolean("bubble_user_bubble_water_glass"),
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
                    val renderMode = UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH
                    state.editorSession.update { values ->
                        val updated = values.withString("bubble_image_render_mode", renderMode)
                        when (target) {
                            ThemeSettingsBubbleTarget.AI ->
                                updated
                                    .withString("bubble_ai_image_uri", internalUriString)
                                    .withBoolean(
                                        "bubble_ai_use_image",
                                        !values.requiredBoolean("bubble_ai_bubble_liquid_glass") &&
                                            !values.requiredBoolean("bubble_ai_bubble_water_glass"),
                                    )
                                    .withFloat("bubble_ai_image_crop_left", autoParams.cropLeftRatio)
                                    .withFloat("bubble_ai_image_crop_top", autoParams.cropTopRatio)
                                    .withFloat("bubble_ai_image_crop_right", autoParams.cropRightRatio)
                                    .withFloat("bubble_ai_image_crop_bottom", autoParams.cropBottomRatio)
                                    .withFloat("bubble_ai_image_repeat_start", autoParams.repeatXStartRatio)
                                    .withFloat("bubble_ai_image_repeat_end", autoParams.repeatXEndRatio)
                                    .withFloat("bubble_ai_image_repeat_y_start", autoParams.repeatYStartRatio)
                                    .withFloat("bubble_ai_image_repeat_y_end", autoParams.repeatYEndRatio)
                                    .withFloat("bubble_ai_image_scale", 1f)

                            ThemeSettingsBubbleTarget.USER ->
                                updated
                                    .withString("bubble_user_image_uri", internalUriString)
                                    .withBoolean(
                                        "bubble_user_use_image",
                                        !values.requiredBoolean("bubble_user_bubble_liquid_glass") &&
                                            !values.requiredBoolean("bubble_user_bubble_water_glass"),
                                    )
                                    .withFloat("bubble_user_image_crop_left", autoParams.cropLeftRatio)
                                    .withFloat("bubble_user_image_crop_top", autoParams.cropTopRatio)
                                    .withFloat("bubble_user_image_crop_right", autoParams.cropRightRatio)
                                    .withFloat("bubble_user_image_crop_bottom", autoParams.cropBottomRatio)
                                    .withFloat("bubble_user_image_repeat_start", autoParams.repeatXStartRatio)
                                    .withFloat("bubble_user_image_repeat_end", autoParams.repeatXEndRatio)
                                    .withFloat("bubble_user_image_repeat_y_start", autoParams.repeatYStartRatio)
                                    .withFloat("bubble_user_image_repeat_y_end", autoParams.repeatYEndRatio)
                                    .withFloat("bubble_user_image_scale", 1f)
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
                                    "custom_user_avatar_uri",
                                    internalUri.toString(),
                                )
                            }
                            ThemeSettingsAvatarPickerMode.AI -> {
                                AppLogger.d("ThemeSettings", "AI avatar saved to: $internalUri")
                                state.editorSession.setOptionalString(
                                    "custom_ai_avatar_uri",
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

    return ThemeSettingsChatRuntime(
        onPickBubbleUserImage = {
            bubbleImagePickerTarget = ThemeSettingsBubbleTarget.USER
            bubbleImagePickerLauncher.launch("image/*")
        },
        onPickBubbleAiImage = {
            bubbleImagePickerTarget = ThemeSettingsBubbleTarget.AI
            bubbleImagePickerLauncher.launch("image/*")
        },
        onClearBubbleUserImage = {
            state.editorSession.setOptionalString("bubble_user_image_uri", null)
            state.editorSession.setBoolean("bubble_user_use_image", false)
        },
        onClearBubbleAiImage = {
            state.editorSession.setOptionalString("bubble_ai_image_uri", null)
            state.editorSession.setBoolean("bubble_ai_use_image", false)
        },
        avatarImagePicker = avatarImagePicker,
        onAvatarPickerModeChange = {
            avatarPickerMode = ThemeSettingsAvatarPickerMode.fromKey(it)
        },
    )
}

@Composable
internal fun ThemeSettingsChatTab(
    shared: ThemeSettingsShared,
    cardColors: androidx.compose.material3.CardColors,
) {
    val editorSession = shared.editorSession
    val displayPreferencesManager = remember {
        DisplayPreferencesManager.getInstance(shared.context)
    }
    val editorDocument by editorSession.document.collectAsState()
    val values = editorDocument.draft
    val defaultCursorUserBubbleColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val defaultBubbleUserBubbleColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val defaultBubbleAiBubbleColor = MaterialTheme.colorScheme.surface.toArgb()

    val chatStyleInput = values.requiredString("chat_style")
    val bubbleShowAvatarInput = values.requiredBoolean("bubble_show_avatar")
    val bubbleWideLayoutEnabledInput = values.requiredBoolean("bubble_wide_layout_enabled")
    val cursorUserBubbleFollowThemeInput = values.requiredBoolean("cursor_user_bubble_follow_theme")
    val cursorUserBubbleLiquidGlassInput = values.requiredBoolean("cursor_user_bubble_liquid_glass")
    val cursorUserBubbleWaterGlassInput = values.requiredBoolean("cursor_user_bubble_water_glass")
    val bubbleUserBubbleLiquidGlassInput = values.requiredBoolean("bubble_user_bubble_liquid_glass")
    val bubbleUserBubbleWaterGlassInput = values.requiredBoolean("bubble_user_bubble_water_glass")
    val bubbleAiBubbleLiquidGlassInput = values.requiredBoolean("bubble_ai_bubble_liquid_glass")
    val bubbleAiBubbleWaterGlassInput = values.requiredBoolean("bubble_ai_bubble_water_glass")
    val cursorUserBubbleColorInput = values.int("cursor_user_bubble_color") ?: defaultCursorUserBubbleColor
    val bubbleUserBubbleColorInput = values.int("bubble_user_bubble_color") ?: defaultBubbleUserBubbleColor
    val bubbleAiBubbleColorInput = values.int("bubble_ai_bubble_color") ?: defaultBubbleAiBubbleColor
    val bubbleUserTextColorInput =
        values.int("bubble_user_text_color")
            ?: getTextColorForBackground(Color(bubbleUserBubbleColorInput)).toArgb()
    val bubbleAiTextColorInput =
        values.int("bubble_ai_text_color")
            ?: getTextColorForBackground(Color(bubbleAiBubbleColorInput)).toArgb()
    val bubbleUserUseCustomFontInput = values.requiredBoolean("bubble_user_use_custom_font")
    val bubbleUserFontTypeInput = values.requiredString("bubble_user_font_type")
    val bubbleUserSystemFontNameInput = values.requiredString("bubble_user_system_font_name")
    val bubbleUserCustomFontPathInput = values.string("bubble_user_custom_font_path")
    val bubbleAiUseCustomFontInput = values.requiredBoolean("bubble_ai_use_custom_font")
    val bubbleAiFontTypeInput = values.requiredString("bubble_ai_font_type")
    val bubbleAiSystemFontNameInput = values.requiredString("bubble_ai_system_font_name")
    val bubbleAiCustomFontPathInput = values.string("bubble_ai_custom_font_path")
    val bubbleUserUseImageInput = values.requiredBoolean("bubble_user_use_image")
    val bubbleAiUseImageInput = values.requiredBoolean("bubble_ai_use_image")
    val bubbleUserImageUriInput = values.string("bubble_user_image_uri")
    val bubbleAiImageUriInput = values.string("bubble_ai_image_uri")
    val bubbleUserImageCropLeftInput = values.requiredFloat("bubble_user_image_crop_left")
    val bubbleUserImageCropTopInput = values.requiredFloat("bubble_user_image_crop_top")
    val bubbleUserImageCropRightInput = values.requiredFloat("bubble_user_image_crop_right")
    val bubbleUserImageCropBottomInput = values.requiredFloat("bubble_user_image_crop_bottom")
    val bubbleUserImageRepeatStartInput = values.requiredFloat("bubble_user_image_repeat_start")
    val bubbleUserImageRepeatEndInput = values.requiredFloat("bubble_user_image_repeat_end")
    val bubbleUserImageRepeatYStartInput = values.requiredFloat("bubble_user_image_repeat_y_start")
    val bubbleUserImageRepeatYEndInput = values.requiredFloat("bubble_user_image_repeat_y_end")
    val bubbleUserImageScaleInput = values.requiredFloat("bubble_user_image_scale")
    val bubbleAiImageCropLeftInput = values.requiredFloat("bubble_ai_image_crop_left")
    val bubbleAiImageCropTopInput = values.requiredFloat("bubble_ai_image_crop_top")
    val bubbleAiImageCropRightInput = values.requiredFloat("bubble_ai_image_crop_right")
    val bubbleAiImageCropBottomInput = values.requiredFloat("bubble_ai_image_crop_bottom")
    val bubbleAiImageRepeatStartInput = values.requiredFloat("bubble_ai_image_repeat_start")
    val bubbleAiImageRepeatEndInput = values.requiredFloat("bubble_ai_image_repeat_end")
    val bubbleAiImageRepeatYStartInput = values.requiredFloat("bubble_ai_image_repeat_y_start")
    val bubbleAiImageRepeatYEndInput = values.requiredFloat("bubble_ai_image_repeat_y_end")
    val bubbleAiImageScaleInput = values.requiredFloat("bubble_ai_image_scale")
    val bubbleImageRenderModeInput = values.requiredString("bubble_image_render_mode")
    val bubbleUserRoundedCornersEnabledInput = values.requiredBoolean("bubble_rounded_corners_enabled")
    val bubbleAiRoundedCornersEnabledInput = values.requiredBoolean("bubble_ai_rounded_corners_enabled")
    val bubbleUserContentPaddingLeftInput = values.requiredFloat("bubble_content_padding_left")
    val bubbleUserContentPaddingRightInput = values.requiredFloat("bubble_content_padding_right")
    val bubbleAiContentPaddingLeftInput = values.requiredFloat("bubble_ai_content_padding_left")
    val bubbleAiContentPaddingRightInput = values.requiredFloat("bubble_ai_content_padding_right")
    val userAvatarUriInput = values.string("custom_user_avatar_uri")
    val aiAvatarUriInput = values.string("custom_ai_avatar_uri")
    val globalUserAvatarUri by displayPreferencesManager.globalUserAvatarUri.collectAsState(initial = null)
    val avatarShapeInput = values.requiredString("avatar_shape")
    val avatarCornerRadiusInput = values.requiredFloat("avatar_corner_radius")
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    var colorPickerTarget by remember { mutableStateOf<NativeThemeColorTargetV1?>(null) }

    val bubbleFontPicker = rememberBubbleFontPicker(shared = shared)
    val runtime = rememberThemeSettingsChatRuntime(
        state = ThemeSettingsChatRuntimeState(
            context = shared.context,
            scope = shared.scope,
            editorSession = editorSession,
        ),
    )

    OutlinedTextField(
        value = values.string("custom_chat_title") ?: "",
        onValueChange = { editorSession.setOptionalString("custom_chat_title", it) },
        label = { Text(stringResource(R.string.custom_chat_title_label)) },
        placeholder = { Text(stringResource(R.string.custom_chat_title_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    ThemeSettingsChatStyleSection(
            cardColors = cardColors,
            editorSession = editorSession,
            chatStyleInput = chatStyleInput,
        bubbleShowAvatarInput = bubbleShowAvatarInput,
        bubbleWideLayoutEnabledInput = bubbleWideLayoutEnabledInput,
        cursorUserBubbleFollowThemeInput = cursorUserBubbleFollowThemeInput,
        cursorUserBubbleLiquidGlassInput = cursorUserBubbleLiquidGlassInput,
        cursorUserBubbleWaterGlassInput = cursorUserBubbleWaterGlassInput,
        bubbleUserBubbleLiquidGlassInput = bubbleUserBubbleLiquidGlassInput,
        bubbleUserBubbleWaterGlassInput = bubbleUserBubbleWaterGlassInput,
        bubbleAiBubbleLiquidGlassInput = bubbleAiBubbleLiquidGlassInput,
        bubbleAiBubbleWaterGlassInput = bubbleAiBubbleWaterGlassInput,
        cursorUserBubbleColorInput = cursorUserBubbleColorInput,
        bubbleUserBubbleColorInput = bubbleUserBubbleColorInput,
        bubbleAiBubbleColorInput = bubbleAiBubbleColorInput,
        bubbleUserTextColorInput = bubbleUserTextColorInput,
        bubbleAiTextColorInput = bubbleAiTextColorInput,
        bubbleUserUseCustomFontInput = bubbleUserUseCustomFontInput,
        bubbleUserFontTypeInput = bubbleUserFontTypeInput,
        bubbleUserSystemFontNameInput = bubbleUserSystemFontNameInput,
        bubbleUserCustomFontPathInput = bubbleUserCustomFontPathInput,
        onPickBubbleUserFont = bubbleFontPicker.onPickBubbleUserFont,
        bubbleAiUseCustomFontInput = bubbleAiUseCustomFontInput,
        bubbleAiFontTypeInput = bubbleAiFontTypeInput,
        bubbleAiSystemFontNameInput = bubbleAiSystemFontNameInput,
        bubbleAiCustomFontPathInput = bubbleAiCustomFontPathInput,
        onPickBubbleAiFont = bubbleFontPicker.onPickBubbleAiFont,
        previewUserAvatarUri = userAvatarUriInput ?: globalUserAvatarUri,
        previewAiAvatarUri = aiAvatarUriInput,
        avatarShapeInput = avatarShapeInput,
        avatarCornerRadiusInput = avatarCornerRadiusInput,
        onShowColorPicker = { target -> colorPickerTarget = target },
        bubbleUserUseImageInput = bubbleUserUseImageInput,
        bubbleAiUseImageInput = bubbleAiUseImageInput,
        bubbleUserImageUriInput = bubbleUserImageUriInput,
        bubbleAiImageUriInput = bubbleAiImageUriInput,
        onPickBubbleUserImage = runtime.onPickBubbleUserImage,
        onPickBubbleAiImage = runtime.onPickBubbleAiImage,
        onClearBubbleUserImage = runtime.onClearBubbleUserImage,
        onClearBubbleAiImage = runtime.onClearBubbleAiImage,
        bubbleUserImageCropLeftInput = bubbleUserImageCropLeftInput,
        bubbleUserImageCropTopInput = bubbleUserImageCropTopInput,
        bubbleUserImageCropRightInput = bubbleUserImageCropRightInput,
        bubbleUserImageCropBottomInput = bubbleUserImageCropBottomInput,
        bubbleUserImageRepeatStartInput = bubbleUserImageRepeatStartInput,
        bubbleUserImageRepeatEndInput = bubbleUserImageRepeatEndInput,
        bubbleUserImageRepeatYStartInput = bubbleUserImageRepeatYStartInput,
        bubbleUserImageRepeatYEndInput = bubbleUserImageRepeatYEndInput,
        bubbleUserImageScaleInput = bubbleUserImageScaleInput,
        bubbleAiImageCropLeftInput = bubbleAiImageCropLeftInput,
        bubbleAiImageCropTopInput = bubbleAiImageCropTopInput,
        bubbleAiImageCropRightInput = bubbleAiImageCropRightInput,
        bubbleAiImageCropBottomInput = bubbleAiImageCropBottomInput,
        bubbleAiImageRepeatStartInput = bubbleAiImageRepeatStartInput,
        bubbleAiImageRepeatEndInput = bubbleAiImageRepeatEndInput,
        bubbleAiImageRepeatYStartInput = bubbleAiImageRepeatYStartInput,
        bubbleAiImageRepeatYEndInput = bubbleAiImageRepeatYEndInput,
        bubbleAiImageScaleInput = bubbleAiImageScaleInput,
        bubbleImageRenderModeInput = bubbleImageRenderModeInput,
        bubbleUserRoundedCornersEnabledInput = bubbleUserRoundedCornersEnabledInput,
        bubbleAiRoundedCornersEnabledInput = bubbleAiRoundedCornersEnabledInput,
        bubbleUserContentPaddingLeftInput = bubbleUserContentPaddingLeftInput,
        bubbleUserContentPaddingRightInput = bubbleUserContentPaddingRightInput,
        bubbleAiContentPaddingLeftInput = bubbleAiContentPaddingLeftInput,
            bubbleAiContentPaddingRightInput = bubbleAiContentPaddingRightInput,
    )

    ThemeSettingsAvatarSection(
            cardColors = cardColors,
            editorSession = editorSession,
            userAvatarUriInput = userAvatarUriInput,
            aiAvatarUriInput = aiAvatarUriInput,
            avatarShapeInput = avatarShapeInput,
            avatarCornerRadiusInput = avatarCornerRadiusInput,
            avatarImagePicker = runtime.avatarImagePicker,
            onAvatarPickerModeChange = runtime.onAvatarPickerModeChange,
    )

    colorPickerTarget?.let { target ->
        val initialColor =
            when (target) {
                NativeThemeColorTargetV1.CURSOR_USER_BUBBLE -> cursorUserBubbleColorInput
                NativeThemeColorTargetV1.BUBBLE_USER_BUBBLE -> bubbleUserBubbleColorInput
                NativeThemeColorTargetV1.BUBBLE_AI_BUBBLE -> bubbleAiBubbleColorInput
                NativeThemeColorTargetV1.BUBBLE_USER_TEXT -> bubbleUserTextColorInput
                NativeThemeColorTargetV1.BUBBLE_AI_TEXT -> bubbleAiTextColorInput
                else -> error("Unsupported chat color target: $target")
            }
        key(target) {
            ColorPickerDialog(
                initialColor = initialColor,
                title = target.pickerTitle.localizedText(),
                recentColors = recentColors,
                onColorSelected = { color ->
                    editorSession.setInt(target.field, color)
                    shared.scope.launch { editorSession.addRecentColor(color) }
                },
                onDismiss = { colorPickerTarget = null },
            )
        }
    }
}

private data class BubbleFontPicker(
    val onPickBubbleUserFont: () -> Unit,
    val onPickBubbleAiFont: () -> Unit,
)

@Composable
private fun rememberBubbleFontPicker(shared: ThemeSettingsShared): BubbleFontPicker {
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
                                    .withString("bubble_user_custom_font_path", internalUriString)
                                    .withString(
                                        "bubble_user_font_type",
                                        UserPreferencesManager.FONT_TYPE_FILE,
                                    )
                            } else {
                                values
                                    .withString("bubble_ai_custom_font_path", internalUriString)
                                    .withString(
                                        "bubble_ai_font_type",
                                        UserPreferencesManager.FONT_TYPE_FILE,
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
