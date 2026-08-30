package com.ai.assistance.operit.ui.theme.renderer.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentCatalogV1
import com.ai.assistance.operit.ui.theme.renderer.catalog.NativeThemeComponentRendererV1

internal object NativeThemeNavigationDrawerItemRendererV1 :
    NativeThemeComponentRendererV1<
        NativeThemeNavigationDrawerItemStateV1,
        NativeThemeNavigationDrawerItemEventV1,
        NativeThemeNavigationDrawerItemSlotsV1,
    > {
    @Composable
    override fun render(
        state: NativeThemeNavigationDrawerItemStateV1,
        slots: NativeThemeNavigationDrawerItemSlotsV1,
        onEvent: (NativeThemeNavigationDrawerItemEventV1) -> Unit,
        modifier: Modifier,
    ) {
        val appearance = rememberNavigationDrawerAppearance()
        val itemShape = MaterialTheme.shapes.small
        val selectedGlassOverlayColor =
            if (state.selected) {
                appearance.selectedContainerColor.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            }
        val contentColor =
            if (state.selected) appearance.selectedContentColor else appearance.itemColor
        val interactionModifier =
            when (state.semanticRole) {
                NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION ->
                    Modifier.selectable(
                        selected = state.selected,
                        enabled = state.enabled,
                        role = Role.Tab,
                        onClick = {
                            onEvent(NativeThemeNavigationDrawerItemEventV1.Activate)
                        },
                    )
                NativeThemeNavigationDrawerItemSemanticRoleV1.ACTION ->
                    Modifier.clickable(
                        enabled = state.enabled,
                        role = Role.Button,
                        onClick = {
                            onEvent(NativeThemeNavigationDrawerItemEventV1.Activate)
                        },
                    )
            }

        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .alpha(if (state.enabled) 1f else 0.38f)
                        .liquidGlass(
                            enabled = appearance.buttonLiquidGlassEnabled,
                            shape = itemShape,
                            containerColor = appearance.buttonContainerColor,
                            shadowElevation = if (state.selected) 6.dp else 4.dp,
                            borderWidth = 0.5.dp,
                            blurRadius = 12.dp,
                            overlayAlphaBoost = 0.04f,
                            enableLens = false,
                        )
                        .clip(itemShape)
                        .background(selectedGlassOverlayColor),
                color =
                    if (appearance.buttonLiquidGlassEnabled) {
                        Color.Transparent
                    } else if (state.selected) {
                        appearance.selectedContainerColor
                    } else {
                        Color.Transparent
                    },
                shape = itemShape,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        slots.leading(Modifier.size(20.dp).clearAndSetSemantics {})
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight =
                                if (state.selected) FontWeight.Medium else FontWeight.Normal,
                            color = contentColor,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clip(itemShape)
                        .then(interactionModifier)
                        .semantics { contentDescription = state.label },
            )
        }
    }
}

@Composable
internal fun NativeThemeNavigationDrawerItemV1(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    semanticRole: NativeThemeNavigationDrawerItemSemanticRoleV1,
    leading: @Composable (Modifier) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NativeThemeComponentCatalogV1
        .requireImplementation(NativeThemeNavigationDrawerItemContractV1.key)
        .renderer
        .render(
            state =
                NativeThemeNavigationDrawerItemStateV1(
                    label = label,
                    selected = selected,
                    enabled = enabled,
                    semanticRole = semanticRole,
                ),
            slots = NativeThemeNavigationDrawerItemSlotsV1(leading = leading),
            onEvent = { event ->
                dispatchNativeThemeNavigationDrawerItemEventV1(event, enabled, onActivate)
            },
            modifier = modifier,
        )
}

internal fun dispatchNativeThemeNavigationDrawerItemEventV1(
    event: NativeThemeNavigationDrawerItemEventV1,
    enabled: Boolean,
    onActivate: () -> Unit,
) {
    when (event) {
        NativeThemeNavigationDrawerItemEventV1.Activate -> if (enabled) onActivate()
    }
}
