package com.ai.assistance.operit.data.resources.models

import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.GZIPOutputStream

class ModelArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { archive ->
            entries.forEach { (path, text) -> archive.putNextEntry(ZipEntry(path)); archive.write(text.toByteArray()); archive.closeEntry() }
        }
    }.toByteArray()

    private fun tarArchive(bzip2: Boolean, write: (TarArchiveOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        val compressed: OutputStream =
            if (bzip2) BZip2CompressorOutputStream(bytes) else GZIPOutputStream(bytes)
        TarArchiveOutputStream(compressed).use(write)
        return bytes.toByteArray()
    }

    @Test fun traversalDoesNotWriteOutsideStaging() {
        val stage = File(temporary.root, "stage")
        val bytes = zip("../escaped" to "invalid")
        assertThrows(IllegalArgumentException::class.java) { ModelArchive.unpack(bytes.inputStream(), "model.zip", stage) }
        assertFalse(File(temporary.root, "escaped").exists())
    }
    @Test fun duplicateArchiveFileIsRejected() {
        val bytes = ByteArrayOutputStream()
        ZipArchiveOutputStream(bytes).use { archive ->
            repeat(2) { archive.putArchiveEntry(ZipArchiveEntry("tokens.txt")); archive.write(byteArrayOf(1)); archive.closeArchiveEntry() }
        }
        assertThrows(IllegalArgumentException::class.java) { ModelArchive.unpack(bytes.toByteArray().inputStream(), "model.zip", temporary.newFolder("duplicate")) }
    }
    @Test fun tarSymbolicLinkCannotEscapeStaging() {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(GZIPOutputStream(bytes)).use { archive ->
            val link = TarArchiveEntry("model", TarConstants.LF_SYMLINK)
            link.linkName = "../escaped"
            archive.putArchiveEntry(link)
            archive.closeArchiveEntry()
        }
        val stage = temporary.newFolder("tar")
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ModelArchive.unpack(bytes.toByteArray().inputStream(), "model.tar.gz", stage)
        }
        assertEquals("资源包不支持链接或设备条目", failure.message)
        assertFalse(File(stage, "model").exists())
        assertFalse(File(temporary.root, "escaped").exists())
    }
    @Test fun tarRejectsSpecialTypesBeforeWritingForBothCompressionFormats() {
        val types = listOf(
            TarConstants.LF_SYMLINK, TarConstants.LF_LINK, TarConstants.LF_CHR,
            TarConstants.LF_BLK, TarConstants.LF_FIFO, TarConstants.LF_CONTIG, 'Z'.code.toByte()
        )
        for (bzip2 in listOf(false, true)) {
            for (type in types) {
                val bytes = tarArchive(bzip2) { archive ->
                    val entry = TarArchiveEntry("model", type)
                    if (type == TarConstants.LF_LINK || type == TarConstants.LF_SYMLINK) {
                        entry.linkName = "../escaped"
                    }
                    archive.putArchiveEntry(entry)
                    archive.closeArchiveEntry()
                }
                val stage = temporary.newFolder("rejected-$type-$bzip2")
                val name = if (bzip2) "model.tar.bz2" else "model.tar.gz"
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    ModelArchive.unpack(bytes.inputStream(), name, stage)
                }
                assertEquals("资源包不支持链接或设备条目", failure.message)
                assertFalse(File(stage, "model").exists())
                assertFalse(File(temporary.root, "escaped").exists())
            }
        }
    }

    @Test fun regularTarFilesAndDirectoriesWorkForBothCompressionFormats() {
        for (bzip2 in listOf(false, true)) {
            val bytes = tarArchive(bzip2) { archive ->
                archive.putArchiveEntry(TarArchiveEntry("model/", TarConstants.LF_DIR))
                archive.closeArchiveEntry()
                for (type in listOf(TarConstants.LF_OLDNORM, TarConstants.LF_NORMAL)) {
                    val entry = TarArchiveEntry("model/file-$type.txt", type)
                    val content = "verified".toByteArray()
                    entry.size = content.size.toLong()
                    archive.putArchiveEntry(entry)
                    archive.write(content)
                    archive.closeArchiveEntry()
                }
            }
            val stage = temporary.newFolder("regular-$bzip2")
            ModelArchive.unpack(bytes.inputStream(), if (bzip2) "model.tar.bz2" else "model.tar.gz", stage)
            for (type in listOf(TarConstants.LF_OLDNORM, TarConstants.LF_NORMAL)) {
                assertEquals("verified", File(stage, "model/file-$type.txt").readText())
            }
        }
    }

    @Test fun nestedUnicodeFileRoundTripsWithEngineBackup() {
        val model = temporary.newFolder("model")
        val engine = temporary.newFolder("engine")
        File(model, "词典").mkdir()
        File(model, "词典/tokens.txt").writeText("你好")
        File(engine, "runtime.so").writeText("fixed engine")
        val bytes = ByteArrayOutputStream()
        ModelArchive.export(model, bytes, {}, { _, _ -> }, mapOf("operit-engine" to engine))
        val restored = temporary.newFolder("restored")
        ModelArchive.unpack(bytes.toByteArray().inputStream(), "backup.zip", restored)
        assertEquals("你好", File(restored, "词典/tokens.txt").readText())
        assertEquals("fixed engine", File(restored, "operit-engine/runtime.so").readText())
    }
    @Test fun sourceFailureRestoresExistingInstallation() {
        val installed = temporary.newFolder("installed")
        File(installed, "tokens.txt").writeText("old")
        assertThrows(Exception::class.java) { ModelArchive.install(File(temporary.root, "missing"), installed, {}) }
        assertEquals("old", File(installed, "tokens.txt").readText())
    }
    @Test fun busyParentPreventsReplacementAndKeepsBothDirectories() {
        val installed = temporary.newFolder("busy")
        File(installed, "tokens.txt").writeText("old")
        val stage = temporary.newFolder("replacement")
        File(stage, "tokens.txt").writeText("new")
        val usage = checkNotNull(LocalModelRuntimeRegistry.acquire(temporary.root))
        try { assertThrows(IllegalStateException::class.java) { ModelArchive.install(stage, installed, {}) } }
        finally { usage.close() }
        assertEquals("old", File(installed, "tokens.txt").readText())
        assertEquals("new", File(stage, "tokens.txt").readText())
    }
    @Test fun cancellationBeforeCommitPreservesInstallation() {
        val installed = temporary.newFolder("cancelled")
        File(installed, "tokens.txt").writeText("old")
        val stage = temporary.newFolder("staging")
        File(stage, "tokens.txt").writeText("new")
        assertThrows(CancellationException::class.java) { ModelArchive.install(stage, installed) { throw CancellationException() } }
        assertEquals("old", File(installed, "tokens.txt").readText())
    }
    @Test fun replacementBlocksNewReadersUntilDirectorySwitchCompletes() {
        val path = temporary.newFolder("exclusive")
        assertTrue(LocalModelRuntimeRegistry.withExclusiveAccess(path) {
            assertNull(LocalModelRuntimeRegistry.acquire(File(path, "tokens.txt")))
        })
        val handle = LocalModelRuntimeRegistry.acquire(path)
        assertNotNull(handle)
        handle?.close()
    }
    @Test fun exclusiveHoldIsReleasedWhenMutationFails() {
        val path = temporary.newFolder("exception")
        assertThrows(IllegalArgumentException::class.java) { LocalModelRuntimeRegistry.withExclusiveAccess(path) { throw IllegalArgumentException("failure") } }
        val handle = LocalModelRuntimeRegistry.acquire(path)
        assertNotNull(handle)
        handle?.close()
    }
    @Test fun downloadLargerThanManifestIsRejected() {
        val target = File(temporary.root, "partial")
        val spec = ModelFile("model.onnx", 1, "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { ModelArchive.write(byteArrayOf(1, 2).inputStream(), target, spec, {}) }
    }
    @Test fun wrongDigestCannotBeMarkedVerified() {
        val folder = temporary.newFolder("digest")
        val file = File(folder, "model.onnx").apply { writeText("bad") }
        assertFalse(ModelArchive.verified(folder, ModelFile(file.name, file.length(), "0".repeat(64))))
    }

    @Test fun activeOldVersionPreventsGroupScopedUpgrade() {
        val group = temporary.newFolder("plugin")
        val old = File(group, "old").apply { mkdirs() }
        File(old, "plugin.js").writeText("old")
        val current = File(group, "current")
        val stage = temporary.newFolder("plugin-stage")
        File(stage, "plugin.js").writeText("new")
        val usage = checkNotNull(LocalModelRuntimeRegistry.acquire(old))
        try {
            assertThrows(IllegalStateException::class.java) {
                ModelArchive.install(stage, current, group, {})
            }
            assertEquals("old", File(old, "plugin.js").readText())
            assertFalse(current.exists())
            assertTrue(stage.isDirectory)
        } finally { usage.close() }
        ModelArchive.install(stage, current, group, {})
        assertEquals("new", File(current, "plugin.js").readText())
        val newUsage = checkNotNull(LocalModelRuntimeRegistry.acquire(current))
        newUsage.close()
    }
}
