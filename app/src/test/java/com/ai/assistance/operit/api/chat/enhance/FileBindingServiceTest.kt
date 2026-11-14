package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * FileBindingService 编辑匹配引擎的纯 JVM 单测。
 * 覆盖：精确快路径、行号前缀剥离、多候选拒绝、occurrence 定位、模糊匹配、无匹配诊断、delete。
 */
class FileBindingServiceTest {

    private lateinit var service: FileBindingService

    @Before
    fun setUp() {
        // 修改意图：AppLogger 默认走 android.util.Log（JVM 单测中是会抛异常的 stub），
        // 关闭系统日志与文件日志，使匹配器可纯 JVM 运行
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
        service = FileBindingService(mock())
    }

    private fun op(
        old: String,
        new: String = "",
        action: FileBindingService.StructuredEditAction =
            FileBindingService.StructuredEditAction.REPLACE,
        occurrence: Int? = null,
    ): FileBindingService.StructuredEditOperation =
        FileBindingService.StructuredEditOperation(
            action = action,
            oldContent = old,
            newContent = new,
            occurrence = occurrence
        )

    @Test
    fun `exact unique match replaces content and reports exact line range`() = runBlocking {
        val original = "line one\nline two\nline three\n"
        val result = service.processFileBindingOperations(
            original,
            listOf(op(old = "line two", new = "line TWO edited"))
        )

        assertFalse(result.diff.startsWith("Error"))
        assertEquals("line one\nline TWO edited\nline three\n", result.mergedContent)
        val range = result.appliedRanges.single()
        assertTrue("range=$range", range.contains("lines 2-2"))
        assertTrue("range=$range", range.contains("exact"))
    }

    @Test
    fun `line number prefixes pasted from read_file output are stripped before matching`() =
        runBlocking {
            val original = "fun a() {\n    val x = 1\n}\n"
            // 模拟模型把 read_file 输出整段粘贴进 old（行号宽度 1）
            val oldWithPrefixes = "1| fun a() {\n2|     val x = 1\n3| }"

            val result = service.processFileBindingOperations(
                original,
                listOf(op(old = oldWithPrefixes, new = "fun a() {\n    val x = 2\n}"))
            )

            assertFalse(result.diff.startsWith("Error"))
            assertEquals("fun a() {\n    val x = 2\n}\n", result.mergedContent)
        }

    @Test
    fun `multiple match locations without occurrence are rejected with line numbers`() =
        runBlocking {
            val original = "aaa\nbbb\naaa\nbbb\n"
            val result = service.processFileBindingOperations(
                original,
                listOf(op(old = "aaa", new = "AAA"))
            )

            assertTrue(result.diff.startsWith("Error"))
            assertEquals(original, result.mergedContent)
            assertTrue("diff=${result.diff}", result.diff.contains("2 distinct locations"))
            assertTrue("diff=${result.diff}", result.diff.contains("lines 1-1"))
            assertTrue("diff=${result.diff}", result.diff.contains("lines 3-3"))
            assertTrue("diff=${result.diff}", result.diff.contains("occurrence"))
        }

    @Test
    fun `occurrence selects the Nth match location`() = runBlocking {
        val original = "aaa\nbbb\naaa\nbbb\n"
        val result = service.processFileBindingOperations(
            original,
            listOf(op(old = "aaa", new = "AAA", occurrence = 2))
        )

        assertFalse(result.diff.startsWith("Error"))
        assertEquals("aaa\nbbb\nAAA\nbbb\n", result.mergedContent)
        assertTrue(
            "range=${result.appliedRanges}",
            result.appliedRanges.single().contains("lines 3-3")
        )
    }

    @Test
    fun `occurrence beyond candidate count is rejected`() = runBlocking {
        val original = "aaa\nbbb\naaa\n"
        val result = service.processFileBindingOperations(
            original,
            listOf(op(old = "aaa", new = "AAA", occurrence = 5))
        )

        assertTrue(result.diff.startsWith("Error"))
        assertEquals(original, result.mergedContent)
    }

    @Test
    fun `fuzzy match tolerates small content drift and reports fuzzy range`() = runBlocking {
        // 修改意图：长块（20 行）中单字符漂移只影响极少数 3-gram，相似度能稳过 0.9 阈值；
        // 短块单字符漂移的 Jaccard 会跌到阈值以下，不适合做模糊命中用例。
        // 评审补记：lambda 必须显式命名参数 i——隐式 it 与字符串模板里的 $i 对不上，
        // 该文件此前从未通过编译（compileDebugKotlin 不编测试源）
        val originalLines = (1..20).map { i -> "        val line$i = $i" }
        val original = "fun a() {\n" + originalLines.joinToString("\n") + "\n}\n"

        val oldLines = (1..20).map { i -> "    val line$i = ${if (i == 7) "8" else "$i"}" }
        val old = "fun a() {\n" + oldLines.joinToString("\n") + "\n}"

        val newLines = (1..20).map { i -> "    val line$i = ${if (i == 7) "9" else "$i"}" }
        val newContent = "fun a() {\n" + newLines.joinToString("\n") + "\n}"

        val result = service.processFileBindingOperations(original, listOf(op(old = old, new = newContent)))

        assertFalse("diff=${result.diff}", result.diff.startsWith("Error"))
        // 多行替换（start != end）不做缩进沿用，new 内容按原样插入；
        // 原文件的尾行换行符保留，故结果比 newContent 多一个结尾 "\n"
        assertEquals(newContent + "\n", result.mergedContent)
        assertTrue(
            "range=${result.appliedRanges}",
            result.appliedRanges.single().contains("fuzzy")
        )
    }

    @Test
    fun `unmatchable old block fails with diagnostic message`() = runBlocking {
        val original = "alpha beta gamma\ndelta epsilon zeta\neta theta iota\n"
        // 评审补记：new 必须非空——空 new 的 REPLACE 在校验阶段就被丢弃
        // （"No valid edit operations provided"），走不到无匹配诊断路径
        val result = service.processFileBindingOperations(
            original,
            listOf(
                op(
                    old = "completely unrelated content that does not exist anywhere here",
                    new = "brand new content that replaces nothing real"
                )
            )
        )

        assertTrue(result.diff.startsWith("Error"))
        assertEquals(original, result.mergedContent)
        assertTrue("diff=${result.diff}", result.diff.contains("Could not find a match"))
        assertTrue("diff=${result.diff}", result.diff.contains("read_file_part"))
    }

    @Test
    fun `delete action removes the matched block`() = runBlocking {
        val original = "a\nb\nc\n"
        val result = service.processFileBindingOperations(
            original,
            listOf(
                op(
                    old = "b",
                    action = FileBindingService.StructuredEditAction.DELETE
                )
            )
        )

        assertFalse(result.diff.startsWith("Error"))
        assertEquals("a\nc\n", result.mergedContent)
    }
}
