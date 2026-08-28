package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsBackgroundPreview
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetActionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorValueChangeV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorValueOverridesV1
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsBackgroundTab(shared: ThemeSettingsShared) {
    val editorSession = shared.editorSession
    val editorDocument by editorSession.document.collectAsState()
    val values = editorDocument.draft
    var useBackgroundImageInput by
        remember { mutableStateOf(values.requiredBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage)) }
    var backgroundImageUriInput by
        remember { mutableStateOf(values.string(NativeThemePreferenceSchemaV1.backgroundImageUri)) }
    var backgroundImageOpacityInput by
        remember {
            mutableStateOf(values.requiredFloat(NativeThemePreferenceSchemaV1.backgroundImageOpacity))
        }
    var backgroundMediaTypeInput by
        remember { mutableStateOf(values.requiredString(NativeThemePreferenceSchemaV1.backgroundMediaType)) }
    var videoBackgroundMutedInput by
        remember { mutableStateOf(values.requiredBoolean(NativeThemePreferenceSchemaV1.videoBackgroundMuted)) }
    var videoBackgroundLoopInput by
        remember { mutableStateOf(values.requiredBoolean(NativeThemePreferenceSchemaV1.videoBackgroundLoop)) }
    var useBackgroundBlurInput by
        remember { mutableStateOf(values.requiredBoolean(NativeThemePreferenceSchemaV1.useBackgroundBlur)) }
    var backgroundBlurRadiusInput by
        remember { mutableStateOf(values.requiredFloat(NativeThemePreferenceSchemaV1.backgroundBlurRadius)) }

    LaunchedEffect(values) {
        val persistedMediaUri = values.string(NativeThemePreferenceSchemaV1.backgroundImageUri)
        backgroundImageUriInput = persistedMediaUri
        backgroundMediaTypeInput =
            values.requiredString(NativeThemePreferenceSchemaV1.backgroundMediaType)
        useBackgroundImageInput = values.requiredBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage)
        backgroundImageOpacityInput =
            values.requiredFloat(NativeThemePreferenceSchemaV1.backgroundImageOpacity)
        videoBackgroundMutedInput =
            values.requiredBoolean(NativeThemePreferenceSchemaV1.videoBackgroundMuted)
        videoBackgroundLoopInput =
            values.requiredBoolean(NativeThemePreferenceSchemaV1.videoBackgroundLoop)
        useBackgroundBlurInput = values.requiredBoolean(NativeThemePreferenceSchemaV1.useBackgroundBlur)
        backgroundBlurRadiusInput = values.requiredFloat(NativeThemePreferenceSchemaV1.backgroundBlurRadius)
    }

    val runtime =
        rememberThemeSettingsBackgroundRuntime(
            context = shared.context,
            scope = shared.scope,
            editorSession = editorSession,
            useBackgroundImageInput = useBackgroundImageInput,
            backgroundImageUriInput = backgroundImageUriInput,
            onBackgroundImageUriInputChange = { backgroundImageUriInput = it },
            backgroundMediaTypeInput = backgroundMediaTypeInput,
            onBackgroundMediaTypeInputChange = { backgroundMediaTypeInput = it },
            videoBackgroundMutedInput = videoBackgroundMutedInput,
            videoBackgroundLoopInput = videoBackgroundLoopInput,
        )
    val valueOverrides =
        NativeThemeEditorValueOverridesV1(
            strings =
                mapOf(
                    NativeThemePreferenceSchemaV1.backgroundMediaType.name to backgroundMediaTypeInput,
                ),
            booleans =
                mapOf(
                    NativeThemePreferenceSchemaV1.useBackgroundImage.name to useBackgroundImageInput,
                    NativeThemePreferenceSchemaV1.videoBackgroundMuted.name to videoBackgroundMutedInput,
                    NativeThemePreferenceSchemaV1.videoBackgroundLoop.name to videoBackgroundLoopInput,
                    NativeThemePreferenceSchemaV1.useBackgroundBlur.name to useBackgroundBlurInput,
                ),
            floats =
                mapOf(
                    NativeThemePreferenceSchemaV1.backgroundImageOpacity.name to
                        backgroundImageOpacityInput,
                    NativeThemePreferenceSchemaV1.backgroundBlurRadius.name to backgroundBlurRadiusInput,
                ),
        )

    NativeThemeEditorGroupV1(
        definition = NativeThemeEditorDefinitionV1.backgroundMedia,
        values = values,
        editorSession = editorSession,
        valueOverrides = valueOverrides,
        onAssetRequested = { definition ->
            when (definition.action) {
                NativeThemeAssetActionV1.BACKGROUND_MEDIA ->
                    if (backgroundMediaTypeInput == NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO) {
                        runtime.mediaPickerLauncher.launch("video/*")
                    } else {
                        runtime.mediaPickerLauncher.launch("image/*")
                    }
                NativeThemeAssetActionV1.APP_FONT ->
                    error("Unsupported background asset action: ${definition.action}")
            }
        },
        onValueChanged = { change ->
            when (change) {
                is NativeThemeEditorValueChangeV1.BooleanChanged -> {
                    when (change.definition.field) {
                        NativeThemePreferenceSchemaV1.useBackgroundImage ->
                            useBackgroundImageInput = change.value
                        NativeThemePreferenceSchemaV1.videoBackgroundMuted ->
                            videoBackgroundMutedInput = change.value
                        NativeThemePreferenceSchemaV1.videoBackgroundLoop ->
                            videoBackgroundLoopInput = change.value
                        NativeThemePreferenceSchemaV1.useBackgroundBlur ->
                            useBackgroundBlurInput = change.value
                        else -> error("Unsupported background boolean: ${change.definition.field.name}")
                    }
                    editorSession.setBoolean(change.definition.field, change.value)
                }
                is NativeThemeEditorValueChangeV1.StringChanged -> {
                    if (change.definition.field == NativeThemePreferenceSchemaV1.backgroundMediaType) {
                        val persistedMediaType =
                            values.requiredString(NativeThemePreferenceSchemaV1.backgroundMediaType)
                        val persistedMediaUri =
                            values.string(NativeThemePreferenceSchemaV1.backgroundImageUri)
                        if (persistedMediaUri != null && persistedMediaUri.isNotEmpty() &&
                            change.value != persistedMediaType
                        ) {
                            if (change.value == NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO) {
                                runtime.mediaPickerLauncher.launch("video/*")
                            } else {
                                runtime.mediaPickerLauncher.launch("image/*")
                            }
                        } else {
                            backgroundMediaTypeInput = change.value
                            backgroundImageUriInput = persistedMediaUri
                            editorSession.setString(change.definition.field, change.value)
                        }
                    } else {
                        error("Unsupported background choice: ${change.definition.field.name}")
                    }
                }
                is NativeThemeEditorValueChangeV1.FloatChanged -> {
                    when (change.definition.field) {
                        NativeThemePreferenceSchemaV1.backgroundImageOpacity ->
                            backgroundImageOpacityInput = change.value
                        NativeThemePreferenceSchemaV1.backgroundBlurRadius ->
                            backgroundBlurRadiusInput = change.value
                        else -> error("Unsupported background slider: ${change.definition.field.name}")
                    }
                    if (change.finished) {
                        editorSession.setFloat(change.definition.field, change.value)
                    }
                }
            }
        },
    )

    NativeThemeEditorPreviewTheme(values = values) {
        ThemeSettingsBackgroundPreview(
            exoPlayer = runtime.exoPlayer,
            launchImageCrop = runtime.launchImageCrop,
            useBackgroundMedia = useBackgroundImageInput,
            backgroundMediaType = backgroundMediaTypeInput,
            backgroundImageUri = backgroundImageUriInput,
            backgroundImageOpacity = backgroundImageOpacityInput,
            useBackgroundBlur = useBackgroundBlurInput,
            backgroundBlurRadius = backgroundBlurRadiusInput,
        )
    }
}

