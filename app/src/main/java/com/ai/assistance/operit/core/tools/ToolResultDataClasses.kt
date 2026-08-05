package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.Locale

/**
 * This file contains all implementations of ToolResultData Centralized for easier maintenance and
 * integration
 */
@Serializable
sealed class ToolResultData {
    /** Converts the structured data to a string representation */
    abstract override fun toString(): String
    fun toJson(): String {
        val jsonConfig = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "__type"
        }
        val json = jsonConfig.encodeToString(this)
        return json
    }
}

// Basic result data types (moved from AITool.kt)
@Serializable
data class BooleanResultData(val value: Boolean) : ToolResultData() {
    override fun toString(): String = value.toString()
}

@Serializable
data class StringResultData(val value: String) : ToolResultData() {
    override fun toString(): String = value
}

@Serializable
data class SleepResultData(
        val requestedMs: Int,
        val sleptMs: Int
) : ToolResultData() {
    override fun toString(): String = "Slept for ${sleptMs}ms"
}

@Serializable
data class SandboxScriptExecutionResultData(
        val success: Boolean,
        val scriptPath: String,
        val functionName: String,
        val params: JsonElement? = null,
        val envFilePath: String? = null,
        val startedAtMs: Long,
        val finishedAtMs: Long,
        val durationMs: Long,
        val result: JsonElement? = null,
        val error: String? = null,
        val events: List<String> = emptyList(),
        val executionMode: String? = null,
        val scriptLabel: String? = null,
        val requestedWaitMs: Long? = null
) : ToolResultData() {
    override fun toString(): String {
        return if (success) {
            "Sandbox script executed successfully (${durationMs}ms)"
        } else {
            error ?: "Sandbox script execution failed"
        }
    }
}

@Serializable
data class EnvironmentVariableReadResultData(
        val key: String,
        val value: String? = null,
        val exists: Boolean
) : ToolResultData() {
    override fun toString(): String {
        return if (exists) {
            "$key=${value.orEmpty()}"
        } else {
            "$key is not set"
        }
    }
}

@Serializable
data class EnvironmentVariableWriteResultData(
        val key: String,
        val requestedValue: String,
        val value: String? = null,
        val exists: Boolean,
        val cleared: Boolean
) : ToolResultData() {
    override fun toString(): String {
        return if (cleared) {
            "$key cleared"
        } else {
            "$key=${value.orEmpty()}"
        }
    }
}

@Serializable
data class SandboxPackageResultItem(
        val packageName: String,
        val displayName: String,
        val description: String,
        val isBuiltIn: Boolean,
        val enabledByDefault: Boolean,
        val enabled: Boolean,
        val imported: Boolean,
        val isDisabledByUser: Boolean,
        val toolCount: Int,
        val manageMode: String
)

@Serializable
data class SandboxPackagesResultData(
        val externalPackagesPath: String,
        val scriptDevGuide: String,
        val totalCount: Int,
        val builtInCount: Int,
        val externalCount: Int,
        val enabledCount: Int,
        val disabledCount: Int,
        val packages: List<SandboxPackageResultItem>,
        val packageLoadErrors: Map<String, String> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        return "Sandbox packages: total=$totalCount, enabled=$enabledCount, builtIn=$builtInCount, external=$externalCount"
    }
}

@Serializable
data class SandboxPackageUpdateResultData(
        val packageName: String,
        val requestedEnabled: Boolean,
        val previousEnabled: Boolean,
        val currentEnabled: Boolean,
        val message: String
) : ToolResultData() {
    override fun toString(): String {
        return message
    }
}

@Serializable
data class McpRestartLogPluginResultItem(
        val id: String,
        val displayName: String,
        val shortName: String,
        val status: String,
        val message: String,
        val serviceName: String,
        val log: String
)

@Serializable
data class McpRestartWithLogsResultData(
        val timeoutMs: Long,
        val elapsedMs: Long,
        val timedOut: Boolean,
        val progress: Double,
        val message: String,
        val pluginsTotal: Int,
        val pluginsStarted: Int,
        val successCount: Int,
        val failedCount: Int,
        val plugins: List<McpRestartLogPluginResultItem>,
        val extraLogs: Map<String, String>
) : ToolResultData() {
    override fun toString(): String {
        return "MCP restart: success=$successCount, failed=$failedCount, timedOut=$timedOut"
    }
}

@Serializable
data class ScriptExecutionTraceData(
        val kind: String,
        val level: String? = null,
        val message: String,
        val callId: String? = null,
        val timestampMs: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        val prefix =
                when (kind.lowercase(Locale.ROOT)) {
                    "log" ->
                            level
                                    ?.takeIf { it.isNotBlank() }
                                    ?.uppercase(Locale.ROOT)
                                    ?.let { "[$it] " }
                                    ?: "[LOG] "
                    "intermediate" -> "[INTERMEDIATE] "
                    else -> "[TRACE] "
                }
        return prefix + message
    }
}

@Serializable
data class IntResultData(val value: Int) : ToolResultData() {
    override fun toString(): String = value.toString()
}

@Serializable
data class BinaryResultData(val value: ByteArray) : ToolResultData() {
    override fun toString(): String = "Binary data (${value.size} bytes)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BinaryResultData
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

/** 文件分段读取结果数据 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FilePartContentData(
        val path: String,
        val content: String,
        val partIndex: Int,
        val totalParts: Int,
        val startLine: Int,
        val endLine: Int,
        val totalLines: Int,
        @EncodeDefault
        val env: String = "android"
) : ToolResultData() {
    override fun toString(): String {
        val partInfo =
                "Part ${partIndex + 1} of $totalParts (Lines ${startLine + 1}-$endLine of $totalLines)"
        return "[$env] $partInfo\n\n$content"
    }
}

/** ADB命令执行结果数据 */
@Serializable
data class ADBResultData(val command: String, val output: String, val exitCode: Int) :
        ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("ADB Command Execution Result:")
        sb.appendLine("Command: $command")
        sb.appendLine("Exit Code: $exitCode")
        sb.appendLine("\nOutput:")
        sb.appendLine(output)
        return sb.toString()
    }
}

/** 终端命令执行结果数据 */
@Serializable
data class TerminalCommandResultData(
        val command: String,
        val output: String,
        val exitCode: Int,
        val sessionId: String,
        val timedOut: Boolean = false
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Terminal Command Execution Result:")
        sb.appendLine("Command: $command")
        sb.appendLine("Session: $sessionId")
        sb.appendLine("Exit Code: $exitCode")
        if (timedOut) {
            sb.appendLine("Timed Out: true")
        }
        sb.appendLine("\nOutput:")
        sb.appendLine(output)
        return sb.toString()
    }
}

/** 终端命令流式事件数据 */
@Serializable
data class TerminalStreamEventData(
        val type: String,
        val command: String,
        val sessionId: String,
        val chunk: String? = null,
        val chunkIndex: Int? = null,
        val receivedChars: Int? = null
) : ToolResultData() {
    override fun toString(): String {
        return when (type) {
            "chunk" -> chunk.orEmpty()
            "start" -> "Terminal stream started"
            else -> "Terminal stream event: $type"
        }
    }
}

/** 隐藏终端命令执行结果数据 */
@Serializable
data class HiddenTerminalCommandResultData(
        val command: String,
        val output: String,
        val exitCode: Int,
        val executorKey: String,
        val timedOut: Boolean = false
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Hidden Terminal Command Execution Result:")
        sb.appendLine("Command: $command")
        sb.appendLine("Executor Key: $executorKey")
        sb.appendLine("Exit Code: $exitCode")
        if (timedOut) {
            sb.appendLine("Timed Out: true")
        }
        sb.appendLine("\nOutput:")
        sb.appendLine(output)
        return sb.toString()
    }
}

/** 音乐播放结果数据 */
@Serializable
data class MusicPlaybackResultData(
        val state: String,
        val source: String? = null,
        val sourceType: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val durationMs: Long? = null,
        val positionMs: Long = 0L,
        val bufferedPositionMs: Long = 0L,
        val volume: Float = 1f,
        val loop: Boolean = false,
        val queueIndex: Int? = null,
        val queueSize: Int? = null,
        val message: String = ""
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Music Playback Result:")
        sb.appendLine("State: $state")
        if (!title.isNullOrBlank()) sb.appendLine("Title: $title")
        if (!artist.isNullOrBlank()) sb.appendLine("Artist: $artist")
        if (!source.isNullOrBlank()) sb.appendLine("Source: $source")
        if (!sourceType.isNullOrBlank()) sb.appendLine("Source Type: $sourceType")
        durationMs?.let { sb.appendLine("Duration: ${it}ms") }
        sb.appendLine("Position: ${positionMs}ms")
        sb.appendLine("Buffered Position: ${bufferedPositionMs}ms")
        sb.appendLine("Volume: $volume")
        sb.appendLine("Loop: $loop")
        if (queueIndex != null && queueSize != null) {
            sb.appendLine("Queue: ${queueIndex + 1}/$queueSize")
        }
        if (message.isNotBlank()) sb.appendLine("Message: $message")
        return sb.toString()
    }
}

