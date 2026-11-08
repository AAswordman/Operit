package com.ai.assistance.operit.ui.features.automation.terminal

/** 快捷键配置使用可读转义，写入 PTY 前还原控制字符。 */
internal fun decodeTerminalShortcut(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '\\' && index + 1 < value.length) {
            val escaped = when (value[index + 1]) {
                'e' -> '\u001b'
                't' -> '\t'
                'n' -> '\n'
                'r' -> '\r'
                '\\' -> '\\'
                else -> null
            }
            if (escaped != null) {
                append(escaped)
                index += 2
                continue
            }
        }
        append(char)
        index++
    }
}

internal fun applyTerminalModifiers(input: String, ctrl: Boolean, alt: Boolean): String =
    com.ai.assistance.operit.terminal.view.canvas.input.applyTerminalModifiers(input, ctrl, alt)
