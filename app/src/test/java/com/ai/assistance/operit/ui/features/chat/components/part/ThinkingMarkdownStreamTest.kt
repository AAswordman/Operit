package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.util.ThinkingMarkup
import com.ai.assistance.operit.util.stream.StreamLogger
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThinkingMarkdownStreamTest {
    private val token = "aBc4"

    @Before fun disableAndroidLogging() { StreamLogger.setEnabled(false) }
    @After fun restoreAndroidLogging() { StreamLogger.setEnabled(true) }

    @Test fun matchingTokenBoundaryAndFollowingAnswerAreExcluded() = runBlocking {
        val thought = "思考正文"
        val raw = ThinkingMarkup.openTag(token) + thought + ThinkingMarkup.closeTag(token) + "回答"
        assertEquals(thought, collectBody(listOf(raw)))
    }

    @Test fun nestedLegacyTagsAndFalseClosersRemainInBody() = runBlocking {
        val thought = "前半\n<think>标签示例</think>后半</thinking></operit_thinking>" +
            "</think token=\"Abc4\"></think data-token=\"aBc4\">" +
            "</think title='token=\"aBc4\"'></thinking token=\"aBc4\">尾部"
        val raw = ThinkingMarkup.openTag(token) + thought + ThinkingMarkup.closeTag(token)
        assertEquals(thought, collectBody(raw.map { it.toString() }))
    }

    @Test fun everyChunkBoundaryKeepsTheSameBody() = runBlocking {
        val thought = "前半<think>示例</think>后半</think token=\"wrong\">尾部"
        val raw = ThinkingMarkup.openTag(token) + thought + ThinkingMarkup.closeTag(token) + "回答"
        for (split in 0..raw.length) {
            assertEquals("分段位置=$split", thought, collectBody(listOf(raw.take(split), raw.drop(split))))
        }
    }

    @Test fun legacyNamesAndTokenNamesUseTheirOwnClosingTags() = runBlocking {
        for (name in listOf("think", "thinking")) {
            val legacy = "<$name>旧内容</$name>回答"
            assertEquals("旧内容", collectBody(legacy.map { it.toString() }, name))
            val thought = "新内容</$name>仍在思考"
            val raw = "<$name token=\"$token\">$thought</$name token=\"$token\">回答"
            assertEquals(thought, collectBody(raw.map { it.toString() }, name))
        }
    }

    @Test fun attributeNamesAndAttributeValuesDoNotCreateAToken() = runBlocking {
        val raw = "<think data-token=\"aBc4\" title='token=\"wrong\"'>旧内容</think>回答"
        assertEquals("旧内容", collectBody(raw.map { it.toString() }))
    }

    @Test fun interruptedStreamPreservesEveryPartialClosingTag() = runBlocking {
        val thought = "正文</think>继续，尚未写完的 </think token= 示例"
        val closing = ThinkingMarkup.closeTag(token)
        for (length in 0 until closing.length) {
            val raw = ThinkingMarkup.openTag(token) + thought + closing.take(length)
            assertEquals("结束标签长度=$length", ThinkingMarkup.body(raw), collectBody(raw.map { it.toString() }))
        }
        val closed = ThinkingMarkup.openTag(token) + thought + closing
        assertEquals(thought, collectBody(closed.map { it.toString() }))
    }

    @Test fun incompleteOpeningTagDoesNotBecomeBody() = runBlocking {
        val opening = ThinkingMarkup.openTag(token)
        for (length in 0 until opening.length) {
            assertEquals("开始标签长度=$length", "", collectBody(listOf(opening.take(length))))
        }
    }

    @Test fun ordinaryBodyIsEmittedBeforeUpstreamCompletes() = runBlocking {
        val result = StringBuilder()
        val source = stream<String> {
            emit(ThinkingMarkup.openTag(token) + "立即显示，2 < 3")
            assertEquals("立即显示，2 < 3", result.toString())
            emit("，后续" + ThinkingMarkup.closeTag(token))
            assertEquals("立即显示，2 < 3，后续", result.toString())
        }
        createThinkMarkdownCharStream(source, "think").collect { result.append(it) }
    }

    @Test fun quotedTokensAndTagCaseFollowStaticBoundaryRules() = runBlocking {
        val thought = "正文</think token='Abc4'>仍在思考"
        val raw = "\n <THINK token='aBc4'>$thought</think token = 'aBc4'>回答"
        assertEquals(thought, collectBody(raw.map { it.toString() }))
    }

    private suspend fun collectBody(chunks: List<String>, tagName: String = "think"): String {
        val source = stream<String> { chunks.forEach { emit(it) } }
        val result = StringBuilder()
        createThinkMarkdownCharStream(source, tagName).collect { result.append(it) }
        return result.toString()
    }
}
