package com.ai.assistance.operit.core.tools.javascript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsToolPkgRegistrationTest {
    @Test
    fun `captures valid marketplace origin during registration`() {
        val session = JsToolPkgRegistrationSession()
        session.begin()
        session.captureMarketOrigin(
            "{\"market\":\"Operit\",\"toolpkgId\":\"uuid-generator\",\"version\":\"1.0.2\",\"author\":[\"Original Author\"]}"
        )

        val capture = session.finish(null)

        assertEquals("Operit", capture.marketOrigin?.market)
        assertEquals("uuid-generator", capture.marketOrigin?.toolpkgId)
        session.end()
    }

    @Test
    fun `does not capture malformed marketplace origin`() {
        val session = JsToolPkgRegistrationSession()
        session.begin()
        session.captureMarketOrigin("{\"market\":\"Operit\"}")

        assertNull(session.finish(null).marketOrigin)
        session.end()
    }

    @Test
    fun `ignores marketplace origin calls outside registration`() {
        val session = JsToolPkgRegistrationSession()
        val originJson =
            "{\"market\":\"Operit\",\"toolpkgId\":\"uuid-generator\",\"version\":\"1.0.2\",\"author\":[\"Original Author\"]}"

        session.captureMarketOrigin(originJson)

        session.begin()
        assertNull(session.finish(null).marketOrigin)
        session.end()
    }

    @Test
    fun `registration bridge exposes encoded marketplace marker method`() {
        val bridge = buildToolPkgRegistrationBridgeScript()

        assertTrue(bridge.contains("_m: captureMarketOrigin"))
        assertTrue(bridge.contains("captureToolPkgMarketOrigin"))
    }
}
