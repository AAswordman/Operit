package com.ai.assistance.operit.data.dao

import android.content.Context
import androidx.room.Room
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.JdbcSQLiteDriver
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 真实 Room + JVM SQLite 驱动下的消息读取测试。
 *
 * 重点覆盖 U+0000：SQLite 的 LENGTH/SUBSTR 把 TEXT 当字符处理并在第一个 NUL 处截断，
 * 旧实现会把 `AB + NUL + CD` 读成 `AB`，把整条 PDF 工具结果和后续回复丢掉。
 * 修复后按 BLOB 字节分段，这些用例保证短消息、长消息、跨分块边界和消息变体都能完整还原。
 */
class ChatContentDaoNulContentTest {

    private lateinit var tempDir: File
    private lateinit var database: AppDatabase
    private lateinit var chatContentDao: ChatContentDao

    private val chatId = "chat-nul-content"

    @Before
    fun setUp() = runBlocking {
        tempDir = createTempDirectory("chat-content-nul-test").toFile()
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.packageName).thenReturn("com.ai.assistance.operit")
        whenever(context.filesDir).thenReturn(tempDir)
        whenever(context.cacheDir).thenReturn(tempDir)
        whenever(context.getDatabasePath(any())).thenAnswer { invocation ->
            File(tempDir, invocation.getArgument<String>(0))
        }

        database =
            Room.databaseBuilder(context, AppDatabase::class.java, "app_database")
                .setDriver(JdbcSQLiteDriver())
                .allowMainThreadQueries()
                .build()
        chatContentDao = database.chatContentDao()
        database.chatDao().insertChat(ChatEntity(id = chatId, title = "NUL content"))
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun shortMessageWithNulIsRestoredCompletely() = runBlocking {
        val content = "AB\u0000CD"
        insertMessage(timestamp = 1L, orderIndex = 0, content = content)

        val messages = chatContentDao.getMessagesForChat(chatId)

        assertEquals(1, messages.size)
        assertEquals(content, messages.single().content)
        assertEquals(5, messages.single().content.length)
    }

    @Test
    fun emptyContentIsRestoredAsEmptyString() = runBlocking {
        insertMessage(timestamp = 1L, orderIndex = 0, content = "")

        assertEquals("", chatContentDao.getMessagesForChat(chatId).single().content)
    }

    @Test
    fun messageWithLeadingAndMultipleNulCharactersIsRestoredCompletely() = runBlocking {
        val content = "\u0000A\u0000B\u0000C\u0000"
        insertMessage(timestamp = 1L, orderIndex = 0, content = content)

        val message = chatContentDao.getMessagesForChat(chatId).single()

        assertEquals(content, message.content)
        assertEquals(content.length, message.content.length)
    }

    @Test
    fun longMessageWithNulAndMultibyteCharacterAcrossChunkBoundaryIsRestoredCompletely() =
        runBlocking {
            // 65,535 个 ASCII 字节之后放一个 3 字节汉字：首块正好切在该字符的第一个字节之后，
            // 只有先把所有字节拼起来再按 UTF-8 解码才能得到正确文本。
            val content =
                buildString {
                    repeat(65_535) { append('a') }
                    append('中')
                    append("\u0000boundary-after-nul")
                    repeat(70_000) { append('b') }
                    append('\u0000')
                    append("emoji-😀-tail")
                    repeat(60_000) { append('c') }
                }
            insertMessage(timestamp = 1L, orderIndex = 0, content = content)

            val message = chatContentDao.getMessagesForChat(chatId).single()

            assertEquals(content, message.content)
            assertArrayEquals(content.toByteArray(Charsets.UTF_8), message.content.toByteArray(Charsets.UTF_8))
        }

