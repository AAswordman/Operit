package com.ai.assistance.operit.core.tools.system.capture

import android.content.Context
import android.media.projection.MediaProjection
import android.content.Intent

/**
 * Global holder for the MediaProjection token/instance.
 * This allows StandardUITools to access the projection without passing it around everywhere.
 */
object MediaProjectionHolder {
    var mediaProjection: MediaProjection? = null
    var permissionResultData: Intent? = null
    var permissionResultCode: Int = 0

    fun clear(context: Context) {
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        mediaProjection = null
        permissionResultData = null
        permissionResultCode = 0
        ScreenCaptureService.stop(context)
    }
}
