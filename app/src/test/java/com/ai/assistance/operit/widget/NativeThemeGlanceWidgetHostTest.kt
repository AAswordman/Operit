package com.ai.assistance.operit.widget

import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
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
    fun everyPresentationEmissionRefreshesInstalledWidgets() = runTest {
        val updates = mutableListOf<String>()
        val presentation = GlobalPresentationSnapshot(themeMode = GlobalThemeMode.SYSTEM)

        NativeThemeGlanceWidgetHost.refreshForThemeChanges(
            themeSnapshots =
                flowOf(presentation, presentation.copy(themeMode = GlobalThemeMode.DARK)),
            onThemeChanged = { updates += "refresh" },
        )

        assertEquals(listOf("refresh", "refresh"), updates)
    }
}
