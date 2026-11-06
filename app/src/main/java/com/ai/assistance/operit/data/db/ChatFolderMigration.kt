package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve released v21 history and convert each scoped legacy group into a stable folder. */
internal object ChatFolderMigration : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { db.execSQL(it) }
    }

    override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql ->
            val statement = connection.prepare(sql)
            try {
                statement.step()
            } finally {
                statement.close()
            }
        }
    }

    private fun migrateSql(exec: (String) -> Unit) {
        exec("ALTER TABLE chats ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        exec("ALTER TABLE chats ADD COLUMN lastMessageAt INTEGER")
        exec(
            """UPDATE chats SET lastMessageAt =
                (SELECT MAX(timestamp) FROM messages WHERE messages.chatId = chats.id)"""
        )
        exec(
            """CREATE TABLE chat_folders (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                parentFolderId TEXT,
                displayOrder INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(parentFolderId) REFERENCES chat_folders(id)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )"""
        )
        exec(
            "CREATE INDEX index_chat_folders_parentFolderId_displayOrder " +
                "ON chat_folders(parentFolderId, displayOrder)"
        )
        exec(
            "ALTER TABLE chats ADD COLUMN folderId TEXT REFERENCES chat_folders(id) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        exec("CREATE INDEX index_chats_folderId ON chats(folderId)")
        // A temporary mapping keeps null scopes distinct from empty strings and avoids loading
        // every chat into memory. Its insertion order works on SQLite versions without window functions.
        exec(
            """CREATE TEMP TABLE folder_migration_scopes (
                folderId TEXT NOT NULL,
                characterCardName TEXT,
                characterGroupId TEXT,
                rawGroup TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )"""
        )
        exec(
            """INSERT INTO folder_migration_scopes
                SELECT lower(hex(randomblob(4))) || '-' ||
                       lower(hex(randomblob(2))) || '-4' ||
                       substr(lower(hex(randomblob(2))), 2) || '-8' ||
                       substr(lower(hex(randomblob(2))), 2) || '-' ||
                       lower(hex(randomblob(6))), characterCardName, characterGroupId,
                       `group`, MIN(createdAt)
                FROM chats
                WHERE `group` IS NOT NULL AND trim(`group`) <> ''
                GROUP BY characterCardName, characterGroupId, `group`
                ORDER BY MIN(displayOrder), MIN(createdAt), characterCardName, characterGroupId, `group`"""
        )
        exec(
            """INSERT INTO chat_folders(id, name, parentFolderId, displayOrder, createdAt)
                SELECT folderId, trim(rawGroup), NULL, rowid - 1, createdAt
                FROM folder_migration_scopes"""
        )
        exec(
            """UPDATE chats SET folderId = (
                SELECT folderId FROM folder_migration_scopes AS scope
                WHERE scope.characterCardName IS chats.characterCardName
                  AND scope.characterGroupId IS chats.characterGroupId
                  AND scope.rawGroup = chats.`group`
            )"""
        )
        exec("DROP TABLE folder_migration_scopes")
    }
}
