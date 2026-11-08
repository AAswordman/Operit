package com.ai.assistance.operit.util.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.ai.assistance.operit.util.logging.AppLogger

object ImageBitmapLimiter {
    private const val UI_MAX_PIXELS = 12_000_000L
    private const val UI_MAX_DIMENSION = 4096

    private const val AI_MAX_PIXELS = 12_000_000L
    private const val AI_MAX_DIMENSION = 4096

    data class LimitedImage(
        val base64: String,
        val mimeType: String
    )

    private data class ImageBounds(
        val width: Int,
        val height: Int
    )

    fun decodeDownsampledBitmap(
        bytes: ByteArray,
        maxPixels: Long = UI_MAX_PIXELS,
        maxDimension: Int = UI_MAX_DIMENSION
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) {
            return null
        }

        val sampleSize = calculateSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            maxPixels = maxPixels,
            maxDimension = maxDimension
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (_: Throwable) {
            null
        }
    }

    fun limitBase64ForAi(base64: String, mimeType: String): LimitedImage? {
        val bytes = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Throwable) {
            return null
        }

        val bounds = decodeImageBounds(bytes) ?: return null
        val needsDownsample =
            bounds.width > AI_MAX_DIMENSION ||
                bounds.height > AI_MAX_DIMENSION ||
                bounds.width.toLong() * bounds.height.toLong() > AI_MAX_PIXELS

        if (!needsDownsample) {
            return LimitedImage(
                base64 = base64,
                mimeType = mimeType
            )
        }

        val sampleSizeForLog = calculateSampleSize(
            srcWidth = bounds.width,
            srcHeight = bounds.height,
            maxPixels = AI_MAX_PIXELS,
            maxDimension = AI_MAX_DIMENSION
        )
        AppLogger.i(
            "ImageBitmapLimiter",
            "AI image resize triggered: mimeType=$mimeType, src=${bounds.width}x${bounds.height}, sampleSize=$sampleSizeForLog"
        )

        val bitmap = decodeDownsampledBitmap(
            bytes = bytes,
            maxPixels = AI_MAX_PIXELS,
            maxDimension = AI_MAX_DIMENSION
        ) ?: return null

        try {
            val format = compressFormatForMimeType(mimeType) ?: return null
            val quality = when (format) {
                Bitmap.CompressFormat.PNG -> 100
                else -> 95
            }

            val outBytes = encodeBitmap(bitmap, format, quality)

            AppLogger.i(
                "ImageBitmapLimiter",
                "AI image re-encoded: mimeType=$mimeType, format=$format, outBytes=${outBytes.size}, bitmap=${bitmap.width}x${bitmap.height}"
            )

            return LimitedImage(
                base64 = Base64.encodeToString(outBytes, Base64.NO_WRAP),
                mimeType = mimeType
            )
        } catch (_: Throwable) {
            return null
        } finally {
            try {
                bitmap.recycle()
            } catch (_: Throwable) {
            }
        }
    }

    private fun encodeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.use { out ->
            bitmap.compress(format, quality, out)
            return out.toByteArray()
        }
    }

    private fun compressFormatForMimeType(mimeType: String): Bitmap.CompressFormat? {
        val mt = mimeType.trim().lowercase().substringBefore(';')
        return when {
            mt == "image/png" -> Bitmap.CompressFormat.PNG
            mt == "image/webp" -> Bitmap.CompressFormat.WEBP
            mt == "image/jpeg" || mt == "image/jpg" -> Bitmap.CompressFormat.JPEG
            else -> null
        }
    }

    private fun decodeImageBounds(bytes: ByteArray): ImageBounds? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) null else ImageBounds(w, h)
        } catch (_: Throwable) {
            null
        }
    }

    private fun calculateSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        maxPixels: Long,
        maxDimension: Int
    ): Int {
        var sampleSize = 1
        while (sampleSize < 128) {
            val w = srcWidth / sampleSize
            val h = srcHeight / sampleSize

            if (w <= 0 || h <= 0) {
                break
            }

            if (w <= maxDimension && h <= maxDimension && w.toLong() * h.toLong() <= maxPixels) {
                break
            }

            sampleSize *= 2
        }
        return sampleSize
    }
}
