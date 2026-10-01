package com.ai.assistance.operit.core.tools.defaultTool

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.admin.*
import com.ai.assistance.operit.core.tools.defaultTool.root.*
import com.ai.assistance.operit.core.tools.defaultTool.standard.*
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** 工具获取器 - 根据三档用户模式获取工具，管理员工具在操作时按实际授权选择后端 */
object ToolGetter {

    /**
     * 获取文件系统工具
     * @param context 应用上下文
     * @return 根据首选权限级别的文件系统工具实现
     */
    fun getFileSystemTools(context: Context): StandardFileSystemTools {
        return when (androidPermissionPreferences.getPreferredPermissionLevel()) {
            AndroidPermissionLevel.ROOT -> RootFileSystemTools(context)
            AndroidPermissionLevel.ADMIN -> AdminFileSystemTools(context)
            AndroidPermissionLevel.STANDARD -> StandardFileSystemTools(context)
            null -> StandardFileSystemTools(context) // 默认使用标准权限级别
        }
    }

    /**
     * 获取Shell工具执行器
     * @param context 应用上下文
     * @return 根据首选权限级别的Shell工具执行器实现
     */
    fun getShellToolExecutor(context: Context): StandardShellToolExecutor {
        return StandardShellToolExecutor(context)
    }

    /**
     * 获取UI工具
     * @param context 应用上下文
     * @return 根据首选权限级别的UI工具实现
     */
    fun getUITools(context: Context): StandardUITools {
        return when (androidPermissionPreferences.getPreferredPermissionLevel()) {
            AndroidPermissionLevel.ROOT -> RootUITools(context)
            AndroidPermissionLevel.ADMIN -> AdminUITools(context)
            AndroidPermissionLevel.STANDARD -> StandardUITools(context)
            null -> StandardUITools(context) // 默认使用标准权限级别
        }
    }

    /**
     * 获取系统操作工具
     * @param context 应用上下文
     * @return 根据首选权限级别的系统操作工具实现
     */
    fun getSystemOperationTools(context: Context): StandardSystemOperationTools {
        return when (androidPermissionPreferences.getPreferredPermissionLevel()) {
            AndroidPermissionLevel.ROOT -> RootSystemOperationTools(context)
            AndroidPermissionLevel.ADMIN -> AdminSystemOperationTools(context)
            AndroidPermissionLevel.STANDARD -> StandardSystemOperationTools(context)
            null -> StandardSystemOperationTools(context) // 默认使用标准权限级别
        }
    }

    /**
     * 获取设备信息工具执行器
     * @param context 应用上下文
     * @return 根据首选权限级别的设备信息工具执行器实现
     */
    fun getDeviceInfoToolExecutor(context: Context): StandardDeviceInfoToolExecutor {
        return when (androidPermissionPreferences.getPreferredPermissionLevel()) {
            AndroidPermissionLevel.ROOT -> RootDeviceInfoToolExecutor(context)
            AndroidPermissionLevel.ADMIN -> AdminDeviceInfoToolExecutor(context)
            AndroidPermissionLevel.STANDARD -> StandardDeviceInfoToolExecutor(context)
            null -> StandardDeviceInfoToolExecutor(context) // 默认使用标准权限级别
        }
    }

    /**
     * 获取HTTP工具
     * @param context 应用上下文
     * @return HTTP工具实现（只有标准版本）
     */
    fun getHttpTools(context: Context): StandardHttpTools {
        return StandardHttpTools(context)
    }

    /**
     * 获取Web访问工具
     * @param context 应用上下文
     * @return Web访问工具实现（只有标准版本）
     */
    fun getWebVisitTool(context: Context): StandardWebVisitTool {
        return StandardWebVisitTool(context)
    }

    /**
     * 获取会话型 Web 工具
     * @param context 应用上下文
     * @return 会话型 Web 工具实现（标准权限级别）
     */
    fun getBrowserSessionTools(context: Context): StandardBrowserSessionTools {
        return StandardBrowserSessionTools(context)
    }

    /**
     * 获取Intent工具执行器
     * @param context 应用上下文
     * @return Intent工具执行器实现（只有标准版本）
     */
    fun getIntentToolExecutor(context: Context): StandardIntentToolExecutor {
        return StandardIntentToolExecutor(context)
    }

    /**
     * 获取发送广播工具执行器
     * @param context 应用上下文
     * @return 发送广播工具执行器实现（只有标准版本）
     */
    fun getSendBroadcastToolExecutor(context: Context): StandardSendBroadcastToolExecutor {
        return StandardSendBroadcastToolExecutor(context)
    }

    /**
     * 获取终端命令执行器
     * @param context 应用上下文
     * @return 终端命令执行器实现（只有标准版本）
     */
    fun getTerminalCommandExecutor(context: Context): StandardTerminalCommandExecutor {
        return StandardTerminalCommandExecutor(context)
    }

    /**
     * 获取音乐播放工具执行器
     * @param context 应用上下文
     * @return 音乐播放工具执行器实现（标准版本）
     */
    fun getMusicPlaybackTools(context: Context): StandardMusicPlaybackTools {
        return StandardMusicPlaybackTools(context)
    }

    /**
     * 获取内存查询工具执行器
     * @param context 应用上下文
     * @return 内存查询工具执行器实现（只有标准版本）
     */
    fun getMemoryQueryToolExecutor(context: Context): MemoryQueryToolExecutor {
        return MemoryQueryToolExecutor(context)
    }

    /**
     * 获取FFmpeg工具执行器
     * @param context 应用上下文
     * @return FFmpeg工具执行器实现（只有标准版本）
     */
    fun getFFmpegToolExecutor(context: Context): StandardFFmpegToolExecutor {
        return StandardFFmpegToolExecutor(context)
    }


    /**
     * 获取FFmpeg信息工具执行器
     * @return FFmpeg信息工具执行器实现（只有标准版本）
     */
    fun getFFmpegInfoToolExecutor(): StandardFFmpegInfoToolExecutor {
        return StandardFFmpegInfoToolExecutor()
    }

    /**
     * 获取FFmpeg转换工具执行器
     * @param context 应用上下文
     * @return FFmpeg转换工具执行器实现（只有标准版本）
     */
    fun getFFmpegConvertToolExecutor(context: Context): StandardFFmpegConvertToolExecutor {
        return StandardFFmpegConvertToolExecutor(context)
    }

    /**
     * 获取计算器
     * @return 计算器实现（只有标准版本）
     */
    fun getCalculator() = StandardCalculator

    /**
     * 获取工作流工具
     * @param context 应用上下文
     * @return 工作流工具实现（只有标准版本）
     */
    fun getWorkflowTools(context: Context): StandardWorkflowTools {
        return StandardWorkflowTools(context)
    }

    /**
     * 获取对话管理工具
     * @param context 应用上下文
     * @return 对话管理工具实现（只有标准版本）
     */
    fun getChatManagerTool(context: Context): StandardChatManagerTool {
        return StandardChatManagerTool(context)
    }

    /**
     * 获取软件设置修改工具
     * @param context 应用上下文
     * @return 软件设置修改工具实现（标准版本）
     */
    fun getSoftwareSettingsModifyTools(context: Context): StandardSoftwareSettingsModifyTools {
        return StandardSoftwareSettingsModifyTools(context)
    }
}