/** 计算结果结构化数据 */
@Serializable
data class CalculationResultData(
        val expression: String,
        val result: Double,
        val formattedResult: String,
        val variables: Map<String, Double> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Expression: $expression")
        sb.appendLine("Result: $formattedResult")

        if (variables.isNotEmpty()) {
            sb.appendLine("Variables:")
            variables.forEach { (name, value) -> sb.appendLine("  $name = $value") }
        }

        return sb.toString()
    }
}

/** 日期结果结构化数据 */
@Serializable
data class DateResultData(val date: String, val format: String, val formattedDate: String) :
        ToolResultData() {
    override fun toString(): String {
        return formattedDate
    }
}

/** Connection result data */
@Serializable
data class ConnectionResultData(
        val connectionId: String,
        val isActive: Boolean,
        val timestamp: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return "Simulated connection established. Demo connection ID: $connectionId"
    }
}

/** Represents a directory listing result */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class DirectoryListingData(
        val path: String,
        val entries: List<FileEntry>,
        @EncodeDefault
        val env: String = "android"
) : ToolResultData() {
    @Serializable
    data class FileEntry(
            val name: String,
            val isDirectory: Boolean,
            val size: Long,
            val permissions: String,
            val lastModified: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("[$env] Directory listing for $path:")
        entries.forEach { entry ->
            val typeIndicator = if (entry.isDirectory) "d" else "-"
            sb.appendLine(
                    "$typeIndicator${entry.permissions} ${
                    entry.size.toString().padStart(8)
                } ${entry.lastModified} ${entry.name}"
            )
        }
        return sb.toString()
    }
}

/** Represents a file content result */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FileContentData(
        val path: String,
        val content: String,
        val size: Long,
        @EncodeDefault
        val env: String = "android"
) :
        ToolResultData() {
    override fun toString(): String {
        return "[$env] Content of $path:\n$content"
    }
}

/** Represents a binary file content result (Base64 encoded) */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BinaryFileContentData(
        val path: String,
        val contentBase64: String,
        val size: Long,
        @EncodeDefault
        val env: String = "android"
) : ToolResultData() {
    override fun toString(): String {
        return "[$env] Binary content of $path (${size} bytes, base64 length=${contentBase64.length})"
    }
}

/** Represents file existence check result */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FileExistsData(
        val path: String,
        val exists: Boolean,
        val isDirectory: Boolean = false,
        val size: Long = 0,
        @EncodeDefault
        val env: String = "android"
) : ToolResultData() {
    override fun toString(): String {
        val fileType = if (isDirectory) "Directory" else "File"
        return if (exists) {
            "[$env] $fileType exists at path: $path (size: $size bytes)"
        } else {
            "[$env] No file or directory exists at path: $path"
        }
    }
}

/** Represents detailed file information */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FileInfoData(
        val path: String,
        val exists: Boolean,
        val fileType: String, // "file", "directory", or "other"
        val size: Long,
        val permissions: String,
        val owner: String,
        val group: String,
        val lastModified: String,
        val rawStatOutput: String,
        @EncodeDefault
        val env: String = "android"
) : ToolResultData() {
    override fun toString(): String {
        if (!exists) {
            return "[$env] File or directory does not exist at path: $path"
        }

        val sb = StringBuilder()
        sb.appendLine("[$env] File information for $path:")
        sb.appendLine("Type: $fileType")
        sb.appendLine("Size: $size bytes")
        sb.appendLine("Permissions: $permissions")
        sb.appendLine("Owner: $owner")
        sb.appendLine("Group: $group")
        sb.appendLine("Last modified: $lastModified")
        return sb.toString()
    }
}

/** Represents a file operation result */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FileOperationData(
        val operation: String,
        @EncodeDefault
        val env: String = "android",
        val path: String,
        val successful: Boolean,
        val details: String
) : ToolResultData() {
    override fun toString(): String {
        return "[$env] $details"
    }
}

/** Represents the result of an 'apply_file' operation, including the AI-generated diff */
@Serializable
data class FileApplyResultData(
    val operation: FileOperationData,
    val aiDiffInstructions: String,
    val diffContent: String? = null
) : ToolResultData() {
    private fun buildRequestContent(): String {
        val sections = mutableListOf<String>()
        sections.add(operation.toString())

        extractDiffSummaryLine()?.let { sections.add(it) }

        return sections.joinToString("\n")
    }

    private fun extractDiffSummaryLine(): String? {
        val candidates = listOfNotNull(diffContent, aiDiffInstructions)

        return candidates
            .asSequence()
            .flatMap { it.lineSequence() }
            .map { it.trim() }
            .firstOrNull {
                it.startsWith("Changes: +") ||
                    it.equals("No changes detected (files are identical)", ignoreCase = true)
            }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine(operation.toString())

        // If diffContent is available, embed it in a custom XML-like tag for the renderer.
        if (diffContent != null) {
            val encodedDiff = diffContent.replace("&", "&").replace("<", "<").replace(">", ">")
            sb.append("<file-diff path=\"${operation.path}\" details=\"${operation.details}\">")
            sb.append("<![CDATA[$encodedDiff]]>")
            sb.append("</file-diff>")
        }

        val requestContent = buildRequestContent()
        if (requestContent.isNotBlank()) {
            sb.append("<file-request-content><![CDATA[$requestContent]]></file-request-content>")
        }

        if (aiDiffInstructions.isNotEmpty() && !aiDiffInstructions.startsWith("Error")) {
            sb.appendLine("\n--- AI-Generated Diff ---")
            sb.appendLine(aiDiffInstructions)
        }
        return sb.toString()
    }
}

/** HTTP响应结果结构化数据 */
@Serializable
data class HttpResponseData(
        val url: String,
        val statusCode: Int,
        val statusMessage: String,
        val headers: Map<String, String>,
        val contentType: String,
        val content: String,
        val contentBase64: String? = null,
        val size: Int,
        val cookies: Map<String, String> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("HTTP Response:")
        sb.appendLine("URL: $url")
        sb.appendLine("Status: $statusCode $statusMessage")
        sb.appendLine("Content-Type: $contentType")
        sb.appendLine("Size: $size bytes")

        // 添加Cookie信息
        if (cookies.isNotEmpty()) {
            sb.appendLine("Cookies: ${cookies.size}")
            cookies.entries.take(5).forEach { (name, value) ->
                sb.appendLine("  $name: ${value.take(30)}${if (value.length > 30) "..." else ""}")
            }
            if (cookies.size > 5) {
                sb.appendLine("  ... and ${cookies.size - 5} more cookies")
            }
        }

        sb.appendLine()
        sb.appendLine("Content Summary:")
        sb.append(content)
        return sb.toString()
    }
}

@Serializable
data class HttpStreamEventData(
        val type: String,
        val url: String,
        val statusCode: Int? = null,
        val statusMessage: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val contentType: String? = null,
        val chunk: String? = null,
        val chunkIndex: Int? = null,
        val receivedBytes: Long? = null
) : ToolResultData() {
    override fun toString(): String {
        return when (type) {
            "chunk" -> chunk.orEmpty()
            "response_started" ->
                "HTTP stream started: ${statusCode ?: "?"} ${statusMessage.orEmpty()}".trim()
            else -> "HTTP stream event: $type"
        }
    }
}

/** 系统设置数据 */
@Serializable
data class SystemSettingData(val namespace: String, val setting: String, val value: String) :
        ToolResultData() {
    override fun toString(): String {
        return "Current value of $namespace.$setting: $value"
    }
}

/** 应用操作结果数据 */
@Serializable
data class AppOperationData(
        val operationType: String,
        val packageName: String,
        val success: Boolean,
        val details: String = ""
) : ToolResultData() {
    override fun toString(): String {
        return when (operationType) {
            "install" -> "Successfully installed app: $packageName $details"
            "uninstall" -> "Successfully uninstalled app: $packageName $details"
            "start" -> "Successfully started app: $packageName $details"
            "stop" -> "Successfully stopped app: $packageName $details"
            else -> details
        }
    }
}

/** 应用列表数据 */
@Serializable
data class AppListData(val includesSystemApps: Boolean, val packages: List<String>) :
        ToolResultData() {
    override fun toString(): String {
        val appType = if (includesSystemApps) "All Apps" else "Third-Party Apps"
        return "Installed ${appType} List:\n${packages.joinToString("\n")}"
    }
}

/** 单个应用的使用时长统计 */
@Serializable
data class AppUsageTimeEntry(
        val packageName: String,
        val appName: String,
        val totalForegroundTimeMs: Long,
        val lastTimeUsed: Long,
        val isSystemApp: Boolean
)

/** 应用使用时长数据 */
@Serializable
data class AppUsageTimeResultData(
        val startTime: Long,
        val endTime: Long,
        val sinceHours: Int,
        val requestedPackageName: String? = null,
        val includesSystemApps: Boolean,
        val totalEntries: Int,
        val entries: List<AppUsageTimeEntry>
) : ToolResultData() {
    override fun toString(): String {
        val header =
                buildString {
                    append("App usage time")
                    append(" (last ${sinceHours}h)")
                    requestedPackageName?.takeIf { it.isNotBlank() }?.let {
                        append(" for $it")
                    }
                }

        if (entries.isEmpty()) {
            return "$header\nNo app usage found in the selected time window."
        }

        val lines =
                entries.joinToString("\n") { entry ->
                    "- ${entry.appName} (${entry.packageName}): ${formatDuration(entry.totalForegroundTimeMs)}"
                }

        return "$header\n$lines"
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0s"
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")
        return parts.joinToString(" ")
    }
}

