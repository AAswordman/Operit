package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The same finalized XML is published to the saved stream and used by the next model request. */
data class ToolExecutionBatch(val results: List<ToolResult>, val message: String)

/**
 * Execution may finish out of order, but saved XML is matched by tool name when history is rebuilt.
 * Publish only a completed prefix of the original calls so same-name results cannot exchange owners.
 * Indexes belong to this batch, not to tool names or invocation equality.
 */
internal class OrderedToolResults(
    count: Int,
    private val publish: suspend (String) -> Unit
) {
    private val mutex = Mutex()
    private val results = arrayOfNulls<ToolResult>(count)
    private val markup = arrayOfNulls<String>(count)
    private var next = 0

    suspend fun complete(index: Int, result: ToolResult) {
        mutex.withLock {
            check(results[index] == null) { "Tool result already completed at index $index" }
            results[index] = result
            // Generate the random XML tag and bound the payload once, for both consumers.
            markup[index] = ConversationMarkupManager.formatToolResultForMessage(result)
            while (next < results.size && results[next] != null) {
                publish(checkNotNull(markup[next]))
                next++
            }
        }
    }

    suspend fun finish(): ToolExecutionBatch = mutex.withLock {
        check(next == results.size) { "Tool batch has unpublished results" }
        ToolExecutionBatch(
            results = results.map { checkNotNull(it) },
            message = markup.joinToString("\n") { checkNotNull(it) }
        )
    }
}
