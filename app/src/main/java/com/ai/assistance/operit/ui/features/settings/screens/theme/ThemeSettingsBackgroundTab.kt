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
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsBackgroundSection
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
internal fun ThemeSettingsBackgroundTab(
    shared: ThemeSettingsShared,
    cardColors: androidx.compose.material3.CardColors,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    val editorSession = shared.editorSession
    val values by editorSession.values.collectAsState()
    val useBackgroundImage = values.requiredBoolean("use_background_image")
    val backgroundImageUri = values.string("background_image_uri")
    val backgroundImageOpacity = values.requiredFloat("background_image_opacity")
    val backgroundMediaType = values.requiredString("background_media_type")
    val videoBackgroundMuted = values.requiredBoolean("video_background_muted")
    val videoBackgroundLoop = values.requiredBoolean("video_background_loop")
    val useBackgroundBlur = values.requiredBoolean("use_background_blur")
    val backgroundBlurRadius = values.requiredFloat("background_blur_radius")
    var useBackgroundImageInput by remember { mutableStateOf(useBackgroundImage) }
    var backgroundImageUriInput by remember { mutableStateOf(backgroundImageUri) }
    var backgroundImageOpacityInput by remember { mutableStateOf(backgroundImageOpacity) }
    var backgroundMediaTypeInput by remember { mutableStateOf(backgroundMediaType) }
    var videoBackgroundMutedInput by remember { mutableStateOf(videoBackgroundMuted) }
    var videoBackgroundLoopInput by remember { mutableStateOf(videoBackgroundLoop) }
    var useBackgroundBlurInput by remember { mutableStateOf(useBackgroundBlur) }
    var backgroundBlurRadiusInput by remember { mutableStateOf(backgroundBlurRadius) }

    LaunchedEffect(
        useBackgroundImage,
        backgroundImageUri,
        backgroundImageOpacity,
        backgroundMediaType,
        videoBackgroundMuted,
        videoBackgroundLoop,
        useBackgroundBlur,
        backgroundBlurRadius,
    ) {
        useBackgroundImageInput = useBackgroundImage
        backgroundImageUriInput = backgroundImageUri
        backgroundImageOpacityInput = backgroundImageOpacity
        backgroundMediaTypeInput = backgroundMediaType
        videoBackgroundMutedInput = videoBackgroundMuted
        videoBackgroundLoopInput = videoBackgroundLoop
        useBackgroundBlurInput = useBackgroundBlur
        backgroundBlurRadiusInput = backgroundBlurRadius
    }

    val runtime = rememberThemeSettingsBackgroundRuntime(
        context = shared.context,
        scope = shared.scope,
        editorSession = editorSession,
        backgroundImageUriInput = backgroundImageUriInput,
        onBackgroundImageUriInputChange = { backgroundImageUriInput = it },
        backgroundMediaTypeInput = backgroundMediaTypeInput,
        onBackgroundMediaTypeInputChange = { backgroundMediaTypeInput = it },
        videoBackgroundMutedInput = videoBackgroundMutedInput,
        videoBackgroundLoopInput = videoBackgroundLoopInput,
    )

    ThemeSettingsBackgroundSection(
        cardColors = cardColors,
        context = shared.context,
        editorSession = editorSession,
        exoPlayer = runtime.exoPlayer,
        launchImageCrop = runtime.launchImageCrop,
        mediaPickerLauncher = runtime.mediaPickerLauncher,
        scrollState = scrollState,
        useBackgroundImageInput = useBackgroundImageInput,
        onUseBackgroundImageInputChange = { useBackgroundImageInput = it },
        backgroundMediaTypeInput = backgroundMediaTypeInput,
        onBackgroundMediaTypeInputChange = { backgroundMediaTypeInput = it },
        backgroundImageUriInput = backgroundImageUriInput,
        backgroundImageOpacityInput = backgroundImageOpacityInput,
        onBackgroundImageOpacityInputChange = { backgroundImageOpacityInput = it },
        videoBackgroundMutedInput = videoBackgroundMutedInput,
        onVideoBackgroundMutedInputChange = { videoBackgroundMutedInput = it },
        videoBackgroundLoopInput = videoBackgroundLoopInput,
        onVideoBackgroundLoopInputChange = { videoBackgroundLoopInput = it },
        useBackgroundBlurInput = useBackgroundBlurInput,
        onUseBackgroundBlurInputChange = { useBackgroundBlurInput = it },
        backgroundBlurRadiusInput = backgroundBlurRadiusInput,
        onBackgroundBlurRadiusInputChange = { backgroundBlurRadiusInput = it },
    )
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
    backgroundImageUriInput: String?,
    onBackgroundImageUriInputChange: (String?) -> Unit,
    backgroundMediaTypeInput: String,
    onBackgroundMediaTypeInputChange: (String) -> Unit,
    videoBackgroundMutedInput: Boolean,
    videoBackgroundLoopInput: Boolean,
): ThemeSettingsBackgroundRuntime {
    val exoPlayer = remember {
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
                playWhenReady = true

                if (!backgroundImageUriInput.isNullOrEmpty() &&
                    backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO
                ) {
                    try {
                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(backgroundImageUriInput))
                            .build()
                        setMediaItem(mediaItem)
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

    LaunchedEffect(backgroundImageUriInput, backgroundMediaTypeInput) {
        if (!backgroundImageUriInput.isNullOrEmpty() &&
            backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO
        ) {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(
                    MediaItem.Builder().setUri(Uri.parse(backgroundImageUriInput)).build(),
                )
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "更新视频来源错误", e)
            }
        }
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
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, croppedUri, "background")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Background image saved to: $internalUri")
                            editorSession.registerStagedAsset(internalUri.toString())
                            onBackgroundImageUriInputChange(internalUri.toString())
                            onBackgroundMediaTypeInputChange(UserPreferencesManager.MEDIA_TYPE_IMAGE)
                            editorSession.setOptionalString(
                                "background_image_uri",
                                internalUri.toString(),
                            )
                            editorSession.setString(
                                "background_media_type",
                                UserPreferencesManager.MEDIA_TYPE_IMAGE,
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
        var primaryColor: Int
        var onPrimaryColor: Int
        var surfaceColor: Int
        var statusBarColor: Int

        val isNightMode =
            context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        try {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            primaryColor = typedValue.data

            try {
                context.theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
                statusBarColor = typedValue.data
            } catch (e: Exception) {
                statusBarColor = primaryColor
            }

            context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            surfaceColor = typedValue.data

            onPrimaryColor =
                if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        } catch (e: Exception) {
            primaryColor = if (isNightMode) 0xFF9C27B0.toInt() else 0xFF6200EE.toInt()
            statusBarColor = if (isNightMode) 0xFF7B1FA2.toInt() else 0xFF3700B3.toInt()
            surfaceColor =
                if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            onPrimaryColor =
                if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        }

        val cropOptions =
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
                    statusBarColor = statusBarColor
                    activityMenuIconColor = onPrimaryColor
                    showCropOverlay = true
                    showProgressBar = true
                    multiTouchEnabled = true
                    autoZoomEnabled = true
                },
            )
        cropImageLauncher.launch(cropOptions)
    }

    val mediaPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                uri: Uri? ->
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
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "background_video")

                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Background video saved to: $internalUri")
                            editorSession.registerStagedAsset(internalUri.toString())
                            onBackgroundImageUriInputChange(internalUri.toString())
                            onBackgroundMediaTypeInputChange(UserPreferencesManager.MEDIA_TYPE_VIDEO)
                            editorSession.setOptionalString(
                                "background_image_uri",
                                internalUri.toString(),
                            )
                            editorSession.setString(
                                "background_media_type",
                                UserPreferencesManager.MEDIA_TYPE_VIDEO,
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
        launchImageCrop = { launchImageCrop(it) },
        mediaPickerLauncher = mediaPickerLauncher,
    )
}
