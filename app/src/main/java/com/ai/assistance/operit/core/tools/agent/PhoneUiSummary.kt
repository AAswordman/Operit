package com.ai.assistance.operit.core.tools.agent

import org.json.JSONArray
import org.json.JSONObject

/** A display-independent summary; native nodes and complete selectors remain local. */
internal data class PhoneUiSummary(
    val candidates: List<PhoneUiNode>,
    val shown: List<PhoneUiNode>,
    val payload: String,
) {
    companion object {
        private const val CHARACTER_BUDGET = 3000
        private const val LABEL_LIMIT = 60

        fun build(state: PhoneUiSnapshot, task: String): PhoneUiSummary {
            val numbered = state.nodes.mapIndexed { index, node -> node.copy(ref = index + 1) }
            val visible = numbered.filter { it.packageName == state.packageName && state.isVisible(it) }
            val parentsWithControls = visible.filter { it.clickable || it.editable }.mapNotNull { it.parentRef }.toHashSet()
            val owners = mutableMapOf<Int, Int?>()
            val merged = mutableMapOf<Int, LinkedHashSet<String>>()
            val consumed = mutableSetOf<Int>()
            for (node in visible) {
                val parentOwner = node.parentRef?.let { owners[it] }
                val interactive = node.clickable || node.editable || node.scrollable
                owners[node.ref] = when {
                    node.editable -> node.ref
                    node.scrollable -> null
                    node.clickable -> node.ref
                    else -> parentOwner
                }
                val words = words(node)
                if (!interactive && parentOwner != null && words.isNotEmpty()) {
                    val labels = merged.getOrPut(parentOwner) { linkedSetOf() }
                    if (labels.size < 8) labels += words
                    consumed += node.ref
                }
            }
            val candidates = visible.mapNotNull { node ->
                if (node.ref in consumed) return@mapNotNull null
                val labels = linkedSetOf<String>()
                words(node).takeIf { it.isNotEmpty() }?.let { labels += it }
                merged[node.ref]?.let { labels += it }
                if (labels.isEmpty() && node.hint.isNotBlank()) labels += node.hint.trim()
                val label = if (node.password) "" else labels.joinToString(" · ")
                val interactive = node.clickable || node.editable || node.scrollable
                if (!interactive && label.isEmpty() && !node.focused && !node.selected && !node.checked) return@mapNotNull null
                // A shell around independent child controls does not need a second empty entry.
                if (node.clickable && !node.editable && !node.scrollable && label.isEmpty() && node.ref in parentsWithControls) return@mapNotNull null
                val clipped = if (state.screenBounds != null) node.bounds?.intersect(state.screenBounds) else node.bounds
                node.copy(bounds = clipped, modelLabel = label)
            }
            // Only the displayed list is deduplicated. Resolution keeps every candidate so two
            // indistinguishable native controls cannot become an apparently unique target.
            val displayCandidates = candidates.distinctBy { it.copy(ref = 0) }
            val goal = task.lowercase()
            val terms = listOf(
                listOf("搜索", "search"), listOf("消息", "message"), listOf("评论", "comment"),
                listOf("输入", "input", "type"), listOf("返回", "back"),
            ).filter { group -> group.any { it in goal } }.flatten()
            fun zone(node: PhoneUiNode): String {
                val frame = state.screenBounds ?: return "content"
                val y = node.bounds?.centerY ?: return "content"
                return when {
                    y < frame.top + (frame.bottom - frame.top) / 5 -> "top"
                    y > frame.top + (frame.bottom - frame.top) * 4 / 5 -> "bottom"
                    else -> "content"
                }
            }
            fun rank(node: PhoneUiNode): Int {
                val label = (node.modelLabel + " " + node.resourceId).lowercase()
                return when {
                    node.focused && node.editable -> 0
                    terms.any { it in label } -> 1
                    node.editable -> 2
                    node.clickable && zone(node) != "content" -> 3
                    node.clickable || node.scrollable || node.selected -> 4
                    else -> 5
                }
            }
            fun item(node: PhoneUiNode): JSONObject = JSONObject().apply {
                put("ref", node.ref)
                put("role", when {
                    node.editable -> "input"
                    node.scrollable -> "list"
                    node.className.contains("Tab") -> "tab"
                    node.clickable -> "button"
                    else -> "text"
                })
                put("zone", zone(node))
                if (node.resourceId.isNotBlank()) put("id", node.resourceId)
                if (node.editable || node.resourceId.isBlank()) put("class", node.className)
                if (!node.password) {
                    if (node.text.isNotEmpty()) put("text", node.text.take(LABEL_LIMIT))
                    if (node.description.isNotEmpty() && node.description != node.text) put("desc", node.description.take(LABEL_LIMIT))
                    if (node.modelLabel.isNotEmpty() && node.modelLabel != node.text && node.modelLabel != node.description) {
                        put("label", node.modelLabel.take(LABEL_LIMIT))
                        if (node.modelLabel.length > LABEL_LIMIT) put("label_truncated", true)
                    }
                    if (node.text.length > LABEL_LIMIT) put("text_truncated", true)
                    if (node.description.length > LABEL_LIMIT) put("desc_truncated", true)
                }
                if (node.clickable) put("clickable", true)
                if (node.editable) put("editable", true)
                if (node.focused) put("focused", true)
                if (node.selected) put("selected", true)
                if (node.checked) put("checked", true)
                if (!node.enabled) put("enabled", false)
                if (node.password) put("password", true)
            }
            // Select by useful role/goal before ordering by position. A long feed cannot consume
            // the entire budget before the search entry, input or navigation is considered.
            val payload = JSONObject().put("screen", state.revision).put("package", state.packageName)
                .put("nodes", JSONArray()).put("read_nodes", state.nodes.size).put("candidates", candidates.size)
                .put("shown", 0).put("truncated", true)
                .put("age_ms", (System.nanoTime() / 1_000_000 - state.capturedAtMs).coerceAtLeast(0L))
            // Reserve the exact envelope plus growth from shown=0 to shown=120 and true to false.
            var remaining = CHARACTER_BUDGET - payload.toString().length - 3
            val chosen = mutableListOf<Pair<PhoneUiNode, JSONObject>>()
            for (node in displayCandidates.sortedWith(compareBy<PhoneUiNode>({ rank(it) }, { it.bounds?.top ?: 0 }, { it.bounds?.left ?: 0 }))) {
                val json = item(node)
                val cost = json.toString().length + if (chosen.isEmpty()) 0 else 1
                if (cost > remaining || chosen.size >= 120) continue
                chosen += node to json
                remaining -= cost
            }
            val ordered = chosen.sortedWith(compareBy({ it.first.bounds?.top ?: 0 }, { it.first.bounds?.left ?: 0 }))
            val items = JSONArray()
            ordered.forEach { items.put(it.second) }
            payload.put("nodes", items).put("shown", ordered.size).put("truncated", ordered.size < candidates.size)
            return PhoneUiSummary(candidates, ordered.map { it.first }, payload.toString())
        }

        private fun words(node: PhoneUiNode): String = when {
            node.password -> ""
            node.text.isNotBlank() -> node.text.trim()
            node.description.isNotBlank() -> node.description.trim()
            else -> ""
        }
    }
}