internal data class ThemeSettingsBackgroundRuntime(
    val exoPlayer: ExoPlayer,
    val launchImageCrop: (Uri) -> Unit,
    val mediaPickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
)

@Composable
internal fun rememberThemeSettingsBackgroundRuntime(
    context: Context,
    scope: CoroutineScope,
    editorSession: ThemeEditorSession,
    useBackgroundImageInput: Boolean,
    backgroundImageUriInput: String?,
    onBackgroundImageUriInputChange: (String?) -> Unit,
    backgroundMediaTypeInput: String,
    onBackgroundMediaTypeInputChange: (String) -> Unit,
    videoBackgroundMutedInput: Boolean,
    videoBackgroundLoopInput: Boolean,
): ThemeSettingsBackgroundRuntime {
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer =
        remember {
            ExoPlayer.Builder(context)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(5000, 10000, 500, 1000)
                        .setTargetBufferBytes(5 * 1024 * 1024)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build(),
                )
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = if (videoBackgroundMutedInput) 0f else 1f
                    playWhenReady =
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            useBackgroundImageInput &&
                            !backgroundImageUriInput.isNullOrEmpty() &&
                            backgroundMediaTypeInput == NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO

                    if (useBackgroundImageInput && !backgroundImageUriInput.isNullOrEmpty() &&
                        backgroundMediaTypeInput == NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO
                    ) {
                        try {
                            setMediaItem(MediaItem.Builder().setUri(Uri.parse(backgroundImageUriInput)).build())
                            prepare()
                        } catch (e: Exception) {
                            AppLogger.e("ThemeSettings", "Video loading error", e)
                        }
                    }
                }
        }

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "ExoPlayer release error", e)
            }
        }
    }

    LaunchedEffect(
        useBackgroundImageInput,
        backgroundImageUriInput,
        backgroundMediaTypeInput,
    ) {
        try {
            if (useBackgroundImageInput &&
                !backgroundImageUriInput.isNullOrEmpty() &&
                backgroundMediaTypeInput == NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO
            ) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(
                    MediaItem.Builder().setUri(Uri.parse(backgroundImageUriInput)).build(),
                )
                exoPlayer.prepare()
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                } else {
                    exoPlayer.playWhenReady = false
                }
            } else {
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        } catch (e: Exception) {
            AppLogger.e("ThemeSettings", "更新视频来源错误", e)
        }
    }

    val shouldPlayVideo =
        useBackgroundImageInput &&
            !backgroundImageUriInput.isNullOrEmpty() &&
            backgroundMediaTypeInput == NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO
    val latestShouldPlayVideo by rememberUpdatedState(shouldPlayVideo)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer =
            LifecycleEventObserver { _, event ->
                try {
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            exoPlayer.playWhenReady = false
                            exoPlayer.pause()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            if (latestShouldPlayVideo) {
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            }
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    AppLogger.e("ThemeSettings", "Preview player lifecycle error", e)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(videoBackgroundMutedInput, videoBackgroundLoopInput) {
        try {
            exoPlayer.volume = if (videoBackgroundMutedInput) 0f else 1f
            exoPlayer.repeatMode =
                if (videoBackgroundLoopInput) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        } catch (e: Exception) {
            AppLogger.e("ThemeSettings", "更新视频设置错误", e)
        }
    }

    val cropImageLauncher =
        rememberLauncherForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                val croppedUri = result.uriContent
                if (croppedUri != null) {
                    scope.launch {
                        val operationGeneration = editorSession.beginAssetOperation() ?: return@launch
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, croppedUri, "background")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Background image saved to: $internalUri")
                            val internalUriString = internalUri.toString()
                            if (!editorSession.registerStagedAsset(internalUriString, operationGeneration)) {
                                return@launch
                            }
                            onBackgroundImageUriInputChange(internalUriString)
                            onBackgroundMediaTypeInputChange(NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE)
                            editorSession.setOptionalString(
                                NativeThemePreferenceSchemaV1.backgroundImageUri,
                                internalUriString,
                            )
                            editorSession.setString(
                                NativeThemePreferenceSchemaV1.backgroundMediaType,
                                NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE,
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.theme_image_saved),
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

    fun launchImageCrop(uri: Uri) {
        val isNightMode =
            context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val (primaryColor, surfaceColor, onPrimaryColor) =
            try {
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                val primary = typedValue.data
                context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
                val surface = typedValue.data
                val onPrimary =
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                Triple(primary, surface, onPrimary)
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "Unable to resolve cropper colors", e)
                Triple(
                    if (isNightMode) 0xFF9C27B0.toInt() else 0xFF6200EE.toInt(),
                    if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK,
                )
            }

        cropImageLauncher.launch(
            CropImageContractOptions(
                uri,
                CropImageOptions().apply {
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                    outputCompressQuality = 90
                    fixAspectRatio = false
                    cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                    activityTitle = context.getString(R.string.theme_crop_image)
                    toolbarColor = primaryColor
                    toolbarBackButtonColor = onPrimaryColor
                    toolbarTitleColor = onPrimaryColor
                    activityBackgroundColor = surfaceColor
                    backgroundColor = surfaceColor
                    activityMenuIconColor = onPrimaryColor
                    showCropOverlay = true
                    showProgressBar = true
                    multiTouchEnabled = true
                    autoZoomEnabled = true
                },
            ),
        )
    }

    val mediaPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val isVideo = FileUtils.isVideoFile(context, uri)
                if (isVideo) {
                    val isVideoSizeAcceptable = FileUtils.checkVideoSize(context, uri, 30)
                    if (!isVideoSizeAcceptable) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_video_too_large),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        val operationGeneration = editorSession.beginAssetOperation() ?: return@launch
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "background_video")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Background video saved to: $internalUri")
                            val internalUriString = internalUri.toString()
                            if (!editorSession.registerStagedAsset(internalUriString, operationGeneration)) {
                                return@launch
                            }
                            onBackgroundImageUriInputChange(internalUriString)
                            onBackgroundMediaTypeInputChange(NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO)
                            editorSession.setOptionalString(
                                NativeThemePreferenceSchemaV1.backgroundImageUri,
                                internalUriString,
                            )
                            editorSession.setString(
                                NativeThemePreferenceSchemaV1.backgroundMediaType,
                                NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO,
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.theme_video_saved),
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
                } else {
                    launchImageCrop(uri)
                }
            }
        }

    return ThemeSettingsBackgroundRuntime(
        exoPlayer = exoPlayer,
        launchImageCrop = ::launchImageCrop,
        mediaPickerLauncher = mediaPickerLauncher,
    )
}
