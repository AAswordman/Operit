package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.api.speech.tts.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory.VoiceServiceType
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences.TtsProfile
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences.TtsHttpConfig
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences.VitsTtsPackageConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SpeechProfileDraftTest {
    private fun profile() = TtsProfile(
        id = "profile-a", name = "档案 A", serviceType = VoiceServiceType.HTTP_TTS,
        httpConfig = TtsHttpConfig(
            urlTemplate = "https://example.com/speech", apiKey = "example-key",
            headers = mapOf("X-Example" to "original"), httpMethod = "POST",
            requestBody = "<speak>{text}</speak>", contentType = "application/ssml+xml",
            localeTag = "zh-CN", voiceId = "voice-a", modelName = "model-a",
            responsePipeline = listOf(HttpTtsResponsePipelineStep(type = "parse_json")),
        ),
        vitsConfig = VitsTtsPackageConfig("/example/voice", "2", mapOf("length_scale" to "1.2")),
        cleanerRegexs = listOf("<[^>]+>"), speechRate = 1.25f, pitch = 0.75f,
        createdAt = 10L, updatedAt = 20L,
    )

    @Test
    fun roundTripKeepsPublishedProfileAndNonJsonBody() {
        val original = profile()
        assertEquals(original, TtsProfileDraft(original).toProfile())
    }

    @Test
    fun editedHttpAndLocalOptionsSurviveSwitchingEngine() {
        val initial = profile()
        val draft = TtsProfileDraft(
            profile = initial.copy(serviceType = VoiceServiceType.OPENAI_TTS),
            headers = """{"X-Example":"changed"}""",
            responsePipeline = """[{"type":"pick","path":"audio.url"}]""",
            options = """{"length_scale":"0.8"}""",
        )
        val saved = draft.toProfile()
        assertEquals(mapOf("X-Example" to "changed"), saved.httpConfig.headers)
        assertEquals("audio.url", saved.httpConfig.responsePipeline.single().path)
        assertEquals(mapOf("length_scale" to "0.8"), saved.vitsConfig.options)
        assertEquals(initial.httpConfig.requestBody, saved.httpConfig.requestBody)
        assertEquals(initial.speechRate, saved.speechRate, 0f)
        assertEquals(initial.pitch, saved.pitch, 0f)
        assertEquals(initial.id, saved.id)
        assertEquals(initial.createdAt, saved.createdAt)
    }

    @Test
    fun blankStructuredFieldsMeanEmptyValues() {
        val saved = TtsProfileDraft(profile(), headers = "", responsePipeline = "", options = "").toProfile()
        assertTrue(saved.httpConfig.headers.isEmpty())
        assertTrue(saved.httpConfig.responsePipeline.isEmpty())
        assertTrue(saved.vitsConfig.options.isEmpty())
    }

    @Test
    fun invalidHeadersDoNotProduceAnUpdatedProfile() {
        assertThrows(IllegalArgumentException::class.java) { TtsProfileDraft(profile(), headers = "[").toProfile() }
    }

    @Test
    fun responsePipelineMustRemainAnArrayOfSteps() {
        assertThrows(IllegalArgumentException::class.java) { TtsProfileDraft(profile(), responsePipeline = "{}").toProfile() }
    }

    @Test
    fun localOptionsMustRemainAnObject() {
        assertThrows(IllegalArgumentException::class.java) { TtsProfileDraft(profile(), options = "[]").toProfile() }
    }

    @Test
    fun invalidCleanerIsRejectedAndBlankRuleIsNotPersisted() {
        val original = profile()
        assertThrows(IllegalArgumentException::class.java) { TtsProfileDraft(original.copy(cleanerRegexs = listOf("["))).toProfile() }
        assertEquals(listOf("<[^>]+>"), TtsProfileDraft(original.copy(cleanerRegexs = listOf("", "<[^>]+>"))).toProfile().cleanerRegexs)
    }

    @Test
    fun inputArrivingDuringSaveIsWrittenBeforeFlushReturns() = runTest {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val state = SpeechProfileEditorState("old", "old") { value ->
            writes += value
            if (writes.size == 1) { started.complete(Unit); resume.await() }
            value
        }
        state.draft = "first"
        val saving = launch { assertTrue(state.save()) }
        started.await()
        state.draft = "latest"
        resume.complete(Unit)
        saving.join()
        assertEquals(listOf("first", "latest"), writes)
        assertEquals("latest", state.draft)
        assertFalse(state.pending)
        assertFalse(state.saving)
    }

    @Test
    fun separateProfileStatesNeverWriteToEachOthersProfile() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val first = SpeechProfileEditorState("old-a", "old-a") { value -> writes += "a" to value; value }
        val second = SpeechProfileEditorState("old-b", "old-b") { value -> writes += "b" to value; value }
        first.draft = "edited-a"
        second.draft = "edited-b"
        first.save()
        second.save()
        assertEquals(listOf("a" to "edited-a", "b" to "edited-b"), writes)
    }

    @Test
    fun saveEchoDoesNotRewriteRawEditingText() = runTest {
        val state = SpeechProfileEditorState("old", "old") { value: String -> value.trim() }
        state.draft = "  edited  "
        state.save()
        state.syncExternal("edited") { it }
        assertEquals("  edited  ", state.draft)
        assertFalse(state.pending)
    }

    @Test
    fun delayedOldFlowEmissionCannotReplaceTheJustSavedInput() = runTest {
        val state = SpeechProfileEditorState(
            "old", "old" to 1L,
            acceptExternal = { saved, incoming -> incoming.second >= saved.second },
        ) { value: String -> value to 2L }
        state.draft = "edited"
        state.save()
        state.syncExternal("old" to 1L) { it.first }
        assertEquals("edited", state.draft)
        assertFalse(state.pending)
        state.syncExternal("external" to 3L) { it.first }
        assertEquals("external", state.draft)
    }

    @Test
    fun externalChangesRefreshAnUneditedForm() {
        val state = SpeechProfileEditorState("old", "old") { value: String -> value }
        state.syncExternal("changed-in-tool") { it }
        assertEquals("changed-in-tool", state.draft)
        assertFalse(state.pending)
    }

    @Test
    fun externalChangesDoNotReplacePendingInput() {
        val state = SpeechProfileEditorState("old", "old") { value: String -> value }
        state.draft = "typing"
        state.syncExternal("external") { it }
        assertEquals("typing", state.draft)
        assertTrue(state.pending)
    }

    @Test
    fun cancellationKeepsTheDraftPendingForFinalFlush() = runTest {
        val state = SpeechProfileEditorState<String, String>("old", "old") { throw CancellationException("离页") }
        state.draft = "typing"
        try {
            state.save()
            fail("取消应继续向上传递")
        } catch (_: CancellationException) {
            assertEquals("typing", state.draft)
            assertTrue(state.pending)
            assertFalse(state.saving)
        }
    }
}
