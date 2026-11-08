package com.ai.assistance.operit.ui.features.extensions.packages.market

import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary

const val MCP_MARKET_VISIBILITY_LABEL = "mcp-plugin"
const val SKILL_MARKET_VISIBILITY_LABEL = "skill-plugin"
const val REVIEW_CHANGES_REQUESTED_LABEL = "review:changes-requested"
const val REVIEW_REJECTED_LABEL = "review:rejected"

val ARTIFACT_MARKET_VISIBILITY_LABELS: Set<String> =
    PublishArtifactType.entries.map { it.marketLabel }.toSet()

val ALL_MARKET_VISIBILITY_LABELS: Set<String> =
    buildSet {
        add(MCP_MARKET_VISIBILITY_LABEL)
        add(SKILL_MARKET_VISIBILITY_LABEL)
        addAll(ARTIFACT_MARKET_VISIBILITY_LABELS)
    }

val MARKET_REVIEW_STATUS_LABELS: Set<String> =
    setOf(
        REVIEW_CHANGES_REQUESTED_LABEL,
        REVIEW_REJECTED_LABEL
    )

val MARKET_REVIEW_REASON_LABELS: Set<String> =
    MarketReviewReason.entries.map { it.labelName }.toSet()

enum class MarketReviewState {
    PENDING,
    APPROVED,
    CHANGES_REQUESTED,
    REJECTED,
    WITHDRAWN
}

enum class MarketReviewReason(
    val code: String,
    val labelName: String
) {
    METADATA_INCOMPLETE(
        code = "metadata-incomplete",
        labelName = "reason:metadata-incomplete"
    ),
    INSTALL_CONFIG_INVALID(
        code = "install-config-invalid",
        labelName = "reason:install-config-invalid"
    ),
    REPOSITORY_UNREACHABLE(
        code = "repository-unreachable",
        labelName = "reason:repository-unreachable"
    ),
    REPOSITORY_CONTENT_INVALID(
        code = "repository-content-invalid",
        labelName = "reason:repository-content-invalid"
    ),
    ENTRY_UNUSABLE(
        code = "entry-unusable",
        labelName = "reason:entry-unusable"
    ),
    QUALITY_TOO_LOW(
        code = "quality-too-low",
        labelName = "reason:quality-too-low"
    ),
    AI_HALLUCINATION(
        code = "ai-hallucination",
        labelName = "reason:ai-hallucination"
    ),
    SECURITY_RISK(
        code = "security-risk",
        labelName = "reason:security-risk"
    ),
    DUPLICATE_SUBMISSION(
        code = "duplicate-submission",
        labelName = "reason:duplicate-submission"
    ),
    POLICY_VIOLATION(
        code = "policy-violation",
        labelName = "reason:policy-violation"
    );

    companion object {
        private val byLabelName = entries.associateBy { it.labelName.lowercase() }
        private val byCode = entries.associateBy { it.code.lowercase() }

        fun fromLabelName(labelName: String): MarketReviewReason? {
            return byLabelName[labelName.trim().lowercase()]
        }

        fun fromCode(code: String): MarketReviewReason? {
            return byCode[code.trim().lowercase()]
        }
    }
}

data class MarketReviewSnapshot(
    val state: MarketReviewState,
    val reasons: List<MarketReviewReason>,
    val isPubliclyApproved: Boolean
)

fun MarketReviewState.labelResId(): Int {
    return when (this) {
        MarketReviewState.PENDING -> R.string.market_review_pending
        MarketReviewState.APPROVED -> R.string.market_review_approved
        MarketReviewState.CHANGES_REQUESTED -> R.string.market_review_changes_requested
        MarketReviewState.REJECTED -> R.string.market_review_rejected
        MarketReviewState.WITHDRAWN -> R.string.removed
    }
}

fun MarketReviewReason.labelResId(): Int {
    return when (this) {
        MarketReviewReason.METADATA_INCOMPLETE -> R.string.market_review_reason_metadata_incomplete
        MarketReviewReason.INSTALL_CONFIG_INVALID -> R.string.market_review_reason_install_config_invalid
        MarketReviewReason.REPOSITORY_UNREACHABLE -> R.string.market_review_reason_repository_unreachable
        MarketReviewReason.REPOSITORY_CONTENT_INVALID -> R.string.market_review_reason_repository_content_invalid
        MarketReviewReason.ENTRY_UNUSABLE -> R.string.market_review_reason_entry_unusable
        MarketReviewReason.QUALITY_TOO_LOW -> R.string.market_review_reason_quality_too_low
        MarketReviewReason.AI_HALLUCINATION -> R.string.market_review_reason_ai_hallucination
        MarketReviewReason.SECURITY_RISK -> R.string.market_review_reason_security_risk
        MarketReviewReason.DUPLICATE_SUBMISSION -> R.string.market_review_reason_duplicate_submission
        MarketReviewReason.POLICY_VIOLATION -> R.string.market_review_reason_policy_violation
    }
}

fun MarketV2Entry.resolveMarketReviewSnapshot(): MarketReviewSnapshot {

    val state =
        when (stateCode.lowercase()) {
            "approved", "open" -> MarketReviewState.APPROVED
            "changes_requested" -> MarketReviewState.CHANGES_REQUESTED
            "rejected", "security_blocked" -> MarketReviewState.REJECTED
            "withdrawn", "closed", "removed" -> MarketReviewState.WITHDRAWN
            else -> MarketReviewState.PENDING
        }
    return MarketReviewSnapshot(
        state = state,
        reasons = emptyList(),
        isPubliclyApproved = state == MarketReviewState.APPROVED
    )
}

fun MarketV2PublisherEntrySummary.resolveMarketReviewSnapshot(): MarketReviewSnapshot {
    val state =
        when (stateCode.lowercase()) {
            "approved", "open" -> MarketReviewState.APPROVED
            "changes_requested" -> MarketReviewState.CHANGES_REQUESTED
            "rejected", "security_blocked" -> MarketReviewState.REJECTED
            "withdrawn", "closed", "removed" -> MarketReviewState.WITHDRAWN
            else -> MarketReviewState.PENDING
        }
    return MarketReviewSnapshot(
        state = state,
        reasons = reasonCodes.mapNotNull { MarketReviewReason.fromCode(it) },
        isPubliclyApproved = state == MarketReviewState.APPROVED
    )
}
