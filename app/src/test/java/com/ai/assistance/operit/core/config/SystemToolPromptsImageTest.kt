package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemToolPromptsImageTest {
    @Test
    fun `image parameters reflect native and backend capabilities in both languages`() {
        for (chinese in listOf(false, true)) {
            for (nativeImage in listOf(false, true)) {
                for (backendImage in listOf(false, true)) {
                    val prompt = readFilePrompt(chinese, nativeImage, backendImage)
                    val names = prompt.parametersStructured.orEmpty().map { it.name }.toSet()
                    val scenario = "chinese=$chinese native=$nativeImage backend=$backendImage"

                    assertEquals(scenario, nativeImage, "direct_image" in names)
                    assertEquals(scenario, backendImage && !nativeImage, "intent" in names)
                    assertTrue(scenario, "path" in names)
                    assertTrue(scenario, "environment" in names)
                    assertFalse(scenario, "direct_audio" in names)
                    assertFalse(scenario, "direct_video" in names)
                }
            }
        }
    }

    @Test
    fun `backend image instructions distinguish analysis from local OCR`() {
        for (chinese in listOf(false, true)) {
            val prompt = readFilePrompt(chinese, nativeImage = false, backendImage = true)
            assertTrue(prompt.description.contains("intent"))
            assertTrue(prompt.description.contains("OCR"))
            assertTrue(prompt.description.contains(if (chinese) "必须提供非空" else "must provide a non-empty"))
            assertTrue(prompt.description.contains(if (chinese) "图片和问题" else "image and your question"))
        }
    }

    @Test
    fun `native image instructions expose the image request parameter`() {
        for (chinese in listOf(false, true)) {
            val prompt = readFilePrompt(chinese, nativeImage = true, backendImage = false)
            assertTrue(prompt.description.contains("direct_image=true"))
        }
    }

    @Test
    fun `backend audio keeps intent available alongside native images`() {
        for (chinese in listOf(false, true)) {
            val prompt = readFilePrompt(chinese, nativeImage = true, backendImage = true, backendAudio = true)
            val names = prompt.parametersStructured.orEmpty().map { it.name }.toSet()
            assertTrue("direct_image" in names)
            assertTrue("intent" in names)
            assertTrue(prompt.description.contains("direct_image=true"))
            assertFalse(prompt.description.contains(if (chinese) "图片和问题" else "image and your question"))
        }
    }

    private fun readFilePrompt(
        chinese: Boolean,
        nativeImage: Boolean,
        backendImage: Boolean,
        backendAudio: Boolean = false
    ): ToolPrompt {
        val categories = if (chinese) {
            SystemToolPrompts.getAIAllCategoriesCn(
                hasBackendImageRecognition = backendImage,
                chatModelHasDirectImage = nativeImage,
                hasBackendAudioRecognition = backendAudio
            )
        } else {
            SystemToolPrompts.getAIAllCategoriesEn(
                hasBackendImageRecognition = backendImage,
                chatModelHasDirectImage = nativeImage,
                hasBackendAudioRecognition = backendAudio
            )
        }
        return categories.flatMap { it.tools }.single { it.name == "read_file" }
    }
}
