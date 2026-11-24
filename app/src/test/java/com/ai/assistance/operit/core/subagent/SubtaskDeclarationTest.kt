package com.ai.assistance.operit.core.subagent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 终局声明提取的回归测试。
 *
 * 用例取自实机归档的真实 finalText：子模型在任务要求「输出一行特定文字」时，
 * 会把那段文字写在前面、把协议声明以裸 JSON 附在后面，导致声明被 overall 丢弃。
 */
class SubtaskDeclarationTest {

    private val thinkPrefix =
        "<think>The task says output one line, but the system says last turn must be JSON. " +
            "Include the required line inside summary.</think>"

    private val bareDeclaration =
        """{"status":"completed","summary":"已用 BigNumberCalculator 计算 10 的阶乘，结果为 3628800。","evidence_ids":["subtask_x:1","subtask_x:2"]}"""

    @Test
    fun `prose followed by a bare JSON declaration is extracted`() {
        val finalText = "$thinkPrefix\n混合后台C完成，结果=3628800\n$bareDeclaration"

        val cleaned = SubtaskDeclaration.stripThinkBlocks(finalText)
        val extracted = SubtaskDeclaration.findLastJsonObject(cleaned)

        assertNotNull(extracted)
        val parsed = JSONObject(requireNotNull(extracted))
        assertEquals("completed", parsed.getString("status"))
        assertEquals(2, parsed.getJSONArray("evidence_ids").length())
    }

    @Test
    fun `declaration after stripped think block keeps the required report line out of the JSON`() {
        val finalText = "${thinkPrefix}混合多轮1: 文件数=31\n\n$bareDeclaration"

        val cleaned = SubtaskDeclaration.stripThinkBlocks(finalText)

        // 任务要求的正文仍然保留在 JSON 之外，摘要提炼时不会丢。
        assertEquals(true, cleaned.startsWith("混合多轮1: 文件数=31"))
        assertNotNull(SubtaskDeclaration.findLastJsonObject(cleaned))
    }

    @Test
    fun `nested objects do not break extraction`() {
        val text = "结果如下\n" +
            """{"status":"completed","summary":"ok","meta":{"a":{"b":1}},"evidence_ids":[]}"""

        val extracted = SubtaskDeclaration.findLastJsonObject(text)

        assertNotNull(extracted)
        assertEquals("ok", JSONObject(requireNotNull(extracted)).getString("summary"))
    }

    @Test
    fun `json without a status key is not treated as a declaration`() {
        // 任务本身让模型输出 JSON 数据时，那份数据不能被误当协议声明。
        val text = "统计结果：\n{\"total\":31,\"files\":21,\"dirs\":10}"

        assertNull(SubtaskDeclaration.findLastJsonObject(text))
    }

    @Test
    fun `text without any JSON object yields null`() {
        assertNull(SubtaskDeclaration.findLastJsonObject("只有正文，没有声明"))
        assertNull(SubtaskDeclaration.findLastJsonObject(""))
    }

    @Test
    fun `fenced declaration still works through the fence path`() {
        val text = "正文\n```json\n$bareDeclaration\n```"

        val fenced = SubtaskDeclaration.findLastJsonFence(text)

        assertNotNull(fenced)
        assertEquals("completed", JSONObject(requireNotNull(fenced)).getString("status"))
    }

    @Test
    fun `pure JSON declaration is handled by the candidate path`() {
        val cleaned = SubtaskDeclaration.stripThinkBlocks("$thinkPrefix$bareDeclaration")

        assertEquals(true, cleaned.startsWith("{"))
        val body = SubtaskDeclaration.stripJsonFence(cleaned)
        assertNotNull(body)
        assertEquals("completed", JSONObject(requireNotNull(body)).getString("status"))
    }
}
