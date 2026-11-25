package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ai.assistance.operit.data.model.SubagentProfile
import com.ai.assistance.operit.data.model.SubagentRoster
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 子代理档案专用 DataStore。v1 是初始版本：profile_list + profile_${id} + subagents_enabled，
// 0→1 无需迁移历史数据，空迁移仅用于写入版本号。
private val Context.subagentProfileDataStore: DataStore<Preferences> by
    versionedPreferencesDataStore(name = "subagent_profiles", currentVersion = 1) { _, _ -> }

/**
 * 子代理档案管理器。
 *
 * `profile_list` 存 id 集合、`profile_${id}` 存完整 JSON，CRUD 在单次 edit 内读改写，
 * 避免并发丢更新。所有写路径先过 [SubagentRoster.normalize]，保证磁盘上永远是收敛后的名单；
 * 读取时同样 normalize，兼容历史脏数据。
 */
class SubagentProfileManager private constructor(private val context: Context) {

    private val dataStore = context.subagentProfileDataStore
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private val TAG = "SubagentProfileManager"
        private val PROFILE_LIST_KEY = stringSetPreferencesKey("profile_list")
        private val ENABLED_KEY = booleanPreferencesKey("subagents_enabled")
        private const val DEFAULT_ENABLED = true

        private fun profileKey(id: String) = stringPreferencesKey("profile_$id")

        @Volatile
        private var INSTANCE: SubagentProfileManager? = null

        fun getInstance(context: Context): SubagentProfileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubagentProfileManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /** 规范化后的名单 Flow（内置通用子代理始终在列）。 */
    val profileListFlow: Flow<List<SubagentProfile>> =
        dataStore.data.map { preferences -> loadFrom(preferences) }

    /** 子代理总开关。关闭时不向模型暴露委派工具与名单，但配置仍可编辑。 */
    val subagentsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[ENABLED_KEY] ?: DEFAULT_ENABLED }

    /** 读取单个档案（规范化视图内的快照）。 */
    suspend fun getProfile(id: String): SubagentProfile? =
        profileListFlow.first().firstOrNull { it.id == id }

    suspend fun upsertProfile(profile: SubagentProfile) {
        upsertProfiles(listOf(profile))
    }

    /** 批量写回：一次 edit 内完成替换/追加，排序调整也走同一条 normalize 通道。 */
    suspend fun upsertProfiles(profiles: List<SubagentProfile>) {
        dataStore.edit { preferences ->
            val current = loadFrom(preferences)
            val replaced = profiles.fold(current) { acc, incoming ->
                if (acc.any { it.id == incoming.id }) {
                    acc.map { if (it.id == incoming.id) incoming else it }
                } else {
                    acc + incoming
                }
            }
            applyTo(preferences, replaced)
        }
    }

    suspend fun deleteProfile(id: String) {
        // 内置通用子代理是协议默认执行者，删除会破坏「未指定名 = 通用」的路由语义，
        // 所以直接拒绝而不是静默通过。
        require(id != SubagentProfile.BUILT_IN_ID) { "The built-in general subagent cannot be deleted" }
        dataStore.edit { preferences ->
            val current = loadFrom(preferences)
            applyTo(preferences, current.filterNot { it.id == id })
        }
    }

    suspend fun setSubagentsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ENABLED_KEY] = enabled }
    }

    suspend fun isSubagentsEnabled(): Boolean = subagentsEnabledFlow.first()

    private fun loadFrom(preferences: Preferences): List<SubagentProfile> {
        val ids = preferences[PROFILE_LIST_KEY]?.toList().orEmpty()
        val profiles = ids.mapNotNull { id ->
            val raw = preferences[profileKey(id)] ?: return@mapNotNull null
            try {
                json.decodeFromString<SubagentProfile>(raw)
            } catch (error: Exception) {
                // 单条损坏只丢该行，其余名单不受影响；抛出会让整个 DataStore 读不可用。
                AppLogger.e(TAG, "忽略损坏的子代理档案 $id", error)
                null
            }
        }
        return SubagentRoster.normalize(profiles)
    }

    private fun applyTo(preferences: MutablePreferences, profiles: List<SubagentProfile>) {
        val normalized = SubagentRoster.normalize(profiles)
        // 先清理已不存在的行，再写回完整名单，避免截断/删除后留下孤儿 JSON。
        val existingIds = preferences[PROFILE_LIST_KEY]?.toList().orEmpty()
        val keptIds = normalized.map { it.id }.toSet()
        existingIds.filterNot { it in keptIds }.forEach { id -> preferences.remove(profileKey(id)) }

        preferences[PROFILE_LIST_KEY] = normalized.map { it.id }.toSet()
        normalized.forEach { profile ->
            preferences[profileKey(profile.id)] = json.encodeToString(profile)
        }
    }
}