/** Bluetooth adapter state. */
@Serializable
data class BluetoothStateData(
        val supported: Boolean,
        val enabled: Boolean,
        val state: String
) : ToolResultData() {
    override fun toString(): String {
        if (!supported) {
            return "Bluetooth is not supported on this device"
        }
        return "Bluetooth state: $state"
    }
}

/** Bluetooth bonded device list. */
@Serializable
data class BluetoothBondedDevicesData(
        val devices: List<BluetoothDeviceData>
) : ToolResultData() {
    override fun toString(): String {
        if (devices.isEmpty()) {
            return "No bonded Bluetooth devices"
        }
        return "Bonded Bluetooth devices:\n" +
                devices.joinToString("\n") { device ->
                    "- ${device.name ?: "Unnamed"} (${device.address}) ${device.type}"
                }
    }
}

@Serializable
data class BluetoothDeviceData(
        val name: String?,
        val address: String,
        val type: String,
        val bondState: String
)

/** Bluetooth scan result. */
@Serializable
data class BluetoothScanResultData(
        val devices: List<BluetoothScannedDeviceData>,
        val durationMs: Long,
        val includesBle: Boolean
) : ToolResultData() {
    override fun toString(): String {
        if (devices.isEmpty()) {
            return "No Bluetooth devices found"
        }
        return "Bluetooth devices:\n" +
                devices.joinToString("\n") { device ->
                    "- ${device.name ?: "Unnamed"} (${device.address}) ${device.source}"
                }
    }
}

@Serializable
data class BluetoothScannedDeviceData(
        val name: String?,
        val address: String,
        val type: String,
        val bondState: String,
        val source: String,
        val rssi: Int? = null
)

/** Bluetooth connection/session result. */
@Serializable
data class BluetoothSessionData(
        val sessionId: String,
        val address: String,
        val mode: String
) : ToolResultData() {
    override fun toString(): String = "Bluetooth $mode session $sessionId connected to $address"
}

/** Bluetooth write result. */
@Serializable
data class BluetoothTransferData(
        val sessionId: String,
        val bytesWritten: Int
) : ToolResultData() {
    override fun toString(): String = "Wrote $bytesWritten bytes to Bluetooth session $sessionId"
}

/** Bluetooth read result. */
@Serializable
data class BluetoothReadData(
        val sessionId: String,
        val bytesRead: Int,
        val text: String? = null,
        val dataBase64: String? = null
) : ToolResultData() {
    override fun toString(): String {
        return text ?: "Read $bytesRead bytes from Bluetooth session $sessionId"
    }
}

@Serializable
data class BluetoothBleServicesData(
        val sessionId: String,
        val services: List<BluetoothBleServiceData>
) : ToolResultData() {
    override fun toString(): String {
        if (services.isEmpty()) {
            return "No BLE services discovered for session $sessionId"
        }
        return "BLE services for $sessionId:\n" +
                services.joinToString("\n") { service ->
                    "- ${service.uuid}: ${service.characteristics.joinToString(", ") { it.uuid }}"
                }
    }
}

@Serializable
data class BluetoothBleServiceData(
        val uuid: String,
        val characteristics: List<BluetoothBleCharacteristicData>
)

@Serializable
data class BluetoothBleCharacteristicData(
        val uuid: String,
        val properties: List<String>
)

@Serializable
data class BluetoothBleNotificationData(
        val sessionId: String,
        val notifications: List<BluetoothBleNotificationEntry>
) : ToolResultData() {
    override fun toString(): String {
        if (notifications.isEmpty()) {
            return "No BLE notifications for session $sessionId"
        }
        return notifications.joinToString("\n") { it.text ?: it.dataBase64.orEmpty() }
    }
}

@Serializable
data class BluetoothBleNotificationEntry(
        val characteristicUuid: String,
        val bytesRead: Int,
        val text: String? = null,
        val dataBase64: String? = null,
        val timestamp: Long = System.currentTimeMillis()
)

/** Represents UI node structure for hierarchical display */
@Serializable
data class SimplifiedUINode(
        val className: String?,
        val text: String?,
        val contentDesc: String?,
        val resourceId: String?,
        val bounds: String?,
        val isClickable: Boolean,
        val children: List<SimplifiedUINode>
) {
    fun toTreeString(indent: String = ""): String {
        if (!shouldKeepNode()) return ""

        val sb = StringBuilder()

        // Node identifier
        sb.append(indent)
        if (isClickable) sb.append("▶ ") else sb.append("◢ ")

        // Class name
        className?.let { sb.append("[$it] ") }

        // Text content (maximum 30 characters)
        text?.takeIf { it.isNotBlank() }?.let {
            val displayText = if (it.length > 30) "${it.take(27)}..." else it
            sb.append("T: \"$displayText\" ")
        }

        // Content description
        contentDesc?.takeIf { it.isNotBlank() }?.let { sb.append("D: \"$it\" ") }

        // Resource ID
        resourceId?.takeIf { it.isNotBlank() }?.let { sb.append("ID: $it ") }

        // Bounds
        bounds?.let { sb.append("⮞ $it") }

        sb.append("\n")

        // Process children recursively
        children.forEach { sb.append(it.toTreeString("$indent  ")) }

        return sb.toString()
    }

    private fun shouldKeepNode(): Boolean {
        // Keep conditions: key element types or has content or clickable or has children that
        // should be kept
        val isKeyElement =
                className in
                        setOf("Button", "TextView", "EditText", "ScrollView", "Switch", "ImageView")
        val hasContent = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        return isKeyElement || hasContent || isClickable || children.any { it.shouldKeepNode() }
    }
}

/** Represents UI page information result data */
@Serializable
data class UIPageResultData(
        val packageName: String,
        val activityName: String,
        val uiElements: SimplifiedUINode
) : ToolResultData() {
    override fun toString(): String {
        return """
            |Current Application: $packageName
            |Current Activity: $activityName
            |
            |UI Elements:
            |${uiElements.toTreeString()}
            """.trimMargin()
    }
}

/** Represents a UI action result data */
@Serializable
data class UIActionResultData(
        val actionType: String,
        val actionDescription: String,
        val coordinates: Pair<Int, Int>? = null,
        val elementId: String? = null
) : ToolResultData() {
    override fun toString(): String {
        return actionDescription
    }
}

/** Represents a combined operation result data */
@Serializable
data class CombinedOperationResultData(
        val operationSummary: String,
        val waitTime: Int,
        val pageInfo: UIPageResultData
) : ToolResultData() {
    override fun toString(): String {
        return "$operationSummary (waited ${waitTime}ms)\n\n$pageInfo"
    }
}

/** Device information result data */
@Serializable
data class DeviceInfoResultData(
        val deviceId: String,
        val model: String,
        val manufacturer: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val screenResolution: String,
        val screenDensity: Float,
        val totalMemory: String,
        val availableMemory: String,
        val totalStorage: String,
        val availableStorage: String,
        val batteryLevel: Int,
        val batteryCharging: Boolean,
        val cpuInfo: String,
        val networkType: String,
        val additionalInfo: Map<String, String> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Device Information:")
        sb.appendLine("Device Model: $manufacturer $model")
        sb.appendLine("Android Version: $androidVersion (SDK $sdkVersion)")
        sb.appendLine("Device ID: $deviceId")
        sb.appendLine("Screen: $screenResolution (${screenDensity}dp)")
        sb.appendLine("Memory: Available $availableMemory / Total $totalMemory")
        sb.appendLine("Storage: Available $availableStorage / Total $totalStorage")
        sb.appendLine("Battery: ${batteryLevel}% ${if (batteryCharging) "(Charging)" else ""}")
        sb.appendLine("Network: $networkType")
        sb.appendLine("Processor: $cpuInfo")

        if (additionalInfo.isNotEmpty()) {
            sb.appendLine("\nOther Information:")
            additionalInfo.forEach { (key, value) -> sb.appendLine("$key: $value") }
        }

        return sb.toString()
    }
}

