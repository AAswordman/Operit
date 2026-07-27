package com.ai.assistance.operit.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONObject

enum class ImageOutputFormat {
    PNG,
    JPEG,
    AUTO
}

data class ImageRegistrationOptions(
    val scalePercent: Int? = null,
    val outputFormat: ImageOutputFormat = ImageOutputFormat.AUTO,
    val jpegQuality: Int? = null,
    val normalizeExif: Boolean = true,
    val maxLongEdge: Int? = null
)

/**
 * 全局图片池管理器
 * 支持内存缓存和本地持久化缓存，使用LRU策略。
 *
 * 图片在注册入池时会被统一解码、方向归一、缩放、格式转换，
 * 池中仅保存最终发送用的唯一标准图。
 */
object ImagePoolManager {
    private const val TAG = "ImagePoolManager"
    private const val DEFAULT_SCALE_PERCENT = 100
    private const val DEFAULT_JPEG_QUALITY = 85
    private const val DEFAULT_MAX_LONG_EDGE = 2048

    private val DEFAULT_REGISTRATION_OPTIONS =
        ImageRegistrationOptions(
            scalePercent = DEFAULT_SCALE_PERCENT,
            outputFormat = ImageOutputFormat.AUTO,
            jpegQuality = DEFAULT_JPEG_QUALITY,
            normalizeExif = true,
            maxLongEdge = DEFAULT_MAX_LONG_EDGE
        )

    var maxPoolSize = 20
        set(value) {
            if (value > 0) {
                field = value
                AppLogger.d(TAG, "池子大小限制已更新为: $value")
            }
        }

    /**
     * 是否保留聊天图片。开启后：
     * 1) 应用启动时不再清空磁盘图片缓存；
     * 2) LRU 淘汰时仅将图片移出内存池，不删除磁盘文件。
     * 由用户设置控制，默认 false（保持原有行为）。
     */
    var keepChatImages: Boolean = false

    private var cacheDir: File? = null
    private var hasResetCacheOnInitialize = false

    data class ImageData(
        val base64: String,
        val mimeType: String,
        val width: Int,
        val height: Int
    )

    private data class ResolvedRegistrationOptions(
        val scalePercent: Int,
        val outputFormat: ImageOutputFormat,
        val jpegQuality: Int,
        val normalizeExif: Boolean,
        val maxLongEdge: Int?
    )

    private data class NormalizedBase64Input(
        val base64: String,
        val mimeType: String
    )

