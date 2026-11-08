package com.ai.assistance.operit.core.chat.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceMessageStateTest {
    @Test
    fun silenceAtStartEndsAtThreeSecondsWithoutSpeech() {
        val gate = VoiceSilenceGate(1000)
        gate.observe(false, 3999)
        assertFalse(gate.shouldFinish(3999))
        assertTrue(gate.shouldFinish(4000))
        assertFalse(gate.heardSpeech)
    }

    @Test
    fun speechRestartsSilenceDeadline() {
        val gate = VoiceSilenceGate(1000)
        gate.observe(true, 3500)
        gate.observe(false, 6499)
        assertFalse(gate.shouldFinish(6499))
        assertTrue(gate.shouldFinish(6500))
        assertTrue(gate.heardSpeech)
    }

    @Test
    fun continuousSpeechIsNotStoppedAfterThreeSeconds() {
        val gate = VoiceSilenceGate(0)
        for (time in 0L..10000L step 500) {
            gate.observe(true, time)
            assertFalse(gate.shouldFinish(time))
        }
        assertFalse(gate.shouldFinish(12999))
        assertTrue(gate.shouldFinish(13000))
    }

    @Test
    fun emptyComposerEnablesVoice() {
        assertTrue(canStartVoiceMessage("", 0, false, false))
        assertTrue(canStartVoiceMessage("   ", 0, false, false))
    }

    @Test
    fun anyAttachmentTextReplyOrActiveRequestDisablesVoice() {
        assertFalse(canStartVoiceMessage("文字", 0, false, false))
        assertFalse(canStartVoiceMessage("", 1, false, false))
        assertFalse(canStartVoiceMessage("", 0, true, false))
        assertFalse(canStartVoiceMessage("", 0, false, true))
        assertFalse(canStartVoiceMessage("文字", 2, true, true))
    }
}
