package com.ai.assistance.operit.ui.features.conversation.workspace

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Workspace configuration data classes
 * 定义工作区配置的数据结构，用于解析 .operit/config.json
 */

@Serializable
data class WorkspaceConfig(
    val projectType: String = "web",
    val title: String? = null, // 自定义标题，null 时使用默认的 projectType
    val description: String? = null, // 自定义描述
    val server: ServerConfig = ServerConfig(),
    val preview: PreviewConfig = PreviewConfig(),
    val commands: List<CommandConfig> = emptyList(),
    val export: ExportConfig = ExportConfig(),
    val watch: WatchConfig = WatchConfig()
)

@Serializable
data class ServerConfig(
    val enabled: Boolean = false,
    val port: Int = 8093,
    val autoStart: Boolean = false
)

@Serializable
data class PreviewConfig(
    val type: String = "browser", // "browser" | "terminal" | "none"
    val url: String = "",
    val showPreviewButton: Boolean = false, // 是否显示"切换到浏览器预览"按钮
    val previewButtonLabel: String = "", // 预览按钮的文字，空字符串时使用默认值
) {
    fun getLocalizedLabel(context: Context): String {
        return if (previewButtonLabel.isBlank()) {
            context.getString(R.string.workspace_browser_preview)
        } else {
            previewButtonLabel
        }
    }
}

@Serializable
data class CommandConfig(
    val id: String,
    val label: String,
    val command: String? = null,
    val tool: String? = null,
    val toolParameters: Map<String, String> = emptyMap(),
    val workingDir: String = ".",
    val shell: Boolean = true,
    val usesDedicatedSession: Boolean = false, // 是否使用独立会话（适用于长时间运行的命令如 tsc watch）
    val sessionTitle: String? = null // 独立会话的标题，null 时使用 label
)

@Serializable
data class ExportConfig(
    val enabled: Boolean = true // 是否显示导出按钮
)

@Serializable
data class WatchConfig(
    val enabled: Boolean = true,
    val maxDepth: Int = 3,
    val maxChangedFiles: Int = 80,
    val exclude: List<String> = listOf(".git", ".operit", ".backup", "backup")
)

object WorkspaceConfigReader {
    private const val TAG = "WorkspaceConfigReader"
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 读取工作区配置文件
     * @param workspacePath 工作区根目录路径
     * @return 解析后的配置对象，如果不存在或解析失败则返回默认配置
     */
    fun readConfig(workspacePath: String): WorkspaceConfig {
        val configFile = File(workspacePath, ".operit/config.json")
        
        if (!configFile.exists()) {
            AppLogger.d(TAG, "Config file not found at ${configFile.absolutePath}, using default")
            return getDefaultWebConfig()
        }

        return try {
            val content = configFile.readText()
            json.decodeFromString<WorkspaceConfig>(content)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse config file: ${e.message}", e)
            getDefaultWebConfig()
        }
    }

    /**
     * 检查工作区是否有配置文件
     */
    fun hasConfig(workspacePath: String): Boolean {
        val configFile = File(workspacePath, ".operit/config.json")
        return configFile.exists()
    }

    /**
     * 默认 Web 配置（向后兼容旧工作区）
     */
    private fun getDefaultWebConfig(): WorkspaceConfig {
        return WorkspaceConfig(
            projectType = "web",
            server = ServerConfig(
                enabled = true,
                port = 8093,
                autoStart = true
            ),
            preview = PreviewConfig(
                type = "browser",
                url = "http://localhost:8093"
            ),
            commands = emptyList()
        )
    }
}
