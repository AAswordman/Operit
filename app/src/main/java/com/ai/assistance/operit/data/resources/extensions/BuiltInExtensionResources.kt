package com.ai.assistance.operit.data.resources.extensions

import android.content.Context
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackCatalog
import com.ai.assistance.operit.data.resources.packs.ResourcePackInstallations
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class BuiltInExtensionSpec(val kind: String, val path: String, val packageNames: List<String>)

/** The APK pins built-in identities. The explicit update check fetches newer catalogs from the maintained resource repository. */
object BuiltInExtensionResources {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    @Volatile private var pinnedCache: ResourcePackCatalog? = null
    @Volatile private var currentCache: List<ResourcePackSpec>? = null
    private fun pinned(context: Context): ResourcePackCatalog {
        pinnedCache?.let { return it }
        return context.assets.open("builtin-extensions.json").bufferedReader()
            .use { json.decodeFromString<ResourcePackCatalog>(it.readText()) }.also { pinnedCache = it }
    }
    // A newer APK has its own identity catalog; an older online snapshot must not override it.
    private fun saved(context: Context) = File(context.filesDir, "builtin-extensions-${pinned(context).releaseTag}.json")
    fun specs(context: Context): List<ResourcePackSpec> {
        currentCache?.let { return it }
        val file = saved(context)
        val catalog = if (file.exists()) json.decodeFromString<ResourcePackCatalog>(file.readText()) else pinned(context)
        validate(context, catalog)
        return catalog.packs.also { currentCache = it }
    }
    private fun validate(context: Context, catalog: ResourcePackCatalog) {
        require(catalog.schemaVersion == 1)
        val identities = pinned(context).packs.associate { it.id to it.extension }
        require(catalog.packs.map { it.id }.toSet() == identities.keys)
        require(catalog.packs.size == identities.size)
        for (spec in catalog.packs) {
            ResourcePackInstallations.validateSpec(spec)
            val identity = requireNotNull(identities.getValue(spec.id))
            val extension = requireNotNull(spec.extension)
            require(extension.kind == identity.kind && extension.path == identity.path &&
                extension.packageNames.first() == identity.packageNames.first()) { "Built-in identity changed: ${spec.id}" }
            require(spec.archive.url == "https://github.com/3316891527/Operit-Kong-resources/releases/download/${catalog.releaseTag}/${spec.archive.path}")
        }
    }
    fun refreshCatalog(context: Context) {
        // Resource releases deliberately do not replace the application's latest-release pointer.
        val releases = client.newCall(Request.Builder()
            .url("https://api.github.com/repos/3316891527/Operit-Kong-resources/releases?per_page=30")
            .header("Accept", "application/vnd.github+json").build()).execute().use {
            check(it.isSuccessful) { "Built-in catalog discovery failed: HTTP ${it.code}" }
            json.parseToJsonElement(requireNotNull(it.body).string()).jsonArray
        }
        val asset = releases.map { it.jsonObject }.sortedByDescending { it["published_at"]?.jsonPrimitive?.content.orEmpty() }.asSequence()
            .filter { it["draft"]?.jsonPrimitive?.boolean == false && it["prerelease"]?.jsonPrimitive?.boolean == false }
            .flatMap { it.getValue("assets").jsonArray.asSequence() }
            .map { it.jsonObject }.first { it.getValue("name").jsonPrimitive.content == "builtin-extensions.json" }
        val url = asset.getValue("browser_download_url").jsonPrimitive.content
        require(url.startsWith("https://github.com/3316891527/Operit-Kong-resources/releases/download/"))
        val text = client.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "Built-in catalog download failed: HTTP ${it.code}" }
            requireNotNull(it.body).string()
        }
        val catalog = json.decodeFromString<ResourcePackCatalog>(text)
        validate(context, catalog)
        val history = File(context.filesDir, "builtin-extension-history.json")
        val previous = historicalSpecs(context) + specs(context)
        history.writeText(json.encodeToString(ResourcePackCatalog.serializer(),
            ResourcePackCatalog(1, catalog.releaseTag, previous.distinctBy { it.id to it.version })))
        val target = saved(context)
        val staged = File(context.filesDir, "builtin-extensions.pending.json")
        staged.writeText(text)
        check(staged.renameTo(target)) { "Could not persist built-in catalog" }
        currentCache = catalog.packs
        PackResources.invalidateCatalog()
    }
    fun historicalSpecs(context: Context): List<ResourcePackSpec> {
        val file = File(context.filesDir, "builtin-extension-history.json")
        return if (file.exists()) json.decodeFromString<ResourcePackCatalog>(file.readText()).packs else emptyList()
    }
    fun packageNames(context: Context): Set<String> = specs(context).flatMap { it.extension!!.packageNames }.toSet()
    fun installed(context: Context): List<Pair<ResourcePackSpec, File>> = specs(context).mapNotNull { current ->
        val installed = PackResources.installedSpec(context, current.id)
        if (installed == null) null else installed to File(PackResources.directory(context, current.id), installed.extension!!.path)
    }
}
