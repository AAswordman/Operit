package com.ai.assistance.operit.ui.common.markdown

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces render updates without losing the last mutation.
 *
 * Requests must be made after renderable state changes. Calls are serialized by the owning
 * Compose scope, so the revision check and job teardown cannot be interleaved by another request.
 */
internal class RenderBatchCoordinator(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val onFlush: () -> Unit,
) {
    private var requestedRevision = 0L
    private var appliedRevision = 0L
    private var updateJob: Job? = null

    fun requestUpdate() {
        requestedRevision++
        if (updateJob?.isActive == true) {
            return
        }

        updateJob =
            scope.launch {
                try {
                    while (appliedRevision != requestedRevision) {
                        delay(intervalMs)
                        val revisionToApply = requestedRevision
                        onFlush()
                        appliedRevision = revisionToApply
                    }
                } finally {
                    updateJob = null
                }
            }
    }
}
