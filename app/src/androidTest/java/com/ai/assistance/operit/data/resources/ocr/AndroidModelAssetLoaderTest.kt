package com.ai.assistance.operit.data.resources.ocr

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class AndroidModelAssetLoaderTest {
    @Test fun storedModelAssetCanBeReadThroughAndroidAssetManager() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = 1.01f })
        val name = "asset-check-${UUID.randomUUID()}.txt"
        val bytes = "外置模型资产".toByteArray()
        val archive = File(app.cacheDir, "ocr-assets-${UUID.randomUUID()}.apk")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                val entry = ZipEntry("assets/$name").apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            val loader = AndroidModelAssetLoader.attach(context, archive)
            assertNotNull(loader)
            assertArrayEquals(bytes, context.assets.open(name).use { it.readBytes() })
            context.assets.openFd(name).use { assertEquals(bytes.size.toLong(), it.length) }
        } finally { archive.delete() }
    }
}
