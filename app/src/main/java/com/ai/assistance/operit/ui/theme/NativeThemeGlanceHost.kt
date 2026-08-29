package com.ai.assistance.operit.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import java.lang.reflect.Field
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class NativeThemeGlanceColor(
    val day: Color,
    val night: Color,
) {
    fun toColorProvider(): ColorProvider = DayNightColorProvider(day = day, night = night)

    fun withAlpha(alpha: Float): NativeThemeGlanceColor =
        NativeThemeGlanceColor(
            day = day.copy(alpha = alpha),
            night = night.copy(alpha = alpha),
        )
}

internal data class NativeThemeGlancePaletteV1(
    val dayTheme: ResolvedNativeThemeV1,
    val nightTheme: ResolvedNativeThemeV1,
    val primary: NativeThemeGlanceColor,
    val onPrimary: NativeThemeGlanceColor,
    val primaryContainer: NativeThemeGlanceColor,
    val onPrimaryContainer: NativeThemeGlanceColor,
    val secondary: NativeThemeGlanceColor,
    val onSecondary: NativeThemeGlanceColor,
    val secondaryContainer: NativeThemeGlanceColor,
    val onSecondaryContainer: NativeThemeGlanceColor,
    val tertiary: NativeThemeGlanceColor,
    val onTertiary: NativeThemeGlanceColor,
    val error: NativeThemeGlanceColor,
    val onError: NativeThemeGlanceColor,
    val background: NativeThemeGlanceColor,
    val onBackground: NativeThemeGlanceColor,
    val surface: NativeThemeGlanceColor,
    val onSurface: NativeThemeGlanceColor,
    val surfaceVariant: NativeThemeGlanceColor,
    val onSurfaceVariant: NativeThemeGlanceColor,
    private val colorsByToken: Map<String, NativeThemeGlanceColor>,
) {
    fun colorForToken(token: String): NativeThemeGlanceColor? =
        colorsByToken[normalizeNativeThemeGlanceColorToken(token)]
}

internal object NativeThemeGlanceDynamicColorTracker {
    private val mutableRevision = MutableStateFlow(0)

    val revision: StateFlow<Int> = mutableRevision.asStateFlow()

    fun markChanged() {
        mutableRevision.update { current -> current + 1 }
    }
}

@Composable
internal fun NativeThemeGlanceHost(
    context: Context,
    initialSnapshot: ThemePreferenceSnapshot,
    content: @Composable (NativeThemeGlancePaletteV1) -> Unit,
) {
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    NativeThemeGlanceContentHost(
        context = context,
        initialSnapshot = initialSnapshot,
        themeSnapshots = activePromptManager.activeThemePreferenceSnapshotFlow,
        dynamicColorRevisions = NativeThemeGlanceDynamicColorTracker.revision,
        content = content,
    )
}

@Composable
internal fun NativeThemeGlanceContentHost(
    context: Context,
    initialSnapshot: ThemePreferenceSnapshot,
    themeSnapshots: Flow<ThemePreferenceSnapshot>,
    dynamicColorRevisions: StateFlow<Int>,
    content: @Composable (NativeThemeGlancePaletteV1) -> Unit,
) {
    val snapshot by themeSnapshots.collectAsState(initial = initialSnapshot)
    val dynamicColorRevision by dynamicColorRevisions.collectAsState()
    val themePalette =
        remember(context, snapshot, dynamicColorRevision) {
            resolveNativeThemeGlancePalette(context, snapshot)
        }
    content(themePalette)
}

private val nativeThemeGlanceColorSchemeFieldByToken: Map<String, Field> by lazy {
    ColorScheme::class.java.declaredFields
        .asSequence()
        .filter { field ->
            field.type == java.lang.Long.TYPE ||
                field.type == java.lang.Long::class.java ||
                field.type == Color::class.java
        }
        .onEach { field -> field.isAccessible = true }
        .associateBy { field -> normalizeNativeThemeGlanceColorToken(field.name) }
}

private fun normalizeNativeThemeGlanceColorToken(raw: String): String =
    raw.lowercase(Locale.ROOT)
        .replace("-", "")
        .replace("_", "")
        .trim()

