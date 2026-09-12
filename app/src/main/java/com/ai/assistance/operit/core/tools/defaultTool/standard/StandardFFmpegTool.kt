package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.tools.FFmpegResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.FFmpegUtil
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/** FFmpeg工具执行器 提供媒体文件处理能力，包括转换、裁剪、合并等功能 */
class StandardFFmpegToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        val command = tool.parameters.find { it.name == "command" }?.value ?: ""

        if (command.isEmpty()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Command cannot be empty"
            )
        }

        return try {
            val startTime = System.currentTimeMillis()

            // 执行FFmpeg命令
            val session = FFmpegUtil.execute(command)
            val returnCode = session.returnCode
            val output = session.output ?: ""
            val duration = System.currentTimeMillis() - startTime

            if (ReturnCode.isSuccess(returnCode)) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FFmpegResultData(
                                        command = command,
                                        returnCode = returnCode.value,
                                        output = output,
                                        duration = duration
                                )
                )
            } else if (ReturnCode.isCancel(returnCode)) {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "FFmpeg command was cancelled"
                )
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "FFmpeg execution failed, return code: ${returnCode.value}\nOutput:\n$output"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "FFmpeg execution exception: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val command = tool.parameters.find { it.name == "command" }?.value

        if (command.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "Must provide command parameter")
        }

        return ToolValidationResult(valid = true)
    }
}

/** FFmpeg信息工具执行器 获取有关系统FFmpeg配置的信息 */
class StandardFFmpegInfoToolExecutor : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegInfoToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        return try {
            val info = StringBuilder()
            val startTime = System.currentTimeMillis()

            // 获取FFmpeg版本信息
            info.appendLine("FFmpeg version: ${FFmpegKitConfig.getVersion()}")
            info.appendLine("Build configuration: ${FFmpegKitConfig.getBuildDate()}")

            // 列出支持的编解码器
            val codecsSession = FFmpegUtil.execute("-codecs")
            val codecsOutput = codecsSession.output ?: ""
            val duration = System.currentTimeMillis() - startTime

            info.appendLine("\nSupported codecs:")
            info.appendLine(codecsOutput)

            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FFmpegResultData(
                                    command = "-codecs",
                                    returnCode = codecsSession.returnCode.value,
                                    output = info.toString(),
                                    duration = duration
                            )
            )
        } catch (e: Exception) {
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get FFmpeg info: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // 不需要参数
        return ToolValidationResult(valid = true)
    }
}

