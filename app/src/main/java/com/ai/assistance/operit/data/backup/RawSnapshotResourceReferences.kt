package com.ai.assistance.operit.data.backup

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.first
import java.io.File

enum class RawSnapshotResourceOwnerType {
    GLOBAL,
    CHARACTER_CARD,
    CHARACTER_GROUP,
}

enum class RawSnapshotResourceKind {
    USER_AVATAR,
    AI_AVATAR,
    BACKGROUND,
    BUBBLE_USER,
    BUBBLE_AI,
    FONT_MAIN,
    FONT_USER,
    FONT_AI,
}

data class RawSnapshotResourceReference(
    val ownerType: RawSnapshotResourceOwnerType,
    val kind: RawSnapshotResourceKind,
    val uri: String,
    val localPath: String? = null,
    val ownerId: String? = null,
    val ownerName: String? = null,
)

interface RawSnapshotResourceReferenceProvider {
    suspend fun collectReferences(): Set<RawSnapshotResourceReference>
}

object EmptyRawSnapshotResourceReferenceProvider : RawSnapshotResourceReferenceProvider {
    override suspend fun collectReferences(): Set<RawSnapshotResourceReference> = emptySet()
}

/** Collects resources referenced by all recoverable character cards and groups. */
class DefaultRawSnapshotResourceReferenceProvider(
    context: Context,
) : RawSnapshotResourceReferenceProvider {
    private val appContext = context.applicationContext ?: context
    private val userPreferences = UserPreferencesManager.getInstance(appContext)
    private val displayPreferences = DisplayPreferencesManager.getInstance(appContext)
    private val characterCards = CharacterCardManager.getInstance(appContext)
    private val characterGroups = CharacterGroupCardManager.getInstance(appContext)

    override suspend fun collectReferences(): Set<RawSnapshotResourceReference> {
        val references = linkedSetOf<RawSnapshotResourceReference>()
        addReference(
            references,
            ownerType = RawSnapshotResourceOwnerType.GLOBAL,
            kind = RawSnapshotResourceKind.USER_AVATAR,
            uri = displayPreferences.globalUserAvatarUri.first(),
        )

        val storedCards = characterCards.getAllCharacterCards()
        val storedCardIds = storedCards.map { it.id }.toSet()
        storedCards.forEach { card ->
            collectCharacterCardReferences(
                references,
                card.id,
                card.name,
            )
        }
        // The built-in "default_character" is the app's always-present chat persona and is NOT
        // returned by getAllCharacterCards(). Its theme/background/avatar lives under the
        // character_card_theme_default_character_* keys, so it must be collected explicitly or
        // those current resources would be treated as unreferenced history and pruned.
        if (!storedCardIds.contains(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)) {
            collectCharacterCardReferences(
                references,
                CharacterCardManager.DEFAULT_CHARACTER_CARD_ID,
                CharacterCardManager.DEFAULT_CHARACTER_NAME,
            )
        }

        characterGroups.getAllCharacterGroupCards().forEach { group ->
            val owner = RawSnapshotResourceOwner(
                type = RawSnapshotResourceOwnerType.CHARACTER_GROUP,
                id = group.id,
                name = group.name,
            )
            addReference(
                references,
                owner,
                RawSnapshotResourceKind.AI_AVATAR,
                userPreferences.getAiAvatarForCharacterGroupFlow(group.id).first(),
            )
            addThemeReferences(
                references,
                userPreferences.resolveThemePreferenceSnapshot(characterGroupId = group.id),
                owner,
            )
        }

        return references
    }

    private suspend fun collectCharacterCardReferences(
        references: MutableSet<RawSnapshotResourceReference>,
        cardId: String,
        cardName: String,
    ) {
        val owner = RawSnapshotResourceOwner(
            type = RawSnapshotResourceOwnerType.CHARACTER_CARD,
            id = cardId,
            name = cardName,
        )
        addReference(
            references,
            owner,
            RawSnapshotResourceKind.USER_AVATAR,
            userPreferences.resolveThemePreferenceSnapshot(characterCardId = cardId).customUserAvatarUri,
        )
        addReference(
            references,
            owner,
            RawSnapshotResourceKind.AI_AVATAR,
            userPreferences.getAiAvatarForCharacterCardFlow(cardId).first(),
        )
        addThemeReferences(
            references,
            userPreferences.resolveThemePreferenceSnapshot(characterCardId = cardId),
            owner,
        )
    }

    private fun addThemeReferences(
        references: MutableSet<RawSnapshotResourceReference>,
        snapshot: ThemePreferenceSnapshot,
        owner: RawSnapshotResourceOwner,
    ) {
        if (snapshot.useBackgroundImage) {
            addReference(references, owner, RawSnapshotResourceKind.BACKGROUND, snapshot.backgroundImageUri)
        }
        if (snapshot.bubbleUserUseImage) {
            addReference(references, owner, RawSnapshotResourceKind.BUBBLE_USER, snapshot.bubbleUserImageUri)
        }
        if (snapshot.bubbleAiUseImage) {
            addReference(references, owner, RawSnapshotResourceKind.BUBBLE_AI, snapshot.bubbleAiImageUri)
        }
        if (snapshot.useCustomFont) {
            addReference(references, owner, RawSnapshotResourceKind.FONT_MAIN, snapshot.customFontPath)
        }
        if (snapshot.bubbleUserUseCustomFont) {
            addReference(references, owner, RawSnapshotResourceKind.FONT_USER, snapshot.bubbleUserCustomFontPath)
        }
        if (snapshot.bubbleAiUseCustomFont) {
            addReference(references, owner, RawSnapshotResourceKind.FONT_AI, snapshot.bubbleAiCustomFontPath)
        }
    }

    private fun addReference(
        references: MutableSet<RawSnapshotResourceReference>,
        ownerType: RawSnapshotResourceOwnerType,
        kind: RawSnapshotResourceKind,
        uri: String?,
        ownerId: String? = null,
        ownerName: String? = null,
    ) {
        addReference(
            references,
            RawSnapshotResourceOwner(ownerType, ownerId, ownerName),
            kind,
            uri,
        )
    }

    private fun addReference(
        references: MutableSet<RawSnapshotResourceReference>,
        owner: RawSnapshotResourceOwner,
        kind: RawSnapshotResourceKind,
        uri: String?,
    ) {
        val normalizedUri = uri?.trim().orEmpty()
        if (normalizedUri.isBlank() || normalizedUri.startsWith("file:///android_asset/")) return
        references += RawSnapshotResourceReference(
            ownerType = owner.type,
            kind = kind,
            uri = normalizedUri,
            localPath = resolveLocalPath(normalizedUri),
            ownerId = owner.id,
            ownerName = owner.name,
        )
    }

    private fun resolveLocalPath(uriString: String): String? {
        val uri = Uri.parse(uriString)
        val path = when (uri.scheme?.lowercase()) {
            null, "" -> uriString
            "file" -> uri.path
            else -> null
        } ?: return null
        return runCatching { File(path).canonicalPath }.getOrNull()
    }

    private data class RawSnapshotResourceOwner(
        val type: RawSnapshotResourceOwnerType,
        val id: String? = null,
        val name: String? = null,
    )
}

