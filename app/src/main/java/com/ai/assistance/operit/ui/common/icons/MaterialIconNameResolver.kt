package com.ai.assistance.operit.ui.common.icons

import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.ConcurrentHashMap

object MaterialIconNameResolver {
    private val iconCache = ConcurrentHashMap<String, ImageVector>()

    fun resolveOrNull(iconName: String?): ImageVector? {
        val normalizedName = iconName?.trim().orEmpty()
        if (normalizedName.isEmpty()) {
            return null
        }
        iconCache[normalizedName]?.let { return it }
        return runCatching {
            val pascalCaseName =
                normalizedName
                    .split(Regex("[^A-Za-z0-9]+"))
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "") { segment ->
                        segment.replaceFirstChar { char -> char.uppercaseChar() }
                    }
                    .ifBlank {
                        normalizedName.replaceFirstChar { char -> char.uppercaseChar() }
                    }
            require(pascalCaseName.isNotEmpty()) {
                "icon name is invalid: $normalizedName"
            }
            MaterialFilledIconCatalog.resolve(pascalCaseName)
                ?.also { iconCache[normalizedName] = it }
        }.getOrNull()
    }

    fun resolveOrDefault(iconName: String?, fallback: ImageVector): ImageVector {
        return runCatching { resolveOrNull(iconName) }.getOrNull() ?: fallback
    }
}
