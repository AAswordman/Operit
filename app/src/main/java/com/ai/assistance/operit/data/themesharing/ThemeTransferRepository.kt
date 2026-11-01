package com.ai.assistance.operit.data.themesharing

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class ThemeTransferSource(
    val name: String,
    val values: ThemePreferenceValues,
    val layout: ChatLayoutSettings,
    val globalUserAvatar: String?,
)

/** 文件准备、预览和确认写入相互独立，目标角色由接收方选择。 */
internal class ThemeTransferRepository(private val context: Context) {
    private val preferences = UserPreferencesManager.getInstance(context)
    private val cards = CharacterCardManager.getInstance(context)
    private val prompts = ActivePromptManager.getInstance(context)

    suspend fun source(): ThemeTransferSource =
        prompts.runThemeTransition {
            val target = prompts.getActivePrompt()
            val name: String
            val values: ThemePreferenceValues
            when (target) {
                is ActivePrompt.CharacterCard -> {
                    name = cards.getCharacterCard(target.id).name
                    values =
                        preferences
                            .resolveThemePreferenceSnapshot(characterCardId = target.id)
                            .values
                }
                is ActivePrompt.CharacterGroup -> {
                    name =
                        CharacterGroupCardManager.getInstance(context)
                            .getCharacterGroupCard(target.id)
                            ?.name
                            ?: throw ThemeTransferException(ThemeTransferError.CHARACTER_MISSING)
                    values =
                        preferences
                            .resolveThemePreferenceSnapshot(characterGroupId = target.id)
                            .values
                }
            }
            ThemeTransferSource(
                name,
                values,
                preferences.chatLayoutSettings.first(),
                DisplayPreferencesManager.getInstance(context).globalUserAvatarUri.first(),
            )
        }

    suspend fun targets(): List<CharacterCard> {
        val existing = cards.getAllCharacterCards()
        val defaultId = CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
        val defaultCard = cards.getCharacterCard(defaultId)
        return listOf(defaultCard) + existing.filter { it.id != defaultId }
    }

