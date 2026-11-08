package com.ai.assistance.operit.data.chat.folders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatFolderImportValidationTest {
    @Test
    fun validHierarchy_canUseAnExistingParent() {
        val existing = listOf(ChatFolder(id = "root", name = "旧目录"))
        val imported = listOf(
            ChatFolder(id = "child", name = "子目录", parentId = "root"),
            ChatFolder(id = "leaf", name = "末级目录", parentId = "child"),
        )
        assertNull(validateImportedFolders(imported, existing))
    }

    @Test
    fun emptyArchive_isValid() {
        assertNull(validateImportedFolders(emptyList(), emptyList()))
    }

    @Test
    fun duplicateId_identifiesTheSecondRecord() {
        val imported = listOf(ChatFolder(id = "same", name = "甲"), ChatFolder(id = "same", name = "乙"))
        assertEquals(
            ChatFolderImportIssue(2, "same", ChatFolderImportErrorKind.DUPLICATE_ID),
            validateImportedFolders(imported, emptyList()),
        )
    }

    @Test
    fun blankId_identifiesTheRecord() {
        assertEquals(
            ChatFolderImportIssue(1, " ", ChatFolderImportErrorKind.EMPTY_ID),
            validateImportedFolders(listOf(ChatFolder(id = " ", name = "目录")), emptyList()),
        )
    }

    @Test
    fun blankName_identifiesTheRecord() {
        assertEquals(
            ChatFolderImportIssue(1, "empty-name", ChatFolderImportErrorKind.EMPTY_NAME),
            validateImportedFolders(listOf(ChatFolder(id = "empty-name", name = " 	")), emptyList()),
        )
    }

    @Test
    fun missingParent_identifiesTheReferencedId() {
        assertEquals(
            ChatFolderImportIssue(1, "child", ChatFolderImportErrorKind.MISSING_PARENT, "missing"),
            validateImportedFolders(listOf(ChatFolder(id = "child", name = "子目录", parentId = "missing")), emptyList()),
        )
    }

    @Test
    fun selfCycle_identifiesTheRecord() {
        assertEquals(
            ChatFolderImportIssue(1, "self", ChatFolderImportErrorKind.CYCLE, "self"),
            validateImportedFolders(listOf(ChatFolder(id = "self", name = "目录", parentId = "self")), emptyList()),
        )
    }

    @Test
    fun indirectCycle_identifiesTheImportedRecord() {
        val imported = listOf(
            ChatFolder(id = "a", name = "甲", parentId = "b"),
            ChatFolder(id = "b", name = "乙", parentId = "c"),
            ChatFolder(id = "c", name = "丙", parentId = "a"),
        )
        assertEquals(ChatFolderImportIssue(1, "a", ChatFolderImportErrorKind.CYCLE, "a"), validateImportedFolders(imported, emptyList()))
    }

    @Test
    fun importedUpdate_cannotCreateACycleThroughExistingFolders() {
        val existing = listOf(
            ChatFolder(id = "root", name = "根"),
            ChatFolder(id = "child", name = "子", parentId = "root"),
        )
        val imported = listOf(ChatFolder(id = "root", name = "根", parentId = "child"))
        assertEquals(ChatFolderImportIssue(1, "root", ChatFolderImportErrorKind.CYCLE, "root"), validateImportedFolders(imported, existing))
    }
}
