package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.OperitChatArchive
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatArchiveImportPolicyTest {
    @Test
    fun acceptsReleasedArchiveAndCollaborationArchive() {
        ChatArchiveImportPolicy.validateHeader(OperitChatArchive.ARCHIVE_TYPE, 2)
        ChatArchiveImportPolicy.validateHeader(OperitChatArchive.ARCHIVE_TYPE, 5)
    }

    @Test
    fun rejectsOtherArchiveContracts() {
        for (version in listOf(1, 3, 4, 6)) {
            assertThrows(IllegalArgumentException::class.java) {
                ChatArchiveImportPolicy.validateHeader(OperitChatArchive.ARCHIVE_TYPE, version)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatArchiveImportPolicy.validateHeader("other", 5)
        }
    }
}