    private val imagePool =
        object : LinkedHashMap<String, ImageData>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ImageData>?): Boolean {
                val shouldRemove = size > maxPoolSize
                if (shouldRemove && eldest != null) {
                    AppLogger.d(TAG, "池子已满，移除最旧的图片: ${eldest.key}")
                    // 保留聊天图片模式下，仅移出内存池，不删除磁盘文件
                    if (!keepChatImages) {
                        deleteFromDisk(eldest.key)
                    }
                }
                return shouldRemove
            }
        }

    fun defaultRegistrationOptions(): ImageRegistrationOptions = DEFAULT_REGISTRATION_OPTIONS.copy()

    fun initialize(cacheDirPath: File, preloadNow: Boolean = true) {
        val targetDir = OperitPaths.imagePoolDir(cacheDirPath)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
            AppLogger.d(TAG, "创建图片缓存目录: ${targetDir.absolutePath}")
        }

        val shouldResetCache =
            !hasResetCacheOnInitialize ||
                cacheDir?.absolutePath != targetDir.absolutePath

        cacheDir = targetDir

        if (shouldResetCache) {
            imagePool.clear()
            // 保留聊天图片模式下，启动不清空磁盘缓存，避免用户已保存的图片丢失
            if (!keepChatImages) {
                clearDiskCache()
            }
            hasResetCacheOnInitialize = true
            AppLogger.d(TAG, "启动时已清空旧图片池缓存")
        }

        if (preloadNow) {
            loadAllFromDisk()
        }
    }

    @Synchronized
    fun addImage(filePath: String, options: ImageRegistrationOptions? = null): String {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                AppLogger.e(TAG, "文件不存在或不是文件: $filePath")
                return "error"
            }

            val bitmap = decodeBitmapFromFile(file.absolutePath) ?: run {
                AppLogger.e(TAG, "无法将文件解码为位图: $filePath")
                return "error"
            }

            val sourceMimeType = getMimeTypeFromFile(file) ?: "image/png"
            registerBitmap(
                sourceBitmap = bitmap,
                sourceMimeType = sourceMimeType,
                options = options,
                sourcePath = file.absolutePath,
                recycleSource = true
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "添加图片时发生异常: $filePath", e)
            "error"
        }
    }

    @Synchronized
    fun addImageFromBase64(
        base64: String,
        mimeType: String,
        options: ImageRegistrationOptions? = null
    ): String {
        return try {
            val normalized = normalizeBase64Input(base64, mimeType)
            val bytes = Base64.decode(normalized.base64, Base64.DEFAULT)
            val bitmap = decodeBitmapFromBytes(bytes) ?: run {
                AppLogger.e(TAG, "无法将 base64 解码为位图")
                return "error"
            }

            registerBitmap(
                sourceBitmap = bitmap,
                sourceMimeType = normalized.mimeType,
                options = options,
                recycleSource = true
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "从base64添加图片时发生异常", e)
            "error"
        }
    }

    @Synchronized
    fun addImageFromBitmap(
        bitmap: Bitmap,
        mimeType: String,
        options: ImageRegistrationOptions? = null
    ): String {
        return try {
            registerBitmap(
                sourceBitmap = bitmap,
                sourceMimeType = mimeType.ifBlank { "image/png" },
                options = options,
                recycleSource = false
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "从bitmap添加图片时发生异常", e)
            "error"
        }
    }

    @Synchronized
    fun getImage(id: String): ImageData? {
        imagePool[id]?.let {
            val normalized = normalizeImageDataMimeTypeIfNeeded(id, it)
            if (normalized !== it) {
                imagePool[id] = normalized
                saveToDisk(id, normalized)
            }
            AppLogger.d(TAG, "从内存缓存获取图片: $id")
            return normalized
        }

        val imageData = loadFromDisk(id)
        if (imageData != null) {
            val normalized = normalizeImageDataMimeTypeIfNeeded(id, imageData)
            AppLogger.d(TAG, "从磁盘缓存加载图片到内存: $id")
            imagePool[id] = normalized
            if (normalized !== imageData) {
                saveToDisk(id, normalized)
            }
            return normalized
        }

        AppLogger.w(TAG, "图片不存在: $id")
        return null
    }

    @Synchronized
    fun getImageMimeType(id: String): String? = getImage(id)?.mimeType

    @Synchronized
    fun removeImage(id: String) {
        if (imagePool.remove(id) != null) {
            AppLogger.d(TAG, "从内存缓存移除图片: $id")
        }
        deleteFromDisk(id)
    }

    @Synchronized
    fun clear() {
        imagePool.clear()
        clearDiskCache()
        AppLogger.d(TAG, "清空图片池和磁盘缓存")
    }

    @Synchronized
    fun size(): Int = imagePool.size

    @Synchronized
    fun preloadFromDisk() {
        loadAllFromDisk()
    }

    private fun registerBitmap(
        sourceBitmap: Bitmap,
        sourceMimeType: String,
        options: ImageRegistrationOptions?,
        sourcePath: String? = null,
        recycleSource: Boolean
    ): String {
        val resolvedOptions = resolveOptions(options)
        var workingBitmap = sourceBitmap
        var ownsWorkingBitmap = recycleSource

        try {
            if (resolvedOptions.normalizeExif && sourcePath != null) {
                val normalizedBitmap = normalizeBitmapOrientationIfNeeded(workingBitmap, sourcePath)
                if (normalizedBitmap !== workingBitmap) {
                    if (ownsWorkingBitmap) {
                        workingBitmap.recycle()
                    }
                    workingBitmap = normalizedBitmap
                    ownsWorkingBitmap = true
                }
            }

            val scaledBitmap = scaleBitmapIfNeeded(workingBitmap, resolvedOptions.scalePercent)
            if (scaledBitmap !== workingBitmap) {
                if (ownsWorkingBitmap) {
                    workingBitmap.recycle()
                }
                workingBitmap = scaledBitmap
                ownsWorkingBitmap = true
            }

            val longEdgeLimitedBitmap =
                limitBitmapLongEdgeIfNeeded(workingBitmap, resolvedOptions.maxLongEdge)
            if (longEdgeLimitedBitmap !== workingBitmap) {
                if (ownsWorkingBitmap) {
                    workingBitmap.recycle()
                }
                workingBitmap = longEdgeLimitedBitmap
                ownsWorkingBitmap = true
            }

            val finalFormat = resolveOutputFormat(workingBitmap, resolvedOptions.outputFormat)
            val outputBitmap =
                if (finalFormat == ImageOutputFormat.JPEG && workingBitmap.hasAlpha()) {
                    val flattened = flattenAlphaForJpeg(workingBitmap)
                    if (flattened !== workingBitmap) {
                        if (ownsWorkingBitmap) {
                            workingBitmap.recycle()
                        }
                        workingBitmap = flattened
                        ownsWorkingBitmap = true
                    }
                    workingBitmap
                } else {
                    workingBitmap
                }

            val finalBytes =
                encodeBitmap(
                    bitmap = outputBitmap,
                    outputFormat = finalFormat,
                    jpegQuality = resolvedOptions.jpegQuality
                )

            val declaredFinalMimeType =
                when (finalFormat) {
                    ImageOutputFormat.PNG -> "image/png"
                    ImageOutputFormat.JPEG -> "image/jpeg"
                    ImageOutputFormat.AUTO -> error("AUTO must be resolved before encoding")
                }
            val detectedFinalMimeType = detectImageMimeTypeFromBytes(finalBytes)
            val finalMimeType = detectedFinalMimeType ?: declaredFinalMimeType
            if (detectedFinalMimeType != null &&
                !detectedFinalMimeType.equals(declaredFinalMimeType, ignoreCase = true)
            ) {
                AppLogger.w(
                    TAG,
                    "图片编码结果与声明格式不一致，已按真实编码修正: declared=$declaredFinalMimeType, detected=$detectedFinalMimeType, sourceMime=$sourceMimeType"
                )
            }

            val imageData =
                ImageData(
                    base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP),
                    mimeType = finalMimeType,
                    width = outputBitmap.width,
                    height = outputBitmap.height
                )

            val id = UUID.randomUUID().toString()
            imagePool[id] = imageData
            saveToDisk(id, imageData)

            AppLogger.d(
                TAG,
                "成功添加图片到池子: $id, mime=$finalMimeType, size=${imageData.width}x${imageData.height}, sourceMime=$sourceMimeType"
            )
            return id
        } catch (e: Exception) {
            AppLogger.e(TAG, "处理图片入池失败, sourceMime=$sourceMimeType", e)
            return "error"
        } finally {
            if (ownsWorkingBitmap) {
                try {
                    workingBitmap.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun resolveOptions(options: ImageRegistrationOptions?): ResolvedRegistrationOptions {
        val merged = options ?: DEFAULT_REGISTRATION_OPTIONS
        return ResolvedRegistrationOptions(
            scalePercent =
                (merged.scalePercent ?: DEFAULT_SCALE_PERCENT)
                    .coerceIn(1, 100),
            outputFormat = merged.outputFormat,
            jpegQuality =
                (merged.jpegQuality ?: DEFAULT_JPEG_QUALITY)
                    .coerceIn(1, 100),
            normalizeExif = merged.normalizeExif,
            maxLongEdge =
                when {
                    options == null -> DEFAULT_MAX_LONG_EDGE
                    options.maxLongEdge != null -> options.maxLongEdge.takeIf { it > 0 }
                    else -> DEFAULT_MAX_LONG_EDGE
                }
        )
    }

    private fun normalizeBase64Input(base64: String, mimeType: String): NormalizedBase64Input {
        val trimmed = base64.trim()
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            val commaIndex = trimmed.indexOf(',')
            if (commaIndex > 0) {
                val header = trimmed.substring(5, commaIndex)
                val headerMimeType = header.substringBefore(';').trim()
                return NormalizedBase64Input(
                    base64 = trimmed.substring(commaIndex + 1),
                    mimeType = headerMimeType.ifBlank { mimeType.ifBlank { "image/png" } }
                )
            }
        }
        return NormalizedBase64Input(
            base64 = trimmed,
            mimeType = mimeType.ifBlank { "image/png" }
        )
    }

    private fun decodeBitmapFromFile(filePath: String): Bitmap? {
        val options =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return try {
            BitmapFactory.decodeFile(filePath, options)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeBitmapFromBytes(bytes: ByteArray): Bitmap? {
        val options =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Throwable) {
            null
        }
    }

    private fun detectImageMimeTypeFromBytes(bytes: ByteArray): String? {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.outMimeType?.takeIf { it.startsWith("image/", ignoreCase = true) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizeImageDataMimeTypeIfNeeded(id: String, imageData: ImageData): ImageData {
        if (!imageData.mimeType.startsWith("image/", ignoreCase = true)) {
            return imageData
        }
        val bytes = try {
            Base64.decode(imageData.base64, Base64.DEFAULT)
        } catch (_: Throwable) {
            return imageData
        }
        val detectedMimeType = detectImageMimeTypeFromBytes(bytes) ?: return imageData
        if (detectedMimeType.equals(imageData.mimeType, ignoreCase = true)) {
            return imageData
        }
        AppLogger.w(
            TAG,
            "检测到图片MIME与真实编码不一致，已修正: id=$id, stored=${imageData.mimeType}, detected=$detectedMimeType"
        )
        return imageData.copy(mimeType = detectedMimeType)
    }

    private fun normalizeBitmapOrientationIfNeeded(bitmap: Bitmap, filePath: String): Bitmap {
        val orientation =
            try {
                ExifInterface(filePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "读取 EXIF 方向失败: $filePath", e)
                ExifInterface.ORIENTATION_NORMAL
            }

        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, scalePercent: Int): Bitmap {
        if (scalePercent >= 100) {
            return bitmap
        }

        val newWidth = (bitmap.width * scalePercent / 100f).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scalePercent / 100f).toInt().coerceAtLeast(1)

        if (newWidth == bitmap.width && newHeight == bitmap.height) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun limitBitmapLongEdgeIfNeeded(bitmap: Bitmap, maxLongEdge: Int?): Bitmap {
        val targetLongEdge = maxLongEdge?.takeIf { it > 0 } ?: return bitmap
        val currentLongEdge = maxOf(bitmap.width, bitmap.height)
        if (currentLongEdge <= targetLongEdge) {
            return bitmap
        }

        val scale = targetLongEdge.toFloat() / currentLongEdge.toFloat()
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        if (newWidth == bitmap.width && newHeight == bitmap.height) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun resolveOutputFormat(
        bitmap: Bitmap,
        outputFormat: ImageOutputFormat
    ): ImageOutputFormat {
        return when (outputFormat) {
            ImageOutputFormat.PNG -> ImageOutputFormat.PNG
            ImageOutputFormat.JPEG -> ImageOutputFormat.JPEG
            ImageOutputFormat.AUTO ->
                if (bitmap.hasAlpha()) ImageOutputFormat.PNG else ImageOutputFormat.JPEG
        }
    }

    private fun flattenAlphaForJpeg(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) {
            return bitmap
        }
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return flattened
    }

    private fun encodeBitmap(
        bitmap: Bitmap,
        outputFormat: ImageOutputFormat,
        jpegQuality: Int
    ): ByteArray {
        val compressFormat =
            when (outputFormat) {
                ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageOutputFormat.AUTO -> error("AUTO must be resolved before encoding")
            }

        val quality =
            when (compressFormat) {
                Bitmap.CompressFormat.PNG -> 100
                else -> jpegQuality
            }

        return ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(compressFormat, quality, output)) {
                error("Bitmap compress failed")
            }
            output.toByteArray()
        }
    }

    private fun getMimeTypeFromFile(file: File): String? {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-ico"
            else -> {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    options.outMimeType
                } catch (e: Exception) {
                    AppLogger.e(TAG, "无法通过文件头识别MIME类型", e)
                    null
                }
            }
        }
    }

    private fun saveToDisk(id: String, imageData: ImageData) {
        val dir = cacheDir
        if (dir == null) {
            AppLogger.w(TAG, "缓存目录未初始化，跳过磁盘保存")
            return
        }

        try {
            val dataFile = File(dir, "$id.dat")
            val metaFile = File(dir, "$id.meta")
            FileOutputStream(dataFile).use { it.write(imageData.base64.toByteArray()) }
            val meta =
                JSONObject()
                    .put("mimeType", imageData.mimeType)
                    .put("width", imageData.width)
                    .put("height", imageData.height)
            FileOutputStream(metaFile).use { it.write(meta.toString().toByteArray()) }
            AppLogger.d(TAG, "图片已保存到磁盘: $id")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存图片到磁盘失败: $id", e)
        }
    }

    private fun loadFromDisk(id: String): ImageData? {
        val dir = cacheDir ?: return null

        return try {
            val dataFile = File(dir, "$id.dat")
            val metaFile = File(dir, "$id.meta")

            if (!dataFile.exists() || !metaFile.exists()) {
                return null
            }

            val base64 = dataFile.readText()
            val meta = JSONObject(metaFile.readText())
            ImageData(
                base64 = base64,
                mimeType = meta.optString("mimeType", "image/png"),
                width = meta.optInt("width", 0),
                height = meta.optInt("height", 0)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘加载图片失败: $id", e)
            null
        }
    }

    @Synchronized
    private fun loadAllFromDisk() {
        val dir = cacheDir ?: return
        if (!dir.exists()) return

        try {
            val files = dir.listFiles { file -> file.extension == "meta" } ?: return
            var loadedCount = 0
            for (file in files) {
                val id = file.nameWithoutExtension
                val imageData = loadFromDisk(id) ?: continue
                imagePool[id] = imageData
                loadedCount++
            }
            AppLogger.d(TAG, "从磁盘加载了 $loadedCount 张图片到内存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘加载图片失败", e)
        }
    }

    private fun deleteFromDisk(id: String) {
        val dir = cacheDir ?: return

        try {
            File(dir, "$id.dat").delete()
            File(dir, "$id.meta").delete()
            AppLogger.d(TAG, "从磁盘删除图片: $id")
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘删除图片失败: $id", e)
        }
    }

    private fun clearDiskCache() {
        val dir = cacheDir ?: return
        if (!dir.exists()) return

        try {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            AppLogger.d(TAG, "已清空磁盘缓存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "清空磁盘缓存失败", e)
        }
    }
}
