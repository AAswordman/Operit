package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** 文件链接保存真实来源；目录与文件类型由该来源的文件工具判断。 */
internal data class WorkspaceFileLink(
    val path: String,
    val environment: String,
    val line: Int? = null,
    val anchor: String? = null,
)

/** 识别绝对路径及显式环境链接，普通网页和应用协议继续交给外部入口。 */
internal fun parseWorkspaceFileLink(url: String): WorkspaceFileLink? {
    val source = url.trim()
    if (source.isEmpty() || source.any { it.code < 32 }) return null
    val repositoryPath = Regex("^repo:([^/:]+):(/.*)$", RegexOption.IGNORE_CASE).matchEntire(source)
    val normalized = if (repositoryPath != null) {
        "repo://${repositoryPath.groupValues[1]}${repositoryPath.groupValues[2]}"
    } else {
        source
    }
    val uri = try {
        URI(normalized.replace(" ", "%20"))
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase()
    val authority = uri.authority.orEmpty()
    val query = uri.rawQuery.orEmpty().split('&').associate { entry ->
        val parts = entry.split('=', limit = 2)
        parts[0] to parts.getOrNull(1).orEmpty()
    }
    var path = uri.path.orEmpty()
    val environment = when (scheme) {
        null -> {
            if (uri.rawAuthority != null || !path.startsWith('/')) return null
            inferWorkspaceFileEnvironment(path)
        }
        "file" -> when (authority.lowercase()) {
            "", "localhost" -> inferWorkspaceFileEnvironment(path)
            "android", "linux" -> authority.lowercase()
            else -> return null
        }
        "android", "linux" -> {
            if (authority.isNotEmpty() && authority != "localhost") path = "/$authority$path"
            scheme
        }
        "repo" -> {
            if (authority.isEmpty()) return null
            "repo:$authority"
        }
        else -> return null
    }
    if (!path.startsWith('/') || path.any { it.code < 32 }) return null
    val explicitEnvironment = try {
        query["environment"]?.let { URLDecoder.decode(it, "UTF-8") }
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (explicitEnvironment?.any { it.code < 32 } == true) return null
    if (explicitEnvironment != null && explicitEnvironment != "android" &&
        explicitEnvironment != "linux" && !explicitEnvironment.matches(Regex("repo:.+"))) return null

    val lineFragment = uri.fragment?.let {
        Regex("(?:L|line=)([0-9]+)(?:-L?[0-9]+)?").matchEntire(it)
    }
    val fragmentLine = lineFragment?.groupValues?.get(1)?.toIntOrNull()
    // 编码后的冒号属于真实文件名，只有原始路径中的冒号才作为行号后缀。
    val suffix = if (Regex("^(.+):([0-9]+)$").matches(uri.rawPath.orEmpty())) {
        Regex("^(.+):([0-9]+)$").matchEntire(path)
    } else null
    // 定位优先级为 fragment、query、路径后缀；后缀始终是定位语法，
    // 即使行号取自更高优先级的来源，也要将后缀从真实文件路径中移除。
    val queryLine = try {
        query["line"]?.let { URLDecoder.decode(it, "UTF-8").toIntOrNull() }
    } catch (_: IllegalArgumentException) {
        return null
    }
    val line = (fragmentLine ?: queryLine ?: suffix?.groupValues?.get(2)?.toIntOrNull())
        ?.takeIf { it > 0 }
    if (suffix != null && line != null) path = suffix.groupValues[1]
    val anchor = uri.fragment?.takeIf { it.isNotEmpty() && lineFragment == null }
    if (anchor?.any { it.code < 32 } == true) return null
    return WorkspaceFileLink(path, explicitEnvironment ?: environment, line, anchor)
}

private fun inferWorkspaceFileEnvironment(path: String): String {
    // Android 的外部存储与 OTG 挂载包含 /mnt；显式协议及 environment 参数仍优先。
    val deviceRoots = listOf("/sdcard", "/storage", "/mnt", "/data", "/system", "/vendor", "/apex")
    return if (deviceRoots.any { path == it || path.startsWith("$it/") }) "android" else "linux"
}
