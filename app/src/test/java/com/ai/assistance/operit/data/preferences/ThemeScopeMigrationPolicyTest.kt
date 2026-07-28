package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeScopeMigrationPolicyTest {
    @Test
    fun activeDefaultWithoutScopedDataMigratesLegacyTheme() {
        val result = ThemeScopeMigrationPolicy.shouldCopyLegacyThemeToDefaultCharacter(
            migrationCompleted = false,
            activeCharacterCardId = "default_character",
            defaultCharacterId = "default_character",
            hasDefaultCharacterTheme = false,
            hasAnyScopedTheme = false,
            defaultCharacterWasCreated = false,
        )

        assertTrue(result)
    }

    @Test
    fun existingScopedDataPreventsLegacyThemeMigration() {
        val result = ThemeScopeMigrationPolicy.shouldCopyLegacyThemeToDefaultCharacter(
            migrationCompleted = false,
            activeCharacterCardId = "other-character",
            defaultCharacterId = "default_character",
            hasDefaultCharacterTheme = false,
            hasAnyScopedTheme = true,
            defaultCharacterWasCreated = false,
        )

        assertFalse(result)
    }

    @Test
    fun newlyCreatedDefaultCardMigratesExistingGlobalTheme() {
        val result = ThemeScopeMigrationPolicy.shouldCopyLegacyThemeToDefaultCharacter(
            migrationCompleted = false,
            activeCharacterCardId = null,
            defaultCharacterId = "default_character",
            hasDefaultCharacterTheme = false,
            hasAnyScopedTheme = false,
            defaultCharacterWasCreated = true,
        )

        assertTrue(result)
    }

    @Test
    fun unknownActiveCardDoesNotAssignLegacyThemeToDefault() {
        val result = ThemeScopeMigrationPolicy.shouldCopyLegacyThemeToDefaultCharacter(
            migrationCompleted = false,
            activeCharacterCardId = null,
            defaultCharacterId = "default_character",
            hasDefaultCharacterTheme = false,
            hasAnyScopedTheme = false,
            defaultCharacterWasCreated = false,
        )

        assertFalse(result)
    }

    @Test
    fun completedMigrationDoesNotRunAgain() {
        val result = ThemeScopeMigrationPolicy.shouldCopyLegacyThemeToDefaultCharacter(
            migrationCompleted = true,
            activeCharacterCardId = "default_character",
            defaultCharacterId = "default_character",
            hasDefaultCharacterTheme = false,
            hasAnyScopedTheme = false,
            defaultCharacterWasCreated = false,
        )

        assertFalse(result)
    }
}
