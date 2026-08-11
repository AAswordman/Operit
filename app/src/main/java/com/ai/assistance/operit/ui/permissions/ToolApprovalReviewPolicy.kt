package com.ai.assistance.operit.ui.permissions

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.util.ChatUtils
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class ToolPermissionDenialSource {
    SETTINGS,
    USER,
    AUTOMATIC_REVIEW,
    CIRCUIT_BREAKER,
}

internal sealed interface ToolPermissionDecision {
    data object Allowed : ToolPermissionDecision

    data class Denied(
        val source: ToolPermissionDenialSource,
        val rejection: String,
        val interruptTurn: Boolean = false,
    ) : ToolPermissionDecision
}

internal data class ToolPermissionReviewContext(
    val conversationHistory: List<PromptTurn>,
    val liveAssistantContent: String,
    val workspacePath: String?,
    val workspaceEnv: String?,
    val conversationLabel: String?,
) {
    val hasUserAuthorizationEvidence: Boolean
        get() = conversationHistory.any { turn ->
            turn.kind == PromptTurnKind.USER && turn.content.isNotBlank()
        }
}

internal data class ToolApprovalReviewRequest(
    val reviewId: String,
    val actionFingerprint: String,
    val messages: List<PromptTurn>,
)

internal enum class LlmApprovalReviewOutcome {
    APPROVE,
    DENY,
    ASK,
}

internal enum class LlmApprovalRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

internal enum class LlmApprovalAuthorization {
    EXPLICIT,
    IMPLICIT,
    ABSENT,
    UNCLEAR,
}

internal data class LlmApprovalReviewDecision(
    val outcome: LlmApprovalReviewOutcome,
    val riskLevel: LlmApprovalRiskLevel,
    val authorization: LlmApprovalAuthorization,
    val reason: String,
)

internal data class PermissionReviewDenialRecord(
    val actionFingerprint: String,
    val reason: String,
)

internal class PermissionReviewCircuitBreaker(
    private val denialLimit: Int = DEFAULT_DENIAL_LIMIT,
) {
    private val lock = Any()
    private val denialRecords = mutableListOf<PermissionReviewDenialRecord>()
    private val deniedActionFingerprints = mutableSetOf<String>()
    private var denialCount = 0

    init {
        require(denialLimit > 0) { "denialLimit must be positive" }
    }

    fun rejectionBeforeReview(actionFingerprint: String): String? = synchronized(lock) {
        when {
            actionFingerprint in deniedActionFingerprints ->
                "This exact action was already denied during the current model turn."

            denialCount >= denialLimit ->
                "Automatic permission review is suspended for the current model turn after repeated denials."

            else -> null
        }
    }

    fun denialHistory(): List<PermissionReviewDenialRecord> = synchronized(lock) {
        denialRecords.toList()
    }

    /** Returns true when this denial opens the breaker. */
    fun recordAutomaticDenial(actionFingerprint: String, reason: String): Boolean = synchronized(lock) {
        deniedActionFingerprints.add(actionFingerprint)
        denialRecords.add(
            PermissionReviewDenialRecord(
                actionFingerprint = actionFingerprint,
                reason = reason.trim().take(MAX_DENIAL_REASON_CHARS),
            )
        )
        denialCount += 1
        denialCount >= denialLimit
    }

    companion object {
        private const val DEFAULT_DENIAL_LIMIT = 2
        private const val MAX_DENIAL_REASON_CHARS = 1_000
    }
}

internal object ToolApprovalReviewPolicy {
    private val responseKeys =
        setOf("review_id", "decision", "risk_level", "user_authorization", "reason")
    private val strictJson = Json { ignoreUnknownKeys = false }

    fun buildRequest(
        tool: AITool,
        reviewContext: ToolPermissionReviewContext,
        useEnglish: Boolean,
        priorDenials: List<PermissionReviewDenialRecord> = emptyList(),
    ): ToolApprovalReviewRequest {
        val reviewId = UUID.randomUUID().toString()
        val canonicalAction = canonicalAction(tool)
        val actionFingerprint = sha256(canonicalAction)
        val transcript = buildParentTranscript(reviewContext)
        val priorDenialsEvidence =
            buildJsonArray {
                priorDenials.forEach { denial ->
                    add(
                        buildJsonObject {
                            put("action_fingerprint", denial.actionFingerprint)
                            put("reason", denial.reason)
                        }
                    )
                }
            }.toString()
        val evidence =
            FunctionalPrompts.buildToolApprovalEvidence(
                reviewId = reviewId,
                actionFingerprint = actionFingerprint,
                canonicalAction = canonicalAction,
                parentTranscript = transcript,
                priorDenials = priorDenialsEvidence,
                workspacePath = reviewContext.workspacePath,
                workspaceEnv = reviewContext.workspaceEnv,
                conversationLabel = reviewContext.conversationLabel,
                useEnglish = useEnglish,
            )
        return ToolApprovalReviewRequest(
            reviewId = reviewId,
            actionFingerprint = actionFingerprint,
            messages =
                listOf(
                    PromptTurn(
                        kind = PromptTurnKind.SYSTEM,
                        content = FunctionalPrompts.toolApprovalPrompt(useEnglish).trim(),
                    ),
                    PromptTurn(kind = PromptTurnKind.USER, content = evidence),
                ),
        )
    }

