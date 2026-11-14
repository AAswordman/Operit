package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import java.io.File

/**
 * 文件工具工作区防护的纯函数判定器。
 *
 * 判定规则（与需求确认一致）：
 * - 会话未绑定工作区（root == null）→ 所有路径一律视为工作区外（不存在可信区）
 * - android 路径：canonicalPath 前缀比较（root 或 root/子路径 才算区内）
 * - linux / repo: 路径无法可靠映射回宿主目录做前缀比较——当工具环境与会话绑定环境
 *   完全一致时视为区内（该环境整体即会话授权的工作区），否则视为区外
 *
 * delete_file 的危险根目录硬底线独立于本判定器，见 [WorkspacePathChecker.DANGEROUS_DELETE_ROOTS]。
 */
object WorkspacePathChecker {

    enum class Zone { INSIDE, OUTSIDE }

    /** 受工作区防护的文件工具族（防护与审查适用范围，共 24 个） */
    val WORKSPACE_PROTECTED_FILE_TOOLS: Set<String> = setOf(
        "list_files",
        "read_file",
        "read_file_part",
        "read_file_full",
        "read_file_binary",
        "write_file",
        "write_file_binary",
        "create_file",
        "edit_file",
        "apply_file",
        "delete_file",
        "make_directory",
        "find_files",
        "grep_code",
        "grep_context",
        "download_file",
        "file_exists",
        "move_file",
        "copy_file",
        "file_info",
        "zip_files",
        "unzip_files",
        "open_file",
        "share_file",
    )

    /** 工具参数中承载路径的参数名 */
    private val PATH_PARAM_NAMES = setOf("path", "source", "destination")

    /** 递归删除的绝对禁区（精确匹配规范化后的路径） */
    val DANGEROUS_DELETE_ROOTS: Set<String> = setOf(
        "/",
        "/storage",
        "/storage/emulated",
        "/storage/emulated/0",
        "/sdcard",
        "/data",
        "/data/data",
        "/system",
        "/vendor",
        "/etc",
        "/mnt",
        "/root",
        "/home",
        "/usr",
        "/var",
    )

    /** 修改意图：递归删除危险根目录的硬底线。命中即拒绝，独立于工作区判定与权限模式 */
    fun isDangerousRecursiveDeleteTarget(path: String): Boolean {
        // 评审 P1：模型传入的拼写变体（/sdcard/.、//sdcard、/storage/emulated/0/.）与
        // 集合的精确字符串匹配不符但指向同一目录，deleteRecursively 会真的清空。
        // 词法规范化覆盖 ./..//重复斜杠（纯字符串运算，测试可跨平台断言）；
        // canonical 再覆盖符号链接与文件系统层差异（路径不存在时 canonicalPath 不抛错，
        // 仍按词法规范化返回）。两道比较任一命中即拒绝。
        val normalized = lexicallyNormalizePath(path)
        if (normalized.isEmpty()) return false
        if (normalized in DANGEROUS_DELETE_ROOTS) return true
        val canonical = runCatching { File(normalized).canonicalPath }.getOrNull() ?: return false
        return canonical in DANGEROUS_DELETE_ROOTS
    }

