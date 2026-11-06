package com.ai.assistance.operit.ui.features.chat.historytree

/** Merge newly visible rows without resetting a drag's optimistic order or scanning it per row. */
internal fun <T> mergeHistoryDragOrder(
    optimistic: List<T>,
    canonical: List<T>,
    keyOf: (T) -> String,
): List<T> {
    val visibleKeys = canonical.mapTo(hashSetOf(), keyOf)
    val existing = optimistic.filter { keyOf(it) in visibleKeys }
    val existingKeys = existing.mapTo(hashSetOf(), keyOf)
    val additions = mutableMapOf<String?, MutableList<T>>()
    var predecessor: String? = null
    canonical.forEach { item ->
        val key = keyOf(item)
        if (key in existingKeys) {
            predecessor = key
        } else {
            additions.getOrPut(predecessor) { mutableListOf() }.add(item)
        }
    }
    return buildList {
        addAll(additions[null].orEmpty())
        existing.forEach { item ->
            add(item)
            addAll(additions[keyOf(item)].orEmpty())
        }
    }
}
