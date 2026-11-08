package com.ai.assistance.operit.data.resources.extensions

import com.ai.assistance.operit.ui.features.extensions.packages.market.ExtensionUpdate
import com.ai.assistance.operit.ui.features.extensions.packages.market.ExtensionUpdateScope
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BuiltInCatalogDiscoveryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val config = File(requireNotNull(System.getProperty("operit.repositoryRoot")), "app/config")
    private val builtins get() = json.parseToJsonElement(File(config, "builtin-extensions.json").readText()).jsonObject
    private val resources get() = json.parseToJsonElement(File(config, "offline-packs.json").readText()).jsonObject
    private val tag get() = builtins.getValue("releaseTag").jsonPrimitive.content

    private fun release(tag: String, published: String, assetName: String?, draft: Boolean = false): JsonObject = buildJsonObject {
        put("tag_name", tag)
        put("published_at", published)
        put("draft", draft)
        put("prerelease", false)
        put("assets", buildJsonArray {
            if (assetName != null) add(buildJsonObject {
                put("name", assetName)
                put("browser_download_url", "https://github.com/3316891527/Operit-Kong-resources/releases/download/$tag/$assetName")
            })
        })
    }

    private fun combined(): JsonObject = JsonObject(resources + ("packs" to JsonArray(
        resources.getValue("packs").jsonArray + builtins.getValue("packs").jsonArray)))

    @Test
    fun publishedV2CatalogLoadsAllFortyExtensionsWithoutChangingTheirArchives() {
        val selected = BuiltInCatalogDiscovery.select(JsonArray(listOf(release(tag, "2026-11-07T00:00:00Z", "offline-packs.json"))))
        val decoded = BuiltInCatalogDiscovery.decode(combined().toString(), selected)
        val expected = json.decodeFromString<com.ai.assistance.operit.data.resources.packs.ResourcePackCatalog>(builtins.toString())
        assertEquals(expected, decoded)
        assertEquals(40, decoded.packs.size)
    }

    @Test
    fun publicationTimeSelectsLatestReleaseAcrossBothLayoutsAndIgnoresDrafts() {
        val selected = BuiltInCatalogDiscovery.select(JsonArray(listOf(
            release("resources-v3-old", "2026-11-01T00:00:00Z", "builtin-extensions.json"),
            release(tag, "2026-11-07T00:00:00Z", "offline-packs.json"),
            release("resources-v3-draft", "2026-11-08T00:00:00Z", "builtin-extensions.json", draft = true),
        )))
        assertEquals(tag, selected.tag)
        assertEquals("offline-packs.json", selected.assetName)
    }

    @Test
    fun newestIncompleteReleaseCannotBeHiddenByAnOlderCompleteRelease() {
        val error = assertThrows(IllegalStateException::class.java) {
            BuiltInCatalogDiscovery.select(JsonArray(listOf(
                release(tag, "2026-11-01T00:00:00Z", "offline-packs.json"),
                release("resources-v3-incomplete", "2026-11-07T00:00:00Z", null),
            )))
        }
        assertTrue(error.message.orEmpty().contains("resources-v3-incomplete"))
        assertTrue(error.message.orEmpty().contains("builtin-extensions.json"))
    }

    @Test
    fun v3LayoutRejectsOrdinaryResourcesAndCatalogsFromAnotherRelease() {
        val v3Tag = tag.replace("resources-v2-", "resources-v3-")
        val release = BuiltInCatalogRelease(v3Tag, "builtin-extensions.json", "unused")
        val standalone = JsonObject(builtins + ("releaseTag" to JsonPrimitive(v3Tag))).toString()
        val invalid = JsonObject(combined() + ("releaseTag" to JsonPrimitive(v3Tag))).toString()
        assertEquals(40, BuiltInCatalogDiscovery.decode(standalone, release).packs.size)
        assertThrows(IllegalArgumentException::class.java) { BuiltInCatalogDiscovery.decode(invalid, release) }
        assertThrows(IllegalArgumentException::class.java) {
            BuiltInCatalogDiscovery.decode(standalone, release.copy(tag = "resources-v3-another"))
        }
    }

    @Test
    fun installedPluginAndScriptCountersUseTheirOwnTypes() {
        val packs = json.decodeFromString<com.ai.assistance.operit.data.resources.packs.ResourcePackCatalog>(builtins.toString()).packs
        val updates = packs.map { ExtensionUpdate.BuiltIn(it) }
        assertEquals(10, updates.count(ExtensionUpdateScope("package")::accepts))
        assertEquals(30, updates.count(ExtensionUpdateScope("script")::accepts))
        assertEquals(40, updates.count(ExtensionUpdateScope(builtInOnly = true)::accepts))
    }
}