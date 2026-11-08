package com.ai.assistance.operit.api.speech.stt

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences.SttHttpConfig
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/** 语音识别服务工厂类 用于创建和管理不同类型的语音识别服务 */
object SttServiceFactory {
    private const val TAG = "SpeechServiceFactory"

    /** 语音识别服务类型 */
    enum class SpeechServiceType {
        /** 基于Sherpa-ncnn的本地识别实现 */
        SHERPA_NCNN,
        /** 从离线资源安装目录读取 ONNX Zipformer 模型。 */
        SHERPA_ONNX,
        OPENAI_STT,
        DEEPGRAM_STT,
    }

    /**
     * 创建语音识别服务实例
     *
     * @param context 应用上下文
     * @return 对应类型的语音识别服务实例
     */
    fun createSpeechService(
        context: Context
    ): SttService {
        val profiles = SpeechServiceProfilesPreferences(context)
        val profile = runBlocking { profiles.getCurrentSttProfile() }

        return createSpeechService(context, profile.serviceType, profile.httpConfig)
    }

    fun createWakeSpeechService(
        context: Context,
    ): SttService {
        val profiles = SpeechServiceProfilesPreferences(context)
        val profile = runBlocking { profiles.getCurrentSttProfile() }
        val selectedType = profile.serviceType
        val effectiveType = when (selectedType) {
            SpeechServiceType.SHERPA_ONNX,
            SpeechServiceType.OPENAI_STT,
            SpeechServiceType.DEEPGRAM_STT,
            -> SpeechServiceType.SHERPA_NCNN
            else -> selectedType
        }
        return createSpeechService(context, effectiveType, profile.httpConfig)
    }

    fun createSpeechService(
        context: Context,
        type: SpeechServiceType,
    ): SttService {
        val profiles = SpeechServiceProfilesPreferences(context)
        val config = runBlocking { profiles.getCurrentSttProfile().httpConfig }
        return createSpeechService(context, type, config)
    }

    /** 使用明确的配置创建实例，供识别测试区直接使用当前编辑值。 */
    internal fun createSpeechService(
        context: Context,
        type: SpeechServiceType,
        httpConfig: SttHttpConfig,
    ): SttService {
        return when (type) {
            SpeechServiceType.SHERPA_NCNN -> acquireLocalSpeechService(context, type)
            SpeechServiceType.SHERPA_ONNX -> com.ai.assistance.operit.api.speech.stt.SherpaOnnxSpeechProvider(context.applicationContext, httpConfig.localModelKey)
            SpeechServiceType.OPENAI_STT -> {
                runBlocking {
                    OpenAISttProvider(
                        context = context,
                        endpointUrl = httpConfig.endpointUrl,
                        apiKey = httpConfig.apiKey,
                        model = httpConfig.modelName,
                    )
                }
            }
            SpeechServiceType.DEEPGRAM_STT -> {
                runBlocking {
                    DeepgramSttProvider(
                        context = context,
                        endpointUrl = httpConfig.endpointUrl,
                        apiKey = httpConfig.apiKey,
                        model = httpConfig.modelName,
                    )
                }
            }
        }
    }

    private class SpeechServiceLease(
        private val delegate: SttService,
        private val onRelease: () -> Unit,
    ) : SttService by delegate {
        private val released = AtomicBoolean(false)

        override fun shutdown() {
            if (released.compareAndSet(false, true)) {
                onRelease()
            }
        }
    }

    private data class LocalEntry(
        val type: SpeechServiceType,
        val service: SttService,
        var refCount: Int,
    )

    private val localLock = Any()
    private var localEntry: LocalEntry? = null

    private fun acquireLocalSpeechService(
        context: Context,
        type: SpeechServiceType,
    ): SttService {
        val appContext = context.applicationContext
        synchronized(localLock) {
            val existing = localEntry
            if (existing != null && existing.type != type) {
                throw IllegalStateException(
                    "Local SpeechService already active: ${existing.type}. " +
                        "Cannot create another local SpeechService of type $type before releasing the previous one."
                )
            }

            val entry =
                if (existing != null) {
                    existing.refCount += 1
                    existing
                } else {
                    val service =
                        when (type) {
                            SpeechServiceType.SHERPA_NCNN -> SherpaSpeechProvider(appContext)
                            else -> throw IllegalArgumentException("Not a local SpeechService type: $type")
                        }
                    LocalEntry(type = type, service = service, refCount = 1).also { localEntry = it }
                }

            return SpeechServiceLease(
                delegate = entry.service,
                onRelease = { releaseLocalSpeechService(entry.type) },
            )
        }
    }

    private fun releaseLocalSpeechService(type: SpeechServiceType) {
        val toShutdown: SttService?
        synchronized(localLock) {
            val entry = localEntry
            if (entry == null || entry.type != type) {
                return
            }

            entry.refCount -= 1
            if (entry.refCount > 0) {
                return
            }

            localEntry = null
            toShutdown = entry.service
        }

        try {
            toShutdown?.shutdown()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to shutdown local SpeechService", e)
        }
    }

    // 单例实例缓存
    private var instance: SttService? = null
    private var currentProfileId: String? = null

    /**
     * 获取语音识别服务单例实例
     *
     * @param context 应用上下文
     * @return 语音识别服务实例
     */
    fun getInstance(
        context: Context,
    ): SttService {
        val profiles = SpeechServiceProfilesPreferences(context)
        val profile = runBlocking { profiles.getCurrentSttProfile() }
        val selectedProfileId = profile.id

        val needNewInstance = instance == null || selectedProfileId != currentProfileId
        
        if (needNewInstance) {
            try {
                instance?.shutdown()
            } catch (_: Exception) {
            }

            val created =
                try {
                    createSpeechService(context)
                } catch (e: IllegalStateException) {
                    AppLogger.w(TAG, "Failed to create SpeechService for profile=$selectedProfileId, keeping previous instance", e)
                    null
                }

            if (created != null) {
                instance = created
                currentProfileId = selectedProfileId
            }
        }

        val value = instance
        if (value != null) return value

        val fallbackEntry = synchronized(localLock) { localEntry }
        if (fallbackEntry != null) {
            return acquireLocalSpeechService(context, fallbackEntry.type)
        }

        return createSpeechService(context, SpeechServiceType.SHERPA_NCNN)
    }

    /** 重置单例实例 在需要更改语音识别服务类型或释放资源时调用 */
    fun resetInstance() {
        // 与 TTS 一致，先解除单例引用再等待旧引擎释放。
        val retiring = instance
        instance = null
        currentProfileId = null
        try {
            retiring?.shutdown()
        } catch (failure: Exception) {
            AppLogger.w(TAG, "释放旧识别服务失败", failure)
        }
    }
}
