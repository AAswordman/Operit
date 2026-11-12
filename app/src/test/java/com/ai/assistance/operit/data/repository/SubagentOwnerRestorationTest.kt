package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.Room
import com.ai.assistance.operit.core.agent.collaboration.CollaborationAgent
import com.ai.assistance.operit.core.agent.collaboration.CollaborationFileAccess
import com.ai.assistance.operit.core.agent.collaboration.CollaborationState
import com.ai.assistance.operit.core.agent.collaboration.CollaborationStore
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.JdbcSQLiteDriver
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.SubagentRunEntity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubagentOwnerRestorationTest {
    @get:Rule val temp = TemporaryFolder()

    private class Files : CollaborationFileAccess {
        var reads = 0
        var cancel = false
        override fun exists(file: File) = file.exists()
        override fun read(file: File): String {
            reads++
            if (cancel) throw CancellationException("cancel restoration")
            return file.readText()
        }
        override fun write(file: File, text: String) = error("Restoration must not write collaboration files")
    }

    private fun worker(child: String = "child", run: String = "run") =
        CollaborationAgent("parent", "/root/worker", child, runId = run)

    private fun legacy(agents: List<CollaborationAgent>) =
        File(temp.root, CollaborationStore.LEGACY).apply {
            writeText(Json.encodeToString(CollaborationState(agents = agents)))
        }

    @Test fun corruptStoreLeavesDatabaseUsableAndRepairCanRetry() = withDatabase { db ->
        seed(db)
        val file = File(temp.root, CollaborationStore.LEGACY).apply { writeText("{broken") }
        val repository = SubagentRunRepository(db)
        val store = CollaborationStore(temp.root, Files())
        assertNotNull(runCatching { repository.restoreV2Owners(store) }.exceptionOrNull())
        assertTrue(db.subagentRunDao().hasPendingOwnerRestore())
        assertEquals("{broken", file.readText())
        assertNull(db.subagentRunDao().getById("run")!!.externalOwnerType)
        assertEquals("Parent history", db.chatDao().getChatById("parent")!!.title)
        legacy(listOf(worker()))
        repository.restoreV2Owners(store)
        assertEquals("/root/worker", db.subagentRunDao().getById("run")!!.externalOwnerId)
        assertFalse(db.subagentRunDao().hasPendingOwnerRestore())
    }

    @Test fun missingReferencedTreeDoesNotPreventDatabaseReads() = withDatabase { db ->
        seed(db)
        val directory = File(temp.root, CollaborationStore.DIRECTORY).apply { mkdirs() }
        val tree = "11111111-1111-1111-1111-111111111111.json"
        File(directory, "manifest.json").writeText("""{"version":1,"trees":{"parent":"$tree"}}""")
        val repository = SubagentRunRepository(db)
        val store = CollaborationStore(temp.root, Files())
        assertTrue(runCatching { repository.restoreV2Owners(store) }.exceptionOrNull() is java.io.FileNotFoundException)
        assertEquals(1, db.subagentRunDao().getAll().size)
        File(directory, tree).writeText(Json.encodeToString(CollaborationState(agents = listOf(worker()))))
        repository.restoreV2Owners(store)
        assertEquals("subagent_v2", db.subagentRunDao().getById("run")!!.externalOwnerType)
    }

    @Test fun restoreMatchesRunAndChildAndPreservesExistingOwners() = withDatabase { db ->
        seed(db)
        seed(db, "wrong-child", "wrong-run")
        seed(db, "owned-child", "owned-run", "existing", "/existing")
        legacy(listOf(worker().copy(archived = true),
            worker("unrelated-child", "wrong-run"), worker("owned-child", "owned-run")))
        SubagentRunRepository(db).restoreV2Owners(CollaborationStore(temp.root, Files()))
        assertEquals("/root/worker", db.subagentRunDao().getById("run")!!.externalOwnerId)
        assertNull(db.subagentRunDao().getById("wrong-run")!!.externalOwnerId)
        assertEquals("existing", db.subagentRunDao().getById("owned-run")!!.externalOwnerType)
        assertEquals("/existing", db.subagentRunDao().getById("owned-run")!!.externalOwnerId)
    }

    @Test fun successfulRestoreIsNotRepeatedByANewRepositoryEvenWithLegacyRuns() = withDatabase { db ->
        seed(db)
        seed(db, "v1-child", "v1-run")
        legacy(listOf(worker()))
        val files = Files()
        val repository = SubagentRunRepository(db)
        SubagentRunRepository(db).restoreV2Owners(CollaborationStore(temp.root, files))
        val reads = files.reads
        File(temp.root, CollaborationStore.LEGACY).writeText("{broken")
        repository.restoreV2Owners(CollaborationStore(temp.root, files))
        assertEquals(reads, files.reads)
        assertNull(db.subagentRunDao().getById("v1-run")!!.externalOwnerType)
    }

    @Test fun cancellationPropagatesAndDoesNotMarkRestorationComplete() = withDatabase { db ->
        seed(db)
        legacy(listOf(worker()))
        val files = Files().apply { cancel = true }
        val repository = SubagentRunRepository(db)
        val store = CollaborationStore(temp.root, files)
        assertTrue(runCatching { repository.restoreV2Owners(store) }.exceptionOrNull() is CancellationException)
        files.cancel = false
        repository.restoreV2Owners(store)
        assertEquals("/root/worker", db.subagentRunDao().getById("run")!!.externalOwnerId)
    }

    @Test fun noUnownedRunsDoesNotReadEvenACorruptStore() = withDatabase { db ->
        seed(db, type = "subagent_v2", path = "/root/worker")
        File(temp.root, CollaborationStore.LEGACY).writeText("{broken")
        val files = Files()
        SubagentRunRepository(db).restoreV2Owners(CollaborationStore(temp.root, files))
        assertEquals(0, files.reads)
    }

    private suspend fun seed(db: AppDatabase, child: String = "child", run: String = "run",
        type: String? = null, path: String? = null) {
        if (db.chatDao().getChatById("parent") == null) {
            db.chatDao().insertChat(ChatEntity(id = "parent", title = "Parent history"))
        }
        db.chatDao().insertChat(ChatEntity(id = child, title = child, parentChatId = "parent", chatKind = "SUBAGENT"))
        db.subagentRunDao().insert(SubagentRunEntity(id = run, parentChatId = "parent", childChatId = child,
            agentProfileId = "profile", title = "User editable title", externalOwnerType = type, externalOwnerId = path))
    }

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.packageName).thenReturn("com.ai.assistance.operit")
        whenever(context.filesDir).thenReturn(temp.root)
        whenever(context.getDatabasePath(any())).thenAnswer { File(temp.root, it.getArgument<String>(0)) }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "owner-restore-test")
            .setDriver(JdbcSQLiteDriver()).allowMainThreadQueries().build()
        try {
            runBlocking {
                // Fresh v24 databases have no checkpoint. This models the row created by 23→24.
                db.subagentRunDao().markPendingOwnerRestore(
                    com.ai.assistance.operit.data.model.PendingSubagentOwnerRestore()
                )
                block(db)
            }
        } finally { db.close() }
    }
}
