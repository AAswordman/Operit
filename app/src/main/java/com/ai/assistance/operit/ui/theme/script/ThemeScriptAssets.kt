package com.ai.assistance.operit.ui.theme.script

import android.graphics.BitmapFactory
import android.graphics.Picture
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import com.ai.assistance.operit.data.model.theme.ThemeScriptAppearance
import com.ai.assistance.operit.data.model.theme.forMode
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.caverock.androidsvg.SVG
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

internal sealed interface ThemeScriptImage {
    data class Bitmap(val image: ImageBitmap) : ThemeScriptImage
    data class Vector(val picture: Picture) : ThemeScriptImage
    data class Font(val family: FontFamily) : ThemeScriptImage
}

/** 素材随不可变资源束准备，绘制阶段不读取文件或重新解析 SVG、字体。 */
internal object ThemeScriptAssets {
    fun prepare(
        bundle: ThemeScriptBundle,
        fontDirectory: File? = null,
        previous: ThemeScriptBundle? = null,
        images: Map<String, ThemeScriptImage> = emptyMap(),
    ): Map<String, ThemeScriptImage> {
        // 连续编辑与撤销保留已解码对象，导入新素材不重复分配整套位图。
        val cached = if (previous == null) emptyMap() else previous.manifest.assets.mapNotNull { (id, path) ->
            images[id]?.let { path to (previous.assets.getValue(path) to it) }
        }.toMap()
        var bitmapPixels = 0L
        val decoded = bundle.assets.mapValues { (path, bytes) ->
            val former = cached[path]
            val image = if (former != null && former.first.contentEquals(bytes)) former.second else when (path.substringAfterLast('.').lowercase()) {
                "ttf", "otf" -> {
                    val directory = requireNotNull(fontDirectory) { "Theme font cache directory is required" }
                    require(directory.isDirectory || directory.mkdirs()) { "Cannot create theme font cache" }
                    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    val file = File(directory, "$digest.${path.substringAfterLast('.')}")
                    synchronized(this) { if (!file.isFile) file.writeBytes(bytes) }
                    ThemeScriptImage.Font(FontFamily(Typeface.createFromFile(file)))
                }
                "svg" -> ThemeScriptImage.Vector(SVG.getFromString(bytes.toString(Charsets.UTF_8)).renderToPicture(512, 512))
                else -> {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 4_194_304L) { "Theme bitmap exceeds the decoded pixel budget" }
                    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Cannot decode theme bitmap" }
                    ThemeScriptImage.Bitmap(bitmap.asImageBitmap())
                }
            }
            if (image is ThemeScriptImage.Bitmap) {
                bitmapPixels += image.image.width.toLong() * image.image.height
                require(bitmapPixels <= 4_194_304L) { "Theme drawing assets exceed the decoded pixel budget" }
            }
            image
        }
        return bundle.manifest.assets.mapValues { (_, path) -> decoded.getValue(path) }
    }

    fun validateAppearance(appearance: ThemeScriptAppearance, images: Map<String, ThemeScriptImage>) {
        val modes = listOf(appearance.copy(modes = emptyMap())) + appearance.modes.keys.map(appearance::forMode)
        modes.forEach { current ->
            val texts = listOf(current.typography) + current.surfaces.values.map { it.textStyle }
            texts.mapNotNull { it.family }.filter { it.startsWith("asset:") }.forEach { family ->
                require(images[family.removePrefix("asset:")] is ThemeScriptImage.Font) { "Theme text requires a font resource" }
            }
            current.icons.values.forEach { icon -> require(images[icon.asset] is ThemeScriptImage.Bitmap || images[icon.asset] is ThemeScriptImage.Vector) { "Icon requires an image resource" } }
            current.surfaces.values.flatMap { it.layers }.forEach { layer ->
                require(layer.blur == 0f || Build.VERSION.SDK_INT >= 31) { "Theme image blur requires Android 12 or newer" }
                if (layer.type == "image") require(images[layer.asset] is ThemeScriptImage.Bitmap || images[layer.asset] is ThemeScriptImage.Vector) { "Drawing layer requires an image resource" }
                if (layer.type == "nineSlice") {
                    val bitmap = images.getValue(requireNotNull(layer.asset)) as? ThemeScriptImage.Bitmap ?: error("Nine-slice skins require bitmap assets")
                    val edge = layer.slices
                    require(edge.start.roundToInt() + edge.end.roundToInt() < bitmap.image.width && edge.top.roundToInt() + edge.bottom.roundToInt() < bitmap.image.height) { "Nine-slice insets must leave a nonempty image center" }
                }
            }
        }
    }
}
