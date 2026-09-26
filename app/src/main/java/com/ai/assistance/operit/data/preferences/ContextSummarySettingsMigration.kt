package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.data.model.ContextSummarySettings
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.SummarySectionOverride
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 把存量用户挂在模型配置上的总结设置迁移到全局默认总结配置。
 *
 * 本特性之前，总结配置（阈值、自定义规则、分段覆盖、对话回顾）的权威来源是模型配置上的
 * summary 字段；改成角色卡 + 全局默认之后这些字段从 ModelConfigData 上删除。不做迁移的话，
 * 自定义过这些配置的存量用户升级后会静默回到默认值，所以这里把旧值搬到全局默认总结配置。
 *
 * 迁移源取全局激活的 chat 模型配置：旧运行时在角色卡没有固定绑定对话模型时用的就是它
 * （effectiveChatConfigTarget 的 activeConfigId 分支）。角色卡固定绑定（FIXED_CONFIG）时
 * 各卡使用自己那份模型配置的总结设置，这份差异不迁移：迁移后统一跟随全局默认。
 *
 * 只执行一次，完成标记写在目标 store（user_preferences）里，与 TokenUsageRepository 的存量
 * 导入同一套路：标记存在即认为已迁移，不再覆盖用户迁移后自己的修改。
 */
internal object ContextSummarySettingsMigration {
    private const val TAG = "ContextSummarySettingsMigration"

    private val migrationMutex = Mutex()

    @Volatile
    private var migrated = false

    suspend fun ensureMigrated(context: Context) {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            val userPreferencesManager = UserPreferencesManager.getInstance(context)
            if (userPreferencesManager.isContextSummaryMigrationDone()) {
                migrated = true
                return
            }

            val sourceConfigId =
                FunctionalConfigManager(context).getConfigIdForFunction(FunctionType.CHAT)
            val legacyJson = ModelConfigManager(context).readLegacySummaryJson(sourceConfigId)
            val legacySettings = parseLegacySummarySettings(legacyJson)
            if (legacySettings != null) {
                userPreferencesManager.saveGlobalContextSummary(legacySettings)
                AppLogger.i(
                    TAG,
                    "已把模型配置 $sourceConfigId 的总结设置迁移到全局默认总结配置"
                )
            } else {
                // 全新安装没有旧配置，或旧 JSON 无法解析：保留全局默认，不覆盖已有值。
                AppLogger.w(TAG, "未找到可迁移的旧总结配置，源配置 $sourceConfigId，保留全局默认")
            }
            userPreferencesManager.markContextSummaryMigrationDone()
            migrated = true
        }
    }

    /**
     * 解析旧模型配置 JSON 里的 summary 字段。
     *
     * 字段名与旧 ModelConfigData 一致，只有对话回顾的两个字段改过名；旧默认值与
     * ContextSummarySettings 的默认值相同，因此旧字段缺失时取默认不会改变行为。
     * 返回 null 表示没有可迁移的旧配置（键不存在、空串或无法解析）。
     */
    internal fun parseLegacySummarySettings(rawJson: String?): ContextSummarySettings? {
        if (rawJson.isNullOrBlank()) return null
        val legacy =
            try {
                legacyJson.decodeFromString<LegacySummaryFields>(rawJson)
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析旧总结配置失败，保留全局默认总结配置", e)
                return null
            }
        return ContextSummarySettings(
            enableSummary = legacy.enableSummary,
            summaryTokenThreshold = legacy.summaryTokenThreshold,
            enableSummaryByMessageCount = legacy.enableSummaryByMessageCount,
            summaryMessageCountThreshold = legacy.summaryMessageCountThreshold,
            summaryCustomRules = legacy.summaryCustomRules,
            summarySectionOverrides = legacy.summarySectionOverrides,
            dialogueReviewEnabled = legacy.enableSummaryDialogueReview,
            dialogueReviewTitle = legacy.summaryDialogueReviewTitle
        )
    }

    private val legacyJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

/** 旧 ModelConfigData 上被删除的总结字段，只用于迁移解析。 */
@Serializable
private data class LegacySummaryFields(
    val enableSummary: Boolean = ContextSummarySettings.DEFAULT_ENABLE_SUMMARY,
    val summaryTokenThreshold: Float = ContextSummarySettings.DEFAULT_SUMMARY_TOKEN_THRESHOLD,
    val enableSummaryByMessageCount: Boolean =
        ContextSummarySettings.DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT,
    val summaryMessageCountThreshold: Int =
        ContextSummarySettings.DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD,
    val summaryCustomRules: String = "",
    val summarySectionOverrides: List<SummarySectionOverride> = emptyList(),
    val enableSummaryDialogueReview: Boolean = true,
    val summaryDialogueReviewTitle: String = ""
)