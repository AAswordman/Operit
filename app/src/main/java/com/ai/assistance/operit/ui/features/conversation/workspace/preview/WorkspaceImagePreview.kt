package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ai.assistance.operit.R

private const val WORKSPACE_IMAGE_MIN_SCALE = 1f
private const val WORKSPACE_IMAGE_DOUBLE_TAP_SCALE = 2.5f
private const val WORKSPACE_IMAGE_MAX_SCALE = 5f

@Composable
internal fun WorkspaceImagePreview(
    fileName: String,
    previewUri: Uri?,
    isSourceLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember(previewUri) { mutableStateOf(WORKSPACE_IMAGE_MIN_SCALE) }
    var offset by remember(previewUri) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val resolvedErrorMessage = errorMessage ?: context.getString(R.string.cannot_open_file, fileName)

    val painter =
        if (previewUri != null) {
            rememberAsyncImagePainter(
                model =
                    remember(context, previewUri) {
                        ImageRequest.Builder(context)
                            .data(previewUri)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .crossfade(false)
                            .build()
                    }
            )
        } else {
            null
        }
    val painterState = painter?.state
    val isImageLoading =
        previewUri != null &&
            (painterState == null ||
                painterState is AsyncImagePainter.State.Empty ||
                painterState is AsyncImagePainter.State.Loading)
    val hasImageError = painterState is AsyncImagePainter.State.Error

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewportSize = it },
        contentAlignment = Alignment.Center
    ) {
        when {
            previewUri == null -> {
                if (!isSourceLoading) {
                    WorkspaceImageErrorMessage(message = resolvedErrorMessage)
                }
            }

            hasImageError -> {
                WorkspaceImageErrorMessage(message = resolvedErrorMessage)
            }

            painter != null -> {
                Image(
                    painter = painter,
                    contentDescription = fileName,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(previewUri) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        if (scale > WORKSPACE_IMAGE_MIN_SCALE) {
                                            scale = WORKSPACE_IMAGE_MIN_SCALE
                                            offset = Offset.Zero
                                        } else {
                                            scale = WORKSPACE_IMAGE_DOUBLE_TAP_SCALE
                                            offset =
                                                doubleTapOffset(
                                                    tapOffset = tapOffset,
                                                    viewportSize = viewportSize,
                                                    scale = WORKSPACE_IMAGE_DOUBLE_TAP_SCALE
                                                )
                                        }
                                    }
                                )
                            }
                            .pointerInput(previewUri) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextScale =
                                        (scale * zoom).coerceIn(
                                            WORKSPACE_IMAGE_MIN_SCALE,
                                            WORKSPACE_IMAGE_MAX_SCALE
                                        )
                                    scale = nextScale
                                    offset =
                                        clampImageOffset(
                                            rawOffset = if (nextScale <= WORKSPACE_IMAGE_MIN_SCALE) Offset.Zero else offset + pan,
                                            viewportSize = viewportSize,
                                            scale = nextScale
                                        )
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                )
            }
        }

        AnimatedVisibility(
            visible = isSourceLoading || (!hasImageError && isImageLoading),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            WorkspaceImageLoadingBadge(fileName = fileName)
        }

        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = fileName,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkspaceImageLoadingBadge(fileName: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.76f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.loading),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = fileName,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WorkspaceImageErrorMessage(message: String) {
    Text(
        text = message,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun clampImageOffset(
    rawOffset: Offset,
    viewportSize: IntSize,
    scale: Float
): Offset {
    if (scale <= WORKSPACE_IMAGE_MIN_SCALE || viewportSize == IntSize.Zero) {
        return Offset.Zero
    }

    val maxX = (viewportSize.width * (scale - 1f)) / 2f
    val maxY = (viewportSize.height * (scale - 1f)) / 2f
    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY)
    )
}

private fun doubleTapOffset(
    tapOffset: Offset,
    viewportSize: IntSize,
    scale: Float
): Offset {
    if (viewportSize == IntSize.Zero) {
        return Offset.Zero
    }

    return clampImageOffset(
        rawOffset =
            Offset(
                x = (viewportSize.width / 2f - tapOffset.x) * (scale - 1f),
                y = (viewportSize.height / 2f - tapOffset.y) * (scale - 1f)
            ),
        viewportSize = viewportSize,
        scale = scale
    )
}
