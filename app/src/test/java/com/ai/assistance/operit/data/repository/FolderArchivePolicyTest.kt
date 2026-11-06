package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.db.JdbcSQLiteDriver
import com.ai.assistance.operit.data.model.OperitArchivedFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FolderArchivePolicyTest {
    @Test fun rejectsDeepAndCyclicArchivesWithoutRecursingThroughTheInput() {
        val deep = (0..20_000).map { index ->
            OperitArchivedFolder(
                id = "$index", name = "Folder",
                parentFolderId = if (index == 20_000) null else "${index + 1}",
                createdAt = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) { validateArchivedFolderGraph(deep) }
        assertThrows(IllegalArgumentException::class.java) {
            validateArchivedFolderGraph(listOf(OperitArchivedFolder("cycle", "Cycle", "cycle", createdAt = 0)))
        }
    }

    @Test fun fullExportKeepsEmptyFoldersButPartialExportOnlyKeepsSelectedPaths() {
        for (fullExport in listOf(false, true)) {
            JdbcSQLiteDriver().open(":memory:").use { db ->
                fun sql(value: String) = db.prepare(value).use { it.step(); Unit }
                sql("ATTACH DATABASE ':memory:' AS operit_export_source")
                sql("CREATE TABLE operit_export_source.chats (id TEXT, folderId TEXT)")
                sql("CREATE TABLE operit_export_source.chat_folders (id TEXT, name TEXT, parentFolderId TEXT, displayOrder INTEGER, createdAt INTEGER)")
                sql("INSERT INTO operit_export_source.chats VALUES ('a', 'child'), ('b', NULL)")
                sql("INSERT INTO operit_export_source.chat_folders VALUES ('root','Root',NULL,0,0), ('child','Child','root',0,0), ('empty','Empty',NULL,1,0)")
                sql("CREATE TABLE export_chat_ids (id TEXT)")
                sql("INSERT INTO export_chat_ids VALUES ('a')")
                if (fullExport) sql("INSERT INTO export_chat_ids VALUES ('b')")
                sql("CREATE TABLE chats AS SELECT * FROM operit_export_source.chats WHERE id IN (SELECT id FROM export_chat_ids)")
                sql(ARCHIVE_FOLDER_SELECTION_SQL)
                val ids = buildList {
                    db.prepare("SELECT id FROM chat_folders ORDER BY id").use {
                        while (it.step()) add(it.getText(0))
                    }
                }
                assertEquals(if (fullExport) listOf("child", "empty", "root") else listOf("child", "root"), ids)
            }
        }
    }
}
