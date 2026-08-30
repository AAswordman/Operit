package com.ai.assistance.operit.ui.theme.scene.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneColumnNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneEdgeInsetsV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneFrameNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneGridNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneHostSlotNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageFitV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneLayerNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNineSliceNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeScenePathNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneRowNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneSlotIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneSurfaceNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTextKeyIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTextNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenResolverV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTransformNodeV1
import kotlin.math.roundToInt

internal typealias ThemeSceneHostSlotsV1 =
    Map<ThemeSceneSlotIdV1, @Composable () -> Unit>

internal typealias ThemeSceneTextResolverV1 = (ThemeSceneTextKeyIdV1) -> String

/**
 * Renders one validated declarative scene tree. Host semantic content is injected through
 * [hostSlots]; everything else in the tree is package-owned decoration. The definition must
 * have passed validateThemeSceneV1 first; missing slots or tokens fail fast at runtime.
 */
@Composable
internal fun ThemeSceneV1(
    stage: ThemeSceneStageNodeV1,
    tokens: ThemeSceneTokenResolverV1,
    assets: ThemeSceneAssetRepositoryV1,
    hostSlots: ThemeSceneHostSlotsV1,
    textResolver: ThemeSceneTextResolverV1,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    stage.backgroundColorToken
                        ?.let { token -> Modifier.background(tokens.color(token, darkTheme)) }
                        ?: Modifier,
                ),
    ) {
        stage.children.forEach { child ->
            ThemeSceneRenderNodeV1(child, tokens, assets, hostSlots, textResolver, darkTheme)
        }
    }
}

