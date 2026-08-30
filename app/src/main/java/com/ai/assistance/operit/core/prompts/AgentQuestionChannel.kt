package com.ai.assistance.operit.core.prompts

import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AgentQuestionChannel — allows AI to ask questions back to the user.
 *
 * Per PROJECT_PLAN.md §15:
 * - AI can ask questions when blocked or before starting work
 * - Questions are shown to user with options
 * - User responds via UI
 * - Timeout handling for unanswered questions
 *
 * Usage:
 * ```kotlin
 * val channel = AgentQuestionChannel()
 *
 * // AI asks a question
 * val answer = channel.askQuestion(
 *     question = "Which model should I use?",
 *     options = listOf("DeepSeek", "Gemini", "OpenAI")
 * )
 *
 * // UI observes questions
 * channel.questionState.collect { question ->
 *     if (question != null) {
 *         // Show question to user
 *     }
 * }
 * ```
 */
class AgentQuestionChannel {
    companion object {
        private const val TAG = "AgentQuestionChannel"
        private const val DEFAULT_TIMEOUT_MS = 120_000L // 2 minutes
    }

    /**
     * A question from AI to the user.
     */
    data class AgentQuestion(
        val id: String,
        val question: String,
        val options: List<String> = emptyList(),
        val allowCustomAnswer: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * User's answer to a question.
     */
    data class AgentAnswer(
        val questionId: String,
        val selectedOption: String? = null,
        val customAnswer: String? = null
    ) {
        val answerText: String
            get() = customAnswer ?: selectedOption ?: ""
    }

    // Current question state
    private val _questionState = MutableStateFlow<AgentQuestion?>(null)
    val questionState: StateFlow<AgentQuestion?> = _questionState.asStateFlow()

    // Question history
    private val _history = mutableListOf<Pair<AgentQuestion, AgentAnswer>>()
    val history: List<Pair<AgentQuestion, AgentAnswer>>
        get() = _history.toList()

    // Pending question deferred
    private var pendingDeferred: CompletableDeferred<AgentAnswer>? = null

    /**
     * AI asks a question to the user.
     *
     * @param question The question text
     * @param options Available options (empty = free text)
     * @param allowCustomAnswer Whether user can type custom answer
     * @param timeoutMs Timeout in milliseconds (0 = no timeout)
     * @return The user's answer, or null if timed out
     */
    suspend fun askQuestion(
        question: String,
        options: List<String> = emptyList(),
        allowCustomAnswer: Boolean = true,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): AgentAnswer? {
        val id = "q_${System.currentTimeMillis()}"
        val agentQuestion = AgentQuestion(
            id = id,
            question = question,
            options = options,
            allowCustomAnswer = allowCustomAnswer
        )

        AppLogger.i(TAG, "AI asking question: $question")
        _questionState.value = agentQuestion

        val deferred = CompletableDeferred<AgentAnswer>()
        pendingDeferred = deferred

        val answer = if (timeoutMs > 0) {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
        } else {
            deferred.await()
        }

        _questionState.value = null
        pendingDeferred = null

        if (answer != null) {
            _history.add(agentQuestion to answer)
            AppLogger.i(TAG, "User answered: ${answer.answerText}")
        } else {
            AppLogger.w(TAG, "Question timed out: $question")
        }

        return answer
    }

    /**
     * User responds to the current question.
     *
     * @param answer The user's answer
     */
    fun respond(answer: AgentAnswer) {
        pendingDeferred?.complete(answer)
        AppLogger.d(TAG, "User response submitted for question: ${answer.questionId}")
    }

    /**
     * User selects an option.
     *
     * @param option The selected option
     */
    fun selectOption(option: String) {
        val question = _questionState.value ?: return
        respond(AgentAnswer(questionId = question.id, selectedOption = option))
    }

    /**
     * User provides a custom answer.
     *
     * @param answer The custom answer text
     */
    fun provideCustomAnswer(answer: String) {
        val question = _questionState.value ?: return
        respond(AgentAnswer(questionId = question.id, customAnswer = answer))
    }

    /**
     * Skip/cancel the current question.
     */
    fun skipQuestion() {
        pendingDeferred?.complete(AgentAnswer(questionId = "skipped"))
        _questionState.value = null
        pendingDeferred = null
        AppLogger.d(TAG, "Question skipped")
    }

    /**
     * Clear question history.
     */
    fun clearHistory() {
        _history.clear()
        AppLogger.d(TAG, "Question history cleared")
    }
}
