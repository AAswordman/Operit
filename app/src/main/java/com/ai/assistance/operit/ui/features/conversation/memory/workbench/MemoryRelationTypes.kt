package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import androidx.annotation.StringRes
import com.ai.assistance.operit.R
import java.util.Locale

internal val memoryRelationCodes = listOf("USES", "INVOLVES", "DEPENDS_ON", "RELATED_TO", "FOLLOWS", "EXPLAINS", "PART_OF", "UPDATES", "CAUSES", "CORRECTS")

/** 显示名称可以本地化，存储中的类型和大小写保持原样。 */
@StringRes
internal fun memoryRelationName(code: String): Int = when (code.uppercase(Locale.ROOT)) {
    "USES" -> R.string.memory_relation_uses
    "INVOLVES" -> R.string.memory_relation_involves
    "DEPENDS_ON" -> R.string.memory_relation_depends_on
    "RELATED_TO", "RELATED" -> R.string.memory_relation_related_to
    "FOLLOWS" -> R.string.memory_relation_follows
    "EXPLAINS" -> R.string.memory_relation_explains
    "PART_OF" -> R.string.memory_relation_part_of
    "UPDATES" -> R.string.memory_relation_updates
    "CAUSES" -> R.string.memory_relation_causes
    "CORRECTS" -> R.string.memory_relation_corrects
    else -> R.string.memory_relation_existing
}

internal fun memoryRelationOptions(existing: String): List<String> =
    if (existing in memoryRelationCodes) memoryRelationCodes else listOf(existing) + memoryRelationCodes