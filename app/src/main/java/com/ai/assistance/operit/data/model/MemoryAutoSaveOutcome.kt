package com.ai.assistance.operit.data.model

/**
 * Result of the most recent automatic memory-save pass for one memory space.
 *
 * [NO_MEMORY_EXTRACTED] is a normal outcome: the model answered that there was nothing worth
 * saving. It must not be shown or logged as [FAILED], because [FAILED] means a real error that is
 * still subject to the retry policy.
 */
enum class MemoryAutoSaveOutcome {
    IDLE,
    SAVED,
    NO_MEMORY_EXTRACTED,
    FAILED
}
