package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2

/** Draws one package-owned component skin while leaving interaction and semantics to the caller. */
@Composable
internal fun ThemeComponentSurfaceV2(
    component: ThemeComponentIdV2,
    state: ThemeComponentStateV2 = ThemeComponentStateV2.NORMAL,
    modifier: Modifier = Modifier,
    applyContentPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    ThemeComponentSurfaceV2(
        skin = LocalThemePackageUiRuntimeV2.current.componentSkin(component, state),
        modifier = modifier,
        applyContentPadding = applyContentPadding,
        content = content,
    )
}

@Composable
internal fun ThemeComponentSurfaceV2(
    skin: ResolvedThemeComponentSkinV2,
    modifier: Modifier = Modifier,
    applyContentPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = remember(skin.frame) { skin.frame.toComposeShape() }
    val contentModifier =
        if (applyContentPadding) {
            Modifier.padding(
                start = skin.paddingStartDp.dp,
                top = skin.paddingTopDp.dp,
                end = skin.paddingEndDp.dp,
                bottom = skin.paddingBottomDp.dp,
            )
        } else {
            Modifier
        }

    Surface(
        modifier =
            modifier.drawWithCache {
                val framePlan = skin.frame.createRenderPlan(size, this)
                onDrawWithContent {
                    drawContent()
                    drawThemeComponentFrame(framePlan)
                }
            },
        shape = shape,
        color = skin.container,
        contentColor = skin.content,
        tonalElevation = 0.dp,
        shadowElevation = skin.elevationDp.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides skin.content) {
            Box(modifier = contentModifier) {
                content()
            }
        }
    }
}
