package com.ai.assistance.operit.data.preferences

internal object ThemeScopeMigrationPolicy {
    fun shouldCopyLegacyThemeToDefaultCharacter(
        migrationCompleted: Boolean,
        activeCharacterCardId: String?,
        defaultCharacterId: String,
        hasDefaultCharacterTheme: Boolean,
        hasAnyScopedTheme: Boolean,
        defaultCharacterWasCreated: Boolean,
    ): Boolean {
        if (migrationCompleted || hasDefaultCharacterTheme || hasAnyScopedTheme) {
            return false
        }

        return defaultCharacterWasCreated || activeCharacterCardId == defaultCharacterId
    }
}
