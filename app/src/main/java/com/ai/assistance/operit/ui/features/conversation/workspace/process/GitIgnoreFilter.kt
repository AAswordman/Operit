package com.ai.assistance.operit.ui.features.conversation.workspace.process

import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File

/**
 * GitIgnore 文件过滤器
 * 根据 .gitignore 规则过滤文件和目录
 */
object GitIgnoreFilter {
    private const val TAG = "GitIgnoreFilter"

    // 默认需要排除的目录（即使 .gitignore 中没有）
    private val DEFAULT_EXCLUDES = setOf("backup", ".backup", ".operit")

    fun defaultRules(): List<String> = DEFAULT_EXCLUDES.toList()

    fun normalizePath(path: String): String {
        var p = path.trim().replace('\\', '/')
        if (p.isEmpty()) return "/"
        while (p.contains("//")) p = p.replace("//", "/")
        return p
    }

    fun parentPath(path: String): String {
        val normalized = normalizePath(path).trimEnd('/')
        if (normalized.isBlank() || normalized == "/") return "/"
        val idx = normalized.lastIndexOf('/')
        if (idx < 0) return normalized
        if (idx == 0) return "/"
        return normalized.substring(0, idx)
    }

    fun joinPath(base: String, child: String): String {
        val normalized = normalizePath(base)
        return if (normalized == "/") {
            "/$child"
        } else {
            normalized.trimEnd('/') + "/" + child
        }
    }

    fun toRelativePath(basePath: String, fullPath: String): String? {
        val base = normalizePath(basePath).trimEnd('/').ifBlank { "/" }
        val full = normalizePath(fullPath)
        if (full == base) return ""

        val prefix = if (base == "/") "/" else "$base/"
        if (!full.startsWith(prefix)) return null
        return full.removePrefix(prefix).trimStart('/')
    }

    fun parseRulesFromContent(content: String?): List<String> {
        if (content.isNullOrBlank()) return emptyList()

        val rules = LinkedHashSet<String>()
        content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { rule ->
                rules.add(rule)
                if (rule.endsWith('/')) {
                    val withoutTrailingSlash = rule.removeSuffix("/").trim()
                    if (withoutTrailingSlash.isNotEmpty()) {
                        rules.add(withoutTrailingSlash)
                    }
                }
            }

        return rules.toList()
    }

    fun mergeWithDefaults(rules: List<String>): List<String> {
        val merged = LinkedHashSet<String>()
        merged.addAll(DEFAULT_EXCLUDES)
        merged.addAll(rules)
        return merged.toList()
    }

    fun buildRulesFromContent(content: String?): List<String> {
        return mergeWithDefaults(parseRulesFromContent(content))
    }

    fun shouldIgnoreFileRelativePath(relativePath: String, rules: List<String>): Boolean {
        val rel = relativePath.replace('\\', '/').trimStart('/')
        if (rel.isBlank()) return false
        val fileName = rel.substringAfterLast('/')
        return shouldIgnore(rel, fileName, isDirectory = false, rules = rules)
    }

    /**
     * 从工作区目录加载 .gitignore 规则
     */
    fun loadRules(workspaceDir: File): List<String> {
        val rules = LinkedHashSet<String>()

        // 添加默认排除规则
        rules.addAll(DEFAULT_EXCLUDES)

        try {
            val gitignoreFile = File(workspaceDir, ".gitignore")
            if (gitignoreFile.exists() && gitignoreFile.isFile) {
                rules.addAll(parseRulesFromContent(gitignoreFile.readText()))
                AppLogger.d(TAG, "已加载 ${rules.size} 条规则 from ${gitignoreFile.absolutePath}")
            } else {
                AppLogger.d(TAG, ".gitignore 文件不存在，仅使用默认排除规则")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载 .gitignore 文件失败", e)
        }

        return rules.toList()
    }
    
    /**
     * 检查文件或目录是否应该被忽略
     * @param file 要检查的文件或目录
     * @param workspaceDir 工作区根目录
     * @param rules gitignore 规则列表
     * @return true 如果应该被忽略
     */
    fun shouldIgnore(file: File, workspaceDir: File, rules: List<String>): Boolean {
        val relativePath = try {
            file.relativeTo(workspaceDir).path.replace(File.separatorChar, '/')
        } catch (e: Exception) {
            return false
        }
        
        val fileName = file.name
        
        for (rule in rules) {
            if (matchesRule(relativePath, fileName, file.isDirectory, rule)) {
                return true
            }
        }
        
        return false
    }

    fun shouldIgnore(relativePath: String, fileName: String, isDirectory: Boolean, rules: List<String>): Boolean {
        val rel = relativePath.trimStart('/').replace('\\', '/').trimStart('/')
        val name = fileName

        for (rule in rules) {
            if (matchesRule(rel, name, isDirectory, rule)) {
                return true
            }
        }

        return false
    }

    /**
     * 检查目录在遍历时是否应该继续进入。
     * 返回 false 时调用方应直接跳过该目录，避免无意义的深层遍历。
     */
    fun shouldEnterDirectory(directory: File, workspaceDir: File, rules: List<String>): Boolean {
        if (!directory.isDirectory) return false
        return !shouldIgnore(directory, workspaceDir, rules)
    }
    
    /**
     * 匹配单个 gitignore 规则
     */
    private fun matchesRule(relativePath: String, fileName: String, isDirectory: Boolean, rule: String): Boolean {
        var pattern = rule.trim()
        if (pattern.isEmpty()) return false
        
        // 处理否定规则（以 ! 开头）
        if (pattern.startsWith("!")) {
            return false // 暂不支持否定规则
        }
        
        // 处理目录规则（以 / 结尾）
        val dirOnly = pattern.endsWith("/")
        if (dirOnly) {
            if (!isDirectory) return false
            pattern = pattern.removeSuffix("/")
        }
        
        // 处理根目录规则（以 / 开头）
        val rootOnly = pattern.startsWith("/")
        if (rootOnly) {
            pattern = pattern.removePrefix("/")
        }
        
        // 简单模式匹配
        return when {
            // 完整路径匹配
            rootOnly -> matchPattern(relativePath, pattern)
            
            // 文件名匹配
            pattern.contains("/") -> matchPattern(relativePath, pattern) || relativePath.endsWith("/$pattern")
            
            // 任何位置的文件名匹配
            else -> {
                fileName == pattern || 
                matchPattern(fileName, pattern) ||
                relativePath.split("/").any { matchPattern(it, pattern) }
            }
        }
    }
    
    /**
     * 简单的通配符模式匹配
     * 支持 * 和 ** 通配符
     */
    private fun matchPattern(text: String, pattern: String): Boolean {
        // 处理 **/ 前缀（匹配任意层级目录）
        if (pattern.startsWith("**/")) {
            val subPattern = pattern.removePrefix("**/")
            return text.endsWith(subPattern) || matchPattern(text, subPattern)
        }
        
        // 处理 /** 后缀（匹配目录下所有内容）
        if (pattern.endsWith("/**")) {
            val prefix = pattern.removeSuffix("/**")
            return text.startsWith(prefix) || text == prefix
        }
        
        // 简单的 * 通配符匹配
        if (pattern.contains("*")) {
            return matchWildcard(text, pattern)
        }
        
        // 精确匹配
        return text == pattern
    }
    
    /**
     * 通配符匹配（支持 * 和 ?）
     */
    private fun matchWildcard(text: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        
        return try {
            text.matches(Regex("^$regex$"))
        } catch (e: Exception) {
            false
        }
    }
}
