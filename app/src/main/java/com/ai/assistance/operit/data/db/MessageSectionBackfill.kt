package com.ai.assistance.operit.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** 升级后分批把旧正文解析成 sections，避免首次打开数据库时卡住。 */
internal object MessageSectionBackfill {
    private const val TAG = "MessageSectionBackfill"
    private const val BATCH_SIZE = 32
    private const val TEXT_CHUNK_CHARACTER_COUNT = 16_384

    suspend fun run(database: AppDatabase) = withContext(Dispatchers.IO) {
        backfillTable(database, "messages", "messageId")
        backfillTable(database, "message_variants", "variantId")
    }

    private suspend fun backfillTable(database: AppDatabase, table: String, key: String) {
        val db = database.openHelper.writableDatabase
        var lastId = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = ArrayList<Pair<Long, String>>(BATCH_SIZE)
            val pendingRows = ArrayList<Pair<Long, Long>>(BATCH_SIZE)
            // 只读取主键和长度，避免大正文直接进入 CursorWindow。
            db.query(
                "SELECT `$key`, LENGTH(searchText) FROM `$table`" +
                    " WHERE `$key` > ? AND sections = '' ORDER BY `$key` LIMIT $BATCH_SIZE",
                arrayOf(lastId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    pendingRows += cursor.getLong(0) to cursor.getLong(1)
                }
            }
            pendingRows.forEach { (id, characterCount) ->
                readLegacyText(db, table, key, id, characterCount)?.let { legacy ->
                    rows += id to legacy
                }
            }

            if (rows.isEmpty()) return

            // 解析、编码和索引生成均在写事务外完成，避免长正文阻塞其他数据库事务。
            val updates = rows.map { (id, legacy) ->
                currentCoroutineContext().ensureActive()
                val encoded = try {
                    val sections = MessageSectionStorage.decodeLegacy(legacy)
                    MessageSectionStorage.encode(sections) to MessageSectionCodec.searchText(sections)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Backfill failed for $table row $id; preserving legacy text", e)
                    "[]" to legacy
                }
                Triple(id, encoded.first, encoded.second)
            }
            currentCoroutineContext().ensureActive()
            db.beginTransaction()
            try {
                updates.forEach { (id, sections, searchText) ->
                    // 准备数据期间可能发生编辑，只更新仍未回填的行。
                    db.execSQL(
                        "UPDATE `$table` SET sections = ?, searchText = ?" +
                            " WHERE `$key` = ? AND sections = ''",
                        arrayOf(sections, searchText, id),
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
            lastId = rows.last().first
            yield()
        }
    }

    private fun readLegacyText(
        db: SupportSQLiteDatabase,
        table: String,
        key: String,
        id: Long,
        characterCount: Long,
    ): String? {
        if (characterCount <= 0L) return ""

        val text = StringBuilder()
        var offset = 0L
        while (offset < characterCount) {
            val chunkLength = minOf(
                TEXT_CHUNK_CHARACTER_COUNT.toLong(),
                characterCount - offset,
            )
            val chunk = db.query(
                "SELECT SUBSTR(searchText, ?, ?) FROM `$table`" +
                    " WHERE `$key` = ? AND sections = '' AND LENGTH(searchText) = ?",
                arrayOf(
                    (offset + 1L).toString(),
                    chunkLength.toString(),
                    id.toString(),
                    characterCount.toString(),
                ),
            ).use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getString(0)
            } ?: return null
            if (chunk.isEmpty()) return null
            text.append(chunk)
            // SQLite SUBSTR 使用字符偏移，不能用 Kotlin UTF-16 长度推进。
            offset += chunkLength
        }
        return text.toString()
    }
}
