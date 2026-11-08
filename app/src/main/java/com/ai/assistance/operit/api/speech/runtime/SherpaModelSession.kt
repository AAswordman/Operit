package com.ai.assistance.operit.api.speech.runtime

import android.content.Context
import com.ai.assistance.operit.data.resources.models.*
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.data.storage.LocalModelUsageHandle
import com.k2fsa.sherpa.onnx.*
import java.io.File

/** 引擎与模型分别校验；运行引用在原生对象释放后才归还。 */
internal class SherpaModelSession(context: Context, key: String, purpose: SpeechModelPurpose) : AutoCloseable {
    val store = SpeechModels(context)
    val model = store.find(key)
    val root: File = store.directory(model)
    private val usage: LocalModelUsageHandle
    init {
        require(model.purpose == purpose) { "模型用途与语音服务不一致" }
        usage = LocalModelRuntimeRegistry.acquire(root) ?: error("模型正在移除")
        try {
            check(store.isInstalled(model)) { "模型不完整，请重新准备或导入" }
            PackResources.loadSpeechEngine(context)
        } catch (error: Throwable) { usage.close(); throw error }
    }
    private fun path(relative: String): String = if (relative.isEmpty()) "" else root.containedFile(relative).absolutePath
    fun ttsConfig(): OfflineTtsConfig {
        val d = model.driver
        val config = OfflineTtsModelConfig(numThreads = 2, provider = "cpu")
        when (d.type) {
            "vits" -> config.vits = OfflineTtsVitsModelConfig(model = path(d.model), lexicon = path(d.lexicon), tokens = path(d.tokens), dataDir = path(d.dataDir))
            "matcha" -> config.matcha = OfflineTtsMatchaModelConfig(acousticModel = path(d.model), vocoder = path(d.vocoder), lexicon = path(d.lexicon), tokens = path(d.tokens), dataDir = path(d.dataDir))
            "kitten" -> config.kitten = OfflineTtsKittenModelConfig(model = path(d.model), voices = path(d.voices), tokens = path(d.tokens), dataDir = path(d.dataDir))
            else -> error("不支持的合成模型驱动")
        }
        return OfflineTtsConfig(model = config, ruleFsts = d.ruleFsts.joinToString(",", transform = ::path), ruleFars = d.ruleFars.joinToString(",", transform = ::path))
    }
    fun sttConfig(): OnlineRecognizerConfig {
        val d = model.driver
        require(d.type == "transducer")
        return OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(path(d.encoder), path(d.decoder), path(d.joiner)),
                tokens = path(d.tokens), numThreads = 2, provider = "cpu", modelType = "zipformer",
            ),
            enableEndpoint = true,
        )
    }
    override fun close() = usage.close()
}
