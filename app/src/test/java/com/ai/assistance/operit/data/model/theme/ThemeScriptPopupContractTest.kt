package com.ai.assistance.operit.data.model.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import com.ai.assistance.operit.data.theme.script.ThemeAppearanceMode
import com.ai.assistance.operit.data.theme.script.ThemeAppearanceShape
import com.ai.assistance.operit.data.theme.script.ThemeScriptAppearance
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import com.ai.assistance.operit.data.theme.script.ThemeScriptManifest
import com.ai.assistance.operit.data.theme.script.ThemeScriptPopupOffset
import com.ai.assistance.operit.data.theme.script.ThemeScriptSurface
import com.ai.assistance.operit.data.theme.script.forMode

class ThemeScriptPopupContractTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js")

    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("应拒绝无效菜单偏移")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun existingThemesKeepZeroPopupOffsets() {
        val result = ThemeScriptContract.decodeResult("""{"apiVersion":1,"surfaces":{"menu":{"strokeWidth":2}}}""", manifest)
        assertEquals(ThemeScriptPopupOffset(), result.surfaces.getValue("menu").popupOffset)
        val exported = ThemeScriptContract.json.encodeToString(ThemeScriptAppearance.serializer(), result)
        assertFalse(exported.contains("popupOffset"))
        assertEquals(result, ThemeScriptContract.decodeResult(exported, manifest))
    }

    @Test
    fun allThreeMenusRoundTripWithShapesBordersOffsetsAndModes() {
        val menus = ThemeScriptContract.popupSurfaceIds.associateWith {
            ThemeScriptSurface(
                shape = ThemeAppearanceShape("cut", 12f),
                strokeColor = "#80D1B5FF",
                strokeWidth = 2f,
                popupOffset = ThemeScriptPopupOffset(up = 256f, down = 32f, left = 128f),
            )
        }
        val full = manifest.copy(appearance = ThemeScriptAppearance(
            apiVersion = 1,
            surfaces = menus,
            modes = mapOf("dark" to ThemeAppearanceMode(surfaces = menus.mapValues { (_, surface) ->
                surface.copy(popupOffset = ThemeScriptPopupOffset(down = 64f))
            })),
        ))
        ThemeScriptContract.validate(full)
        val result = ThemeScriptContract.decodeManifest(ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), full))
        assertEquals(full, result)
        assertEquals(ThemeScriptPopupOffset(up = 256f, down = 32f, left = 128f), result.appearance.forMode("light").surfaces.getValue("modelMenu").popupOffset)
        assertEquals(ThemeScriptPopupOffset(down = 64f), result.appearance.forMode("dark").surfaces.getValue("modelMenu").popupOffset)
    }

    @Test
    fun offsetsRejectNonfiniteNegativeAndExcessiveValues() {
        for (id in ThemeScriptContract.popupSurfaceIds) {
            for (value in listOf(-.01f, 256.01f, Float.NaN, Float.POSITIVE_INFINITY)) {
                for (offset in listOf(ThemeScriptPopupOffset(up = value), ThemeScriptPopupOffset(down = value), ThemeScriptPopupOffset(left = value))) {
                    rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf(id to ThemeScriptSurface(popupOffset = offset))), manifest) }
                }
            }
        }
    }

    @Test
    fun otherSurfacesAndRightOffsetsAreRejected() {
        for (id in ThemeScriptContract.surfaceIds - ThemeScriptContract.popupSurfaceIds) {
            rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf(id to ThemeScriptSurface(popupOffset = ThemeScriptPopupOffset(left = 1f)))), manifest) }
        }
        rejects { ThemeScriptContract.decodeResult("""{"apiVersion":1,"surfaces":{"modelMenu":{"popupOffset":{"right":1}}}}""", manifest) }
    }
}
