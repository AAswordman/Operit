package com.ai.assistance.operit.core.prompts

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AgentQuestionChannel.
 */
class AgentQuestionChannelTest {

    private lateinit var channel: AgentQuestionChannel

    @Before
    fun setUp() {
        channel = AgentQuestionChannel()
    }

    @Test
    fun `selectOption sends answer`() = runTest {
        val job = launch {
            val answer = channel.askQuestion(
                question = "Which model?",
                options = listOf("DeepSeek", "Gemini"),
                timeoutMs = 5000
            )
            assertEquals("DeepSeek", answer?.selectedOption)
        }

        delay(100)
        channel.selectOption("DeepSeek")
        job.join()
    }

    @Test
    fun `provideCustomAnswer sends custom text`() = runTest {
        val job = launch {
            val answer = channel.askQuestion(
                question = "Any preference?",
                allowCustomAnswer = true,
                timeoutMs = 5000
            )
            assertEquals("Custom model", answer?.customAnswer)
        }

        delay(100)
        channel.provideCustomAnswer("Custom model")
        job.join()
    }

    @Test
    fun `skipQuestion returns skipped answer`() = runTest {
        val job = launch {
            val answer = channel.askQuestion(
                question = "Skip this?",
                timeoutMs = 5000
            )
            assertEquals("skipped", answer?.questionId)
        }

        delay(100)
        channel.skipQuestion()
        job.join()
    }

    @Test
    fun `question state is set when asking`() = runTest {
        val job = launch {
            channel.askQuestion(
                question = "Test question?",
                timeoutMs = 1000
            )
        }

        delay(50)
        val question = channel.questionState.value
        assertNotNull(question)
        assertEquals("Test question?", question?.question)

        channel.skipQuestion()
        job.join()
    }

    @Test
    fun `question state clears after answer`() = runTest {
        val job = launch {
            channel.askQuestion(
                question = "Clear test?",
                timeoutMs = 5000
            )
        }

        delay(50)
        assertNotNull(channel.questionState.value)

        channel.selectOption("Yes")
        job.join()

        delay(50)
        assertNull(channel.questionState.value)
    }

    @Test
    fun `history records Q and A`() = runTest {
        val job = launch {
            channel.askQuestion(
                question = "History test?",
                options = listOf("Yes"),
                timeoutMs = 5000
            )
        }

        delay(50)
        channel.selectOption("Yes")
        job.join()

        assertEquals(1, channel.history.size)
        assertEquals("History test?", channel.history[0].first.question)
        assertEquals("Yes", channel.history[0].second.selectedOption)
    }

    @Test
    fun `clearHistory removes all entries`() = runTest {
        val job = launch {
            channel.askQuestion(
                question = "Clear all?",
                timeoutMs = 5000
            )
        }

        delay(50)
        channel.selectOption("Yes")
        job.join()

        assertEquals(1, channel.history.size)
        channel.clearHistory()
        assertEquals(0, channel.history.size)
    }

    @Test
    fun `timeout returns null`() = runTest {
        val answer = channel.askQuestion(
            question = "Will timeout?",
            timeoutMs = 100
        )
        assertNull(answer)
    }
}
