package com.ai.assistance.operit.data.annualreport

import androidx.room.Dao
import androidx.room.Query

// 年度报告的只读聚合查询。所有统计都在 SQL 里完成，不把消息正文读进内存；
// 只有工具调用需要解析正文，这里只筛出候选消息的定位信息，正文交给 ChatContentDao 分段读取，
// 避免超大消息直接 SELECT content 触发 SQLiteBlobTooBigException。
// 时间窗口一律左闭右开；strftime 的 'localtime' 与 Token 统计页按天分组的口径一致。

data class SenderCountRow(
    val sender: String,
    val count: Long,
)

data class DayCountRow(
    val localDate: String,
    val count: Long,
)

data class HourCountRow(
    val hour: Int,
    val count: Long,
)

data class FirstMessageRow(
    val timestamp: Long,
    val preview: String,
    val chatTitle: String,
)

data class ChatHighlightRow(
    val title: String,
    val messages: Long,
)

data class CharacterHighlightRow(
    val name: String,
    val messages: Long,
    val chats: Long,
)

data class AiWorkRow(
    val durationMs: Long,
    val longestReplyTokens: Long,
    val firstTrackedAt: Long?,
)

data class MessageLocatorRow(
    val chatId: String,
    val timestamp: Long,
)

@Dao
interface AnnualReportDao {
    @Query("SELECT COUNT(*) FROM chats WHERE createdAt >= :startMs AND createdAt < :endMs")
    suspend fun countChatsCreated(startMs: Long, endMs: Long): Long

    @Query(
        """
        SELECT sender AS sender, COUNT(*) AS count
        FROM messages
        WHERE timestamp >= :startMs AND timestamp < :endMs
        GROUP BY sender
        """
    )
    suspend fun countMessagesBySender(startMs: Long, endMs: Long): List<SenderCountRow>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS localDate,
            COUNT(*) AS count
        FROM messages
        WHERE sender = 'user' AND timestamp >= :startMs AND timestamp < :endMs
        GROUP BY localDate
        ORDER BY localDate
        """
    )
    suspend fun countUserMessagesByDay(startMs: Long, endMs: Long): List<DayCountRow>

    @Query(
        """
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
            COUNT(*) AS count
        FROM messages
        WHERE sender = 'user' AND timestamp >= :startMs AND timestamp < :endMs
        GROUP BY hour
        """
    )
    suspend fun countUserMessagesByHour(startMs: Long, endMs: Long): List<HourCountRow>

    /** 本地时间 0 点到 5 点之间、时刻最晚的一条用户消息。 */
    @Query(
        """
        SELECT timestamp
        FROM messages
        WHERE sender = 'user' AND timestamp >= :startMs AND timestamp < :endMs
            AND CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) < 5
        ORDER BY strftime('%H:%M:%S', timestamp / 1000, 'unixepoch', 'localtime') DESC
        LIMIT 1
        """
    )
    suspend fun findLatestNightMessage(startMs: Long, endMs: Long): Long?

    /** 只取正文开头，供年度第一句话展示；完整正文不离开数据库。 */
    @Query(
        """
        SELECT m.timestamp AS timestamp, SUBSTR(m.content, 1, 600) AS preview, c.title AS chatTitle
        FROM messages m
        INNER JOIN chats c ON c.id = m.chatId
        WHERE m.sender = 'user' AND m.timestamp >= :startMs AND m.timestamp < :endMs
        ORDER BY m.timestamp ASC
        LIMIT 1
        """
    )
    suspend fun findFirstUserMessage(startMs: Long, endMs: Long): FirstMessageRow?

    @Query(
        """
        SELECT c.title AS title, COUNT(*) AS messages
        FROM messages m
        INNER JOIN chats c ON c.id = m.chatId
        WHERE m.timestamp >= :startMs AND m.timestamp < :endMs
        GROUP BY m.chatId
        ORDER BY messages DESC
        LIMIT 1
        """
    )
    suspend fun findLongestChat(startMs: Long, endMs: Long): ChatHighlightRow?

    /** 按对话绑定的角色卡汇总；没有绑定角色卡的对话和群聊不参与。 */
    @Query(
        """
        SELECT c.characterCardName AS name, COUNT(*) AS messages, COUNT(DISTINCT m.chatId) AS chats
        FROM messages m
        INNER JOIN chats c ON c.id = m.chatId
        WHERE m.timestamp >= :startMs AND m.timestamp < :endMs
            AND c.characterCardName IS NOT NULL AND TRIM(c.characterCardName) <> ''
        GROUP BY c.characterCardName
        ORDER BY messages DESC
        LIMIT 1
        """
    )
    suspend fun findTopCharacter(startMs: Long, endMs: Long): CharacterHighlightRow?

    /** messages 表保存原始回复，重新生成的版本单独存在 message_variants，所以变体行数就是重新生成次数。 */
    @Query("SELECT COUNT(*) FROM message_variants WHERE messageTimestamp >= :startMs AND messageTimestamp < :endMs")
    suspend fun countVariants(startMs: Long, endMs: Long): Long

    @Query("SELECT COUNT(*) FROM messages WHERE isFavorite = 1 AND timestamp >= :startMs AND timestamp < :endMs")
    suspend fun countFavorites(startMs: Long, endMs: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(waitDurationMs + outputDurationMs), 0) AS durationMs,
            COALESCE(MAX(outputTokens), 0) AS longestReplyTokens,
            MIN(CASE WHEN waitDurationMs > 0 OR outputDurationMs > 0 THEN timestamp END) AS firstTrackedAt
        FROM messages
        WHERE sender = 'ai' AND timestamp >= :startMs AND timestamp < :endMs
        """
    )
    suspend fun aiWorkInMessages(startMs: Long, endMs: Long): AiWorkRow

    @Query(
        """
        SELECT COALESCE(SUM(waitDurationMs + outputDurationMs), 0) AS durationMs,
            COALESCE(MAX(outputTokens), 0) AS longestReplyTokens,
            MIN(CASE WHEN waitDurationMs > 0 OR outputDurationMs > 0 THEN messageTimestamp END) AS firstTrackedAt
        FROM message_variants
        WHERE messageTimestamp >= :startMs AND messageTimestamp < :endMs
        """
    )
    suspend fun aiWorkInVariants(startMs: Long, endMs: Long): AiWorkRow

    /** 含工具标签的 AI 回复。LIKE 在 SQLite 内部完成筛选，只返回定位信息。 */
    @Query(
        """
        SELECT chatId AS chatId, timestamp AS timestamp
        FROM messages
        WHERE sender = 'ai' AND timestamp >= :startMs AND timestamp < :endMs
            AND content LIKE '%<tool%'
        """
    )
    suspend fun findToolMessages(startMs: Long, endMs: Long): List<MessageLocatorRow>

    @Query(
        """
        SELECT DISTINCT chatId AS chatId, messageTimestamp AS timestamp
        FROM message_variants
        WHERE messageTimestamp >= :startMs AND messageTimestamp < :endMs
            AND content LIKE '%<tool%'
        """
    )
    suspend fun findToolVariantMessages(startMs: Long, endMs: Long): List<MessageLocatorRow>

    @Query("SELECT MIN(occurredAtMs) FROM token_usage_records WHERE occurredAtMs >= :startMs AND occurredAtMs < :endMs")
    suspend fun earliestTokenRecord(startMs: Long, endMs: Long): Long?
}