/** Web page visit result data */
@Serializable
data class VisitWebResultData(
        val url: String,
        val title: String,
        val content: String,
        val metadata: Map<String, String> = emptyMap(),
        val links: List<LinkData> = emptyList(),
        val imageLinks: List<String> = emptyList(),
        val visitKey: String? = null,
        val contentSavedTo: String? = null,
        val contentTruncated: Boolean = false,
        val originalContentLength: Int? = null
) : ToolResultData() {
    companion object {
        private const val MAX_INLINE_LINKS = 120
        private const val MAX_INLINE_IMAGES = 120
    }

    @Serializable
    data class LinkData(val url: String, val text: String)

    override fun toString(): String {
        val sb = StringBuilder()
        visitKey?.let { sb.appendLine("Visit key: $it\n") }

        if (links.isNotEmpty()) {
            sb.appendLine("Results:")
            links.take(MAX_INLINE_LINKS).forEachIndexed { index, link ->
                sb.appendLine("[${index + 1}] ${link.text}")
            }
            val omittedCount = links.size - MAX_INLINE_LINKS
            if (omittedCount > 0) {
                sb.appendLine("... ($omittedCount more links omitted from inline preview)")
            }
            sb.appendLine()
        }

        if (imageLinks.isNotEmpty()) {
            sb.appendLine("Images:")
            imageLinks.take(MAX_INLINE_IMAGES).forEachIndexed { index, link ->
                val name = link.substringAfterLast('/').substringBefore('?').ifBlank { "image" }
                sb.appendLine("[${index + 1}] $name")
            }
            val omittedCount = imageLinks.size - MAX_INLINE_IMAGES
            if (omittedCount > 0) {
                sb.appendLine("... ($omittedCount more images omitted from inline preview)")
            }
            sb.appendLine()
        }

        contentSavedTo?.let {
            sb.appendLine("Full content saved to file: $it")
            originalContentLength?.let { totalChars ->
                sb.appendLine("Original content length: $totalChars chars")
            }
            if (contentTruncated) {
                sb.appendLine("Use read_file_part or grep_code to inspect the saved file.")
            }
            sb.appendLine()
        }

        sb.appendLine(if (contentTruncated) "Content Preview:" else "Content:")
        sb.append(content)

        return sb.toString()
    }
}

/** Intent execution result data */
@Serializable
data class IntentResultData(
        val action: String,
        val uri: String,
        val package_name: String,
        val component: String,
        val flags: Int,
        val extras_count: Int,
        val result: String
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Intent Execution Result:")
        sb.appendLine("Action: $action")
        if (uri != "null") sb.appendLine("URI: $uri")
        if (package_name != "null") sb.appendLine("Package: $package_name")
        if (component != "null") sb.appendLine("Component: $component")
        sb.appendLine("Flags: $flags")
        sb.appendLine("Extras Count: $extras_count")
        sb.appendLine("\nExecution Result: $result")
        return sb.toString()
    }
}

/** 文件查找结果数据 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FindFilesResultData(
        val path: String,
        val pattern: String,
        val files: List<String>,
        @EncodeDefault
        val env: String = "android"
) :
        ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("[$env] File Search Result:")
        sb.appendLine("Search Path: $path")
        sb.appendLine("Pattern: $pattern")

        sb.appendLine("Found ${files.size} files:")
        files.forEachIndexed { index, file ->
            if (index < 10 || files.size <= 20) {
                sb.appendLine("- $file")
            } else if (index == 10 && files.size > 20) {
                sb.appendLine("... and ${files.size - 10} other files")
            }
        }

        return sb.toString()
    }
}

/** FFmpeg处理结果数据 */
@Serializable
data class FFmpegResultData(
        val command: String,
        val returnCode: Int,
        val output: String,
        val duration: Long,
        val outputFile: String? = null,
        val mediaInfo: MediaInfo? = null
) : ToolResultData() {
    @Serializable
    data class MediaInfo(
            val format: String,
            val duration: String,
            val bitrate: String,
            val videoStreams: List<StreamInfo>,
            val audioStreams: List<StreamInfo>
    )

    @Serializable
    data class StreamInfo(
            val index: Int,
            val codecType: String,
            val codecName: String,
            val resolution: String? = null,
            val frameRate: String? = null,
            val sampleRate: String? = null,
            val channels: Int? = null
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("FFmpeg Execution Result:")
        sb.appendLine("Command: $command")
        sb.appendLine("Return Code: $returnCode")
        sb.appendLine("Execution Time: ${duration}ms")

        outputFile?.let { sb.appendLine("Output File: $it") }

        mediaInfo?.let { info ->
            sb.appendLine("\nMedia Information:")
            sb.appendLine("Format: ${info.format}")
            sb.appendLine("Duration: ${info.duration}")
            sb.appendLine("Bitrate: ${info.bitrate}")

            if (info.videoStreams.isNotEmpty()) {
                sb.appendLine("\nVideo Streams:")
                info.videoStreams.forEach { stream ->
                    sb.appendLine("  Index: ${stream.index}")
                    sb.appendLine("  Codec: ${stream.codecName}")
                    stream.resolution?.let { sb.appendLine("  Resolution: $it") }
                    stream.frameRate?.let { sb.appendLine("  Frame Rate: $it") }
                    sb.appendLine()
                }
            }

            if (info.audioStreams.isNotEmpty()) {
                sb.appendLine("\nAudio Streams:")
                info.audioStreams.forEach { stream ->
                    sb.appendLine("  Index: ${stream.index}")
                    sb.appendLine("  Codec: ${stream.codecName}")
                    stream.sampleRate?.let { sb.appendLine("  Sample Rate: $it") }
                    stream.channels?.let { sb.appendLine("  Channels: $it") }
                    sb.appendLine()
                }
            }
        }

        sb.appendLine("\nOutput Log:")
        sb.append(output)

        return sb.toString()
    }
}

/** 通知数据结构 */
@Serializable
data class NotificationData(val notifications: List<Notification>, val timestamp: Long) :
        ToolResultData() {
    @Serializable
    data class Notification(val packageName: String, val text: String, val timestamp: Long)

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Device Notifications (${notifications.size} total):")

        notifications.forEachIndexed { index, notification ->
            sb.appendLine("${index + 1}. Package: ${notification.packageName}")
            sb.appendLine("   Content: ${notification.text}")
            sb.appendLine()
        }

        if (notifications.isEmpty()) {
            sb.appendLine("No notifications")
        }

        return sb.toString()
    }
}

/** 位置数据结构 */
@Serializable
data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val provider: String,
        val timestamp: Long,
        val rawData: String,
        val address: String = "",
        val city: String = "",
        val province: String = "",
        val country: String = ""
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Device Location Information:")
        sb.appendLine("Longitude: $longitude")
        sb.appendLine("Latitude: $latitude")
        sb.appendLine("Accuracy: $accuracy meters")
        sb.appendLine("Provider: $provider")
        sb.appendLine(
                "Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))}"
        )

        if (address.isNotEmpty()) {
            sb.appendLine("Address: $address")
        }
        if (city.isNotEmpty()) {
            sb.appendLine("City: $city")
        }
        if (province.isNotEmpty()) {
            sb.appendLine("Province/State: $province")
        }
        if (country.isNotEmpty()) {
            sb.appendLine("Country: $country")
        }

        return sb.toString()
    }
}

/** Represents a simplified HTML node for computer desktop actions, focusing on interactability */
@Serializable
data class ComputerPageInfoNode(
    val interactionId: Int?,
    val type: String, // e.g., "container", "button", "link", "text", "input"
    val description: String,
    val children: List<ComputerPageInfoNode>
) {
    fun toTreeString(level: Int = 0): String {
        val indent = "  ".repeat(level)
        val idPrefix = interactionId?.let { "($it) " } ?: ""
        val typePrefix = if (type != "container" && type != "text") "▶ $type: " else ""
        val selfStr = "$indent$idPrefix$typePrefix'${description.trim()}'"

        val childrenStr = if (children.isNotEmpty()) {
            "\n" + children.joinToString("\n") { it.toTreeString(level + 1) }
        } else {
            ""
        }
        return selfStr + childrenStr
    }
}

/** Represents the result of a computer desktop action */
@Serializable
data class ComputerDesktopActionResultData(
    val action: String,
    val target: String? = null,
    val resultSummary: String,
    val tabs: List<ComputerTabInfo>? = null,
    val pageContent: ComputerPageInfoNode? = null
) : ToolResultData() {
    @Serializable
    data class ComputerTabInfo(
        val id: String,
        val title: String,
        val url: String,
        val isActive: Boolean
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Computer Desktop Action: '$action'")
        target?.let { sb.appendLine("Target: $it") }
        sb.appendLine("Result: $resultSummary")
        tabs?.let {
            sb.appendLine("\nOpen Tabs (${it.size}):")
            it.forEach { tab ->
                sb.appendLine("- [${if (tab.isActive) "*" else " "}] ${tab.title} (${tab.url})")
            }
        }
        pageContent?.let {
            sb.appendLine("\n--- Page Content (Interactable Elements marked with ▶) ---")
            sb.append(it.toTreeString())
        }
        return sb.toString()
    }
}

/** Represents the result of a memory query */
@Serializable
data class MemoryQueryResultData(
    val memories: List<MemoryInfo>,
    val snapshotId: String? = null,
    val snapshotCreated: Boolean = false,
    val excludedBySnapshotCount: Int = 0
) : ToolResultData() {

    @Serializable
    data class MemoryInfo(
        val title: String,
        val content: String,
        val source: String,
        val tags: List<String>,
        val createdAt: String,
        val chunkInfo: String? = null,
        val chunkIndices: List<Int>? = null
    )

    override fun toString(): String {
        val snapshotSummary = buildList {
            snapshotId?.takeIf { it.isNotBlank() }?.let {
                add("Snapshot ID: $it")
            }
            if (snapshotCreated) {
                add("Snapshot created: true")
            }
            if (excludedBySnapshotCount > 0) {
                add("Excluded by snapshot: $excludedBySnapshotCount")
            }
        }.joinToString("\n")

        if (memories.isEmpty()) {
            return if (snapshotSummary.isBlank()) {
                "No relevant memories found."
            } else {
                "$snapshotSummary\nNo relevant memories found."
            }
        }
        val memoryText = memories.joinToString("\n---\n") { memory ->
            """
            Title: ${memory.title}
            Content: ${memory.content}
            Source: ${memory.source}
            Tags: ${memory.tags.joinToString(", ")}
            Created: ${memory.createdAt}
            """.trimIndent()
        }
        return if (snapshotSummary.isBlank()) {
            memoryText
        } else {
            "$snapshotSummary\n---\n$memoryText"
        }
    }
}

