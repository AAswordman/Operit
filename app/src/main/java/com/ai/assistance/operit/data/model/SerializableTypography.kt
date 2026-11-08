package com.ai.assistance.operit.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerializableTypography(
    val displayLarge: SerializableTextStyle,
    val displayMedium: SerializableTextStyle,
    val displaySmall: SerializableTextStyle,
    val headlineLarge: SerializableTextStyle,
    val headlineMedium: SerializableTextStyle,
    val headlineSmall: SerializableTextStyle,
    val titleLarge: SerializableTextStyle,
    val titleMedium: SerializableTextStyle,
    val titleSmall: SerializableTextStyle,
    val bodyLarge: SerializableTextStyle,
    val bodyMedium: SerializableTextStyle,
    val bodySmall: SerializableTextStyle,
    val labelLarge: SerializableTextStyle,
    val labelMedium: SerializableTextStyle,
    val labelSmall: SerializableTextStyle
) : Parcelable

@Parcelize
data class SerializableTextStyle(
    val fontSize: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val fontWeight: Int
) : Parcelable
