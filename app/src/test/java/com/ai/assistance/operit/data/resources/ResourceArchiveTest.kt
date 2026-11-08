package com.ai.assistance.operit.data.resources

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val content = "verified model".toByteArray()
    private val spec = ResourceFileSpec("model.bin", content.size.toLong(), sha256(content))

    @Test fun exportAndImportPreserveExactContent() {
        val source = temporary.newFolder("source")
        File(source, spec.name).writeBytes(content)
        val output = ByteArrayOutputStream()
        ResourceArchive.exportZip(output, listOf(spec), source)
        val destination = temporary.newFolder("destination")
        ResourceArchive.importZip(ByteArrayInputStream(output.toByteArray()), listOf(spec), File(temporary.root, "stage"), destination)
        assertArrayEquals(content, File(destination, spec.name).readBytes())
        assertTrue(ResourceArchive.verified(File(destination, spec.name), spec))
        assertFalse(File(temporary.root, "stage").exists())
    }

    @Test fun corruptedContentDoesNotOverwriteExistingResource() {
        rejectArchive(listOf(spec.name to ByteArray(content.size) { 1 }))
    }

    @Test fun truncatedContentIsRejected() {
        rejectArchive(listOf(spec.name to content.copyOf(content.size - 1)))
    }

    @Test fun oversizedContentIsRejectedBeforeInstallation() {
        rejectArchive(listOf(spec.name to (content + byteArrayOf(1))))
    }

    @Test fun missingFileDoesNotInstallAnIncompleteGroup() {
        val second = ResourceFileSpec("tokens.txt", 1, sha256(byteArrayOf(1)))
        rejectArchive(listOf(spec.name to content), listOf(spec, second))
    }

    @Test fun traversalAndUnknownEntriesAreRejected() {
        listOf("../outside", "/absolute", "nested/model.bin", "unknown.bin").forEachIndexed { index, name ->
            rejectArchive(listOf(name to content), suffix = index.toString())
        }
        assertFalse(File(temporary.root, "outside").exists())
    }

    @Test fun duplicateEntriesAreRejected() {
        // ZIP 写入器拒绝同名成员，先写等长名称再替换原始 ZIP 中的名称。
        val bytes = zip(listOf("model.bin" to content, "other.bin" to content))
        val old = "other.bin".toByteArray()
        val replacement = "model.bin".toByteArray()
        for (i in 0..bytes.size - old.size) {
            if (old.indices.all { bytes[i + it] == old[it] }) replacement.copyInto(bytes, i)
        }
        rejectBytes(bytes)
    }

    @Test fun cancellationLeavesInstalledContentUntouchedAndRemovesStaging() {
        val directory = temporary.newFolder("cancel-destination")
        File(directory, spec.name).writeText("old")
        val staging = File(temporary.root, "cancel-stage")
        val cancelled = IllegalStateException("cancelled")
        val thrown = assertThrows(IllegalStateException::class.java) {
            ResourceArchive.importZip(ByteArrayInputStream(zip(listOf(spec.name to content))), listOf(spec), staging, directory, { throw cancelled })
        }
        assertSame(cancelled, thrown)
        assertEquals("old", File(directory, spec.name).readText())
        assertFalse(staging.exists())
    }

    @Test fun fileWithCorrectSizeAndWrongHashIsNotReady() {
        val file = temporary.newFile("bad.bin")
        file.writeBytes(ByteArray(content.size) { 2 })
        assertFalse(ResourceArchive.verified(file, spec))
    }

    private fun rejectArchive(entries: List<Pair<String, ByteArray>>, specs: List<ResourceFileSpec> = listOf(spec), suffix: String = "") {
        rejectBytes(zip(entries), specs, suffix)
    }

    private fun rejectBytes(bytes: ByteArray, specs: List<ResourceFileSpec> = listOf(spec), suffix: String = "") {
        val destination = temporary.newFolder("reject-destination$suffix")
        File(destination, spec.name).writeText("previous resource")
        val staging = File(temporary.root, "reject-stage$suffix")
        assertThrows(IOException::class.java) {
            ResourceArchive.importZip(ByteArrayInputStream(bytes), specs, staging, destination)
        }
        assertEquals("previous resource", File(destination, spec.name).readText())
        assertFalse(staging.exists())
    }

    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
