package com.ai.assistance.operit.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

internal object NativeThemeGlanceWidgetHost {
    suspend fun refreshAll(context: Context) {
        refreshAll(
            refreshVoiceAssistant = { VoiceAssistantGlanceWidget().updateAll(context) },
            refreshToolPkg = { ToolPkgDesktopGlanceWidget().updateAll(context) },
        )
    }

    internal suspend fun refreshAll(
        refreshVoiceAssistant: suspend () -> Unit,
        refreshToolPkg: suspend () -> Unit,
    ) {
        refreshVoiceAssistant()
        refreshToolPkg()
    }

    internal suspend fun refreshForThemeChanges(
        themeSnapshots: Flow<GlobalPresentationSnapshot>,
        onThemeChanged: suspend () -> Unit,
    ) {
        themeSnapshots.collect { onThemeChanged() }
    }
}
