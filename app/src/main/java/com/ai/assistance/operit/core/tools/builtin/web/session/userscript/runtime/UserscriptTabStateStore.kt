package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.runtime

import java.util.concurrent.ConcurrentHashMap

internal class UserscriptTabStateStore {
    private val stateByScript = ConcurrentHashMap<Long, ConcurrentHashMap<String, String>>()

    fun getTab(
        scriptId: Long,
        sessionId: String
    ): String = stateByScript[scriptId]?.get(sessionId) ?: "{}"

    fun saveTab(
        scriptId: Long,
        sessionId: String,
        stateJson: String
    ) {
        stateByScript.getOrPut(scriptId) { ConcurrentHashMap() }[sessionId] = stateJson
    }

    fun getTabs(scriptId: Long): Map<String, String> =
        stateByScript[scriptId]?.toMap().orEmpty()

    fun removeSession(sessionId: String) {
        stateByScript.values.forEach { tabs -> tabs.remove(sessionId) }
        stateByScript.entries.removeIf { it.value.isEmpty() }
    }

    fun clearSession(sessionId: String) = removeSession(sessionId)
}
