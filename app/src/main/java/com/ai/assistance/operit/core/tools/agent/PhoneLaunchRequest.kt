package com.ai.assistance.operit.core.tools.agent

/** Only an exact installed app name/package can turn a request into a launch-only task. */
internal object PhoneLaunchRequest {
    private val prefixes = listOf(
        "请帮我打开", "请帮我启动", "帮我打开", "帮我启动", "请打开", "请启动", "打开一下", "启动一下", "打开", "启动",
        "please open the ", "please launch ", "please open ", "open the ", "open ", "launch ", "start ",
    ).sortedByDescending { it.length }
    private val suffixes = listOf("的软件", "的应用", "的App", " application", " app", "软件", "应用", " App")
        .sortedByDescending { it.length }

    fun appQuery(task: String): String? {
        var text = task.substringAfterLast("进一步完成:", task)
        text = text.substringAfterLast("进一步完成：", text).trim().trimEnd('。', '.', '!', '！').trim()
        val prefix = prefixes.firstOrNull { text.startsWith(it, ignoreCase = true) } ?: return null
        var name = text.substring(prefix.length).trim()
        if (name.startsWith("名为")) name = name.substring(2).trim()
        val suffix = suffixes.firstOrNull { name.endsWith(it, ignoreCase = true) }
        if (suffix != null) name = name.dropLast(suffix.length).trim()
        name = name.trim('"', '\'', '“', '”', '「', '」').trim()
        return name.takeIf { it.isNotEmpty() && it.length <= 120 }
    }

    // A short relative class does not identify its owning app. Only fully qualified classes or
    // flattened components can confirm foreground identity without reading a hierarchy.
    fun foregroundComponent(activity: String, packageName: String): String? {
        val name = activity.trim()
        if ('/' in name) return name.takeIf { it.substringBefore('/') == packageName }
        if ('.' !in name || name.startsWith('.')) return null
        return "$packageName/$name"
    }

    fun matchesInstalledApp(task: String, label: String, packageName: String): Boolean {
        val name = appQuery(task) ?: return false
        return name.equals(label, ignoreCase = true) || name == packageName
    }
}

internal data class PhoneLaunchTarget(val packageName: String, val label: String)