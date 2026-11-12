package com.ai.assistance.operit.data.repository

/**
 * Why a run that never reached a terminal state is stored as INTERRUPTED.
 *
 * The writers and the conversation card read the same constants, so the card can explain the reason
 * in the user's language instead of showing the stored sentence as a failure.
 */
internal object SubagentInterruption {
    /** The process was killed while the run was still active. */
    const val APP_RESTART = "The app stopped before this Subagent task reached a terminal state."

    /** A frozen archive was restored while the run was still active. */
    const val ARCHIVE_IMPORT = "Imported while the Subagent task was incomplete."

    /** A branch snapshot copied the run while it was still active. */
    const val CHAT_BRANCH = "This Subagent run was interrupted because its parent chat was branched."

    /** The reading companion reconciled the run as missing after a restart. */
    const val READING_COMPANION = "Reading companion run was reconciled as missing after restart."
}