/** 自动化配置搜索结果数据 */
@Serializable
data class AutomationConfigSearchResult(
    val searchPackageName: String?,
    val searchAppName: String?,
    val foundConfigs: List<ConfigInfo>,
    val totalFound: Int
) : ToolResultData() {
    
    @Serializable
    data class ConfigInfo(
        val appName: String,
        val packageName: String,
        val description: String,
        val isBuiltIn: Boolean,
        val fileName: String,
        val matchType: String  // "packageName" or "appName"
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Automation Configuration Search Result:")

        if (!searchPackageName.isNullOrBlank()) {
            sb.appendLine("Search Package Name: $searchPackageName")
        }
        if (!searchAppName.isNullOrBlank()) {
            sb.appendLine("Search App Name: $searchAppName")
        }

        sb.appendLine("Found $totalFound matching configurations:")

        if (foundConfigs.isEmpty()) {
            sb.appendLine("No matching automation configurations found")
        } else {
            foundConfigs.forEach { config ->
                sb.appendLine()
                sb.appendLine("App Name: ${config.appName}")
                sb.appendLine("Package Name: ${config.packageName}")
                sb.appendLine("Description: ${config.description}")
                sb.appendLine("Type: ${if (config.isBuiltIn) "Built-in" else "User Imported"}")
                sb.appendLine("Match Type: ${if (config.matchType == "packageName") "Package Name" else "App Name"}")
            }
        }

        return sb.toString()
    }
}

/** 自动化计划参数结果数据 */
@Serializable
data class AutomationPlanParametersResult(
    val functionName: String,
    val targetPackageName: String?,
    val requiredParameters: List<ParameterInfo>,
    val planSteps: Int,
    val planDescription: String
) : ToolResultData() {
    
    @Serializable
    data class ParameterInfo(
        val key: String,
        val description: String,
        val type: String,
        val isRequired: Boolean,
        val defaultValue: String?
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Automation Plan Parameters Information:")
        sb.appendLine("Function Name: $functionName")
        targetPackageName?.let { sb.appendLine("Target App: $it") }
        sb.appendLine("Plan Description: $planDescription")
        sb.appendLine()

        if (requiredParameters.isEmpty()) {
            sb.appendLine("This function does not require additional parameters and can be executed directly.")
        } else {
            sb.appendLine("Required Parameters (${requiredParameters.size} total):")
            requiredParameters.forEach { param ->
                sb.appendLine()
                sb.appendLine("Parameter Name: ${param.key}")
                sb.appendLine("Description: ${param.description}")
                sb.appendLine("Type: ${param.type}")
                sb.appendLine("Required: ${if (param.isRequired) "Yes" else "No"}")
                param.defaultValue?.let { sb.appendLine("Default Value: $it") }
            }
        }

        return sb.toString()
    }
}

/** 自动化执行结果数据 */
@Serializable
data class AutomationExecutionResult(
    val functionName: String,
    val providedParameters: Map<String, String>,
    val agentId: String? = null,
    val displayId: Int? = null,
    val executionSuccess: Boolean,
    val executionMessage: String,
    val executionError: String?,
    val finalState: UIStateInfo?,
    val executionSteps: Int
) : ToolResultData() {
    
    @Serializable
    data class UIStateInfo(
        val nodeId: String,
        val packageName: String,
        val activityName: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Automation Execution Result:")
        sb.appendLine("Function Name: $functionName")
        agentId?.let { sb.appendLine("AgentId: $it") }
        sb.appendLine("Execution Status: ${if (executionSuccess) "Success" else "Failure"}")
        sb.appendLine("Execution Steps: $executionSteps")
        sb.appendLine("Result Message: $executionMessage")

        if (!executionError.isNullOrBlank()) {
            sb.appendLine("Error Message: $executionError")
        }

        if (providedParameters.isNotEmpty()) {
            sb.appendLine("\nUsed Parameters:")
            providedParameters.forEach { (key, value) ->
                sb.appendLine("  $key: $value")
            }
        }

        finalState?.let { state ->
            sb.appendLine("\nFinal State:")
            sb.appendLine("  Node ID: ${state.nodeId}")
            sb.appendLine("  Package Name: ${state.packageName}")
            sb.appendLine("  Activity: ${state.activityName}")
        }

        return sb.toString()
    }
}