    fun parseAndEnforce(
        response: String,
        expectedReviewId: String,
        hasUserAuthorizationEvidence: Boolean,
    ): LlmApprovalReviewDecision? {
        val root =
            runCatching { strictJson.parseToJsonElement(response.trim()) as? JsonObject }
                .getOrNull()
                ?: return null
        if (root.keys != responseKeys) return null

        val reviewId = root.stringValue("review_id") ?: return null
        if (reviewId != expectedReviewId) return null
        val requestedOutcome =
            when (root.stringValue("decision")?.lowercase()) {
                "approve" -> LlmApprovalReviewOutcome.APPROVE
                "deny" -> LlmApprovalReviewOutcome.DENY
                "ask" -> LlmApprovalReviewOutcome.ASK
                else -> return null
            }
        val riskLevel =
            when (root.stringValue("risk_level")?.lowercase()) {
                "low" -> LlmApprovalRiskLevel.LOW
                "medium" -> LlmApprovalRiskLevel.MEDIUM
                "high" -> LlmApprovalRiskLevel.HIGH
                "critical" -> LlmApprovalRiskLevel.CRITICAL
                else -> return null
            }
        val authorization =
            when (root.stringValue("user_authorization")?.lowercase()) {
                "explicit" -> LlmApprovalAuthorization.EXPLICIT
                "implicit" -> LlmApprovalAuthorization.IMPLICIT
                "absent" -> LlmApprovalAuthorization.ABSENT
                "unclear" -> LlmApprovalAuthorization.UNCLEAR
                else -> return null
            }
        val reviewerReason = root.stringValue("reason")?.takeIf { it.isNotBlank() } ?: return null

        val enforcedOutcome =
            when {
                requestedOutcome == LlmApprovalReviewOutcome.DENY -> LlmApprovalReviewOutcome.DENY
                riskLevel == LlmApprovalRiskLevel.CRITICAL -> LlmApprovalReviewOutcome.DENY
                authorization == LlmApprovalAuthorization.ABSENT -> LlmApprovalReviewOutcome.DENY
                !hasUserAuthorizationEvidence -> LlmApprovalReviewOutcome.ASK
                authorization == LlmApprovalAuthorization.UNCLEAR -> LlmApprovalReviewOutcome.ASK
                riskLevel == LlmApprovalRiskLevel.HIGH &&
                    authorization != LlmApprovalAuthorization.EXPLICIT ->
                    LlmApprovalReviewOutcome.DENY

                else -> requestedOutcome
            }
        val enforcedReason =
            when {
                riskLevel == LlmApprovalRiskLevel.CRITICAL &&
                    requestedOutcome != LlmApprovalReviewOutcome.DENY ->
                    "Host policy denied a critical-risk action. Reviewer: $reviewerReason"

                authorization == LlmApprovalAuthorization.ABSENT &&
                    requestedOutcome != LlmApprovalReviewOutcome.DENY ->
                    "Host policy denied an action without user authorization. Reviewer: $reviewerReason"

                !hasUserAuthorizationEvidence ->
                    "Manual confirmation is required because no parent user message was available."

                riskLevel == LlmApprovalRiskLevel.HIGH &&
                    authorization != LlmApprovalAuthorization.EXPLICIT &&
                    requestedOutcome == LlmApprovalReviewOutcome.APPROVE ->
                    "Host policy denied a high-risk action without explicit authorization. Reviewer: $reviewerReason"

                else -> reviewerReason
            }
        return LlmApprovalReviewDecision(
            outcome = enforcedOutcome,
            riskLevel = riskLevel,
            authorization = authorization,
            reason = enforcedReason.take(MAX_REASON_CHARS),
        )
    }

    internal fun canonicalAction(tool: AITool): String =
        buildJsonObject {
            put("tool_name", tool.name)
            put(
                "parameters",
                buildJsonArray {
                    tool.parameters.forEach { parameter ->
                        add(
                            buildJsonObject {
                                put("name", parameter.name)
                                put("value", parameter.value)
                            }
                        )
                    }
                },
            )
        }.toString()