    /**
     * 纯词法路径规范化：折叠重复斜杠、消解 "." 与 ".."（绝对路径在根处的 ".." 停留在根）、
     * 去首尾空白与尾部斜杠。不做文件系统访问，桌面 JVM 与设备行为一致，供单测断言。
     */
    internal fun lexicallyNormalizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return ""
        val absolute = trimmed.startsWith('/')
        val parts = trimmed.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = ArrayDeque<String>()
        for (part in parts) {
            if (part == "..") {
                val canPop = stack.isNotEmpty() && stack.last() != ".."
                if (canPop) {
                    stack.removeLast()
                } else if (!absolute) {
                    stack.addLast(part)
                }
            } else {
                stack.addLast(part)
            }
        }
        val joined = stack.joinToString("/")
        return if (absolute) "/$joined" else joined
    }

    fun isWorkspaceProtectedTool(toolName: String): Boolean {
        if (toolName in WORKSPACE_PROTECTED_FILE_TOOLS) return true
        // 修改意图：扩展包文件工具（如 extended_file_tools:copy_file）与内置文件工具同族，
        // 按限定名后缀纳入同一套工作区防护
        val localName = toolName.substringAfter(':', "")
        return localName.isNotEmpty() && localName in WORKSPACE_PROTECTED_FILE_TOOLS
    }

    /** 从工具参数中提取全部路径（path / source / destination） */
    fun extractPaths(tool: AITool): List<String> =
        tool.parameters
            .filter { it.name in PATH_PARAM_NAMES }
            .map { it.value }
            .filter { it.isNotBlank() }

    /**
     * 判定一次文件工具调用的目标整体是否在工作区内。
     *
     * @param chatWorkspaceRoot 会话绑定的工作区根（ChatHistory.workspace；null = 未绑定）
     * @param chatWorkspaceEnv 会话绑定的环境（ChatHistory.workspaceEnv；null 视为 android）
     * @return 多路径时（move/copy）所有路径都在区内才算 INSIDE；任一路径在区外即 OUTSIDE
     */
    fun resolveZone(
        chatWorkspaceRoot: String?,
        chatWorkspaceEnv: String?,
        tool: AITool,
    ): Zone {
        val paths = extractPaths(tool)
        if (paths.isEmpty()) {
            // 修改意图：文件工具没有可提取的路径参数属于异常调用，按区外保守处理
            return Zone.OUTSIDE
        }
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val zones = paths.map { path ->
            resolvePathZone(
                chatWorkspaceRoot = chatWorkspaceRoot,
                chatWorkspaceEnv = chatWorkspaceEnv,
                path = path,
                pathEnvironment = environment,
            )
        }
        return if (zones.all { it == Zone.INSIDE }) Zone.INSIDE else Zone.OUTSIDE
    }

    /**
     * 单路径判定。move/copy 的 source/destination 可各自带 *_environment 参数；
     * 这里只按主 environment 参数解析（跨环境复制的精确判定由 copy_file 自身的校验承担）。
     */
    private fun resolvePathZone(
        chatWorkspaceRoot: String?,
        chatWorkspaceEnv: String?,
        path: String,
        pathEnvironment: String?,
    ): Zone {
        // 需求确认：未绑定工作区时不存在可信区，所有路径按区外
        if (chatWorkspaceRoot.isNullOrBlank()) return Zone.OUTSIDE

        val effectiveToolEnv = pathEnvironment?.trim()?.takeIf { it.isNotBlank() } ?: "android"
        val effectiveWorkspaceEnv = chatWorkspaceEnv?.trim()?.takeIf { it.isNotBlank() } ?: "android"

        return when {
            // android 路径 vs android 工作区：canonicalPath 前缀比较
            effectiveToolEnv == "android" && effectiveWorkspaceEnv == "android" ->
                if (isAndroidPathInside(chatWorkspaceRoot, path)) Zone.INSIDE else Zone.OUTSIDE

            // linux / repo: 无法做宿主目录前缀比较——环境完全一致即视为区内
            effectiveToolEnv == effectiveWorkspaceEnv &&
                (effectiveToolEnv == "linux" || effectiveToolEnv.startsWith("repo:")) -> Zone.INSIDE

            else -> Zone.OUTSIDE
        }
    }

    private fun isAndroidPathInside(workspaceRoot: String, path: String): Boolean {
        val root = runCatching { File(workspaceRoot).canonicalPath }.getOrDefault(workspaceRoot)
            .trimEnd('/')
        val target = runCatching { File(path).canonicalPath }.getOrDefault(path)
        if (root.isEmpty()) return false
        return target == root || target.startsWith("$root/")
    }
}
