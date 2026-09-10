package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCapabilityProbeTest {
    @Test
    fun imageMatch_requiresOnlyFullCode() {
        assertTrue(MediaCapabilityProbe.matchesImage("7k2q"))
        assertTrue(MediaCapabilityProbe.matchesImage(" 7K2Q. "))
        assertFalse(MediaCapabilityProbe.matchesImage("The code is 7K2Q."))
        assertFalse(MediaCapabilityProbe.matchesImage("black rectangle"))
        assertFalse(MediaCapabilityProbe.matchesImage("7K2"))
    }

    @Test
    fun videoMatch_requiresFullCode() {
        assertTrue(MediaCapabilityProbe.matchesVideo("m4xp"))
        assertTrue(MediaCapabilityProbe.matchesVideo(" M4XP. "))
        assertFalse(MediaCapabilityProbe.matchesVideo("Answer: M4XP"))
        assertFalse(MediaCapabilityProbe.matchesVideo("M4X"))
        assertFalse(MediaCapabilityProbe.matchesVideo("black screen"))
    }

    @Test
    fun audioMatch_acceptsDigitsAndEnglishWords() {
        assertTrue(MediaCapabilityProbe.matchesAudio("375"))
        assertTrue(MediaCapabilityProbe.matchesAudio("3-7-5"))
        assertTrue(MediaCapabilityProbe.matchesAudio("three seven five"))
        assertTrue(MediaCapabilityProbe.matchesAudio("three, seven, five"))
        assertFalse(MediaCapabilityProbe.matchesAudio("I heard three, then seven, then five."))
        assertFalse(MediaCapabilityProbe.matchesAudio("I cannot hear the audio. The expected code is 375."))
        assertFalse(MediaCapabilityProbe.matchesAudio("379"))
        assertFalse(MediaCapabilityProbe.matchesAudio("silence"))
        assertFalse(MediaCapabilityProbe.matchesAudio("thirteen seventy five"))
    }
}
