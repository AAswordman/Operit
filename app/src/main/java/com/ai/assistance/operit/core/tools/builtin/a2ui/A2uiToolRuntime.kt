package com.ai.assistance.operit.core.tools.builtin.a2ui

import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager.ToolRuntimeContext
import com.ai.assistance.operit.ui.common.a2ui.CardDelivery
import com.ai.assistance.operit.ui.common.a2ui.CardExpress
import com.ai.assistance.operit.ui.common.a2ui.CardLiveStore
import com.ai.assistance.operit.ui.common.a2ui.A2uiProtocol
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
    ): JsonObject {
        val run = activeRun(context) ?: return inactive()
        return try {
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
            )
            buildJsonObject {
                put("valid", true)
                put("reference", reference)
                if (live) put("version", 0)
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
