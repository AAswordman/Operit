package com.ai.assistance.operit.data.backup

import org.junit.Assert.assertFalse
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

}
