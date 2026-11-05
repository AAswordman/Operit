package com.ai.assistance.operit.core.tools.agent

import org.json.JSONArray
import org.json.JSONObject

/** Keeps recent selectors, and retains older screen text without resending every old node attribute. */
internal class PhoneObservationHistory {
    private val payloads = linkedMapOf<Int, String>()

    fun record(messageIndex: Int, stateLine: String) {
        require(stateLine.startsWith("[UI_STATE] "))
        payloads[messageIndex] = stateLine
    }

    fun reset() = payloads.clear()

    fun forModel(history: List<Pair<String, String>>): List<Pair<String, String>> {
        val compact = payloads.keys.toList().dropLast(2).toSet()
        return history.mapIndexed { index, message ->
            if (index !in compact) message else {
                val payload = requireNotNull(payloads[index])
                val state = JSONObject(payload.removePrefix("[UI_STATE] "))
                val oldText = JSONArray()
                val nodes = state.getJSONArray("nodes")
                for (i in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(i)
                    val words = JSONObject()
                    for (key in listOf("text", "desc", "checked", "selected", "focused", "password")) {
                        if (node.has(key)) words.put(key, node.get(key))
                    }
                    if (words.length() > 0) oldText.put(words)
                }
                val replacement = "[EARLIER_SCREEN_TEXT] " + JSONObject()
                    .put("screen", state.getLong("screen")).put("package", state.getString("package"))
                    .put("content", oldText).put("truncated", state.getBoolean("truncated"))
                message.first to message.second.replace(payload, replacement)
            }
        }
    }
}
