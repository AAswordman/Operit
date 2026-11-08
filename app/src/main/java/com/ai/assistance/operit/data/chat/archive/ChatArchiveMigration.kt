package com.ai.assistance.operit.data.chat.archive

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 已发布数据库保留全部会话，升级后已有会话默认显示在常规列表中。 */
internal object ChatArchiveMigration : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chats ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
    }
}