@Composable
private fun ThemeSceneRenderNodeV1(
    node: ThemeSceneNodeV1,
    tokens: ThemeSceneTokenResolverV1,
    assets: ThemeSceneAssetRepositoryV1,
    hostSlots: ThemeSceneHostSlotsV1,
    textResolver: ThemeSceneTextResolverV1,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is ThemeSceneStageNodeV1 ->
            Box(modifier) {
                node.children.forEach { child ->
                    ThemeSceneRenderNodeV1(child, tokens, assets, hostSlots, textResolver, darkTheme)
                }
            }

        is ThemeSceneLayerNodeV1 ->
            Box(modifier.fillMaxSize()) {
                node.children.forEach { child ->
                    ThemeSceneRenderNodeV1(child, tokens, assets, hostSlots, textResolver, darkTheme)
                }
            }

        is ThemeSceneRowNodeV1 ->
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(node.spacingDp.dp),
            ) {
                node.children.forEach { child ->
                    ThemeSceneRenderNodeV1(child, tokens, assets, hostSlots, textResolver, darkTheme)
                }
            }

        is ThemeSceneColumnNodeV1 ->
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(node.spacingDp.dp),
            ) {
                node.children.forEach { child ->
                    ThemeSceneRenderNodeV1(child, tokens, assets, hostSlots, textResolver, darkTheme)
                }
            }

        is ThemeSceneGridNodeV1 -> {
            val spacing = node.spacingDp.dp
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
                node.children.chunked(node.columns).forEach { rowChildren ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        rowChildren.forEach { child ->
                            Box(Modifier.weight(1f)) {
                                ThemeSceneRenderNodeV1(
                                    child,
                                    tokens,
                                    assets,
                                    hostSlots,
                                    textResolver,
                                    darkTheme,
                                )
                            }
                        }
                    }
                }
            }
        }

        is ThemeSceneFrameNodeV1 -> {
            Box(
                modifier =
                    modifier
                        .sceneFrame(node)
                        .then(node.contentPadding?.let { Modifier.padding(it.toPaddingValues()) } ?: Modifier),
            ) {
                ThemeSceneRenderNodeV1(node.child, tokens, assets, hostSlots, textResolver, darkTheme)
            }
        }

        is ThemeSceneHostSlotNodeV1 -> {
            val content =
                hostSlots[node.slotId]
                    ?: error("Host did not provide content for slot ${node.slotId.value}.")
            Box(
                modifier =
                    modifier.then(
                        node.contentPadding?.let { Modifier.padding(it.toPaddingValues()) }
                            ?: Modifier,
                    ),
            ) {
                key(node.slotId.value) {
                    content()
                }
            }
        }

        is ThemeSceneSurfaceNodeV1 -> {
            val shape = RoundedCornerShape(node.cornerRadiusDp.dp)
            val fill =
                node.fillToken?.let { Modifier.background(tokens.color(it, darkTheme), shape) }
                    ?: Modifier
            val outline =
                if (node.outlineToken != null && node.outlineWidthDp > 0f) {
                    Modifier.border(
                        width = node.outlineWidthDp.dp,
                        color = tokens.color(node.outlineToken, darkTheme),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            val alpha = if (node.opacity < 1f) Modifier.alpha(node.opacity) else Modifier
            Box(
                modifier =
                    modifier
                        .then(if (node.child == null) Modifier.fillMaxSize() else Modifier)
                        .then(fill)
                        .then(outline)
                        .then(alpha),
            ) {
                node.child?.let { child ->
                    ThemeSceneRenderNodeV1(child, tokens, assets, hostSlots, textResolver, darkTheme)
                }
            }
        }

        is ThemeSceneImageNodeV1 ->
            Image(
                bitmap = assets.bitmap(node.assetId),
                contentDescription = null,
                modifier = modifier.fillMaxSize(),
                contentScale =
                    when (node.fit) {
                        ThemeSceneImageFitV1.FILL -> ContentScale.FillBounds
                        ThemeSceneImageFitV1.FIT -> ContentScale.Fit
                        ThemeSceneImageFitV1.CROP -> ContentScale.Crop
                    },
            )

        is ThemeSceneNineSliceNodeV1 -> {
            val bitmap = assets.bitmap(node.assetId)
            Box(modifier = modifier.fillMaxSize().clipToBounds()) {
                Box(
                    modifier =
                        Modifier.matchParentSize().drawNineSlice(
                            bitmap = bitmap,
                            sourceInsets = node.sourceCapInsetsPx,
                            destinationInsets = node.destinationCapInsetsDp,
                        ),
                )
                node.child?.let { child ->
                    ThemeSceneRenderNodeV1(child, tokens, assets, hostSlots, textResolver, darkTheme)
                }
            }
        }

        is ThemeSceneTextNodeV1 -> {
            val styleToken =
                node.styleToken
                    ?: error("Scene text node ${node.nodeId.value} requires a style token.")
            val resolved = tokens.textStyle(styleToken, darkTheme)
            Text(
                text = textResolver(node.textKey),
                modifier = modifier,
                color = resolved.color,
                fontSize = resolved.fontSizeSp.sp,
                fontFamily = resolved.fontAsset?.let { assets.fontFamily(it) },
                fontWeight = FontWeight(resolved.fontWeight),
                letterSpacing = resolved.letterSpacingEm.sp,
            )
        }

        is ThemeScenePathNodeV1 ->
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .alpha(node.opacity)
                        .drawBehind {
                            val path =
                                assets.composePath(
                                    assetId = node.assetId,
                                    widthPx = size.width,
                                    heightPx = size.height,
                                )
                            node.fillToken?.let { fill ->
                                drawPath(path, tokens.color(fill, darkTheme))
                            }
                            if (node.outlineToken != null && node.outlineWidthDp > 0f) {
                                drawPath(
                                    path,
                                    tokens.color(node.outlineToken, darkTheme),
                                    style = Stroke(node.outlineWidthDp.dp.toPx()),
                                )
                            }
                        },
            )

        is ThemeSceneTransformNodeV1 ->
            Box(
                modifier =
                    modifier.graphicsLayer {
                        translationX = node.translationXDp.dp.toPx()
                        translationY = node.translationYDp.dp.toPx()
                        scaleX = node.scale
                        scaleY = node.scale
                        rotationZ = node.rotationDegrees
                        alpha = node.alpha
                    },
            ) {
                ThemeSceneRenderNodeV1(node.child, tokens, assets, hostSlots, textResolver, darkTheme)
            }
    }
}

private fun Modifier.sceneFrame(node: ThemeSceneFrameNodeV1): Modifier =
    layout { measurable, constraints ->
        val parentWidth = constraints.maxWidth.toFloat()
        val parentHeight = constraints.maxHeight.toFloat()
        val anchor = node.anchor
        val startX = anchor?.startX ?: 0f
        val startY = anchor?.startY ?: 0f
        val endX = anchor?.endX ?: 1f
        val endY = anchor?.endY ?: 1f
        val regionLeft = (startX * parentWidth).roundToInt()
        val regionTop = (startY * parentHeight).roundToInt()
        val regionWidth = ((endX - startX) * parentWidth).roundToInt().coerceAtLeast(0)
        val regionHeight = ((endY - startY) * parentHeight).roundToInt().coerceAtLeast(0)
        val targetWidth = node.width.resolve(regionWidth)
        val targetHeight = node.height.resolve(regionHeight)
        val minWidth = node.minWidthDp?.dp?.roundToPx() ?: 0
        val maxWidth = node.maxWidthDp?.dp?.roundToPx() ?: targetWidth
        val constrainedWidth = targetWidth.coerceIn(minWidth, maxWidth.coerceAtLeast(minWidth))
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = 0,
                    minHeight = 0,
                    maxWidth = constrainedWidth,
                    maxHeight = targetHeight,
                ),
            )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(regionLeft, regionTop)
        }
    }

