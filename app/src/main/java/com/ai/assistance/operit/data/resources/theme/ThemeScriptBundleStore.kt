package com.ai.assistance.operit.data.resources.theme

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.net.Uri
import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import com.ai.assistance.operit.data.model.theme.ThemeScriptManifest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** 内部资源束与角色引用分离；复制角色时只复制引用，导出时重新枚举全部素材。 */
data class ThemeScriptBundle(
    val manifest: ThemeScriptManifest,
    val source: String,
    val assets: Map<String, ByteArray>,
)

object ThemeScriptBundleStore {
    private val svgTags = setOf(
        "svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
        "defs", "linearGradient", "radialGradient", "stop", "clipPath", "title", "desc",
    )

    /** 内置模板和导入包使用相同模型与校验，不向角色主题直接写入。 */
    fun readPreset(assets: AssetManager, id: String): ThemeScriptBundle {
        require(id in setOf("forest", "paper")) { "Unknown theme preset" }
        val root = "theme-script/presets/$id"
        fun bytes(path: String, limit: Int): ByteArray =
            assets.open("$root/$path").use { readLimited(it, limit) }
        val manifest = ThemeScriptContract.decodeManifest(decodeUtf8(bytes("manifest.json", ThemeScriptContract.MAX_MANIFEST_BYTES)))
        val source = decodeUtf8(bytes(manifest.entry, ThemeScriptContract.MAX_SOURCE_BYTES))
        val images = manifest.assets.values.toSet().associateWith { bytes(it, ThemeScriptContract.MAX_ASSET_BYTES) }
        return ThemeScriptBundle(manifest, source, images).also(::validate)
    }

    fun fileForReference(filesDir: File, reference: String, previewRoot: File? = null): File {
        val uri = Uri.parse(reference)
        require(uri.scheme == "file" && uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null) {
            "Theme script reference must be an internal resource"
        }
        val file = File(requireNotNull(uri.path)).canonicalFile
        val internal = file.parentFile == filesDir.canonicalFile
        val preview = previewRoot != null && file.toPath().startsWith(previewRoot.canonicalFile.toPath())
        require((internal || preview) && file.name.startsWith("theme_script_") && file.extension == "zip") {
            "Theme script is outside its storage"
        }
        return file
    }

    fun fromFiles(manifest: ThemeScriptManifest, files: Map<String, File>): ThemeScriptBundle {
        ThemeScriptContract.validate(manifest)
        val entry = requireNotNull(files[manifest.entry]) { "Theme script entry is missing" }
        require(entry.length() <= ThemeScriptContract.MAX_SOURCE_BYTES) { "Theme script is too large" }
        val source = decodeUtf8(entry.readBytes())
        require(entry.length() + manifest.assets.values.toSet().sumOf { path ->
            requireNotNull(files[path]) { "Theme script asset is missing: $path" }.length()
        } <= ThemeScriptContract.MAX_BUNDLE_BYTES) { "Theme script bundle is too large" }
        val assets = manifest.assets.values.toSet().associateWith { path ->
            val file = requireNotNull(files[path]) { "Theme script asset is missing: $path" }
            require(file.length() <= ThemeScriptContract.MAX_ASSET_BYTES) { "Theme script asset is too large" }
            file.readBytes()
        }
        return ThemeScriptBundle(manifest, source, assets).also(::validate)
    }

    fun validate(bundle: ThemeScriptBundle) {
        ThemeScriptContract.validate(bundle.manifest)
        require(bundle.source.isNotBlank()) { "Theme script is empty" }
        val bytes = bundle.source.toByteArray(Charsets.UTF_8)
        require(bytes.size <= ThemeScriptContract.MAX_SOURCE_BYTES) { "Theme script is too large" }
        require(bundle.assets.keys == bundle.manifest.assets.values.toSet()) { "Theme script asset set does not match its manifest" }
        require(bytes.size.toLong() + bundle.assets.values.sumOf { it.size.toLong() } <= ThemeScriptContract.MAX_BUNDLE_BYTES) {
            "Theme script bundle is too large"
        }
        var pixels = 0L
        bundle.assets.forEach { (path, data) ->
            pixels += validateAsset(path, data)
            require(pixels <= 4_194_304L) { "Theme drawing assets exceed the decoded pixel budget" }
        }
    }

