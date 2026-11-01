package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import org.junit.Assert.*
import org.junit.Test

class ThemeStudioCatalogTest {
    @Test
    fun typographyPaletteAndIconsDoNotOfferLayersOrStates() {
        val targets = ThemeStudioTargets.filter { it.kind in setOf(StudioKind.Typography, StudioKind.Palette, StudioKind.Icon) }
        assertEquals(15, targets.size)
        targets.forEach { assertEquals(it.id, listOf(StudioProperty.Appearance), it.properties) }
    }

    @Test
    fun catalogCoversAllSurfacesAndOnlyInteractiveTargetsOfferStateEditing() {
        assertEquals(ThemeScriptContract.surfaceIds, ThemeStudioTargets.filter { it.kind == StudioKind.Surface }.map { it.id }.toSet())
        assertEquals(ThemeScriptContract.iconIds, ThemeStudioTargets.filter { it.kind == StudioKind.Icon }.map { it.id }.toSet())
        ThemeStudioTargets.filter { it.kind == StudioKind.Surface }.forEach { assertTrue(it.id, StudioProperty.Layers in it.properties) }
        assertEquals(setOf("button", "iconButton", "navigationItem"), ThemeStudioTargets.filter { StudioProperty.States in it.properties }.map { it.id }.toSet())
        assertEquals(listOf("pressed", "focused", "disabled"), ThemeStudioTargets.first { it.id == "button" }.states)
        assertEquals(listOf("selected"), ThemeStudioTargets.first { it.id == "navigationItem" }.states)
    }
}