package com.ai.assistance.operit.core.chat.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLinkBuilderIdAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageLink_supportsDashedId() {
        assertTrue(MediaLinkBuilder.image(context, "img-1").contains("img-1"))
    }

    @Test fun audioLink_supportsDashedId() {
        assertTrue(MediaLinkBuilder.audio(context, "aud-1").contains("aud-1"))
    }

    @Test fun videoLink_supportsDashedId() {
        assertTrue(MediaLinkBuilder.video(context, "vid-1").contains("vid-1"))
    }
}
