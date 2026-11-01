package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 切片由后台准备，绘制只调整纹理矩阵；一像素图案也只提交一个矩形。 */
internal class BubbleImageTile(
    private val bitmap: Bitmap,
    private val repeatX: Boolean,
    private val repeatY: Boolean,
) {
    private val matrix = Matrix()
    private val shader = BitmapShader(
        bitmap,
        if (repeatX) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP,
        if (repeatY) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP,
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = this@BubbleImageTile.shader
    }

    fun draw(
        scope: DrawScope,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        scale: Float,
    ) {
        if (width <= 0 || height <= 0) return
        val periodX = if (repeatX) bubbleTileExtent(bitmap.width, scale) else width
        val periodY = if (repeatY) bubbleTileExtent(bitmap.height, scale) else height
        matrix.setScale(periodX.toFloat() / bitmap.width, periodY.toFloat() / bitmap.height)
        matrix.postTranslate(x.toFloat(), y.toFloat())
        shader.setLocalMatrix(matrix)
        scope.drawContext.canvas.nativeCanvas.drawRect(
            x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), paint,
        )
    }
}

// 缓存只共享像素切片；每个气泡单独持有纹理矩阵，避免不同消息互相修改绘制状态。
private data class BubbleImageTileBitmaps(
    val top: Bitmap?,
    val bottom: Bitmap?,
    val left: Bitmap?,
    val right: Bitmap?,
    val center: Bitmap,
) {
    val byteCount: Long get() =
        listOfNotNull(top, bottom, left, right, center).sumOf { it.allocationByteCount.toLong() }

    fun createTiles(): BubbleImageTiles = BubbleImageTiles(
        top = top?.let { BubbleImageTile(it, repeatX = true, repeatY = false) },
        bottom = bottom?.let { BubbleImageTile(it, repeatX = true, repeatY = false) },
        left = left?.let { BubbleImageTile(it, repeatX = false, repeatY = true) },
        right = right?.let { BubbleImageTile(it, repeatX = false, repeatY = true) },
        center = BubbleImageTile(center, repeatX = true, repeatY = true),
    )
}

internal data class BubbleImageTiles(
    val top: BubbleImageTile?,
    val bottom: BubbleImageTile?,
    val left: BubbleImageTile?,
    val right: BubbleImageTile?,
    val center: BubbleImageTile,
)

private data class BubbleTileSource(val bitmap: ImageBitmap, val layout: BubbleSliceLayout)

// 计入源图引用的内存并限制小切片条目数，连续调节不会无限保留历史图片或切片。
private val bubbleTilesCache = object : LruCache<BubbleTileSource, BubbleImageTileBitmaps>(24 * 1024 * 1024) {
    override fun sizeOf(key: BubbleTileSource, value: BubbleImageTileBitmaps): Int =
        (key.bitmap.asAndroidBitmap().allocationByteCount.toLong() + value.byteCount)
            .coerceIn(256L * 1024L, Int.MAX_VALUE.toLong()).toInt()
}
private val bubbleTilesMutex = Mutex()

@Composable
internal fun rememberBubbleImageTiles(
    bitmap: ImageBitmap?,
    layout: BubbleSliceLayout?,
): BubbleImageTiles? {
    val source = remember(bitmap, layout) {
        if (bitmap != null && layout != null) BubbleTileSource(bitmap, layout) else null
    }
    var bitmaps by remember(source) { mutableStateOf(source?.let { bubbleTilesCache[it] }) }
    LaunchedEffect(source) {
        val requested = source ?: return@LaunchedEffect
        bitmaps = withContext(Dispatchers.Default) {
            bubbleTilesMutex.withLock {
                bubbleTilesCache[requested] ?: prepareBubbleImageTiles(requested).also {
                    bubbleTilesCache.put(requested, it)
                }
            }
        }
    }
    return remember(bitmaps) { bitmaps?.createTiles() }
}

private fun prepareBubbleImageTiles(source: BubbleTileSource): BubbleImageTileBitmaps {
    val bitmap = source.bitmap.asAndroidBitmap()
    val layout = source.layout
    fun tile(x: Int, y: Int, width: Int, height: Int): Bitmap? =
        if (width == 0 || height == 0) null else Bitmap.createBitmap(bitmap, x, y, width, height)

    val centerX = layout.srcX + layout.leftCapWidth
    val centerY = layout.srcY + layout.topCapHeight
    val rightX = centerX + layout.centerWidth
    val bottomY = centerY + layout.centerHeight
    return BubbleImageTileBitmaps(
        top = tile(centerX, layout.srcY, layout.centerWidth, layout.topCapHeight),
        bottom = tile(centerX, bottomY, layout.centerWidth, layout.bottomCapHeight),
        left = tile(layout.srcX, centerY, layout.leftCapWidth, layout.centerHeight),
        right = tile(rightX, centerY, layout.rightCapWidth, layout.centerHeight),
        center = Bitmap.createBitmap(bitmap, centerX, centerY, layout.centerWidth, layout.centerHeight),
    )
}
