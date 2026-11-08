package com.ai.assistance.operit.core.chat.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLinkBuilderStructureAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageLink_startsWithLinkTag() {
        assertTrue(MediaLinkBuilder.image(context, "id").startsWith("<link "))
    }

    @Test fun audioLink_startsWithLinkTag() {
        assertTrue(MediaLinkBuilder.audio(context, "id").startsWith("<link "))
    }

    @Test fun videoLink_startsWithLinkTag() {
        assertTrue(MediaLinkBuilder.video(context, "id").startsWith("<link "))
    }
}
