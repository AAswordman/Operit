package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.util.Locale

/** 文档引用按当前文件的目录和来源解析，外链保留交给系统的完整地址。 */
internal sealed interface WorkspaceMarkdownLink {
    data class File(val target: WorkspaceFileLink) : WorkspaceMarkdownLink
    data class External(val url: String) : WorkspaceMarkdownLink
    data object Invalid : WorkspaceMarkdownLink
}

internal fun resolveWorkspaceMarkdownLink(
    url: String,
    documentPath: String,
    documentEnvironment: String,
): WorkspaceMarkdownLink {
    val source = url.trim().removeSurrounding("<", ">")
    if (source.isEmpty() || source.any { it.code < 32 } || !documentPath.startsWith('/')) {
        return WorkspaceMarkdownLink.Invalid
    }
    // 普通文件名的行号后缀不是应用协议；反斜杠只在文档路径中作为分隔符处理。
    val relativeLine = Regex("^[^:/?#]+\\.[^:/?#]+:[0-9]+(?:[?#].*)?$").matches(source)
    val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(source)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
    val local = scheme == null || relativeLine || scheme in setOf("file", "android", "linux", "repo")
    if (!local) {
        return if (validWorkspaceMarkdownUri(source) != null) WorkspaceMarkdownLink.External(source)
        else WorkspaceMarkdownLink.Invalid
    }
    val normalized = if (scheme == null || relativeLine) source.replace('\\', '/') else source
    val uri = validWorkspaceMarkdownUri(if (relativeLine) "./$normalized" else normalized)
        ?: return WorkspaceMarkdownLink.Invalid
    if (uri.scheme == null && uri.rawAuthority != null) {
        return WorkspaceMarkdownLink.External("https:$normalized")
    }
    val rawFragment = uri.fragment.orEmpty()
    val explicitLine = Regex("(?:L|line=)([0-9]+)(?:-L?[0-9]+)?").matchEntire(rawFragment)
    if (explicitLine != null && explicitLine.groupValues[1].toIntOrNull()?.takeIf { it > 0 } == null) {
        return WorkspaceMarkdownLink.Invalid
    }
    val queryLine = uri.rawQuery.orEmpty().split('&').lastOrNull { it.substringBefore('=') == "line" }
        ?.substringAfter('=', "")
    val suffixLine = Regex(":([0-9]+)$").find(uri.rawPath.orEmpty())?.groupValues?.get(1)
    for (value in listOfNotNull(queryLine, suffixLine)) {
        val decoded = try { URLDecoder.decode(value, "UTF-8") } catch (_: IllegalArgumentException) {
            return WorkspaceMarkdownLink.Invalid
        }
        if (decoded.toIntOrNull()?.takeIf { it > 0 } == null) return WorkspaceMarkdownLink.Invalid
    }
    val fileUrl = if (uri.scheme == null) {
        // 拼接原始编码路径，保留 %3A 等文件名字符与行号语法的区别。
        val encodedParent = URI(null, null, documentPath.substringBeforeLast('/', ""), null).rawPath
        val rawPath = when {
            uri.rawPath.isNullOrEmpty() -> URI(null, null, documentPath, null).rawPath
            uri.rawPath.startsWith('/') -> uri.rawPath
            else -> "$encodedParent/${uri.rawPath}"
        }
        "file://$rawPath" + uri.rawQuery?.let { "?$it" }.orEmpty() +
            uri.rawFragment?.let { "#$it" }.orEmpty()
    } else {
        normalized
    }
    val parsed = parseWorkspaceFileLink(fileUrl) ?: return WorkspaceMarkdownLink.Invalid
    val hasEnvironment = uri.rawQuery.orEmpty().split('&').any { it.substringBefore('=') == "environment" }
    val implicitEnvironment = uri.scheme == null ||
        (uri.scheme.equals("file", ignoreCase = true) && uri.authority.orEmpty().lowercase(Locale.ROOT) in setOf("", "localhost"))
    val environment = if (implicitEnvironment && !hasEnvironment) documentEnvironment else parsed.environment
    if (environment != "android" && environment != "linux" && !environment.matches(Regex("repo:.+"))) {
        return WorkspaceMarkdownLink.Invalid
    }
    return WorkspaceMarkdownLink.File(parsed.copy(
        path = normalizeWorkspaceMarkdownPath(parsed.path),
        environment = environment,
        line = if (uri.scheme == null && uri.rawPath.isNullOrEmpty() && rawFragment.isEmpty()) parsed.line ?: 1 else parsed.line,
    ))
}

private fun validWorkspaceMarkdownUri(source: String): URI? = try {
    URI(source.replace(" ", "%20"))
} catch (_: URISyntaxException) {
    null
}

private fun normalizeWorkspaceMarkdownPath(path: String): String {
    val segments = mutableListOf<String>()
    for (segment in path.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
    }
    return "/" + segments.joinToString("/")
}

/** 标题锚点定位到源码的一基行号；代码块中的伪标题不参与匹配。 */
internal fun findWorkspaceMarkdownAnchorLine(content: String, anchor: String): Int? {
    if (anchor.isEmpty()) return 1
    val target = anchor.removePrefix("user-content-").lowercase(Locale.ROOT)
    val usedSlugs = mutableSetOf<String>()
    val nextDuplicates = mutableMapOf<String, Int>()
    val atx = Regex("^ {0,3}#{1,6}(?:[ \\t]+|$)(.*?)(?:[ \\t]+#+[ \\t]*)?$")
    val fencePattern = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    val setext = Regex("^ {0,3}(?:=+|-+)[ \\t]*$")
    var fence: String? = null
    var previous = ""
    var previousLine = 0
    for ((index, rawLine) in content.lineSequence().withIndex()) {
        val line = rawLine.removeSuffix("\r")
        val fenceMatch = fencePattern.matchEntire(line)
        val currentFence = fence
        if (currentFence != null) {
            if (fenceMatch != null && fenceMatch.groupValues[1].first() == currentFence.first() &&
                fenceMatch.groupValues[1].length >= currentFence.length && fenceMatch.groupValues[2].isBlank()) {
                fence = null
            }
            previous = ""
            continue
        }
        if (fenceMatch != null) {
            fence = fenceMatch.groupValues[1]
            previous = ""
            continue
        }
        val heading = atx.matchEntire(line)?.groupValues?.get(1)
        val setextHeading = heading == null && previous.isNotBlank() &&
            !previous.startsWith("    ") && !previous.startsWith('\t') && setext.matches(line)
        val text = when {
            heading != null -> heading
            setextHeading -> previous
            else -> null
        }
        if (text != null) {
            val plain = text.replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("(?<!\\w)_{1,3}([^_]+)_{1,3}(?!\\w)"), "$1")
                .replace("*", "").replace("`", "").trim()
            val base = plain.lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{N}\\p{M}_\\-\\s]"), "")
                .replace(Regex("\\s"), "-")
            var duplicate = nextDuplicates[base] ?: 0
            var slug = if (duplicate == 0) base else "$base-$duplicate"
            while (!usedSlugs.add(slug)) {
                duplicate += 1
                slug = "$base-$duplicate"
            }
            nextDuplicates[base] = duplicate + 1
            if (slug == target || plain == anchor) return if (setextHeading) previousLine else index + 1
            previous = ""
        } else {
            previous = line
            previousLine = index + 1
        }
    }
    return null
}