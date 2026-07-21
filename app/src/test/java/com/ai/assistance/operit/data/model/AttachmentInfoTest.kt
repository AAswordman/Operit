package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentInfoTest {

    @Test fun `create with required fields`() {
        val info = AttachmentInfo(
            filePath = "/path/to/file.txt",
            fileName = "file.txt",
            mimeType = "text/plain",
            fileSize = 1024L,
        )
        assertEquals("/path/to/file.txt", info.filePath)
        assertEquals("file.txt", info.fileName)
        assertEquals("text/plain", info.mimeType)
        assertEquals(1024L, info.fileSize)
    }

    @Test fun `content defaults to empty`() {
        val info = AttachmentInfo(
            filePath = "/path/file.txt",
            fileName = "file.txt",
            mimeType = "text/plain",
            fileSize = 0L,
        )
        assertEquals("", info.content)
    }

    @Test fun `content can be set`() {
        val info = AttachmentInfo(
            filePath = "/path/file.txt",
            fileName = "file.txt",
            mimeType = "text/plain",
            fileSize = 10L,
            content = "inline content",
        )
        assertEquals("inline content", info.content)
    }

    @Test fun `copy with different file path`() {
        val info = AttachmentInfo("/old", "file.txt", "text/plain", 100L)
        val copy = info.copy(filePath = "/new")
        assertEquals("/new", copy.filePath)
        assertEquals("file.txt", copy.fileName)
    }

    @Test fun `image attachment`() {
        val info = AttachmentInfo("/img/photo.jpg", "photo.jpg", "image/jpeg", 50000L)
        assertEquals("image/jpeg", info.mimeType)
        assertEquals(50000L, info.fileSize)
    }

    @Test fun `pdf attachment`() {
        val info = AttachmentInfo("/docs/report.pdf", "report.pdf", "application/pdf", 200000L)
        assertEquals("application/pdf", info.mimeType)
        assertEquals(200000L, info.fileSize)
    }

    @Test fun `zero file size`() {
        val info = AttachmentInfo("/empty", "empty.txt", "text/plain", 0L)
        assertEquals(0L, info.fileSize)
    }

    @Test fun `large file size`() {
        val info = AttachmentInfo("/big", "big.mp4", "video/mp4", 1_000_000_000L)
        assertEquals(1_000_000_000L, info.fileSize)
    }

    @Test fun `content with special characters`() {
        val info = AttachmentInfo("/f", "f.txt", "text/plain", 10L, content = "Hello\nWorld")
        assertEquals("Hello\nWorld", info.content)
    }

    @Test fun `copy preserves mime type`() {
        val info = AttachmentInfo("/a", "a.pdf", "application/pdf", 100L)
        val copy = info.copy(fileSize = 200L)
        assertEquals("application/pdf", copy.mimeType)
    }
}
