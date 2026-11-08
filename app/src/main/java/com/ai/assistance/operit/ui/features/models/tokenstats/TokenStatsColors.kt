package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 信息架构重构后的配色令牌（设计规范 2026-08-18）：
 * - 页面与全部卡片走应用中性 surface 阶梯（surfaceContainer / surfaceContainerHigh），
 *   明暗主题各自取对应层级；用户自选主色（示例稿中的橙色仅为示例）不浸染容器；
 * - 主色只出现在关键数字、图表线条柱形、进度条、选中态与图标上；
 * - 图表、热力图、堆叠分量颜色全部由主色/次色透明度阶推导，无硬编码色值。
 */
data class TokenStatsColors(
    // 页面层（应用标准背景）
    val pageBackground: Color,
    val pageContent: Color,
    val pageSupportingContent: Color,
    val pageAccent: Color,
    // 一级卡片（surfaceContainer，1dp 描边见 cardBorder）
    val cardContainer: Color,
    val cardContent: Color,
    val cardSupportingContent: Color,
    val cardWeakContent: Color,
    val cardBorder: Color,
    val cardAccent: Color,
    // 二级容器：图标底座 / tooltip / 徽章 / 未选中分段
    val innerContainer: Color,
    // 分段选中底（主色低透明度叠于卡片，避免大面积实心主色）
    val selectedSegmentContainer: Color,
    // 图表
    val chartGrid: Color,
    val chartLabel: Color,
    val chartAccent: Color,
    val chartAreaFillBase: Color,
    // 热力图（level 0 = 未激活灰格；1..5 = 主色透明度阶）
    val heatmapInactive: Color,
    val heatmapLevels: List<Color>,
    // 多模型调色板：主色 / 次色透明度交替，保证可区分
    val modelPalette: List<Color>,
    // Token 堆叠分量（未缓存 / 缓存 / 输出）
    val componentColors: List<Color>,
    val unknownHint: Color,
)

/** 全部图表共用一套中性卡片 + 主色强调的样式。 */
internal data class TokenStatsChartStyle(
    val containerColor: Color,
    val contentColor: Color,
    val accentColor: Color,
    val gridColor: Color,
    val labelColor: Color,
    val tooltipContainerColor: Color,
    val tooltipContentColor: Color,
)

@Composable
internal fun tokenStatsChartStyle(): TokenStatsChartStyle {
    val colors = LocalTokenStatsColors.current
    return TokenStatsChartStyle(
        containerColor = colors.cardContainer,
        contentColor = colors.cardContent,
        accentColor = colors.chartAccent,
        gridColor = colors.chartGrid,
        labelColor = colors.chartLabel,
        tooltipContainerColor = colors.innerContainer,
        tooltipContentColor = colors.cardContent,
    )
}

@Composable
fun tokenStatsColors(): TokenStatsColors {
    val scheme = MaterialTheme.colorScheme
    // 用户主题可带 alpha，容器统一归一为不透明色，明暗主题各自取 surface 层级
    val cardContainer = scheme.surfaceContainer.copy(alpha = 1f)
    val innerContainer = scheme.surfaceContainerHigh.copy(alpha = 1f)
    // 主色对比度校验：极端自定义主题下与卡片容器对比不足时改用卡片内容色，
    // 保证关键数字可读（对比度选择规则，保证任意用户主题成立）
    val cardAccent = visibleAccent(scheme.primary, cardContainer, scheme.onSurface)
    val secondaryAccent = visibleAccent(scheme.secondary, cardContainer, scheme.onSurface)
    return TokenStatsColors(
        pageBackground = scheme.background,
        pageContent = scheme.onBackground,
        pageSupportingContent = scheme.onSurfaceVariant,
        pageAccent = visibleAccent(scheme.primary, scheme.background, scheme.onBackground),
        cardContainer = cardContainer,
        cardContent = scheme.onSurface,
        cardSupportingContent = scheme.onSurfaceVariant,
        cardWeakContent = scheme.onSurfaceVariant.copy(alpha = 0.72f),
        cardBorder = scheme.outlineVariant,
        cardAccent = cardAccent,
        innerContainer = innerContainer,
        selectedSegmentContainer = scheme.primary.copy(alpha = 0.30f),
        chartGrid = scheme.outlineVariant,
        chartLabel = scheme.onSurfaceVariant,
        chartAccent = cardAccent,
        chartAreaFillBase = cardAccent.copy(alpha = 0.28f),
        heatmapInactive = scheme.onSurface.copy(alpha = 0.10f),
        heatmapLevels = listOf(
            cardAccent.copy(alpha = 0.16f),
            cardAccent.copy(alpha = 0.34f),
            cardAccent.copy(alpha = 0.54f),
            cardAccent.copy(alpha = 0.76f),
            cardAccent,
        ),
        modelPalette = listOf(
            cardAccent,
            secondaryAccent,
            cardAccent.copy(alpha = 0.62f),
            secondaryAccent.copy(alpha = 0.55f),
            cardAccent.copy(alpha = 0.40f),
            scheme.onSurface.copy(alpha = 0.45f),
        ),
        componentColors = listOf(
            cardAccent.copy(alpha = 0.42f),
            cardAccent.copy(alpha = 0.68f),
            cardAccent,
        ),
        unknownHint = scheme.onSurfaceVariant,
    )
}

private fun visibleAccent(preferred: Color, container: Color, content: Color): Color {
    val opaquePreferred = preferred.copy(alpha = 1f)
    return if (contrastRatio(opaquePreferred, container) >= 3f) opaquePreferred else content
}

private fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

val LocalTokenStatsColors = staticCompositionLocalOf<TokenStatsColors> {
    error("TokenStatsColors not provided")
}

@Composable
fun TokenStatsColorsProvider(content: @Composable () -> Unit) {
    val colors = tokenStatsColors()
    // 保留应用完整 ColorScheme；统计页全部容器走应用中性 surface，
    // 主色只作为强调进入显式组件，主题设置完全由用户掌控。
    CompositionLocalProvider(LocalTokenStatsColors provides colors, content = content)
}
