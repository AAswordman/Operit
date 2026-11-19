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
        var failManifest = false
        override fun exists(file: File) = file.exists()
        override fun read(file: File) = file.readText()
        override fun write(file: File, text: String) {
            if (failManifest && file.name == "manifest.json") error("disk failure")
            writes.add(file.name)
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
