package com.ai.assistance.operit.core.tools.defaultTool.admin

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.debugger.DebuggerFileSystemTools
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardFileSystemTools
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.permissions.PermissionOperationRouter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

/** 管理员文件工具在每次操作前选择可用后端，复用现有标准和 Shell 实现。 */
open class AdminFileSystemTools(context: Context) : DebuggerFileSystemTools(context) {
    private val standardTools = StandardFileSystemTools(context)

    private suspend fun route(
        readOnly: Boolean,
        privileged: suspend () -> ToolResult,
        standard: suspend () -> ToolResult
    ): ToolResult = PermissionOperationRouter.execute(
        capabilities = PermissionCapabilityResolver.shellSnapshot(context),
        readOnly = readOnly,
        privileged = privileged,
        standard = standard,
        isSuccess = { it.success }
    )

    override suspend fun listFiles(tool: AITool): ToolResult =
        route(true, { super.listFiles(tool) }, { standardTools.listFiles(tool) })

    override suspend fun readFileFull(tool: AITool): ToolResult =
        route(true, { super.readFileFull(tool) }, { standardTools.readFileFull(tool) })

    override suspend fun readFile(tool: AITool): ToolResult =
        route(true, { super.readFile(tool) }, { standardTools.readFile(tool) })

    override suspend fun readFilePart(tool: AITool): ToolResult =
        route(true, { super.readFilePart(tool) }, { standardTools.readFilePart(tool) })

    override suspend fun fileExists(tool: AITool): ToolResult =
        route(true, { super.fileExists(tool) }, { standardTools.fileExists(tool) })

    override suspend fun findFiles(tool: AITool): ToolResult =
        route(true, { super.findFiles(tool) }, { standardTools.findFiles(tool) })

    override suspend fun fileInfo(tool: AITool): ToolResult =
        route(true, { super.fileInfo(tool) }, { standardTools.fileInfo(tool) })

    override suspend fun writeFile(tool: AITool): ToolResult =
        route(false, { super.writeFile(tool) }, { standardTools.writeFile(tool) })

    override suspend fun deleteFile(tool: AITool): ToolResult =
        route(false, { super.deleteFile(tool) }, { standardTools.deleteFile(tool) })

    override suspend fun moveFile(tool: AITool): ToolResult =
        route(false, { super.moveFile(tool) }, { standardTools.moveFile(tool) })

    override suspend fun copyFile(tool: AITool): ToolResult =
        route(false, { super.copyFile(tool) }, { standardTools.copyFile(tool) })

    override suspend fun makeDirectory(tool: AITool): ToolResult =
        route(false, { super.makeDirectory(tool) }, { standardTools.makeDirectory(tool) })

    override suspend fun zipFiles(tool: AITool): ToolResult =
        route(false, { super.zipFiles(tool) }, { standardTools.zipFiles(tool) })

    override suspend fun unzipFiles(tool: AITool): ToolResult =
        route(false, { super.unzipFiles(tool) }, { standardTools.unzipFiles(tool) })

    override suspend fun openFile(tool: AITool): ToolResult =
        route(false, { super.openFile(tool) }, { standardTools.openFile(tool) })

    override suspend fun shareFile(tool: AITool): ToolResult =
        route(false, { super.shareFile(tool) }, { standardTools.shareFile(tool) })

    override suspend fun downloadFile(tool: AITool): ToolResult =
        route(false, { super.downloadFile(tool) }, { standardTools.downloadFile(tool) })
}
