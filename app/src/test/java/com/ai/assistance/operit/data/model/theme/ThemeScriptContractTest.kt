package com.ai.assistance.operit.data.model.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeScriptContractTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js", mapOf("badge" to "assets/badge.svg"))
    private fun rejects(action: () -> Unit) {
        try { action(); fail("应拒绝无效脚本协议") } catch (_: IllegalArgumentException) { }
    }
    private fun appearance(layer: ThemeScriptLayer) = ThemeScriptAppearance(
        1, mapOf("userMessage" to ThemeScriptSurface(layers = listOf(layer))),
    )

    @Test
    fun acceptsAllSurfacesAndDeclaredImages() {
        val result = ThemeScriptAppearance(1, ThemeScriptContract.surfaceIds.associateWith {
            ThemeScriptSurface(layers = listOf(ThemeScriptLayer("image", asset = "badge", fit = "crop")))
        })
        ThemeScriptContract.validate(result, manifest)
        assertEquals(setOf("assets/main.js", "assets/badge.svg"), ThemeScriptContract.references(manifest))
    }

    @Test
    fun rejectsUnsafePathsVersionsAndExcessiveAssets() {
        listOf("../main.js", "assets/../main.js", "https://example.invalid/main.js", "assets/main.ts").forEach {
            rejects { ThemeScriptContract.validate(manifest.copy(entry = it)) }
        }
        rejects { ThemeScriptContract.validate(manifest.copy(apiVersion = 2)) }
        rejects { ThemeScriptContract.validate(manifest.copy(assets = (1..49).associate { "a$it" to "assets/$it.png" })) }
        rejects { ThemeScriptContract.validate(manifest.copy(assets = mapOf("main" to manifest.entry))) }
    }

    @Test
    fun rejectsUnknownSurfacesLayersAndAssets() {
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf("toolbar" to ThemeScriptSurface())), manifest) }
        rejects { ThemeScriptContract.validate(appearance(ThemeScriptLayer("html", color = "#112233")), manifest) }
        rejects { ThemeScriptContract.validate(appearance(ThemeScriptLayer("image", asset = "missing")), manifest) }
        rejects { ThemeScriptContract.validate(appearance(ThemeScriptLayer("rect")), manifest) }
    }

    @Test
    fun boundsPaddingAndPaintStayInsideContract() {
        val valid = ThemeScriptLayer("rect", color = "#AA112233", radius = 128f, strokeWidth = 16f)
        ThemeScriptContract.validate(appearance(valid), manifest)
        listOf(
            valid.copy(opacity = Float.NaN), valid.copy(radius = 129f), valid.copy(strokeWidth = -1f),
            valid.copy(bounds = ThemeScriptBounds(x = 1.01f, width = 1f)),
            valid.copy(color = "red"), valid.copy(state = "hover"), valid.copy(placement = "overlay"),
        ).forEach { invalid -> rejects { ThemeScriptContract.validate(appearance(invalid), manifest) } }
        rejects {
            ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf(
                "background" to ThemeScriptSurface(padding = ThemeScriptInsets(top = 1f)),
            )), manifest)
        }
        rejects {
            ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf(
                "composer" to ThemeScriptSurface(padding = ThemeScriptInsets(end = 49f)),
            )), manifest)
        }
    }

    @Test
    fun layerBudgetAppliesAcrossAllSurfaces() {
        val layer = ThemeScriptLayer("ellipse", color = "#123456")
        val surface = ThemeScriptSurface(layers = List(64) { layer })
        ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf("userMessage" to surface, "composer" to surface)), manifest)
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, ThemeScriptContract.surfaceIds.associateWith { surface }), manifest) }
    }

    @Test
    fun rejectsUnknownOutputFieldsDeepJsonAndOversizedResults() {
        rejects { ThemeScriptContract.decodeResult("""{"apiVersion":1,"script":"code"}""", manifest) }
        rejects { ThemeScriptContract.decodeResult("[".repeat(1000) + "0" + "]".repeat(1000), manifest) }
        rejects { ThemeScriptContract.decodeManifest("[".repeat(1000) + "0" + "]".repeat(1000)) }
        rejects { ThemeScriptContract.decodeResult(" ".repeat(ThemeScriptContract.MAX_RESULT_BYTES + 1), manifest) }
        assertEquals(ThemeScriptAppearance(1), ThemeScriptContract.decodeResult("""{"apiVersion":1}""", manifest))
    }

    @Test
    fun extendedStylesIconsAndPalettesRoundTripAsManifestData() {
        val appearance = ThemeScriptAppearance(
            1,
            surfaces = mapOf(
                "button" to ThemeScriptSurface(shape = ThemeAppearanceShape("cut", 12f), strokeColor = "#445566", strokeWidth = 2f),
                "assistantAvatar" to ThemeScriptSurface(size = 48f, shape = ThemeAppearanceShape("circle")),
                "assistantMessage" to ThemeScriptSurface(messageWidth = 0.8f, textStyle = ThemeAppearanceText(family = "serif", scale = 1.1f)),
            ),
            icons = mapOf("composer.send" to ThemeAppearanceIcon("badge", 32f, useContentTint = true)),
            palette = ThemeAppearancePalette(light = mapOf("surfaceContainerHighest" to "#112233"), dark = mapOf("onSurface" to "#FFEEDD")),
        )
        val full = manifest.copy(appearance = appearance)
        val json = ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), full)
        assertEquals(full, ThemeScriptContract.decodeManifest(json))
    }

    @Test
    fun rejectsUnsupportedStyleValuesAndUndeclaredIcons() {
        listOf(
            ThemeScriptSurface(shape = ThemeAppearanceShape("polygon")),
            ThemeScriptSurface(strokeWidth = 17f),
            ThemeScriptSurface(fillColor = "transparent"),
            ThemeScriptSurface(textStyle = ThemeAppearanceText(scale = Float.NaN)),
            ThemeScriptSurface(elevation = -1f),
            ThemeScriptSurface(size = 32f),
            ThemeScriptSurface(messageWidth = 0.8f),
        ).forEach { surface ->
            rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf("button" to surface)), manifest) }
        }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, icons = mapOf("unknown" to ThemeAppearanceIcon("badge"))), manifest) }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, icons = mapOf("composer.send" to ThemeAppearanceIcon("missing"))), manifest) }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, palette = ThemeAppearancePalette(light = mapOf("unknown" to "#112233"))), manifest) }
    }

    @Test
    fun acceptsNativeStatesAndNineSliceButRejectsInvalidSliceBounds() {
        for (state in listOf("selected", "disabled", "error")) {
            ThemeScriptContract.validate(appearance(ThemeScriptLayer("rect", color = "#112233", state = state)), manifest)
        }
        val sliced = ThemeScriptLayer("nineSlice", asset = "badge", slices = ThemeScriptInsets(8f, 8f, 8f, 8f))
        ThemeScriptContract.validate(appearance(sliced), manifest)
        rejects { ThemeScriptContract.validate(appearance(sliced.copy(slices = ThemeScriptInsets(end = -1f))), manifest) }
        rejects { ThemeScriptContract.validate(appearance(sliced.copy(slices = ThemeScriptInsets(end = 2049f))), manifest) }
    }
    @Test
    fun textColorsRoundTripInGlobalLocalAndModeTypography() {
        val base = ThemeScriptAppearance(
            1,
            typography = ThemeAppearanceText(color = "#80112233"),
            surfaces = mapOf("assistantMessage" to ThemeScriptSurface(textStyle = ThemeAppearanceText(color = "#445566"))),
            modes = mapOf("dark" to ThemeAppearanceMode(typography = ThemeAppearanceText(color = "#FFEEDD"))),
        )
        val full = manifest.copy(appearance = base)
        assertEquals(full, ThemeScriptContract.decodeManifest(ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), full)))
        assertEquals("#80112233", base.forMode("light").typography.color)
        assertEquals("#FFEEDD", base.forMode("dark").typography.color)
        assertNull(ThemeScriptContract.decodeResult("""{"apiVersion":1}""", manifest).typography.color)
        listOf("red", "#12345", "#GG112233").forEach { invalid ->
            rejects { ThemeScriptContract.validate(base.copy(typography = ThemeAppearanceText(color = invalid)), manifest) }
            rejects { ThemeScriptContract.validate(base.copy(modes = mapOf("light" to ThemeAppearanceMode(typography = ThemeAppearanceText(color = invalid)))), manifest) }
            rejects { ThemeScriptContract.validate(base.copy(surfaces = mapOf("input" to ThemeScriptSurface(textStyle = ThemeAppearanceText(color = invalid)))), manifest) }
        }
    }

    @Test
    fun fullSizeImagesAndNineSlicesTranslateWithoutChangingTheirSize() {
        for (type in listOf("image", "nineSlice")) {
            for (x in listOf(-1f, -.5f, 0f, .5f, 1f)) for (y in listOf(-1f, 0f, 1f)) {
                val layer = ThemeScriptLayer(type, asset = "badge", bounds = ThemeScriptBounds(x = x, y = y))
                val source = manifest.copy(appearance = appearance(layer))
                val result = ThemeScriptContract.decodeManifest(ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), source))
                assertEquals(layer.bounds, result.appearance.surfaces.getValue("userMessage").layers.single().bounds)
            }
        }
        listOf(
            ThemeScriptBounds(x = -1.01f), ThemeScriptBounds(y = 1.01f), ThemeScriptBounds(x = Float.NaN),
            ThemeScriptBounds(width = 0f), ThemeScriptBounds(height = 1.01f), ThemeScriptBounds(height = Float.POSITIVE_INFINITY),
        ).forEach { bounds -> rejects { ThemeScriptContract.validate(appearance(ThemeScriptLayer("image", asset = "badge", bounds = bounds)), manifest) } }
    }

    @Test
    fun imageBlurUsesTheSameEightDpLimitAsTheEditor() {
        val layer = ThemeScriptLayer("image", asset = "badge")
        for (blur in listOf(0f, ThemeScriptContract.MAX_BLUR_DP)) ThemeScriptContract.validate(appearance(layer.copy(blur = blur)), manifest)
        assertEquals(8f, ThemeScriptContract.MAX_BLUR_DP, 0f)
        for (blur in listOf(-.01f, 8.01f, 32f, Float.NaN, Float.POSITIVE_INFINITY)) {
            rejects { ThemeScriptContract.validate(appearance(layer.copy(blur = blur)), manifest) }
        }
    }

}