private fun com.ai.assistance.operit.ui.theme.scene.ThemeSceneSizeV1.resolve(
    region: Int,
): Int =
    when (this) {
        com.ai.assistance.operit.ui.theme.scene.ThemeSceneSizeV1.Fill -> region
        com.ai.assistance.operit.ui.theme.scene.ThemeSceneSizeV1.Wrap -> region
        is com.ai.assistance.operit.ui.theme.scene.ThemeSceneSizeV1.Fraction ->
            (region * value).roundToInt().coerceAtLeast(0)
    }

private fun ThemeSceneEdgeInsetsV1.toPaddingValues() =
    PaddingValues(
        start = startDp.dp,
        top = topDp.dp,
        end = endDp.dp,
        bottom = bottomDp.dp,
    )

private fun Modifier.drawNineSlice(
    bitmap: ImageBitmap,
    sourceInsets: com.ai.assistance.operit.ui.theme.scene.ThemeScenePixelInsetsV1,
    destinationInsets: ThemeSceneEdgeInsetsV1,
): Modifier =
    drawBehind {
        val srcCapLeft = sourceInsets.startPx.toFloat()
        val srcCapTop = sourceInsets.topPx.toFloat()
        val srcCapRight = sourceInsets.endPx.toFloat()
        val srcCapBottom = sourceInsets.bottomPx.toFloat()
        val dstCapLeft = destinationInsets.startDp.dp.toPx()
        val dstCapTop = destinationInsets.topDp.dp.toPx()
        val dstCapRight = destinationInsets.endDp.dp.toPx()
        val dstCapBottom = destinationInsets.bottomDp.dp.toPx()
        val srcWidth = bitmap.width.toFloat()
        val srcHeight = bitmap.height.toFloat()
        val dstWidth = size.width
        val dstHeight = size.height

        fun drawRegion(
            srcLeft: Float,
            srcTop: Float,
            srcRight: Float,
            srcBottom: Float,
            dstLeft: Float,
            dstTop: Float,
            dstRight: Float,
            dstBottom: Float,
        ) {
            val srcW = (srcRight - srcLeft).roundToInt().coerceAtLeast(0)
            val srcH = (srcBottom - srcTop).roundToInt().coerceAtLeast(0)
            val dstW = (dstRight - dstLeft).roundToInt().coerceAtLeast(0)
            val dstH = (dstBottom - dstTop).roundToInt().coerceAtLeast(0)
            if (srcW == 0 || srcH == 0 || dstW == 0 || dstH == 0) return
            drawImage(
                image = bitmap,
                srcOffset = androidx.compose.ui.unit.IntOffset(srcLeft.roundToInt(), srcTop.roundToInt()),
                srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                dstOffset = androidx.compose.ui.unit.IntOffset(dstLeft.roundToInt(), dstTop.roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(dstW, dstH),
            )
        }

        val srcInnerLeft = srcCapLeft
        val srcInnerTop = srcCapTop
        val srcInnerRight = (srcWidth - srcCapRight).coerceAtLeast(srcCapLeft)
        val srcInnerBottom = (srcHeight - srcCapBottom).coerceAtLeast(srcCapTop)
        val dstInnerLeft = dstCapLeft
        val dstInnerTop = dstCapTop
        val dstInnerRight = (dstWidth - dstCapRight).coerceAtLeast(dstCapLeft)
        val dstInnerBottom = (dstHeight - dstCapBottom).coerceAtLeast(dstCapTop)

        drawRegion(0f, 0f, srcInnerLeft, srcInnerTop, 0f, 0f, dstInnerLeft, dstInnerTop)
        drawRegion(srcInnerLeft, 0f, srcInnerRight, srcInnerTop, dstInnerLeft, 0f, dstInnerRight, dstInnerTop)
        drawRegion(srcInnerRight, 0f, srcWidth, srcInnerTop, dstInnerRight, 0f, dstWidth, dstInnerTop)
        drawRegion(0f, srcInnerTop, srcInnerLeft, srcInnerBottom, 0f, dstInnerTop, dstInnerLeft, dstInnerBottom)
        drawRegion(srcInnerLeft, srcInnerTop, srcInnerRight, srcInnerBottom, dstInnerLeft, dstInnerTop, dstInnerRight, dstInnerBottom)
        drawRegion(srcInnerRight, srcInnerTop, srcWidth, srcInnerBottom, dstInnerRight, dstInnerTop, dstWidth, dstInnerBottom)
        drawRegion(0f, srcInnerBottom, srcInnerLeft, srcHeight, 0f, dstInnerBottom, dstInnerLeft, dstHeight)
        drawRegion(srcInnerLeft, srcInnerBottom, srcInnerRight, srcHeight, dstInnerLeft, dstInnerBottom, dstInnerRight, dstHeight)
        drawRegion(srcInnerRight, srcInnerBottom, srcWidth, srcHeight, dstInnerRight, dstInnerBottom, dstWidth, dstHeight)
    }
