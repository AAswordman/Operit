package com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CompactDialogMetrics(
    val isCompactWidth: Boolean,
    val isCompactHeight: Boolean,
    val maxHeight: Dp
) {
    val isCompact: Boolean
        get() = isCompactWidth || isCompactHeight
}

@Composable
fun rememberCompactDialogMetrics(
    compactWidthThreshold: Int = 320,
    compactHeightThreshold: Int = 560,
    maxHeightRatio: Float = 0.9f
): CompactDialogMetrics {
    val configuration = LocalConfiguration.current
    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        compactWidthThreshold,
        compactHeightThreshold,
        maxHeightRatio
    ) {
        CompactDialogMetrics(
            isCompactWidth = configuration.screenWidthDp < compactWidthThreshold,
            isCompactHeight = configuration.screenHeightDp < compactHeightThreshold,
            maxHeight = configuration.screenHeightDp.dp * maxHeightRatio
        )
    }
}

fun Modifier.compactDialogHeight(
    metrics: CompactDialogMetrics,
    defaultMaxHeight: Dp? = null
): Modifier {
    return when {
        metrics.isCompact -> heightIn(max = metrics.maxHeight)
        defaultMaxHeight != null -> heightIn(max = defaultMaxHeight)
        else -> this
    }
}

fun Modifier.compactDialogHeightWhenShort(
    metrics: CompactDialogMetrics,
    defaultMaxHeight: Dp? = null
): Modifier {
    return when {
        metrics.isCompactHeight -> heightIn(max = metrics.maxHeight)
        defaultMaxHeight != null -> heightIn(max = defaultMaxHeight)
        else -> this
    }
}

fun Modifier.compactDialogHeightOrWrapContent(metrics: CompactDialogMetrics): Modifier {
    return if (metrics.isCompactHeight) heightIn(max = metrics.maxHeight) else wrapContentHeight()
}

fun Modifier.verticalScrollWhenCompact(
    metrics: CompactDialogMetrics,
    scrollState: ScrollState
): Modifier {
    return if (metrics.isCompact) verticalScroll(scrollState) else this
}

fun Modifier.verticalScrollWhenShort(
    metrics: CompactDialogMetrics,
    scrollState: ScrollState
): Modifier {
    return if (metrics.isCompactHeight) verticalScroll(scrollState) else this
}

@Composable
fun rememberCompactDialogScrollState(): ScrollState {
    return rememberScrollState()
}
