package com.ai.assistance.operit.core.chat.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLinkBuilderAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageLink_usesImageTypeMarkup() {
        assertEquals("<link type=\"image\" id=\"img1\">图片</link>", MediaLinkBuilder.image(context, "img1"))
    }

    @Test fun audioLink_usesAudioTypeMarkup() {
        assertEquals("<link type=\"audio\" id=\"aud1\">音频</link>", MediaLinkBuilder.audio(context, "aud1"))
    }

    @Test fun videoLink_usesVideoTypeMarkup() {
        assertEquals("<link type=\"video\" id=\"vid1\">视频</link>", MediaLinkBuilder.video(context, "vid1"))
    }

    @Test fun imageLink_embedsRequestedId() {
        assertTrue(MediaLinkBuilder.image(context, "abc-123").contains("id=\"abc-123\""))
    }

    @Test fun audioLink_embedsRequestedId() {
        assertTrue(MediaLinkBuilder.audio(context, "abc-123").contains("id=\"abc-123\""))
    }

    @Test fun videoLink_embedsRequestedId() {
        assertTrue(MediaLinkBuilder.video(context, "abc-123").contains("id=\"abc-123\""))
    }

    @Test fun imageLink_supportsNumericId() {
        assertTrue(MediaLinkBuilder.image(context, "42").contains("id=\"42\""))
    }

    @Test fun audioLink_supportsNumericId() {
        assertTrue(MediaLinkBuilder.audio(context, "42").contains("id=\"42\""))
    }

    @Test fun videoLink_supportsNumericId() {
        assertTrue(MediaLinkBuilder.video(context, "42").contains("id=\"42\""))
    }

    @Test fun imageLink_keepsXmlStructureStable() {
        assertTrue(MediaLinkBuilder.image(context, "img1").startsWith("<link "))
    }

    @Test fun audioLink_keepsXmlStructureStable() {
        assertTrue(MediaLinkBuilder.audio(context, "aud1").endsWith("</link>"))
    }

    @Test fun videoLink_keepsXmlStructureStable() {
        assertTrue(MediaLinkBuilder.video(context, "vid1").contains(">视频</link>"))
    }
}
