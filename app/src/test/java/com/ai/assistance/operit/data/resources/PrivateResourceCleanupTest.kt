package com.ai.assistance.operit.data.resources

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateResourceCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun asset(root: File, name: String): File = File(root, name).apply { writeText(name) }
    private fun uri(file: File): String = file.toURI().toASCIIString()

    @Test
    fun replacementDeletesOldFileOnlyAfterNewReferenceIsSaved() = runBlocking {
        val root = temporaryFolder.newFolder()
        val old = asset(root, "background_old.png")
        val new = asset(root, "background_new.png")
        var references = setOf(uri(old))
        val result = PrivateResourceCleanup.update(
            root,
            readReferences = { references },
            persist = {
                assertTrue(old.exists())
                assertTrue(new.exists())
                references = setOf(uri(new))
                "saved"
            },
            readAllReferences = { references },
            onCleanupFailure = { throw AssertionError(it) },
        )
        assertEquals("saved", result)
        assertFalse(old.exists())
        assertTrue(new.exists())
    }

    @Test
    fun failedPersistencePreservesOldFile() = runBlocking {
        val root = temporaryFolder.newFolder()
        val old = asset(root, "user_avatar_old.png")
        try {
            PrivateResourceCleanup.update<Unit>(
                root,
                readReferences = { setOf(uri(old)) },
                persist = { throw IOException("保存失败") },
                readAllReferences = { emptySet() },
                onCleanupFailure = { fail("保存失败时不应进入资源清理") },
            )
            fail("应传播保存失败")
        } catch (e: IOException) {
            assertEquals("保存失败", e.message)
        }
        assertTrue(old.exists())
    }

    @Test
    fun sharedResourceIsDeletedOnlyAfterLastReferenceIsRemoved() = runBlocking {
        val root = temporaryFolder.newFolder()
        val shared = asset(root, "global_user_avatar_shared.png")
        var characterReferences = setOf(uri(shared))
        var globalReferences = setOf(uri(shared))
        PrivateResourceCleanup.update(
            root,
            readReferences = { characterReferences },
            persist = { characterReferences = emptySet() },
            readAllReferences = { characterReferences + globalReferences },
            onCleanupFailure = { throw AssertionError(it) },
        )
        assertTrue(shared.exists())
        PrivateResourceCleanup.update(
            root,
            readReferences = { globalReferences },
            persist = { globalReferences = emptySet() },
            readAllReferences = { characterReferences + globalReferences },
            onCleanupFailure = { throw AssertionError(it) },
        )
        assertFalse(shared.exists())
    }

    @Test
    fun encodedUriAndAbsolutePathProtectSameResource() {
        val root = temporaryFolder.newFolder()
        val font = asset(root, "custom_font_中文 空格.ttf")
        val deleted = ManagedResourceFiles(root).deleteUnreferenced(
            setOf(uri(font)),
            setOf(font.absolutePath),
        ) { fail("保留的字体不应被删除") }
        assertTrue(deleted.isEmpty())
        assertTrue(font.exists())
    }

    @Test
    fun clearingSettingDeletesReferencedFileWithoutSweepingOldOrphans() {
        val root = temporaryFolder.newFolder()
        val old = asset(root, "avatar_card_old.png")
        val orphan = asset(root, "avatar_card_historical.png")
        val deleted = ManagedResourceFiles(root).deleteUnreferenced(
            setOf(uri(old)), emptySet(),
        ) { fail("旧头像应正常删除") }
        assertEquals(setOf(old.canonicalFile), deleted)
        assertTrue(orphan.exists())
    }

    @Test
    fun externalFilesApplicationDataAndDirectoriesArePreserved() {
        val root = temporaryFolder.newFolder()
        val external = asset(temporaryFolder.newFolder(), "background_external.png")
        val data = asset(root, "user_preferences.json")
        val directory = File(root, "background_directory").apply { mkdir() }
        val nested = asset(directory, "background_nested.png")
        val deleted = ManagedResourceFiles(root).deleteUnreferenced(
            setOf(uri(external), uri(data), uri(directory), uri(nested),
                "file:///android_asset/operit.png", "content://images/1", "https://example.com/image.png"),
            emptySet(),
        ) { fail("这些路径不应尝试删除") }
        assertTrue(deleted.isEmpty())
        assertTrue(external.exists())
        assertTrue(data.exists())
        assertTrue(directory.isDirectory)
        assertTrue(nested.exists())
    }

    @Test
    fun symbolicLinkCannotDeleteFileOutsidePrivateRoot() {
        val root = temporaryFolder.newFolder()
        val external = asset(temporaryFolder.newFolder(), "background_external.png")
        val link = File(root, "background_link.png")
        Files.createSymbolicLink(link.toPath(), external.toPath())
        val deleted = ManagedResourceFiles(root).deleteUnreferenced(
            setOf(uri(link)), emptySet(),
        ) { fail("外部符号链接不应触发删除") }
        assertTrue(deleted.isEmpty())
        assertTrue(external.exists())
        assertTrue(link.exists())
    }

    @Test
    fun preferenceReferencesIncludeInactiveCardsGroupsAndGlobalAvatar() {
        val references = ManagedResourceFiles.references(mapOf(
            "background_image_uri" to "file:///background.png",
            "character_card_theme_card_1_custom_user_avatar_uri" to "file:///user.png",
            "character_group_theme_group_2_custom_ai_avatar_uri" to "file:///group.png",
            "character_card_theme_other_custom_font_path" to "file:///font.ttf",
            "global_user_avatar_uri" to "file:///global.png",
            "workspace_uri" to "file:///workspace",
            "custom_chat_title" to "file:///title",
            "character_card_theme_other_use_background_image" to false,
        ))
        assertEquals(setOf("file:///background.png", "file:///user.png", "file:///group.png",
            "file:///font.ttf", "file:///global.png"), references)
    }

    @Test
    fun disablingResourceWithoutRemovingReferenceKeepsFile() = runBlocking {
        val root = temporaryFolder.newFolder()
        val font = asset(root, "bubble_user_font_retained.ttf")
        val references = setOf(uri(font))
        PrivateResourceCleanup.update(
            root, { references }, { Unit }, { references },
        ) { throw AssertionError(it) }
        assertTrue(font.exists())
    }

    @Test
    fun concurrentReplacementsDeleteIntermediateFileAndPreserveFinalFile() = runBlocking {
        val root = temporaryFolder.newFolder()
        val old = asset(root, "background_old.png")
        val middle = asset(root, "background_middle.png")
        val final = asset(root, "background_final.png")
        var references = setOf(uri(old))
        val firstStarted = CompletableDeferred<Unit>()
        val allowFirstSave = CompletableDeferred<Unit>()
        var secondStarted = false
        val first = launch {
            PrivateResourceCleanup.update(
                root, { references },
                persist = {
                    firstStarted.complete(Unit)
                    allowFirstSave.await()
                    references = setOf(uri(middle))
                },
                readAllReferences = { references },
                onCleanupFailure = { throw AssertionError(it) },
            )
        }
        firstStarted.await()
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            PrivateResourceCleanup.update(
                root, { references },
                persist = {
                    secondStarted = true
                    references = setOf(uri(final))
                },
                readAllReferences = { references },
                onCleanupFailure = { throw AssertionError(it) },
            )
        }
        assertFalse(secondStarted)
        allowFirstSave.complete(Unit)
        first.join()
        second.join()
        assertFalse(old.exists())
        assertFalse(middle.exists())
        assertTrue(final.exists())
    }

    @Test
    fun cancellationAfterPersistenceStillCompletesCleanup() = runBlocking {
        val root = temporaryFolder.newFolder()
        val old = asset(root, "group_avatar_old.png")
        val new = asset(root, "group_avatar_new.png")
        var references = setOf(uri(old))
        val job = launch {
            PrivateResourceCleanup.update(
                root, { references },
                persist = {
                    references = setOf(uri(new))
                    currentCoroutineContext().cancel()
                },
                readAllReferences = { references },
                onCleanupFailure = { throw AssertionError(it) },
            )
        }
        job.join()
        assertFalse(old.exists())
        assertTrue(new.exists())
    }

    @Test
    fun failedReferenceReadReportsErrorWithoutInvalidatingSavedSettings() = runBlocking {
        val root = temporaryFolder.newFolder()
        val old = asset(root, "background_old.png")
        var saved = false
        var failure: Exception? = null
        val result = PrivateResourceCleanup.update(
            root, { setOf(uri(old)) },
            persist = { saved = true; "saved" },
            readAllReferences = { throw IOException("引用读取失败") },
            onCleanupFailure = { failure = it },
        )
        assertTrue(saved)
        assertEquals("saved", result)
        assertEquals("引用读取失败", failure?.message)
        assertTrue(old.exists())
    }
}
