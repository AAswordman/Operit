package com.ai.assistance.operit.data.themesharing

import com.ai.assistance.operit.data.model.theme.ThemeAssetPaths
import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 完整主题只接受根配置和 assets 资源；校验与解包均在预览缓存目录中完成。 */
internal object ThemeArchive {
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    private const val MAX_ASSET_BYTES = 256L * 1024 * 1024
    private const val MAX_ENTRIES = 64
    fun isAssetPath(path: String): Boolean = ThemeAssetPaths.isFile(path)

    fun read(archive: File, directory: File): PreparedTheme {
        try {
            if (archive.length() > MAX_ARCHIVE_BYTES) tooLarge()
            if (!directory.mkdirs()) throw ThemeTransferException(ThemeTransferError.IO_ERROR)
            val assets = mutableMapOf<String, File>()
            var document: ThemeTransferDocument? = null
            val names = mutableSetOf<String>()
            var totalBytes = 0L
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!names.add(name) || names.size > MAX_ENTRIES) invalid()
                    if (entry.isDirectory) {
                        if (name != "assets/" && !ThemeAssetPaths.isFolder(name.removeSuffix("/"))) invalid()
                        continue
                    }
                    if (name != "manifest.json" && !isAssetPath(name)) invalid()
                    val limit =
                        if (name == "manifest.json") ThemeTransferSchema.MAX_PACKAGE_JSON_BYTES.toLong()
                        else MAX_ASSET_BYTES
                    if (entry.size > limit) tooLarge()
                    val target = File(directory, name)
                    target.parentFile?.mkdirs()
                    val crc = CRC32()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var entryBytes = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                entryBytes += count
                                totalBytes += count
                                if (entryBytes > limit || totalBytes > MAX_ARCHIVE_BYTES) tooLarge()
                                crc.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            if (entryBytes != entry.size || crc.value != entry.crc) invalid()
                        }
                    }
                    if (name == "manifest.json") {
                        document = ThemeTransferSchema.decode(target.readBytes(), full = true)
                    } else {
                        if (target.length() == 0L)
                            throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
                        assets[name] = target
                    }
                }
            }
            val parsed =
                document ?: throw ThemeTransferException(ThemeTransferError.MISSING_MANIFEST)
            val references = references(parsed)
            if (!assets.keys.containsAll(references))
                throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
            if (assets.keys != references) invalid()
            val preview = parsed.script?.let { manifest ->
                ThemeScriptBundleStore.create(directory, ThemeScriptBundleStore.fromFiles(manifest, assets))
            }
            return PreparedTheme(parsed, directory, assets, preview)
        } catch (e: Exception) {
            directory.deleteRecursively()
            if (e is ThemeTransferException) throw e
            throw ThemeTransferException(ThemeTransferError.INVALID_ARCHIVE, e)
        }
    }

    fun write(output: OutputStream, document: ThemeTransferDocument, assets: Map<String, File>) {
        if (!document.full) invalid()
        val references = references(document)
        if (assets.keys != references)
            throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
        document.script?.let { ThemeScriptBundleStore.fromFiles(it, assets) }
        val manifest = ThemeTransferSchema.encode(document)
        var total = manifest.size.toLong()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            assets.forEach { (name, file) ->
                if (!isAssetPath(name) || !file.isFile || file.length() == 0L)
                    throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
                zip.putNextEntry(ZipEntry(name))
                total += file.inputStream().use { copyLimited(it, zip, MAX_ASSET_BYTES) }
                if (total > MAX_ARCHIVE_BYTES) tooLarge()
                zip.closeEntry()
            }
        }
    }

    private fun references(document: ThemeTransferDocument): Set<String> =
        document.values.strings.filterKeys { it in ThemeTransferSchema.assetKeys }.values.toSet() +
            document.script?.let(ThemeScriptContract::references).orEmpty()

    fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > limit) tooLarge()
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun invalid(): Nothing =
        throw ThemeTransferException(ThemeTransferError.INVALID_ARCHIVE)

    private fun tooLarge(): Nothing = throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
}
