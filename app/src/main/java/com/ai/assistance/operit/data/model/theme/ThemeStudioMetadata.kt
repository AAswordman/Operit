package com.ai.assistance.operit.data.model.theme

import kotlinx.serialization.Serializable

/** 工程信息与空目录属于制作数据，随主题包保留，不参与脚本业务执行。 */
@Serializable
data class ThemeStudioMetadata(
    val name: String = "",
    val author: String = "",
    val version: String = "1.0.0",
    val description: String = "",
    val license: String = "",
    val id: String = "",
    val folders: List<String> = emptyList(),
)

/** 模式只覆盖声明的部件，公共外观与浅深色板仍保留原有结构。 */
@Serializable
data class ThemeAppearanceMode(
    val surfaces: Map<String, ThemeScriptSurface> = emptyMap(),
    val icons: Map<String, ThemeAppearanceIcon> = emptyMap(),
    val typography: ThemeAppearanceText? = null,
)

@Serializable
data class ThemeAppearanceState(
    val fillColor: String? = null,
    val contentColor: String? = null,
    val strokeColor: String? = null,
    val strokeWidth: Float? = null,
    val opacity: Float = 1f,
)

/** 模式和状态按协议声明继承公共属性。 */
fun ThemeScriptAppearance.forMode(mode: String): ThemeScriptAppearance {
    val variant = modes[mode]
    if (variant == null) return copy(modes = emptyMap())
    return copy(
        surfaces = surfaces + variant.surfaces,
        icons = icons + variant.icons,
        typography = variant.typography ?: typography,
        modes = emptyMap(),
    )
}

fun ThemeScriptSurface.forState(state: String): ThemeScriptSurface {
    val variant = states[state] ?: return this
    return copy(
        fillColor = variant.fillColor ?: fillColor,
        contentColor = variant.contentColor ?: contentColor,
        strokeColor = variant.strokeColor ?: strokeColor,
        strokeWidth = variant.strokeWidth ?: strokeWidth,
        opacity = opacity * variant.opacity,
    )
}

/** 所有资源路径与目录使用同一规则，归档与工作室不能分别解释路径。 */
object ThemeAssetPaths {
    private val segment = Regex("[\\p{L}\\p{N}_-][\\p{L}\\p{N}._ -]{0,95}")
    private val extension = Regex("[A-Za-z0-9]{1,10}")

    fun isFolder(path: String): Boolean {
        if (path.length > 160 || !path.startsWith("assets/") || path.endsWith('/')) return false
        val parts = path.split('/')
        return parts.size in 2..10 && parts.drop(1).all { segment.matches(it) && !it.contains("..") && !it.endsWith(' ') && !it.endsWith('.') }
    }

    fun isFile(path: String): Boolean {
        if (path.length > 160 || !path.startsWith("assets/") || path.endsWith('/')) return false
        val parts = path.split('/')
        return parts.size in 2..10 && parts.drop(1).all { segment.matches(it) && !it.contains("..") && !it.endsWith(' ') && !it.endsWith('.') } &&
            extension.matches(parts.last().substringAfterLast('.', ""))
    }
}
