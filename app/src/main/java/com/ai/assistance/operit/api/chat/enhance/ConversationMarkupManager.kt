package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Manages the markup elements used in conversations with the AI assistant.
 *
 * This class handles the generation of standardized XML-formatted status messages, tool invocation
 * formats, and tool results to be displayed in the conversation.
 */
class ConversationMarkupManager {

    companion object {
        private const val TOOL_RESULT_TRUNCATION_SUFFIX =
            "\n[工具结果过长，已截断]"

        /**
         * Creates an 'error' status markup element for a tool.
         *
         * @param toolName The name of the tool that produced the error
         * @param errorMessage The error message
         * @return The formatted status element
         */
        fun createToolErrorStatus(toolName: String, errorMessage: String): String {
            return createToolResultXml(
                toolName = toolName,
                status = "error",
                content = "<content><error>${errorMessage}</error></content>"
            )
        }

        /**
         * Creates a 'warning' status markup element.
         *
         * @param warningMessage The warning message to display
         * @return The formatted status element
         */
        fun createWarningStatus(warningMessage: String): String {
            return "<status type=\"warning\">$warningMessage</status>"
        }


        /**
         * Formats a tool result message for sending to the AI.
         *
         * @param result The tool execution result
         * @return The formatted tool result message
         */
        fun formatToolResultForMessage(result: ToolResult): String {
            return if (result.success) {
                val (toolPayload, imageLinkPayload) = splitImageLinksForModel(result.result.toString())
                val toolResultXml =
                    createBoundedToolResultXml(
                        toolName = result.toolName,
                        status = "success",
                        rawPayload = toolPayload
                    ) { payload ->
                        "<content>$payload</content>"
                    }

                if (imageLinkPayload.isBlank()) {
                    toolResultXml
                } else {
                    "$toolResultXml\n$imageLinkPayload"
                }
            } else {
                val errorPayload = buildString {
                    val message = result.error.orEmpty().trim()
                    val detail = result.result.toString().trim()
                    append(message)
                    if (detail.isNotEmpty()) {
                        if (message.isNotEmpty()) {
                            append("\n\n")
                        }
                        append(detail)
                    }
                }
                createBoundedToolResultXml(
                    toolName = result.toolName,
                    status = "error",
                    rawPayload = errorPayload
                ) { payload ->
                    "<content><error>$payload</error></content>"
                }
            }
        }

        private fun splitImageLinksForModel(rawPayload: String): Pair<String, String> {
            if (!MediaLinkParser.hasImageLinks(rawPayload)) {
                return rawPayload to ""
            }

            val imageLinkPayload =
                MediaLinkParser.extractImageLinkIds(rawPayload)
                    .joinToString("\n") { id -> """<link type="image" id="$id"></link>""" }
            val textPayload = MediaLinkParser.removeImageLinks(rawPayload).trim()
            val toolPayload =
                listOf("Image attached as multimodal input.", textPayload)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")

            return toolPayload to imageLinkPayload
        }

        fun buildBoundedToolResultMessage(results: List<ToolResult>): String {
            if (results.isEmpty()) {
                return ""
            }

            val maxChars = ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS
            val separator = "\n"
            val builder = StringBuilder()

            for (result in results) {
                val formatted = formatToolResultForMessage(result)
                val additionalLength =
                    (if (builder.isEmpty()) 0 else separator.length) + formatted.length
                if (builder.length + additionalLength > maxChars) {
                    break
                }
                if (builder.isNotEmpty()) {
                    builder.append(separator)
                }
                builder.append(formatted)
            }

            return builder.toString()
        }

        /**
         * Formats a message indicating multiple tool invocations were found but only one will be
         * processed.
         *
         * @param context The context to access string resources
         * @param toolName The name of the tool that will be processed
         * @return The formatted warning message
         */
        fun createMultipleToolsWarning(context: Context, toolName: String): String {
            return createWarningStatus(
                    context.getString(R.string.conversation_markup_multiple_tools_warning, toolName)
            )
        }

        /**
         * Creates a message for when a tool is not available.
         *
         * @param toolName The name of the unavailable tool
         * @param details Optional detailed error message
         * @return The formatted error message
         */
        fun createToolNotAvailableError(toolName: String, details: String? = null): String {
            val errorMessage = details ?: "The tool `$toolName` is not available."
            return createToolErrorStatus(toolName, errorMessage)
        }

        private fun createToolResultXml(toolName: String, status: String, content: String): String {
            val tagName = ChatMarkupRegex.generateRandomToolResultTagName()
            return """<$tagName name="$toolName" status="$status">$content</$tagName>""".trimIndent()
        }

        private fun createBoundedToolResultXml(
            toolName: String,
            status: String,
            rawPayload: String,
            bodyBuilder: (String) -> String
        ): String {
            val emptyXml =
                createToolResultXml(
                    toolName = toolName,
                    status = status,
                    content = bodyBuilder("")
                )
            val maxPayloadChars =
                (ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS - emptyXml.length)
                    .coerceAtLeast(0)
            val boundedPayload = truncatePayload(rawPayload, maxPayloadChars)
            return createToolResultXml(
                toolName = toolName,
                status = status,
                content = bodyBuilder(boundedPayload)
            )
        }

        private fun truncatePayload(payload: String, maxChars: Int): String {
            if (payload.length <= maxChars) {
                return payload
            }
            if (maxChars <= 0) {
                return ""
            }
            if (TOOL_RESULT_TRUNCATION_SUFFIX.length >= maxChars) {
                return TOOL_RESULT_TRUNCATION_SUFFIX.take(maxChars)
            }
            return payload
                .take(maxChars - TOOL_RESULT_TRUNCATION_SUFFIX.length)
                .trimEnd() + TOOL_RESULT_TRUNCATION_SUFFIX
        }

    }
}
