// 来源：k2-fsa/sherpa-onnx v1.13.2，版权归 Xiaomi Corporation，采用 Apache-2.0。
// 保留 JNI 所需的类名与字段；运行库由 PackResources 完成固定摘要校验后显式加载。

package com.k2fsa.sherpa.onnx

import androidx.annotation.Keep

import android.content.res.AssetManager

data class OfflineTtsVitsModelConfig(
    var model: String = "",
    var lexicon: String = "",
    var tokens: String = "",
    var dataDir: String = "",
    var dictDir: String = "",
    var noiseScale: Float = 0.667f,
    var noiseScaleW: Float = 0.8f,
    var lengthScale: Float = 1.0f,
)

data class OfflineTtsMatchaModelConfig(
    var acousticModel: String = "",
    var vocoder: String = "",
    var lexicon: String = "",
    var tokens: String = "",
    var dataDir: String = "",
    var dictDir: String = "",
    var noiseScale: Float = 1.0f,
    var lengthScale: Float = 1.0f,
)

data class OfflineTtsKokoroModelConfig(
    var model: String = "",
    var voices: String = "",
    var tokens: String = "",
    var dataDir: String = "",
    var lexicon: String = "",
    var lang: String = "",
    var dictDir: String = "",
    var lengthScale: Float = 1.0f,
)

data class OfflineTtsZipVoiceModelConfig(
    var tokens: String = "",
    var encoder: String = "",
    var decoder: String = "",
    var vocoder: String = "",
    var dataDir: String = "",
    var lexicon: String = "",
    var featScale: Float = 0.1f,
    var tShift: Float = 0.5f,
    var targetRms: Float = 0.1f,
    var guidanceScale: Float = 1.0f,
)

data class OfflineTtsKittenModelConfig(
    var model: String = "",
    var voices: String = "",
    var tokens: String = "",
    var dataDir: String = "",
    var lengthScale: Float = 1.0f,
)


data class OfflineTtsPocketModelConfig(
  var lmFlow: String = "",
  var lmMain: String = "",
  var encoder: String = "",
  var decoder: String = "",
  var textConditioner: String = "",
  var vocabJson: String = "",
  var tokenScoresJson: String = "",
  var voiceEmbeddingCacheCapacity: Int = 50,
)

data class OfflineTtsSupertonicModelConfig(
  var durationPredictor: String = "",
  var textEncoder: String = "",
  var vectorEstimator: String = "",
  var vocoder: String = "",
  var ttsJson: String = "",
  var unicodeIndexer: String = "",
  var voiceStyle: String = "",
)

data class OfflineTtsModelConfig(
    var vits: OfflineTtsVitsModelConfig = OfflineTtsVitsModelConfig(),
    var matcha: OfflineTtsMatchaModelConfig = OfflineTtsMatchaModelConfig(),
    var kokoro: OfflineTtsKokoroModelConfig = OfflineTtsKokoroModelConfig(),
    var zipvoice: OfflineTtsZipVoiceModelConfig = OfflineTtsZipVoiceModelConfig(),
    var kitten: OfflineTtsKittenModelConfig = OfflineTtsKittenModelConfig(),
    var pocket: OfflineTtsPocketModelConfig = OfflineTtsPocketModelConfig(),
    var supertonic: OfflineTtsSupertonicModelConfig = OfflineTtsSupertonicModelConfig(),

    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
)

data class OfflineTtsConfig(
    var model: OfflineTtsModelConfig = OfflineTtsModelConfig(),
    var ruleFsts: String = "",
    var ruleFars: String = "",
    var maxNumSentences: Int = 1,
    var silenceScale: Float = 0.2f,
)

class GeneratedAudio(
    val samples: FloatArray,
    val sampleRate: Int,
) {
    fun save(filename: String) =
        saveImpl(filename = filename, samples = samples, sampleRate = sampleRate)

    private external fun saveImpl(
        filename: String,
        samples: FloatArray,
        sampleRate: Int
    ): Boolean
}

data class GenerationConfig(
    var silenceScale: Float = 0.2f,
    var speed: Float = 1.0f,
    var sid: Int = 0,
    var referenceAudio: FloatArray? = null,
    var referenceSampleRate: Int = 0,
    var referenceText: String? = null,
    var numSteps: Int = 5,
    var extra: Map<String, String>? = null
)

class OfflineTts(
    assetManager: AssetManager? = null,
    var config: OfflineTtsConfig,
) {
    private var ptr: Long

    init {
        ptr = if (assetManager != null) {
            newFromAsset(assetManager, config)
        } else {
            newFromFile(config)
        }
        check(ptr != 0L) { "本地语音模型初始化失败，请检查模型文件与驱动" }
    }

    fun sampleRate() = getSampleRate(ptr)

    fun numSpeakers() = getNumSpeakers(ptr)

    fun generate(
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f
    ): GeneratedAudio {
        return generateImpl(ptr, text = text, sid = sid, speed = speed)
    }

    fun generateWithCallback(
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f,
        callback: (samples: FloatArray) -> Int
    ): GeneratedAudio {
        return generateWithCallbackImpl(
            ptr,
            text = text,
            sid = sid,
            speed = speed,
            callback = OfflineTtsCallback(callback)
        )
    }

    fun generateWithConfig(
      text: String,
      config: GenerationConfig
    ): GeneratedAudio {
        return generateWithConfigImpl(ptr, text, config, null)
    }

    fun generateWithConfigAndCallback(
        text: String,
        config: GenerationConfig,
        callback: (samples: FloatArray) -> Int
    ): GeneratedAudio {
        return generateWithConfigImpl(ptr, text, config, OfflineTtsCallback(callback))
    }

    fun allocate(assetManager: AssetManager? = null) {
        if (ptr == 0L) {
            ptr = if (assetManager != null) {
                newFromAsset(assetManager, config)
            } else {
                newFromFile(config)
            }
        }
    }

    fun free() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()

    private external fun newFromAsset(
        assetManager: AssetManager,
        config: OfflineTtsConfig,
    ): Long

    private external fun newFromFile(
        config: OfflineTtsConfig,
    ): Long

    private external fun delete(ptr: Long)
    private external fun getSampleRate(ptr: Long): Int
    private external fun getNumSpeakers(ptr: Long): Int





    private external fun generateImpl(
        ptr: Long,
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f
    ): GeneratedAudio

    private external fun generateWithCallbackImpl(
        ptr: Long,
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f,
        callback: (samples: FloatArray) -> Int
    ): GeneratedAudio


    private external fun generateWithConfigImpl(
        ptr: Long,
        text: String,
        config: GenerationConfig,
        callback: ((samples: FloatArray) -> Int)?
    ): GeneratedAudio

}

/**
 * JNI 按 invoke(float[]) -> Integer 查找方法，不能直接传入只实现擦除签名的合成 lambda。
 * 显式类保留具类型的 invoke 及编译器桥接方法，内部仍执行原有取消回调。
 */
@Keep
internal class OfflineTtsCallback(
    private val callback: (FloatArray) -> Int,
) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int = callback(samples)
}
