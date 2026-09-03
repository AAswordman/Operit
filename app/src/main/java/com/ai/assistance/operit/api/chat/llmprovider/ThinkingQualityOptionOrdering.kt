package com.ai.assistance.operit.api.chat.llmprovider

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Keeps known thinking levels in the order used by the quality slider. */
internal object ThinkingQualityOptionOrdering {
    private data class OrderKey(
        val group: Int,
        val rank: Double,
        val originalIndex: Int,
    )

    private data class IndexedOption(
        val option: ThinkingQualityOption,
        val key: OrderKey,
    )

    private data class IndexedJsonOption(
        val option: JSONObject,
        val key: OrderKey,
    )

    private val effortRanks = mapOf(
        "minimal" to 0,
        "low" to 1,
        "medium" to 2,
        "high" to 3,
        "xhigh" to 4,
        "max" to 5,
    )

    fun sort(options: List<ThinkingQualityOption>): List<ThinkingQualityOption> =
        options.mapIndexed { index, option ->
            IndexedOption(option, orderKey(option.id, option.wireValue, index))
        }.sortedWith(
            compareBy<IndexedOption> { it.key.group }
                .thenBy { it.key.rank }
                .thenBy { it.key.originalIndex }
        ).map { it.option }

    fun sortJsonOptions(options: JSONArray): JSONArray {
        val indexed = buildList {
            for (index in 0 until options.length()) {
                options.optJSONObject(index)?.let { option ->
                    add(IndexedJsonOption(option, orderKey(option.optString("id"), option.opt("value"), index)))
                }
            }
        }
        return JSONArray().apply {
            indexed.sortedWith(
                compareBy<IndexedJsonOption> { it.key.group }
                    .thenBy { it.key.rank }
                    .thenBy { it.key.originalIndex }
            ).forEach { put(JSONObject(it.option.toString())) }
        }
    }

    fun isDisabled(option: ThinkingQualityOption): Boolean =
        option.id.equals("off", ignoreCase = true) ||
            option.id.equals("none", ignoreCase = true) ||
            (option.wireValue as? ThinkingQualityWireValue.Text)
                ?.value
                ?.equals("none", ignoreCase = true) == true

    private fun orderKey(id: String, wireValue: ThinkingQualityWireValue, originalIndex: Int): OrderKey {
        val value = when (wireValue) {
            is ThinkingQualityWireValue.Text -> wireValue.value
            is ThinkingQualityWireValue.Number -> wireValue.value.toString()
            ThinkingQualityWireValue.Omitted -> null
        }
        return orderKey(id, value, originalIndex)
    }

    private fun orderKey(id: String, value: Any?, originalIndex: Int): OrderKey {
        val normalizedId = id.trim().lowercase(Locale.US)
        val normalizedValue = (value as? String)?.trim()?.lowercase(Locale.US)
        if (normalizedId == "off" || normalizedId == "none" || normalizedValue == "none") {
            return OrderKey(group = 0, rank = 0.0, originalIndex = originalIndex)
        }

        val numericValue = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
        if (numericValue != null) {
            return OrderKey(group = 1, rank = numericValue, originalIndex = originalIndex)
        }

        val effortRank = effortRanks[normalizedId] ?: normalizedValue?.let(effortRanks::get)
        if (effortRank != null) {
            return OrderKey(group = 2, rank = effortRank.toDouble(), originalIndex = originalIndex)
        }

        return OrderKey(group = 3, rank = 0.0, originalIndex = originalIndex)
    }
}
