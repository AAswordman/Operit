package com.ai.assistance.operit.core.tools.agent

import org.junit.Assert.*
import org.junit.Test

class PhoneObservationHistoryTest {
    @Test fun oldTextAndTaskArePreservedWhileOldSelectorsAreOmitted() {
        val history = mutableListOf<Pair<String, String>>()
        val tracker = PhoneObservationHistory()
        for (i in 1..3) {
            val state = PhoneUiSnapshot(i.toLong(), "com.example.app", listOf(
                PhoneUiNode("com.example.app", resourceId = "id$i", text = "result$i"),
            ))
            val payload = "[UI_STATE] ${state.toPrompt()}"
            tracker.record(history.size, payload)
            history += "user" to "User task\n$payload\n[ACTION_RESULT] accepted"
            history += "assistant" to "action $i"
        }
        val compact = tracker.forModel(history)
        assertTrue(compact[0].second.contains("User task"))
        assertTrue(compact[0].second.contains("result1"))
        assertTrue(compact[0].second.contains("[ACTION_RESULT] accepted"))
        assertFalse(compact[0].second.contains("\"id\":\"id1\""))
        assertTrue(compact[2].second.contains("\"id\":\"id2\""))
        assertTrue(history[0].second.contains("\"id\":\"id1\""))
    }

    @Test fun taskTextThatLooksLikeAMarkerIsNotCompactedWithoutARecordedObservation() {
        val history = listOf("user" to "Explain [UI_STATE] without operating the phone")
        assertEquals(history, PhoneObservationHistory().forModel(history))
    }
}
