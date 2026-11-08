package com.ai.assistance.operit.data.resources.extensions

import com.ai.assistance.operit.data.resources.packs.ResourcePackCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class BuiltInCatalogRelease(val tag: String, val assetName: String, val url: String)

/** Both published layouts are explicit protocols; a broken latest release must remain an error. */
internal object BuiltInCatalogDiscovery {
    private const val PREFIX = "https://github.com/3316891527/Operit-Kong-resources/releases/download/"
    private val json = Json { ignoreUnknownKeys = true }

    fun select(releases: JsonArray): BuiltInCatalogRelease {
        val latest = releases.map { it.jsonObject }
            .filter { release ->
                !release.getValue("draft").jsonPrimitive.boolean &&
                    !release.getValue("prerelease").jsonPrimitive.boolean &&
                    release.getValue("tag_name").jsonPrimitive.content.let { tag ->
                        tag.startsWith("resources-v2-") || tag.startsWith("resources-v3-")
                    }
            }
            .maxByOrNull { it.getValue("published_at").jsonPrimitive.content }
            ?: error("No published built-in resource release was found")
        val tag = latest.getValue("tag_name").jsonPrimitive.content
        val name = when {
            tag.startsWith("resources-v2-") -> "offline-packs.json"
            tag.startsWith("resources-v3-") -> "builtin-extensions.json"
            else -> error("Unsupported resource release: $tag")
        }
        val asset = latest.getValue("assets").jsonArray.map { it.jsonObject }
            .singleOrNull { it.getValue("name").jsonPrimitive.content == name }
            ?: error("Resource release $tag is missing $name")
        val url = asset.getValue("browser_download_url").jsonPrimitive.content
        require(url == "$PREFIX$tag/$name") { "Resource catalog URL does not match release $tag" }
        return BuiltInCatalogRelease(tag, name, url)
    }

    fun decode(text: String, release: BuiltInCatalogRelease): ResourcePackCatalog {
        val catalog = json.decodeFromString<ResourcePackCatalog>(text)
        require(catalog.schemaVersion == 1 && catalog.releaseTag == release.tag) {
            "Resource catalog does not match release ${release.tag}"
        }
        val extensions = when (release.assetName) {
            "offline-packs.json" -> catalog.packs.filter { it.extension != null }
            "builtin-extensions.json" -> catalog.packs.also { packs ->
                require(packs.all { it.extension != null }) { "Built-in catalog contains non-extension resources" }
            }
            else -> error("Unsupported resource catalog: ${release.assetName}")
        }
        require(extensions.isNotEmpty()) { "Resource catalog contains no built-in extensions" }
        return catalog.copy(packs = extensions)
    }
}