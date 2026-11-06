package com.ai.assistance.operit.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CustomEmoji
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CustomEmojiPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 自定义表情 Repository
 *
 * 管理按角色卡/角色组隔离的表情文件和数据持久化。
 * - 文件存储在 filesDir/custom_emoji/<target_scope>/{category}/{uuid}.{ext}
 * - 元数据通过 CustomEmojiPreferences 按 target 独立存储在 DataStore
 */
class CustomEmojiRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: CustomEmojiRepository? = null

        fun getInstance(context: Context): CustomEmojiRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomEmojiRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private const val TAG = "CustomEmojiRepository"
        private const val EMOJI_DIR = "custom_emoji"
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }

    private val preferences = CustomEmojiPreferences.getInstance(context)
    private val activePromptManager by lazy { ActivePromptManager.getInstance(context) }
    @Volatile
    private var legacyStoragePurged = false

    suspend fun initializeBuiltinEmojis() = withContext(Dispatchers.IO) {
        initializeBuiltinEmojis(activePromptManager.getActivePrompt())
    }

    suspend fun initializeBuiltinEmojis(target: ActivePrompt) = withContext(Dispatchers.IO) {
        purgeLegacyGlobalStorage()
        preferences.removeLegacyBuiltinEmojis(target)
    }

    suspend fun resetToDefault() = withContext(Dispatchers.IO) {
        resetToDefault(activePromptManager.getActivePrompt())
    }

    /** 清空指定目标的表情库。应用不再提供内置默认表情。 */
    suspend fun resetToDefault(target: ActivePrompt) = withContext(Dispatchers.IO) {
        try {
            preferences.clearAllEmojis(target)
            getTargetBaseDir(target).deleteRecursively()
            preferences.removeLegacyBuiltinEmojis(target)
            AppLogger.d(TAG, "Cleared emojis for target: $target")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error resetting emojis for target: $target", e)
            throw e
        }
    }

    suspend fun addCustomEmoji(category: String, sourceUri: Uri): Result<CustomEmoji> = withContext(Dispatchers.IO) {
        addCustomEmoji(activePromptManager.getActivePrompt(), category, sourceUri)
    }

    /**
     * 为指定目标添加自定义表情
     */
    suspend fun addCustomEmoji(
        target: ActivePrompt,
        category: String,
        sourceUri: Uri
    ): Result<CustomEmoji> = withContext(Dispatchers.IO) {
        try {
            initializeBuiltinEmojis(target)

            val extension = getFileExtension(sourceUri) ?: return@withContext Result.failure(
                IllegalArgumentException(context.getString(R.string.emoji_cannot_determine_extension))
            )

            if (extension.lowercase() !in SUPPORTED_EXTENSIONS) {
                return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.emoji_unsupported_image_format, extension))
                )
            }

            val fileName = "${UUID.randomUUID()}.$extension"
            val categoryDir = getCategoryDir(target, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            val targetFile = File(categoryDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(
                IllegalStateException(context.getString(R.string.emoji_cannot_read_source))
            )

            val emoji = CustomEmoji(
                emotionCategory = category,
                fileName = fileName,
                isBuiltInCategory = false
            )

            preferences.addCategory(target, category)
            preferences.addCustomEmoji(target, emoji)

            AppLogger.d(TAG, "Successfully added emoji: $fileName to target: $target category: $category")
            Result.success(emoji)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error adding custom emoji for target: $target", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomEmoji(emojiId: String): Result<Unit> = withContext(Dispatchers.IO) {
        deleteCustomEmoji(activePromptManager.getActivePrompt(), emojiId)
    }

    suspend fun deleteCustomEmoji(target: ActivePrompt, emojiId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val emoji = preferences.getCustomEmojisFlow(target).first()
                .find { it.id == emojiId }
                ?: return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.emoji_not_exist, emojiId))
                )

            val file = getEmojiFile(target, emoji)
            if (file.exists()) {
                file.delete()
                AppLogger.d(TAG, "Deleted file: ${file.absolutePath}")
            }

            preferences.deleteCustomEmoji(target, emojiId)

            AppLogger.d(TAG, "Successfully deleted emoji: $emojiId from target: $target")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting custom emoji for target: $target", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(category: String): Result<Unit> = withContext(Dispatchers.IO) {
        deleteCategory(activePromptManager.getActivePrompt(), category)
    }

    suspend fun deleteCategory(target: ActivePrompt, category: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val emojis = preferences.getEmojisForCategory(target, category).first()
            emojis.forEach { emoji ->
                val file = getEmojiFile(target, emoji)
                if (file.exists()) {
                    file.delete()
                }
            }

            val categoryDir = getCategoryDir(target, category)
            if (categoryDir.exists()) {
                categoryDir.deleteRecursively()
            }

            preferences.deleteCategory(target, category)

            AppLogger.d(TAG, "Successfully deleted category: $category from target: $target")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting category for target: $target", e)
            Result.failure(e)
        }
    }

    fun getEmojiFile(target: ActivePrompt, emoji: CustomEmoji): File {
        return File(getCategoryDir(target, emoji.emotionCategory), emoji.fileName)
    }

    fun getEmojiUri(target: ActivePrompt, emoji: CustomEmoji): Uri {
        return Uri.fromFile(getEmojiFile(target, emoji))
    }

    fun getEmojisForCategory(target: ActivePrompt, category: String): Flow<List<CustomEmoji>> {
        return preferences.getEmojisForCategory(target, category)
    }

    fun getAllCategories(target: ActivePrompt): Flow<List<String>> {
        return preferences.getAllCategories(target)
    }

    fun getAllEmojis(target: ActivePrompt): Flow<List<CustomEmoji>> {
        return preferences.getCustomEmojisFlow(target)
    }

    suspend fun addCategory(target: ActivePrompt, categoryName: String) {
        initializeBuiltinEmojis(target)
        preferences.addCategory(target, categoryName)
    }

    suspend fun categoryExists(target: ActivePrompt, categoryName: String): Boolean {
        initializeBuiltinEmojis(target)
        return getAllCategories(target).first().contains(categoryName)
    }

    suspend fun cloneEmojiSet(source: ActivePrompt, target: ActivePrompt) = withContext(Dispatchers.IO) {
        if (source == target) {
            initializeBuiltinEmojis(target)
            return@withContext
        }

        initializeBuiltinEmojis(source)
        deleteTarget(target)

        val categories = preferences.getAllCategories(source).first()
        preferences.setAllCategories(target, categories.toSet())

        val emojis = preferences.getCustomEmojisFlow(source).first()
        val copiedEmojis = mutableListOf<CustomEmoji>()
        emojis.forEach { emoji ->
            val sourceFile = getEmojiFile(source, emoji)
            if (!sourceFile.exists()) return@forEach

            val targetDir = getCategoryDir(target, emoji.emotionCategory)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val targetFile = File(targetDir, emoji.fileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            copiedEmojis.add(emoji)
        }

        preferences.setCustomEmojis(target, copiedEmojis)
        preferences.removeLegacyBuiltinEmojis(target)
    }

    suspend fun deleteTarget(target: ActivePrompt) = withContext(Dispatchers.IO) {
        preferences.deleteTarget(target)
        getTargetBaseDir(target).deleteRecursively()
    }

    suspend fun cloneEmojisBetweenCharacterCards(sourceCharacterCardId: String, targetCharacterCardId: String) {
        cloneEmojiSet(
            ActivePrompt.CharacterCard(sourceCharacterCardId),
            ActivePrompt.CharacterCard(targetCharacterCardId)
        )
    }

    suspend fun deleteCharacterCardEmojis(characterCardId: String) {
        deleteTarget(ActivePrompt.CharacterCard(characterCardId))
    }

    suspend fun cloneEmojisBetweenCharacterGroups(sourceGroupId: String, targetGroupId: String) {
        cloneEmojiSet(
            ActivePrompt.CharacterGroup(sourceGroupId),
            ActivePrompt.CharacterGroup(targetGroupId)
        )
    }

    suspend fun deleteCharacterGroupEmojis(characterGroupId: String) {
        deleteTarget(ActivePrompt.CharacterGroup(characterGroupId))
    }

    fun isValidCategoryName(categoryName: String): Boolean {
        return categoryName.matches(Regex("^[a-z0-9_]+$"))
    }

    // 旧版内置表情复制逻辑已移除。

    private fun getTargetScopeDirName(target: ActivePrompt): String {
        return when (target) {
            is ActivePrompt.CharacterCard -> "character_card_${target.id}"
            is ActivePrompt.CharacterGroup -> "character_group_${target.id}"
        }
    }

    private suspend fun purgeLegacyGlobalStorage() {
        if (legacyStoragePurged) return

        preferences.clearLegacyGlobalStorage()

        val emojiRootDir = File(context.filesDir, EMOJI_DIR)
        emojiRootDir.listFiles()?.forEach { child ->
            val isCurrentScopeDir = child.isDirectory &&
                (child.name.startsWith("character_card_") || child.name.startsWith("character_group_"))
            if (!isCurrentScopeDir) {
                child.deleteRecursively()
            }
        }

        legacyStoragePurged = true
    }

    private fun getTargetBaseDir(target: ActivePrompt): File {
        return File(context.filesDir, "$EMOJI_DIR/${getTargetScopeDirName(target)}")
    }

    private fun getCategoryDir(target: ActivePrompt, category: String): File {
        return File(getTargetBaseDir(target), category)
    }

    private fun getFileExtension(uri: Uri): String? {
        return try {
            val extensionFromMimeType =
                if ("content" == uri.scheme) {
                    val mimeType = context.contentResolver.getType(uri)
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                } else {
                    null
                }

            extensionFromMimeType
                ?.takeIf { it.isNotBlank() }
                ?.lowercase()
                ?: getFileNameFromUri(uri)
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.lowercase()
                ?: uri.path
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.lowercase()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting file extension", e)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if ("content" != uri.scheme) {
            return uri.lastPathSegment
        }

        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
    }
}
