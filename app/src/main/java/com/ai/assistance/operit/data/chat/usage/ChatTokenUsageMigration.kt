package com.ai.assistance.operit.data.chat.usage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 旧总额保留，新增可空明细字段，旧会话不会被假定具有九类历史记录。 */
internal object ChatTokenUsageMigration : Migration(28, 29) {
    const val SQL = "ALTER TABLE chats ADD COLUMN usageBreakdownJson TEXT DEFAULT NULL"
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(SQL)
    }
}
