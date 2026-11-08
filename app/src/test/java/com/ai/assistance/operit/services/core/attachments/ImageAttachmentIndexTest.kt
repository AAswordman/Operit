package com.ai.assistance.operit.services.core.attachments

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageAttachmentIndexTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun identicalContentFromDifferentSourcesIsRejected() {
        val index = ImageAttachmentIndex()
        val first = image("first.png", byteArrayOf(1, 2, 3, 4))
        val second = image("renamed.png", byteArrayOf(1, 2, 3, 4))
        assertFalse(index.isDuplicate(first, emptyList()))
        assertTrue(index.isDuplicate(second, listOf(first)))
    }

    @Test
    fun sameFileNameWithDifferentContentIsAllowed() {
        val index = ImageAttachmentIndex()
        val first = File(folder.newFolder("one"), "image.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val second = File(folder.newFolder("two"), "image.png").apply { writeBytes(byteArrayOf(3, 4)) }
        assertFalse(index.isDuplicate(first, emptyList()))
        assertFalse(index.isDuplicate(second, listOf(first)))
    }

    @Test
    fun removingAnAttachmentAllowsAddingItsContentAgain() {
        val index = ImageAttachmentIndex()
        val first = image("first.png", byteArrayOf(5, 6, 7))
        val second = image("second.png", byteArrayOf(5, 6, 7))
        assertFalse(index.isDuplicate(first, emptyList()))
        assertTrue(index.isDuplicate(second, listOf(first)))
        assertFalse(index.isDuplicate(second, emptyList()))
    }

    @Test
    fun anExistingLocalPathIsAlreadyAttached() {
        val index = ImageAttachmentIndex()
        val first = image("first.png", byteArrayOf(8, 9))
        assertTrue(index.isDuplicate(first, listOf(first)))
    }

    private fun image(name: String, bytes: ByteArray): File = folder.newFile(name).apply { writeBytes(bytes) }
}