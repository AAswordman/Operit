package com.ai.assistance.operit.data.preferences

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPermissionModeMigrationTest {
    private val migration = AndroidPermissionModeMigration()
    private val modeKey = stringPreferencesKey("preferred_permission_level")
    private val suKey = stringPreferencesKey("custom_su_command")

    @Test fun migratesBothLegacyModesAndKeepsOtherPreferences() = runBlocking {
        for (old in listOf("ACCESSIBILITY", "DEBUGGER", "accessibility")) {
            val preferences = emptyPreferences().toMutablePreferences().apply {
                this[modeKey] = old
                this[suKey] = "custom-su"
            }
            assertTrue(migration.shouldMigrate(preferences))
            val migrated = migration.migrate(preferences)
            assertEquals("ADMIN", migrated[modeKey])
            assertEquals("custom-su", migrated[suKey])
            assertEquals(old, preferences[modeKey])
            assertFalse(migration.shouldMigrate(migrated))
        }
    }

    @Test fun newModesAndUnsetPreferenceDoNotMigrate() = runBlocking {
        assertFalse(migration.shouldMigrate(emptyPreferences()))
        for (mode in listOf("STANDARD", "ADMIN", "ROOT")) {
            val preferences = emptyPreferences().toMutablePreferences().apply { this[modeKey] = mode }
            assertFalse(migration.shouldMigrate(preferences))
        }
    }
}
