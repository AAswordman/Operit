package com.ai.assistance.operit.data.repository

import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatFolderEntity
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.SYSTEM_UNGROUPED_FOLDER_ID
import com.ai.assistance.operit.data.model.SYSTEM_UNGROUPED_FOLDER_NAME
import java.util.UUID

enum class HistorySiblingKind {
    FOLDER,
    CHAT,
}

data class HistorySiblingSnapshot(
    val kind: HistorySiblingKind,
    val id: String,
    val parentFolderId: String?,
    val displayOrder: Long,
    val pinned: Boolean? = null,
    val isFavorite: Boolean? = null,
    val characterCardName: String? = null,
    val characterGroupId: String? = null,
) {
    val stableKey: String
        get() = if (kind == HistorySiblingKind.FOLDER) "folder:$id" else "chat:$id"

    companion object {
        fun fromChat(chat: ChatEntity): HistorySiblingSnapshot =
            HistorySiblingSnapshot(
                kind = HistorySiblingKind.CHAT,
                id = chat.id,
                parentFolderId = chat.folderId,
                displayOrder = chat.displayOrder,
                pinned = chat.pinned,
                isFavorite = chat.isFavorite,
                characterCardName = chat.characterCardName,
                characterGroupId = chat.characterGroupId,
            )

        fun fromFolder(folder: ChatFolderEntity): HistorySiblingSnapshot =
            HistorySiblingSnapshot(
                kind = HistorySiblingKind.FOLDER,
                id = folder.id,
                parentFolderId = folder.parentFolderId,
                displayOrder = folder.displayOrder,
            )
    }
}

/**
 * Stable-ID folder mutations. Every structural write is validated and committed in one Room
 * transaction; names are display data only and never identify a target.
 */
