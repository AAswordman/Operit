package com.ai.assistance.operit.data.resources.models

import org.junit.Assert.*
import org.junit.Test

class SpeechModelManifestTest {
    private val files = listOf("model.onnx", "tokens.txt", "lexicon.txt").map { ModelFile(it, 1, "1".repeat(64)) }
    private fun model() = SpeechModelManifest("sample", "v1", "示例", purpose = SpeechModelPurpose.TTS, driver = ModelDriver("vits", model = "model.onnx", tokens = "tokens.txt", lexicon = "lexicon.txt"), files = files)
    @Test fun validManifestKeepsVersionedPurposeAndEnginePath() {
        assertEquals("runtime/models/local/tts/sherpa_onnx/sample/v1", model().relativeInstallPath())
    }
    @Test fun relativePathTraversalIsRejectedBeforeInstallation() {
        assertThrows(IllegalArgumentException::class.java) { model().copy(files = files + ModelFile("../outside", 1, "1".repeat(64))).validate() }
    }
    @Test fun missingDriverFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { model().copy(files = files.dropLast(1)).validate() }
    }
    @Test fun duplicateModelFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { model().copy(files = files + files.first()).validate() }
    }
    @Test fun ttsCannotBeDeclaredAsTransducer() {
        assertThrows(IllegalArgumentException::class.java) { model().copy(purpose = SpeechModelPurpose.STT).validate() }
    }
    @Test fun modelFileUrlMustUseHttps() {
        assertThrows(IllegalArgumentException::class.java) { model().copy(files = files.map { it.copy(url = "http://example.com/${it.path}") }).validate() }
    }
    @Test fun directoryIdentityCannotEscapeInstallationRoot() {
        assertThrows(IllegalArgumentException::class.java) { model().copy(id = "../sample").validate() }
    }
    @Test fun modelScopeUrlCarriesItsOwnRepositoryAndRevision() {
        val repo = ModelRepository.parse("https://modelscope.cn/models/team/project/tree/v2", ModelDownloadSource.HUGGING_FACE)
        assertEquals(ModelDownloadSource.MODELSCOPE, repo.source)
        assertEquals("team/project", repo.repository)
        assertEquals("v2", repo.revision)
    }
    @Test fun explicitRevisionOverridesUrlRevision() {
        val repo = ModelRepository.parse("https://hf-mirror.com/team/project/resolve/main/model.onnx", ModelDownloadSource.MODELSCOPE, "pinned")
        assertEquals(ModelDownloadSource.HF_MIRROR, repo.source)
        assertEquals("pinned", repo.revision)
    }
    @Test fun unsupportedRepositoryHostIsRejected() {
        assertThrows(IllegalStateException::class.java) { ModelRepository.parse("https://example.com/team/model", ModelDownloadSource.HUGGING_FACE) }
    }
}
