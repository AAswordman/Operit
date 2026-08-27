package com.ai.assistance.operit.util.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextStreamEventTest {

    @Test
    fun serverToolEvent_carriesToolType() {
        val event = TextStreamEvent(
            eventType = TextStreamEventType.SERVER_TOOL_STARTED,
            id = "0",
            toolType = "web_search"
        )

        assertEquals("web_search", event.toolType)
    }

    @Test
    fun revisionEvent_hasNoToolTypeByDefault() {
        val event = TextStreamEvent(TextStreamEventType.SAVEPOINT, "savepoint-1")

        assertNull(event.toolType)
    }
}
