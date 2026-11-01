package com.ai.assistance.operit.ui.theme.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.tools.javascript.QuickJsNativeRuntime
import com.ai.assistance.operit.data.model.theme.ThemeScriptContext
import com.ai.assistance.operit.data.model.theme.ThemeAppearanceShape
import com.ai.assistance.operit.data.model.theme.ThemeAppearanceText
import com.ai.assistance.operit.data.model.theme.ThemeScriptDefaults
import com.ai.assistance.operit.data.model.theme.ThemeScriptSurface
import com.ai.assistance.operit.data.model.theme.ThemeScriptManifest
import com.ai.assistance.operit.data.model.theme.ThemeScriptSettings
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 这些用例需要 Android 原生 QuickJS 库，需单独编译并在设备上执行。 */
@RunWith(AndroidJUnit4::class)
class ThemeScriptAndroidTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js")
    private val input = ThemeScriptContext(
        mode = "dark", colors = mapOf("primary" to "#FF336699"),
        settings = ThemeScriptSettings(emptyMap(), emptyMap(), emptyMap(), emptyMap()),
    )
    private fun bundle(source: String) = ThemeScriptBundle(manifest, source, emptyMap())
    private fun rejects(source: String) = runBlocking {
        try {
            ThemeScriptResolver.resolve(bundle(source), input)
            fail("应拒绝脚本")
        } catch (_: IllegalStateException) {
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun bundledExampleRendersWithNativeEngine() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = context.assets.open("theme-script/example.js").bufferedReader().use { it.readText() }
        val appearance = ThemeScriptResolver.resolve(bundle(source), input)
        assertEquals(input.appearance, appearance)
    }

    @Test
    fun nativePresetsRetainVisualDataAndDeclaredAssets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (id in listOf("forest", "paper")) {
            val preset = ThemeScriptBundleStore.readPreset(context.assets, id)
            val images = ThemeScriptAssets.prepare(preset)
            val appearance = ThemeScriptResolver.resolve(preset, input.copy(appearance = preset.manifest.appearance))
            ThemeScriptAssets.validateAppearance(appearance, images)
            assertEquals(preset.manifest.appearance, appearance)
            assertEquals(36, appearance.palette.light.size)
            assertEquals(36, appearance.palette.dark.size)
            assertTrue(appearance.icons.keys.containsAll(setOf("composer.send", "composer.stop", "composer.microphone")))
        }
    }

    @Test
    fun nineSliceRequiresBitmapAndAVisibleCenterAfterPixelRounding() {
        val bitmap = ThemeScriptImage.Bitmap(androidx.compose.ui.graphics.ImageBitmap(16, 16))
        val vector = ThemeScriptImage.Vector(android.graphics.Picture())
        fun appearance(edge: Float) = com.ai.assistance.operit.data.model.theme.ThemeScriptAppearance(
            1, mapOf("assistantMessage" to com.ai.assistance.operit.data.model.theme.ThemeScriptSurface(
                layers = listOf(com.ai.assistance.operit.data.model.theme.ThemeScriptLayer(
                    "nineSlice", asset = "badge",
                    slices = com.ai.assistance.operit.data.model.theme.ThemeScriptInsets(edge, edge, edge, edge),
                )),
            )),
        )
        ThemeScriptAssets.validateAppearance(appearance(4f), mapOf("badge" to bitmap))
        try {
            ThemeScriptAssets.validateAppearance(appearance(7.6f), mapOf("badge" to bitmap))
            fail("切片取整后必须留下中心像素")
        } catch (_: IllegalArgumentException) { }
        try {
            ThemeScriptAssets.validateAppearance(appearance(4f), mapOf("badge" to vector))
            fail("九宫格必须使用位图")
        } catch (_: IllegalStateException) { }
    }

    @Test
    fun contextIsFrozenAndNativeBridgeIsAbsent() = runBlocking {
        val result = ThemeScriptResolver.resolve(bundle("""
            module.exports = { apiVersion: 1, render(context) {
                if (!Object.isFrozen(context) || !Object.isFrozen(context.settings.strings) || !Object.isFrozen(context.appearance) ||
                    typeof NativeInterface !== "undefined" || typeof Tools !== "undefined" ||
                    typeof require !== "undefined" || typeof fetch !== "undefined") throw new Error("隔离约束不成立");
                return { apiVersion: 1 };
            }};
        """.trimIndent()), input)
        assertTrue(result.surfaces.isEmpty())
    }

    @Test(timeout = 10000)
    fun stopsInfiniteModuleAndRenderAndMemoryExhaustion() {
        rejects("while (true) {}")
        rejects("module.exports = {apiVersion:1,render(){while(true){}}}")
        rejects("module.exports = {apiVersion:1,render(){return new Array(8000000).fill(1)}}")
    }

    @Test
    fun rejectsPromisesAndUnsupportedModules() {
        rejects("module.exports = {apiVersion:1,async render(){return {apiVersion:1}}}")
        rejects("module.exports = {apiVersion:2,render(){return {apiVersion:1}}}")
        rejects("module.exports = {apiVersion:1,render(){return {apiVersion:1,surfaces:{toolbar:{}}}}}")
    }

    @Test(timeout = 10000)
    fun executionBudgetResetsAndOtherInstancesRemainUsable() {
        val bridge = object : QuickJsNativeRuntime.HostBridge {
            override fun onCall(method: String, argsJson: String?): String? = error("不允许原生调用")
        }
        QuickJsNativeRuntime.create(bridge).use { limited ->
            limited.setExecutionLimits(16L * 1024 * 1024, 100)
            assertFalse(limited.eval("while(true){}").success)
            assertTrue(limited.eval("42").success)
            assertFalse(limited.eval("new Array(8000000).fill(1)").success)
        }
        QuickJsNativeRuntime.create(bridge).use { ordinary ->
            assertEquals("42", ordinary.eval("6 * 7").valueJson)
        }
    }

    @Test(timeout = 30000)
    fun repeatedAppearanceEditsSurviveNativeRuntimeCleanup() = runBlocking {
        val shapes = listOf(
            ThemeAppearanceShape("rounded", 16f),
            ThemeAppearanceShape("cut", 16f),
            ThemeAppearanceShape("rounded", 0f),
        )
        val colors = listOf("#FF112233", "#80335577", "#FF00AA88")
        // 较长上下文和反复销毁实例覆盖释放路径，不能只验证最终外观字段。
        val settings = input.settings.copy(strings = mapOf("system_font_name" to "sans-serif".repeat(256)))
        repeat(18) { index ->
            val surface = ThemeScriptSurface(
                shape = shapes[index % shapes.size], contentColor = colors[index % colors.size],
                textStyle = ThemeAppearanceText(color = colors[(index + 1) % colors.size]),
            )
            val appearance = input.appearance.copy(surfaces = mapOf("assistantMessage" to surface))
            val project = bundle(ThemeScriptDefaults.SOURCE).copy(manifest = manifest.copy(appearance = appearance))
            val resolved = ThemeScriptResolver.resolve(project, input.copy(appearance = appearance, settings = settings))
            assertEquals(surface, resolved.surfaces["assistantMessage"])
        }
    }

    @Test
    fun rejectsSvgExternalContentAndAcceptsStaticVectors() {
        val vectorManifest = manifest.copy(assets = mapOf("badge" to "assets/badge.svg"))
        fun vector(text: String) = ThemeScriptBundle(vectorManifest, "module.exports={}", mapOf("assets/badge.svg" to text.toByteArray()))
        val safe = vector("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><rect width="10" height="10" fill="#112233"/></svg>""")
        ThemeScriptBundleStore.validate(safe)
        assertEquals(setOf("badge"), ThemeScriptAssets.prepare(safe).keys)
        listOf(
            """<svg><image href="https://example.invalid/image.png"/></svg>""",
            """<svg><rect onload="run()"/></svg>""",
            """<!DOCTYPE svg [<!ENTITY x SYSTEM "file:///private">]><svg/>""",
            """<svg><path fill="url(https://example.invalid/paint)"/></svg>""",
        ).forEach { text ->
            try { ThemeScriptBundleStore.validate(vector(text)); fail("应拒绝外部 SVG 内容") }
            catch (_: IllegalArgumentException) { }
        }
    }
}