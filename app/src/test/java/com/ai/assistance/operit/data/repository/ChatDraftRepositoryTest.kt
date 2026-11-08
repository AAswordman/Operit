package com.ai.assistance.operit.data.repository

import android.content.SharedPreferences
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatDraftRepositoryTest {
    @Test
    fun draftSurvivesOwnerRecreationWithTextAndSelection() {
        val preferences = memoryPreferences()
        val draft = TextFieldValue("待发送的文字🙂\n第二行  ", TextRange(9, 2))
        ChatDraftRepository(preferences).saveDraft("chat-a", draft)

        val restored = ChatDraftRepository(preferences).loadDraft("chat-a")

        assertEquals(draft.text, restored?.text)
        assertEquals(draft.selection, restored?.selection)
    }

    @Test
    fun removingSentDraftDoesNotRemoveAnotherChatsDraft() {
        val preferences = memoryPreferences()
        val repository = ChatDraftRepository(preferences)
        repository.saveDraft("chat-a", TextFieldValue("已经发送"))
        repository.saveDraft("chat-b", TextFieldValue("仍未发送"))

        repository.removeDraft("chat-a")

        val restored = ChatDraftRepository(preferences)
        assertNull(restored.loadDraft("chat-a"))
        assertEquals("仍未发送", restored.loadDraft("chat-b")?.text)
        assertEquals(setOf("chat-b"), restored.chatIdsWithDrafts())
    }

    @Test
    fun clearingInputDoesNotRestoreOldText() {
        val preferences = memoryPreferences()
        val repository = ChatDraftRepository(preferences)
        repository.saveDraft("chat-a", TextFieldValue("随后删除的草稿"))

        repository.saveDraft("chat-a", TextFieldValue(""))

        assertNull(ChatDraftRepository(preferences).loadDraft("chat-a"))
    }

    @Test
    fun whitespaceDraftIsPreservedWithoutTrimming() {
        val preferences = memoryPreferences()
        ChatDraftRepository(preferences).saveDraft("chat-a", TextFieldValue(" \n  "))

        assertEquals(" \n  ", ChatDraftRepository(preferences).loadDraft("chat-a")?.text)
    }

    private fun memoryPreferences(): SharedPreferences {
        // 用同一份持久层模拟多个所有者；Editor 的变更仅在 apply 时一起提交。
        val values = mutableMapOf<String, Any>()
        val preferences = mock<SharedPreferences>()
        whenever(preferences.all).thenAnswer { values.toMap() }
        whenever(preferences.getString(any(), anyOrNull())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            if (values.containsKey(key)) values[key] as String else invocation.getArgument<String?>(1)
        }
        whenever(preferences.getInt(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            if (values.containsKey(key)) values[key] as Int else invocation.getArgument<Int>(1)
        }
        whenever(preferences.edit()).thenAnswer {
            val pending = mutableMapOf<String, Any?>()
            val editor = mock<SharedPreferences.Editor>()
            whenever(editor.putString(any(), anyOrNull())).thenAnswer { invocation ->
                pending[invocation.getArgument<String>(0)] = invocation.getArgument<String?>(1)
                editor
            }
            whenever(editor.putInt(any(), any())).thenAnswer { invocation ->
                pending[invocation.getArgument<String>(0)] = invocation.getArgument<Int>(1)
                editor
            }
            whenever(editor.remove(any())).thenAnswer { invocation ->
                pending[invocation.getArgument<String>(0)] = null
                editor
            }
            doAnswer {
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                null
            }.whenever(editor).apply()
            editor
        }
        return preferences
    }
}