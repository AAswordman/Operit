package com.ai.assistance.operit.core.agent.collaboration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The idle early return must not fire while anything can still deliver a message. */
class CollaborationWaitWorkTest {
    private val root = "chat-root"
    private val own = "$root:/root"

    private fun has(
        mailboxEmpty: Boolean = true,
        jobs: Set<String> = emptySet(),
        reservations: Set<String> = emptySet(),
        peers: Set<String> = emptySet(),
        streaming: Set<String> = emptySet(),
    ) = hasWaitableWork(root, own, mailboxEmpty, jobs, reservations, peers, streaming)

    @Test fun anEmptyTreeHasNothingToWaitFor() {
        assertFalse(has())
    }

    @Test fun unconsumedMailAlwaysCounts() {
        assertTrue(has(mailboxEmpty = false))
    }

    @Test fun ownJobAndOtherRootConversationsDoNotCount() {
        assertFalse(has(jobs = setOf(own, "chat-other:/root/child")))
        assertFalse(has(reservations = setOf("chat-other:/root/pending")))
    }

    @Test fun liveJobAPendingSpawnAndAStreamingPeerAllCount() {
        assertTrue(has(jobs = setOf("$root:/root/child")))
        assertTrue(has(reservations = setOf("$root:/root/pending")))
        assertTrue(has(peers = setOf("chat-child"), streaming = setOf("chat-child")))
        // A child waiting on its parent must keep waiting while the root turn still streams.
        assertTrue(has(peers = setOf(root), streaming = setOf(root)))
        assertFalse(has(peers = setOf("chat-child"), streaming = setOf("chat-other")))
    }
}
