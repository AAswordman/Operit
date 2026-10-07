package com.ai.assistance.operit.data.resources

import java.io.File
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 统一串行化资源引用变更，配置落盘后才释放旧资源。 */
internal object PrivateResourceCleanup {
    private val mutationMutex = Mutex()

    suspend fun <T> update(
        filesDir: File,
        readReferences: suspend () -> Set<String>,
        persist: suspend () -> T,
        readAllReferences: suspend () -> Set<String>,
        onCleanupFailure: (Exception) -> Unit,
    ): T = mutationMutex.withLock {
        val previousReferences = readReferences()
        val saved = persist()

        // 保存成功后完成清理；退出设置页导致的取消不应留下本次替换的旧文件。
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                ManagedResourceFiles(filesDir).deleteUnreferenced(
                    previousReferences,
                    readAllReferences(),
                ) { file ->
                    onCleanupFailure(IOException("Failed to delete unused resource: ${file.absolutePath}"))
                }
            } catch (e: Exception) {
                // 文件清理失败需要记录，但已经持久化的新设置仍然有效。
                onCleanupFailure(e)
            }
        }
        saved
    }
}

/** 仅管理由设置页和角色卡导入到 files 根目录的图片、视频与字体副本。 */
internal class ManagedResourceFiles(private val filesDir: File) {
    fun deleteUnreferenced(
        previousReferences: Set<String>,
        retainedReferences: Set<String>,
        onDeleteFailure: (File) -> Unit,
    ): Set<File> {
        val root = filesDir.canonicalFile
        val retainedFiles = retainedReferences.mapNotNull(::resolveFile).toSet()
        val deleted = mutableSetOf<File>()
        previousReferences.mapNotNull(::resolveFile).toSet().forEach { file ->
            if (file.parentFile != root || file in retainedFiles || !isManagedName(file.name)) {
                return@forEach
            }
            if (file.isFile) {
                if (file.delete()) {
                    deleted += file
                } else {
                    onDeleteFailure(file)
                }
            }
        }
        return deleted
    }

    private fun resolveFile(reference: String): File? {
        val file = when {
            reference.startsWith("/") -> File(reference)
            reference.startsWith("file:") -> {
                val uri = URI(reference)
                if (!uri.authority.isNullOrEmpty() || uri.query != null || uri.fragment != null) {
                    return null
                }
                File(uri)
            }
            else -> return null
        }
        return file.canonicalFile
    }

    private fun isManagedName(name: String): Boolean =
        managedPrefixes.any { prefix -> name.startsWith("${prefix}_") }

    companion object {
        private val managedPrefixes = setOf(
            "background", "background_video",
            "user_avatar", "ai_avatar", "global_user_avatar", "avatar", "group_avatar",
            "custom_font", "bubble_user_font", "bubble_ai_font", "bubble_user", "bubble_ai",
        )
        private val resourceKeys = setOf(
            "background_image_uri", "custom_user_avatar_uri", "custom_ai_avatar_uri",
            "global_user_avatar_uri", "custom_font_path", "bubble_user_custom_font_path",
            "bubble_ai_custom_font_path", "bubble_user_image_uri", "bubble_ai_image_uri",
        )

        fun references(values: Map<String, Any>): Set<String> =
            values.mapNotNull { (name, value) ->
                val isResourceKey = name in resourceKeys ||
                    ((name.startsWith("character_card_theme_") ||
                        name.startsWith("character_group_theme_")) &&
                        resourceKeys.any { name.endsWith("_$it") })
                (value as? String)?.takeIf { isResourceKey && it.isNotBlank() }
            }.toSet()
    }
}
