package com.ai.assistance.operit.api.chat.model

import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.util.logging.AppLogger
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GeminiModelListUrlTest {
    private var previousSystemLogging = true
    private var previousFileLogging = true
    private val providers = listOf(ApiProviderType.GOOGLE, ApiProviderType.GEMINI_GENERIC)

    @Before
    fun disableAndroidLogging() {
        previousSystemLogging = AppLogger.enableSystemLog
        previousFileLogging = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogging
        AppLogger.enableFileLogging = previousFileLogging
    }

    @Test
    fun `custom model catalogs keep the configured service and gateway path`() {
        assertRoutes(
            "https://relay.example.com/v1beta/models" to "https://relay.example.com/v1beta/models",
            "http://127.0.0.1:8080/gemini/v1beta/models/" to "http://127.0.0.1:8080/gemini/v1beta/models",
            "https://relay.example.com:8443/team/gateway" to "https://relay.example.com:8443/team/gateway/v1beta/models",
            "https://relay.example.com/" to "https://relay.example.com/v1beta/models",
            "https://relay.example.com/models/gateway/v1beta" to "https://relay.example.com/models/gateway/v1beta/models",
            "https://relay.example.com/models/gateway" to "https://relay.example.com/models/gateway/v1beta/models"
        )
    }

    @Test
    fun `generation and streaming endpoints resolve to their own catalog`() {
        assertRoutes(
            "https://relay.example.com/gemini/v1beta/models/gemini-3.8-flash-high:generateContent" to
                "https://relay.example.com/gemini/v1beta/models",
            "https://relay.example.com/gemini/v1beta/models/gemini-3.8-flash-high:streamGenerateContent?alt=sse&tenant=one" to
                "https://relay.example.com/gemini/v1beta/models?tenant=one"
        )
    }

    @Test
    fun `explicit API versions are retained`() {
        assertRoutes(
            "https://relay.example.com/gateway/v1" to "https://relay.example.com/gateway/v1/models",
            "https://relay.example.com/gateway/v1/models" to "https://relay.example.com/gateway/v1/models",
            "https://relay.example.com/gateway/v1beta/" to "https://relay.example.com/gateway/v1beta/models",
            "https://relay.example.com/gateway/v1alpha" to "https://relay.example.com/gateway/v1alpha/models"
        )
    }

    @Test
    fun `gateway query parameters and encoded path segments are retained`() {
        for (provider in providers) {
            val url = ModelListFetcher.getModelsListUrl(
                "https://relay.example.com/team%2Fone/v1beta/models?tenant=a%2Fb&key=sample-key#catalog",
                provider
            ).toHttpUrl()
            assertEquals("relay.example.com", url.host)
            assertEquals("/team%2Fone/v1beta/models", url.encodedPath)
            assertEquals("a/b", url.queryParameter("tenant"))
            assertEquals("sample-key", url.queryParameter("key"))
            assertNull(url.fragment)
        }
    }

    @Test
    fun `official Google catalogs remain on the configured version`() {
        assertRoutes(
            "https://generativelanguage.googleapis.com/v1beta/models" to
                "https://generativelanguage.googleapis.com/v1beta/models",
            "https://generativelanguage.googleapis.com/v1/models" to
                "https://generativelanguage.googleapis.com/v1/models",
            "https://generativelanguage.googleapis.com" to
                "https://generativelanguage.googleapis.com/v1beta/models"
        )
    }

    @Test
    fun `Vertex catalogs preserve region host project and location`() {
        assertRoutes(
            "https://us-central1-aiplatform.googleapis.com/v1/projects/project-a/locations/us-central1/publishers/google/models/gemini-pro:generateContent" to
                "https://us-central1-aiplatform.googleapis.com/v1/projects/project-a/locations/us-central1/publishers/google/models",
            "https://vertex-relay.example.com/proxy/v1/projects/project-a/locations/global" to
                "https://vertex-relay.example.com/proxy/v1/projects/project-a/locations/global/publishers/google/models",
            "https://vertex-relay.example.com/proxy/v1/projects/project-a/locations/global/publishers/google" to
                "https://vertex-relay.example.com/proxy/v1/projects/project-a/locations/global/publishers/google/models"
        )
    }

    @Test
    fun `Vertex text in a proxy hostname does not select a different service`() {
        assertRoutes(
            "https://vertex-relay.example.com/gateway" to
                "https://vertex-relay.example.com/gateway/v1beta/models"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an invalid endpoint is rejected instead of changing the service`() {
        ModelListFetcher.getModelsListUrl("not-an-http-endpoint", ApiProviderType.GEMINI_GENERIC)
    }

    @Test
    fun `OpenAI model lists keep their existing protocol route`() {
        assertEquals(
            "https://relay.example.com/gateway/v1/models",
            ModelListFetcher.getModelsListUrl(
                "https://relay.example.com/gateway/v1/chat/completions",
                ApiProviderType.OPENAI_GENERIC
            )
        )
    }

    private fun assertRoutes(vararg routes: Pair<String, String>) {
        for (provider in providers) {
            for ((endpoint, expected) in routes) {
                val actual = ModelListFetcher.getModelsListUrl(endpoint, provider)
                assertEquals("provider=$provider endpoint=$endpoint", expected, actual)
                if (!endpoint.contains("generativelanguage.googleapis.com")) {
                    assertFalse(actual.contains("generativelanguage.googleapis.com"))
                }
            }
        }
    }
}
