package com.ai.assistance.operit.ui.features.settings.sections

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView

private fun calculateBackgroundLuminance(color: Color): Float =
    0.299f * color.red + 0.587f * color.green + 0.114f * color.blue

@Composable
internal fun ThemeSettingsBackgroundPreview(
    exoPlayer: ExoPlayer?,
    launchImageCrop: (Uri) -> Unit,
    useBackgroundMedia: Boolean,
    backgroundMediaType: String,
    backgroundImageUri: String?,
    backgroundImageOpacity: Float,
    useBackgroundBlur: Boolean,
    backgroundBlurRadius: Float,
    modifier: Modifier = Modifier,
) {
    val imageUri =
        if (useBackgroundMedia) {
            backgroundImageUri?.takeIf(String::isNotEmpty)
        } else {
            null
        }
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightTheme = calculateBackgroundLuminance(backgroundColor) > 0.5f

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (imageUri == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.theme_no_bg_selected),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (backgroundMediaType == NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                    contentDescription = stringResource(R.string.theme_background_preview),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(backgroundImageOpacity)
                            .then(
                                if (useBackgroundBlur) {
                                    Modifier.blur(backgroundBlurRadius.dp)
                                } else {
                                    Modifier
                                }
                            ),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = { launchImageCrop(Uri.parse(imageUri)) },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = stringResource(R.string.theme_recrop),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (backgroundMediaType == NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO) {
                exoPlayer?.let { player ->
                    val videoBackgroundColor =
                        if (isLightTheme) {
                            android.graphics.Color.WHITE
                        } else {
                            android.graphics.Color.BLACK
                        }
                    AndroidView(
                        factory = { viewContext ->
                            (LayoutInflater.from(viewContext).inflate(
                                R.layout.view_background_texture_player,
                                null,
                                false,
                            ) as StyledPlayerView).apply {
                                this.player = player
                                useController = false
                                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                setBackgroundColor(videoBackgroundColor)
                                setShutterBackgroundColor(videoBackgroundColor)
                                setKeepContentOnPlayerReset(true)
                                foreground = backgroundOverlay(backgroundImageOpacity, isLightTheme)
                            }
                        },
                        update = { view ->
                            if (view.player !== player) view.player = player
                            view.setBackgroundColor(videoBackgroundColor)
                            view.setShutterBackgroundColor(videoBackgroundColor)
                            view.setKeepContentOnPlayerReset(true)
                            view.foreground = backgroundOverlay(backgroundImageOpacity, isLightTheme)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun backgroundOverlay(opacity: Float, isLightTheme: Boolean) =
    android.graphics.drawable.ColorDrawable(
        android.graphics.Color.argb(
            ((1f - opacity) * 255).toInt(),
            if (isLightTheme) 255 else 0,
            if (isLightTheme) 255 else 0,
            if (isLightTheme) 255 else 0,
        ),
    )
