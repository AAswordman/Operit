package com.ai.assistance.operit.ui.features.settings.sections

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.MediaTypeOption
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.flow.collect
import kotlin.math.abs

private fun calculateLuminance(color: Color): Float {
    return 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
}

@Composable
internal fun ThemeSettingsBackgroundSection(
    cardColors: CardColors,
    context: Context,
    editorSession: ThemeEditorSession,
    exoPlayer: ExoPlayer,
    launchImageCrop: (Uri) -> Unit,
    mediaPickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    scrollState: ScrollState,
    useBackgroundImageInput: Boolean,
    onUseBackgroundImageInputChange: (Boolean) -> Unit,
    backgroundMediaTypeInput: String,
    onBackgroundMediaTypeInputChange: (String) -> Unit,
    backgroundImageUriInput: String?,
    backgroundImageOpacityInput: Float,
    onBackgroundImageOpacityInputChange: (Float) -> Unit,
    videoBackgroundMutedInput: Boolean,
    onVideoBackgroundMutedInputChange: (Boolean) -> Unit,
    videoBackgroundLoopInput: Boolean,
    onVideoBackgroundLoopInputChange: (Boolean) -> Unit,
    useBackgroundBlurInput: Boolean,
    onUseBackgroundBlurInputChange: (Boolean) -> Unit,
    backgroundBlurRadiusInput: Float,
    onBackgroundBlurRadiusInputChange: (Float) -> Unit,
) {
    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.theme_title_background),
        icon = Icons.Default.Image,
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_bg_media),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_use_custom_bg),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.theme_custom_bg_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = useBackgroundImageInput,
                    onCheckedChange = {
                        onUseBackgroundImageInputChange(it)
                        editorSession.setBoolean("use_background_image", it)
                    },
                )
            }

            if (useBackgroundImageInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.theme_media_type),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MediaTypeOption(
                        title = stringResource(id = R.string.theme_media_image),
                        icon = Icons.Default.Image,
                        selected =
                            backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_IMAGE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onBackgroundMediaTypeInputChange(
                                UserPreferencesManager.MEDIA_TYPE_IMAGE,
                            )
                            if (!backgroundImageUriInput.isNullOrEmpty()) {
                                editorSession.setString(
                                    "background_media_type",
                                    UserPreferencesManager.MEDIA_TYPE_IMAGE,
                                )
                            }
                        },
                    )

                    MediaTypeOption(
                        title = stringResource(id = R.string.theme_media_video),
                        icon = Icons.Default.Videocam,
                        selected =
                            backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onBackgroundMediaTypeInputChange(
                                UserPreferencesManager.MEDIA_TYPE_VIDEO,
                            )
                            if (!backgroundImageUriInput.isNullOrEmpty()) {
                                editorSession.setString(
                                    "background_media_type",
                                    UserPreferencesManager.MEDIA_TYPE_VIDEO,
                                )
                            }
                        },
                    )
                }

                if (!backgroundImageUriInput.isNullOrEmpty()) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(200.dp)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp),
                                )
                                .background(Color.Black.copy(alpha = 0.1f)),
                    ) {
                        if (backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
                            Image(
                                painter =
                                    rememberAsyncImagePainter(Uri.parse(backgroundImageUriInput)),
                                contentDescription = stringResource(R.string.theme_background_preview),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )

                            IconButton(
                                onClick = {
                                    backgroundImageUriInput?.let {
                                        launchImageCrop(Uri.parse(it))
                                    }
                                },
                                modifier =
                                    Modifier.align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            CircleShape,
                                        ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = stringResource(R.string.theme_recrop),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                            val isLightTheme =
                                calculateLuminance(MaterialTheme.colorScheme.background) > 0.5f

                            AndroidView(
                                factory = { ctx ->
                                    (LayoutInflater.from(ctx).inflate(
                                        R.layout.view_background_texture_player,
                                        null,
                                        false,
                                    ) as StyledPlayerView).apply {
                                        player = exoPlayer
                                        useController = false
                                        layoutParams =
                                            ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setBackgroundColor(backgroundColor)
                                        setShutterBackgroundColor(backgroundColor)
                                        setKeepContentOnPlayerReset(true)
                                        foreground =
                                            android.graphics.drawable.ColorDrawable(
                                                android.graphics.Color.argb(
                                                    ((1f - backgroundImageOpacityInput) * 255)
                                                        .toInt(),
                                                    if (isLightTheme) 255 else 0,
                                                    if (isLightTheme) 255 else 0,
                                                    if (isLightTheme) 255 else 0,
                                                ),
                                            )
                                    }
                                },
                                update = { view ->
                                    view.setBackgroundColor(backgroundColor)
                                    view.setShutterBackgroundColor(backgroundColor)
                                    view.setKeepContentOnPlayerReset(true)
                                    view.foreground =
                                        android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.argb(
                                                ((1f - backgroundImageOpacityInput) * 255).toInt(),
                                                if (isLightTheme) 255 else 0,
                                                if (isLightTheme) 255 else 0,
                                                if (isLightTheme) 255 else 0,
                                            ),
                                        )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )

                            Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                IconButton(
                                    onClick = {
                                        val newMuted = !videoBackgroundMutedInput
                                        onVideoBackgroundMutedInputChange(newMuted)
                                        editorSession.setBoolean(
                                            "video_background_muted",
                                            newMuted,
                                        )
                                    },
                                    modifier =
                                        Modifier.padding(end = 8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(
                                                    alpha = 0.7f,
                                                ),
                                                CircleShape,
                                            ),
                                ) {
                                    Icon(
                                        imageVector =
                                            if (videoBackgroundMutedInput) {
                                                Icons.AutoMirrored.Outlined.VolumeOff
                                            } else {
                                                Icons.AutoMirrored.Rounded.VolumeUp
                                            },
                                        contentDescription =
                                            if (videoBackgroundMutedInput) {
                                                stringResource(id = R.string.theme_unmute)
                                            } else {
                                                stringResource(id = R.string.theme_mute)
                                            },
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val newLoop = !videoBackgroundLoopInput
                                        onVideoBackgroundLoopInputChange(newLoop)
                                        editorSession.setBoolean(
                                            "video_background_loop",
                                            newLoop,
                                        )
                                        Toast.makeText(
                                            context,
                                            if (newLoop) {
                                                context.getString(R.string.theme_loop_enabled)
                                            } else {
                                                context.getString(R.string.theme_loop_disabled)
                                            },
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    modifier =
                                        Modifier.background(
                                            if (videoBackgroundLoopInput) {
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.3f,
                                                )
                                            } else {
                                                MaterialTheme.colorScheme.surface.copy(
                                                    alpha = 0.7f,
                                                )
                                            },
                                            CircleShape,
                                        ),
                                ) {
                                    Icon(
                                        if (videoBackgroundLoopInput) {
                                            Icons.Default.Loop
                                        } else {
                                            Icons.Outlined.Loop
                                        },
                                        contentDescription =
                                            if (videoBackgroundLoopInput) {
                                                stringResource(id = R.string.theme_loop_on)
                                            } else {
                                                stringResource(id = R.string.theme_loop_off)
                                            },
                                        tint =
                                            if (videoBackgroundLoopInput) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(150.dp)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp),
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(id = R.string.theme_no_bg_selected),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = {
                        if (backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO) {
                            mediaPickerLauncher.launch("video/*")
                        } else {
                            mediaPickerLauncher.launch("image/*")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO) {
                            stringResource(id = R.string.theme_select_video)
                        } else {
                            stringResource(id = R.string.theme_select_image)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text =
                        stringResource(
                            id = R.string.theme_bg_opacity,
                            (backgroundImageOpacityInput * 100).toInt(),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                var lastSavedOpacity by remember { mutableStateOf(backgroundImageOpacityInput) }
                var isDragging by remember { mutableStateOf(false) }
                val interactionSource = remember { MutableInteractionSource() }

                LaunchedEffect(interactionSource) {
                    interactionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is DragInteraction.Start -> isDragging = true
                            is DragInteraction.Stop -> isDragging = false
                            is DragInteraction.Cancel -> isDragging = false
                        }
                    }
                }

                if (isDragging) {
                    DisposableEffect(Unit) {
                        scrollState.value
                        onDispose {}
                    }
                }

                val latestOpacity by rememberUpdatedState(backgroundImageOpacityInput)
                val latestOpacityChange by rememberUpdatedState(onBackgroundImageOpacityInputChange)

                val updateOpacity = remember {
                    { value: Float -> latestOpacityChange(value) }
                }

                val onValueChangeFinished = remember {
                    {
                        val newOpacity = latestOpacity
                        if (abs(lastSavedOpacity - newOpacity) > 0.01f) {
                            editorSession.setFloat("background_image_opacity", newOpacity)
                            lastSavedOpacity = newOpacity
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 8.dp)) {
                    Slider(
                        value = backgroundImageOpacityInput,
                        onValueChange = updateOpacity,
                        onValueChangeFinished = onValueChangeFinished,
                        valueRange = 0.1f..1f,
                        interactionSource = interactionSource,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.theme_background_blur),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(id = R.string.theme_background_blur_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useBackgroundBlurInput,
                        onCheckedChange = {
                            onUseBackgroundBlurInputChange(it)
                            editorSession.setBoolean("use_background_blur", it)
                        },
                    )
                }

                if (useBackgroundBlurInput) {
                    Text(
                        text =
                            stringResource(id = R.string.theme_background_blur_radius) +
                                ": ${backgroundBlurRadiusInput.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )

                    var lastSavedBlurRadius by remember {
                        mutableStateOf(backgroundBlurRadiusInput)
                    }
                    val blurInteractionSource = remember { MutableInteractionSource() }
                    val latestBlurRadius by rememberUpdatedState(backgroundBlurRadiusInput)
                    val latestBlurRadiusChange by
                        rememberUpdatedState(onBackgroundBlurRadiusInputChange)

                    val onBlurValueChange = remember {
                        { value: Float -> latestBlurRadiusChange(value) }
                    }

                    val onBlurValueChangeFinished = remember {
                        {
                            val newBlurRadius = latestBlurRadius
                            if (abs(lastSavedBlurRadius - newBlurRadius) > 0.1f) {
                                editorSession.setFloat("background_blur_radius", newBlurRadius)
                                lastSavedBlurRadius = newBlurRadius
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 8.dp),
                    ) {
                        Slider(
                            value = backgroundBlurRadiusInput,
                            onValueChange = onBlurValueChange,
                            onValueChangeFinished = onBlurValueChangeFinished,
                            valueRange = 1f..30f,
                            interactionSource = blurInteractionSource,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

}
