package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.OperitChatArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatArchiveImportPolicyTest {
    @Test
    fun v2MissingFavorite_preservesLocalState() {
        assertEquals(
            true,
            ChatArchiveImportPolicy.resolveFavorite(
                formatVersion = 2,
                archivedFavorite = null,
                localFavorite = true,
            ),
        )
        assertEquals(
            false,
            ChatArchiveImportPolicy.resolveFavorite(
                formatVersion = 2,
                archivedFavorite = null,
                localFavorite = false,
            ),
        )
    }

    @Test
    fun v3ExplicitFavorite_overridesLocalState() {
        assertEquals(
            false,
            ChatArchiveImportPolicy.resolveFavorite(
                formatVersion = 3,
                archivedFavorite = false,
                localFavorite = true,
            ),
        )
        assertEquals(
            true,
            ChatArchiveImportPolicy.resolveFavorite(
                formatVersion = 3,
                archivedFavorite = true,
                localFavorite = false,
            ),
        )
    }

    @Test
    fun v3MissingFavorite_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatArchiveImportPolicy.resolveFavorite(
                formatVersion = 3,
                archivedFavorite = null,
                localFavorite = true,
            )
        }
    }

    @Test
    fun headerValidation_acceptsOperitV2ThroughV4() {
        ChatArchiveImportPolicy.validateHeader(OperitChatArchive.ARCHIVE_TYPE, 2)
        ChatArchiveImportPolicy.validateHeader(OperitChatArchive.ARCHIVE_TYPE, 3)
        ChatArchiveImportPolicy.validateHeader(OperitChatArchive.ARCHIVE_TYPE, 4)

        assertThrows(IllegalArgumentException::class.java) {
            ChatArchiveImportPolicy.validateHeader("other", 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatArchiveImportPolicy.validateHeader(OperitChatArchive.ARCHIVE_TYPE, 5)
        }
    }
}
