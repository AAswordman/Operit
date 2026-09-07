package com.ai.assistance.operit.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenStatsPriceScopeTest {

    @Test
    fun `legacy records without configuration id use provider model pricing`() {
        assertEquals(
            TokenStatsPriceScope.PROVIDER_MODEL,
            tokenStatsPriceScopeForConfigId(""),
        )
        assertEquals(
            TokenStatsPriceScope.PROVIDER_MODEL,
            tokenStatsPriceScopeForConfigId("   "),
        )
    }

    @Test
    fun `current records with configuration id keep configuration pricing`() {
        assertEquals(
            TokenStatsPriceScope.CONFIG,
            tokenStatsPriceScopeForConfigId("config-123"),
        )
    }
}