    private fun JsonObject.stringValue(name: String): String? {
        val value = get(name) as? JsonPrimitive ?: return null
        if (!value.isString) return null
        return value.content.trim()
    }

    private fun buildParentTranscript(reviewContext: ToolPermissionReviewContext): String {
        val candidates =
            reviewContext.conversationHistory
                .asSequence()
                .filter { turn -> turn.kind != PromptTurnKind.SYSTEM && turn.content.isNotBlank() }
                .toList()
                .takeLast(MAX_TRANSCRIPT_CANDIDATES)
                .map { turn ->
                    val content =
                        if (turn.kind == PromptTurnKind.ASSISTANT) {
                            ChatUtils.removeThinkingContent(turn.content)
                        } else {
                            turn.content
                        }
                    turn.kind to "[${turn.role}]\n${truncateTranscriptEntry(content)}\n"
                }
                .filter { (_, rendered) -> rendered.isNotBlank() }
                .toList()

        val selected = ArrayDeque<Pair<PromptTurnKind, String>>()
        var selectedChars = 0
        for (entry in candidates.asReversed()) {
            if (selected.size >= MAX_TRANSCRIPT_MESSAGES) break
            if (selectedChars + entry.second.length > MAX_TRANSCRIPT_CHARS) continue
            selected.addFirst(entry)
            selectedChars += entry.second.length
        }
        if (selected.none { (kind, _) -> kind == PromptTurnKind.USER }) {
            candidates.lastOrNull { (kind, _) -> kind == PromptTurnKind.USER }?.let { userAnchor ->
                selected.addFirst(
                    PromptTurnKind.USER to
                        "[user; older messages omitted]\n${userAnchor.second.substringAfter('\n')}",
                )
            }
        }

        val liveAssistant =
            reviewContext.liveAssistantContent
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { "[assistant; live]\n${truncateTranscriptEntry(it)}\n" }
        return buildString {
            selected.forEach { (_, rendered) -> append(rendered) }
            if (liveAssistant != null) append(liveAssistant)
        }.ifBlank { "(no parent conversation evidence)" }
    }

    private fun truncateTranscriptEntry(value: String): String {
        if (value.length <= MAX_TRANSCRIPT_ENTRY_CHARS) return value
        val marker = "\n<transcript_truncated />\n"
        val remaining = MAX_TRANSCRIPT_ENTRY_CHARS - marker.length
        val prefixLength = remaining / 2
        return value.take(prefixLength) + marker + value.takeLast(remaining - prefixLength)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }

    private const val MAX_TRANSCRIPT_CANDIDATES = 24
    private const val MAX_TRANSCRIPT_MESSAGES = 12
    private const val MAX_TRANSCRIPT_ENTRY_CHARS = 4_000
    private const val MAX_TRANSCRIPT_CHARS = 16_000
    private const val MAX_REASON_CHARS = 1_000
}

internal fun permissionDeniedBySettings(): ToolPermissionDecision.Denied =
    ToolPermissionDecision.Denied(
        source = ToolPermissionDenialSource.SETTINGS,
        rejection = "Tool execution denied by permission settings.",
    )

internal fun permissionDeniedByUser(): ToolPermissionDecision.Denied =
    ToolPermissionDecision.Denied(
        source = ToolPermissionDenialSource.USER,
        rejection = "User cancelled the tool execution.",
    )

internal fun permissionDeniedByAutomaticReview(
    reason: String,
    interruptTurn: Boolean,
): ToolPermissionDecision.Denied {
    val normalizedReason = reason.trim().take(1_000).ifBlank { "No reason provided." }
    return ToolPermissionDecision.Denied(
        source = ToolPermissionDenialSource.AUTOMATIC_REVIEW,
        rejection =
            "Automatic permission review denied this action: $normalizedReason " +
                "Do not retry, rephrase, split, encode, delegate, or use another tool or path " +
                "to work around this denial. Ask the user for explicit authorization or choose " +
                "a genuinely different safe action.",
        interruptTurn = interruptTurn,
    )
}

internal fun permissionDeniedByCircuitBreaker(reason: String): ToolPermissionDecision.Denied =
    ToolPermissionDecision.Denied(
        source = ToolPermissionDenialSource.CIRCUIT_BREAKER,
        rejection =
            "$reason Do not retry, rephrase, split, encode, delegate, or use another tool or " +
                "path to bypass earlier denials.",
        interruptTurn = true,
    )