/** 自动化功能列表结果数据 */
@Serializable
data class AutomationFunctionListResult(
    val packageName: String?,
    val functions: List<FunctionInfo>,
    val totalCount: Int
) : ToolResultData() {
    
    @Serializable
    data class FunctionInfo(
        val name: String,
        val description: String,
        val targetNodeName: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Available Automation Functions:")
        packageName?.let { sb.appendLine("Package Name: $it") }
        sb.appendLine("Function Count: $totalCount")
        sb.appendLine()

        if (functions.isEmpty()) {
            sb.appendLine("No automation functions available")
        } else {
            functions.forEach { func ->
                sb.appendLine("Function Name: ${func.name}")
                sb.appendLine("Description: ${func.description}")
                sb.appendLine("Target Page: ${func.targetNodeName}")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}

/** 终端会话创建结果数据 */
@Serializable
data class TerminalSessionCreationResultData(
    val sessionId: String,
    val sessionName: String,
    val isNewSession: Boolean
) : ToolResultData() {
    override fun toString(): String {
        return if (isNewSession) {
            "Successfully created new terminal session. Session Name: '$sessionName', Session ID: $sessionId"
        } else {
            "Successfully retrieved existing terminal session. Session Name: '$sessionName', Session ID: $sessionId"
        }
    }
}

/** 终端会话关闭结果数据 */
@Serializable
data class TerminalSessionCloseResultData(
    val sessionId: String,
    val success: Boolean,
    val message: String
) : ToolResultData() {
    override fun toString(): String = message
}

/** 终端会话当前屏幕内容结果数据（仅当前屏，不含历史滚动缓冲） */
@Serializable
data class TerminalSessionScreenResultData(
    val sessionId: String,
    val rows: Int,
    val cols: Int,
    val content: String
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Terminal Session Screen Snapshot:")
        sb.appendLine("Session: $sessionId")
        sb.appendLine("Size: ${cols}x${rows}")
        sb.appendLine()
        sb.append(content)
        return sb.toString()
    }
}

/** Grep代码搜索结果数据 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class GrepResultData(
    val searchPath: String,
    val pattern: String,
    val matches: List<FileMatch>,
    val totalMatches: Int,
    val filesSearched: Int,
    @EncodeDefault
    val env: String = "android"
) : ToolResultData() {
    
    @Serializable
    data class FileMatch(
        val filePath: String,
        val lineMatches: List<LineMatch>
    )
    
    @Serializable
    data class LineMatch(
        val lineNumber: Int,
        val lineContent: String,
        val matchContext: String? = null
    )

    private fun parsePreNumberedLineNumber(line: String): Int? {
        val trimmed = line.trimStart()
        val separatorIndex = trimmed.indexOf('|')
        if (separatorIndex <= 0) return null
        return trimmed.substring(0, separatorIndex).trim().toIntOrNull()
    }

    private fun markPreNumberedContextLine(line: String): String {
        val separatorIndex = line.indexOf('|')
        if (separatorIndex < 0) return line
        if (separatorIndex + 1 < line.length && line[separatorIndex + 1] == '>') return line
        return buildString(line.length + 1) {
            append(line, 0, separatorIndex + 1)
            append('>')
            append(line.substring(separatorIndex + 1))
        }
    }
    
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("[$env] Grep Search Result:")
        sb.appendLine("Search Path: $searchPath")
        sb.appendLine("Pattern: $pattern")
        sb.appendLine("Total Matches: $totalMatches (in ${matches.size} files)")
        sb.appendLine("Files Searched: $filesSearched")
        sb.appendLine()

        if (matches.isEmpty()) {
            sb.appendLine("No matches found")
        } else {
            // Set display limit - show up to 30 match groups
            val maxDisplayMatches = 30
            var displayedMatches = 0
            var collapsedMatches = 0

            for (fileMatch in matches) {
                val remainingSlots = maxDisplayMatches - displayedMatches
                if (remainingSlots <= 0) {
                    // Count remaining collapsed matches
                    collapsedMatches += fileMatch.lineMatches.size
                    continue
                }

                sb.appendLine("File: ${fileMatch.filePath}")

                val matchesToShow = fileMatch.lineMatches.take(remainingSlots)
                val matchesCollapsedInFile = fileMatch.lineMatches.size - matchesToShow.size

                matchesToShow.forEach { lineMatch ->
                    // If context is available, show full context
                    if (lineMatch.matchContext != null && lineMatch.matchContext.isNotBlank()) {
                        val contextLines = lineMatch.matchContext.lines()
                        val isPreNumberedContext =
                            contextLines.any { it.isNotBlank() } &&
                                contextLines.all { it.isBlank() || parsePreNumberedLineNumber(it) != null }

                        if (isPreNumberedContext) {
                            contextLines.forEach { contextLine ->
                                val renderedLine =
                                    if (parsePreNumberedLineNumber(contextLine) == lineMatch.lineNumber) {
                                        markPreNumberedContextLine(contextLine)
                                    } else {
                                        contextLine
                                    }
                                sb.appendLine(renderedLine)
                            }
                        } else {
                            val centerIndex = contextLines.size / 2

                            contextLines.forEachIndexed { idx, contextLine ->
                                val actualLineNum = lineMatch.lineNumber - centerIndex + idx
                                val lineNumStr = String.format("%6d", actualLineNum)

                                if (idx == centerIndex) {
                                    sb.appendLine("$lineNumStr|>${contextLine}")
                                } else {
                                    sb.appendLine("$lineNumStr| ${contextLine}")
                                }
                            }
                        }
                        sb.appendLine() // Add blank line after each match block
                    } else {
                        // No context, show only matching line
                        val lineNumStr = String.format("%6d", lineMatch.lineNumber)
                        sb.appendLine("$lineNumStr| ${lineMatch.lineContent}")
                    }
                    displayedMatches++
                }

                if (matchesCollapsedInFile > 0) {
                    sb.appendLine("  ... ($matchesCollapsedInFile more match groups collapsed in this file)")
                    collapsedMatches += matchesCollapsedInFile
                }

                sb.appendLine()
            }

            if (collapsedMatches > 0) {
                sb.appendLine("=" .repeat(60))
                sb.appendLine("To save space, $collapsedMatches match groups were collapsed")
                sb.appendLine("Displayed $displayedMatches match groups, total $totalMatches matches")
            }
        }

        return sb.toString()
    }
}

/** 工作流基本信息结果数据 */
@Serializable
data class WorkflowResultData(
    val id: String,
    val name: String,
    val description: String,
    val nodeCount: Int,
    val connectionCount: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExecutionTime: Long? = null,
    val lastExecutionStatus: String? = null,
    val totalExecutions: Int = 0,
    val successfulExecutions: Int = 0,
    val failedExecutions: Int = 0
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("ID: $id")
        sb.appendLine("Name: $name")
        sb.appendLine("Description: $description")
        sb.appendLine("Status: ${if (enabled) "Enabled" else "Disabled"}")
        sb.appendLine("Node Count: $nodeCount")
        sb.appendLine("Connection Count: $connectionCount")
        sb.appendLine("Total Executions: $totalExecutions")
        sb.appendLine("Successful Executions: $successfulExecutions")
        sb.appendLine("Failed Executions: $failedExecutions")
        if (lastExecutionTime != null) {
            sb.appendLine("Last Execution Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(lastExecutionTime))}")
            sb.appendLine("Last Execution Status: ${lastExecutionStatus ?: "Unknown"}")
        }
        return sb.toString().trim()
    }
}

/** 工作流列表结果数据 */
@Serializable
data class WorkflowListResultData(
    val workflows: List<WorkflowResultData>,
    val totalCount: Int
) : ToolResultData() {
    override fun toString(): String {
        if (workflows.isEmpty()) {
            return "No workflows"
        }
        val sb = StringBuilder()
        sb.appendLine("Workflow List ($totalCount total):")
        sb.appendLine()
        workflows.forEach { workflow ->
            sb.appendLine("ID: ${workflow.id}")
            sb.appendLine("Name: ${workflow.name}")
            sb.appendLine("Description: ${workflow.description}")
            sb.appendLine("Status: ${if (workflow.enabled) "Enabled" else "Disabled"}")
            sb.appendLine("Node Count: ${workflow.nodeCount}")
            sb.appendLine("Connection Count: ${workflow.connectionCount}")
            sb.appendLine("Total Executions: ${workflow.totalExecutions}")
            sb.appendLine("---")
        }
        return sb.toString().trim()
    }
    
    companion object {
        /**
         * 创建一个空的WorkflowListResultData，用于错误情况
         */
        fun empty() = WorkflowListResultData(
            workflows = emptyList(),
            totalCount = 0
        )
    }
}

/** 工作流详细信息结果数据（包含完整的节点和连接信息） */
@Serializable
data class WorkflowDetailResultData(
    val id: String,
    val name: String,
    val description: String,
    val nodes: List<com.ai.assistance.operit.data.model.WorkflowNode>,
    val connections: List<com.ai.assistance.operit.data.model.WorkflowNodeConnection>,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExecutionTime: Long? = null,
    val lastExecutionStatus: String? = null,
    val totalExecutions: Int = 0,
    val successfulExecutions: Int = 0,
    val failedExecutions: Int = 0
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Workflow Details:")
        sb.appendLine("ID: $id")
        sb.appendLine("Name: $name")
        sb.appendLine("Description: $description")
        sb.appendLine("Status: ${if (enabled) "Enabled" else "Disabled"}")
        sb.appendLine()

        sb.appendLine("Nodes (${nodes.size}):")
        nodes.forEach { node ->
            when (node) {
                is com.ai.assistance.operit.data.model.TriggerNode -> {
                    sb.appendLine("  - [Trigger] ${node.name} (${node.id})")
                    sb.appendLine("    Type: ${node.triggerType}")
                    if (node.description.isNotBlank()) {
                        sb.appendLine("    Description: ${node.description}")
                    }
                }
                is com.ai.assistance.operit.data.model.ExecuteNode -> {
                    sb.appendLine("  - [Execute] ${node.name} (${node.id})")
                    sb.appendLine("    Action: ${node.actionType}")
                    if (node.description.isNotBlank()) {
                        sb.appendLine("    Description: ${node.description}")
                    }
                }
                is com.ai.assistance.operit.data.model.ConditionNode -> {
                    sb.appendLine("  - [Condition] ${node.name} (${node.id})")
                    sb.appendLine("    Operator: ${node.operator}")
                    if (node.description.isNotBlank()) {
                        sb.appendLine("    Description: ${node.description}")
                    }
                }
                is com.ai.assistance.operit.data.model.LogicNode -> {
                    sb.appendLine("  - [Logic] ${node.name} (${node.id})")
                    sb.appendLine("    Operator: ${node.operator}")
                    if (node.description.isNotBlank()) {
                        sb.appendLine("    Description: ${node.description}")
                    }
                }
                is com.ai.assistance.operit.data.model.ExtractNode -> {
                    sb.appendLine("  - [Extract] ${node.name} (${node.id})")
                    sb.appendLine("    Mode: ${node.mode}")
                    if (node.expression.isNotBlank()) {
                        sb.appendLine("    Expression: ${node.expression}")
                    }
                    if (node.description.isNotBlank()) {
                        sb.appendLine("    Description: ${node.description}")
                    }
                }
            }
        }
        sb.appendLine()

        sb.appendLine("Connections (${connections.size}):")
        connections.forEach { conn ->
            val sourceName = nodes.find { it.id == conn.sourceNodeId }?.name ?: conn.sourceNodeId
            val targetName = nodes.find { it.id == conn.targetNodeId }?.name ?: conn.targetNodeId
            sb.append("  - $sourceName → $targetName")
            if (conn.condition != null) {
                sb.append(" (Condition: ${conn.condition})")
            }
            sb.appendLine()
        }
        sb.appendLine()

        sb.appendLine("Execution Statistics:")
        sb.appendLine("  Total Executions: $totalExecutions")
        sb.appendLine("  Successful Executions: $successfulExecutions")
        sb.appendLine("  Failed Executions: $failedExecutions")
        if (lastExecutionTime != null) {
            sb.appendLine("  Last Execution Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(lastExecutionTime))}")
            sb.appendLine("  Last Execution Status: ${lastExecutionStatus ?: "Unknown"}")
        }

        return sb.toString().trim()
    }
    
    companion object {
        /**
         * 创建一个空的WorkflowDetailResultData，用于错误情况
         */
        fun empty() = WorkflowDetailResultData(
            id = "",
            name = "",
            description = "",
            nodes = emptyList(),
            connections = emptyList(),
            enabled = false,
            createdAt = 0L,
            updatedAt = 0L,
            lastExecutionTime = null,
            lastExecutionStatus = null,
            totalExecutions = 0,
            successfulExecutions = 0,
            failedExecutions = 0
        )
    }
}

/** 对话服务启动结果数据 */
@Serializable
data class ChatServiceStartResultData(
    val isConnected: Boolean,
    val connectionTime: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return if (isConnected) {
            "Chat service started and connected successfully"
        } else {
            "Chat service connection failed"
        }
    }
}

/** 新建对话结果数据 */
@Serializable
data class ChatCreationResultData(
    val chatId: String,
    val createdAt: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return "Created new chat\nChat ID: $chatId"
    }
}

