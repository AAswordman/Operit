package com.ai.assistance.operit.core.terminal.commands

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeCommandAssetsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun installsAndReusesVerifiedAsset() {
        val destination = File(temporary.root, "commands/apply_patch")
        val bytes = "命令正文".toByteArray()
        NativeCommandAssets.install(destination, digest(bytes)) { ByteArrayInputStream(bytes) }
        assertArrayEquals(bytes, destination.readBytes())
        NativeCommandAssets.install(destination, digest(bytes)) { error("相同资源不应重新读取") }
    }

    @Test fun failedUpgradeKeepsPreviousAssetAndRemovesTemporaryFile() {
        val directory = temporary.newFolder()
        val destination = File(directory, "apply_patch").apply { writeText("旧版本") }
        assertThrows(IllegalStateException::class.java) {
            NativeCommandAssets.install(destination, digest("新版本".toByteArray())) {
                ByteArrayInputStream("损坏内容".toByteArray())
            }
        }
        assertEquals("旧版本", destination.readText())
        assertEquals(listOf("apply_patch"), directory.listFiles()!!.map { it.name })
    }

    @Test fun replacesDestinationSymlinkWithoutChangingItsTarget() {
        val target = temporary.newFile().apply { writeText("保留") }
        val destination = File(temporary.root, "apply_patch")
        Files.createSymbolicLink(destination.toPath(), target.toPath())
        val bytes = "命令正文".toByteArray()
        NativeCommandAssets.install(destination, digest(bytes)) { ByteArrayInputStream(bytes) }
        assertFalse(Files.isSymbolicLink(destination.toPath()))
        assertArrayEquals(bytes, destination.readBytes())
        assertEquals("保留", target.readText())
    }
}
