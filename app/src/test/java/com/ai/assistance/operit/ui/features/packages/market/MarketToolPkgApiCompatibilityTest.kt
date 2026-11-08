package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.core.tools.packTool.ToolPkgApiCompatibility
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MarketToolPkgApiCompatibilityTest {
    private val supportedApis =
        ToolPkgApiCompatibility.supportedApiVersions("1.12.2").map { it.toString() }

    @Test
    fun `supported API versions match the loader support set`() {
        supportedApis.forEach { apiVersion ->
            assertNull(resolveToolPkgApiCompatibility("package", apiVersion, supportedApis))
        }
    }

    @Test
    fun `unsupported versions report the declared API and host support`() {
        listOf("2.0.0", "1.0.2").forEach { apiVersion ->
            assertEquals(
                MarketToolPkgApiCompatibility(apiVersion, supportedApis.joinToString(", ")),
                resolveToolPkgApiCompatibility("package", apiVersion, supportedApis)
            )
        }
    }

    @Test
    fun `malformed API metadata is rejected without breaking market rendering`() {
        listOf("2", "v1.0.0", "1.0.0-preview", "999999999999.0.0").forEach { apiVersion ->
            assertNotNull(resolveToolPkgApiCompatibility("package", apiVersion, supportedApis))
        }
    }

    @Test
    fun `legacy metadata and parsed version normalization match the loader`() {
        listOf(null, "", "  ", " 1.0.0 ", "01.00.0").forEach { apiVersion ->
            assertNull(resolveToolPkgApiCompatibility("package", apiVersion, supportedApis))
        }
    }

    @Test
    fun `non ToolPkg entries do not acquire an API requirement`() {
        listOf("script", "skill", "mcp").forEach { type ->
            assertNull(resolveToolPkgApiCompatibility(type, "2.0.0", supportedApis))
        }
    }

    @Test
    fun `client version ranges do not block a supported package`() {
        val entry = MarketV2Entry(
            type = "package",
            latestVersion = MarketV2Version(
                apiVersion = "1.0.0",
                minAppVer = "99.0.0",
                maxAppVer = "0.0.1"
            )
        )
        assertNull(entry.resolveToolPkgApiCompatibility())
    }

    @Test
    fun `permissive client version ranges do not allow an unsupported API`() {
        val entry = MarketV2Entry(
            type = "package",
            latestVersion = MarketV2Version(
                apiVersion = "2.0.0",
                minAppVer = "0.0.1",
                maxAppVer = "99.0.0"
            )
        )
        assertNotNull(entry.resolveToolPkgApiCompatibility())
    }
}