private fun readNativeThemeGlanceColor(
    field: Field,
    colorScheme: ColorScheme,
): Color? =
    when (field.type) {
        java.lang.Long.TYPE -> Color(field.getLong(colorScheme).toULong())
        java.lang.Long::class.java -> Color((field.get(colorScheme) as Long).toULong())
        else -> field.get(colorScheme) as? Color
    }

private fun resolveNativeThemeGlanceColorPairs(
    dayColorScheme: ColorScheme,
    nightColorScheme: ColorScheme,
): Map<String, NativeThemeGlanceColor> =
    nativeThemeGlanceColorSchemeFieldByToken.mapNotNull { (token, field) ->
        val day = readNativeThemeGlanceColor(field, dayColorScheme) ?: return@mapNotNull null
        val night = readNativeThemeGlanceColor(field, nightColorScheme) ?: return@mapNotNull null
        token to NativeThemeGlanceColor(day = day, night = night)
    }.toMap()

internal fun resolveNativeThemeGlancePalette(
    context: Context,
    snapshot: ThemePreferenceSnapshot,
): NativeThemeGlancePaletteV1 {
    val (lightColorScheme, darkColorScheme) = resolveNativeThemeDetachedBaseColorSchemes(context)
    return resolveNativeThemeGlancePalette(
        snapshot = snapshot,
        lightColorScheme = lightColorScheme,
        darkColorScheme = darkColorScheme,
    )
}

internal fun resolveNativeThemeGlancePalette(
    snapshot: ThemePreferenceSnapshot,
    lightColorScheme: ColorScheme,
    darkColorScheme: ColorScheme,
): NativeThemeGlancePaletteV1 {
    val dayTheme =
        resolveNativeThemeForDetachedComposeHost(
            snapshot = snapshot,
            hostSurface = NativeThemeHostSurface.GLANCE,
            systemDarkTheme = false,
            lightColorScheme = lightColorScheme,
            darkColorScheme = darkColorScheme,
        )
    val nightTheme =
        resolveNativeThemeForDetachedComposeHost(
            snapshot = snapshot,
            hostSurface = NativeThemeHostSurface.GLANCE,
            systemDarkTheme = true,
            lightColorScheme = lightColorScheme,
            darkColorScheme = darkColorScheme,
        )

    fun colorPair(selector: (ColorScheme) -> Color): NativeThemeGlanceColor =
        NativeThemeGlanceColor(
            day = selector(dayTheme.colorScheme),
            night = selector(nightTheme.colorScheme),
        )

    return NativeThemeGlancePaletteV1(
        dayTheme = dayTheme,
        nightTheme = nightTheme,
        primary = colorPair(ColorScheme::primary),
        onPrimary = colorPair(ColorScheme::onPrimary),
        primaryContainer = colorPair(ColorScheme::primaryContainer),
        onPrimaryContainer = colorPair(ColorScheme::onPrimaryContainer),
        secondary = colorPair(ColorScheme::secondary),
        onSecondary = colorPair(ColorScheme::onSecondary),
        secondaryContainer = colorPair(ColorScheme::secondaryContainer),
        onSecondaryContainer = colorPair(ColorScheme::onSecondaryContainer),
        tertiary = colorPair(ColorScheme::tertiary),
        onTertiary = colorPair(ColorScheme::onTertiary),
        error = colorPair(ColorScheme::error),
        onError = colorPair(ColorScheme::onError),
        background = colorPair(ColorScheme::background),
        onBackground = colorPair(ColorScheme::onBackground),
        surface = colorPair(ColorScheme::surface),
        onSurface = colorPair(ColorScheme::onSurface),
        surfaceVariant = colorPair(ColorScheme::surfaceVariant),
        onSurfaceVariant = colorPair(ColorScheme::onSurfaceVariant),
        colorsByToken =
            resolveNativeThemeGlanceColorPairs(
                dayColorScheme = dayTheme.colorScheme,
                nightColorScheme = nightTheme.colorScheme,
            ),
    )
}
