package com.ai.assistance.operit.data.theme.script

/** 独立动作按钮未声明外观时继承共用容器；显式空声明可保持原生按钮外观。 */
fun ThemeScriptAppearance.actionButtonSurface(id: String): ThemeScriptSurface? {
    require(id in ThemeScriptContract.actionButtonIds)
    return if (id in surfaces) surfaces.getValue(id) else surfaces["iconButton"]
}

/** 清除当前明暗模式的独立容器，同时保留另一模式及图标素材。 */
fun ThemeScriptAppearance.clearActionButtonSurface(id: String, mode: String): ThemeScriptAppearance {
    require(id in ThemeScriptContract.actionButtonIds && mode in setOf("light", "dark"))
    val variants = modes.toMutableMap()
    for (name in listOf("light", "dark")) {
        val variant = variants[name] ?: ThemeAppearanceMode()
        val local = variant.surfaces.toMutableMap()
        if (name == mode) {
            local.remove(id)
        } else if (id !in local && id in surfaces) {
            local[id] = surfaces.getValue(id)
        }
        variants[name] = variant.copy(surfaces = local)
    }
    return copy(surfaces = surfaces - id, modes = variants)
}
