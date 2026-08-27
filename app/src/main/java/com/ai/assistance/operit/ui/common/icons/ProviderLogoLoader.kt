package com.ai.assistance.operit.ui.common.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.InputStream
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.ai.assistance.operit.data.model.normalizeProviderTypeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 模型 Provider Logo 加载器（方案 A）
 *
 * Logo 素材存放于 assets/model_logos/{ApiProviderType.name}/ 目录下，
 * 每个 provider 文件夹内只有一个文件（.svg 或 .png）。
 * - .svg 使用 AndroidSVG 解析渲染为 Bitmap
 * - .png 直接解码并按目标尺寸缩放
 */
object ProviderLogoLoader {

    private const val LOGO_ROOT = "model_logos"

    /** 查找 provider 对应的 assets 内 logo 文件路径，不存在返回 null */
    fun findLogoAssetPath(context: Context, providerTypeId: String): String? {
        val normalizedProviderTypeId = normalizeProviderTypeId(providerTypeId)
        if (normalizedProviderTypeId.isBlank()) return null
        val dir = "$LOGO_ROOT/$normalizedProviderTypeId"
        return try {
            val files = context.assets.list(dir) ?: return null
            val file =
                files.firstOrNull {
                    it.endsWith(".svg", ignoreCase = true) ||
                        it.endsWith(".png", ignoreCase = true)
                } ?: return null
            "$dir/$file"
        } catch (_: Exception) {
            null
        }
    }

    /** 加载 provider logo 为指定尺寸的 Bitmap（正方形画布，内容按比例居中） */
    fun loadLogoBitmap(context: Context, providerTypeId: String, sizePx: Int): Bitmap? {
        val path = findLogoAssetPath(context, providerTypeId) ?: return null
        return try {
            context.assets.open(path).use { input ->
                LogoBitmapLoader.load(
                    input = input,
                    mimeType = null,
                    fileName = path,
                    sizePx = sizePx
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

object LogoBitmapLoader {
    private val rasterExtensions = setOf("png", "jpg", "jpeg", "webp")
    private val rasterMimeTypes = setOf("image/png", "image/jpeg", "image/webp")

    fun load(
        bytes: ByteArray,
        mimeType: String?,
        fileName: String?,
        sizePx: Int
    ): Bitmap? {
        return bytes.inputStream().use { input ->
            load(input = input, mimeType = mimeType, fileName = fileName, sizePx = sizePx)
        }
    }

    fun load(
        input: InputStream,
        mimeType: String?,
        fileName: String?,
        sizePx: Int
    ): Bitmap? {
        require(sizePx > 0) { "Logo size must be positive" }
        return try {
            when {
                isSvg(mimeType, fileName) -> renderSvgToBitmap(input, sizePx)
                isRaster(mimeType, fileName) -> scaleBitmapToBitmap(input, sizePx)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isSvg(mimeType: String?, fileName: String?): Boolean {
        return mimeType.equals("image/svg+xml", ignoreCase = true) ||
            fileName.extension().equals("svg", ignoreCase = true)
    }

    private fun isRaster(mimeType: String?, fileName: String?): Boolean {
        return mimeType?.trim()?.lowercase()?.let { it in rasterMimeTypes } == true ||
            fileName.extension().lowercase() in rasterExtensions
    }

    private fun renderSvgToBitmap(input: InputStream, sizePx: Int): Bitmap? {
        val svg = SVG.getFromInputStream(input)
        val viewWidth = if (svg.documentWidth > 0f) svg.documentWidth else 24f
        val viewHeight = if (svg.documentHeight > 0f) svg.documentHeight else 24f
        val scale = sizePx / max(viewWidth, viewHeight)
        val scaledWidth = viewWidth * scale
        val scaledHeight = viewHeight * scale

        svg.setDocumentWidth(scaledWidth)
        svg.setDocumentHeight(scaledHeight)

        val picture = svg.renderToPicture()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate((sizePx - scaledWidth) / 2f, (sizePx - scaledHeight) / 2f)
        picture.draw(canvas)
        return bitmap
    }

    private fun scaleBitmapToBitmap(input: InputStream, sizePx: Int): Bitmap? {
        val source = BitmapFactory.decodeStream(input) ?: return null
        if (source.width == sizePx && source.height == sizePx) return source
        val scale = sizePx / max(source.width, source.height).toFloat()
        val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(
                (sizePx - scaledWidth) / 2f,
                (sizePx - scaledHeight) / 2f,
                (sizePx + scaledWidth) / 2f,
                (sizePx + scaledHeight) / 2f
            ),
            null
        )
        source.recycle()
        return bitmap
    }

    private fun String?.extension(): String {
        return this?.substringAfterLast('.', "").orEmpty()
    }
}

/**
 * 以 Compose Painter 形式获取 provider logo。
 * provider 无 logo 素材时返回 null，调用方显示默认图标或首字母色块。
 */
@Composable
fun rememberProviderLogoPainter(providerTypeId: String?, size: Dp = 24.dp): Painter? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by
        produceState<ImageBitmap?>(initialValue = null, providerTypeId, sizePx) {
            value =
                if (providerTypeId.isNullOrBlank()) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        ProviderLogoLoader.loadLogoBitmap(context, providerTypeId, sizePx)
                            ?.asImageBitmap()
                    }
                }
        }
    return bitmap?.let { BitmapPainter(it) }
}

@Composable
fun rememberLogoPainter(
    logoKey: Any?,
    bytes: ByteArray?,
    mimeType: String?,
    fileName: String?,
    size: Dp = 24.dp
): Painter? {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by
        produceState<ImageBitmap?>(
            initialValue = null,
            logoKey,
            bytes?.contentHashCode(),
            mimeType,
            fileName,
            sizePx
        ) {
            value =
                bytes?.let {
                    withContext(Dispatchers.IO) {
                        LogoBitmapLoader.load(
                            bytes = it,
                            mimeType = mimeType,
                            fileName = fileName,
                            sizePx = sizePx
                        )?.asImageBitmap()
                    }
                }
        }
    return bitmap?.let { BitmapPainter(it) }
}

/** 深色表面下将黑色品牌素材染亮，浅色模式保留 logo 原色。 */
@Composable
fun providerLogoColorFilter(): ColorFilter? {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) {
        ColorFilter.tint(scheme.onSurface)
    } else {
        null
    }
}
