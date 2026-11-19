package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.compositionLocalOf
import com.ai.assistance.operit.data.model.SubagentRunEntity

/** One subscription for the timeline, rather than a fresh database query for each recycled card. */
internal data class TranscriptRunSnapshot(val chatId: String, val runs: List<SubagentRunEntity>)
internal val LocalTranscriptRuns = compositionLocalOf<TranscriptRunSnapshot?> { null }

/**
 * For a transcript shown outside the main chat screen (Orbit's records and developer view): the
 * chat its subagent rows belong to, and how they open a subagent's chat. Without it they read the
 * main screen's current chat and switch it to the subagent.
 */
internal class SubagentChatHost(val parentChatId: String, val open: (childChatId: String) -> Unit)
internal val LocalSubagentChatHost = compositionLocalOf<SubagentChatHost?> { null }
