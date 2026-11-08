package com.ai.assistance.operit.data.themesharing

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import com.ai.assistance.operit.data.theme.script.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeAppearanceTransferTest {
    @get:Rule val folder = TemporaryFolder()

    private fun appearance() = ThemeScriptAppearance(
        apiVersion = 1,
        palette = ThemeAppearancePalette(light = mapOf("primary" to "#123456"), dark = mapOf("surface" to "#203040")),
        typography = ThemeAppearanceText(family = "asset:bodyFont", scale = 1.2f, stroke = ThemeTextStroke("#112233", 1.5f)),
        icons = mapOf("composer.send" to ThemeAppearanceIcon("sendIcon", 32f, 0.65f, true, "#445566")),
        surfaces = mapOf(
            "composer.send" to ThemeScriptSurface(
                shape = ThemeAppearanceShape("cut", 9f), fillColor = "#223344", minHeight = 44f,
                states = mapOf("pressed" to ThemeAppearanceState(fillColor = "#334455", opacity = 0.7f)),
                textStyle = ThemeAppearanceText(scale = 1.1f, stroke = ThemeTextStroke("#556677", 2f)),
            ),
        ),
        modes = mapOf("dark" to ThemeAppearanceMode(surfaces = mapOf(
            "modelMenu" to ThemeScriptSurface(
                padding = ThemeScriptInsets(2f, 4f, 6f, 8f),
                popupOffset = ThemeScriptPopupOffset(up = 12f, left = 8f),
                textStyle = ThemeAppearanceText(letterSpacing = 0.5f, stroke = ThemeTextStroke("#667788", 0f)),
                layers = listOf(ThemeScriptLayer("rect", id = "paint", color = "#778899", bounds = ThemeScriptBounds(0.1f, 0.2f, 0.8f, 0.6f))),
            ),
        ))),
    )

    private fun manifest(value: ThemeScriptAppearance = appearance()) = ThemeScriptManifest(
        1, "assets/main.js", mapOf("bodyFont" to "assets/body.ttf", "sendIcon" to "assets/send.png"), value,
        ThemeStudioMetadata(name = "配置主题", author = "作者", folders = listOf("assets/empty")),
    )

    private fun parameters(value: ThemeScriptAppearance = appearance()) = ThemeTransferSchema.create(
        "自定义配置", "作者", ThemePreferenceValues.defaultVisual(), ChatLayoutSettings(), false,
        appearance = ThemeAppearanceParameters.export(value),
    )

    @Test fun parameterCodeKeepsExistingEncodingAndRoundTripsCustomFields() {
        val document = parameters()
        val code = ThemeShareCode.encode(document)
        assertTrue(code.startsWith(ThemeShareCode.PREFIX))
        assertEquals(document, ThemeShareCode.decode(code))
        assertTrue("Only configuration should be shared", code.length < 5000)
        val json = ThemeTransferSchema.encode(document).toString(Charsets.UTF_8)
        assertFalse(json.contains("assets/"))
        assertFalse(json.contains("bodyFont"))
        assertFalse(json.contains("sendIcon"))
        assertFalse(json.contains("file:///"))
        assertFalse(json.contains("module.exports"))
    }

    @Test fun fullManifestKeepsNestedModesLayersStatesAndTextStroke() {
        val script = manifest()
        val document = ThemeTransferSchema.create("完整主题", "作者", ThemePreferenceValues.defaultVisual(), ChatLayoutSettings(), true, script)
        assertEquals(script, ThemeTransferSchema.decode(ThemeTransferSchema.encode(document), true).script)
        assertEquals(appearance(), requireNotNull(document.script).appearance)
    }

    @Test fun parameterImportCreatesUsableAppearanceWithoutMedia() {
        val merged = ThemeAppearanceParameters.merge(ThemeScriptAppearance(1), requireNotNull(parameters().appearance))
        assertEquals(appearance().palette, merged.palette)
        assertEquals(appearance().surfaces, merged.surfaces)
        assertEquals(appearance().modes, merged.modes)
        assertEquals(1.2f, merged.typography.scale)
        assertEquals(ThemeTextStroke("#112233", 1.5f), merged.typography.stroke)
        assertNull(merged.typography.family)
        assertTrue(merged.icons.isEmpty())
        ThemeScriptContract.validate(ThemeScriptManifest(1, "assets/main.js", appearance = merged))
    }

    @Test fun parameterImportKeepsReceiverFontAndIconBindingsWhileUpdatingTheirStyle() {
        val receiver = ThemeScriptAppearance(
            1, typography = ThemeAppearanceText(family = "asset:receiverFont"),
            icons = mapOf("composer.send" to ThemeAppearanceIcon("receiverIcon", 20f)),
        )
        val merged = ThemeAppearanceParameters.merge(receiver, requireNotNull(parameters().appearance))
        assertEquals("asset:receiverFont", merged.typography.family)
        assertEquals("receiverIcon", merged.icons.getValue("composer.send").asset)
        assertEquals(32f, merged.icons.getValue("composer.send").size)
        assertEquals(0.65f, merged.icons.getValue("composer.send").opacity)
        assertEquals("#445566", merged.icons.getValue("composer.send").tintColor)
    }

    @Test fun modeParametersKeepInheritedReceiverMediaBindings() {
        val receiver = ThemeScriptAppearance(1,
            typography = ThemeAppearanceText(family = "asset:receiverFont"),
            icons = mapOf("composer.send" to ThemeAppearanceIcon("receiverIcon")),
        )
        val incoming = ThemeScriptAppearance(1, modes = mapOf("dark" to ThemeAppearanceMode(
            typography = ThemeAppearanceText(scale = 1.4f),
            icons = mapOf("composer.send" to ThemeAppearanceIcon("senderIcon", size = 40f)),
        )))
        val merged = ThemeAppearanceParameters.merge(receiver, ThemeAppearanceParameters.export(incoming))
        assertEquals("asset:receiverFont", merged.forMode("dark").typography.family)
        assertEquals(1.4f, merged.forMode("dark").typography.scale)
        assertEquals("receiverIcon", merged.forMode("dark").icons.getValue("composer.send").asset)
        assertEquals(40f, merged.forMode("dark").icons.getValue("composer.send").size)
    }

    @Test fun imageParametersMatchNamedLayersAndRetainUnmentionedReceiverImages() {
        val receiver = ThemeScriptAppearance(1, surfaces = mapOf("background" to ThemeScriptSurface(layers = listOf(
            ThemeScriptLayer("image", id = "wallpaper", asset = "receiverImage"),
            ThemeScriptLayer("image", id = "decoration", asset = "receiverDecoration"),
        ))))
        val sender = ThemeScriptAppearance(1, surfaces = mapOf("background" to ThemeScriptSurface(layers = listOf(
            ThemeScriptLayer("image", id = "wallpaper", asset = "senderImage", opacity = 0.4f, fit = "crop"),
        ))))
        val parameters = ThemeAppearanceParameters.export(sender)
        assertFalse(parameters.toString().contains("senderImage"))
        val merged = ThemeAppearanceParameters.merge(receiver, parameters).surfaces.getValue("background").layers
        assertEquals("receiverImage", merged.first().asset)
        assertEquals(0.4f, merged.first().opacity)
        assertEquals("crop", merged.first().fit)
        assertEquals("receiverDecoration", merged.last().asset)
    }

    @Test fun absentImageBindingsDoNotPreventIndependentSurfaceParameters() {
        val sender = ThemeScriptAppearance(1, surfaces = mapOf("composer" to ThemeScriptSurface(
            minHeight = 48f, strokeColor = "#334455", strokeWidth = 2f,
            layers = listOf(ThemeScriptLayer("image", id = "texture", asset = "senderImage")),
        )))
        val merged = ThemeAppearanceParameters.merge(ThemeScriptAppearance(1), ThemeAppearanceParameters.export(sender))
        val surface = merged.surfaces.getValue("composer")
        assertEquals(48f, surface.minHeight)
        assertEquals(2f, surface.strokeWidth)
        assertTrue(surface.layers.isEmpty())
    }

    @Test fun invalidParametersAreRejectedEvenWhenReceiverHasNoMatchingResource() {
        val invalid = ThemeScriptAppearance(1, icons = mapOf("composer.send" to ThemeAppearanceIcon("image", size = 1000f)))
        try {
            ThemeAppearanceParameters.merge(ThemeScriptAppearance(1), ThemeAppearanceParameters.export(invalid))
            fail("Invalid resource-dependent parameters must still be validated")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun resourceIdentifiersCannotBeInjectedIntoParameterCodes() {
        val injected = buildJsonObject {
            put("apiVersion", 1)
            put("icons", buildJsonObject {
                put("composer.send", buildJsonObject { put("asset", "file:///private/icon.png") })
            })
        }
        try {
            ThemeAppearanceParameters.validate(injected)
            fail("A parameter code cannot replace resource bindings")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun fullPackageRoundTripsNestedAppearanceFontVideoAndSourceBytes() {
        val appearance = appearance().copy(icons = emptyMap())
        val script = manifest(appearance).copy(assets = mapOf("bodyFont" to "assets/body.ttf"))
        val values = ThemePreferenceValues.defaultVisual()
            .withString("background_image_uri", "assets/background.mp4")
            .withString("background_media_type", "video")
            .withBoolean("use_background_image", true)
            .withBoolean("video_background_muted", false)
            .withBoolean("video_background_loop", false)
        val document = ThemeTransferSchema.create("完整主题", "作者", values, ChatLayoutSettings(), true, script)
        val source = "module.exports={apiVersion:1,render(context){return context.appearance}};".toByteArray()
        val font = byteArrayOf(0, 1, 0, 0) + ByteArray(8)
        val video = byteArrayOf(1, 2, 3, 4)
        val bytes = mapOf(script.entry to source, "assets/body.ttf" to font, "assets/background.mp4" to video)
        val assets = bytes.mapValues { (_, data) -> folder.newFile().apply { writeBytes(data) } }
        val archive = folder.newFile("complete.oktheme")
        archive.outputStream().use { ThemeArchive.write(it, document, assets) }
        ThemeArchive.read(archive, File(folder.root, "prepared")).use { prepared ->
            assertEquals(document, prepared.document)
            bytes.forEach { (path, data) -> assertArrayEquals(data, prepared.assets.getValue(path).readBytes()) }
            assertEquals(listOf("assets/empty"), requireNotNull(prepared.document.script).studio.folders)
        }
    }

    @Test fun unnamedImageParametersUseTheReceiverLayerAtTheSamePosition() {
        val receiver = ThemeScriptAppearance(1, surfaces = mapOf("background" to ThemeScriptSurface(layers = listOf(
            ThemeScriptLayer("image", id = "receiverLayer", asset = "receiverImage"),
        ))))
        val sender = ThemeScriptAppearance(1, surfaces = mapOf("background" to ThemeScriptSurface(layers = listOf(
            ThemeScriptLayer("image", asset = "senderImage", opacity = 0.3f),
        ))))
        val merged = ThemeAppearanceParameters.merge(receiver, ThemeAppearanceParameters.export(sender))
        val layer = merged.surfaces.getValue("background").layers.single()
        assertEquals("receiverImage", layer.asset)
        assertEquals("receiverLayer", layer.id)
        assertEquals(0.3f, layer.opacity)
    }

    @Test fun incomingPaintCannotReplaceAnExistingResourceLayerIdentity() {
        val receiver = ThemeScriptAppearance(1, surfaces = mapOf("background" to ThemeScriptSurface(layers = listOf(
            ThemeScriptLayer("image", id = "wallpaper", asset = "receiverImage"),
        ))))
        val sender = ThemeScriptAppearance(1, surfaces = mapOf("background" to ThemeScriptSurface(layers = listOf(
            ThemeScriptLayer("rect", id = "wallpaper", color = "#112233"),
        ))))
        val merged = ThemeAppearanceParameters.merge(receiver, ThemeAppearanceParameters.export(sender))
        val layers = merged.surfaces.getValue("background").layers
        assertNull(layers.first().id)
        assertEquals("wallpaper", layers.last().id)
        assertEquals("receiverImage", layers.last().asset)
    }
}