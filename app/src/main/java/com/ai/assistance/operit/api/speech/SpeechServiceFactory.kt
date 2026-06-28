package com.ai.assistance.operit.api.speech

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences.VadConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/** 语音识别服务工厂类 用于创建和管理不同类型的语音识别服务 */
object SpeechServiceFactory {
    private const val TAG = "SpeechServiceFactory"

    /** 语音识别服务类型 */
    enum class SpeechServiceType {
        /** 基于Sherpa-ncnn的本地识别实现 */
        SHERPA_NCNN,
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
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        val type = runBlocking { prefs.sttServiceTypeFlow.first() }

        return createSpeechService(context, type)
    }

    fun createWakeSpeechService(
        context: Context,
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        val selectedType = runBlocking { prefs.sttServiceTypeFlow.first() }
        val effectiveType = when (selectedType) {
            SpeechServiceType.OPENAI_STT,
            SpeechServiceType.DEEPGRAM_STT,
            -> SpeechServiceType.SHERPA_NCNN
            else -> selectedType
        }
        return createSpeechService(context, effectiveType)
    }

    fun createSpeechService(
        context: Context,
        type: SpeechServiceType,
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        return when (type) {
            SpeechServiceType.SHERPA_NCNN -> acquireLocalSpeechService(context, type)
            SpeechServiceType.OPENAI_STT -> {
                runBlocking {
                    val sttConfig = prefs.sttHttpConfigFlow.first()
                    OpenAISttProvider(
                        context = context,
                        endpointUrl = sttConfig.endpointUrl,
                        apiKey = sttConfig.apiKey,
                        model = sttConfig.modelName,
                    )
                }
            }
            SpeechServiceType.DEEPGRAM_STT -> {
                runBlocking {
                    val sttConfig = prefs.sttHttpConfigFlow.first()
                    DeepgramSttProvider(
                        context = context,
                        endpointUrl = sttConfig.endpointUrl,
                        apiKey = sttConfig.apiKey,
                        model = sttConfig.modelName,
                    )
                }
            }
        }
    }

    private class SpeechServiceLease(
        private val delegate: SpeechService,
        private val onRelease: () -> Unit,
    ) : SpeechService by delegate {
        private val released = AtomicBoolean(false)

        override fun shutdown() {
            if (released.compareAndSet(false, true)) {
                onRelease()
            }
        }
    }

    private data class LocalEntry(
        val type: SpeechServiceType,
        val service: SpeechService,
        var refCount: Int,
    )

    private val localLock = Any()
    private var localEntry: LocalEntry? = null

    private fun acquireLocalSpeechService(
        context: Context,
        type: SpeechServiceType,
    ): SpeechService {
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
                            SpeechServiceType.SHERPA_NCNN -> {
                                val vc = runBlocking { SpeechServicesPreferences(appContext).vadConfigFlow.first() }
                                SherpaSpeechProvider(appContext, vc)
                            }
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
        val toShutdown: SpeechService?
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
    private var instance: SpeechService? = null
    private var currentType: SpeechServiceType? = null

    /**
     * 获取语音识别服务单例实例
     *
     * @param context 应用上下文
     * @return 语音识别服务实例
     */
    fun getInstance(
        context: Context,
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        val selectedType = runBlocking { prefs.sttServiceTypeFlow.first() }
        
        val needNewInstance = instance == null || selectedType != currentType
        
        if (needNewInstance) {
            try {
                instance?.shutdown()
            } catch (_: Exception) {
            }

            val created =
                try {
                    createSpeechService(context)
                } catch (e: IllegalStateException) {
                    AppLogger.w(TAG, "Failed to create SpeechService for type=$selectedType, keeping previous instance", e)
                    null
                }

            if (created != null) {
                instance = created
                currentType = selectedType
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
        try {
            instance?.shutdown()
        } catch (_: Exception) {
        }
        instance = null
        currentType = null
    }
}