/** FFmpeg转换视频工具执行器 提供一个简化的接口用于常见的视频转换操作 */
class StandardFFmpegConvertToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegConvertToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        val inputPath = tool.parameters.find { it.name == "input_path" }?.value ?: ""
        val outputPath = tool.parameters.find { it.name == "output_path" }?.value ?: ""
        val format = tool.parameters.find { it.name == "format" }?.value
        val resolution = tool.parameters.find { it.name == "resolution" }?.value
        val bitrate = tool.parameters.find { it.name == "bitrate" }?.value
        val audioCodec = tool.parameters.find { it.name == "audio_codec" }?.value
        val videoCodec = tool.parameters.find { it.name == "video_codec" }?.value

        if (inputPath.isEmpty() || outputPath.isEmpty()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Input path and output path cannot be empty"
            )
        }

        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Input file does not exist: $inputPath"
            )
        }

        // 构建FFmpeg命令
        val commandBuilder = StringBuilder("-i \"$inputPath\"")

        // 添加可选参数
        if (!videoCodec.isNullOrEmpty()) {
            commandBuilder.append(" -c:v $videoCodec")
        }

        if (!audioCodec.isNullOrEmpty()) {
            commandBuilder.append(" -c:a $audioCodec")
        }

        if (!resolution.isNullOrEmpty()) {
            commandBuilder.append(" -s $resolution")
        }

        if (!bitrate.isNullOrEmpty()) {
            commandBuilder.append(" -b:v $bitrate")
        }

        // 添加输出文件
        commandBuilder.append(" \"$outputPath\"")

        val command = commandBuilder.toString()

        return try {
            val startTime = System.currentTimeMillis()

            // 执行FFmpeg命令
            val session = FFmpegUtil.execute(command)
            val returnCode = session.returnCode
            val output = session.output ?: ""
            val duration = System.currentTimeMillis() - startTime

            if (ReturnCode.isSuccess(returnCode)) {
                // 获取输出文件的媒体信息
                val mediaSession = FFmpegUtil.getMediaInformation(outputPath)
                val mediaInfo = mediaSession?.mediaInformation

                val ffmpegResult =
                        if (mediaInfo != null) {
                            val videoStreams =
                                    mediaInfo
                                            .streams
                                            .filter { it.type.equals("video", ignoreCase = true) }
                                            .map { stream ->
                                                FFmpegResultData.StreamInfo(
                                                        index = stream.index?.toInt() ?: 0,
                                                        codecType = stream.type ?: "unknown",
                                                        codecName = stream.codec ?: "unknown",
                                                        resolution =
                                                                "${stream.width}x${stream.height}",
                                                        frameRate =
                                                                null // We'll get this from FFprobe
                                                        // if needed
                                                        )
                                            }
                                            .toMutableList()

                            val audioStreams =
                                    mediaInfo
                                            .streams
                                            .filter { it.type.equals("audio", ignoreCase = true) }
                                            .map { stream ->
                                                FFmpegResultData.StreamInfo(
                                                        index = stream.index?.toInt() ?: 0,
                                                        codecType = stream.type ?: "unknown",
                                                        codecName = stream.codec ?: "unknown",
                                                        sampleRate =
                                                                null, // We'll get this from FFprobe
                                                        // if needed
                                                        channels =
                                                                null // We'll get this from FFprobe
                                                        // if needed
                                                        )
                                            }
                                            .toMutableList()

                            val ffprobeInfo = mediaInfo

                            if (ffprobeInfo != null) {
                                // Update stream information with FFprobe data
                                ffprobeInfo.streams.forEach { probeStream ->
                                    when (probeStream.type) {
                                        "video" -> {
                                            val index =
                                                    videoStreams.indexOfFirst {
                                                        it.index == probeStream.index?.toInt()
                                                    }
                                            if (index != -1) {
                                                val stream = videoStreams[index]
                                                videoStreams[index] =
                                                        stream.copy(
                                                                frameRate =
                                                                        probeStream
                                                                                .allProperties
                                                                                ?.get(
                                                                                        "r_frame_rate"
                                                                                )
                                                                                ?.toString()
                                                        )
                                            }
                                        }
                                        "audio" -> {
                                            val index =
                                                    audioStreams.indexOfFirst {
                                                        it.index == probeStream.index?.toInt()
                                                    }
                                            if (index != -1) {
                                                val stream = audioStreams[index]
                                                audioStreams[index] =
                                                        stream.copy(
                                                                sampleRate =
                                                                        probeStream
                                                                                .allProperties
                                                                                ?.get("sample_rate")
                                                                                ?.toString(),
                                                                channels =
                                                                        probeStream
                                                                                .allProperties
                                                                                ?.get("channels")
                                                                                ?.toString()
                                                                                ?.toIntOrNull()
                                                        )
                                            }
                                        }
                                    }
                                }
                            }

                            FFmpegResultData(
                                    command = command,
                                    returnCode = returnCode.value,
                                    output = output,
                                    duration = duration,
                                    outputFile = outputPath,
                                    mediaInfo =
                                            FFmpegResultData.MediaInfo(
                                                    format = mediaInfo.format ?: "unknown",
                                                    duration = mediaInfo.duration ?: "0",
                                                    bitrate = mediaInfo.bitrate ?: "0",
                                                    videoStreams = videoStreams,
                                                    audioStreams = audioStreams
                                            )
                            )
                        } else {
                            FFmpegResultData(
                                    command = command,
                                    returnCode = returnCode.value,
                                    output = output,
                                    duration = duration,
                                    outputFile = outputPath
                            )
                        }

                ToolResult(toolName = tool.name, success = true, result = ffmpegResult)
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Video conversion failed, return code: ${returnCode.value}\nCommand: $command\nOutput:\n$output"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Video conversion exception: ${e.message}\nCommand: $command"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
        val outputPath = tool.parameters.find { it.name == "output_path" }?.value

        if (inputPath.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "Must provide input_path parameter")
        }

        if (outputPath.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "Must provide output_path parameter")
        }

        return ToolValidationResult(valid = true)
    }
}
