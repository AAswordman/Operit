package com.ai.assistance.operit.core.tools.builtin.a2ui

import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager.ToolRuntimeContext
import com.ai.assistance.operit.ui.common.a2ui.CardDelivery
import com.ai.assistance.operit.ui.common.a2ui.CardExpress
import com.ai.assistance.operit.ui.common.a2ui.CardLiveStore
import com.ai.assistance.operit.ui.common.a2ui.A2uiProtocol
import com.ai.assistance.operit.ui.common.a2ui.submission.CardSubmissionStore
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.serialization.json.*

/** 所有生成操作只接受执行器传入的真实请求上下文，不从模型参数读取聊天身份。 */
internal object A2uiToolRuntime {
    private fun activeRun(context: ToolRuntimeContext?): String? =
        context?.takeIf { !it.callerChatId.isNullOrBlank() }?.a2uiRunId

    internal fun failure(code: String, message: String): JsonObject = buildJsonObject {
        put("valid", false)
        put("code", code)
        put("message", message)
    }

    private fun inactive(): JsonObject = failure(
        "inactive_turn", "Generate or update cards inside an active chat turn.",
    )

    fun prepare(
        filesDirectory: File,
        context: ToolRuntimeContext?,
        source: String,
        live: Boolean = false,
        waitTimeoutSeconds: Int = 0,
    ): JsonObject {
        val run = activeRun(context) ?: return inactive()
        return try {
            require(waitTimeoutSeconds in 0..600) { "Wait timeout must be between 0 and 600 seconds." }
            require(waitTimeoutSeconds == 0 || context?.publishA2ui != null) { "Waiting cards require an active streaming chat." }
            require(source.length <= 65_536) { "Express source exceeds 65536 characters." }
            val report = CardExpress.report(source)
            if (report["valid"]?.jsonPrimitive?.booleanOrNull != true) return report
            val chat = requireNotNull(context?.callerChatId)
            val reference = CardDelivery.register(
                run = run,
                document = report.getValue("document").jsonPrimitive.content,
                source = report["preparedSource"]?.jsonPrimitive?.content ?: source,
                liveChat = if (live) chat else null,
                // 普通卡片与 live 快照统一存入 a2ui 私有目录。
                liveDirectory = if (live) File(filesDirectory, "a2ui/" + A2uiProtocol.fingerprint(chat)) else null,
                waitChat = if (waitTimeoutSeconds > 0) chat else null,
                waitTimeoutMillis = waitTimeoutSeconds * 1000L,
            )
            buildJsonObject {
                put("valid", true)
                put("reference", reference)
                if (live) put("version", 0)
                if (waitTimeoutSeconds > 0) put("wait_timeout_seconds", waitTimeoutSeconds)
                report["repaired"]?.let { put("repaired", it) }
                report["repair"]?.let { put("repair", it) }
                put("message", "Card compiled and queued. Operit will render it automatically when this turn completes. To choose its position, optionally output this exact reference fence; never output a historical summary or rewrite the JSON:\n```a2ui-ref\n$reference\n```")
            }
        } catch (error: IllegalArgumentException) {
            failure("invalid_card", error.message?.take(240) ?: "Invalid card data.")
        } catch (_: CardDelivery.InactiveRunException) {
            inactive()
        }
    }

    suspend fun awaitSubmission(context: ToolRuntimeContext?, reference: String): JsonObject {
        val run = activeRun(context) ?: return inactive()
        val publish = context?.publishA2ui ?: return failure("stream_required", "Waiting cards require an active streaming chat.")
        val id = CardDelivery.canonical(run, reference)
            ?: return failure("unknown_reference", "No waiting card in this turn.")
        return try {
            val document = CardDelivery.resolve(run, reference) ?: error("Card could not be published.")
            publish(document)
            val outcome = CardSubmissionStore.current.await(run, id)
            buildJsonObject {
                put("valid", true)
                put("reference", reference)
                put("status", when (outcome.status) {
                    CardSubmissionStore.Status.Submitted -> "submitted"
                    CardSubmissionStore.Status.TimedOut -> "timed_out"
                    CardSubmissionStore.Status.Cancelled -> "cancelled"
                    else -> error("Waiting card did not reach a terminal state.")
                })
                outcome.action?.let { put("action", it) }
                outcome.data?.let { put("data", it) }
                put("message", "Form wait completed. The card was displayed during this tool call.")
            }
        } catch (cancelled: CancellationException) {
            CardSubmissionStore.current.cancel(run, id)
            throw cancelled
        } catch (error: Exception) {
            CardSubmissionStore.current.cancel(run, id)
            failure("wait_failed", error.message?.take(240) ?: "The waiting card is unavailable.")
        }
    }

    fun revise(
        context: ToolRuntimeContext?,
        reference: String,
        changes: String,
        remove: List<String>,
    ): JsonObject {
        val run = activeRun(context) ?: return inactive()
        return try {
            require(reference.length <= 80 && changes.length <= 65_536)
            require(remove.size <= 128 && remove.all { it.length <= 256 })
            val source = CardDelivery.source(run, reference)
                ?: return failure("unknown_reference", "Reference is not from this active turn.")
            val prepared = CardExpress.prepareRevision(source, changes, remove)
            val updated = CardDelivery.register(run, prepared.document, prepared.source, supersedesReference = reference)
            buildJsonObject {
                put("valid", true)
                put("reference", updated)
                put("baseReference", reference)
                prepared.repair?.let { put("repaired", true); put("repair", it) }
                put("message", "Revision compiled and queued. Previously displayed cards are preserved; unplaced older revisions are replaced. Operit will render the new card automatically when this turn completes. Optional position reference:\n```a2ui-ref\n$updated\n```")
            }
        } catch (error: CardExpress.Failure) {
            buildJsonObject {
                put("valid", false)
                put("code", "invalid_revision")
                put("line", error.line)
                put("column", error.column)
                put("message", error.reason)
            }
        } catch (error: IllegalArgumentException) {
            failure("invalid_revision", error.message?.take(240) ?: "Invalid revision parameters.")
        } catch (_: IllegalStateException) {
            failure("invalid_revision", "Revision is unavailable in this turn or exceeds its storage limits.")
        }
    }

    fun patchData(
        context: ToolRuntimeContext?,
        reference: String,
        expectedVersion: Int,
        data: JsonObject,
    ): JsonObject {
        val run = activeRun(context) ?: return inactive()
        return try {
            require(reference.length <= 80 && expectedVersion >= 0 && data.toString().length <= 8192)
            val id = CardDelivery.canonical(run, reference)
                ?: return failure("unknown_reference", "No live card with this reference in the active turn.")
            val version = CardLiveStore.patch(run, id, expectedVersion, data)
            buildJsonObject {
                put("valid", true)
                put("version", version)
                put("message", "Card data updated in place. Do not display the reference again.")
            }
        } catch (error: IllegalArgumentException) {
            failure("data_update_failed", error.message?.take(240) ?: "Invalid card data.")
        } catch (error: IllegalStateException) {
            failure("data_update_failed", error.message?.take(240) ?: "Live card is unavailable.")
        }
    }
}
