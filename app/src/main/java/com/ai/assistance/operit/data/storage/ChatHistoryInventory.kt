package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class ChatStorageEntry(
    val chat: ChatHistory,
    val messageCount: Int,
    val estimatedBytes: Long,
    val databaseBytes: Long,
    val canDelete: Boolean,
    val isCurrent: Boolean,
    val characterCardId: String? = null,
    val avatarUri: String? = null,
) {
    val id: String get() = chat.id
    val title: String get() = chat.title
    val characterName: String? get() = chat.characterCardName
    val hasWorkspace: Boolean get() = !chat.workspace.isNullOrBlank()
    val updatedAtMillis: Long
        get() = chat.updatedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

data class ChatHistorySnapshot(
    val chats: List<ChatStorageEntry>,
    val chatCount: Int,
    val messageCount: Int,
    val databaseBytes: Long,
    val estimatedBytes: Long,
    val scannedAtMillis: Long,
)

class ChatHistoryInventory(context: Context) {
    private val appContext = context.applicationContext
    private val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
    private val characterCardManager = CharacterCardManager.getInstance(appContext)
    private val userPreferences = UserPreferencesManager.getInstance(appContext)
    private val database = AppDatabase.getDatabase(appContext)
    private val storageRepository = DataStorageRepository(appContext)

    suspend fun load(): ChatHistorySnapshot = withContext(Dispatchers.IO) {
        val chats = chatHistoryManager.chatHistoriesFlow.first()
        val currentId = chatHistoryManager.currentChatIdFlow.first()
        val messageCounts = chatHistoryManager.getMessageCountsByChatId()
        val contentCounts =
            runCatching { database.chatContentDao().getSelectedContentCharacterCountsByChat() }
                .getOrDefault(emptyList())
                .associate { it.chatId to it.contentCharacterCount }
        val databaseFile = appContext.getDatabasePath("app_database")
        val databaseBytes =
            listOf("", "-wal", "-shm", "-journal").sumOf { suffix ->
                File(databaseFile.absolutePath + suffix).takeIf { it.isFile }?.length() ?: 0L
            }
        val totalMessages = messageCounts.values.sum().toLong().coerceAtLeast(1L)
        val cards = characterCardManager.getAllCharacterCards()
        val cardsByName = cards.associateBy { it.name }
        val avatarByCardId = cards.associate { card ->
            card.id to userPreferences.getAiAvatarForCharacterCardFlow(card.id).first()
        }
        val entries = chats.map { chat ->
            val messages = messageCounts[chat.id] ?: 0
            val contentBytes = estimateTextBytes(contentCounts[chat.id] ?: 0L)
            val share =
                if (totalMessages <= 0L) 0L else databaseBytes * messages.toLong() / totalMessages
            val characterCardId = chat.characterCardName?.takeIf { it.isNotBlank() }?.let { cardsByName[it]?.id }
            ChatStorageEntry(
                chat = chat,
                messageCount = messages,
                estimatedBytes = contentBytes + share,
                databaseBytes = share,
                canDelete = chat.locked != true && chat.id != currentId,
                isCurrent = chat.id == currentId,
                characterCardId = characterCardId,
                avatarUri = characterCardId?.let { avatarByCardId[it] },
            )
        }
        ChatHistorySnapshot(
            chats = entries,
            chatCount = entries.size,
            messageCount = messageCounts.values.sum(),
            databaseBytes = databaseBytes,
            estimatedBytes = entries.sumOf { it.estimatedBytes },
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(ids: List<String>, onProgress: (String, Int, Int, Long) -> Unit): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        val snapshot = load()
        val byId = snapshot.chats.associateBy { it.id }
        ids.forEachIndexed { index, id ->
            val entry = byId[id]
            onProgress(entry?.title ?: id, index, ids.size, released)
            if (entry == null || !entry.canDelete) {
                failed++
                return@forEachIndexed
            }
            val ok = runCatching { chatHistoryManager.deleteChatHistory(id) }.getOrDefault(false)
            if (ok) {
                deleted++
                released += entry.estimatedBytes
            } else {
                failed++
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }
}

data class StorageDeleteBatchResult(
    val deletedCount: Int,
    val failedCount: Int,
    val releasedBytes: Long,
)
