package com.ai.assistance.operit.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayBehaviorResetTest {
    @Test
    fun resetPreservesIdentityAndUnrelatedPreferences() {
        val avatar = stringPreferencesKey("global_user_avatar_uri")
        val name = stringPreferencesKey("global_user_name")
        val unrelated = stringPreferencesKey("unrelated_setting")
        val preferences =
            mutablePreferencesOf(
                avatar to "content://avatars/custom-avatar",
                name to "已设置的用户名",
                unrelated to "原值",
                booleanPreferencesKey("enable_reply_notification") to false,
                booleanPreferencesKey("enable_enter_to_send") to true,
                booleanPreferencesKey("enable_background_keep_alive") to true,
                intPreferencesKey("screenshot_quality") to 99,
                stringPreferencesKey("orientation_reload_policy") to "NEVER",
            )

        DisplayPreferencesManager.resetDisplaySettingsValues(preferences)

        assertEquals("content://avatars/custom-avatar", preferences[avatar])
        assertEquals("已设置的用户名", preferences[name])
        assertEquals("原值", preferences[unrelated])
        assertTrue(preferences[booleanPreferencesKey("enable_reply_notification")]!!)
        assertFalse(preferences[booleanPreferencesKey("enable_enter_to_send")]!!)
        assertFalse(preferences[booleanPreferencesKey("enable_background_keep_alive")]!!)
        assertNull(preferences[intPreferencesKey("screenshot_quality")])
        assertNull(preferences[stringPreferencesKey("orientation_reload_policy")])
    }
}
