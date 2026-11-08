package com.ai.assistance.operit.api.speech.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.ai.assistance.operit.api.speech.stt.SttService
import com.ai.assistance.operit.api.speech.audio.SpeechPrerollStore
import com.ai.assistance.operit.data.resources.models.SpeechModelPurpose
import com.ai.assistance.operit.util.logging.AppLogger
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import kotlin.math.sqrt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.api.speech.runtime.SherpaModelSession

/** 在线流式模型在本机解码，同时支持已有录音的转写。 */
@SuppressLint("MissingPermission")
class SherpaOnnxSpeechProvider(private val context: Context, private val modelKey: String) : SttService {
    private val commands = Mutex()
    private val nativeLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: OnlineRecognizer? = null
    private var session: SherpaModelSession? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var stopRequested = false
    @Volatile private var discard = false
    @Volatile private var closed = false
    private var recording: Job? = null
    private val initialized = MutableStateFlow(false)
    private val state = MutableStateFlow(SttService.RecognitionState.UNINITIALIZED)
    private val result = MutableStateFlow(SttService.RecognitionResult(""))
    private val errors = MutableStateFlow(SttService.RecognitionError(0, ""))
    private val volume = MutableStateFlow(0f)
    override val isInitialized = initialized.asStateFlow()
    override val recognitionStateFlow = state.asStateFlow()
    override val recognitionResultFlow = result.asStateFlow()
    override val recognitionErrorFlow = errors.asStateFlow()
    override val volumeLevelFlow = volume.asStateFlow()
    override val isRecognizing get() = state.value == SttService.RecognitionState.RECOGNIZING
    override val currentState get() = state.value

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        synchronized(nativeLock) {
            check(!closed) { "识别服务已释放" }
            if (initialized.value) return@synchronized true
            try {
                val created = SherpaModelSession(context, modelKey, SpeechModelPurpose.STT)
                try { engine = OnlineRecognizer(config = created.sttConfig()); session = created } catch (failure: Throwable) { created.close(); throw failure }
                initialized.value = true
                state.value = SttService.RecognitionState.IDLE
                true
            } catch (failure: Exception) { report(failure); false }
        }
    }
    override suspend fun startRecognition(languageCode: String, continuousMode: Boolean, partialResults: Boolean, audioSource: Int): Boolean = commands.withLock {
        if (recording?.isActive == true || currentState == SttService.RecognitionState.PROCESSING) return@withLock false
        if (!initialize()) return@withLock false
        state.value = SttService.RecognitionState.PREPARING
        result.value = SttService.RecognitionResult("")
        errors.value = SttService.RecognitionError(0, "")
        stopRequested = false
        discard = false
        try {
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minimum > 0) { "设备不支持录音格式" }
            val audio = AudioRecord(audioSource, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 8192))
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "录音初始化失败" }
            audio.startRecording()
            state.value = SttService.RecognitionState.RECOGNIZING
            recording = scope.launch {
                var stream: OnlineStream? = null
                try {
                    val recognizer = checkNotNull(engine)
                    stream = synchronized(nativeLock) { recognizer.createStream() }
                    val currentStream = checkNotNull(stream)
                    SpeechPrerollStore.consumePending()?.let { pcm -> feed(recognizer, currentStream, FloatArray(pcm.size) { pcm[it] / 32768f }) }
                    val buffer = ShortArray(1600)
                    var committed = ""
                    while (isActive && !stopRequested) {
                        val count = audio.read(buffer, 0, buffer.size)
                        if (count < 0 && !stopRequested) error("录音读取失败：$count")
                        if (count <= 0) break
                        SpeechPrerollStore.appendPcm(buffer, count)
                        volume.value = sqrt((0 until count).sumOf { val v = buffer[it] / 32768.0; v * v } / count).toFloat().coerceIn(0f, 1f)
                        val endpoint = synchronized(nativeLock) {
                            feed(recognizer, currentStream, FloatArray(count) { buffer[it] / 32768f })
                            val text = recognizer.getResult(currentStream).text.trim()
                            val endpoint = recognizer.isEndpoint(currentStream)
                            if (endpoint) {
                                committed = listOf(committed, text).filter(String::isNotBlank).joinToString(" ")
                                result.value = SttService.RecognitionResult(committed, isFinal = true)
                                recognizer.reset(currentStream)
                            } else if (partialResults) result.value = SttService.RecognitionResult(listOf(committed, text).filter(String::isNotBlank).joinToString(" "))
                            endpoint
                        }
                        if (endpoint && !continuousMode) break
                    }
                    if (!discard && !closed) synchronized(nativeLock) {
                        finish(recognizer, currentStream)
                        val tail = recognizer.getResult(currentStream).text.trim()
                        result.value = SttService.RecognitionResult(listOf(committed, tail).filter(String::isNotBlank).joinToString(" "), isFinal = true)
                    }
                } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) { if (!discard) report(failure) } finally {
                    synchronized(nativeLock) { stream?.release() }
                    try { audio.stop() } catch (failure: IllegalStateException) { AppLogger.w("SherpaOnnxSTT", "录音已经停止", failure) }
                    audio.release()
                    if (recorder === audio) recorder = null
                    volume.value = 0f
                    if (currentState != SttService.RecognitionState.ERROR) state.value = SttService.RecognitionState.IDLE
                }
            }
            true
        } catch (failure: Exception) {
            recorder?.release(); recorder = null
            report(failure)
            false
        }
    }
    private fun feed(recognizer: OnlineRecognizer, stream: OnlineStream, samples: FloatArray) {
        stream.acceptWaveform(samples, 16000)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
    }
    private fun finish(recognizer: OnlineRecognizer, stream: OnlineStream) {
        feed(recognizer, stream, FloatArray(4800))
        stream.inputFinished()
        while (recognizer.isReady(stream)) recognizer.decode(stream)
    }
    override suspend fun stopRecognition(): Boolean = commands.withLock {
        val active = recording ?: return@withLock false
        stopRequested = true
        state.value = SttService.RecognitionState.PROCESSING
        active.join()
        recording = null
        true
    }
    override suspend fun cancelRecognition() = commands.withLock {
        discard = true
        stopRequested = true
        recording?.join()
        recording = null
        result.value = SttService.RecognitionResult("")
        volume.value = 0f
        if (initialized.value) state.value = SttService.RecognitionState.IDLE
    }
    override suspend fun recognize(audioData: FloatArray) = commands.withLock {
        check(recording?.isActive != true) { "识别服务正在录音" }
        if (!initialize()) return@withLock
        state.value = SttService.RecognitionState.PROCESSING
        result.value = SttService.RecognitionResult("")
        errors.value = SttService.RecognitionError(0, "")
        try {
            val text = withContext(Dispatchers.IO) {
                val recognizer = checkNotNull(engine)
                val stream = synchronized(nativeLock) { recognizer.createStream() }
                try {
                    for (offset in audioData.indices step 16000) {
                        currentCoroutineContext().ensureActive()
                        check(!closed) { "识别服务已释放" }
                        synchronized(nativeLock) { feed(recognizer, stream, audioData.copyOfRange(offset, minOf(offset + 16000, audioData.size))) }
                    }
                    synchronized(nativeLock) { finish(recognizer, stream); recognizer.getResult(stream).text.trim() }
                } finally { synchronized(nativeLock) { stream.release() } }
            }
            result.value = SttService.RecognitionResult(text, isFinal = true)
            state.value = SttService.RecognitionState.IDLE
        } catch (cancel: CancellationException) { state.value = SttService.RecognitionState.IDLE; throw cancel } catch (failure: Exception) { report(failure) }
    }
    override suspend fun getSupportedLanguages(): List<String> = withContext(Dispatchers.IO) { session?.model?.languages.orEmpty() }
    private fun report(failure: Exception) {
        AppLogger.e("SherpaOnnxSTT", "本地语音识别失败", failure)
        errors.value = SttService.RecognitionError(-1, failure.localizedMessage.orEmpty())
        state.value = SttService.RecognitionState.ERROR
    }
    override fun shutdown() {
        closed = true
        runBlocking(Dispatchers.IO) {
            cancelRecognition()
            commands.withLock {
                synchronized(nativeLock) { engine?.release(); engine = null; session?.close(); session = null }
                initialized.value = false
                state.value = SttService.RecognitionState.UNINITIALIZED
            }
        }
        scope.cancel()
    }
}
