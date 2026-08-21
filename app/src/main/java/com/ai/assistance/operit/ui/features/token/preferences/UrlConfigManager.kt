package com.ai.assistance.operit.ui.features.token.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.token.model.TabConfig
import com.ai.assistance.operit.ui.features.token.model.UrlConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.urlConfigDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "url_config")

class UrlConfigManager(private val context: Context) {
    companion object {
        private val URL_CONFIG_KEY = stringPreferencesKey("url_config")
        
        // 预设配置
        val PRESET_CONFIGS = listOf(
            UrlConfig(
                name = "Claude",
                signInUrl = "https://claude.ai/login",
                tabs = listOf(
                    TabConfig("Chat", "https://claude.ai/chats"),
                    TabConfig("Projects", "https://claude.ai/projects"),
                    TabConfig("Artifacts", "https://claude.ai/artifacts"),
                    TabConfig("Settings", "https://claude.ai/settings")
                )
            ),
            UrlConfig(
                name = "ChatGPT",
                signInUrl = "https://chat.openai.com/auth/login",
                tabs = listOf(
                    TabConfig("Chat", "https://chat.openai.com/"),
                    TabConfig("GPTs", "https://chat.openai.com/gpts"),
                    TabConfig("Settings", "https://chat.openai.com/settings"),
                    TabConfig("Account", "https://platform.openai.com/account")
                )
            ),
            UrlConfig(
                name = "Gemini",
                signInUrl = "https://gemini.google.com/",
                tabs = listOf(
                    TabConfig("Chat", "https://gemini.google.com/app"),
                    TabConfig("History", "https://gemini.google.com/history"),
                    TabConfig("Settings", "https://gemini.google.com/settings"),
                    TabConfig("Help", "https://support.google.com/gemini")
                )
            ),
            UrlConfig(
                name = "Poe",
                signInUrl = "https://poe.com/login",
                tabs = listOf(
                    TabConfig("Chat", "https://poe.com/"),
                    TabConfig("Explore", "https://poe.com/explore"),
                    TabConfig("Create", "https://poe.com/create"),
                    TabConfig("Settings", "https://poe.com/settings")
                )
            )
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    // 获取URL配置的Flow
    val urlConfigFlow: Flow<UrlConfig> = context.urlConfigDataStore.data.map { preferences ->
        val configJson = preferences[URL_CONFIG_KEY]
        if (configJson != null) {
            try {
                json.decodeFromString<UrlConfig>(configJson)
            } catch (e: Exception) {
                UrlConfig().localizePresetTabNames(context)
            }
        } else {
            UrlConfig().localizePresetTabNames(context)
        }
    }

    // 保存URL配置
    suspend fun saveUrlConfig(urlConfig: UrlConfig) {
        context.urlConfigDataStore.edit { preferences ->
            preferences[URL_CONFIG_KEY] = json.encodeToString(urlConfig)
        }
    }

    // 重置为默认配置
    suspend fun resetToDefault() {
        saveUrlConfig(UrlConfig().localizePresetTabNames(context))
    }
}

private fun UrlConfig.localizePresetTabNames(context: Context): UrlConfig {
    if (tabs.isEmpty()) return this

    val mappedTabs = tabs.map { tab ->
        val localizedTitle = when (tab.title) {
            "Chat", "聊天" -> context.getString(R.string.url_tab_chat)
            "Projects", "项目" -> context.getString(R.string.url_tab_projects)
            "Artifacts", "工件" -> context.getString(R.string.url_tab_artifacts)
            "Settings", "设置" -> context.getString(R.string.url_tab_settings)
            "Account", "账户" -> context.getString(R.string.url_tab_account)
            "History", "历史" -> context.getString(R.string.url_tab_history)
            "Help", "帮助" -> context.getString(R.string.url_tab_help)
            "Explore", "探索" -> context.getString(R.string.url_tab_explore)
            "Create", "创建" -> context.getString(R.string.url_tab_create)
            else -> tab.title
        }

        if (localizedTitle == tab.title) tab else TabConfig(title = localizedTitle, url = tab.url)
    }

    return copy(tabs = mappedTabs)
}
