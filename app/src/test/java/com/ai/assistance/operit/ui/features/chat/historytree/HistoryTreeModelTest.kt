package com.ai.assistance.operit.ui.features.chat.historytree

import com.ai.assistance.operit.data.model.ChatFolderEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.SYSTEM_UNGROUPED_FOLDER_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTreeModelTest {
    @Test
    fun `filtered search keeps matching empty folders and only their ancestors`() {
        val nodes = buildVisibleHistoryTree(
            folders = listOf(
                folder("root", "Root"),
                folder("match", "Match", parentId = "root"),
                folder("other", "Other"),
            ),
            histories = emptyList(),
            projection = HistoryTreeProjection.FILTERED,
            includedFolderIds = setOf("match"),
        ).nodes
        assertEquals(listOf("folder:root", "folder:match"), nodes.map { it.stableKey })
    }

    @Test
    fun `folder and chat keys use separate namespaces`() {
        val folder = folder(id = "same", name = "Folder")
        val chat = chat(id = "same", folderId = "same")

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(folder),
                histories = listOf(chat),
                projection = HistoryTreeProjection.ALL,
            ).nodes

        assertEquals(listOf("folder:same", "chat:same"), nodes.map { it.stableKey })
    }

    @Test
    fun `favorites keeps only ancestor branches containing favorite chats`() {
        val keptRoot = folder("kept", "Same")
        val keptChild = folder("child", "Same", parentId = "kept")
        val emptyRoot = folder("empty", "Same", order = 1)

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(keptRoot, keptChild, emptyRoot),
                histories =
                    listOf(
                        chat("favorite", "child", favorite = true),
                        chat("hidden", "empty", favorite = false),
                    ),
                projection = HistoryTreeProjection.FAVORITES,
            ).nodes

        assertEquals(
            listOf("folder:kept", "folder:child", "chat:favorite"),
            nodes.map { it.stableKey },
        )
    }

    @Test
    fun `missing parents and cycles are promoted instead of dropped`() {
        val orphan = folder("orphan", "Orphan", parentId = "missing")
        val first = folder("first", "First", parentId = "second")
        val second = folder("second", "Second", parentId = "first")

        val result =
            buildVisibleHistoryTree(
                folders = listOf(orphan, first, second),
                histories = listOf(chat("cycle-chat", "second")),
                projection = HistoryTreeProjection.ALL,
            )

        assertEquals(setOf("folder:orphan", "folder:first", "folder:second", "chat:cycle-chat"), result.nodes.map { it.stableKey }.toSet())
        assertTrue(result.structurallyInvalidFolderIds.isNotEmpty())
    }

    @Test
    fun `root chats stay inside the fixed ungrouped folder`() {
        val rootFolder = folder("folder", "Folder", order = 5)
        val histories =
            listOf(
                chat("unpinned", null, order = 0),
                chat("pinned", null, order = 10, pinned = true),
            )

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(rootFolder),
                histories = histories,
                projection = HistoryTreeProjection.ALL,
                includeUngroupedFolder = true,
            ).nodes

        assertEquals(
            listOf(
                UNGROUPED_FOLDER_STABLE_KEY,
                "chat:pinned",
                "chat:unpinned",
                "folder:folder",
            ),
            nodes.map { it.stableKey },
        )
        assertEquals(
            listOf(2, 2),
            nodes.filterIsInstance<HistoryTreeNode.Chat>().map { it.depth },
        )
    }

    @Test
    fun `pinned chats sort before persisted order at root and inside folders`() {
        val folder = folder("folder", "Folder")
        val histories =
            listOf(
                chat("root-unpinned", null, order = 0),
                chat("root-pinned", null, order = 10, pinned = true),
                chat("nested-unpinned", "folder", order = 0),
                chat("nested-pinned", "folder", order = 10, pinned = true),
            )

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(folder),
                histories = histories,
                projection = HistoryTreeProjection.ALL,
                includeUngroupedFolder = true,
            ).nodes

        assertEquals(
            listOf(
                UNGROUPED_FOLDER_STABLE_KEY,
                "chat:root-pinned",
                "chat:root-unpinned",
                "folder:folder",
                "chat:nested-pinned",
                "chat:nested-unpinned",
            ),
            nodes.map { it.stableKey },
        )
    }

    @Test
    fun `filtered projection hides unrelated empty branches and keeps ancestors`() {
        val matchingRoot = folder("matching-root", "Root")
        val matchingChild = folder("matching-child", "Child", parentId = "matching-root")
        val unrelated = folder("unrelated", "Empty", order = 1)

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(matchingRoot, matchingChild, unrelated),
                histories = listOf(chat("matching-chat", "matching-child")),
                projection = HistoryTreeProjection.FILTERED,
            ).nodes

        assertEquals(
            listOf("folder:matching-root", "folder:matching-child", "chat:matching-chat"),
            nodes.map { it.stableKey },
        )
    }

    @Test
    fun `ungrouped folder remains visible when favorites has no root chats`() {
        val folder = folder("folder", "Folder")

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(folder),
                histories = listOf(chat("favorite", "folder", favorite = true)),
                projection = HistoryTreeProjection.FAVORITES,
                includeUngroupedFolder = true,
            ).nodes

        assertEquals(
            listOf(UNGROUPED_FOLDER_STABLE_KEY, "folder:folder", "chat:favorite"),
            nodes.map { it.stableKey },
        )
    }

    @Test
    fun `ungrouped folder remains visible and controls root chat expansion`() {
        val expanded =
            buildVisibleHistoryTree(
                folders = emptyList(),
                histories = listOf(chat("root", null, favorite = true)),
                projection = HistoryTreeProjection.FAVORITES,
                includeUngroupedFolder = true,
            ).nodes
        val collapsed =
            buildVisibleHistoryTree(
                folders = emptyList(),
                histories = listOf(chat("root", null, favorite = true)),
                projection = HistoryTreeProjection.FAVORITES,
                includeUngroupedFolder = true,
                isUngroupedFolderCollapsed = true,
            ).nodes

        assertEquals(
            listOf(UNGROUPED_FOLDER_STABLE_KEY, "chat:root"),
            expanded.map { it.stableKey },
        )
        assertEquals(2, expanded.filterIsInstance<HistoryTreeNode.Chat>().single().depth)
        assertEquals(listOf(UNGROUPED_FOLDER_STABLE_KEY), collapsed.map { it.stableKey })
    }

    @Test
    fun `collapsing a folder hides its complete subtree`() {
        val root = folder("root", "A")
        val child = folder("child", "B", parentId = "root")
        val grandchild = folder("grandchild", "C", parentId = "child")

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(root, child, grandchild),
                histories = listOf(chat("nested-chat", "grandchild")),
                projection = HistoryTreeProjection.ALL,
                collapsedFolderIds = setOf("root"),
                includeUngroupedFolder = true,
            ).nodes

        assertEquals(
            listOf(UNGROUPED_FOLDER_STABLE_KEY, "folder:root"),
            nodes.map { it.stableKey },
        )
    }

    @Test
    fun `persisted ungrouped order positions its root container`() {
        val ungrouped = folder(SYSTEM_UNGROUPED_FOLDER_ID, "internal", order = 2)
        val before = folder("before", "Before", order = 0)
        val after = folder("after", "After", order = 3)

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(ungrouped, before, after),
                histories = listOf(chat("root", null, order = 1)),
                projection = HistoryTreeProjection.ALL,
                includeUngroupedFolder = true,
            ).nodes

        assertEquals(
            listOf(
                "folder:before",
                UNGROUPED_FOLDER_STABLE_KEY,
                "chat:root",
                "folder:after",
            ),
            nodes.map { it.stableKey },
        )
    }

    @Test
    fun `legacy system folder references are recovered into ungrouped`() {
        val ungrouped = folder(SYSTEM_UNGROUPED_FOLDER_ID, "internal", order = 0)

        val nodes =
            buildVisibleHistoryTree(
                folders = listOf(ungrouped),
                histories = listOf(chat("legacy", SYSTEM_UNGROUPED_FOLDER_ID)),
                projection = HistoryTreeProjection.ALL,
                includeUngroupedFolder = true,
            ).nodes

        assertEquals(
            listOf(UNGROUPED_FOLDER_STABLE_KEY, "chat:legacy"),
            nodes.map { it.stableKey },
        )
    }

    private fun folder(
        id: String,
        name: String,
        parentId: String? = null,
        order: Long = 0,
    ) = ChatFolderEntity(
        id = id,
        name = name,
        parentFolderId = parentId,
        displayOrder = order,
        createdAt = order,
    )

    private fun chat(
        id: String,
        folderId: String?,
        favorite: Boolean = false,
        pinned: Boolean = false,
        order: Long = 0,
    ) = ChatHistory(
        id = id,
        title = id,
        messages = emptyList(),
        folderId = folderId,
        isFavorite = favorite,
        pinned = pinned,
        displayOrder = order,
    )
}
