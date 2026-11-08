package com.ai.assistance.operit.services.core.attachments

import java.io.File
import java.security.MessageDigest

/** 待发送附件使用不可变副本；缓存内容指纹，按当前列表清理已移除项。调用方串行访问。 */
internal class ImageAttachmentIndex {
    private val fingerprints = mutableMapOf<String, ByteArray>()

    fun isDuplicate(image: File, currentImages: List<File>): Boolean {
        val currentPaths = currentImages.mapTo(hashSetOf()) { it.absolutePath }
        fingerprints.keys.retainAll(currentPaths)
        if (image.absolutePath in currentPaths) return true
        val incoming = fingerprint(image)
        val duplicate = currentImages.any { current ->
            fingerprints.getOrPut(current.absolutePath) { fingerprint(current) }.contentEquals(incoming)
        }
        if (!duplicate) fingerprints[image.absolutePath] = incoming
        return duplicate
    }

    private fun fingerprint(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = input.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return digest.digest()
    }
}