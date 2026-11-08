package com.ai.assistance.operit.api.speech.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import com.ai.assistance.operit.util.AssetCopyUtils
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.ceil

class OnnxSileroVad(
    context: Context,
    private val sampleRate: Int = 16000,
    private val frameSize: Int = 512,
    private val mode: Mode = Mode.NORMAL,
    speechDurationMs: Int = 50,
    silenceDurationMs: Int = 300,
    modelAssetPath: String = "models/silero_vad.onnx",
) : AutoCloseable {

    private companion object {
        private const val TAG = "OnnxSileroVad"
    }

    enum class Mode {
        OFF,
        NORMAL,
        AGGRESSIVE,
        VERY_AGGRESSIVE,
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    private val inputNameOrder: List<String>
    private val outputNameOrder: List<String>
    private val audioInputName: String
    private val srInputName: String?
    private val stateInputName: String?
    private val hInputName: String?
    private val cInputName: String?
    private val inputWindowSize: Int
    private val contextSize: Int

    private val audioTensorShape: LongArray
    private val srTensorShape: LongArray
    private val stateTensorShape: LongArray
    private val hTensorShape: LongArray
    private val cTensorShape: LongArray

    private var audioContext = FloatArray(0)

    private var h = FloatArray(128)
    private var c = FloatArray(128)
    private var state = FloatArray(256)

    private var speechFramesCount = 0
    private var silenceFramesCount = 0
    private var maxSpeechFramesCount = msToFrames(speechDurationMs)
    private var maxSilenceFramesCount = msToFrames(silenceDurationMs)

    init {
        val modelFile = AssetCopyUtils.copyAssetToCache(context, modelAssetPath)
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelFile.absolutePath, opts)

        inputNameOrder = session.inputNames.toList()
        outputNameOrder = session.outputNames.toList()

        val inputNameSet = inputNameOrder.toSet()
        audioInputName = when {
            inputNameSet.contains("input") -> "input"
            inputNameSet.contains("audio") -> "audio"
            inputNameSet.contains("x") -> "x"
            else -> inputNameOrder.firstOrNull() ?: "input"
        }
        srInputName = when {
            inputNameSet.contains("sr") -> "sr"
            inputNameSet.contains("sample_rate") -> "sample_rate"
            else -> null
        }
        stateInputName = when {
            inputNameSet.contains("state") -> "state"
            else -> null
        }
        hInputName = if (inputNameSet.contains("h")) "h" else null
        cInputName = if (inputNameSet.contains("c")) "c" else null

        fun getTensorInfo(name: String): TensorInfo? {
            return (session.inputInfo[name]?.info as? TensorInfo)
        }

        fun normalizeShape(shape: LongArray?, fallback: LongArray): LongArray {
            if (shape == null || shape.isEmpty()) return fallback
            return LongArray(shape.size) { i ->
                val v = shape[i]
                if (v <= 0) 1L else v
            }
        }

        val audioTensorInfo = getTensorInfo(audioInputName)
        val audioModelShape = audioTensorInfo?.shape
        val lastDim = audioModelShape?.lastOrNull()?.toInt() ?: -1
        inputWindowSize =
            if (lastDim > 0) {
                lastDim
            } else {
                when {
                    sampleRate == 16000 && frameSize == 512 -> 512 + 64
                    sampleRate == 8000 && frameSize == 256 -> 256 + 32
                    else -> frameSize
                }
            }
        contextSize = (inputWindowSize - frameSize).coerceAtLeast(0)
        audioContext = FloatArray(contextSize)

        audioTensorShape = when (audioModelShape?.size) {
            1 -> longArrayOf(inputWindowSize.toLong())
            2 -> longArrayOf(1, inputWindowSize.toLong())
            else -> longArrayOf(1, inputWindowSize.toLong())
        }

        val srInfo = srInputName?.let { getTensorInfo(it) }
        srTensorShape = when (srInfo?.shape?.size) {
            null -> longArrayOf(1)
            0 -> longArrayOf()
            else -> normalizeShape(srInfo.shape, longArrayOf(1))
        }

        val stateInfo = stateInputName?.let { getTensorInfo(it) }
        stateTensorShape = normalizeShape(stateInfo?.shape, longArrayOf(2, 1, 128))

        val hInfo = hInputName?.let { getTensorInfo(it) }
        val cInfo = cInputName?.let { getTensorInfo(it) }
        hTensorShape = normalizeShape(hInfo?.shape, longArrayOf(2, 1, 64))
        cTensorShape = normalizeShape(cInfo?.shape, longArrayOf(2, 1, 64))

        AppLogger.d(
            TAG,
            "Loaded Silero VAD model. inputs=$inputNameOrder outputs=$outputNameOrder inputWindowSize=$inputWindowSize audioShape=${audioTensorShape.toList()} srShape=${srTensorShape.toList()} stateShape=${stateTensorShape.toList()}"
        )
    }

    fun reset() {
        h = FloatArray(128)
        c = FloatArray(128)
        state = FloatArray(256)
        audioContext = FloatArray(contextSize)
        speechFramesCount = 0
        silenceFramesCount = 0
    }

    fun setSpeechDurationMs(ms: Int) {
        maxSpeechFramesCount = msToFrames(ms)
    }

    fun setSilenceDurationMs(ms: Int) {
        maxSilenceFramesCount = msToFrames(ms)
    }

    fun isSpeech(frame: ShortArray): Boolean {
        if (mode == Mode.OFF) return false
        require(frame.size == frameSize)

        val audio = FloatArray(frameSize) { i -> frame[i] / 32768.0f }
        val modelInput = if (contextSize > 0) {
            val input = FloatArray(contextSize + frameSize)
            if (audioContext.isNotEmpty()) {
                java.lang.System.arraycopy(audioContext, 0, input, 0, contextSize)
            }
            java.lang.System.arraycopy(audio, 0, input, contextSize, frameSize)
            input
        } else {
            audio
        }

        val prob = predictProbability(modelInput)
        val isSpeechFrame = prob > threshold()

        if (contextSize > 0) {
            java.lang.System.arraycopy(modelInput, modelInput.size - contextSize, audioContext, 0, contextSize)
        }

        return isContinuousSpeech(isSpeechFrame)
    }

    private fun isContinuousSpeech(isSpeechFrame: Boolean): Boolean {
        if (isSpeechFrame) {
            if (speechFramesCount <= maxSpeechFramesCount) speechFramesCount++

            if (speechFramesCount > maxSpeechFramesCount) {
                silenceFramesCount = 0
                return true
            }
        } else {
            if (silenceFramesCount <= maxSilenceFramesCount) silenceFramesCount++

            if (silenceFramesCount > maxSilenceFramesCount) {
                speechFramesCount = 0
                return false
            } else if (speechFramesCount > maxSpeechFramesCount) {
                return true
            }
        }
        return false
    }

    private fun predictProbability(audioData: FloatArray): Float {
        val toClose = ArrayList<AutoCloseable>(4)
        try {
            val inputs = LinkedHashMap<String, OnnxTensor>()

            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(audioData),
                audioTensorShape
            )
            toClose.add(inputTensor)
            inputs[audioInputName] = inputTensor

            srInputName?.let { name ->
                val srTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(sampleRate.toLong())),
                    srTensorShape
                )
                toClose.add(srTensor)
                inputs[name] = srTensor
            }

            if (stateInputName != null) {
                val stateTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(state),
                    stateTensorShape
                )
                toClose.add(stateTensor)
                inputs[stateInputName] = stateTensor
            } else if (hInputName != null && cInputName != null) {
                val hTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(h),
                    hTensorShape
                )
                toClose.add(hTensor)
                inputs[hInputName] = hTensor

                val cTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(c),
                    cTensorShape
                )
                toClose.add(cTensor)
                inputs[cInputName] = cTensor
            }

            session.run(inputs).use { out ->
                val confidence = extractScalarFloat(out[0].value)

                if (stateInputName != null && out.size() >= 2) {
                    extractFloatArray(out[1].value, 256)?.let { st ->
                        state = st
                    }
                } else if (hInputName != null && cInputName != null && out.size() >= 3) {
                    extractFloatArray(out[1].value, 128)?.let { hn ->
                        h = hn
                    }
                    extractFloatArray(out[2].value, 128)?.let { cn ->
                        c = cn
                    }
                }

                return confidence
            }
        } finally {
            for (i in toClose.indices.reversed()) {
                try {
                    toClose[i].close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun extractScalarFloat(value: Any?): Float {
        return when (value) {
            is FloatArray -> value.firstOrNull() ?: 0f
            is Array<*> -> extractScalarFloat(value.firstOrNull())
            else -> 0f
        }
    }

    private fun extractFloatArray(value: Any?, expectedSize: Int): FloatArray? {
        val out = ArrayList<Float>(expectedSize)
        fun walk(v: Any?) {
            when (v) {
                is FloatArray -> v.forEach { out.add(it) }
                is Array<*> -> v.forEach { walk(it) }
            }
        }
        walk(value)
        return if (out.size == expectedSize) out.toFloatArray() else null
    }

    private fun threshold(): Float {
        return when (mode) {
            Mode.NORMAL -> 0.5f
            Mode.AGGRESSIVE -> 0.8f
            Mode.VERY_AGGRESSIVE -> 0.95f
            Mode.OFF -> 1f
        }
    }

    private fun msToFrames(ms: Int): Int {
        if (ms <= 0) return 0
        val frameDurationMs = (frameSize * 1000.0) / sampleRate
        return ceil(ms / frameDurationMs).toInt().coerceAtLeast(0)
    }

    override fun close() {
        session.close()
    }
}
