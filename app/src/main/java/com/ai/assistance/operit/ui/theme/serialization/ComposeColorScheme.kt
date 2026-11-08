package com.ai.assistance.operit.ui.theme.serialization

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.model.SerializableColorScheme

fun ColorScheme.toSerializable(): SerializableColorScheme {
    return SerializableColorScheme(
        primary = this.primary.value.toLong(),
        onPrimary = this.onPrimary.value.toLong(),
        primaryContainer = this.primaryContainer.value.toLong(),
        onPrimaryContainer = this.onPrimaryContainer.value.toLong(),
        inversePrimary = this.inversePrimary.value.toLong(),
        secondary = this.secondary.value.toLong(),
        onSecondary = this.onSecondary.value.toLong(),
        secondaryContainer = this.secondaryContainer.value.toLong(),
        onSecondaryContainer = this.onSecondaryContainer.value.toLong(),
        tertiary = this.tertiary.value.toLong(),
        onTertiary = this.onTertiary.value.toLong(),
        tertiaryContainer = this.tertiaryContainer.value.toLong(),
        onTertiaryContainer = this.onTertiaryContainer.value.toLong(),
        background = this.background.value.toLong(),
        onBackground = this.onBackground.value.toLong(),
        surface = this.surface.value.toLong(),
        onSurface = this.onSurface.value.toLong(),
        surfaceVariant = this.surfaceVariant.value.toLong(),
        onSurfaceVariant = this.onSurfaceVariant.value.toLong(),
        surfaceTint = this.surfaceTint.value.toLong(),
        inverseSurface = this.inverseSurface.value.toLong(),
        inverseOnSurface = this.inverseOnSurface.value.toLong(),
        error = this.error.value.toLong(),
        onError = this.onError.value.toLong(),
        errorContainer = this.errorContainer.value.toLong(),
        onErrorContainer = this.onErrorContainer.value.toLong(),
        outline = this.outline.value.toLong(),
        outlineVariant = this.outlineVariant.value.toLong(),
        scrim = this.scrim.value.toLong()
    )
}

fun SerializableColorScheme.toComposeColorScheme(): ColorScheme {
    return ColorScheme(
        primary = Color(this.primary.toULong()),
        onPrimary = Color(this.onPrimary.toULong()),
        primaryContainer = Color(this.primaryContainer.toULong()),
        onPrimaryContainer = Color(this.onPrimaryContainer.toULong()),
        inversePrimary = Color(this.inversePrimary.toULong()),
        secondary = Color(this.secondary.toULong()),
        onSecondary = Color(this.onSecondary.toULong()),
        secondaryContainer = Color(this.secondaryContainer.toULong()),
        onSecondaryContainer = Color(this.onSecondaryContainer.toULong()),
        tertiary = Color(this.tertiary.toULong()),
        onTertiary = Color(this.onTertiary.toULong()),
        tertiaryContainer = Color(this.tertiaryContainer.toULong()),
        onTertiaryContainer = Color(this.onTertiaryContainer.toULong()),
        background = Color(this.background.toULong()),
        onBackground = Color(this.onBackground.toULong()),
        surface = Color(this.surface.toULong()),
        onSurface = Color(this.onSurface.toULong()),
        surfaceVariant = Color(this.surfaceVariant.toULong()),
        onSurfaceVariant = Color(this.onSurfaceVariant.toULong()),
        surfaceTint = Color(this.surfaceTint.toULong()),
        inverseSurface = Color(this.inverseSurface.toULong()),
        inverseOnSurface = Color(this.inverseOnSurface.toULong()),
        error = Color(this.error.toULong()),
        onError = Color(this.onError.toULong()),
        errorContainer = Color(this.errorContainer.toULong()),
        onErrorContainer = Color(this.onErrorContainer.toULong()),
        outline = Color(this.outline.toULong()),
        outlineVariant = Color(this.outlineVariant.toULong()),
        scrim = Color(this.scrim.toULong())
    )
}