    fun createArchive(
        source: ThemeTransferSource,
        name: String,
        author: String,
        cache: File,
        scriptBundle: ThemeScriptBundle? = null,
    ): File {
        val staging = File(cache, UUID.randomUUID().toString())
        if (!staging.mkdirs()) throw ThemeTransferException(ThemeTransferError.IO_ERROR)
        val archive = File(cache, "${UUID.randomUUID()}.oktheme")
        try {
            val bundle = if (scriptBundle != null) scriptBundle else source.values.string(ThemePreferenceValues.SCRIPT_URI_KEY)?.let { reference ->
                ThemeScriptBundleStore.read(ThemeScriptBundleStore.fileForReference(context.filesDir, reference))
            }
            bundle?.let { ThemeScriptContract.validate(it.manifest) }
            val scriptPaths = bundle?.let { it.manifest.assets.values.toSet() + it.manifest.entry }.orEmpty()
            val scriptFolders = bundle?.manifest?.studio?.folders.orEmpty()
            var values = source.values
            if (values.string("custom_user_avatar_uri") == null) {
                values = values.withString("custom_user_avatar_uri", source.globalUserAvatar)
            }
            // 当前使用全局用户头像时，将它复制为接收角色的局部头像，不改接收方的全局设置。
            val resourceStrings = values.strings.filterKeys { it in ThemeTransferSchema.assetKeys }
            val relativePaths = mutableMapOf<String, String>()
            val assets = mutableMapOf<String, File>()
            var total = 0L
            resourceStrings.forEach { (key, reference) ->
                val relative =
                    relativePaths.getOrPut(reference) {
                        val extension = resourceExtension(reference)
                        // 普通主题资源独立分配目录，制作工程的原路径不因同名素材而改写。
                        val path = ThemeResourcePaths.allocate(extension, scriptPaths + assets.keys, scriptFolders.toSet())
                        val file = File(staging, path)
                        file.parentFile?.mkdirs()
                        total +=
                            openResource(reference).use { input ->
                                file.outputStream().use { output ->
                                    ThemeArchive.copyLimited(input, output, 256L * 1024 * 1024)
                                }
                            }
                        if (file.length() == 0L)
                            throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
                        if (total > ThemeArchive.MAX_ARCHIVE_BYTES)
                            throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
                        assets[path] = file
                        path
                    }
                values = values.withString(key, relative)
            }
            val script = bundle?.let { project ->
                val (manifest, files) = ThemeScriptBundleStore.exportFiles(project, staging)
                assets.putAll(files)
                manifest
            }
            val document =
                ThemeTransferSchema.create(name, author, values, source.layout, full = true, script = script)
            archive.outputStream().use { ThemeArchive.write(it, document, assets) }
            if (archive.length() > ThemeArchive.MAX_ARCHIVE_BYTES)
                throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
            return archive
        } catch (e: Exception) {
            archive.delete()
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    fun readArchive(uri: Uri, cache: File): PreparedTheme {
        val archive = File(cache, "${UUID.randomUUID()}.zip")
        try {
            val input =
                context.contentResolver.openInputStream(uri)
                    ?: throw ThemeTransferException(ThemeTransferError.IO_ERROR)
            input.use { source ->
                archive.outputStream().use {
                    ThemeArchive.copyLimited(source, it, ThemeArchive.MAX_ARCHIVE_BYTES)
                }
            }
            return ThemeArchive.read(archive, File(cache, UUID.randomUUID().toString()))
        } finally {
            archive.delete()
        }
    }

    fun saveArchive(file: File, uri: Uri) {
        val output =
            context.contentResolver.openOutputStream(uri, "wt")
                ?: throw ThemeTransferException(ThemeTransferError.IO_ERROR)
        output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
    }

    suspend fun apply(theme: PreparedTheme, characterId: String, overwriteConfirmed: Boolean) {
        // 与角色删除、切换和主题编辑共用锁，避免把确认后的配置写进已删除的角色。
        prompts.runThemeTransition {
            if (targets().none { it.id == characterId })
                throw ThemeTransferException(ThemeTransferError.CHARACTER_MISSING)
            withContext(NonCancellable + Dispatchers.IO) {
                val copied = mutableListOf<File>()
                try {
                    val current =
                        preferences
                            .resolveThemePreferenceSnapshot(characterCardId = characterId)
                            .values
                    if (!overwriteConfirmed && !ThemeTransferSchema.isDefaultVisualTheme(current)) {
                        throw ThemeTransferException(ThemeTransferError.OVERWRITE_REQUIRED)
                    }
                    var values = theme.document.values
                    if (theme.document.full) {
                        theme.document.values.strings
                            .filterKeys { it in ThemeTransferSchema.assetKeys }
                            .forEach { (key, path) ->
                                val source =
                                    theme.assets[path]
                                        ?: throw ThemeTransferException(
                                            ThemeTransferError.MISSING_ASSET
                                        )
                                val prefix =
                                    when (key) {
                                        "background_image_uri" -> "background"
                                        "custom_user_avatar_uri" -> "user_avatar"
                                        "custom_ai_avatar_uri" -> "ai_avatar"
                                        "bubble_user_image_uri" -> "bubble_user"
                                        "bubble_ai_image_uri" -> "bubble_ai"
                                        "custom_font_path" -> "custom_font"
                                        "bubble_user_custom_font_path" -> "bubble_user_font"
                                        "bubble_ai_custom_font_path" -> "bubble_ai_font"
                                        else ->
                                            throw ThemeTransferException(
                                                ThemeTransferError.INVALID_CONFIG
                                            )
                                    }
                                val target =
                                    File(
                                        context.filesDir,
                                        "${prefix}_${UUID.randomUUID()}.${source.extension}",
                                    )
                                copied += target
                                source.copyTo(target)
                                values =
                                    values.withString(
                                        key,
                                        if (key.endsWith("font_path")) target.absolutePath
                                        else Uri.fromFile(target).toString(),
                                    )
                            }
                    }
                    theme.document.script?.let { manifest ->
                        val bundle = ThemeScriptBundleStore.fromFiles(manifest, theme.assets)
                        val file = ThemeScriptBundleStore.create(context.filesDir, bundle)
                        copied += file
                        values = values.withString(ThemePreferenceValues.SCRIPT_URI_KEY, Uri.fromFile(file).toString())
                    }
                    val document = theme.document.copy(values = values)
                    // 在同一笔 DataStore 写入中再次检查配置，并同时保存全局聊天布局。
                    preferences.mutateThemeAndLayoutForCharacterCard(
                        characterId,
                        document.layout,
                    ) { latest ->
                        if (
                            !overwriteConfirmed && !ThemeTransferSchema.isDefaultVisualTheme(latest)
                        ) {
                            throw ThemeTransferException(ThemeTransferError.OVERWRITE_REQUIRED)
                        }
                        ThemeTransferSchema.merge(latest, document)
                    }
                } catch (e: Exception) {
                    copied.forEach { it.delete() }
                    throw e
                }
            }
        }
    }

    private fun openResource(reference: String): InputStream {
        if (reference.startsWith("/")) return File(reference).inputStream()
        val uri = Uri.parse(reference)
        if (reference.startsWith("file:///android_asset/")) {
            return context.assets.open(reference.removePrefix("file:///android_asset/"))
        }
        if (uri.scheme !in setOf("content", "file"))
            throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
        return context.contentResolver.openInputStream(uri)
            ?: throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
    }

    private fun resourceExtension(reference: String): String {
        val uri = Uri.parse(reference)
        val mime = if (uri.scheme == "content") context.contentResolver.getType(uri) else null
        val mimeExtension = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val extension =
            if (mimeExtension != null) mimeExtension else File(uri.path.orEmpty()).extension
        if (!extension.matches(Regex("[A-Za-z0-9]{1,10}")))
            throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
        return extension.lowercase()
    }
}
