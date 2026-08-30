package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Typographyimport androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.net.toFile
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import java.io.File

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 根据系统字体名称获取 FontFamily
 */
fun getSystemFontFamily(systemFontName: String): FontFamily {
    return when (systemFontName) {
        UserPreferencesManager.SYSTEM_FONT_SERIF -> FontFamily.Serif
        UserPreferencesManager.SYSTEM_FONT_SANS_SERIF -> FontFamily.SansSerif
        UserPreferencesManager.SYSTEM_FONT_MONOSPACE -> FontFamily.Monospace
        UserPreferencesManager.SYSTEM_FONT_CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}

/**
 * 从文件路径加载自定义字体
 */
fun loadCustomFontFamily(context: Context, fontPath: String): FontFamily? {
    return try {
        // - 修复了 file:// URI 路径无法被 File 正确解析的问题
        val file = if (fontPath.startsWith("file://")) {
            Uri.parse(fontPath).toFile()
        } else {
            File(fontPath)
        }

        if (!file.exists()) {
            AppLogger.e("TypeKt", "Font file does not exist: $fontPath")
            return null
        }

        FontFamily(
            Font(file)
        )
    } catch (e: Exception) {
        AppLogger.e("TypeKt", "Error loading custom font from $fontPath", e)
        null
    }
}

/**
 * 根据全局字体缩放创建 Typography
 */
fun createCustomTypography(
    fontScale: Float
): Typography {
    if (fontScale == 1.0f) {
        return Typography
    }

    // Helper to apply scale. It will be applied to every style.
    fun TextStyle.withScale(): TextStyle = copy(
        fontSize = fontSize * fontScale,
        lineHeight = lineHeight * fontScale
    )

    return Typography(
        displayLarge = Typography.displayLarge.withScale(),
        displayMedium = Typography.displayMedium.withScale(),
        displaySmall = Typography.displaySmall.withScale(),
        headlineLarge = Typography.headlineLarge.withScale(),
        headlineMedium = Typography.headlineMedium.withScale(),
        headlineSmall = Typography.headlineSmall.withScale(),
        titleLarge = Typography.titleLarge.withScale(),
        titleMedium = Typography.titleMedium.withScale(),
        titleSmall = Typography.titleSmall.withScale(),
        bodyLarge = Typography.bodyLarge.withScale(),
        bodyMedium = Typography.bodyMedium.withScale(),
        bodySmall = Typography.bodySmall.withScale(),
        labelLarge = Typography.labelLarge.withScale(),
        labelMedium = Typography.labelMedium.withScale(),
        labelSmall = Typography.labelSmall.withScale()
    )
}
