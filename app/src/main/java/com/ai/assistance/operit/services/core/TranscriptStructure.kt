package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.ChatMessageProcessMetadata

internal data class TranscriptTurn(
    val key: Long,
    val finalTimestamp: Long,
    val memberTimestamps: Set<Long>,
    val durationMs: Long,
)

/** Rows written by the app to report the transcript rather than to take part in a turn. */
internal const val TRANSCRIPT_SUMMARY_SENDER: String = "summary"

/** Agent input cards report the work of the run they sit in, so they stay inside its process. */
internal fun isAgentInputCard(displayModeName: String): Boolean =
    ChatMessageDisplayMode.entries
        .firstOrNull { it.name == displayModeName }
        ?.isCollaborationEvent == true

/**
 * True when the rows between two turns belong to the reply process itself. An automatic summary is
 * written inside the run it reports and the run resumes right after it; an agent input card is
 * that run reporting its own subagent work. Either way those turns are one reply process instead of
 * two. A row that asks for a new answer — the owner's own turn or one the app delivered for them —
 * still ends the process.
 */
internal fun isContinuedAcrossSummaries(
    betweenSenders: List<String>,
    betweenDisplayModeNames: List<String>,
): Boolean =
    betweenSenders.isNotEmpty() &&
        betweenSenders.size == betweenDisplayModeNames.size &&
        betweenSenders.indices.all { index ->
            betweenSenders[index] == TRANSCRIPT_SUMMARY_SENDER ||
                isAgentInputCard(betweenDisplayModeNames[index])
        }

private data class TurnSegment(
    val turn: TranscriptTurn,
    val firstIndex: Int,
    val lastIndex: Int,
)

/** Presentation structure is independent of which bodies happen to be in memory. */
internal class TranscriptStructure(val rows: List<ChatMessageProcessMetadata>) {
    fun retainedTimestamps(topLevelTimestamps: Set<Long>, loadedTimestamps: List<Long>): Set<Long> =
        loadedTimestamps.filterTo(hashSetOf()) { timestamp ->
            timestamp in topLevelTimestamps ||
                turnByTimestamp[timestamp]?.finalTimestamp in topLevelTimestamps
        }

    val turns: List<TranscriptTurn>
    val turnByTimestamp: Map<Long, TranscriptTurn>
    val topLevelRows: List<ChatMessageProcessMetadata>

    init {
        val segments = mutableListOf<TurnSegment>()
        val pending = mutableListOf<ChatMessageProcessMetadata>()
        var pendingFirstIndex = -1
        var pendingKey: Long? = null
        rows.forEachIndexed { index, row ->
            val collaboration = isAgentInputCard(row.displayMode)
            if (row.sender != "ai") {
                if (collaboration) {
                    if (pending.isEmpty()) pendingFirstIndex = index
                    pending += row
                } else if (pendingKey == null) {
                    pending.clear()
                    pendingFirstIndex = -1
                }
                return@forEachIndexed
            }
            if (pendingKey != null && pendingKey != row.sentAt) {
                pending.clear()
                pendingFirstIndex = -1
            }
            pendingKey = row.sentAt
            if (row.sentAt > 0 && row.displayMode == ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE.name) {
                if (pending.isEmpty()) pendingFirstIndex = index
                pending += row
            } else {
                if (pending.isNotEmpty() && row.sentAt > 0 && row.completedAt > 0 && row.hasContent &&
                    row.displayMode == ChatMessageDisplayMode.NORMAL.name
                ) {
                    segments += TurnSegment(
                        turn = TranscriptTurn(
                            row.sentAt, row.timestamp,
                            (pending.map { it.timestamp } + row.timestamp).toSet(),
                            row.waitDurationMs + row.outputDurationMs,
                        ),
                        firstIndex = if (pendingFirstIndex >= 0) pendingFirstIndex else index,
                        lastIndex = index,
                    )
                }
                pending.clear()
                pendingFirstIndex = -1
                pendingKey = null
            }
        }
        turns = mergeContinuedSegments(segments)
        turnByTimestamp = buildMap {
            turns.forEach { turn -> turn.memberTimestamps.forEach { put(it, turn) } }
        }
        topLevelRows = rows.filter { row ->
            turnByTimestamp[row.timestamp]?.let { it.finalTimestamp == row.timestamp } ?: true
        }
    }

    /**
     * A run that compaction cut in half is resumed by an automatic continuation that brings no user
     * message of its own, so the halves stay one fold and their elapsed time adds up.
     */
    private fun mergeContinuedSegments(segments: List<TurnSegment>): List<TranscriptTurn> {
        val merged = mutableListOf<TranscriptTurn>()
        var previousLastIndex = -1
        segments.forEach { segment ->
            val previous = merged.lastOrNull()
            val between = previousLastIndex + 1 until segment.firstIndex
            if (previous != null &&
                isContinuedAcrossSummaries(
                    between.map { rows[it].sender },
                    between.map { rows[it].displayMode },
                )
            ) {
                merged[merged.lastIndex] = previous.continuedBy(segment.turn)
            } else {
                merged += segment.turn
            }
            previousLastIndex = segment.lastIndex
        }
        return merged
    }
}

private fun TranscriptTurn.continuedBy(next: TranscriptTurn): TranscriptTurn =
    copy(
        finalTimestamp = next.finalTimestamp,
        memberTimestamps = memberTimestamps + next.memberTimestamps,
        durationMs = durationMs + next.durationMs,
    )
