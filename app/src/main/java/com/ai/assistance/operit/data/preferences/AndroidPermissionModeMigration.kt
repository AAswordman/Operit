package com.ai.assistance.operit.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import java.util.Locale

/** 只更新旧模式名称，保留 Root 设置以及其他偏好。 */
internal class AndroidPermissionModeMigration : DataMigration<Preferences> {
    private val modeKey = stringPreferencesKey("preferred_permission_level")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[modeKey]?.uppercase(Locale.ROOT) in setOf("ACCESSIBILITY", "DEBUGGER")

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            this[modeKey] = AndroidPermissionLevel.ADMIN.name
        }

    override suspend fun cleanUp() = Unit
}
