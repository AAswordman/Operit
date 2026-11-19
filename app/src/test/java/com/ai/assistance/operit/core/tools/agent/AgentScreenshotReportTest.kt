package com.ai.assistance.operit.core.tools.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the virtual-display screenshot contract from issue #1371: the agent's screenshot comes
 * from the display it drives, and a failed virtual-display capture is reported as a failure instead
 * of being replaced by a main-screen picture.
 */
class AgentScreenshotReportTest {

    @Test
    fun virtualDisplayDiagnostic_namesAgentAndDisplay() {
        val text = virtualDisplayScreenshotDiagnostic(
            agentId = "agent-abc",
            displayId = 7,
            reason = "Shower returned no frame for displayId=7"
        )

        assertTrue(text.contains("agentId=agent-abc"))
        assertTrue(text.contains("displayId=7"))
        assertTrue(text.contains("Shower returned no frame for displayId=7"))
    }

    @Test
    fun virtualDisplayDiagnostic_statesMainDisplayWasNotCaptured() {
        val text = virtualDisplayScreenshotDiagnostic(
            agentId = "agent-abc",
            displayId = 7,
            reason = "no frame returned"
        )

        // The model must not treat this as a usable image of its own screen.
        assertTrue(text.contains("main display was NOT captured"))
    }

    @Test
    fun virtualDisplayDiagnostic_marksUnboundDisplayExplicitly() {
        val text = virtualDisplayScreenshotDiagnostic(
            agentId = "agent-abc",
            displayId = null,
            reason = "no Shower display is bound to this agent"
        )

        assertTrue(text.contains("displayId=unbound"))
        assertFalse(text.contains("displayId=null"))
    }

    @Test
    fun buildScreenInfo_embedsImageLinkWhenCaptureSucceeded() {
        val info = buildScreenInfo(AgentScreenshot(link = "<link type=\"image\" id=\"img-1\"></link>"))

        assertTrue(info.contains("[SCREENSHOT]"))
        assertTrue(info.contains("img-1"))
        assertFalse(info.contains("UNAVAILABLE"))
    }

    @Test
    fun buildScreenInfo_reportsDiagnosticInsteadOfImageOnFailure() {
        val info = buildScreenInfo(
            AgentScreenshot(link = null, diagnostic = "Virtual display capture failed (agentId=a1, displayId=7)")
        )

        assertTrue(info.contains("[SCREENSHOT UNAVAILABLE]"))
        assertTrue(info.contains("displayId=7"))
        assertFalse(info.contains("[SCREENSHOT] Below"))
    }

    @Test
    fun buildScreenInfo_fallsBackToPlainMessageWithoutImageOrDiagnostic() {
        val info = buildScreenInfo(AgentScreenshot(link = null))

        assertEquals("No screenshot available for this step.", info)
    }
}