    @Test
    fun alternativesReadPathsAlsoRestoreNulContent() = runBlocking {
        val content = "start\u0000middle\u0000end"
        insertMessage(timestamp = 100L, orderIndex = 0, content = content)

        assertEquals(content, chatContentDao.getMessageByTimestamp(chatId, 100L)?.content)
        assertEquals(content, chatContentDao.getMessagesForChatAsc(chatId, 10).single().content)
        assertEquals(content, chatContentDao.getMessagesForChatDesc(chatId, 10).single().content)
        assertEquals(
            content,
            chatContentDao.getMessagesForChatInRangeAsc(chatId, null, null, null).single().content,
        )
        assertEquals(
            content,
            chatContentDao.getMessagesForChatBeforeTimestampDesc(chatId, 100L, 10).single().content,
        )
    }

    @Test
    fun longMessageWithoutNulStillUsesChunkedRead() = runBlocking {
        val content = buildString {
            repeat(200_000) { append(('a'.code + (it % 26)).toChar()) }
            append("😀")
        }
        insertMessage(timestamp = 1L, orderIndex = 0, content = content)

        assertEquals(content, chatContentDao.getMessagesForChat(chatId).single().content)
    }

    @Test
    fun shortVariantWithNulIsRestoredCompletely() = runBlocking {
        val baseContent = "base"
        val variantContent = "variant\u0000with\u0000nul"
        insertMessage(timestamp = 10L, orderIndex = 0, content = baseContent, selectedVariantIndex = 1)
        database.messageVariantDao().insertVariant(
            MessageVariantEntity(
                chatId = chatId,
                messageTimestamp = 10L,
                variantIndex = 1,
                content = variantContent,
            )
        )

        val variants = chatContentDao.getVariantsForChat(chatId)
        assertEquals(1, variants.size)
        assertEquals(variantContent, variants.single().content)
        assertEquals(
            variantContent,
            chatContentDao.getVariantForMessage(chatId, 10L, 1)?.content,
        )
        assertEquals(
            variantContent,
            chatContentDao.getVariantsForMessages(chatId, listOf(10L)).single().content,
        )
    }

    @Test
    fun longVariantWithNulAcrossChunkBoundariesIsRestoredCompletely() = runBlocking {
        val variantContent = buildString {
            repeat(65_535) { append('x') }
            append('中')
            append("\u0000variant-boundary")
            repeat(80_000) { append('y') }
            append('\u0000')
            append("tail")
        }
        insertMessage(timestamp = 20L, orderIndex = 0, content = "base")
        database.messageVariantDao().insertVariant(
            MessageVariantEntity(
                chatId = chatId,
                messageTimestamp = 20L,
                variantIndex = 1,
                content = variantContent,
            )
        )

        assertEquals(
            variantContent,
            chatContentDao.getVariantsForMessage(chatId, 20L).single().content,
        )
        assertEquals(
            variantContent,
            chatContentDao.getVariantForMessage(chatId, 20L, 1)?.content,
        )
    }

    @Test
    fun selectedVariantContentIsRestoredCompletelyForHistoryHydration() = runBlocking {
        val baseContent = "base message"
        val selectedVariantContent = "selected\u0000variant\u0000content"
        insertMessage(timestamp = 30L, orderIndex = 0, content = baseContent, selectedVariantIndex = 1)
        database.messageVariantDao().insertVariant(
            MessageVariantEntity(
                chatId = chatId,
                messageTimestamp = 30L,
                variantIndex = 1,
                content = selectedVariantContent,
            )
        )

        val messages = chatContentDao.getMessagesForChat(chatId)
        val variants = chatContentDao.getVariantsForMessages(chatId, listOf(30L))

        assertEquals(baseContent, messages.single().content)
        assertEquals(selectedVariantContent, variants.single().content)
    }

    private suspend fun insertMessage(
        timestamp: Long,
        orderIndex: Int,
        content: String,
        selectedVariantIndex: Int = 0,
    ) {
        database.messageDao().insertMessage(
            MessageEntity(
                chatId = chatId,
                sender = "ai",
                content = content,
                timestamp = timestamp,
                orderIndex = orderIndex,
                selectedVariantIndex = selectedVariantIndex,
            )
        )
    }
}
