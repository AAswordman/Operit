package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.AppLogger
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.MediaInformationSession
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Utility class for FFmpeg operations.
 *
 * FFmpegKit's bundled libavcodec is not safe for overlapping sessions. Concurrent execute/probe
 * calls can destroy the same pthread mutex twice and abort the process on Android 17 FORTIFY.
 */
object FFmpegUtil {
    private const val TAG = "FFmpegUtil"
    private val nativeSessionLock = Any()

    /**
     * Build a scale filter string that survives FFmpegKit argument parsing.
     * FFmpeg expressions need an escaped comma when passed without a shell.
     */
    fun scaleFilterMaxWidth(maxWidth: Int): String = "scale=min(${maxWidth}\\,iw):-2"

    fun <T> withNativeSession(block: () -> T): T = synchronized(nativeSessionLock) { block() }

    fun execute(command: String): FFmpegSession =
        withNativeSession { FFmpegKit.execute(command) }

    fun getMediaInformation(filePath: String): MediaInformationSession =
        withNativeSession { FFprobeKit.getMediaInformation(filePath) }

    fun executeCommand(command: String): Boolean {
        try {
            AppLogger.d(TAG, "Executing FFmpeg command: $command")
            val session = execute(command)
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

    fun getMediaInfo(filePath: String): MediaInformation? {
        return try {
            getMediaInformation(filePath).mediaInformation
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting media info: ${e.message}")
            null
        }
    }
}
