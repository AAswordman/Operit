package com.ai.assistance.operit.data.backup

import androidx.datastore.preferences.PreferencesProto.PreferenceMap
import androidx.datastore.preferences.PreferencesProto.Value
import java.io.File
import java.net.URI

internal data class RawSnapshotResourceRestorePlan(
    val originalUri: String,
    val snapshotFile: File,
    val destination: File,
    val destinationUri: String,
    val restoredWithPayload: Boolean = false,
)

internal object RawSnapshotMediaRestore {
    private val resourceKeys = setOf(
        "global_user_avatar_uri",
        "custom_user_avatar_uri",
        "custom_ai_avatar_uri",
        "background_image_uri",
        "bubble_user_image_uri",
        "bubble_ai_image_uri",
        "custom_font_path",
        "bubble_user_custom_font_path",
        "bubble_ai_custom_font_path",
    )

    data class Result(
        val plans: List<RawSnapshotResourceRestorePlan>,
        val skippedCount: Int,
    )

    fun preferenceFiles(workDir: File): List<File> {
        val payloadRoot = File(workDir, "payload")
        return listOf("files/datastore", "datastore", "shared_prefs")
            .map { File(payloadRoot, it) }
            .filter { it.isDirectory }
            .flatMap { directory ->
                directory.walkTopDown().filter {
                    it.isFile && (it.name.endsWith(".preferences_pb") || it.name.endsWith(".xml"))
                }.toList()
            }
            .distinctBy { it.canonicalPath }
    }

    fun resourceUris(bytes: ByteArray): Set<String> {
        return PreferenceMap.parseFrom(bytes).preferencesMap
            .filter { (key, value) -> isResourceKey(key) && value.valueCase == Value.ValueCase.STRING }
            .values.map { it.string }.filter { it.isNotBlank() }.toSet()
    }

    private fun isResourceKey(key: String): Boolean {
        if (key in resourceKeys) return true
        return (key.startsWith("character_card_theme_") || key.startsWith("character_group_theme_")) &&
            resourceKeys.any { key.endsWith("_$it") }
    }

    fun collectPlans(
        workDir: File,
        sourcePackageName: String,
        filesDir: File,
        externalFilesDir: File?,
        mappedPlans: List<RawSnapshotResourceRestorePlan>,
        mappedOriginalUris: Set<String>,
    ): Result {
        val references = preferenceFiles(workDir)
            .filter { it.name.endsWith(".preferences_pb") }
            .flatMap { resourceUris(it.readBytes()) }.toSet()
        val mappedByPath = mappedPlans.mapNotNull { plan ->
            localPath(plan.originalUri)?.let { it to plan }
        }.toMap()
        val plans = mutableListOf<RawSnapshotResourceRestorePlan>()
        var skipped = 0
        references.forEach { originalUri ->
            if (originalUri in mappedOriginalUris) return@forEach
            val path = localPath(originalUri) ?: return@forEach
            if (path.startsWith("/android_asset/")) return@forEach

            // 同一个文件可能同时以绝对路径和 file URI 保存，所有配置都需要重绑。
            val mapped = mappedByPath[path]
            if (mapped != null) {
                plans += mapped.copy(
                    originalUri = originalUri,
                    destinationUri = uriForDestination(originalUri, mapped.destination),
                )
                return@forEach
            }

            // 旧 release 快照没有资源映射，按原包名和偏好引用定位原始归档中的文件。
            val location = rawLocation(path, sourcePackageName)
            val destinationRoot = when (location?.first) {
                "files" -> filesDir
                "external_files" -> externalFilesDir
                else -> null
            }
            if (location == null || destinationRoot == null) {
                skipped++
                return@forEach
            }
            val snapshotFile = resolveWithin(File(workDir, "payload/${location.first}"), location.second)
            val destination = resolveWithin(destinationRoot, location.second)
            if (snapshotFile == null || !snapshotFile.isFile || destination == null) {
                skipped++
                return@forEach
            }
            plans += RawSnapshotResourceRestorePlan(
                originalUri = originalUri,
                snapshotFile = snapshotFile,
                destination = destination,
                destinationUri = uriForDestination(originalUri, destination),
                restoredWithPayload = true,
            )
        }
        return Result(plans, skipped)
    }

    private fun rawLocation(path: String, sourcePackageName: String): Pair<String, String>? {
        val packagePattern = Regex.escape(sourcePackageName)
        val internal = Regex("^/data/(?:data/$packagePattern|(?:user|user_de)/[0-9]+/$packagePattern)/files/(.+)$")
        internal.matchEntire(path)?.let { return "files" to it.groupValues[1] }
        val external = Regex("^/(?:storage/(?:emulated/[0-9]+|[^/]+)|sdcard)/Android/(?:data|media)/$packagePattern/files/(.+)$")
        external.matchEntire(path)?.let { return "external_files" to it.groupValues[1] }
        return null
    }

    private fun localPath(value: String): String? {
        val path = if (value.startsWith('/')) {
            value
        } else {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (!uri.scheme.equals("file", ignoreCase = true) ||
                !uri.authority.isNullOrEmpty() || uri.query != null || uri.fragment != null
            ) return null
            uri.path ?: return null
        }
        if (!path.startsWith('/') || path.contains('\u0000') || path.contains('\\') ||
            path.split('/').any { it == ".." || it == "." }
        ) return null
        return path
    }

    private fun resolveWithin(root: File, relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.startsWith('/') || relativePath.contains('\u0000')) return null
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        return candidate.takeIf { it.path.startsWith(canonicalRoot.path + File.separator) }
    }

    private fun uriForDestination(originalUri: String, destination: File): String {
        if (originalUri.startsWith('/')) return destination.path
        return URI("file", "", destination.path, null).toASCIIString()
    }

    fun rewritePreferences(bytes: ByteArray, replacements: Map<String, String>): ByteArray {
        // 使用真实 Preferences 协议，仅改字符串值，保留嵌套长度、键名及其他类型。
        val preferences = PreferenceMap.parseFrom(bytes)
        val builder = preferences.toBuilder()
        preferences.preferencesMap.forEach { (key, value) ->
            when (value.valueCase) {
                Value.ValueCase.STRING -> {
                    val rewritten = rewriteStringReferences(value.string, replacements)
                    if (rewritten != value.string) builder.putPreferences(key, value.toBuilder().setString(rewritten).build())
                }
                Value.ValueCase.STRING_SET -> {
                    val strings = value.stringSet.stringsList
                    val rewritten = strings.map { rewriteStringReferences(it, replacements) }
                    if (rewritten != strings) {
                        val stringSet = value.stringSet.toBuilder().clearStrings().addAllStrings(rewritten)
                        builder.putPreferences(key, value.toBuilder().setStringSet(stringSet).build())
                    }
                }
                else -> Unit
            }
        }
        val rewritten = builder.build()
        return if (rewritten == preferences) bytes else rewritten.toByteArray()
    }

    fun rewriteStringReferences(text: String, replacements: Map<String, String>): String {
        val sources = replacements.keys.filter { it.isNotEmpty() }.sortedByDescending { it.length }
        if (sources.isEmpty()) return text
        // 单次替换防止目标 URI 被下一条映射再次改写，并避免误改同名前缀的文件。
        val pattern = Regex(sources.joinToString("|", "(?:", ")(?![\\p{L}\\p{N}_./%+-])") { Regex.escape(it) })
        return pattern.replace(text) { match -> replacements.getValue(match.value) }
    }
}
