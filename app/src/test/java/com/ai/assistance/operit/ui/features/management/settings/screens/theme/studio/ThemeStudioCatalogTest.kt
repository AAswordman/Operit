package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import org.junit.Assert.*
import org.junit.Test

class ThemeStudioCatalogTest {
    @Test
    fun typographyPaletteAndIconsDoNotOfferLayersOrStates() {
        val targets = ThemeStudioTargets.filter { it.kind in setOf(StudioKind.Typography, StudioKind.Palette, StudioKind.Icon) }
        assertEquals(3, targets.size)
        targets.forEach { assertEquals(it.id, listOf(StudioProperty.Appearance), it.properties) }
    }

    @Test
    fun onlyChatPopupMenusOfferPositionEditing() {
        val surfaces = ThemeStudioTargets.filter { it.kind == StudioKind.Surface }
        assertEquals(ThemeScriptContract.popupSurfaceIds, surfaces.filter { it.supports(StudioSurfaceControl.PopupOffset) }.map { it.id }.toSet())
        surfaces.filter { it.id in ThemeScriptContract.popupSurfaceIds }.forEach {
            assertTrue(it.supports(StudioSurfaceControl.Shape))
            assertTrue(it.supports(StudioSurfaceControl.Stroke))
            assertTrue(it.supports(StudioSurfaceControl.Padding))
            assertEquals(StudioScene.Components, it.scene)
        }
    }

    @Test
    fun catalogCoversAllSurfacesAndOnlyInteractiveTargetsOfferStateEditing() {
        assertEquals(ThemeScriptContract.surfaceIds, ThemeStudioTargets.filter { it.kind == StudioKind.Surface }.map { it.id }.toSet())
        assertEquals(ThemeScriptContract.iconIds - ThemeScriptContract.actionButtonIds, ThemeStudioTargets.filter { it.kind == StudioKind.Icon }.map { it.id }.toSet())
        ThemeStudioTargets.filter { it.kind == StudioKind.Surface }.forEach { assertTrue(it.id, StudioProperty.Layers in it.properties) }
        assertEquals(setOf("button", "iconButton", "navigationItem") + ThemeScriptContract.actionButtonIds, ThemeStudioTargets.filter { StudioProperty.States in it.properties }.map { it.id }.toSet())
        assertEquals(listOf("pressed", "focused", "disabled"), ThemeStudioTargets.first { it.id == "button" }.states)
        assertEquals(listOf("selected"), ThemeStudioTargets.first { it.id == "navigationItem" }.states)
    }
    @Test
    fun actionButtonsExposeCompleteContainerControlsAndIndependentStates() {
        assertEquals(ThemeScriptContract.actionButtonIds, ThemeStudioButtonIcons.map { it.id }.toSet())
        ThemeStudioButtonIcons.forEach { target ->
            assertEquals(StudioKind.Surface, target.kind)
            assertEquals(listOf(StudioProperty.Appearance, StudioProperty.Layers, StudioProperty.States), target.properties)
            assertEquals(listOf("pressed", "focused", "selected", "disabled"), target.states)
            listOf(StudioSurfaceControl.ContentColor, StudioSurfaceControl.Shape, StudioSurfaceControl.Stroke, StudioSurfaceControl.Padding).forEach { assertTrue(target.supports(it)) }
        }
        assertEquals(ThemeStudioTargets.size, ThemeStudioTargets.map { it.id }.toSet().size)
    }

}
