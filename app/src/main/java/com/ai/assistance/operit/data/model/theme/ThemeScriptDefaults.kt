package com.ai.assistance.operit.data.model.theme

/** 新工程和普通字体窗口共用入口，默认源码保持四空格缩进。 */
object ThemeScriptDefaults {
    val SOURCE = """
        module.exports = {
            apiVersion: 1,
            render(context) {
                return context.appearance;
            }
        };
    """.trimIndent()
}
