package com.ai.assistance.operit.core.chat

import java.util.concurrent.atomic.AtomicInteger

/** Execution identities follow the displayed assistant message, including after steering. */
internal class AssistantToolSequence(initialScopeId: String?) {
    var scopeId: String? = initialScopeId
        private set
    val nextIndex = AtomicInteger(0)

    fun startMessage(scopeId: String) {
        this.scopeId = scopeId
        nextIndex.set(0)
    }
}
