package com.ai.assistance.operit.data.theme.script

import org.junit.Assert.*
import org.junit.Test

class ThemeActionButtonAppearanceTest {
    private val common = ThemeScriptSurface(fillColor = "#112233", strokeColor = "#445566", strokeWidth = 0.25f, shape = ThemeAppearanceShape("cut", 6f))
    private val send = common.copy(fillColor = "#778899", padding = ThemeScriptInsets(2f, 3f, 4f, 5f),
        layers = listOf(ThemeScriptLayer("rect", color = "#123456")),
        states = mapOf(
            "pressed" to ThemeAppearanceState(strokeWidth = 0.1f),
            "focused" to ThemeAppearanceState(contentColor = "#FFFFFF"),
            "selected" to ThemeAppearanceState(fillColor = "#AABBCC"),
            "disabled" to ThemeAppearanceState(opacity = 0.4f),
        ))

    @Test
    fun allActionIdsAcceptTheirOwnLayersShapeColorsAndStates() {
        val project = ThemeScriptAppearance(1, surfaces = ThemeScriptContract.actionButtonIds.associateWith { send })
        ThemeScriptContract.validate(project, ThemeScriptManifest(1, "assets/main.js"))
        assertEquals(12, project.surfaces.size)
    }

    @Test
    fun undeclaredButtonsInheritTheSharedStyle() {
        val project = ThemeScriptAppearance(1, surfaces = mapOf("iconButton" to common))
        ThemeScriptContract.actionButtonIds.forEach { assertEquals(common, project.actionButtonSurface(it)) }
    }

    @Test
    fun localStyleDoesNotChangeSiblingButtonsOrTheSharedStyle() {
        val project = ThemeScriptAppearance(1, surfaces = mapOf("iconButton" to common, "composer.send" to send))
        assertEquals(send, project.actionButtonSurface("composer.send"))
        assertEquals(common, project.actionButtonSurface("composer.attachment"))
        assertEquals(common, project.surfaces.getValue("iconButton"))
        assertEquals("#AABBCC", project.actionButtonSurface("composer.send")!!.forState("selected").fillColor)
    }

    @Test
    fun explicitEmptyStyleRemainsDifferentFromInheritedStyle() {
        val project = ThemeScriptAppearance(1, surfaces = mapOf("iconButton" to common, "composer.send" to ThemeScriptSurface()))
        assertEquals(ThemeScriptSurface(), project.actionButtonSurface("composer.send"))
        assertEquals(common, project.actionButtonSurface("composer.stop"))
    }

    @Test
    fun modeStylesAndLayersSurviveSerialization() {
        val project = ThemeScriptAppearance(1, surfaces = mapOf("iconButton" to common), modes = mapOf(
            "light" to ThemeAppearanceMode(surfaces = mapOf("composer.send" to send)),
            "dark" to ThemeAppearanceMode(surfaces = mapOf("composer.send" to send.copy(fillColor = "#ABCDEF"))),
        ))
        val encoded = ThemeScriptContract.json.encodeToString(ThemeScriptAppearance.serializer(), project)
        val restored = ThemeScriptContract.json.decodeFromString(ThemeScriptAppearance.serializer(), encoded)
        assertEquals(send, restored.forMode("light").actionButtonSurface("composer.send"))
        assertEquals("#ABCDEF", restored.forMode("dark").actionButtonSurface("composer.send")!!.fillColor)
        val local = restored.forMode("light").actionButtonSurface("composer.send")!!
        assertEquals(0.1f, local.forState("pressed").strokeWidth, 0f)
        assertEquals("#FFFFFF", local.forState("focused").contentColor)
        assertEquals(0.4f, local.forState("disabled").opacity, 0f)
    }

    @Test
    fun restoringOneModeRetainsTheOtherModeAndTheIconAsset() {
        val project = ThemeScriptAppearance(1,
            surfaces = mapOf("iconButton" to common, "composer.send" to send),
            icons = mapOf("composer.send" to ThemeAppearanceIcon("arrow")),
            modes = mapOf("dark" to ThemeAppearanceMode(surfaces = mapOf("composer.send" to send.copy(fillColor = "#ABCDEF")))),
        )
        val reset = project.clearActionButtonSurface("composer.send", "light")
        assertEquals(common, reset.forMode("light").actionButtonSurface("composer.send"))
        assertEquals("#ABCDEF", reset.forMode("dark").actionButtonSurface("composer.send")!!.fillColor)
        assertEquals(project.icons, reset.icons)
    }
    @Test
    fun restoringInheritanceDoesNotSnapshotUnrelatedSharedSurfaces() {
        val project = ThemeScriptAppearance(1, surfaces = mapOf(
            "iconButton" to common, "composer.send" to send, "composer.attachment" to common,
        ))
        val reset = project.clearActionButtonSurface("composer.send", "light")
        assertEquals(emptyMap<String, ThemeScriptSurface>(), reset.modes.getValue("light").surfaces)
        assertEquals(mapOf("composer.send" to send), reset.modes.getValue("dark").surfaces)
        assertEquals(common, reset.surfaces.getValue("composer.attachment"))
    }

}
