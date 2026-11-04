package com.ai.assistance.operit.ui.common.markdown

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.util.stream.Stream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlRenderPluginRegistryTest {
    private class FixedTagPlugin(
        override val id: String,
        private val tags: Set<String>,
    ) : XmlRenderPlugin {
        override fun supports(tagName: String): Boolean = tagName.lowercase() in tags

        override suspend fun resolve(
            context: Context,
            xmlContent: String,
            tagName: String,
            textColor: Color,
            xmlStream: Stream<String>?,
        ): XmlRenderResult = XmlRenderResult.Text(xmlContent)
    }

    @Test
    fun supportsTag_tracksRegistrationLifecycle() {
        val plugin = FixedTagPlugin(id = "test-registry-voice", tags = setOf("voice"))
        XmlRenderPluginRegistry.register(plugin)
        try {
            assertTrue(XmlRenderPluginRegistry.supportsTag("voice"))
            assertTrue(XmlRenderPluginRegistry.supportsTag("VOICE"))
            assertFalse(XmlRenderPluginRegistry.supportsTag("dsh_test"))
            assertFalse(XmlRenderPluginRegistry.supportsTag("  "))
        } finally {
            XmlRenderPluginRegistry.unregister(plugin.id)
        }

        assertFalse(XmlRenderPluginRegistry.supportsTag("voice"))
    }

    @Test
    fun supportsTag_multiplePluginsDoNotShadowEachOther() {
        val voicePlugin = FixedTagPlugin(id = "test-registry-multi-voice", tags = setOf("voice"))
        val memePlugin = FixedTagPlugin(id = "test-registry-multi-meme", tags = setOf("meme"))
        XmlRenderPluginRegistry.register(voicePlugin)
        XmlRenderPluginRegistry.register(memePlugin)
        try {
            assertTrue(XmlRenderPluginRegistry.supportsTag("voice"))
            assertTrue(XmlRenderPluginRegistry.supportsTag("meme"))
            assertFalse(XmlRenderPluginRegistry.supportsTag("sticker"))
        } finally {
            XmlRenderPluginRegistry.unregister(voicePlugin.id)
            XmlRenderPluginRegistry.unregister(memePlugin.id)
        }
    }
}
