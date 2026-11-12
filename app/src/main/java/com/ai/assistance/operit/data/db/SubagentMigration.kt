package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the first subagent schema without rebuilding existing conversations or token statistics. */
internal object SubagentMigration : Migration(21, 23) {
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
        exec("ALTER TABLE chats ADD COLUMN chatKind TEXT NOT NULL DEFAULT 'NORMAL'")
        exec("UPDATE chats SET chatKind = 'BRANCH' WHERE parentChatId IS NOT NULL")
        exec("CREATE INDEX index_chats_chatKind ON chats(chatKind)")
        exec("CREATE INDEX index_chats_parentChatId_chatKind ON chats(parentChatId, chatKind)")
        exec(
            """CREATE TABLE subagent_runs (
                id TEXT NOT NULL PRIMARY KEY,
                parentChatId TEXT NOT NULL,
                childChatId TEXT NOT NULL,
                parentToolCallId TEXT,
                agentProfileId TEXT NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                startedAt INTEGER,
                completedAt INTEGER,
                error TEXT,
                agentConfigSnapshot TEXT,
                modelConfigIdSnapshot TEXT,
                modelIndexSnapshot INTEGER,
                toolInvocationCount INTEGER NOT NULL,
                archivedAt INTEGER,
                FOREIGN KEY(parentChatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(childChatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        exec("CREATE INDEX index_subagent_runs_parentChatId ON subagent_runs(parentChatId)")
        exec("CREATE UNIQUE INDEX index_subagent_runs_childChatId ON subagent_runs(childChatId)")
        exec(
            "CREATE INDEX index_subagent_runs_parentChatId_parentToolCallId " +
                "ON subagent_runs(parentChatId, parentToolCallId)"
        )
        exec("CREATE INDEX index_subagent_runs_status ON subagent_runs(status)")
    }
}

/** External collaboration files must not decide whether the chat database can open. */
internal object SubagentPresentationMigration : Migration(23, 24) {
    private fun migrateSchema(exec: (String) -> Unit) {
        exec("ALTER TABLE subagent_runs ADD COLUMN externalOwnerType TEXT")
        exec("ALTER TABLE subagent_runs ADD COLUMN externalOwnerId TEXT")
        exec("ALTER TABLE subagent_runs ADD COLUMN modelRoundCount INTEGER NOT NULL DEFAULT 0")
        exec("CREATE TABLE pending_subagent_owner_restore (id INTEGER NOT NULL PRIMARY KEY)")
        exec("INSERT INTO pending_subagent_owner_restore (id) VALUES (1)")
    }
    override fun migrate(db: SupportSQLiteDatabase) = migrateSchema { db.execSQL(it) }
    override fun migrate(connection: SQLiteConnection) =
        migrateSchema { sql -> connection.prepare(sql).use { it.step() } }
}
