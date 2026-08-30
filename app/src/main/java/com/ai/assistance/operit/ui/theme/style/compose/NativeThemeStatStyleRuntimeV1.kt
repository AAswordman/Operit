package com.ai.assistance.operit.ui.theme.style.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ThemeStyleInstancePreferences
import com.ai.assistance.operit.data.preferences.ThemeStyleInstanceRecordV1
import com.ai.assistance.operit.data.preferences.themeStyleInstanceKey
import com.ai.assistance.operit.ui.theme.LocalResolvedNativeThemeV1
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.toThemeTarget
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLinkResultV1

internal val LocalNativeThemeStatStylePlanV1 =
    compositionLocalOf<NativeThemeStatStylePlanV1?> { null }

@Composable
internal fun rememberNativeThemeStatStylePlanV1(): NativeThemeStatStylePlanV1 {
    val context = LocalContext.current
    val snapshot = LocalThemePreferenceSnapshot.current
    val resolvedTheme = LocalResolvedNativeThemeV1.current
    val hostSurface = resolvedTheme.environment.hostSurface
    val target =
        when (hostSurface) {
            NativeThemeHostSurface.MAIN -> snapshot.toThemeTarget()
            NativeThemeHostSurface.EDITOR_PREVIEW -> null
            else -> error("Stat styles are not available on ${hostSurface.name}.")
        }
    val instancePreferences = remember(context) { ThemeStyleInstancePreferences.getInstance(context) }
    val records by instancePreferences.recordsFlow.collectAsState(initial = emptyMap())
    val instanceRecord =
        when (target) {
            is ActivePrompt.CharacterCard,
            is ActivePrompt.CharacterGroup -> records[target.themeStyleInstanceKey()] ?: ThemeStyleInstanceRecordV1.empty()

            null -> ThemeStyleInstanceRecordV1.empty()
        }
    val linkResult =
        remember(resolvedTheme, instanceRecord) {
            NativeThemeNativeV1StyleCompilerV1.linkStat(
                resolvedTheme = resolvedTheme,
                instanceLayer = instanceRecord.instanceLayer,
            )
        }

    val linked =
        when (linkResult) {
            is NativeThemeStyleLinkResultV1.Linked -> linkResult
            is NativeThemeStyleLinkResultV1.Rejected ->
                error(
                    "The native_v1 Stat style instance cannot be linked: " +
                        linkResult.issues.joinToString { issue -> issue.code.name },
                )
        }
    val generatedPlan = NativeThemeNativeV1StyleCompilerV1.resolveStat(
        linked = linked,
        surface = hostSurface,
        darkTheme = resolvedTheme.darkTheme,
    )
    return LocalNativeThemeStatStylePlanV1.current ?: generatedPlan
}
