package com.ai.assistance.operit.data.persistence

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

internal object StorageQuarantineFiles {
    fun copyVerified(source: File, target: File) {
        require(source.isFile) { "Storage quarantine source is not a regular file" }
        check(!target.exists()) { "Storage quarantine target already exists" }
        val parent = requireNotNull(target.parentFile) {
            "Storage quarantine target has no parent directory"
        }
        check(parent.mkdirs() || parent.isDirectory) {
            "Failed to create storage quarantine target directory"
        }

        val sourceDigest = MessageDigest.getInstance("SHA-256")
        var copiedBytes = 0L
        FileInputStream(source).buffered().use { input ->
            FileOutputStream(target, false).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    sourceDigest.update(buffer, 0, read)
                    copiedBytes += read
                }
                output.flush()
                output.fd.sync()
            }
        }

        check(copiedBytes == source.length() && copiedBytes == target.length()) {
            "Storage quarantine copy length verification failed"
        }
        check(sourceDigest.digest().contentEquals(sha256(target))) {
            "Storage quarantine copy hash verification failed"
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
}
