package com.ai.assistance.operit.core.agent.collaboration

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CollaborationStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private class Files : CollaborationFileAccess {
        val writes = mutableListOf<String>()
        /** How many archived entries were written in all. */
        var archivedEntriesWritten = 0
        var failManifest = false
        override fun exists(file: File) = file.exists()
        override fun read(file: File) = file.readText()
        override fun write(file: File, text: String) {
            if (failManifest && file.name == "manifest.json") error("disk failure")
            writes.add(file.name)
            archivedEntriesWritten += Regex("\"archived\":true").findAll(text).count()
            file.parentFile!!.mkdirs()
            file.writeText(text)
        }
    }
    private fun agent(root: String) = CollaborationAgent(root, "/root", root)

    @Test fun `one changed tree does not rewrite other histories`() {
        val files = Files()
        val store = CollaborationStore(temp.root, files)
        val a = agent("a")
        val state = CollaborationState(agents = listOf(a, agent("b")))
        store.save(state)
        files.writes.clear()
        store.save(state.update(a.copy(finalAnswer = "finished")))
        assertEquals(2, files.writes.size) // one tree and one manifest
        files.writes.clear()
        store.save(state.update(a.copy(finalAnswer = "finished")))
        assertTrue(files.writes.isEmpty())
        assertEquals("finished", CollaborationStore(temp.root, files).load().find("a", "/root")!!.finalAnswer)
    }

    @Test fun `archived agents live in their own file and are not rewritten by live work`() {
        val files = Files()
        val store = CollaborationStore(temp.root, files)
        val root = agent("a")
        val old = CollaborationAgent("a", "/root/old", "old-chat", inheritedHistory =
            listOf(CollaborationTurn("USER", "long history")), systemPrompt = "prompt")
        val worker = CollaborationAgent("a", "/root/worker", "worker-chat")
        val archived = CollaborationState(agents = listOf(root, old, worker)).archive(setOf("old-chat"))
        val slim = archived.agents.single { it.archived }
        assertTrue(slim.inheritedHistory.isEmpty() && slim.systemPrompt.isEmpty())
        store.save(archived)
        files.writes.clear()
        val busy = archived.update(worker.copy(status = CollaborationStatus.RUNNING))
        store.save(busy)
        assertEquals(2, files.writes.size) // the live tree and the manifest; the archive stays
        val loaded = CollaborationStore(temp.root, files).load()
        assertEquals(busy.agents.toSet(), loaded.agents.toSet())
        assertNull(loaded.find("a", "/root/old"))
        val backup = temp.newFolder("backup")
        store.copyForBackup(backup)
        assertEquals(busy.agents.toSet(), CollaborationStore(backup, files).load().agents.toSet())
    }

    @Test fun `failed multi tree commit leaves old generation readable and can retry`() {
        val files = Files()
        val store = CollaborationStore(temp.root, files)
        val old = CollaborationState(agents = listOf(agent("a"), agent("b")))
        store.save(old)
        val next = old.copy(agents = old.agents.map { it.copy(status = CollaborationStatus.INTERRUPTED) })
        files.failManifest = true
        assertThrows(IllegalStateException::class.java) { store.save(next) }
        assertEquals(old, CollaborationStore(temp.root, files).load())
        files.failManifest = false
        store.save(next)
        assertEquals(next, CollaborationStore(temp.root, files).load())
    }

    @Test fun `legacy migration deletion and backup remain complete`() {
        val files = Files()
        val old = CollaborationState(agents = listOf(agent("a"), agent("b")))
        File(temp.root, CollaborationStore.LEGACY).writeText(Json.encodeToString(old))
        val store = CollaborationStore(temp.root, files)
        assertEquals(old, store.load())
        store.save(old)
        val backup = temp.newFolder("backup")
        store.copyForBackup(backup)
        store.save(CollaborationState())
        assertEquals(old, CollaborationStore(backup, files).load())
        assertTrue(CollaborationStore(temp.root, files).load().agents.isEmpty())
        assertFalse(File(temp.root, CollaborationStore.LEGACY).exists())
    }

    @Test fun `corrupt manifest cannot fall back to stale legacy`() {
        val files = Files()
        File(temp.root, CollaborationStore.LEGACY).writeText(Json.encodeToString(CollaborationState()))
        val dir = File(temp.root, CollaborationStore.DIRECTORY).apply { mkdirs() }
        File(dir, "manifest.json").writeText("""{"version":1,"trees":{"a":"../outside.json"}}""")
        assertThrows(IllegalArgumentException::class.java) { CollaborationStore(temp.root, files).load() }
    }

    @Test fun `archiving one agent at a time writes the past about log n times`() {
        val files = Files()
        val store = CollaborationStore(temp.root, files)
        val workers = (1..256).map { CollaborationAgent("a", "/root/w$it", "w$it") }
        var state = CollaborationState(agents = listOf(agent("a")) + workers)
        store.save(state)
        files.writes.clear()
        files.archivedEntriesWritten = 0
        workers.forEach { state = state.archive(setOf(it.chatId)); store.save(state) }
        // A full rewrite each time would write 256 * 257 / 2 = 32896 entries; segments keep it near n log n.
        val n = workers.size
        assertTrue("archived entries written: ${files.archivedEntriesWritten}", files.archivedEntriesWritten <= n * 10)
        val dir = File(temp.root, CollaborationStore.DIRECTORY)
        assertTrue(dir.listFiles()!!.size <= 12)
        assertEquals(state.agents.toSet(), CollaborationStore(temp.root, files).load().agents.toSet())
        val backup = temp.newFolder("segments-backup")
        store.copyForBackup(backup)
        assertEquals(state.agents.toSet(), CollaborationStore(backup, files).load().agents.toSet())
    }

    @Test fun `version 2 single archive file loads and moves to segments`() {
        val files = Files()
        val dir = File(temp.root, CollaborationStore.DIRECTORY).apply { mkdirs() }
        val live = agent("a")
        val old = CollaborationState(agents = listOf(live, CollaborationAgent("a", "/root/old", "old"))).archive(setOf("old"))
        File(dir, "11111111-1111-1111-1111-111111111111.json").writeText(Json.encodeToString(CollaborationState(agents = listOf(live))))
        File(dir, "22222222-2222-2222-2222-222222222222.json").writeText(Json.encodeToString(CollaborationState(agents = old.agents.filter { it.archived })))
        File(dir, "manifest.json").writeText(
            """{"version":2,"trees":{"a":"11111111-1111-1111-1111-111111111111.json"},"archives":{"a":"22222222-2222-2222-2222-222222222222.json"}}""",
        )
        val store = CollaborationStore(temp.root, files)
        assertEquals(old.agents.toSet(), store.load().agents.toSet())
        val next = CollaborationState(agents = old.agents + CollaborationAgent("a", "/root/new", "new")).archive(setOf("new"))
        store.save(next)
        assertTrue(File(dir, "manifest.json").readText().contains("\"version\":3"))
        assertEquals(next.agents.toSet(), CollaborationStore(temp.root, files).load().agents.toSet())
    }

    @Test fun `legacy restore replaces newer format instead of being shadowed`() {
        val files = Files()
        CollaborationStore(temp.root, files).save(CollaborationState(agents = listOf(agent("new"))))
        val backup = temp.newFolder("legacy-backup")
        val restored = CollaborationState(agents = listOf(agent("restored")))
        File(backup, CollaborationStore.LEGACY).writeText(Json.encodeToString(restored))
        CollaborationStore.prepareRestore(backup, temp.root)
        File(backup, CollaborationStore.LEGACY).copyTo(File(temp.root, CollaborationStore.LEGACY))
        assertEquals(restored, CollaborationStore(temp.root, files).load())
    }
}
