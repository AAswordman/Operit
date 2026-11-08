package com.ai.assistance.operit.data.updates

/**
 * One download source probed for an update artifact, plus the reason it cannot be
 * used when it failed probing.
 *
 * A probe only samples the first bytes of a file, so a source can pass it and still
 * die with 403/404/timeout partway through the real transfer. The updater therefore
 * never commits to a single winner: it keeps every source that passed, ordered
 * fastest first, and walks the list until one delivers a verifiable artifact.
 *
 * @param probeError null when the source is usable, otherwise the failure reason to
 *   show the user.
 */
data class SourceCandidate(
    val key: String,
    val url: String,
    val speedBytesPerSec: Long? = null,
    val latencyMs: Long? = null,
    val probeError: String? = null
) {
    val isUsable: Boolean get() = probeError == null
}

/**
 * Probe outcome split into the sources the updater may download from and the ones it
 * must skip.
 */
data class RankedSources(
    val usable: List<SourceCandidate>,
    val rejected: List<SourceCandidate>
) {
    val hasUsable: Boolean get() = usable.isNotEmpty()

    /**
     * Sources still worth trying, in order, minus the ones that already failed during
     * this update.
     */
    fun remaining(failedKeys: Set<String>): List<SourceCandidate> =
        usable.filterNot { failedKeys.contains(it.key) }

    /**
     * Same list with [preferredKey] moved to the front, used when the user picks a
     * source by hand: their choice goes first, the rest stay available after it.
     *
     * [preferredKey] is read from the same candidate list the UI rendered, so it is
     * always present among the usable sources.
     */
    fun prioritized(preferredKey: String, failedKeys: Set<String>): List<SourceCandidate> {
        val remaining = remaining(failedKeys)
        val chosen = remaining.partition { it.key == preferredKey }
        return chosen.first + chosen.second
    }
}

/**
 * Turns probe results into the ordered source list both update paths walk.
 *
 * Ranking lives here rather than in each installer so the full APK and patch flows
 * apply one policy: throughput first, latency to break ties, key last so the order is
 * stable between runs and the UI list does not reshuffle on its own.
 */
object UpdateSourceRanking {

    private val fastestFirst =
        compareByDescending<SourceCandidate> { it.speedBytesPerSec ?: -1L }
            .thenBy { it.latencyMs ?: Long.MAX_VALUE }
            .thenBy { it.key }

    fun rank(candidates: Collection<SourceCandidate>): RankedSources {
        val (usable, rejected) = candidates.partition { it.isUsable }
        return RankedSources(
            usable = usable.sortedWith(fastestFirst),
            rejected = rejected.sortedBy { it.key }
        )
    }

    /**
     * Per-source failure lines for the "could not download" message: probe
     * rejections first, then the sources that failed while actually downloading.
     */
    fun failureLines(
        rejected: List<SourceCandidate>,
        downloadFailures: Map<String, String>
    ): List<String> =
        rejected.map { "${it.key}: ${it.probeError.orEmpty()}" } +
            downloadFailures.entries.map { "${it.key}: ${it.value}" }
}
