package com.ai.assistance.operit.util.media

import com.ai.assistance.operit.util.logging.AppLogger
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Utility class for FFmpeg operations
 */
object FFmpegUtil {
    private const val TAG = "FFmpegUtil"

    /**
     * Build a scale filter string that survives FFmpegKit argument parsing.
     * FFmpeg expressions need an escaped comma when passed without a shell.
     */
    fun scaleFilterMaxWidth(maxWidth: Int): String = "scale=min(${maxWidth}\\,iw):-2"

    /**
     * Execute an FFmpeg command and return if it was successful
     */
    fun executeCommand(command: String): Boolean {
        try {
            AppLogger.d(TAG, "Executing FFmpeg command: $command")
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                AppLogger.d(TAG, "FFmpeg command executed successfully")
                return true
            } else {
                AppLogger.e(
                    TAG,
                    "FFmpeg failed with return code: ${returnCode.value}, output: ${session.output}"
                )
                return false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing FFmpeg command", e)
            return false
        }
    }

    /**
     * Get media information for a file
     */
    fun getMediaInfo(filePath: String): MediaInformation? {
        return try {
            val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
            mediaInfoSession.mediaInformation
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting media info: ${e.message}")
            null
        }
    }
} 
