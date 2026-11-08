package com.ai.assistance.operit.core.tools.builtin.web.session.userscript

import android.net.Uri
import java.util.Locale

internal object UserscriptMatcher {
    fun matches(
        metadata: ParsedUserscriptMetadata,
        pageUrl: String,
        isTopFrame: Boolean
    ): Boolean {
        if (metadata.noFrames && !isTopFrame) {
            return false
        }
        if (pageUrl.isBlank()) {
            return false
        }
        if (metadata.excludeMatches.any { matchPattern(it, pageUrl) }) {
            return false
        }
        if (metadata.excludes.any { globPattern(it, pageUrl) }) {
            return false
        }

        val hasPositiveRules = metadata.matches.isNotEmpty() || metadata.includes.isNotEmpty()
        if (!hasPositiveRules) {
            return false
        }
        if (metadata.matches.any { matchPattern(it, pageUrl) }) {
            return true
        }
        if (metadata.includes.any { globPattern(it, pageUrl) }) {
            return true
        }
        return false
    }

    fun isConnectAllowed(
        metadata: ParsedUserscriptMetadata,
        pageUrl: String,
        targetUrl: String
    ): Boolean {
        val pageUri = Uri.parse(pageUrl)
        val targetUri = Uri.parse(targetUrl)
        val pageOrigin = originOf(pageUri)
        val targetOrigin = originOf(targetUri)
        if (pageOrigin != null && targetOrigin != null && pageOrigin == targetOrigin) {
            return true
        }
        if (metadata.connects.isEmpty()) {
            return false
        }
        val targetHost = targetUri.host?.lowercase(Locale.ROOT).orEmpty()
        return metadata.connects.any { rule ->
            when (val normalized = rule.trim().lowercase(Locale.ROOT)) {
                "*" -> true
                "self" -> pageOrigin != null && pageOrigin == targetOrigin
                targetHost -> true
                else -> {
                    if (normalized.startsWith("*.")) {
                        val suffix = normalized.removePrefix("*.")
                        targetHost == suffix || targetHost.endsWith(".$suffix")
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun matchPattern(pattern: String, url: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) {
            return false
        }
        return runCatching {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val fullPath =
                buildString {
                    append(uri.encodedPath ?: "/")
                    uri.encodedQuery?.takeIf { it.isNotBlank() }?.let {
                        append('?')
                        append(it)
                    }
                    uri.encodedFragment?.takeIf { it.isNotBlank() }?.let {
                        append('#')
                        append(it)
                    }
                }

            val schemePart = trimmed.substringBefore("://")
            val afterScheme = trimmed.substringAfter("://", "")
            val hostPart = afterScheme.substringBefore("/")
            val pathPart = afterScheme.substringAfter("/", "")

            val schemeMatches =
                when (schemePart) {
                    "*" -> scheme == "http" || scheme == "https"
                    else -> schemePart.equals(scheme, ignoreCase = true)
                }
            if (!schemeMatches) {
                return false
            }

            val normalizedHost = hostPart.lowercase(Locale.ROOT)
            val hostMatches =
                when {
                    normalizedHost == "*" -> host.isNotBlank()
                    normalizedHost.startsWith("*.") -> {
                        val suffix = normalizedHost.removePrefix("*.")
                        host == suffix || host.endsWith(".$suffix")
                    }
                    else -> host == normalizedHost
                }
            if (!hostMatches) {
                return false
            }

            val regex = globToRegex("/$pathPart")
            regex.matches(fullPath.ifBlank { "/" })
        }.getOrDefault(false)
    }

    private fun globPattern(pattern: String, url: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) {
            return false
        }
        return globToRegex(trimmed).matches(url)
    }

    private fun globToRegex(pattern: String): Regex {
        val builder = StringBuilder("^")
        pattern.forEach { ch ->
            when (ch) {
                '*' -> builder.append(".*")
                '.', '?', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' ->
                    builder.append('\\').append(ch)
                else -> builder.append(ch)
            }
        }
        builder.append('$')
        return Regex(builder.toString(), setOf(RegexOption.IGNORE_CASE))
    }

    private fun originOf(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val portPart =
            when {
                uri.port < 0 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
        return "$scheme://$host$portPart"
    }
}
