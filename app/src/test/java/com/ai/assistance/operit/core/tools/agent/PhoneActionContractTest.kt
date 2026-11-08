package com.ai.assistance.operit.core.tools.agent

import com.ai.assistance.operit.core.config.FunctionalPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionContractTest {
    @Test fun bothLanguagesAdvertiseOnlyExecutableDoActions() {
        val commands = Regex("""do\(action="([^"]+)""" )
        for (english in listOf(false, true)) {
            val prompt = FunctionalPrompts.uiAutomationAgentPrompt(english)
            assertEquals(PhoneLocalProtocol.executableActions,
                commands.findAll(prompt).map { it.groupValues[1] }.toSet())
            assertTrue(prompt.contains("finish(message="))
            assertTrue(prompt.contains("OBSERVATION_MODE"))
            assertTrue(prompt.contains("ACCESSIBILITY_MAIN"))
        }
    }
}
