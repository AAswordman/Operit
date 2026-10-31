package com.ai.assistance.operit.data.model.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeStudioContractTest {
    private val manifest = ThemeScriptManifest(
        1, "assets/scripts/main.js",
        mapOf("badge" to "assets/icons/badge.svg", "font" to "assets/fonts/body.ttf"),
    )

    private fun rejects(action: () -> Unit) {
        try { action(); fail("应拒绝无效工程数据") } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun modeOverridesReplaceOnlyDeclaredComponentsAndDoNotMutateCommonData() {
        val common = ThemeScriptSurface(fillColor = "#123456", padding = ThemeScriptInsets(top = 8f))
        val dark = ThemeScriptSurface(fillColor = "#ABCDEF")
        val base = ThemeScriptAppearance(
            1,
            surfaces = mapOf("button" to common, "composer" to common),
            icons = mapOf("composer.send" to ThemeAppearanceIcon("badge")),
            typography = ThemeAppearanceText(family = "serif"),
            modes = mapOf("dark" to ThemeAppearanceMode(
                surfaces = mapOf("button" to dark),
                typography = ThemeAppearanceText(family = "asset:font"),
            )),
        )
        ThemeScriptContract.validate(base, manifest)
        val resolved = base.forMode("dark")
        assertEquals(dark, resolved.surfaces.getValue("button"))
        assertEquals(common, resolved.surfaces.getValue("composer"))
        assertEquals(base.icons, resolved.icons)
        assertEquals("asset:font", resolved.typography.family)
        assertTrue(resolved.modes.isEmpty())
        assertEquals(common, base.forMode("light").surfaces.getValue("button"))
        assertEquals("serif", base.typography.family)
        assertEquals(8f, base.surfaces.getValue("button").padding.top)
    }

    @Test
    fun stateColorsInheritAndOpacityMultipliesWithoutChangingTheDefault() {
        val base = ThemeScriptSurface(
            fillColor = "#123456", contentColor = "#FFFFFF", strokeColor = "#112233", strokeWidth = 2f, opacity = .5f,
            states = mapOf("pressed" to ThemeAppearanceState(fillColor = "#654321", opacity = .4f)),
        )
        val pressed = base.forState("pressed")
        assertEquals("#654321", pressed.fillColor)
        assertEquals("#FFFFFF", pressed.contentColor)
        assertEquals("#112233", pressed.strokeColor)
        assertEquals(2f, pressed.strokeWidth)
        assertEquals(.2f, pressed.opacity, .0001f)
        assertSame(base, base.forState("always"))
        assertEquals("#123456", base.fillColor)
        assertEquals(.5f, base.opacity)
    }

    @Test
    fun unknownModesAndInvalidStateFieldsAreRejected() {
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, modes = mapOf("system" to ThemeAppearanceMode())), manifest) }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, modes = mapOf("dark" to ThemeAppearanceMode(surfaces = mapOf("missing" to ThemeScriptSurface())))), manifest) }
        for (states in listOf(
            mapOf("hover" to ThemeAppearanceState()),
            mapOf("pressed" to ThemeAppearanceState(contentColor = "red")),
            mapOf("disabled" to ThemeAppearanceState(opacity = Float.NaN)),
            mapOf("focused" to ThemeAppearanceState(strokeWidth = 17f)),
        )) {
            rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, surfaces = mapOf("button" to ThemeScriptSurface(states = states))), manifest) }
        }
    }

    @Test
    fun fontsAndImagesCannotBeBoundToTheWrongKindOfProperty() {
        ThemeScriptContract.validate(manifest.copy(appearance = ThemeScriptAppearance(1, typography = ThemeAppearanceText(family = "asset:font"))))
        rejects { ThemeScriptContract.validate(manifest.copy(appearance = ThemeScriptAppearance(1, typography = ThemeAppearanceText(family = "asset:badge")))) }
        rejects { ThemeScriptContract.validate(manifest.copy(appearance = ThemeScriptAppearance(1, icons = mapOf("composer.send" to ThemeAppearanceIcon("font"))))) }
        rejects { ThemeScriptContract.validate(manifest.copy(appearance = ThemeScriptAppearance(1, surfaces = mapOf("button" to ThemeScriptSurface(layers = listOf(ThemeScriptLayer("image", asset = "font"))))))) }
    }

    @Test
    fun samplingAlignmentBlurAndTintRespectTheirBoundaries() {
        // 合法样例与编辑器共用模糊上限，旧的 32 dp 只作为超限拒绝用例。
        val layer = ThemeScriptLayer("image", asset = "badge", fit = "tile", sampling = "nearest", alignment = "end", blur = ThemeScriptContract.MAX_BLUR_DP)
        fun appearance(value: ThemeScriptLayer) = ThemeScriptAppearance(1, surfaces = mapOf("composer" to ThemeScriptSurface(layers = listOf(value))))
        ThemeScriptContract.validate(appearance(layer), manifest)
        for (invalid in listOf(
            layer.copy(sampling = "auto"), layer.copy(alignment = "left"),
            layer.copy(blur = ThemeScriptContract.MAX_BLUR_DP + .01f),
            layer.copy(blur = 32f), layer.copy(blur = Float.NaN),
        )) {
            rejects { ThemeScriptContract.validate(appearance(invalid), manifest) }
        }
        ThemeScriptContract.validate(ThemeScriptAppearance(1, icons = mapOf("composer.send" to ThemeAppearanceIcon("badge", tintColor = "#80112233"))), manifest)
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, icons = mapOf("composer.send" to ThemeAppearanceIcon("badge", tintColor = "blue"))), manifest) }
    }

    @Test
    fun nestedUnicodeFoldersAndMetadataRoundTripWithoutFlatteningPaths() {
        val studio = ThemeStudioMetadata(name = "像素主题", id = "pixel_theme", author = "作者", license = "CC BY 4.0", folders = listOf("assets/图片/空目录"))
        val full = manifest.copy(studio = studio, assets = manifest.assets + ("sky" to "assets/图片/天空.webp"))
        val text = ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), full)
        assertEquals(full, ThemeScriptContract.decodeManifest(text))
        assertTrue(ThemeAssetPaths.isFile("assets/图片/天空.webp"))
        assertTrue(ThemeAssetPaths.isFolder("assets/图片/空目录"))
        assertTrue(ThemeAssetPaths.isFile("assets/" + (1..8).joinToString("/") { "d$it" } + "/x.ttf"))
        assertFalse(ThemeAssetPaths.isFile("assets/" + (1..9).joinToString("/") { "d$it" } + "/x.ttf"))
    }

    @Test
    fun invalidFoldersTraversalAndFileDirectoryCollisionsAreRejected() {
        for (path in listOf("assets/../icons", "assets//icons", "/assets/icons", "assets/icons/", "assets/icons.", "assets/a..b", "assets/icons ", "assets\\icons", "assets/" + "x".repeat(161))) {
            assertFalse(path, ThemeAssetPaths.isFolder(path))
            rejects { ThemeScriptContract.validate(manifest.copy(studio = ThemeStudioMetadata(folders = listOf(path)))) }
        }
        rejects { ThemeScriptContract.validate(manifest.copy(studio = ThemeStudioMetadata(folders = listOf("assets/fonts", "assets/fonts")))) }
        rejects { ThemeScriptContract.validate(manifest.copy(studio = ThemeStudioMetadata(id = "../invalid"))) }
        rejects { ThemeScriptContract.validate(manifest.copy(studio = ThemeStudioMetadata(folders = listOf("assets/icons/badge.svg/child")))) }
        rejects { ThemeScriptContract.validate(manifest.copy(assets = manifest.assets + ("other" to "assets/icons/badge.svg/other.svg"))) }
    }
}