package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.library.MemoryAutoSaveScheduler
import com.ai.assistance.operit.data.model.MemoryAutoSaveOutcome
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the auto-save detail shown from the chat input menu.
 *
 * The last outcome is part of the detail so a normal "nothing to save" result is visible instead
 * of being indistinguishable from a silent failure. Dead-lettered candidates are shown separately
 * because they are no longer counted as pending work.
 */
suspend fun buildMemoryAutoSaveStatusDetail(context: Context, profileId: String): String =
    withContext(Dispatchers.IO) {
        val repository = MemoryAutoSaveCandidateRepository(context, profileId)
        val preferences = MemorySearchSettingsPreferences(context, profileId)
        val pendingCandidateCount = repository.countPendingAndFailedCandidates()
        val deadLetterCount = repository.countDeadLetterCandidates()
        val minutesUntilNextSave =
            MemoryAutoSaveScheduler.getInstance()?.getMinutesUntilNextRun(profileId)
                ?: preferences.loadAutoSaveIntervalMinutes().toLong()
        val lastOutcomeText =
            when (preferences.loadLastAutoSaveOutcome()) {
                MemoryAutoSaveOutcome.IDLE ->
                    context.getString(R.string.memory_auto_update_last_outcome_idle)
                MemoryAutoSaveOutcome.SAVED ->
                    context.getString(R.string.memory_auto_update_last_outcome_saved)
                MemoryAutoSaveOutcome.NO_MEMORY_EXTRACTED ->
                    context.getString(R.string.memory_auto_update_last_outcome_no_memory)
                MemoryAutoSaveOutcome.FAILED ->
                    context.getString(R.string.memory_auto_update_last_outcome_failed)
            }

        buildString {
            append(
                context.getString(
                    R.string.memory_auto_update_runtime_status,
                    pendingCandidateCount,
                    minutesUntilNextSave
                )
            )
            append('\n')
            append(context.getString(R.string.memory_auto_update_last_outcome, lastOutcomeText))
            if (deadLetterCount > 0) {
                append('\n')
                append(
                    context.getString(
                        R.string.memory_auto_update_dead_letter_count,
                        deadLetterCount
                    )
                )
            }
        }
    }
