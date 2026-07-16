package com.ai.assistance.operit.api.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.speech.SpeechPrerollStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import kotlin.math.log10

@SuppressLint("MissingPermission")
class OpenAISttProvider(
    private val context: Context,
    private val endpointUrl: String,
    private val apiKey: String,
    private val model: String,
) : SpeechService {

    companion object {
        private const val TAG = "OpenAISttProvider"
        private const val DEFAULT_TIMEOUT_SECONDS = 60
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val WAV_HEADER_SIZE = 44
        private const val MAX_FILE_BYTES = 25L * 1024L * 1024L
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var recordingJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var outputStream: FileOutputStream? = null
    private var pcmBytesWritten: Long = 0
    private var lastLanguageCode: String? = null
    private var vad: OnnxSileroVad? = null

    private val _recognitionState = MutableStateFlow(SpeechService.RecognitionState.UNINITIALIZED)
    override val currentState: SpeechService.RecognitionState
        get() = _recognitionState.value
    override val recognitionStateFlow: StateFlow<SpeechService.RecognitionState> =
        _recognitionState.asStateFlow()

    private val _recognitionResult = MutableStateFlow(SpeechService.RecognitionResult(""))
    override val recognitionResultFlow: StateFlow<SpeechService.RecognitionResult> =
        _recognitionResult.asStateFlow()

    private val _recognitionError = MutableStateFlow(SpeechService.RecognitionError(0, ""))
    override val recognitionErrorFlow: StateFlow<SpeechService.RecognitionError> =
        _recognitionError.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _volumeLevelFlow = MutableStateFlow(0f)
    override val volumeLevelFlow: StateFlow<Float> = _volumeLevelFlow.asStateFlow()

    override val isRecognizing: Boolean
        get() = currentState == SpeechService.RecognitionState.RECOGNIZING

    private val VOLUME_SMOOTHING_FACTOR = 0.1f
    private var currentVolume = 0f

    override suspend fun initialize(): Boolean {
        if (isInitialized.value) return true

        return try {
            withContext(Dispatchers.IO) {
                if (endpointUrl.isBlank()) {
                    throw IOException(context.getString(R.string.openai_stt_error_url_not_set))
                }
                if (!endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")) {
                    throw IOException(context.getString(R.string.openai_stt_error_url_invalid_scheme))
                }
                if (!endpointUrl.contains("/audio/transcriptions")) {
                    throw IOException(context.getString(R.string.openai_stt_error_url_invalid_path))
                }
                if (apiKey.isBlank()) {
                    throw IOException(context.getString(R.string.openai_stt_error_api_key_not_set))
                }
                if (model.isBlank()) {
                    throw IOException(context.getString(R.string.openai_stt_error_model_not_set))
                }

                _isInitialized.value = true
                _recognitionState.value = SpeechService.RecognitionState.IDLE
                true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "OpenAI STT initialize failed", e)
            _isInitialized.value = false
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value = SpeechService.RecognitionError(-1, e.message ?: "Unknown error")
            false
        }
    }

    override suspend fun startRecognition(
        languageCode: String,
        continuousMode: Boolean,
        partialResults: Boolean,
        audioSource: Int,
    ): Boolean {
        if (!isInitialized.value) {
            val ok = initialize()
            if (!ok) return false
        }

        if (recordingJob?.isActive == true) return false

        lastLanguageCode = languageCode

        _recognitionError.value = SpeechService.RecognitionError(0, "")
        _recognitionResult.value = SpeechService.RecognitionResult("")
        _recognitionState.value = SpeechService.RecognitionState.PREPARING

        return try {
            withContext(Dispatchers.IO) {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    throw IOException("AudioRecord buffer init failed")
                }

                val record = AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize * 2,
                )

                val file = File(context.cacheDir, "openai_stt_${UUID.randomUUID()}.wav")
                val stream = FileOutputStream(file)
                stream.write(ByteArray(WAV_HEADER_SIZE))

                pcmBytesWritten = 0

                val pendingPcm = SpeechPrerollStore.consumePending()
                if (pendingPcm != null && pendingPcm.isNotEmpty()) {
                    try {
                        writePcm16le(stream, pendingPcm, pendingPcm.size)
                        AppLogger.d(TAG, "Applied preroll: samples=${pendingPcm.size}")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to apply preroll", e)
                    }
                }

                audioRecord = record
                outputFile = file
                outputStream = stream

                record.startRecording()

                _recognitionState.value = SpeechService.RecognitionState.RECOGNIZING

                val audioBuffer = ShortArray(1024)

                recordingJob =
                    scope.launch {
                        try {
                            val vadInstance = try {
                                (vad ?: OnnxSileroVad(context = context, speechDurationMs = 0)).also { created ->
                                    vad = created
                                    created.reset()
                                }
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "Failed to initialize Silero VAD, falling back to non-VAD mode", e)
                                null
                            }

                            val vadFrameSize = 512
                            val vadFrame = ShortArray(vadFrameSize)
                            var vadFramePos = 0

                            val preRollSamples = SAMPLE_RATE / 2
                            val preRoll = ShortArray(preRollSamples)
                            var preRollPos = 0
                            var preRollFilled = 0

                            fun appendToPreRoll(frame: ShortArray, size: Int) {
                                var idx = 0
                                while (idx < size) {
                                    val toCopy = minOf(size - idx, preRollSamples - preRollPos)
                                    java.lang.System.arraycopy(frame, idx, preRoll, preRollPos, toCopy)
                                    preRollPos += toCopy
                                    if (preRollPos >= preRollSamples) preRollPos = 0
                                    preRollFilled = minOf(preRollFilled + toCopy, preRollSamples)
                                    idx += toCopy
                                }
                            }

                            fun flushPreRoll(streamOut: FileOutputStream) {
                                if (preRollFilled <= 0) return
                                if (preRollFilled < preRollSamples) {
                                    writePcm16le(streamOut, preRoll, preRollFilled)
                                } else {
                                    val firstLen = preRollSamples - preRollPos
                                    if (firstLen > 0) {
                                        writePcm16le(streamOut, preRoll, firstLen, offset = preRollPos)
                                    }
                                    if (preRollPos > 0) {
                                        writePcm16le(streamOut, preRoll, preRollPos, offset = 0)
                                    }
                                }
                                preRollFilled = 0
                                preRollPos = 0
                            }

                            var speechActive = false
                            var autoStopTriggered = false

                            while (isActive && _recognitionState.value == SpeechService.RecognitionState.RECOGNIZING) {
                                val read = record.read(audioBuffer, 0, audioBuffer.size)
                                if (read > 0) {
                                    SpeechPrerollStore.appendPcm(audioBuffer, read)
                                    updateVolumeLevel(audioBuffer, read)

                                    if (vadInstance == null) {
                                        try {
                                            writePcm16le(stream, audioBuffer, read)
                                        } catch (e: IOException) {
                                            throw e
                                        }
                                    } else {
                                        var idx = 0
                                        while (idx < read) {
                                            val toCopy = minOf(vadFrameSize - vadFramePos, read - idx)
                                            java.lang.System.arraycopy(audioBuffer, idx, vadFrame, vadFramePos, toCopy)
                                            vadFramePos += toCopy
                                            idx += toCopy

                                            if (vadFramePos == vadFrameSize) {
                                                val isSpeech = vadInstance.isSpeech(vadFrame)
                                                if (!speechActive) {
                                                    if (isSpeech) {
                                                        speechActive = true
                                                        flushPreRoll(stream)
                                                        try {
                                                            writePcm16le(stream, vadFrame, vadFrameSize)
                                                        } catch (e: IOException) {
                                                            throw e
                                                        }
                                                    } else {
                                                        appendToPreRoll(vadFrame, vadFrameSize)
                                                    }
                                                } else {
                                                    if (isSpeech) {
                                                        try {
                                                            writePcm16le(stream, vadFrame, vadFrameSize)
                                                        } catch (e: IOException) {
                                                            throw e
                                                        }
                                                    } else if (!autoStopTriggered) {
                                                        autoStopTriggered = true
                                                        scope.launch { stopRecognition() }
                                                        return@launch
                                                    }
                                                }

                                                if (pcmBytesWritten > MAX_FILE_BYTES) {
                                                    val fileSizeMB = MAX_FILE_BYTES / 1024 / 1024
                                                    throw IOException(context.getString(R.string.openai_stt_error_file_too_large, fileSizeMB))
                                                }

                                                vadFramePos = 0
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Recording loop failed", e)
                            withContext(Dispatchers.Main) {
                                _recognitionState.value = SpeechService.RecognitionState.ERROR
                                _recognitionError.value = SpeechService.RecognitionError(-1, e.message ?: "Recording failed")
                            }
                        }
                    }

                true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "OpenAI STT startRecognition failed", e)
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value = SpeechService.RecognitionError(-1, e.message ?: "startRecognition failed")
            false
        }
    }

    override suspend fun stopRecognition(): Boolean {
        if (_recognitionState.value != SpeechService.RecognitionState.RECOGNIZING) return false

        _recognitionState.value = SpeechService.RecognitionState.PROCESSING

        return try {
            val file = withContext(Dispatchers.IO) { stopRecordingInternal(deleteFile = false) }
                ?: run {
                    _recognitionState.value = SpeechService.RecognitionState.ERROR
                    _recognitionError.value = SpeechService.RecognitionError(-1, "No audio recorded")
                    return false
                }

            if (file.length() > MAX_FILE_BYTES) {
                val fileSizeMB = MAX_FILE_BYTES / 1024 / 1024
                file.delete()
                _recognitionState.value = SpeechService.RecognitionState.ERROR
                _recognitionError.value = SpeechService.RecognitionError(-1, context.getString(R.string.openai_stt_error_file_too_large, fileSizeMB))
                return false
            }

            val text = withContext(Dispatchers.IO) {
                transcribeWavFile(file, languageCode = lastLanguageCode)
            }

            file.delete()

            _recognitionResult.value = SpeechService.RecognitionResult(text = text, isFinal = true, confidence = 0f)
            _recognitionState.value = SpeechService.RecognitionState.IDLE
            _volumeLevelFlow.value = 0f
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "OpenAI STT stopRecognition failed", e)
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value = SpeechService.RecognitionError(-1, e.message ?: "stopRecognition failed")
            false
        }
    }

    override suspend fun cancelRecognition() {
        withContext(Dispatchers.IO) {
            stopRecordingInternal(deleteFile = true)
        }
        _volumeLevelFlow.value = 0f
        if (_recognitionState.value != SpeechService.RecognitionState.UNINITIALIZED) {
            _recognitionState.value = SpeechService.RecognitionState.IDLE
        }
    }

    override fun shutdown() {
        try {
            scope.cancel()
        } catch (_: Exception) {
        }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { outputStream?.close() }
        outputStream = null
        outputFile?.delete()
        outputFile = null
        runCatching { vad?.close() }
        vad = null
        _isInitialized.value = false
        _recognitionState.value = SpeechService.RecognitionState.UNINITIALIZED
        _volumeLevelFlow.value = 0f
    }

    override suspend fun getSupportedLanguages(): List<String> =
        withContext(Dispatchers.IO) {
            return@withContext listOf("zh", "en", "es", "ko", "id", "ms", "pt-BR")
        }

    override suspend fun recognize(audioData: FloatArray) {
        if (!isInitialized.value) {
            val ok = initialize()
            if (!ok) return
        }

        _recognitionError.value = SpeechService.RecognitionError(0, "")
        _recognitionResult.value = SpeechService.RecognitionResult("")
        _recognitionState.value = SpeechService.RecognitionState.PROCESSING

        try {
            val file = withContext(Dispatchers.IO) {
                val out = File(context.cacheDir, "openai_stt_${UUID.randomUUID()}.wav")
                FileOutputStream(out).use { fos ->
                    fos.write(ByteArray(WAV_HEADER_SIZE))
                    val bytes = ByteArray(audioData.size * 2)
                    var idx = 0
                    for (f in audioData) {
                        val clamped = f.coerceIn(-1f, 1f)
                        val s = (clamped * 32767f).toInt().toShort()
                        bytes[idx++] = (s.toInt() and 0xff).toByte()
                        bytes[idx++] = ((s.toInt() shr 8) and 0xff).toByte()
                    }
                    fos.write(bytes)
                }
                writeWavHeader(out, pcmDataSize = audioData.size.toLong() * 2L)
                out
            }

            val text = withContext(Dispatchers.IO) {
                transcribeWavFile(file, languageCode = lastLanguageCode)
            }

            file.delete()

            _recognitionResult.value = SpeechService.RecognitionResult(text = text, isFinal = true, confidence = 0f)
            _recognitionState.value = SpeechService.RecognitionState.IDLE
        } catch (e: Exception) {
            AppLogger.e(TAG, "OpenAI STT recognize(audioData) failed", e)
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value = SpeechService.RecognitionError(-1, e.message ?: "recognize failed")
        }
    }

    private suspend fun stopRecordingInternal(deleteFile: Boolean): File? {
        val job = recordingJob
        recordingJob = null
        try {
            if (job != null) {
                job.cancel()
                job.join()
            }
        } catch (_: Exception) {
        }

        val record = audioRecord
        audioRecord = null
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        try {
            record?.release()
        } catch (_: Exception) {
        }

        val stream = outputStream
        outputStream = null
        try {
            stream?.flush()
        } catch (_: Exception) {
        }
        try {
            stream?.close()
        } catch (_: Exception) {
        }

        val file = outputFile
        outputFile = null

        if (file != null && file.exists()) {
            if (deleteFile) {
                file.delete()
                return null
            }
            if (pcmBytesWritten <= 0L) {
                file.delete()
                return null
            }
            writeWavHeader(file, pcmDataSize = pcmBytesWritten)
            return file
        }

        return null
    }
    private fun writePcm16le(stream: FileOutputStream, pcm: ShortArray, length: Int, offset: Int = 0) {
        if (length <= 0) return
        val bytes = ByteArray(length * 2)
        var idx = 0
        for (i in 0 until length) {
            val v = pcm[offset + i].toInt()
            bytes[idx++] = (v and 0xff).toByte()
            bytes[idx++] = ((v shr 8) and 0xff).toByte()
        }
        stream.write(bytes)
        pcmBytesWritten += bytes.size
    }

    private fun writeWavHeader(file: File, pcmDataSize: Long) {
        val totalDataLen = pcmDataSize + 36
        val byteRate = SAMPLE_RATE * 2

        val header = ByteArray(WAV_HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        buffer.position(4)
        buffer.putInt(totalDataLen.toInt())
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        buffer.position(16)
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(1.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort(2.toShort())
        buffer.putShort(16.toShort())
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        buffer.position(40)
        buffer.putInt(pcmDataSize.toInt())

        try {
            val raf = java.io.RandomAccessFile(file, "rw")
            raf.seek(0)
            raf.write(header)
            raf.close()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to write WAV header", e)
        }
    }

    private fun mapLanguage(languageCode: String?): String? {
        val code = languageCode?.trim()?.lowercase() ?: return null
        if (code.isBlank()) return null
        return when {
            code.startsWith("zh") -> "zh"
            code.startsWith("en") -> "en"
            code.startsWith("es") -> "es"
            code.startsWith("ko") -> "ko"
            code.startsWith("id") -> "id"
            code.startsWith("ms") -> "ms"
            code.startsWith("pt") -> "pt"
            else -> code
        }
    }

    private fun transcribeWavFile(file: File, languageCode: String?): String {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        bodyBuilder.addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
        bodyBuilder.addFormDataPart("model", model)
        val lang = mapLanguage(languageCode)
        if (!lang.isNullOrBlank()) {
            bodyBuilder.addFormDataPart("language", lang)
        }
        bodyBuilder.addFormDataPart("response_format", "json")

        val request = Request.Builder()
            .url(endpointUrl)
            .post(bodyBuilder.build())
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.openai_stt_error_request_failed), e)
        }

        response.use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException("OpenAI STT request failed with code ${resp.code}: $body")
            }
            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                return json.optString("text", trimmed)
            }
            return trimmed
        }
    }

    private fun updateVolumeLevel(buffer: ShortArray, length: Int) {
        if (length <= 0) return

        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble() / 32768.0
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / length)
        val db = 20 * log10(rms + 1e-6)
        val normalized = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)

        currentVolume = currentVolume * (1f - VOLUME_SMOOTHING_FACTOR) + normalized * VOLUME_SMOOTHING_FACTOR
        _volumeLevelFlow.value = currentVolume
    }
}
