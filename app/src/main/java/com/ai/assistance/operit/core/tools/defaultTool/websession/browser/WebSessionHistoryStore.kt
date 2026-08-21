package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private val Context.webSessionHistoryDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "web_session_browser_store")

internal class WebSessionHistoryStore private constructor(private val context: Context) {

    companion object {
        private val KEY_BOOKMARKS = stringPreferencesKey("bookmarks_json")
        private val KEY_HISTORY = stringPreferencesKey("history_json")
        private val KEY_DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        private const val MAX_HISTORY_ENTRIES = 500

        @Volatile private var instance: WebSessionHistoryStore? = null

        fun getInstance(context: Context): WebSessionHistoryStore {
            return instance ?: synchronized(this) {
                instance ?: WebSessionHistoryStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val bookmarksFlow: Flow<List<WebSessionBookmark>> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            decodeBookmarks(preferences[KEY_BOOKMARKS])
                .sortedByDescending { it.updatedAt }
        }

    val historyFlow: Flow<List<WebSessionHistoryEntry>> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            decodeHistory(preferences[KEY_HISTORY])
                .sortedByDescending { it.visitedAt }
        }

    val desktopModeFlow: Flow<Boolean> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            preferences[KEY_DESKTOP_MODE] ?: true
        }

    suspend fun recordVisit(url: String, title: String, isReload: Boolean) {
        val normalizedUrl = normalizeUrl(url) ?: return
        if (isReload) {
            updateTitle(normalizedUrl, title)
            return
        }

        val now = System.currentTimeMillis()
        context.webSessionHistoryDataStore.edit { preferences ->
            val current = decodeHistory(preferences[KEY_HISTORY])
            val normalizedTitle = normalizeTitle(title, normalizedUrl)
            val updated =
                if (current.firstOrNull()?.url == normalizedUrl) {
                    current.toMutableList().also { list ->
                        list[0] = list[0].copy(
                            title = normalizedTitle.ifBlank { list[0].title },
                            visitedAt = now
                        )
                    }
                } else {
                    buildList {
                        add(
                            WebSessionHistoryEntry(
                                url = normalizedUrl,
                                title = normalizedTitle,
                                visitedAt = now
                            )
                        )
                        addAll(current.take(MAX_HISTORY_ENTRIES - 1))
                    }
                }
            preferences[KEY_HISTORY] = json.encodeToString(updated.take(MAX_HISTORY_ENTRIES))
        }
    }

    suspend fun updateTitle(url: String, title: String) {
        val normalizedUrl = normalizeUrl(url) ?: return
        val normalizedTitle = normalizeTitle(title, normalizedUrl)

        context.webSessionHistoryDataStore.edit { preferences ->
            val history =
                decodeHistory(preferences[KEY_HISTORY]).map { entry ->
                    if (entry.url == normalizedUrl && normalizedTitle.isNotBlank()) {
                        entry.copy(title = normalizedTitle)
                    } else {
                        entry
                    }
                }

            val bookmarks =
                decodeBookmarks(preferences[KEY_BOOKMARKS]).map { bookmark ->
                    if (bookmark.url == normalizedUrl && normalizedTitle.isNotBlank()) {
                        bookmark.copy(title = normalizedTitle, updatedAt = System.currentTimeMillis())
                    } else {
                        bookmark
                    }
                }

            preferences[KEY_HISTORY] = json.encodeToString(history.take(MAX_HISTORY_ENTRIES))
            preferences[KEY_BOOKMARKS] = json.encodeToString(bookmarks)
        }
    }

    suspend fun addBookmark(url: String, title: String) {
        val normalizedUrl = normalizeUrl(url) ?: return
        val now = System.currentTimeMillis()
        val normalizedTitle = normalizeTitle(title, normalizedUrl)

        context.webSessionHistoryDataStore.edit { preferences ->
            val current = decodeBookmarks(preferences[KEY_BOOKMARKS])
            val existing = current.firstOrNull { it.url == normalizedUrl }
            val updated =
                buildList {
                    add(
                        existing?.copy(
                            title = normalizedTitle.ifBlank { existing.title },
                            updatedAt = now
                        ) ?: WebSessionBookmark(
                            url = normalizedUrl,
                            title = normalizedTitle,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    addAll(current.filterNot { it.url == normalizedUrl })
                }
            preferences[KEY_BOOKMARKS] = json.encodeToString(updated)
        }
    }

    suspend fun removeBookmark(url: String) {
        val normalizedUrl = normalizeUrl(url) ?: return
        context.webSessionHistoryDataStore.edit { preferences ->
            val updated = decodeBookmarks(preferences[KEY_BOOKMARKS]).filterNot { it.url == normalizedUrl }
            preferences[KEY_BOOKMARKS] = json.encodeToString(updated)
        }
    }

    suspend fun toggleBookmark(url: String, title: String): Boolean {
        val normalizedUrl = normalizeUrl(url) ?: return false
        return if (isBookmarked(normalizedUrl)) {
            removeBookmark(normalizedUrl)
            false
        } else {
            addBookmark(normalizedUrl, title)
            true
        }
    }

    suspend fun isBookmarked(url: String): Boolean {
        val normalizedUrl = normalizeUrl(url) ?: return false
        return bookmarksFlow.first().any { it.url == normalizedUrl }
    }

    suspend fun clearHistory() {
        context.webSessionHistoryDataStore.edit { preferences ->
            preferences[KEY_HISTORY] = json.encodeToString(emptyList<WebSessionHistoryEntry>())
        }
    }

    suspend fun setDesktopMode(enabled: Boolean) {
        context.webSessionHistoryDataStore.edit { preferences ->
            preferences[KEY_DESKTOP_MODE] = enabled
        }
    }

    private fun decodeBookmarks(raw: String?): List<WebSessionBookmark> {
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<WebSessionBookmark>>(raw) }.getOrElse { emptyList() }
        }
    }

    private fun decodeHistory(raw: String?): List<WebSessionHistoryEntry> {
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<WebSessionHistoryEntry>>(raw) }.getOrElse { emptyList() }
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return null
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("about:") || lower.startsWith("blob:") || lower.startsWith("data:")) {
            return null
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null
        }

        return runCatching {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val portPart =
                when {
                    uri.port < 0 -> ""
                    scheme == "http" && uri.port == 80 -> ""
                    scheme == "https" && uri.port == 443 -> ""
                    else -> ":${uri.port}"
                }
            val path = uri.encodedPath?.ifBlank { "/" } ?: "/"
            buildString {
                append(scheme)
                append("://")
                append(host)
                append(portPart)
                append(path)
                uri.encodedQuery?.takeIf { it.isNotBlank() }?.let {
                    append('?')
                    append(it)
                }
            }
        }.getOrElse { trimmed }
    }

    private fun normalizeTitle(title: String, fallbackUrl: String): String {
        val trimmed = title.trim()
        return if (trimmed.isBlank()) fallbackUrl else trimmed
    }
}