/** 对话列表结果数据 */
@Serializable
data class ChatListResultData(
    val totalCount: Int,
    val currentChatId: String?,
    val chats: List<ChatInfo>
) : ToolResultData() {
    
    @Serializable
    data class ChatInfo(
        val id: String,
        val title: String,
        val messageCount: Int,
        val createdAt: String,
        val updatedAt: String,
        val isCurrent: Boolean,
        val inputTokens: Long,
        val outputTokens: Long,
        val characterCardName: String? = null
    )
    
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Chat List ($totalCount total):")
        if (currentChatId != null) {
            sb.appendLine("Current Chat ID: $currentChatId")
        }
        sb.appendLine()

        if (chats.isEmpty()) {
            sb.appendLine("No chats")
        } else {
            chats.forEach { chat ->
                val currentMarker = if (chat.isCurrent) " [Current]" else ""
                sb.appendLine("ID: ${chat.id}$currentMarker")
                sb.appendLine("Title: ${chat.title}")
                sb.appendLine("Message Count: ${chat.messageCount}")
                if (!chat.characterCardName.isNullOrBlank()) {
                    sb.appendLine("Character Card: ${chat.characterCardName}")
                }
                sb.appendLine("Token Statistics: Input ${chat.inputTokens} / Output ${chat.outputTokens}")
                sb.appendLine("Created: ${chat.createdAt}")
                sb.appendLine("Updated: ${chat.updatedAt}")
                sb.appendLine("---")
            }
        }

        return sb.toString().trim()
    }
}

/** 查找对话结果数据 */
@Serializable
data class ChatFindResultData(
    val matchedCount: Int,
    val chat: ChatListResultData.ChatInfo?
) : ToolResultData() {
    override fun toString(): String {
        return if (chat != null) {
            "Found chat (${chat.id}) (matched=$matchedCount)"
        } else {
            "No chat found (matched=$matchedCount)"
        }
    }
}

/** 对话输入状态结果数据 */
@Serializable
data class AgentStatusResultData(
    val chatId: String,
    val state: String,
    val message: String? = null,
    val isIdle: Boolean = false,
    val isProcessing: Boolean = false
) : ToolResultData() {
    override fun toString(): String {
        val detail = message?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        return "Chat $chatId status: $state$detail"
    }
}

/** 切换对话结果数据 */
@Serializable
data class ChatSwitchResultData(
    val chatId: String,
    val chatTitle: String = "",
    val switchedAt: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return if (chatTitle.isNotBlank()) {
            "Switched to chat: $chatTitle\nChat ID: $chatId"
        } else {
            "Switched to chat: $chatId"
        }
    }
}

/** 更新对话标题结果数据 */
@Serializable
data class ChatTitleUpdateResultData(
    val chatId: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return "Updated chat title: $chatId -> $title"
    }
}

/** 删除对话结果数据 */
@Serializable
data class ChatDeleteResultData(
    val chatId: String,
    val deletedAt: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return "Deleted chat: $chatId"
    }
}

@Serializable
data class ChatMessagesResultData(
    val chatId: String,
    val order: String,
    val limit: Int,
    val messages: List<ChatMessageInfo>,
    val start: Int? = null,
    val end: Int? = null,
) : ToolResultData() {

    @Serializable
    data class ChatMessageInfo(
        val sender: String,
        val content: String,
        val timestamp: Long,
        val roleName: String = "",
        val provider: String = "",
        val modelName: String = ""
    )

    override fun toString(): String {
        val rangeInfo = if (start != null && end != null) ", range=$start-$end" else ""
        return "Chat messages: $chatId (order=$order, limit=$limit$rangeInfo)\nTotal: ${messages.size}"
    }
}

/** 角色卡列表结果数据 */
@Serializable
data class CharacterCardListResultData(
    val totalCount: Int,
    val cards: List<CharacterCardInfo>
) : ToolResultData() {

    @Serializable
    data class CharacterCardInfo(
        val id: String,
        val name: String,
        val description: String,
        val isDefault: Boolean,
        val createdAt: Long,
        val updatedAt: Long
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Character Cards ($totalCount total):")
        if (cards.isEmpty()) {
            sb.appendLine("No cards")
        } else {
            cards.forEach { card ->
                val defaultMarker = if (card.isDefault) " [Default]" else ""
                sb.appendLine("ID: ${card.id}$defaultMarker")
                sb.appendLine("Name: ${card.name}")
                if (card.description.isNotBlank()) {
                    sb.appendLine("Description: ${card.description}")
                }
                sb.appendLine("Created: ${card.createdAt}")
                sb.appendLine("Updated: ${card.updatedAt}")
                sb.appendLine("---")
            }
        }
        return sb.toString().trim()
    }
}

/** 发送消息结果数据 */
@Serializable
data class MessageSendResultData(
    val chatId: String,
    val message: String,
    val aiResponse: String? = null,
    val receivedAt: Long? = null,
    val sentAt: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        val messagePreview = if (message.length > 50) {
            "${message.take(50)}..."
        } else {
            message
        }
        val response = aiResponse
        return if (response.isNullOrBlank()) {
            "Message sent to chat: $chatId\nMessage content: $messagePreview"
        } else {
            val responsePreview = if (response.length > 200) {
                "${response.take(200)}..."
            } else {
                response
            }
            "Message sent to chat: $chatId\nMessage content: $messagePreview\nAI Reply: $responsePreview"
        }
    }
}

/** 发送消息流式事件数据 */
@Serializable
data class MessageSendStreamEventData(
    val type: String,
    val chatId: String,
    val message: String,
    val waifu: Boolean = false,
    val chunk: String? = null,
    val chunkIndex: Int? = null,
    val receivedChars: Int? = null
) : ToolResultData() {
    override fun toString(): String {
        return when (type) {
            "chunk" -> chunk.orEmpty()
            "start" -> "Message stream started"
            else -> "Message stream event: $type"
        }
    }
}

/** 记忆链接结果数据 */
@Serializable
data class MemoryLinkResultData(
    val sourceTitle: String,
    val targetTitle: String,
    val linkType: String,
    val weight: Float,
    val description: String
) : ToolResultData() {
    override fun toString(): String {
        return "Successfully linked memory: '$sourceTitle' -> '$targetTitle' (Type: $linkType, Strength: $weight)"
    }
}

/** 记忆链接查询结果数据 */
@Serializable
data class MemoryLinkQueryResultData(
    val totalCount: Int,
    val links: List<LinkInfo>
) : ToolResultData() {
    @Serializable
    data class LinkInfo(
        val linkId: Long,
        val sourceTitle: String,
        val targetTitle: String,
        val linkType: String,
        val weight: Float,
        val description: String
    )

    override fun toString(): String {
        if (links.isEmpty()) {
            return "No memory links found."
        }
        val sb = StringBuilder()
        sb.appendLine("Memory Links ($totalCount):")
        links.forEach { link ->
            sb.appendLine("- #${link.linkId}: '${link.sourceTitle}' -> '${link.targetTitle}' (Type: ${link.linkType}, Weight: ${link.weight})")
            if (link.description.isNotBlank()) {
                sb.appendLine("  Description: ${link.description}")
            }
        }
        return sb.toString().trim()
    }
}

/** 语音服务 TTS HTTP 配置条目 */
@Serializable
data class SpeechTtsHttpConfigResultItem(
    val urlTemplate: String,
    val apiKeySet: Boolean,
    val apiKeyPreview: String,
    val headers: Map<String, String>,
    val httpMethod: String,
    val requestBody: String,
    val contentType: String,
    val localeTag: String,
    val voiceId: String,
    val modelName: String,
    val responsePipeline: List<HttpTtsResponsePipelineStep>
)

/** 语音服务 TTS VITS/Piper 包配置条目 */
@Serializable
data class SpeechTtsVitsPackageConfigResultItem(
    val packagePath: String,
    val speakerId: String,
    val options: Map<String, String>
)

/** 语音服务 STT HTTP 配置条目 */
@Serializable
data class SpeechSttHttpConfigResultItem(
    val endpointUrl: String,
    val apiKeySet: Boolean,
    val apiKeyPreview: String,
    val modelName: String
)

/** 获取语音服务配置结果 */
@Serializable
data class SpeechServicesConfigResultData(
    val ttsServiceType: String,
    val ttsHttpConfig: SpeechTtsHttpConfigResultItem,
    val ttsVitsPackageConfig: SpeechTtsVitsPackageConfigResultItem,
    val ttsCleanerRegexs: List<String>,
    val ttsSpeechRate: Float,
    val ttsPitch: Float,
    val sttServiceType: String,
    val sttHttpConfig: SpeechSttHttpConfigResultItem
) : ToolResultData() {
    override fun toString(): String {
        return "Speech services config: TTS=$ttsServiceType, STT=$sttServiceType"
    }
}

/** 更新语音服务配置结果 */
@Serializable
data class SpeechServicesUpdateResultData(
    val updated: Boolean,
    val changedFields: List<String>,
    val ttsServiceType: String,
    val sttServiceType: String,
    val ttsApiKeySet: Boolean,
    val sttApiKeySet: Boolean
) : ToolResultData() {
    override fun toString(): String {
        return "Speech services updated: changed=${changedFields.size}, TTS=$ttsServiceType, STT=$sttServiceType"
    }
}

