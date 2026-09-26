package com.ai.assistance.operit.data.db

import com.ai.assistance.operit.data.model.MessageSectionCodec
import com.ai.assistance.operit.data.model.MessageSectionStorage
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 升级后分批把旧正文解析成 sections，避免首次打开数据库时卡住。 */
internal object MessageSectionBackfill {
    private const val TAG = "MessageSectionBackfill"
    private const val BATCH_SIZE = 32

    suspend fun run(database: AppDatabase) = withContext(Dispatchers.IO) {
        backfillTable(database, "messages", "messageId")
        backfillTable(database, "message_variants", "variantId")
    }

    private fun backfillTable(database: AppDatabase, table: String, key: String) {
        val db = database.openHelper.writableDatabase
        while (true) {
            val rows = ArrayList<Triple<Long, String, String>>(BATCH_SIZE)
            db.query(
                "SELECT `$key`, sections, searchText FROM `$table` WHERE sections = '' LIMIT $BATCH_SIZE"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
                }
            }
            if (rows.isEmpty()) return
            db.beginTransaction()
            try {
                rows.forEach { (id, _, legacy) ->
                    val encoded = try {
                        val sections = MessageSectionStorage.decodeLegacy(legacy)
                        MessageSectionStorage.encode(sections) to MessageSectionCodec.searchText(sections)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Backfill failed for $table row $id; preserving legacy text", e)
                        db.execSQL(
                            "UPDATE `$table` SET sections = ? WHERE `$key` = ?",
                            arrayOf("[]", id),
                        )
                        return@forEach
                    }
                    db.execSQL(
                        "UPDATE `$table` SET sections = ?, searchText = ? WHERE `$key` = ?",
                        arrayOf(encoded.first, encoded.second, id),
                    )
                }
                db.setTransactionSuccessful()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Backfill failed for $table", e)
                return
            } finally {
                db.endTransaction()
            }
        }
    }
}
