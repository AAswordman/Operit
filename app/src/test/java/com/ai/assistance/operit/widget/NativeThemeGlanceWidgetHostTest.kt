package com.ai.assistance.operit.widget

import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeThemeGlanceWidgetHostTest {
    @Test
    fun refreshAllUpdatesBothWidgetTypes() = runTest {
        val updates = mutableListOf<String>()

        NativeThemeGlanceWidgetHost.refreshAll(
            refreshVoiceAssistant = { updates += "voice" },
            refreshToolPkg = { updates += "toolpkg" },
        )

        assertEquals(listOf("voice", "toolpkg"), updates)
    }

    @Test
    fun everyActiveThemeEmissionRefreshesInstalledWidgets() = runTest {
        val updates = mutableListOf<String>()
        val snapshot =
            ThemePreferenceSnapshot(
                source = "character_card",
                sourceId = "glance-refresh-test",
                values = ThemePreferenceValues.defaultVisual(),
            )

        NativeThemeGlanceWidgetHost.refreshForThemeChanges(
            themeSnapshots = flowOf(snapshot, snapshot.copy(sourceId = "glance-refresh-next")),
            onThemeChanged = { updates += "refresh" },
        )

        assertEquals(listOf("refresh", "refresh"), updates)
    }
}
