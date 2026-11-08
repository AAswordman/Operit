package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.api.speech.tts.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences.TtsProfile
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 保留原始编辑文本，校验通过后才投影为已发布的档案格式。 */
internal data class TtsProfileDraft(
    val profile: TtsProfile,
    val headers: String = Json.encodeToString(profile.httpConfig.headers),
    val responsePipeline: String = HttpTtsResponsePipelineStep.encodeList(profile.httpConfig.responsePipeline),
    val options: String = Json.encodeToString(profile.vitsConfig.options),
) {
    fun toProfile(): TtsProfile = profile.copy(
        httpConfig = profile.httpConfig.copy(
            headers = parseStringMap(headers),
            responsePipeline = HttpTtsResponsePipelineStep.parseList(responsePipeline),
        ),
        vitsConfig = profile.vitsConfig.copy(options = parseStringMap(options)),
        cleanerRegexs = profile.cleanerRegexs.filter(String::isNotBlank).onEach { Regex(it) },
    )
}

internal fun parseStringMap(text: String): Map<String, String> =
    if (text.isBlank()) emptyMap() else Json.decodeFromString<Map<String, String>>(text)

/** 每个档案拥有自己的草稿和写锁，保存回执不会覆盖用户继续输入的内容。 */
internal class SpeechProfileEditorState<D, P>(
    initial: D,
    initialModel: P,
    private val acceptExternal: (P, P) -> Boolean = { _, _ -> true },
    private val persist: suspend (D) -> P,
) {
    var draft by mutableStateOf(initial)
    private var savedDraft by mutableStateOf(initial)
    private var savedModel = initialModel
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val pending: Boolean get() = draft != savedDraft
    private val mutex = Mutex()

    fun syncExternal(model: P, buildDraft: (P) -> D) {
        if (!pending && !saving && model != savedModel && acceptExternal(savedModel, model)) {
            val updated = buildDraft(model)
            draft = updated
            savedDraft = updated
            savedModel = model
            error = null
        }
    }

    suspend fun save(): Boolean = mutex.withLock {
        if (!pending) return@withLock true
        saving = true
        try {
            while (pending) {
                val snapshot = draft
                savedModel = persist(snapshot)
                savedDraft = snapshot
            }
            error = null
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("SpeechProfileEditor", "保存语音配置档案失败", error)
            this.error = error.localizedMessage ?: error.javaClass.simpleName
            false
        } finally {
            saving = false
        }
    }
}
