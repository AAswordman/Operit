package com.ai.assistance.operit.core.chat.hooks

import android.content.Context
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ActivePromptHookMetadataTest {
    private val context = mock<Context>()
    private val manager = mock<ChatHistoryManager>()
    private val historyCache = MutableStateFlow<List<ChatHistory>>(emptyList())
    private val instanceField =
        ChatHistoryManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }
    private var previousInstance: Any? = null

    @Before
    fun installManager() {
        previousInstance = instanceField.get(null)
        instanceField.set(null, manager)
        whenever(manager.chatHistoriesFlow).thenReturn(historyCache)
    }

    @After
    fun restoreManager() {
        instanceField.set(null, previousInstance)
    }

    @Test
    fun `first background hook reads a persisted binding while the UI cache is empty`() = runBlocking {
        val persisted = history().copy(characterGroupId = "persisted-group")
        whenever(manager.getChatHistorySnapshot(CHAT_ID)).thenReturn(persisted)

        val resolved = resolveBoundChatForHookMetadata(context, CHAT_ID)

        verify(manager, never()).chatHistoriesFlow
        assertSame(persisted, resolved)
        assertEquals("persisted-group", resolved?.characterGroupId)
        assertEquals(emptyList<ChatHistory>(), historyCache.value)
        assertEquals(0, historyCache.subscriptionCount.value)
    }

    @Test
    fun `hook observes a persisted rebind instead of a stale cached card`() = runBlocking {
        historyCache.value = listOf(history().copy(characterCardName = "old-card"))
        val persisted = history().copy(characterGroupId = "new-group")
        whenever(manager.getChatHistorySnapshot(CHAT_ID)).thenReturn(persisted)

        val resolved = resolveBoundChatForHookMetadata(context, CHAT_ID)

        verify(manager, never()).chatHistoriesFlow
        assertEquals("new-group", resolved?.characterGroupId)
        assertNull(resolved?.characterCardName)
        assertEquals("old-card", historyCache.value.single().characterCardName)
    }

    @Test
    fun `deleted chat does not retain the binding still present in the UI cache`() = runBlocking {
        historyCache.value = listOf(history().copy(characterGroupId = "deleted-group"))
        whenever(manager.getChatHistorySnapshot(CHAT_ID)).thenReturn(null)

        val resolved = resolveBoundChatForHookMetadata(context, CHAT_ID)

        verify(manager, never()).chatHistoriesFlow
        assertNull(resolved)
    }

    private fun history() = ChatHistory(id = CHAT_ID, title = "Chat", messages = emptyList())

    private companion object {
        const val CHAT_ID = "chat"
    }
}
