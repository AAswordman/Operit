package com.ai.assistance.operit.data.resources.packs

import com.ai.assistance.operit.data.resources.models.ModelArchive
import com.ai.assistance.operit.data.resources.models.ModelFile
import com.ai.assistance.operit.data.resources.models.containedFile
import com.ai.assistance.operit.data.resources.models.storageSegment
import com.ai.assistance.operit.data.resources.models.validateRelativePath
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class ResourcePackInstallHistory(
    val schemaVersion: Int,
    val packs: List<ResourcePackSpec>,
)

internal data class InstalledResourcePack(val spec: ResourcePackSpec, val directory: File) {
    fun hasCurrentContent(current: ResourcePackSpec): Boolean =
        spec.files == current.files && spec.loadOrder == current.loadOrder && spec.extension == current.extension
}

/** 已安装清单独立于 APK 最新目录，升级不会撤销用户已经校验完成的安装。 */
internal object ResourcePackInstallations {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private data class VerifiedStamp(val files: List<ModelFile>, val stamp: List<Pair<Long, Long>>)
    private val verified = mutableMapOf<String, VerifiedStamp>()

    fun validateSpec(spec: ResourcePackSpec) {
        storageSegment(spec.id)
        storageSegment(spec.version)
        require(spec.files.isNotEmpty() && spec.files.map { it.path }.distinct().size == spec.files.size)
        spec.files.forEach {
            validateRelativePath(it.path)
            require(it.bytes >= 0 && it.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        require(spec.loadOrder.all { path -> spec.files.any { it.path == path } })
        spec.extension?.let {
            require(it.kind in setOf("plugin", "sandbox"))
            validateRelativePath(it.path)
            require(spec.files.any { file -> file.path == it.path })
            require(it.packageNames.isNotEmpty() && it.packageNames.distinct().size == it.packageNames.size)
            require(it.path.endsWith(if (it.kind == "plugin") ".toolpkg" else ".js"))
        }
    }

    private fun compatible(current: ResourcePackSpec, installed: ResourcePackSpec): Boolean =
        current.id == installed.id && current.extension != null && installed.extension != null &&
            current.extension.kind == installed.extension.kind && current.extension.path == installed.extension.path &&
            current.extension.packageNames.first() == installed.extension.packageNames.first()

    @Synchronized
    fun supported(
        current: ResourcePackSpec,
        history: List<ResourcePackSpec>,
        groupDirectory: File,
    ): List<ResourcePackSpec> {
        val records = if (current.extension == null) emptyList() else installationRecords(groupDirectory)
        return (listOf(current) + (history + records).filter { compatible(current, it) })
            .distinctBy { it.version }
    }

    private fun installationRecords(groupDirectory: File): List<ResourcePackSpec> =
        File(groupDirectory, ".installed").listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file ->
                runCatching { json.decodeFromString<ResourcePackSpec>(file.readText()).also(::validateSpec) }.getOrNull()
            }

    /** 新内容已就绪才回收已知旧版本；运行中的旧版本留待释放后重试清理。 */
    @Synchronized
    fun cleanupObsolete(
        current: ResourcePackSpec,
        history: List<ResourcePackSpec>,
        directoryFor: (ResourcePackSpec) -> File,
        checkpoint: () -> Unit = {},
    ) {
        if (current.extension == null) return
        val retained = find(current, history, directoryFor) ?: return
        if (!retained.hasCurrentContent(current)) return
        val group = directoryFor(current).parentFile!!.canonicalFile
        val obsolete = (history + installationRecords(group))
            .filter { it.id == current.id && it.version != retained.spec.version }
            .distinctBy { it.version }
            .filter { directoryFor(it).exists() || group.containedFile(".installed/${it.version}.json").exists() }
        if (obsolete.isEmpty()) return
        // 清理前重新核对真实内容，不能因校验缓存命中就删掉唯一可用的旧安装。
        if (!verified(retained.directory, retained.spec, force = true)) return
        for (pack in obsolete) {
            checkpoint()
            validateSpec(pack)
            val directory = directoryFor(pack)
            if (directory.canonicalFile.parentFile != group || directory.canonicalFile == retained.directory.canonicalFile) continue
            when (LocalModelRuntimeRegistry.deleteIfUnused(directory)) {
                LocalModelDeleteOutcome.IN_USE -> continue
                LocalModelDeleteOutcome.FAILED -> error("无法清理旧插件版本：${pack.version}")
                LocalModelDeleteOutcome.DELETED -> {
                    verified.remove(directory.path)
                    val record = group.containedFile(".installed/${pack.version}.json")
                    check(!record.exists() || record.delete()) { "无法清理旧插件安装记录：${pack.version}" }
                }
            }
        }
    }

    @Synchronized
    fun find(
        current: ResourcePackSpec,
        history: List<ResourcePackSpec>,
        directoryFor: (ResourcePackSpec) -> File,
        force: Boolean = false,
    ): InstalledResourcePack? {
        val group = directoryFor(current).parentFile!!
        val candidates = supported(current, history, group)
        val ordered = candidates.take(1) + candidates.drop(1).sortedByDescending { directoryFor(it).lastModified() }
        for (spec in ordered) {
            val root = directoryFor(spec)
            if (root.canonicalFile.parentFile != group.canonicalFile || !verified(root, spec, force)) continue
            // 旧安装的身份由随 APK 固定的历史清单引入，后续安装由成功校验流程登记。
            if (spec.extension != null) remember(spec, group)
            return InstalledResourcePack(spec, root)
        }
        return null
    }

    @Synchronized
    fun verified(root: File, spec: ResourcePackSpec, force: Boolean = false): Boolean {
        val marker = File(root, "operit-resource.json")
        if (!marker.isFile) return false
        val files = listOf(marker) + spec.files.map { root.containedFile(it.path) }
        val stamp = files.map { it.length() to it.lastModified() }
        if (!force && verified[root.path] == VerifiedStamp(spec.files, stamp)) return true
        val valid = runCatching {
            val info = json.parseToJsonElement(marker.readText()).jsonObject
            require(info.getValue("id").jsonPrimitive.content == spec.id)
            require(info.getValue("version").jsonPrimitive.content == spec.version)
            val expected = spec.files.map { it.path }.toSet() + "operit-resource.json"
            require(root.walkTopDown().filter(File::isFile).map { it.relativeTo(root).invariantSeparatorsPath }.toSet() == expected)
            spec.files.all { ModelArchive.verified(root, it) }
        }.getOrDefault(false)
        if (valid) verified[root.path] = VerifiedStamp(spec.files, stamp) else verified.remove(root.path)
        return valid
    }

    @Synchronized
    fun remember(spec: ResourcePackSpec, groupDirectory: File) {
        if (spec.extension == null) return
        validateSpec(spec)
        val record = groupDirectory.containedFile(".installed/${spec.version}.json")
        val content = json.encodeToString(spec)
        if (record.isFile && record.readText() == content) return
        check(record.parentFile!!.isDirectory || record.parentFile!!.mkdirs())
        val pending = File(record.parentFile, ".${record.name}.${UUID.randomUUID()}.tmp")
        try {
            pending.outputStream().use { output -> output.write(content.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            Files.move(pending.toPath(), record.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }
}
