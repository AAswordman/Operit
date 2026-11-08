package com.ai.assistance.operit.core.chat.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLinkBuilderFormatAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageLink_containsLocalizedLabel() {
        assertTrue(MediaLinkBuilder.image(context, "id1").contains(">图片</link>"))
    }

    @Test fun audioLink_containsLocalizedLabel() {
        assertTrue(MediaLinkBuilder.audio(context, "id1").contains(">音频</link>"))
    }

    @Test fun videoLink_containsLocalizedLabel() {
        assertTrue(MediaLinkBuilder.video(context, "id1").contains(">视频</link>"))
    }
}
