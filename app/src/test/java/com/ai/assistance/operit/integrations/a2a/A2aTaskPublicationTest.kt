package com.ai.assistance.operit.integrations.a2a

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2aTaskPublicationTest {
    @Test
    fun `retry replacement reaches live subscribers before completion including empty output`() {
        for (finalText in listOf("correct answer", "")) {
            val record = A2aTaskManager.TaskRecord("task", "context")
            val events = mutableListOf<A2aTaskEvent>()
            record.subscribe(false) { events.add(it) }.use {
                record.appendOutput("discarded prefix")
                record.appendOutput("correct answer")
                record.replaceOutput(finalText)
                record.complete()
            }
            var displayed = ""
            events.filterIsInstance<A2aTaskEvent.Artifact>().forEach { event ->
                displayed = if (event.append) displayed + event.text else event.text
            }
            assertEquals(finalText, displayed)
            assertEquals(record.snapshot().output, displayed)
            val replacement = events[events.lastIndex - 1] as A2aTaskEvent.Artifact
            val update = A2aJsonSerialization.eventStreamFrame(1, replacement)
                .payload.getJSONObject("result").getJSONObject("artifactUpdate")
            assertFalse(update.getBoolean("append"))
            assertTrue(update.getBoolean("lastChunk"))
            assertTrue(A2aJsonSerialization.eventStreamFrame(1, events.last()).endsStream)
        }
    }

    @Test
    fun `concurrent subscription reconstructs each output chunk exactly once`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val record = A2aTaskManager.TaskRecord("task", "context")
                val start = CountDownLatch(1)
                val events = java.util.concurrent.CopyOnWriteArrayList<A2aTaskEvent>()
                val producer = executor.submit {
                    start.await()
                    repeat(100) { index -> record.appendOutput("$index,") }
                    record.complete()
                }
                val subscriber = executor.submit<A2aTaskSubscription> {
                    start.await()
                    record.subscribe(false) { events.add(it) }
                }
                start.countDown()
                val subscription = subscriber.get(5, TimeUnit.SECONDS)
                producer.get(5, TimeUnit.SECONDS)
                subscription.close()
                val reconstructed = subscription.snapshot.output + events
                    .filterIsInstance<A2aTaskEvent.Artifact>().joinToString("") { event -> event.text }
                assertEquals(record.snapshot().output, reconstructed)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