    fun create(directory: File, bundle: ThemeScriptBundle): File {
        validate(bundle)
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create theme script storage" }
        val file = File(directory, "theme_script_${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(file.outputStream()).use { output ->
                fun put(path: String, bytes: ByteArray) {
                    output.putNextEntry(ZipEntry(path))
                    output.write(bytes)
                    output.closeEntry()
                }
                put("manifest.json", ThemeScriptContract.json.encodeToString(bundle.manifest).toByteArray())
                put(bundle.manifest.entry, bundle.source.toByteArray(Charsets.UTF_8))
                bundle.assets.toSortedMap().forEach { (path, bytes) -> put(path, bytes) }
            }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun read(file: File): ThemeScriptBundle {
        require(file.isFile && file.length() <= ThemeScriptContract.MAX_BUNDLE_BYTES + 131072L) { "Invalid theme script bundle" }
        return ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            require(entries.size <= 50 && entries.map { it.name }.toSet().size == entries.size) { "Invalid theme script entries" }
            val manifestEntry = requireNotNull(zip.getEntry("manifest.json")) { "Theme script manifest is missing" }
            val manifest = ThemeScriptContract.decodeManifest(
                decodeUtf8(zip.getInputStream(manifestEntry).use { readLimited(it, ThemeScriptContract.MAX_MANIFEST_BYTES) }),
            )
            val expected = ThemeScriptContract.references(manifest) + "manifest.json"
            require(entries.map { it.name }.toSet() == expected && entries.none { it.isDirectory }) { "Unexpected theme script file" }
            var remaining = ThemeScriptContract.MAX_BUNDLE_BYTES
            fun bytes(path: String, limit: Int): ByteArray {
                val data = zip.getInputStream(requireNotNull(zip.getEntry(path))).use { readLimited(it, minOf(limit, remaining)) }
                remaining -= data.size
                return data
            }
            val source = decodeUtf8(bytes(manifest.entry, ThemeScriptContract.MAX_SOURCE_BYTES))
            val assets = manifest.assets.values.toSet().associateWith { bytes(it, ThemeScriptContract.MAX_ASSET_BYTES) }
            ThemeScriptBundle(manifest, source, assets).also(::validate)
        }
    }

    /** 制作页路径直接写入归档，重命名和移动只改变清单路径，不改素材标识。 */
    fun exportFiles(bundle: ThemeScriptBundle, directory: File): Pair<ThemeScriptManifest, Map<String, File>> {
        validate(bundle)
        val bytes = bundle.assets + (bundle.manifest.entry to bundle.source.toByteArray(Charsets.UTF_8))
        val targets = bytes.keys.associateWith { path ->
            val target = File(directory, path).canonicalFile
            require(target.toPath().startsWith(directory.canonicalFile.toPath()) && !target.exists()) { "Theme resource path conflicts with another resource: $path" }
            target
        }
        val files = bytes.mapValues { (path, data) ->
            targets.getValue(path).also { target ->
                require(target.parentFile.isDirectory || target.parentFile.mkdirs()) { "Cannot create theme resource directory" }
                target.writeBytes(data)
            }
        }
        return bundle.manifest to files
    }

    fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Theme script resource exceeds size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()

    private fun validateAsset(path: String, bytes: ByteArray): Long {
        require(bytes.isNotEmpty() && bytes.size <= ThemeScriptContract.MAX_ASSET_BYTES) { "Invalid theme script asset size" }
        return when (path.substringAfterLast('.').lowercase()) {
            "png", "jpg", "jpeg", "webp" -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                require(options.outWidth > 0 && options.outHeight > 0 &&
                    options.outWidth.toLong() * options.outHeight <= 4_194_304L) { "Invalid theme script bitmap" }
                options.outWidth.toLong() * options.outHeight
            }
            "svg" -> { validateSvg(bytes); 0L }
            "ttf", "otf" -> {
                require(bytes.size >= 12) { "Theme font header is incomplete" }
                val magic = bytes.take(4).map { it.toInt() and 255 }
                require(magic == listOf(0, 1, 0, 0) || bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) in setOf("OTTO", "true")) { "Invalid theme font header" }
                0L
            }
            else -> error("Theme script drawing assets must be PNG, JPEG, WebP or static SVG")
        }
    }

    private fun validateSvg(bytes: ByteArray) {
        require(bytes.size <= 262144) { "Theme SVG is too large" }
        val text = decodeUtf8(bytes)
        require(!Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            "Theme SVG cannot declare external content"
        }
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var nodes = 0
        var hasRoot = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            require(event != XmlPullParser.DOCDECL && event != XmlPullParser.ENTITY_REF) { "Theme SVG cannot declare external content" }
            if (event == XmlPullParser.START_TAG) {
                if (!hasRoot) { require(parser.name == "svg") { "Theme vector must have an SVG root" }; hasRoot = true }
                require(++nodes <= 2048 && parser.depth <= 24 && parser.name in svgTags) { "Unsupported theme SVG element" }
                for (index in 0 until parser.attributeCount) {
                    val name = parser.getAttributeName(index)
                    val value = parser.getAttributeValue(index)
                    require(!name.startsWith("on", ignoreCase = true) && name !in setOf("href", "src", "style")) { "Unsupported SVG attribute" }
                    require(!value.contains("url", ignoreCase = true) || Regex("url\\(#[A-Za-z_][A-Za-z0-9_.-]*\\)").matches(value)) {
                        "SVG paint references must be local"
                    }
                }
            }
            event = parser.nextToken()
        }
        require(hasRoot) { "Theme SVG is empty" }
    }
}