object RawSnapshotResourceLayout {
    const val ROOT = "payload/resources/"

    fun directoryFor(reference: RawSnapshotResourceReference): String {
        val ownerDirectory = when (reference.ownerType) {
            RawSnapshotResourceOwnerType.GLOBAL -> "global"
            RawSnapshotResourceOwnerType.CHARACTER_CARD ->
                "character_cards/${ownerDirectoryName(reference)}"
            RawSnapshotResourceOwnerType.CHARACTER_GROUP ->
                "character_groups/${ownerDirectoryName(reference)}"
        }
        return "$ROOT$ownerDirectory/"
    }

    fun fileName(reference: RawSnapshotResourceReference, extension: String): String {
        val kindName = when (reference.kind) {
            RawSnapshotResourceKind.USER_AVATAR -> "user_avatar"
            RawSnapshotResourceKind.AI_AVATAR -> "ai_avatar"
            RawSnapshotResourceKind.BACKGROUND -> "background"
            RawSnapshotResourceKind.BUBBLE_USER -> "bubble_user"
            RawSnapshotResourceKind.BUBBLE_AI -> "bubble_ai"
            RawSnapshotResourceKind.FONT_MAIN -> "font_main"
            RawSnapshotResourceKind.FONT_USER -> "font_user"
            RawSnapshotResourceKind.FONT_AI -> "font_ai"
        }
        val ownerSegment = when (reference.ownerType) {
            RawSnapshotResourceOwnerType.GLOBAL -> null
            else -> {
                val name = sanitizeSegment(reference.ownerName ?: "unnamed")
                val id = sanitizeSegment(reference.ownerId ?: "unknown").takeLast(8)
                "${name}_$id"
            }
        }
        val stem = if (ownerSegment.isNullOrBlank()) kindName else "${kindName}_$ownerSegment"
        val suffix = extension.trim().trimStart('.').lowercase().ifBlank { "bin" }
        return "$stem.$suffix"
    }

    private fun ownerDirectoryName(reference: RawSnapshotResourceReference): String {
        val name = sanitizeSegment(reference.ownerName ?: "unnamed")
        val id = sanitizeSegment(reference.ownerId ?: "unknown").takeLast(12)
        return "${name}_$id"
    }

    private fun sanitizeSegment(value: String): String {
        return value
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
            .trim('_', '.', ' ')
            .take(48)
            .ifBlank { "unnamed" }
    }
}
