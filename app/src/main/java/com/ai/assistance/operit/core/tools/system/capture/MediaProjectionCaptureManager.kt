package com.ai.assistance.operit.core.tools.system.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.io.FileOutputStream

/**
 * Dedicated manager for capturing screenshots using Android's MediaProjection API.
 * This is used for Standard permission level users who don't have ADB access.
 */
class MediaProjectionCaptureManager(private val context: Context, private val mediaProjection: MediaProjection) {

    companion object {
        private const val TAG = "MediaProjectionCapture"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val callbackHandler = Handler(Looper.getMainLooper())
    private var projectionCallback: MediaProjection.Callback? = null
    
    /**
     * Set up the virtual display using the MediaProjection token.
     */
    fun setupDisplay() {
        if (virtualDisplay != null) return
        
        try {
            ensureProjectionCallbackRegistered()

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val densityDpi = metrics.densityDpi

            // Using RGBA_8888
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "OperitScreenCapture",
                    width,
                    height,
                    densityDpi,
                    flags,
                    reader.surface,
                    null,
                    null
            )
            
            AppLogger.d(TAG, "Created MediaProjection virtual display: ${width}x${height}")
        } catch (e: Exception) {
            try {
                imageReader?.close()
            } catch (_: Exception) {
            }
            imageReader = null
            AppLogger.e(TAG, "Failed to create MediaProjection virtual display", e)
        }
    }

    private fun ensureProjectionCallbackRegistered() {
        if (projectionCallback != null) return

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                AppLogger.w(TAG, "MediaProjection stopped")
                try {
                    MediaProjectionHolder.clear(context)
                } catch (_: Exception) {
                }
                release()
            }
        }

        projectionCallback = callback
        try {
            mediaProjection.registerCallback(callback, callbackHandler)
        } catch (e: Exception) {
            projectionCallback = null
            AppLogger.e(TAG, "Failed to register MediaProjection callback", e)
        }
    }

    /**
     * Capture the latest frame as a raw bitmap.
     */
    fun captureToBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        return try {
            // Try to get the latest image
            image = reader.acquireLatestImage()
            if (image == null) {
                 // Sometimes it takes a moment for the first frame to arrive
                 return null
            }

            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) {
                return null
            }
            
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()

            cropped
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error capturing frame from MediaProjection", e)
            null
        } finally {
            image?.close()
        }
    }

    /**
     * Capture the latest frame to a file.
     */
    fun captureToFile(file: File): Boolean {
        val bitmap = captureToBitmap() ?: return false
        return try {
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error writing MediaProjection capture to file", e)
            false
        } finally {
            bitmap.recycle()
        }
    }

    fun release() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing resources", e)
        }
        virtualDisplay = null
        imageReader = null

        val callback = projectionCallback
        if (callback != null) {
            try {
                mediaProjection.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        projectionCallback = null
    }
}
