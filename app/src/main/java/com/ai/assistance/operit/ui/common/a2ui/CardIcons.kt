package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.common.icons.LucideIconData
import kotlinx.serialization.json.*

/** One catalog for cards, Flow, inline icons and badges. Old names keep their original artwork. */
internal object CardIcons {
    private val cache = object : LinkedHashMap<String, ImageVector>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageVector>?) = size > 128
    }
    fun contains(name: String?): Boolean =
        name != null && (name in cardFlowIcons || name.removePrefix("lucide:") in LucideIconData.entries)

    fun get(name: String?): ImageVector? {
        if (name == null) return null
        cardFlowIcons[name]?.let { return it }
        val key = name.removePrefix("lucide:")
        val encoded = LucideIconData.entries[key] ?: return null
        return synchronized(cache) {
            cache[key] ?: run {
                val paths = encoded.split('\t', limit = 3)
                val builder = ImageVector.Builder("lucide:$key", 24.dp, 24.dp, 24f, 24f)
                paths.take(2).forEachIndexed { index, group ->
                    group.split('|').filter(String::isNotBlank).forEach { path -> builder.addPath(
                        pathData = PathParser().parsePathString(path).toNodes(),
                        fill = if (index == 1) SolidColor(Color.Black) else null,
                        stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                    ) }
                }
                builder.build().also { cache[key] = it }
            }
        }
    }

    fun search(query: String, limit: Int, offset: Int): String {
        val needle = query.trim().lowercase().take(80).removePrefix("lucide:")
        val terms = needle.split(Regex("[\\s_-]+")).filter(String::isNotEmpty)
        val matches = LucideIconData.entries.filter { (name, data) ->
            val keywords = name.replace('-', ' ') + " " + data.substringAfterLast('\t')
            terms.all { it in keywords }
        }.keys.sortedWith(compareBy<String> { if (it == needle) 0 else 1 }.thenBy { it })
        val start = offset.coerceIn(0, matches.size)
        val items = matches.drop(start).take(limit.coerceIn(1, 100))
        return buildJsonObject {
            put("library", "Lucide")
            put("version", LucideIconData.VERSION)
            put("total", matches.size)
            put("icons", JsonArray(items.map { JsonPrimitive("lucide:$it") }))
            put("next_offset", if (start + items.size < matches.size) JsonPrimitive(start + items.size) else JsonNull)
        }.toString()
    }
}
