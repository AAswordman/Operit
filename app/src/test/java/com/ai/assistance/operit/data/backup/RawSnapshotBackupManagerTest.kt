package com.ai.assistance.operit.data.backup

import androidx.datastore.preferences.PreferencesProto.PreferenceMap
import androidx.datastore.preferences.PreferencesProto.Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotBackupManagerTest {

    @Test
    fun snapshotPackageName_acceptsOperitPackagePrefix() {
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit"))
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit.debug"))
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit.clone"))
    }

    @Test
    fun snapshotPackageName_rejectsDifferentPackagePrefix() {
        assertFalse(isSupportedSnapshotPackageName("com.ai.assistance.other"))
        assertFalse(isSupportedSnapshotPackageName("com.example.operit"))
    }

    @Test
    fun themeMediaFileName_matchesGeneratedNamesOnly() {
        assertTrue(
            RawSnapshotBackupManager.isThemeMediaFlatFileName(
                "background_123e4567-e89b-12d3-a456-426614174000.jpg"
            )
        )
        assertTrue(
            RawSnapshotBackupManager.isThemeMediaFlatFileName(
                "avatar_card-id_123e4567-e89b-12d3-a456-426614174000.png"
            )
        )
        assertTrue(
            RawSnapshotBackupManager.isThemeMediaFlatFileName(
                "background_video_123e4567-e89b-12d3-a456-426614174000.mp4"
            )
        )
    }

    @Test
    fun themeMediaFileName_doesNotMatchUserFilesWithSimilarPrefixes() {
        assertFalse(RawSnapshotBackupManager.isThemeMediaFlatFileName("background_notes.txt"))
        assertFalse(RawSnapshotBackupManager.isThemeMediaFlatFileName("avatar_backup.png"))
        assertFalse(RawSnapshotBackupManager.isThemeMediaFlatFileName("background_1234.png"))
    }

    @Test
    fun xmlPreferenceText_rewritesEscapedUriSubstring() {
        val source = "content://example/resource?name=one&mode=full"
        val destination = "file:///data/user/0/com.ai.assistance.operit/files/resource?name=two&mode=full"
        val sourceInXml = source.replace("&", "&amp;")
        val destinationInXml = destination.replace("&", "&amp;")
        val original = "<string name=\"resource\">prefix $sourceInXml suffix</string>"

        val rewritten = RawSnapshotBackupManager.rewriteXmlPreferenceText(
            original,
            mapOf(source to destination),
        )

        assertEquals(
            "<string name=\"resource\">prefix $destinationInXml suffix</string>",
            rewritten,
        )
    }

    @Test
    fun preferenceProto_rewritesUriSubstringInsideStringValue() {
        val source = "file:///old/path/avatar.png"
        val destination = "file:///new-install/longer/path/avatar.png"
        val original = PreferenceMap.newBuilder()
            .putPreferences(
                "json",
                Value.newBuilder()
                    .setString("{\"avatar\":\"$source\"}").build(),
            ).build()

        val rewritten = RawSnapshotMediaRestore.rewritePreferences(
            original.toByteArray(), mapOf(source to destination)
        )
        val restored = PreferenceMap.parseFrom(rewritten)

        assertEquals("{\"avatar\":\"$destination\"}", restored.preferencesMap.getValue("json").string)
    }

    @Test
    fun resourceFileName_collisionIndexProducesDistinctPath() {
        val reference = RawSnapshotResourceReference(
            ownerType = RawSnapshotResourceOwnerType.CHARACTER_CARD,
            kind = RawSnapshotResourceKind.BACKGROUND,
            uri = "file:///data/background.png",
            ownerId = "same-id",
            ownerName = "Same Name",
        )

        val first = RawSnapshotResourceLayout.fileName(reference, "png")
        val second = RawSnapshotResourceLayout.fileName(reference, "png", collisionIndex = 2)

        assertNotEquals(first, second)
    }
}