/** TTS 单次播放测试结果 */
@Serializable
data class SpeechServicesTtsPlaybackTestResultData(
    val ttsServiceType: String,
    val providerClass: String,
    val initialized: Boolean,
    val playbackTriggered: Boolean,
    val interrupt: Boolean,
    val textLength: Int,
    val speechRate: Float,
    val pitch: Float,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val httpStatusCode: Int? = null,
    val errorBody: String? = null,
    val causeMessage: String? = null
) : ToolResultData() {
    override fun toString(): String {
        return "TTS playback test: type=$ttsServiceType, initialized=$initialized, triggered=$playbackTriggered"
    }
}

/** 角色卡工具访问配置条目 */
@Serializable
data class CharacterCardToolAccessConfigResultItem(
    val enabled: Boolean,
    val allowedBuiltinTools: List<String>,
    val allowedPackages: List<String>,
    val allowedSkills: List<String>,
    val allowedMcpServers: List<String>
)

/** 角色卡配置条目 */
@Serializable
data class CharacterCardResultItem(
    val id: String,
    val name: String,
    val description: String,
    val characterSetting: String,
    val openingStatement: String,
    val otherContentChat: String,
    val otherContentVoice: String,
    val attachedTagIds: List<String>,
    val advancedCustomPrompt: String,
    val marks: String,
    val chatModelBindingMode: String,
    val chatModelConfigId: String?,
    val chatModelIndex: Int,
    val memoryProfileBindingMode: String,
    val memoryProfileId: String?,
    val toolAccessConfig: CharacterCardToolAccessConfigResultItem,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/** 角色卡列表结果 */
@Serializable
data class CharacterCardsResultData(
    val totalCount: Int,
    val activeCharacterCardId: String?,
    val cards: List<CharacterCardResultItem>
) : ToolResultData() {
    override fun toString(): String {
        return "Character cards: $totalCount, active=$activeCharacterCardId"
    }
}

/** 单张角色卡查询结果 */
@Serializable
data class CharacterCardResultData(
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Character card: ${card.id} (${card.name})"
    }
}

/** 角色卡创建结果 */
@Serializable
data class CharacterCardCreateResultData(
    val created: Boolean,
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?,
    val changedFields: List<String>
) : ToolResultData() {
    override fun toString(): String {
        return "Character card created: ${card.id} (${card.name})"
    }
}

/** 角色卡更新结果 */
@Serializable
data class CharacterCardUpdateResultData(
    val updated: Boolean,
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?,
    val changedFields: List<String>
) : ToolResultData() {
    override fun toString(): String {
        return "Character card updated: ${card.id}, changed=${changedFields.size}"
    }
}

/** 角色卡删除结果 */
@Serializable
data class CharacterCardDeleteResultData(
    val deleted: Boolean,
    val characterCardId: String,
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Character card deleted: $characterCardId"
    }
}

/** 角色卡活跃状态更新结果 */
@Serializable
data class CharacterCardActivationResultData(
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Active character card: $activeCharacterCardId"
    }
}

/** 角色卡 Tavern JSON 导入结果 */
@Serializable
data class CharacterCardImportResultData(
    val imported: Boolean,
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Character card imported: ${card.id} (${card.name})"
    }
}

/** 角色卡 Tavern JSON 导出结果 */
@Serializable
data class CharacterCardExportResultData(
    val characterCardId: String,
    val tavernJson: String
) : ToolResultData() {
    override fun toString(): String {
        return "Character card exported: $characterCardId"
    }
}

/** 模型配置条目 */
@Serializable
data class ModelConfigResultItem(
    val id: String,
    val name: String,
    val apiProviderType: String,
    val apiProviderTypeId: String,
    val apiEndpoint: String,
    val modelName: String,
    val modelList: List<String>,
    val apiKeySet: Boolean,
    val apiKeyPreview: String,
    val maxTokensEnabled: Boolean,
    val maxTokens: Int,
    val temperatureEnabled: Boolean,
    val temperature: Float,
    val topPEnabled: Boolean,
    val topP: Float,
    val topKEnabled: Boolean,
    val topK: Int,
    val presencePenaltyEnabled: Boolean,
    val presencePenalty: Float,
    val frequencyPenaltyEnabled: Boolean,
    val frequencyPenalty: Float,
    val repetitionPenaltyEnabled: Boolean,
    val repetitionPenalty: Float,
    val hasCustomParameters: Boolean,
    val customParameters: String,
    val hasCustomHeaders: Boolean,
    val customHeaders: String,
    val contextLength: Float,
    val maxContextLength: Float,
    val enableMaxContextMode: Boolean,
    val summaryTokenThreshold: Float,
    val enableSummary: Boolean,
    val enableSummaryByMessageCount: Boolean,
    val summaryMessageCountThreshold: Int,
    val mnnForwardType: Int,
    val mnnThreadCount: Int,
    val llamaThreadCount: Int,
    val llamaContextSize: Int,
    val llamaBatchSize: Int,
    val llamaUBatchSize: Int,
    val llamaGpuLayers: Int,
    val llamaUseMmap: Boolean,
    val llamaFlashAttention: Boolean,
    val llamaKvUnified: Boolean,
    val llamaOffloadKqv: Boolean,
    val enableDirectImageProcessing: Boolean,
    val enableDirectAudioProcessing: Boolean,
    val enableDirectVideoProcessing: Boolean,
    val enableGoogleSearch: Boolean,
    val enableClaude1hPromptCache: Boolean,
    val enableToolCall: Boolean,
    val requestLimitPerMinute: Int,
    val maxConcurrentRequests: Int,
    val useMultipleApiKeys: Boolean,
    val apiKeyPoolCount: Int
)

/** 功能模型绑定条目 */
@Serializable
data class FunctionModelMappingResultItem(
    val functionType: String,
    val configId: String,
    val configName: String? = null,
    val modelIndex: Int,
    val actualModelIndex: Int? = null,
    val selectedModel: String? = null
)

/** 列出模型配置结果 */
@Serializable
data class ModelConfigsResultData(
    val totalConfigCount: Int,
    val defaultConfigId: String,
    val configs: List<ModelConfigResultItem>,
    val functionMappings: List<FunctionModelMappingResultItem>
) : ToolResultData() {
    override fun toString(): String {
        return "Model configs: $totalConfigCount, bindings: ${functionMappings.size}"
    }
}

/** 创建模型配置结果 */
@Serializable
data class ModelConfigCreateResultData(
    val created: Boolean,
    val config: ModelConfigResultItem,
    val changedFields: List<String>
) : ToolResultData() {
    override fun toString(): String {
        return "Model config created: ${config.id} (${config.name})"
    }
}

/** 更新模型配置结果 */
@Serializable
data class ModelConfigUpdateResultData(
    val updated: Boolean,
    val config: ModelConfigResultItem,
    val changedFields: List<String>,
    val affectedFunctions: List<String>
) : ToolResultData() {
    override fun toString(): String {
        return "Model config updated: ${config.id}, changed=${changedFields.size}, affectedFunctions=${affectedFunctions.size}"
    }
}

/** 删除模型配置结果 */
@Serializable
data class ModelConfigDeleteResultData(
    val deleted: Boolean,
    val configId: String,
    val affectedFunctions: List<String>,
    val fallbackConfigId: String
) : ToolResultData() {
    override fun toString(): String {
        return "Model config deleted: $configId, affectedFunctions=${affectedFunctions.size}"
    }
}

/** 列出功能模型绑定结果 */
@Serializable
data class FunctionModelConfigsResultData(
    val defaultConfigId: String,
    val mappings: List<FunctionModelMappingResultItem>
) : ToolResultData() {
    override fun toString(): String {
        return "Function model bindings: ${mappings.size}"
    }
}

/** 查询单个功能模型绑定结果 */
@Serializable
data class FunctionModelConfigResultData(
    val defaultConfigId: String,
    val functionType: String,
    val configId: String,
    val configName: String,
    val modelIndex: Int,
    val actualModelIndex: Int,
    val selectedModel: String,
    val config: ModelConfigResultItem
) : ToolResultData() {
    override fun toString(): String {
        return "Function model config: $functionType -> $configId[$actualModelIndex]"
    }
}

/** 设置功能模型绑定结果 */
@Serializable
data class FunctionModelBindingResultData(
    val functionType: String,
    val configId: String,
    val configName: String,
    val requestedModelIndex: Int,
    val actualModelIndex: Int,
    val selectedModel: String
) : ToolResultData() {
    override fun toString(): String {
        return "Function binding updated: $functionType -> $configId[$actualModelIndex]"
    }
}

/** 模型配置连接测试单项 */
@Serializable
data class ModelConfigConnectionTestItemResultData(
    val type: String,
    val success: Boolean,
    val error: String? = null
)

/** 模型配置连接测试结果 */
@Serializable
data class ModelConfigConnectionTestResultData(
    val configId: String,
    val configName: String,
    val providerType: String,
    val requestedModelIndex: Int,
    val actualModelIndex: Int,
    val testedModelName: String,
    val success: Boolean,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val tests: List<ModelConfigConnectionTestItemResultData>
) : ToolResultData() {
    override fun toString(): String {
        return "Model config connection test: $configId, success=$success, passed=$passedTests/$totalTests"
    }
}