class ChatFolderRepository(
    private val database: AppDatabase,
) {
    private val folderDao = database.chatFolderDao()
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()

    suspend fun ensureUngroupedFolder() {
        database.withTransaction {
            if (folderDao.getFolder(SYSTEM_UNGROUPED_FOLDER_ID) == null) {
                val folders = folderDao.getFolders()
                val chats = chatDao.getAllChatsDirectly()
                val firstRootOrder =
                    siblings(parentFolderId = null, folders = folders, chats = chats)
                        .minOfOrNull { it.displayOrder }
                        ?: 0L
                folderDao.insertFolder(
                    ChatFolderEntity(
                        id = SYSTEM_UNGROUPED_FOLDER_ID,
                        name = SYSTEM_UNGROUPED_FOLDER_NAME,
                        parentFolderId = null,
                        displayOrder =
                            if (firstRootOrder == Long.MIN_VALUE) {
                                Long.MIN_VALUE
                            } else {
                                firstRootOrder - 1L
                            },
                        createdAt = 0L,
                    )
                )
            }
            chatDao.clearFolderReferences(SYSTEM_UNGROUPED_FOLDER_ID)
        }
    }

    suspend fun createFolder(parentFolderId: String?, name: String): String =
        database.withTransaction {
            createFolderInCurrentTransaction(parentFolderId, name)
        }

    /**
     * Creates a folder and its first normal chat as one persisted unit.
     *
     * The optional opening message belongs to [initialChat] and is committed in the same Room
     * transaction, so a failed chat or message insert cannot leave an unusable empty folder.
     */
    suspend fun createFolderWithInitialChat(
        parentFolderId: String?,
        name: String,
        initialChat: ChatEntity,
        openingMessage: MessageEntity? = null,
    ): ChatEntity =
        database.withTransaction {
            require(openingMessage == null || openingMessage.chatId == initialChat.id) {
                "The opening message must belong to the initial chat"
            }

            val folderId = createFolderInCurrentTransaction(parentFolderId, name)
            val persistedChat =
                initialChat.copy(
                    folderId = folderId,
                    updatedAt =
                        if (openingMessage == null) {
                            initialChat.updatedAt
                        } else {
                            System.currentTimeMillis()
                        },
                    lastMessageAt = openingMessage?.timestamp ?: initialChat.lastMessageAt,
                )
            chatDao.insertChat(persistedChat)
            openingMessage?.let { messageDao.insertMessage(it) }
            persistedChat
        }

    suspend fun renameFolder(folderId: String, newName: String) {
        database.withTransaction {
            require(folderId != SYSTEM_UNGROUPED_FOLDER_ID) {
                "The ungrouped folder cannot be renamed"
            }
            val normalizedName = newName.trim()
            require(normalizedName.isNotEmpty()) { "Folder name must not be blank" }
            val folder = requireNotNull(folderDao.getFolder(folderId)) { "Folder does not exist" }
            folderDao.updateFolder(folder.copy(name = normalizedName))
        }
    }

    suspend fun moveFolder(
        folderId: String,
        targetParentFolderId: String?,
        expectedSourceSiblings: List<HistorySiblingSnapshot>,
        expectedTargetSiblings: List<HistorySiblingSnapshot>,
        beforeNodeKey: String? = null,
        afterNodeKey: String? = null,
        allowAppendToNonEmptyTarget: Boolean = true,
    ) {
        database.withTransaction {
            val folders = folderDao.getFolders()
            val chats = chatDao.getAllChatsDirectly()
            val folderById = folders.associateBy { it.id }
            val moving = requireNotNull(folderById[folderId]) { "Folder does not exist" }
            require(targetParentFolderId != SYSTEM_UNGROUPED_FOLDER_ID) {
                "The ungrouped folder cannot contain folders"
            }
            if (folderId == SYSTEM_UNGROUPED_FOLDER_ID) {
                require(targetParentFolderId == null) {
                    "The ungrouped folder must stay at the root"
                }
            }
            val actualSourceSiblings =
                siblings(moving.parentFolderId, folders, chats).map { it.snapshot }
            require(expectedSourceSiblings == actualSourceSiblings) {
                "Folder order changed; refresh and try again"
            }
            val actualTargetSiblings =
                siblings(targetParentFolderId, folders, chats).map { it.snapshot }
            require(expectedTargetSiblings == actualTargetSiblings) {
                "Target folder order changed; refresh and try again"
            }
            require(targetParentFolderId == null || targetParentFolderId in folderById) {
                "Target parent folder does not exist"
            }
            require(targetParentFolderId != folderId) { "A folder cannot contain itself" }
            require(!isDescendant(targetParentFolderId, folderId, folderById)) {
                "A folder cannot be moved into its descendant"
            }
            val targetParentDepth = folderDepth(targetParentFolderId, folders)
            val movedHeight = subtreeHeight(folderId, folders)
            require(targetParentDepth + movedHeight <= MAX_DEPTH) {
                "Folder depth cannot exceed $MAX_DEPTH"
            }

            val sourceAfter =
                siblings(moving.parentFolderId, folders, chats)
                    .filterNot { it.stableKey == "folder:$folderId" }
            val targetAfter =
                if (moving.parentFolderId == targetParentFolderId) {
                    sourceAfter.toMutableList()
                } else {
                    siblings(targetParentFolderId, folders, chats)
                        .filterNot { it.stableKey == "folder:$folderId" }
                        .toMutableList()
                }
            val insertionIndex =
                insertionIndex(
                    targetAfter = targetAfter,
                    beforeNodeKey = beforeNodeKey,
                    afterNodeKey = afterNodeKey,
                    allowAppendToNonEmptyTarget = allowAppendToNonEmptyTarget,
                )
            targetAfter.add(
                insertionIndex,
                OrderedSibling(folder = moving.copy(parentFolderId = targetParentFolderId)),
            )
            if (moving.parentFolderId != targetParentFolderId) {
                updateSiblingOrders(sourceAfter, moving.parentFolderId)
            }
            updateSiblingOrders(targetAfter, targetParentFolderId)
        }
    }

    /**
     * Moves a chat while preserving hidden siblings.
     *
     * For a projected same-folder reorder, [orderedVisibleNodeKeys] is substituted only into its
     * existing visible slots. For cross-folder moves, [beforeNodeKey]/[afterNodeKey] anchor
     * insertion avoids rebuilding either complete sibling list from a filtered UI.
     */
    suspend fun moveChat(
        chatId: String,
        targetFolderId: String?,
        expectedSourceSiblings: List<HistorySiblingSnapshot>,
        expectedTargetSiblings: List<HistorySiblingSnapshot>,
        orderedVisibleNodeKeys: List<String>? = null,
        beforeNodeKey: String? = null,
        afterNodeKey: String? = null,
        allowAppendToNonEmptyTarget: Boolean = true,
    ) {
        database.withTransaction {
            require(targetFolderId != SYSTEM_UNGROUPED_FOLDER_ID) {
                "Use null for the ungrouped folder"
            }
            val folders = folderDao.getFolders()
            require(targetFolderId == null || folders.any { it.id == targetFolderId }) {
                "Target folder does not exist"
            }
            val chats = chatDao.getAllChatsDirectly()
            val moving = requireNotNull(chats.firstOrNull { it.id == chatId }) {
                "Chat does not exist"
            }
            val source =
                siblings(moving.folderId, folders, chats)
            val target =
                if (moving.folderId == targetFolderId) {
                    source
                } else {
                    siblings(targetFolderId, folders, chats)
                }
            val actualSourceSnapshot = source.map { it.snapshot }
            require(expectedSourceSiblings == actualSourceSnapshot) {
                "Chat order changed; refresh and try again"
            }
            val actualTargetSnapshot = target.map { it.snapshot }
            require(expectedTargetSiblings == actualTargetSnapshot) {
                "Target chat order changed; refresh and try again"
            }

            if (moving.folderId == targetFolderId && orderedVisibleNodeKeys != null) {
                require(orderedVisibleNodeKeys.isNotEmpty()) { "Visible history order is empty" }
                require(orderedVisibleNodeKeys.distinct().size == orderedVisibleNodeKeys.size) {
                    "Visible history order contains duplicates"
                }
                val visibleKeys = orderedVisibleNodeKeys.toSet()
                require("chat:$chatId" in visibleKeys) {
                    "Moved chat is not in the visible projection"
                }
                require(source.count { it.stableKey in visibleKeys } == visibleKeys.size) {
                    "Visible history projection is stale"
                }
                val byKey = source.associateBy { it.stableKey }
                val iterator = orderedVisibleNodeKeys.iterator()
                val merged =
                    source.map { sibling ->
                        if (sibling.stableKey in visibleKeys) {
                            requireNotNull(byKey[iterator.next()])
                        } else {
                            sibling
                        }
                    }
                updateSiblingOrders(merged, moving.folderId)
                return@withTransaction
            }

            val sourceAfter = source.filterNot { it.stableKey == "chat:$chatId" }
            val targetAfter =
                if (moving.folderId == targetFolderId) {
                    sourceAfter.toMutableList()
                } else {
                    target.filterNot { it.stableKey == "chat:$chatId" }.toMutableList()
                }
            val insertionIndex =
                insertionIndex(
                    targetAfter = targetAfter,
                    beforeNodeKey = beforeNodeKey,
                    afterNodeKey = afterNodeKey,
                    allowAppendToNonEmptyTarget = allowAppendToNonEmptyTarget,
                )
            targetAfter.add(
                insertionIndex.coerceIn(0, targetAfter.size),
                OrderedSibling(chat = moving.copy(folderId = targetFolderId)),
            )
            if (moving.folderId != targetFolderId) {
                updateSiblingOrders(sourceAfter, moving.folderId)
            }
            updateSiblingOrders(targetAfter, targetFolderId)
        }
    }

    /** Reorders an observed chat projection while preserving hidden chat and folder slots. */
    suspend fun reorderProjectedChats(
        expectedChatIds: List<String>,
        orderedChatIds: List<String>,
        expectedFolderIdsByChatId: Map<String, String?>,
        expectedDisplayOrdersByChatId: Map<String, Long>,
    ): Boolean =
        database.withTransaction {
            val expectedIds = expectedChatIds.toSet()
            if (
                expectedIds.isEmpty() ||
                expectedIds.size != expectedChatIds.size ||
                    orderedChatIds.toSet() != expectedIds ||
                    expectedFolderIdsByChatId.keys != expectedIds ||
                    expectedDisplayOrdersByChatId.keys != expectedIds
            ) {
                return@withTransaction false
            }

            val folders = folderDao.getFolders()
            val chats = chatDao.getAllChatsDirectly()
            val currentById = chats.associateBy { it.id }
            if (
                expectedIds.any { it !in currentById } ||
                    expectedIds.any { chatId ->
                        val current = currentById.getValue(chatId)
                        current.folderId != expectedFolderIdsByChatId.getValue(chatId) ||
                            current.displayOrder != expectedDisplayOrdersByChatId.getValue(chatId)
                    }
            ) {
                return@withTransaction false
            }

            val parentFolderIds = expectedChatIds.mapTo(linkedSetOf()) {
                expectedFolderIdsByChatId.getValue(it)
            }
            for (parentFolderId in parentFolderIds) {
                val source = siblings(parentFolderId, folders, chats)
                val expectedProjectedIds =
                    expectedChatIds.filter {
                        expectedFolderIdsByChatId.getValue(it) == parentFolderId
                    }
                val orderedProjectedIds =
                    orderedChatIds.filter {
                        expectedFolderIdsByChatId.getValue(it) == parentFolderId
                    }
                val projectedKeys = expectedProjectedIds.mapTo(hashSetOf()) { "chat:$it" }
                val siblingsByKey = source.associateBy { it.stableKey }
                val orderedIterator = orderedProjectedIds.iterator()
                val merged =
                    source.map { sibling ->
                        if (sibling.stableKey in projectedKeys) {
                            requireNotNull(siblingsByKey["chat:${orderedIterator.next()}"])
                        } else {
                            sibling
                        }
                    }
                updateSiblingOrders(merged, parentFolderId)
            }
            true
        }

    suspend fun getFolderDeletionChatIds(
        folderId: String,
        characterCardName: String?,
        characterGroupId: String?,
    ): Set<String> =
        database.withTransaction {
            findFolderDeletionChats(folderId, characterCardName, characterGroupId)
                .mapTo(linkedSetOf()) { it.id }
        }

    suspend fun deleteFolderWithChats(
        folderId: String,
        characterCardName: String?,
        characterGroupId: String?,
        expectedChatIds: Set<String>,
    ): List<String> =
        database.withTransaction {
            val chatsToDelete =
                findFolderDeletionChats(folderId, characterCardName, characterGroupId)
            check(chatsToDelete.mapTo(linkedSetOf()) { it.id } == expectedChatIds) {
                "Folder deletion candidates changed while preparing deletion"
            }
            chatsToDelete.forEach {
                chatDao.deleteChat(it.id)
            }
            deleteFolder(folderId)
            chatsToDelete.map { it.id }
        }

    private suspend fun findFolderDeletionChats(
        folderId: String,
        characterCardName: String?,
        characterGroupId: String?,
    ): List<ChatEntity> {
        require(folderId != SYSTEM_UNGROUPED_FOLDER_ID) {
            "The ungrouped folder cannot be deleted"
        }
        val folders = folderDao.getFolders()
        require(folders.any { it.id == folderId }) { "Folder does not exist" }
        val descendantFolderIds = linkedSetOf(folderId)
        var addedDescendant: Boolean
        do {
            addedDescendant = false
            folders.forEach { folder ->
                if (
                    folder.parentFolderId in descendantFolderIds &&
                        descendantFolderIds.add(folder.id)
                ) {
                    addedDescendant = true
                }
            }
        } while (addedDescendant)

        val allChats = chatDao.getAllChatsDirectly()
        val scopedChatIds =
            allChats
                .asSequence()
                .filter {
                    it.folderId in descendantFolderIds &&
                        (
                            when {
                                characterGroupId != null ->
                                    it.characterGroupId == characterGroupId
                                characterCardName != null ->
                                    it.characterGroupId == null &&
                                        it.characterCardName == characterCardName
                                else -> true
                            }
                        )
                }
                .mapTo(hashSetOf()) { it.id }
        return allChats.filter { it.id in scopedChatIds && !it.locked }
    }

    suspend fun deleteFolder(folderId: String) {
        database.withTransaction {
            require(folderId != SYSTEM_UNGROUPED_FOLDER_ID) {
                "The ungrouped folder cannot be deleted"
            }
            val folders = folderDao.getFolders()
            val deleted = requireNotNull(folders.firstOrNull { it.id == folderId }) {
                "Folder does not exist"
            }
            val parentId = deleted.parentFolderId
            val chats = chatDao.getAllChatsDirectly()
            val currentParentSiblings = siblings(parentId, folders, chats)
            val deletedIndex =
                currentParentSiblings.indexOfFirst { it.stableKey == "folder:$folderId" }
                    .coerceAtLeast(0)
            val parentAfter =
                currentParentSiblings
                    .filterNot { it.stableKey == "folder:$folderId" }
                    .toMutableList()
            val promoted =
                siblings(folderId, folders, chats).map { sibling ->
                    sibling.withParent(parentId)
                }
            parentAfter.addAll(deletedIndex.coerceIn(0, parentAfter.size), promoted)
            updateSiblingOrders(parentAfter, parentId)
            folderDao.deleteFolder(folderId)
        }
    }

    private suspend fun updateSiblingOrders(
        siblings: List<OrderedSibling>,
        parentFolderId: String?,
    ) {
        val folderUpdates = mutableListOf<ChatFolderEntity>()
        siblings.forEachIndexed { index, sibling ->
            sibling.folder?.let { folder ->
                folderUpdates +=
                    folder.copy(
                        parentFolderId = parentFolderId,
                        displayOrder = index.toLong(),
                    )
            }
            sibling.chat?.let { chat ->
                chatDao.updateChatOrderAndFolder(
                    chatId = chat.id,
                    displayOrder = index.toLong(),
                    folderId = parentFolderId,
                )
            }
        }
        if (folderUpdates.isNotEmpty()) {
            folderDao.updateFolders(folderUpdates)
        }
    }

    private fun siblings(
        parentFolderId: String?,
        folders: List<ChatFolderEntity>,
        chats: List<ChatEntity>,
    ): List<OrderedSibling> =
        (
            folders
                .asSequence()
                .filter { it.parentFolderId == parentFolderId }
                .map { OrderedSibling(folder = it) } +
                chats
                    .asSequence()
                    .filter {
                        it.folderId == parentFolderId
                    }
                    .map { OrderedSibling(chat = it) }
        ).sortedWith(siblingComparator).toList()

    private fun insertionIndex(
        targetAfter: List<OrderedSibling>,
        beforeNodeKey: String?,
        afterNodeKey: String?,
        allowAppendToNonEmptyTarget: Boolean,
    ): Int =
        when {
            beforeNodeKey != null -> {
                val index = targetAfter.indexOfFirst { it.stableKey == beforeNodeKey }
                require(index >= 0) { "Target before-anchor is stale" }
                index
            }
            afterNodeKey != null -> {
                val index = targetAfter.indexOfFirst { it.stableKey == afterNodeKey }
                require(index >= 0) { "Target after-anchor is stale" }
                index + 1
            }
            else -> {
                require(allowAppendToNonEmptyTarget || targetAfter.isEmpty()) {
                    "Target projection is hidden; expand it and try again"
                }
                targetAfter.size
            }
        }

    private fun folderDepth(folderId: String?, folders: List<ChatFolderEntity>): Int {
        if (folderId == null) return 0
        val byId = folders.associateBy { it.id }
        val visited = hashSetOf<String>()
        var depth = 0
        var current: String = folderId
        while (true) {
            require(visited.add(current)) { "Folder hierarchy contains a cycle" }
            val folder = requireNotNull(byId[current]) { "Folder does not exist" }
            depth++
            current = folder.parentFolderId ?: return depth
        }
    }

    private suspend fun createFolderInCurrentTransaction(
        parentFolderId: String?,
        name: String,
    ): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Folder name must not be blank" }
        val folders = folderDao.getFolders()
        require(parentFolderId == null || folders.any { it.id == parentFolderId }) {
            "Parent folder does not exist"
        }
        require(parentFolderId != SYSTEM_UNGROUPED_FOLDER_ID) {
            "The ungrouped folder cannot contain folders"
        }
        require(folderDepth(parentFolderId, folders) + 1 <= MAX_DEPTH) {
            "Folder depth cannot exceed $MAX_DEPTH"
        }
        val id = allocateFolderId(folders.mapTo(hashSetOf()) { it.id })
        val chats = chatDao.getAllChatsDirectly()
        val nextOrder =
            siblings(parentFolderId, folders, chats)
                .maxOfOrNull { it.displayOrder }
                ?.plus(1)
                ?: 0L
        folderDao.insertFolder(
            ChatFolderEntity(
                id = id,
                name = normalizedName,
                parentFolderId = parentFolderId,
                displayOrder = nextOrder,
                createdAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    private fun subtreeHeight(folderId: String, folders: List<ChatFolderEntity>): Int {
        val children = folders.groupBy { it.parentFolderId }
        fun height(id: String, visiting: MutableSet<String>): Int {
            require(visiting.add(id)) { "Folder hierarchy contains a cycle" }
            val childHeight =
                children[id].orEmpty().maxOfOrNull { child -> height(child.id, visiting) } ?: 0
            visiting.remove(id)
            return childHeight + 1
        }
        return height(folderId, hashSetOf())
    }

    private fun isDescendant(
        candidateId: String?,
        ancestorId: String,
        folderById: Map<String, ChatFolderEntity>,
    ): Boolean {
        var current = candidateId
        val visited = hashSetOf<String>()
        while (current != null && visited.add(current)) {
            if (current == ancestorId) return true
            current = folderById[current]?.parentFolderId
        }
        return false
    }

    private fun allocateFolderId(allocated: MutableSet<String>): String {
        while (true) {
            val candidate = UUID.randomUUID().toString()
            if (allocated.add(candidate)) return candidate
        }
    }

    private companion object {
        const val MAX_DEPTH = 3
        val siblingComparator =
            compareBy<OrderedSibling> { it.displayOrder }
                .thenBy { it.kind }
                .thenBy { it.id }
    }
}

private data class OrderedSibling(
    val folder: ChatFolderEntity? = null,
    val chat: ChatEntity? = null,
) {
    init {
        require((folder == null) != (chat == null))
    }

    val kind: HistorySiblingKind
        get() = if (folder != null) HistorySiblingKind.FOLDER else HistorySiblingKind.CHAT
    val id: String
        get() = folder?.id ?: requireNotNull(chat).id
    val stableKey: String
        get() = if (folder != null) "folder:$id" else "chat:$id"
    val displayOrder: Long
        get() = folder?.displayOrder ?: requireNotNull(chat).displayOrder
    val snapshot: HistorySiblingSnapshot
        get() =
            folder?.let(HistorySiblingSnapshot::fromFolder)
                ?: HistorySiblingSnapshot.fromChat(requireNotNull(chat))

    fun withParent(parentFolderId: String?): OrderedSibling =
        folder?.let { copy(folder = it.copy(parentFolderId = parentFolderId)) }
            ?: copy(chat = requireNotNull(chat).copy(folderId = parentFolderId))
}
