package com.ai.assistance.operit.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** 按聊天持久保存未发送文字，界面或进程重建后继续使用同一份草稿。 */
class ChatDraftRepository internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("chat_input_drafts", Context.MODE_PRIVATE)
    )

    fun loadDraft(chatId: String): TextFieldValue? = synchronized(preferences) {
        val prefix = "draft.$chatId"
        val text = preferences.getString("$prefix.text", null) ?: return@synchronized null
        val start = preferences.getInt("$prefix.selection_start", 0).coerceIn(0, text.length)
        val end = preferences.getInt("$prefix.selection_end", 0).coerceIn(0, text.length)
        TextFieldValue(text = text, selection = TextRange(start, end))
    }

    fun saveDraft(chatId: String, value: TextFieldValue) {
        synchronized(preferences) {
            if (value.text.isEmpty()) {
                removeDraft(chatId)
                return
            }
            val prefix = "draft.$chatId"
            if (preferences.getString("$prefix.text", null) == value.text &&
                preferences.getInt("$prefix.selection_start", -1) == value.selection.start &&
                preferences.getInt("$prefix.selection_end", -1) == value.selection.end
            ) {
                return
            }
            // apply 立即更新共享内存并异步合并磁盘写入，输入过程不等待落盘。
            preferences.edit()
                .putString("$prefix.text", value.text)
                .putInt("$prefix.selection_start", value.selection.start)
                .putInt("$prefix.selection_end", value.selection.end)
                .apply()
        }
    }

    fun chatIdsWithDrafts(): Set<String> = synchronized(preferences) {
        preferences.all.keys
            .filter { it.startsWith("draft.") && it.endsWith(".text") }
            .mapTo(mutableSetOf()) { it.removePrefix("draft.").removeSuffix(".text") }
    }

    fun removeDraft(chatId: String) {
        synchronized(preferences) {
            val prefix = "draft.$chatId"
            preferences.edit()
                .remove("$prefix.text")
                .remove("$prefix.selection_start")
                .remove("$prefix.selection_end")
                .apply()
        }
    }
}
