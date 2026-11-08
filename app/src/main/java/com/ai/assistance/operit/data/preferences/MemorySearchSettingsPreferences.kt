package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.data.memory.search.MemoryScoreMode
import com.ai.assistance.operit.data.memory.search.MemorySearchConfig

class MemorySearchSettingsPreferences(context: Context, profileId: String) {
    private val searchPrefs = context.applicationContext.getSharedPreferences(
        "memory_search_settings_$profileId",
        Context.MODE_PRIVATE
    )

    private val legacyCloudPrefs = context.applicationContext.getSharedPreferences(
        "cloud_embedding_settings_$profileId", Context.MODE_PRIVATE
    )

    fun load(): MemorySearchConfig {
        val config = MemorySearchConfig(
            scoreMode = MemoryScoreMode.entries[searchPrefs.getInt(KEY_SCORE_MODE, MemoryScoreMode.BALANCED.ordinal)],
            keywordWeight = searchPrefs.getFloat(KEY_KEYWORD_WEIGHT, 10.0f),
            tagWeight = searchPrefs.getFloat(KEY_TAG_WEIGHT, 0.0f),
            vectorWeight = searchPrefs.getFloat(KEY_VECTOR_WEIGHT, 0.0f),
            edgeWeight = searchPrefs.getFloat(KEY_EDGE_WEIGHT, 0.4f),
            minSemanticSimilarity = searchPrefs.getFloat(
                KEY_MIN_SEMANTIC_SIMILARITY,
                MemorySearchConfig.DEFAULT_MIN_SEMANTIC_SIMILARITY
            )
        )
        return config.normalized()
    }

    fun save(config: MemorySearchConfig) {
        val normalized = config.normalized()
        searchPrefs.edit()
            .putInt(KEY_SCORE_MODE, normalized.scoreMode.ordinal)
            .putFloat(KEY_KEYWORD_WEIGHT, normalized.keywordWeight)
            .putFloat(KEY_TAG_WEIGHT, normalized.tagWeight)
            .putFloat(KEY_VECTOR_WEIGHT, normalized.vectorWeight)
            .putFloat(KEY_EDGE_WEIGHT, normalized.edgeWeight)
            .putFloat(KEY_MIN_SEMANTIC_SIMILARITY, normalized.minSemanticSimilarity)
            .apply()
    }

    fun reset() {
        save(MemorySearchConfig())
    }

    fun loadAutoSaveIntervalMinutes(): Int {
        return searchPrefs.getInt(KEY_AUTO_SAVE_INTERVAL_MINUTES, DEFAULT_AUTO_SAVE_INTERVAL_MINUTES)
            .coerceIn(MIN_AUTO_SAVE_INTERVAL_MINUTES, MAX_AUTO_SAVE_INTERVAL_MINUTES)
    }

    fun saveAutoSaveIntervalMinutes(minutes: Int) {
        val normalized =
            minutes.coerceIn(MIN_AUTO_SAVE_INTERVAL_MINUTES, MAX_AUTO_SAVE_INTERVAL_MINUTES)
        searchPrefs.edit()
            .putInt(KEY_AUTO_SAVE_INTERVAL_MINUTES, normalized)
            .putLong(
                KEY_NEXT_AUTO_SAVE_RUN_AT_MS,
                System.currentTimeMillis() + normalized * 60_000L
            )
            .apply()
    }

    fun loadMemoryExtractionCustomRules(): String {
        return requireNotNull(
            searchPrefs.getString(
                KEY_MEMORY_EXTRACTION_CUSTOM_RULES,
                DEFAULT_MEMORY_EXTRACTION_CUSTOM_RULES
            )
        ) { "Memory extraction custom rules must not be null" }
    }

    fun saveMemoryExtractionCustomRules(rules: String) {
        searchPrefs.edit()
            .putString(KEY_MEMORY_EXTRACTION_CUSTOM_RULES, rules)
            .apply()
    }

    fun loadNextAutoSaveRunAtMs(): Long {
        return searchPrefs.getLong(KEY_NEXT_AUTO_SAVE_RUN_AT_MS, 0L)
    }

    fun saveNextAutoSaveRunAtMs(timestampMs: Long) {
        searchPrefs.edit()
            .putLong(KEY_NEXT_AUTO_SAVE_RUN_AT_MS, timestampMs.coerceAtLeast(0L))
            .apply()
    }

    /** 只检测旧配置是否存在，保留原端点和密钥供用户在功能模型中重新配置。 */
    fun hasLegacyCloudEmbeddingConfiguration(): Boolean {
        val values = legacyCloudPrefs.all
        return values.any { (key, value) ->
            key in setOf("endpoint", "api_key", "model") && value is String && value.isNotBlank()
        }
    }

    fun loadCloudEmbeddingEnabled(): Boolean = searchPrefs.getBoolean(KEY_CLOUD_EMBEDDING_ENABLED, true)

    fun saveCloudEmbeddingEnabled(enabled: Boolean) {
        searchPrefs.edit().putBoolean(KEY_CLOUD_EMBEDDING_ENABLED, enabled).apply()
    }

    companion object {
        private const val KEY_CLOUD_EMBEDDING_ENABLED = "cloud_embedding_enabled"
        private const val KEY_SCORE_MODE = "score_mode"
        private const val KEY_KEYWORD_WEIGHT = "keyword_weight"
        private const val KEY_TAG_WEIGHT = "tag_weight"
        private const val KEY_VECTOR_WEIGHT = "vector_weight"
        private const val KEY_EDGE_WEIGHT = "edge_weight"
        private const val KEY_MIN_SEMANTIC_SIMILARITY = "min_semantic_similarity"
        private const val KEY_AUTO_SAVE_INTERVAL_MINUTES = "auto_save_interval_minutes"
        private const val KEY_NEXT_AUTO_SAVE_RUN_AT_MS = "next_auto_save_run_at_ms"
        private const val KEY_MEMORY_EXTRACTION_CUSTOM_RULES = "memory_extraction_custom_rules"

        const val DEFAULT_AUTO_SAVE_INTERVAL_MINUTES = 5
        const val MIN_AUTO_SAVE_INTERVAL_MINUTES = 1
        const val MAX_AUTO_SAVE_INTERVAL_MINUTES = 30
        const val DEFAULT_MEMORY_EXTRACTION_CUSTOM_RULES = ""
    }
}
