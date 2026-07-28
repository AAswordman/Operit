package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.GroupMemberConfig
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil

private val Context.characterGroupCardDataStore by preferencesDataStore(
    name = "character_groups"
)

/**
 * 群组角色卡管理器
 */
class CharacterGroupCardManager private constructor(private val context: Context) {

    private val dataStore = context.characterGroupCardDataStore
    private val gson = Gson()
    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val userPreferencesManager = UserPreferencesManager.getInstance(context)
    private val waifuPreferences = WaifuPreferences.getInstance(context)
    private val customEmojiRepository by lazy { CustomEmojiRepository.getInstance(context) }

    companion object {
        private val CHARACTER_GROUP_LIST = stringSetPreferencesKey("character_group_list")
        private val ACTIVE_CHARACTER_GROUP_ID = stringPreferencesKey("active_character_group_id")

        @Volatile
        private var INSTANCE: CharacterGroupCardManager? = null

        fun getInstance(context: Context): CharacterGroupCardManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CharacterGroupCardManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val characterGroupCardListFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[CHARACTER_GROUP_LIST]?.toList() ?: emptyList()
    }

    private val activeCharacterGroupCardIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_CHARACTER_GROUP_ID]?.takeIf { it.isNotBlank() }
    }

    val allCharacterGroupCardsFlow: Flow<List<CharacterGroupCard>> = dataStore.data.map { preferences ->
        val ids = preferences[CHARACTER_GROUP_LIST]?.toList() ?: emptyList()
        ids.mapNotNull { id ->
            val raw = preferences[groupDataKey(id)] ?: return@mapNotNull null
            decodeGroup(raw)
        }.sortedByDescending { it.updatedAt }
    }

    fun getCharacterGroupCardFlow(id: String): Flow<CharacterGroupCard?> = dataStore.data.map { preferences ->
        val raw = preferences[groupDataKey(id)] ?: return@map null
        decodeGroup(raw)
    }

    private val activeCharacterGroupCardFlow: Flow<CharacterGroupCard?> = dataStore.data.map { preferences ->
        val activeId = preferences[ACTIVE_CHARACTER_GROUP_ID]?.takeIf { it.isNotBlank() } ?: return@map null
        val raw = preferences[groupDataKey(activeId)] ?: return@map null
        decodeGroup(raw)
    }

    internal fun observeActiveCharacterGroupId(): Flow<String?> = activeCharacterGroupCardIdFlow

    suspend fun createCharacterGroupCard(group: CharacterGroupCard): String {
        // Target activation must not interleave with a theme snapshot save.
        return ActivePromptManager.getInstance(context).runThemeTransition {
            createCharacterGroupCardLocked(group)
        }
    }

    private suspend fun createCharacterGroupCardLocked(group: CharacterGroupCard): String {
        val emojiSourcePrompt = resolveEmojiSourcePrompt()
        val now = System.currentTimeMillis()
        val id = group.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val normalizedGroup = normalizeGroup(
            group.copy(
                id = id,
                createdAt = if (group.createdAt > 0) group.createdAt else now,
                updatedAt = now
            )
        )
        var activatedGroup = false

        dataStore.edit { preferences ->
            val currentList = preferences[CHARACTER_GROUP_LIST]?.toMutableSet() ?: mutableSetOf()
            currentList.add(id)
            preferences[CHARACTER_GROUP_LIST] = currentList
            preferences[groupDataKey(id)] = gson.toJson(normalizedGroup)

            if (preferences[ACTIVE_CHARACTER_GROUP_ID].isNullOrBlank()) {
                preferences[ACTIVE_CHARACTER_GROUP_ID] = id
                activatedGroup = true
            }
        }

        createDefaultBindingsForCharacterGroup(normalizedGroup, emojiSourcePrompt)
        if (activatedGroup) {
            waifuPreferences.switchToCharacterGroupWaifuSettings(id)
        }
        return id
    }

    suspend fun updateCharacterGroupCard(group: CharacterGroupCard) {
        if (group.id.isBlank()) return

        val existingGroup = getCharacterGroupCard(group.id)
        val previousAvatarUri = userPreferencesManager.getAiAvatarForCharacterGroupFlow(group.id).first()
        val normalizedGroup = normalizeGroup(
            group.copy(updatedAt = System.currentTimeMillis())
        )

        dataStore.edit { preferences ->
            val currentList = preferences[CHARACTER_GROUP_LIST]?.toMutableSet() ?: mutableSetOf()
            currentList.add(group.id)
            preferences[CHARACTER_GROUP_LIST] = currentList
            preferences[groupDataKey(group.id)] = gson.toJson(normalizedGroup)
        }

        val shouldRegenerateDefaultAvatar =
            hasMemberRosterChanged(existingGroup, normalizedGroup) &&
                isManagedDefaultGroupAvatar(previousAvatarUri, group.id)
        if (shouldRegenerateDefaultAvatar) {
            regenerateDefaultAvatarForGroup(normalizedGroup, previousAvatarUri)
        }
    }

    suspend fun deleteCharacterGroupCard(groupId: String) {
        if (groupId.isBlank()) return

        // Target activation must not interleave with a theme snapshot save.
        ActivePromptManager.getInstance(context).runThemeTransition {
            deleteCharacterGroupCardLocked(groupId)
        }
    }

    private suspend fun deleteCharacterGroupCardLocked(groupId: String) {
        val groupAvatarUri = userPreferencesManager.getAiAvatarForCharacterGroupFlow(groupId).first()
        var deletedActiveGroup = false
        dataStore.edit { preferences ->
            val currentList = preferences[CHARACTER_GROUP_LIST]?.toMutableSet() ?: mutableSetOf()
            currentList.remove(groupId)
            preferences[CHARACTER_GROUP_LIST] = currentList
            preferences.remove(groupDataKey(groupId))

            if (preferences[ACTIVE_CHARACTER_GROUP_ID] == groupId) {
                deletedActiveGroup = true
                preferences.remove(ACTIVE_CHARACTER_GROUP_ID)
            }
        }

        runCatching { userPreferencesManager.deleteCharacterGroupTheme(groupId) }
        runCatching { waifuPreferences.deleteCharacterGroupWaifuSettings(groupId) }
        runCatching { customEmojiRepository.deleteCharacterGroupEmojis(groupId) }
        runCatching { ChatHistoryManager.getInstance(context).clearCharacterGroupBinding(groupId) }
        if (isManagedDefaultGroupAvatar(groupAvatarUri, groupId)) {
            val avatarPath = Uri.parse(groupAvatarUri).path
            if (!avatarPath.isNullOrBlank()) {
                runCatching { File(avatarPath).delete() }
            }
        }

        if (deletedActiveGroup) {
            val activeCardId = characterCardManager.observeActiveCharacterCardId().first()
                ?: CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
            runCatching { waifuPreferences.switchToCharacterCardWaifuSettings(activeCardId) }
        }
    }

    suspend fun setActiveCharacterGroupCard(groupId: String?) {
        dataStore.edit { preferences ->
            if (groupId.isNullOrBlank()) {
                preferences.remove(ACTIVE_CHARACTER_GROUP_ID)
            } else {
                preferences[ACTIVE_CHARACTER_GROUP_ID] = groupId
            }
        }

        if (!groupId.isNullOrBlank()) {
            runCatching { waifuPreferences.switchToCharacterGroupWaifuSettings(groupId) }
        }
    }

    suspend fun getCharacterGroupCard(groupId: String): CharacterGroupCard? {
        return getCharacterGroupCardFlow(groupId).first()
    }

    suspend fun getAllCharacterGroupCards(): List<CharacterGroupCard> {
        return allCharacterGroupCardsFlow.first()
    }

    suspend fun initializeIfNeeded() {
        dataStore.edit { preferences ->
            if (preferences[CHARACTER_GROUP_LIST] == null) {
                preferences[CHARACTER_GROUP_LIST] = emptySet()
            }
        }
    }

    suspend fun cloneBindingsFromCharacterGroup(sourceGroupId: String, targetGroupId: String) {
        val activePromptManager = ActivePromptManager.getInstance(context)
        activePromptManager.runThemeTransition {
            cloneBindingsFromCharacterGroupLocked(sourceGroupId, targetGroupId)
            if (activePromptManager.getActivePrompt() == ActivePrompt.CharacterGroup(targetGroupId)) {
                waifuPreferences.switchToCharacterGroupWaifuSettings(targetGroupId)
            }
        }
    }

    private suspend fun cloneBindingsFromCharacterGroupLocked(
        sourceGroupId: String,
        targetGroupId: String,
    ) {
        runCatching {
            userPreferencesManager.cloneThemeBetweenCharacterGroups(sourceGroupId, targetGroupId)
        }
        runCatching {
            waifuPreferences.cloneWaifuSettingsBetweenCharacterGroups(sourceGroupId, targetGroupId)
        }
        runCatching {
            customEmojiRepository.cloneEmojisBetweenCharacterGroups(sourceGroupId, targetGroupId)
        }
    }

    suspend fun duplicateCharacterGroupCard(
        sourceGroupId: String,
        newName: String? = null
    ): String? {
        val activePromptManager = ActivePromptManager.getInstance(context)
        return activePromptManager.runThemeTransition {
            val source = getCharacterGroupCard(sourceGroupId) ?: return@runThemeTransition null
            val duplicated = source.copy(
                id = "",
                name = newName?.takeIf { it.isNotBlank() } ?: source.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = createCharacterGroupCardLocked(duplicated)
            cloneBindingsFromCharacterGroupLocked(sourceGroupId, newId)
            if (activePromptManager.getActivePrompt() == ActivePrompt.CharacterGroup(newId)) {
                waifuPreferences.switchToCharacterGroupWaifuSettings(newId)
            }
            newId
        }
    }

    private fun decodeGroup(json: String): CharacterGroupCard? {
        return runCatching {
            gson.fromJson(json, CharacterGroupCard::class.java)
        }.getOrNull()?.let { normalizeGroup(it) }
    }

    private fun normalizeGroup(group: CharacterGroupCard): CharacterGroupCard {
        val normalizedMembers = group.members
            .filter { it.characterCardId.isNotBlank() }
            .sortedBy { it.orderIndex }
            .mapIndexed { index, member ->
                GroupMemberConfig(
                    characterCardId = member.characterCardId,
                    orderIndex = index
                )
            }
        val now = System.currentTimeMillis()

        return group.copy(
            members = normalizedMembers,
            createdAt = if (group.createdAt > 0) group.createdAt else now,
            updatedAt = if (group.updatedAt > 0) group.updatedAt else now
        )
    }

    private fun groupDataKey(groupId: String): Preferences.Key<String> {
        return stringPreferencesKey("character_group_${groupId}_data")
    }

    private suspend fun createDefaultBindingsForCharacterGroup(
        group: CharacterGroupCard,
        emojiSourcePrompt: ActivePrompt
    ) {
        runCatching { userPreferencesManager.deleteCharacterGroupTheme(group.id) }
        runCatching { waifuPreferences.copyCurrentWaifuSettingsToCharacterGroup(group.id) }
        runCatching { customEmojiRepository.cloneEmojiSet(emojiSourcePrompt, ActivePrompt.CharacterGroup(group.id)) }
        runCatching {
            val avatarUri = buildDefaultGroupAvatar(group)
            userPreferencesManager.saveAiAvatarForCharacterGroup(group.id, avatarUri)
        }
    }

    private suspend fun resolveEmojiSourcePrompt(): ActivePrompt {
        val activeGroupId = observeActiveCharacterGroupId().first()
        if (!activeGroupId.isNullOrBlank()) {
            return ActivePrompt.CharacterGroup(activeGroupId)
        }

        val activeCardId = characterCardManager.observeActiveCharacterCardId().first()
        return ActivePrompt.CharacterCard(activeCardId ?: CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    }

    private fun hasMemberRosterChanged(
        oldGroup: CharacterGroupCard?,
        newGroup: CharacterGroupCard
    ): Boolean {
        if (oldGroup == null) return false
        val oldMemberIds = oldGroup.members
            .map { it.characterCardId }
            .filter { it.isNotBlank() }
            .sorted()
        val newMemberIds = newGroup.members
            .map { it.characterCardId }
            .filter { it.isNotBlank() }
            .sorted()
        return oldMemberIds != newMemberIds
    }

    private fun isManagedDefaultGroupAvatar(avatarUri: String?, groupId: String): Boolean {
        if (avatarUri.isNullOrBlank()) return false
        val uri = Uri.parse(avatarUri)
        if (uri.scheme?.lowercase() != "file") return false
        val fileName = File(uri.path ?: return false).name
        return fileName.startsWith("group_avatar_${groupId}_") && fileName.endsWith(".png")
    }

    private suspend fun regenerateDefaultAvatarForGroup(
        group: CharacterGroupCard,
        previousAvatarUri: String?
    ) {
        runCatching {
            val newAvatarUri = buildDefaultGroupAvatar(group)
            ActivePromptManager.getInstance(context).saveAiAvatarForPrompt(
                ActivePrompt.CharacterGroup(group.id),
                newAvatarUri,
            )

            if (!previousAvatarUri.isNullOrBlank() && previousAvatarUri != newAvatarUri) {
                val oldUri = Uri.parse(previousAvatarUri)
                if (oldUri.scheme?.lowercase() == "file") {
                    runCatching { File(oldUri.path ?: "").delete() }
                }
            }
        }
    }

    private suspend fun buildDefaultGroupAvatar(group: CharacterGroupCard): String? = withContext(Dispatchers.IO) {
        val memberIds = group.members
            .sortedBy { it.orderIndex }
            .map { it.characterCardId }
            .filter { it.isNotBlank() }
            .distinct()
            .take(9)

        data class Tile(val bitmap: Bitmap?, val label: String)
        val tiles = mutableListOf<Tile>()

        memberIds.forEach { cardId ->
            val card = runCatching { characterCardManager.getCharacterCard(cardId) }.getOrNull()
            val avatarUri = runCatching {
                userPreferencesManager.getAiAvatarForCharacterCardFlow(cardId).first()
            }.getOrNull()
            val avatarBitmap = avatarUri?.let { decodeAvatarBitmap(it) }
            tiles.add(Tile(bitmap = avatarBitmap, label = card?.name ?: "AI"))
        }

        if (tiles.isEmpty()) {
            tiles.add(Tile(bitmap = null, label = group.name.ifBlank { "Group" }))
        }

        val sizePx = 512
        val outerPadding = 24f
        val gap = 8f
        val tileCount = tiles.size.coerceIn(1, 9)
        val columns = when {
            tileCount <= 1 -> 1
            tileCount <= 4 -> 2
            else -> 3
        }
        val rows = ceil(tileCount / columns.toDouble()).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F3F4F6"))

        val gridWidth = sizePx - outerPadding * 2f - gap * (columns - 1)
        val gridHeight = sizePx - outerPadding * 2f - gap * (rows - 1)
        val tileWidth = gridWidth / columns
        val tileHeight = gridHeight / rows
        val tileSize = minOf(tileWidth, tileHeight)

        val actualGridWidth = columns * tileSize + gap * (columns - 1)
        val actualGridHeight = rows * tileSize + gap * (rows - 1)
        val startX = (sizePx - actualGridWidth) / 2f
        val startY = (sizePx - actualGridHeight) / 2f

        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = tileSize * 0.38f
        }

        tiles.take(tileCount).forEachIndexed { index, tile ->
            val row = index / columns
            val col = index % columns
            val left = startX + col * (tileSize + gap)
            val top = startY + row * (tileSize + gap)
            val right = left + tileSize
            val bottom = top + tileSize
            val dstRect = RectF(left, top, right, bottom)

            val src = tile.bitmap
            if (src != null) {
                val minSize = minOf(src.width, src.height)
                val srcLeft = (src.width - minSize) / 2
                val srcTop = (src.height - minSize) / 2
                val srcRect = Rect(srcLeft, srcTop, srcLeft + minSize, srcTop + minSize)
                canvas.drawBitmap(src, srcRect, dstRect, null)
            } else {
                val hue = (abs(tile.label.hashCode()) % 360).toFloat()
                placeholderPaint.color = Color.HSVToColor(floatArrayOf(hue, 0.35f, 0.82f))
                canvas.drawRoundRect(dstRect, tileSize * 0.14f, tileSize * 0.14f, placeholderPaint)
                val initial = tile.label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                val baseY = top + tileSize / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(initial, left + tileSize / 2f, baseY, textPaint)
            }
        }

        tiles.forEach { tile -> tile.bitmap?.recycle() }

        runCatching {
            val file = File(context.filesDir, "group_avatar_${group.id}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.flush()
            }
            Uri.fromFile(file).toString()
        }.getOrNull().also {
            bitmap.recycle()
        }
    }

    private fun decodeAvatarBitmap(uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        val uri = Uri.parse(uriString)
        return runCatching {
            when (uri.scheme?.lowercase()) {
                "content" -> context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
                "file" -> {
                    val path = uri.path
                    when {
                        path.isNullOrBlank() -> null
                        path.startsWith("/android_asset/") -> {
                            val assetPath = path.removePrefix("/android_asset/")
                            context.assets.open(assetPath).use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                        }
                        else -> BitmapFactory.decodeFile(path)
                    }
                }
                else -> null
            }
        }.getOrNull()
    }
}
