package com.ai.assistance.operit.core.tools.packTool.floating

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolPkgFloatingWindowControllerTest {

    private class FakeSurface(override val id: String) : ToolPkgFloatingWindowSurface {
        var alive = true
        var closed = false
        val appliedOptions = mutableListOf<ToolPkgFloatingWindowOptions>()
        val updatedStates = mutableListOf<JSONObject>()
        var shown = 0
        var hidden = 0

        override fun applyOptions(options: ToolPkgFloatingWindowOptions) {
            appliedOptions.add(options)
        }

        override fun show() {
            shown++
        }

        override fun hide() {
            hidden++
        }

        override fun close() {
            closed = true
            alive = false
        }

        override fun updateState(state: JSONObject) {
            updatedStates.add(state)
        }

        override fun isAlive(): Boolean = alive
    }

    private class Harness(vararg enabledOwners: String) {
        val enabled = enabledOwners.toMutableSet()
        val surfaces = mutableListOf<FakeSurface>()

        private val factory = ToolPkgFloatingWindowSurfaceFactory { owner, id, _, options, state ->
            FakeSurface(id).also { surface ->
                surfaces.add(surface)
                surface.appliedOptions.add(options)
                surface.updatedStates.add(state)
            }
        }

        val controller = ToolPkgFloatingWindowController(
            surfaceFactory = factory,
            capabilitiesProvider = { JSONObject().put("supported", true) },
            requestPermissionAction = { true },
            enabledOwnersProvider = { enabled.toSet() },
        )

        fun open(owner: String = "com.example.plugin", id: String = "w1"): JSONObject {
            return controller.command(
                owner, "open",
                JSONObject()
                    .put("id", id)
                    .put("route", "demo")
            ) as JSONObject
        }
    }

    @Test
    fun `open returns info with defaults merged`() {
        val harness = Harness("com.example.plugin")
        val info = harness.open()

        assertEquals("w1", info.getString("id"))
        assertEquals("demo", info.getString("route"))
        assertTrue(info.getBoolean("visible"))
        val options = info.getJSONObject("options")
        assertTrue(options.getBoolean("alwaysOnTop"))
        assertFalse(options.getBoolean("clickThrough"))
        assertFalse(options.getBoolean("focusable"))
        assertFalse(options.getBoolean("draggable"))
    }

    @Test
    fun `duplicate open fails`() {
        val harness = Harness("com.example.plugin")
        harness.open()
        try {
            harness.open()
            fail("Expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("already exists"))
        }
    }

    @Test
    fun `id must be trimmed 1-128 characters`() {
        val harness = Harness("com.example.plugin")
        for (invalid in listOf("", "  ", " lead", "trail ", "a".repeat(129))) {
            try {
                harness.open(id = invalid)
                fail("Expected IllegalArgumentException for id '$invalid'")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("trimmed"))
            }
        }
        harness.open(id = "a".repeat(128))
    }

    @Test
    fun `plugin may own at most 16 windows`() {
        val harness = Harness("com.example.plugin")
        repeat(16) { index -> harness.open(id = "w$index") }
        try {
            harness.open(id = "overflow")
            fail("Expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("at most 16"))
        }
    }

    @Test
    fun `update merges options patch and shallow-merges state`() {
        val harness = Harness("com.example.plugin")
        harness.controller.command(
            "com.example.plugin", "open",
            JSONObject()
                .put("id", "w1")
                .put("route", "demo")
                .put("state", JSONObject().put("count", 1).put("label", "a"))
        )
        val info = harness.controller.command(
            "com.example.plugin", "update",
            JSONObject()
                .put("id", "w1")
                .put("state", JSONObject().put("count", 2))
        ) as JSONObject

        val surface = harness.surfaces.single()
        val merged = surface.updatedStates.last()
        assertEquals(2, merged.getInt("count"))
        assertEquals("a", merged.getString("label"))
        assertEquals(2, surface.updatedStates.size)
        assertTrue(info.getBoolean("visible"))

        val geometry = harness.controller.command(
            "com.example.plugin", "update",
            JSONObject()
                .put("id", "w1")
                .put("options", JSONObject().put("x", 120).put("width", 300))
        ) as JSONObject
        val options = geometry.getJSONObject("options")
        assertEquals(120, options.getInt("x"))
        assertEquals(300, options.getInt("width"))
        val applied = surface.appliedOptions.last()
        assertEquals(120, applied.x)
        assertEquals(300, applied.width)
        assertTrue(applied.alwaysOnTop == true)
    }

    @Test
    fun `show and hide keep the session alive`() {
        val harness = Harness("com.example.plugin")
        harness.open()
        val hidden = harness.controller.command(
            "com.example.plugin", "hide", JSONObject().put("id", "w1")
        ) as JSONObject
        assertFalse(hidden.getBoolean("visible"))
        val shown = harness.controller.command(
            "com.example.plugin", "show", JSONObject().put("id", "w1")
        ) as JSONObject
        assertTrue(shown.getBoolean("visible"))
        val surface = harness.surfaces.single()
        assertFalse(surface.closed)
        assertEquals(1, surface.hidden)
        assertEquals(1, surface.shown)
    }

    @Test
    fun `close releases and unlists the window`() {
        val harness = Harness("com.example.plugin")
        harness.open()
        val result = harness.controller.command(
            "com.example.plugin", "close", JSONObject().put("id", "w1")
        )
        assertNull(result)
        val list = harness.controller.command(
            "com.example.plugin", "list", JSONObject()
        ) as JSONArray
        assertEquals(0, list.length())
        try {
            harness.controller.command(
                "com.example.plugin", "show", JSONObject().put("id", "w1")
            )
            fail("Expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Unknown window"))
        }
    }

    @Test
    fun `owners are isolated`() {
        val harness = Harness("com.example.a", "com.example.b")
        harness.open(owner = "com.example.a", id = "w1")
        harness.open(owner = "com.example.b", id = "w1")

        val listA = harness.controller.command("com.example.a", "list", JSONObject()) as JSONArray
        assertEquals(1, listA.length())

        harness.controller.command(
            "com.example.a", "close", JSONObject().put("id", "w1")
        )
        assertTrue(harness.surfaces[0].closed)
        val remainingA = harness.controller.command("com.example.a", "list", JSONObject()) as JSONArray
        assertEquals(0, remainingA.length())
        val remainingB = harness.controller.command("com.example.b", "list", JSONObject()) as JSONArray
        assertEquals(1, remainingB.length())
        assertEquals("w1", remainingB.getJSONObject(0).getString("id"))
        assertFalse(harness.surfaces[1].closed)

        // Once A has closed its own ID, another close must not reach B's identically named window.
        try {
            harness.controller.command(
                "com.example.a", "close", JSONObject().put("id", "w1")
            )
            fail("close must not affect another owner's window with the same id")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Unknown window"))
        }
        assertFalse(harness.surfaces[1].closed)
    }

    @Test
    fun `audit closes windows of disabled plugins and prunes dead surfaces`() {
        val harness = Harness("com.example.a", "com.example.b")
        harness.open(owner = "com.example.a", id = "w1")
        harness.open(owner = "com.example.b", id = "w2")

        harness.enabled.remove("com.example.b")
        harness.surfaces[0].alive = false

        val closed = harness.controller.audit()

        assertEquals(1, closed)
        assertTrue(harness.surfaces[1].closed)
        assertFalse(harness.surfaces[0].closed)
        val listAll = harness.controller.command("com.example.a", "list", JSONObject()) as JSONArray
        assertEquals(0, listAll.length())
    }

    @Test
    fun `geometry validation rejects out-of-range sizes`() {
        val harness = Harness("com.example.plugin")
        for (invalid in listOf(15, 4097)) {
            try {
                harness.controller.command(
                    "com.example.plugin", "open",
                    JSONObject()
                        .put("id", "w$invalid")
                        .put("route", "demo")
                        .put("options", JSONObject().put("width", invalid))
                )
                fail("Expected IllegalArgumentException for width $invalid")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("width"))
            }
        }
        harness.controller.command(
            "com.example.plugin", "open",
            JSONObject()
                .put("id", "ok")
                .put("route", "demo")
                .put("options", JSONObject().put("width", 16).put("height", 4096))
        )
    }

    @Test
    fun `shallow merge helper overrides only patched keys`() {
        val base = JSONObject().put("a", 1).put("b", JSONObject().put("nested", true))
        val patch = JSONObject().put("b", "replaced")
        val merged = ToolPkgFloatingWindowController.shallowMerge(base, patch)

        assertEquals(1, merged.getInt("a"))
        assertEquals("replaced", merged.get("b"))
    }
}
