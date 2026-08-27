package com.ai.assistance.operit.integrations.http

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.R
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.integrations.http.bridge.WebChatActionBridge
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.integrations.http.bridge.WebChatInputSettingsBridge
import com.ai.assistance.operit.integrations.http.bridge.WebChatMemorySelectorBridge
import com.ai.assistance.operit.integrations.http.bridge.WebChatManagementBridge
import com.ai.assistance.operit.integrations.externalchat.ExternalChatResponseSanitizer
import com.ai.assistance.operit.services.core.MAX_DISPLAY_PAGE_COUNT
import com.ai.assistance.operit.services.core.resolveDisplayPageRanges
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.StructuredAssistantContentParser
import com.ai.assistance.operit.ui.theme.resolveThemeColorScheme
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URLConnection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.util.stream.SharedStream
import androidx.compose.ui.graphics.toArgb

class WebChatHttpBridge(
    context: Context,
    private val preferences: ExternalHttpApiPreferences,
    private val serviceScope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val runtimeHolder = ChatRuntimeHolder.getInstance(appContext)
    private val core = runtimeHolder.getCore(ChatRuntimeSlot.MAIN)
    private val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
    private val userPreferencesManager = UserPreferencesManager.getInstance(appContext)
    private val displayPreferencesManager = DisplayPreferencesManager.getInstance(appContext)
    private val activePromptManager = ActivePromptManager.getInstance(appContext)
    private val characterCardManager = CharacterCardManager.getInstance(appContext)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(appContext)
    private val functionalConfigManager = FunctionalConfigManager(appContext)
    private val modelConfigManager = ModelConfigManager(appContext)
    private val inputSettingsBridge = WebChatInputSettingsBridge(appContext, core)
    private val memorySelectorBridge = WebChatMemorySelectorBridge(appContext)
    private val actionBridge = WebChatActionBridge(core)
    private val chatManagementBridge =
        WebChatManagementBridge(core, chatHistoryManager, activePromptManager)
    private val assetIdBySource = ConcurrentHashMap<String, String>()
    private val assetsById = ConcurrentHashMap<String, RegisteredAsset>()
    private val uploadsById = ConcurrentHashMap<String, UploadedAttachmentEntry>()

    fun handleApi(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        cleanupExpiredEntries()

        if (session.uri.startsWith(ASSET_ROUTE_PREFIX)) {
            return handleRegisteredAsset(session).withCors()
        }

        val unauthorized = requireBearerToken(session)
        if (unauthorized != null) {
            return unauthorized
        }

        return when {
            session.uri == BOOTSTRAP_PATH && session.method == NanoHTTPD.Method.GET ->
                handleBootstrap()

            session.uri == CHARACTER_SELECTOR_PATH && session.method == NanoHTTPD.Method.GET ->
                handleCharacterSelector()

            session.uri == ACTIVE_PROMPT_PATH && session.method == NanoHTTPD.Method.POST ->
                handleSetActivePrompt(session)

            session.uri == MODEL_SELECTOR_PATH && session.method == NanoHTTPD.Method.GET ->
                handleModelSelector()

            session.uri == MODEL_SELECTOR_PATH && session.method == NanoHTTPD.Method.POST ->
                handleSelectModel(session)

            session.uri == MEMORY_SELECTOR_PATH && session.method == NanoHTTPD.Method.GET ->
                handleMemorySelector()

            session.uri == MEMORY_SELECTOR_PATH && session.method == NanoHTTPD.Method.POST ->
                handleSelectMemoryProfile(session)

            session.uri == INPUT_SETTINGS_PATH && session.method == NanoHTTPD.Method.GET ->
                handleInputSettings()

            session.uri == INPUT_SETTINGS_PATH && session.method == NanoHTTPD.Method.PATCH ->
                handleUpdateInputSettings(session)

            session.uri == MANUAL_MEMORY_UPDATE_PATH && session.method == NanoHTTPD.Method.POST ->
                handleManualMemoryUpdate()

            session.uri == MANUAL_CONVERSATION_SUMMARY_PATH && session.method == NanoHTTPD.Method.POST ->
                handleManualConversationSummary()

            session.uri == CHATS_PATH && session.method == NanoHTTPD.Method.GET ->
                handleListChats()

            session.uri == CHATS_PATH && session.method == NanoHTTPD.Method.POST ->
                handleCreateChat(session)

            session.uri == CHATS_REORDER_PATH && session.method == NanoHTTPD.Method.POST ->
                handleReorderChats(session)

            session.uri == CHAT_GROUP_RENAME_PATH && session.method == NanoHTTPD.Method.POST ->
                handleRenameGroup(session)

            session.uri == CHAT_GROUP_DELETE_PATH && session.method == NanoHTTPD.Method.POST ->
                handleDeleteGroup(session)

            chatIdFrom(session.uri, CHATS_PATH)?.let { chatId ->
                session.uri == "$CHATS_PATH/$chatId"
            } == true && session.method == NanoHTTPD.Method.PATCH ->
                handleUpdateChat(session, requireNotNull(chatIdFrom(session.uri, CHATS_PATH)))

            chatIdFrom(session.uri, CHATS_PATH)?.let { chatId ->
                session.uri == "$CHATS_PATH/$chatId"
            } == true && session.method == NanoHTTPD.Method.DELETE ->
                handleDeleteChat(requireNotNull(chatIdFrom(session.uri, CHATS_PATH)))

            chatIdFrom(session.uri, "$CHATS_PATH/", "/select") != null &&
                session.method == NanoHTTPD.Method.POST ->
                handleSelectChat(
                    requireNotNull(chatIdFrom(session.uri, "$CHATS_PATH/", "/select"))
                )

            chatIdFrom(session.uri, "$CHATS_PATH/", "/messages") != null &&
                session.method == NanoHTTPD.Method.GET ->
                handleMessages(
                    session,
                    requireNotNull(chatIdFrom(session.uri, "$CHATS_PATH/", "/messages"))
                )

            chatIdFrom(session.uri, "$CHATS_PATH/", "/message-locator") != null &&
                session.method == NanoHTTPD.Method.GET ->
                handleMessageLocator(
                    session,
                    requireNotNull(chatIdFrom(session.uri, "$CHATS_PATH/", "/message-locator"))
                )

            chatIdFrom(session.uri, "$CHATS_PATH/", "/messages/reveal") != null &&
                session.method == NanoHTTPD.Method.POST ->
                handleRevealMessage(
                    session,
                    requireNotNull(chatIdFrom(session.uri, "$CHATS_PATH/", "/messages/reveal"))
                )

            chatIdFrom(session.uri, "$CHATS_PATH/", "/messages/favorite") != null &&
                session.method == NanoHTTPD.Method.PATCH ->
                handleToggleMessageFavorite(
                    session,
                    requireNotNull(chatIdFrom(session.uri, "$CHATS_PATH/", "/messages/favorite"))
                )

            chatIdFrom(session.uri, "$CHATS_PATH/", "/theme") != null &&
                session.method == NanoHTTPD.Method.GET ->
                handleTheme(
                    requireNotNull(chatIdFrom(session.uri, "$CHATS_PATH/", "/theme"))
                )

            chatIdFrom(session.uri, "$CHATS_PATH/", "/messages/stream") != null &&
                session.method == NanoHTTPD.Method.POST ->
                handleStream(
                    session,
                    requireNotNull(chatIdFrom(session.uri, "$CHATS_PATH/", "/messages/stream"))
                )

            session.uri == UPLOADS_PATH && session.method == NanoHTTPD.Method.POST ->
                handleUpload(session)

            else ->
                jsonResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    WebErrorResponse("API endpoint not found")
                )
        }.withCors()
    }

    fun serveStatic(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        cleanupExpiredEntries()

        if (session.method != NanoHTTPD.Method.GET && session.method != NanoHTTPD.Method.HEAD) {
            return plainTextResponse(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "Method not allowed"
            ).withCors()
        }

        val requestedPath = normalizeStaticPath(session.uri)
            ?: return plainTextResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "Access denied"
            ).withCors()

        val assetPath = resolvePackagedAssetPath(requestedPath)
        val staticAsset = openPackagedAsset(assetPath)
        if (staticAsset == null) {
            return plainTextResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "Web assets are not available in app assets/web-chat"
            ).withCors()
        }

        return byteArrayResponse(
            status = NanoHTTPD.Response.Status.OK,
            mimeType = staticAsset.mimeType,
            bytes = staticAsset.bytes
        ).withCors()
    }

    private fun handleBootstrap(): NanoHTTPD.Response {
        val currentChatId = core.currentChatId.value
        val snapshot = runBlocking {
            val chat = if (currentChatId == null) null else currentChatMeta(currentChatId)
            resolveThemePreferenceSnapshot(chat)
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            WebBootstrapResponse(
                versionName = BuildConfig.VERSION_NAME,
                currentChatId = currentChatId,
                defaultChatStyle = snapshot.chatStyle,
                defaultInputStyle = snapshot.inputStyle,
                showThinkingProcess = snapshot.showThinkingProcess,
                showStatusTags = snapshot.showStatusTags,
                showInputProcessingStatus = snapshot.showInputProcessingStatus,
                capabilities = WebCapabilities(
                    attachments = true,
                    perChatTheme = true,
                    structuredRender = true,
                    streaming = true,
                    renameChat = true,
                    deleteChat = true
                )
            )
        )
    }

    private fun handleCharacterSelector(): NanoHTTPD.Response {
        val response = runBlocking { buildCharacterSelectorResponse() }
        return jsonResponse(NanoHTTPD.Response.Status.OK, response)
    }

    private fun handleSetActivePrompt(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebSetActivePromptRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )

        val targetType = request.type.trim().lowercase(Locale.US)
        val targetId = request.id.trim()
        if (targetId.isBlank()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Missing active prompt id")
            )
        }

        val response = runBlocking {
            when (targetType) {
                ACTIVE_PROMPT_TYPE_CHARACTER_CARD -> {
                    val cardExists = characterCardManager.getAllCharacterCards().any { it.id == targetId }
                    if (!cardExists) {
                        return@runBlocking null
                    }
                    activePromptManager.setActivePrompt(ActivePrompt.CharacterCard(targetId))
                }

                ACTIVE_PROMPT_TYPE_CHARACTER_GROUP -> {
                    val groupExists = characterGroupCardManager.getCharacterGroupCard(targetId) != null
                    if (!groupExists) {
                        return@runBlocking null
                    }
                    activePromptManager.setActivePrompt(ActivePrompt.CharacterGroup(targetId))
                }

                else -> return@runBlocking null
            }

            buildCharacterSelectorResponse()
        }

        return when {
            targetType != ACTIVE_PROMPT_TYPE_CHARACTER_CARD &&
                targetType != ACTIVE_PROMPT_TYPE_CHARACTER_GROUP ->
                jsonResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    WebErrorResponse("Unsupported active prompt type: $targetType")
                )

            response == null ->
                jsonResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    WebErrorResponse("Active prompt target not found")
                )

            else ->
                jsonResponse(NanoHTTPD.Response.Status.OK, response)
        }
    }

    private fun handleListChats(): NanoHTTPD.Response {
        val chats = runBlocking {
            val histories = chatHistoryManager.chatHistoriesFlow.first()
            val characterGroupNamesById = resolveCharacterGroupNames(histories)
            val bindingAvatarUrlByChatId = resolveBindingAvatarUrls(histories)

            histories.map { history ->
                buildChatSummary(
                    history = history,
                    characterGroupName = history.characterGroupId?.let(characterGroupNamesById::get),
                    bindingAvatarUrl = bindingAvatarUrlByChatId[history.id]
                )
            }
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, chats)
    }

    private fun handleModelSelector(): NanoHTTPD.Response {
        val selector = runBlocking { resolveModelSelectorState() }
        return jsonResponse(NanoHTTPD.Response.Status.OK, selector)
    }

    private fun handleSelectModel(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebSelectModelRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )

        val selectedId = request.configId.trim()
        if (selectedId.isBlank()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Missing config_id")
            )
        }

        val response = runBlocking {
            functionalConfigManager.initializeIfNeeded()
            modelConfigManager.initializeIfNeeded()

            val selectorBefore = resolveModelSelectorState()
            val targetConfig = selectorBefore.configs.firstOrNull { it.id == selectedId }
                ?: return@runBlocking null
            val normalizedModelIndex = if (targetConfig.models.isEmpty()) {
                0
            } else {
                getValidModelIndex(targetConfig.modelName, request.modelIndex)
            }
            val isSameSelection =
                selectorBefore.currentConfigId == selectedId &&
                    selectorBefore.currentModelIndex == normalizedModelIndex

            if (
                selectorBefore.lockedByCharacterCard &&
                    !isSameSelection &&
                    !request.confirmCharacterCardSwitch
            ) {
                return@runBlocking WebSelectModelResponse(
                    success = false,
                    requiresCharacterCardSwitchConfirmation = true,
                    selector = selectorBefore
                )
            }

            if (!isSameSelection) {
                if (selectorBefore.lockedByCharacterCard) {
                    val activePrompt = activePromptManager.getActivePrompt()
                    if (activePrompt !is ActivePrompt.CharacterCard) {
                        return@runBlocking null
                    }
                    val activeCard = characterCardManager.getCharacterCard(activePrompt.id)
                    characterCardManager.updateCharacterCard(
                        activeCard.copy(
                            chatModelBindingMode = CharacterCardChatModelBindingMode.FIXED_CONFIG,
                            chatModelConfigId = selectedId,
                            chatModelIndex = normalizedModelIndex
                        )
                    )
                } else {
                    functionalConfigManager.setConfigForFunction(
                        FunctionType.CHAT,
                        selectedId,
                        normalizedModelIndex
                    )
                }
                EnhancedAIService.refreshServiceForFunction(appContext, FunctionType.CHAT)
            }

            WebSelectModelResponse(
                success = true,
                selector = resolveModelSelectorState()
            )
        }

        return if (response == null) {
            jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Model config not found")
            )
        } else {
            jsonResponse(NanoHTTPD.Response.Status.OK, response)
        }
    }

    private fun handleCreateChat(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebCreateChatRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )

        val created = runBlocking {
            val newChat = chatHistoryManager.createNewChat(
                group = request.group?.trim()?.takeIf { it.isNotBlank() },
                characterCardName = request.characterCardName?.trim()?.takeIf { it.isNotBlank() },
                characterGroupId = request.characterGroupId?.trim()?.takeIf { it.isNotBlank() },
                setAsCurrentChat = request.setCurrent
            )
            val normalizedTitle = request.title?.trim()?.takeIf { it.isNotBlank() }
            if (normalizedTitle != null) {
                chatHistoryManager.updateChatTitle(newChat.id, normalizedTitle)
            }
            if (request.setCurrent) {
                switchAppChatContext(newChat.id)
            }
            val updatedChat = currentChatMeta(newChat.id) ?: newChat
            buildChatSummary(updatedChat)
        }

        return jsonResponse(NanoHTTPD.Response.Status.OK, created)
    }

    private fun handleInputSettings(): NanoHTTPD.Response {
        val settings = runBlocking { inputSettingsBridge.resolveState() }
        return jsonResponse(NanoHTTPD.Response.Status.OK, settings)
    }

    private fun handleMemorySelector(): NanoHTTPD.Response {
        val selector = runBlocking { memorySelectorBridge.resolveState() }
        return jsonResponse(NanoHTTPD.Response.Status.OK, selector)
    }

    private fun handleSelectMemoryProfile(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebSelectMemoryProfileRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        val updated = runBlocking { memorySelectorBridge.selectProfile(request.profileId) }
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid profile_id")
            )
        return jsonResponse(NanoHTTPD.Response.Status.OK, updated)
    }

    private fun handleUpdateInputSettings(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebUpdateInputSettingsRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )

        val updated = runBlocking { inputSettingsBridge.update(request) }
        return jsonResponse(NanoHTTPD.Response.Status.OK, updated)
    }

    private fun handleManualMemoryUpdate(): NanoHTTPD.Response {
        actionBridge.manuallyUpdateMemory()
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            WebActionResponse(
                success = true,
                chatId = core.currentChatId.value
            )
        )
    }

    private fun handleManualConversationSummary(): NanoHTTPD.Response {
        actionBridge.manuallySummarizeConversation()
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            WebActionResponse(
                success = true,
                chatId = core.currentChatId.value
            )
        )
    }

    private fun handleUpdateChat(
        session: NanoHTTPD.IHTTPSession,
        chatId: String
    ): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val request = parseJsonRequest<WebUpdateChatRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        val hasTitleChange = request.title != null
        val hasGroupChange = request.updateGroup
        val hasLockedChange = request.updateLocked && request.locked != null
        val hasPinnedChange = request.updatePinned && request.pinned != null
        val hasBindingChange = request.updateBinding
        if (!hasTitleChange && !hasGroupChange && !hasLockedChange && !hasPinnedChange && !hasBindingChange) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("No update fields provided")
            )
        }

        val normalizedTitle = request.title?.trim()?.takeIf { it.isNotBlank() }
        if (hasTitleChange && normalizedTitle == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Missing title")
            )
        }

        val normalizedCharacterCardName = request.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCharacterGroupId = request.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        if (hasBindingChange && normalizedCharacterCardName != null && normalizedCharacterGroupId != null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Chat binding cannot target both a character card and a character group")
            )
        }

        val updated = runBlocking {
            chatManagementBridge.updateChat(
                chatId = chatId,
                request = request,
                currentChatMeta = ::currentChatMeta,
                buildChatSummary = ::buildChatSummary
            )
        }
        return if (updated != null) {
            jsonResponse(NanoHTTPD.Response.Status.OK, updated)
        } else {
            jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }
    }

    private fun handleReorderChats(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebReorderChatsRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        if (request.items.isEmpty()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("No reorder items provided")
            )
        }

        val success = runBlocking { chatManagementBridge.reorderChats(request.items) }

        return if (success) {
            jsonResponse(
                NanoHTTPD.Response.Status.OK,
                WebActionResponse(success = true)
            )
        } else {
            jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("One or more chats could not be found")
            )
        }
    }

    private fun handleRenameGroup(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebRenameGroupRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        val oldName = request.oldName.trim()
        val newName = request.newName.trim()
        if (oldName.isBlank() || newName.isBlank()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Group names must not be blank")
            )
        }

        runBlocking { chatManagementBridge.renameGroup(request) }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            WebActionResponse(success = true)
        )
    }

    private fun handleDeleteGroup(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = parseJsonRequest<WebDeleteGroupRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        val groupName = request.groupName.trim()
        if (groupName.isBlank()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Group name must not be blank")
            )
        }

        runBlocking { chatManagementBridge.deleteGroup(request) }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            WebActionResponse(success = true)
        )
    }

    private fun handleSelectChat(chatId: String): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val switched = runBlocking { switchAppChatContext(chatId) }
        return if (switched) {
            jsonResponse(
                NanoHTTPD.Response.Status.OK,
                WebActionResponse(success = true, chatId = chatId)
            )
        } else {
            jsonResponse(
                NanoHTTPD.Response.Status.CONFLICT,
                WebErrorResponse("Failed to switch chat context")
            )
        }
    }

    private fun handleDeleteChat(chatId: String): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        if (!runBlocking { chatHistoryManager.canDeleteChatHistory(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.CONFLICT,
                WebErrorResponse("Chat is locked and cannot be deleted")
            )
        }

        if (core.activeStreamingChatIds.value.contains(chatId)) {
            runBlocking { core.cancelMessageForDestructiveMutation(chatId) }
        }

        val deleted = runBlocking { chatHistoryManager.deleteChatHistory(chatId) }
        return if (deleted) {
            jsonResponse(
                NanoHTTPD.Response.Status.OK,
                WebActionResponse(success = true, chatId = chatId, deleted = true)
            )
        } else {
            jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                WebErrorResponse("Failed to delete chat")
            )
        }
    }

    private fun handleMessages(
        session: NanoHTTPD.IHTTPSession,
        chatId: String
    ): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val limit = session.parameters["limit"]
            ?.firstOrNull()
            ?.toIntOrNull()
            ?.coerceIn(1, MAX_MESSAGES_PAGE_SIZE)
            ?: DEFAULT_MESSAGES_PAGE_SIZE
        val beforeTimestamp = session.parameters["before_timestamp"]
            ?.firstOrNull()
            ?.toLongOrNull()
        val afterTimestamp = session.parameters["after_timestamp"]
            ?.firstOrNull()
            ?.toLongOrNull()
        if (beforeTimestamp != null && afterTimestamp != null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("before_timestamp and after_timestamp cannot both be provided")
            )
        }

        val page = runBlocking {
            val structuredRenderPreferences = resolveStructuredRenderPreferences(chatId)
            when {
                beforeTimestamp != null -> {
                    val fetchedMessages = chatHistoryManager.loadOlderChatMessages(
                        chatId = chatId,
                        beforeTimestampExclusive = beforeTimestamp,
                        limit = limit + 1
                    )
                    val hasMoreBefore = fetchedMessages.size > limit
                    val pageMessages = if (hasMoreBefore) fetchedMessages.takeLast(limit) else fetchedMessages
                    val hasMoreAfter = pageMessages.lastOrNull()?.timestamp?.let {
                        chatHistoryManager.hasMessagesAfter(chatId, it)
                    } ?: false
                    WebChatMessagesPage(
                        messages = pageMessages.map { message ->
                            buildWebMessage(chatId, message, structuredRenderPreferences = structuredRenderPreferences)
                        },
                        hasMoreBefore = hasMoreBefore,
                        hasMoreAfter = hasMoreAfter,
                        nextBeforeTimestamp = pageMessages.firstOrNull()?.timestamp,
                        nextAfterTimestamp = pageMessages.lastOrNull()?.timestamp
                    )
                }

                afterTimestamp != null -> {
                    val fetchedMessages = chatHistoryManager.loadChatMessagesAscAfter(
                        chatId = chatId,
                        afterTimestampExclusive = afterTimestamp,
                        limit = limit + 1
                    )
                    val hasMoreAfter = fetchedMessages.size > limit
                    val pageMessages = fetchedMessages.take(limit)
                    val hasMoreBefore = pageMessages.firstOrNull()?.timestamp?.let {
                        chatHistoryManager.hasMessagesBefore(chatId, it)
                    } ?: false
                    WebChatMessagesPage(
                        messages = pageMessages.map { message ->
                            buildWebMessage(chatId, message, structuredRenderPreferences = structuredRenderPreferences)
                        },
                        hasMoreBefore = hasMoreBefore,
                        hasMoreAfter = hasMoreAfter,
                        nextBeforeTimestamp = pageMessages.firstOrNull()?.timestamp,
                        nextAfterTimestamp = pageMessages.lastOrNull()?.timestamp
                    )
                }

                else -> {
                    val fetchedMessagesDesc = chatHistoryManager.loadChatMessagesDesc(
                        chatId = chatId,
                        limit = limit + 1
                    )
                    val hasMoreBefore = fetchedMessagesDesc.size > limit
                    val pageMessages =
                        fetchedMessagesDesc
                            .take(limit)
                            .asReversed()
                    WebChatMessagesPage(
                        messages = pageMessages.map { message ->
                            buildWebMessage(chatId, message, structuredRenderPreferences = structuredRenderPreferences)
                        },
                        hasMoreBefore = hasMoreBefore,
                        hasMoreAfter = false,
                        nextBeforeTimestamp = pageMessages.firstOrNull()?.timestamp,
                        nextAfterTimestamp = pageMessages.lastOrNull()?.timestamp
                    )
                }
            }
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, page)
    }

    private fun handleMessageLocator(
        session: NanoHTTPD.IHTTPSession,
        chatId: String
    ): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val query = session.parameters["query"]?.firstOrNull().orEmpty()
        val previews = runBlocking {
            chatHistoryManager.loadChatMessageLocatorPreviews(chatId, query)
                .map(::buildWebMessageLocatorPreview)
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, previews)
    }

    private fun handleRevealMessage(
        session: NanoHTTPD.IHTTPSession,
        chatId: String
    ): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val request = parseJsonRequest<WebRevealMessageRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        val targetTimestamp = request.timestamp
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Missing timestamp")
            )

        val page = runBlocking {
            val locatorEntries = chatHistoryManager.loadChatMessageLocatorPreviews(chatId)
            val pageRanges = resolveDisplayPageRanges(locatorEntries)
            val targetPageIndex =
                pageRanges.indexOfFirst { range ->
                    targetTimestamp in range.startTimestampInclusive..range.endTimestampInclusive
                }
            if (targetPageIndex < 0) {
                null
            } else {
                val windowStartPageIndex =
                    if (targetPageIndex < pageRanges.lastIndex) {
                        targetPageIndex
                    } else {
                        (targetPageIndex - (MAX_DISPLAY_PAGE_COUNT - 1)).coerceAtLeast(0)
                    }
                val windowEndPageIndex =
                    (windowStartPageIndex + MAX_DISPLAY_PAGE_COUNT - 1).coerceAtMost(pageRanges.lastIndex)
                val revealedMessages =
                    chatHistoryManager.loadChatMessagesWindow(
                        chatId = chatId,
                        startTimestampInclusive = pageRanges[windowStartPageIndex].startTimestampInclusive,
                        endTimestampInclusive = pageRanges[windowEndPageIndex].endTimestampInclusive
                    )
                val structuredRenderPreferences = resolveStructuredRenderPreferences(chatId)
                WebChatMessagesPage(
                    messages = revealedMessages.map { message ->
                        buildWebMessage(
                            chatId,
                            message,
                            structuredRenderPreferences = structuredRenderPreferences
                        )
                    },
                    hasMoreBefore = windowStartPageIndex > 0,
                    hasMoreAfter = windowEndPageIndex < pageRanges.lastIndex,
                    nextBeforeTimestamp = revealedMessages.firstOrNull()?.timestamp,
                    nextAfterTimestamp = revealedMessages.lastOrNull()?.timestamp
                )
            }
        }

        return if (page != null) {
            jsonResponse(NanoHTTPD.Response.Status.OK, page)
        } else {
            jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Message not found")
            )
        }
    }

    private fun handleToggleMessageFavorite(
        session: NanoHTTPD.IHTTPSession,
        chatId: String
    ): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val request = parseJsonRequest<WebToggleMessageFavoriteRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        val timestamp = request.timestamp
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Missing timestamp")
            )
        val isFavorite = request.isFavorite
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Missing favorite state")
            )

        runBlocking {
            chatHistoryManager.setMessageFavorite(chatId, timestamp, isFavorite)
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            WebActionResponse(success = true, chatId = chatId)
        )
    }

    private fun handleTheme(chatId: String): NanoHTTPD.Response {
        val chat = runBlocking { currentChatMeta(chatId) }
        if (chat == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val snapshot = runBlocking {
            val resolved = resolveThemePreferenceSnapshot(chat)
            val display = resolveDisplayPreferencesSnapshot(resolved)
            buildThemeSnapshot(resolved, display)
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, snapshot)
    }

    private fun handleUpload(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val contentType = session.headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value.orEmpty()
        if (!contentType.contains("multipart/form-data", ignoreCase = true)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Content-Type must be multipart/form-data")
            )
        }

        val tempFiles = HashMap<String, String>()
        return try {
            session.parseBody(tempFiles)
            val fileEntry = tempFiles.entries.firstOrNull { it.key != "postData" }
                ?: return jsonResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    WebErrorResponse("No file found in upload body")
                )

            val tempFile = File(fileEntry.value)
            if (!tempFile.exists()) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    WebErrorResponse("Uploaded file payload is missing")
                )
            }
            if (tempFile.length() > MAX_UPLOAD_BYTES) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    WebErrorResponse("File exceeds maximum upload size of ${MAX_UPLOAD_BYTES / (1024 * 1024)}MB")
                )
            }

            val originalName = session.parameters[fileEntry.key]
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "upload_${System.currentTimeMillis()}"
            val safeName = sanitizeFilename(originalName)
            val mimeType = guessMimeType(safeName)
            val attachmentId = UUID.randomUUID().toString()
            val targetDir = File(appContext.cacheDir, "external_http_uploads")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val storedFile = File(targetDir, "${attachmentId}_$safeName")
            tempFile.copyTo(storedFile, overwrite = true)

            val attachmentInfo = AttachmentInfo(
                filePath = storedFile.absolutePath,
                fileName = safeName,
                mimeType = mimeType,
                fileSize = storedFile.length()
            )
            uploadsById[attachmentId] = UploadedAttachmentEntry(
                attachment = attachmentInfo,
                storedFile = storedFile,
                createdAt = System.currentTimeMillis()
            )

            jsonResponse(
                NanoHTTPD.Response.Status.OK,
                WebUploadedAttachment(
                    attachmentId = attachmentId,
                    fileName = safeName,
                    mimeType = mimeType,
                    fileSize = storedFile.length()
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to process web upload", e)
            jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                WebErrorResponse("Failed to process upload: ${e.message ?: "unknown error"}")
            )
        }
    }

    private fun handleStream(
        session: NanoHTTPD.IHTTPSession,
        chatId: String
    ): NanoHTTPD.Response {
        if (!runBlocking { chatHistoryManager.chatExists(chatId) }) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Chat not found")
            )
        }

        val request = parseJsonRequest<WebSendMessageRequest>(session)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Invalid JSON body")
            )
        val messageText = request.message?.trim().orEmpty()
        if (messageText.isBlank() && request.attachmentIds.isEmpty()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("Message or attachment_ids is required")
            )
        }

        val attachments = request.attachmentIds.mapNotNull { attachmentId ->
            uploadsById[attachmentId]?.attachment
        }
        if (attachments.size != request.attachmentIds.size) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                WebErrorResponse("One or more attachment_ids are invalid or expired")
            )
        }

        val pipeInput = PipedInputStream(SSE_PIPE_BUFFER_SIZE)
        val pipeOutput = PipedOutputStream(pipeInput)
        val activeChatId = AtomicReference(chatId)
        val streamJob: Job = serviceScope.launch(Dispatchers.IO) {
            pipeOutput.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                try {
                    val switched = switchAppChatContext(
                        chatId = chatId,
                        syncActivePromptFromBinding = core.currentChatId.value != chatId
                    )
                    if (!switched) {
                        writeSseEvent(
                            writer,
                            WebChatStreamEvent(
                                event = STREAM_EVENT_ERROR,
                                chatId = chatId,
                                error = "Failed to switch chat context"
                            )
                        )
                        return@use
                    }
                    val structuredRenderPreferences = resolveStructuredRenderPreferences(chatId)

                    core.clearAttachments()
                    val addedAttachments = core.getAttachmentDelegate().addAttachments(attachments)
                    core.updateUserMessage(messageText)

                    val optimisticTimestamp = System.currentTimeMillis()
                    val optimisticUserMessage = WebChatMessage(
                        id = "$chatId:user:$optimisticTimestamp",
                        sender = "user",
                        contentRaw = messageText,
                        timestamp = optimisticTimestamp,
                        displayContent = messageText,
                        attachments = addedAttachments.map { attachment ->
                            WebMessageAttachment(
                                id = attachment.filePath,
                                fileName = attachment.fileName,
                                mimeType = attachment.mimeType,
                                fileSize = attachment.fileSize,
                                assetUrl = registerAsset(attachment.filePath, attachment.mimeType)
                            )
                        }
                    )

                    writeSseEvent(
                        writer,
                        WebChatStreamEvent(
                            event = STREAM_EVENT_START,
                            chatId = chatId
                        )
                    )
                    writeSseEvent(
                        writer,
                        WebChatStreamEvent(
                            event = STREAM_EVENT_USER_MESSAGE,
                            chatId = chatId,
                            message = optimisticUserMessage
                        )
                    )

                    core.sendUserMessage(preferActiveRoleCard = true)

                    val responseStream: SharedStream<String>? =
                        withTimeoutOrNull<SharedStream<String>>(STREAM_READY_TIMEOUT_MS) {
                            var stream: SharedStream<String>? = null
                            while (stream == null) {
                                stream = core.getResponseStream(chatId)
                                if (stream == null) {
                                    delay(40)
                                }
                            }
                            return@withTimeoutOrNull stream
                        }

                    if (responseStream == null) {
                        writeSseEvent(
                            writer,
                            WebChatStreamEvent(
                                event = STREAM_EVENT_ERROR,
                                chatId = chatId,
                                error = "Timed out while waiting for response stream"
                            )
                        )
                        return@use
                    }

                    val streamToCollect = ExternalChatResponseSanitizer.sanitizeStream(
                        responseStream,
                        request.returnToolStatus
                    )
                    val assistantContent = StringBuilder()
                    val streamingAssistantTimestamp = System.currentTimeMillis() + 1
                    streamToCollect.collect { chunk ->
                        if (chunk.isEmpty()) {
                            return@collect
                        }
                        assistantContent.append(chunk)
                        writeSseEvent(
                            writer,
                            WebChatStreamEvent(
                                event = STREAM_EVENT_ASSISTANT_DELTA,
                                chatId = chatId,
                                delta = chunk,
                                message = buildStreamingAssistantMessage(
                                    chatId = chatId,
                                    timestamp = streamingAssistantTimestamp,
                                    content = assistantContent.toString(),
                                    structuredRenderPreferences = structuredRenderPreferences
                                )
                            )
                        )
                    }

                    when (val finalState = awaitFinalState(chatId)) {
                        is InputProcessingState.Error -> {
                            writeSseEvent(
                                writer,
                                WebChatStreamEvent(
                                    event = STREAM_EVENT_ERROR,
                                    chatId = chatId,
                                    error = finalState.message
                                )
                            )
                        }

                        else -> {
                            val finalMessage = latestAssistantMessage(chatId, request.returnToolStatus)
                            writeSseEvent(
                                writer,
                                WebChatStreamEvent(
                                    event = STREAM_EVENT_ASSISTANT_DONE,
                                    chatId = chatId,
                                    message = finalMessage
                                )
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    core.cancelMessage(activeChatId.get())
                    throw e
                } catch (e: IOException) {
                    AppLogger.i(TAG, "Web SSE client disconnected for chatId=$chatId")
                    core.cancelMessage(activeChatId.get())
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Web SSE stream failed for chatId=$chatId", e)
                    runCatching {
                        writeSseEvent(
                            writer,
                            WebChatStreamEvent(
                                event = STREAM_EVENT_ERROR,
                                chatId = chatId,
                                error = e.message ?: "Unknown error"
                            )
                        )
                    }
                    core.cancelMessage(activeChatId.get())
                }
            }
        }

        val responseInput = object : FilterInputStream(pipeInput) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    streamJob.cancel()
                    core.cancelMessage(activeChatId.get())
                }
            }
        }

        return NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK,
            SSE_MIME_TYPE,
            responseInput
        ).apply {
            addHeader("Cache-Control", "no-cache")
            addHeader("Connection", "keep-alive")
            addHeader("X-Accel-Buffering", "no")
        }
    }

    private fun handleRegisteredAsset(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.GET && session.method != NanoHTTPD.Method.HEAD) {
            return plainTextResponse(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "Method not allowed"
            )
        }

        val assetId = session.uri.removePrefix("$ASSET_ROUTE_PREFIX/")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Asset not found")
            )

        val entry = assetsById[assetId]
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Asset not found")
            )
        val payload = readRegisteredAsset(entry)
            ?: return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                WebErrorResponse("Asset content is unavailable")
            )

        return byteArrayResponse(
            status = NanoHTTPD.Response.Status.OK,
            mimeType = payload.mimeType,
            bytes = payload.bytes
        ).apply {
            addHeader("Cache-Control", "no-store")
        }
    }

    private suspend fun buildChatSummary(history: ChatHistory): WebChatSummary {
        val characterGroupName = resolveCharacterGroupName(history.characterGroupId)
        val bindingAvatarUrl = resolveBindingAvatarUrls(listOf(history))[history.id]
        return buildChatSummary(history, characterGroupName, bindingAvatarUrl)
    }

    private fun buildChatSummary(
        history: ChatHistory,
        characterGroupName: String?,
        bindingAvatarUrl: String?
    ): WebChatSummary {
        return WebChatSummary(
            id = history.id,
            title = history.title,
            updatedAt = history.updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            group = history.group,
            characterCardName = history.characterCardName,
            characterGroupId = history.characterGroupId,
            characterGroupName = characterGroupName,
            bindingAvatarUrl = bindingAvatarUrl,
            parentChatId = history.parentChatId,
            activeStreaming = core.activeStreamingChatIds.value.contains(history.id),
            locked = history.locked,
            pinned = history.pinned
        )
    }

    private suspend fun resolveCharacterGroupName(groupId: String?): String? {
        val normalizedGroupId = groupId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            characterGroupCardManager.getCharacterGroupCard(normalizedGroupId)?.name
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveCharacterGroupNames(histories: List<ChatHistory>): Map<String, String> {
        val groupIds = histories
            .mapNotNull { it.characterGroupId?.trim()?.takeIf { groupId -> groupId.isNotBlank() } }
            .toSet()
        if (groupIds.isEmpty()) {
            return emptyMap()
        }

        return try {
            characterGroupCardManager.getAllCharacterGroupCards()
                .asSequence()
                .filter { it.id in groupIds }
                .associate { it.id to it.name }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun resolveBindingAvatarUrls(histories: List<ChatHistory>): Map<String, String> {
        if (histories.isEmpty()) {
            return emptyMap()
        }

        val characterCardNames = histories
            .mapNotNull { it.characterCardName?.trim()?.takeIf { name -> name.isNotBlank() } }
            .toSet()
        val characterGroupIds = histories
            .mapNotNull { it.characterGroupId?.trim()?.takeIf { id -> id.isNotBlank() } }
            .toSet()

        val characterCardsByName = if (characterCardNames.isEmpty()) {
            emptyMap()
        } else {
            runCatching { characterCardManager.getAllCharacterCards() }
                .getOrDefault(emptyList())
                .associateBy { it.name }
        }
        val characterGroupsById = if (characterGroupIds.isEmpty()) {
            emptyMap()
        } else {
            runCatching { characterGroupCardManager.getAllCharacterGroupCards() }
                .getOrDefault(emptyList())
                .associateBy { it.id }
        }

        val cardAvatarUrlByName = mutableMapOf<String, String>()
        characterCardNames.forEach { cardName ->
            val cardId = characterCardsByName[cardName]?.id ?: return@forEach
            val avatarSource = runCatching {
                userPreferencesManager.getAiAvatarForCharacterCardFlow(cardId).first()
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            val registeredAssetUrl = registerAsset(avatarSource, guessMimeType(avatarSource))
            if (!registeredAssetUrl.isNullOrBlank()) {
                cardAvatarUrlByName[cardName] = registeredAssetUrl
            }
        }

        val groupAvatarUrlById = mutableMapOf<String, String>()
        characterGroupIds.forEach { groupId ->
            val directGroupAvatarSource = runCatching {
                userPreferencesManager.getAiAvatarForCharacterGroupFlow(groupId).first()
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            val fallbackMemberCardId = characterGroupsById[groupId]
                ?.members
                ?.sortedBy { it.orderIndex }
                ?.firstOrNull()
                ?.characterCardId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val fallbackMemberAvatarSource = if (directGroupAvatarSource.isNullOrBlank()) {
                fallbackMemberCardId?.let { cardId ->
                    runCatching {
                        userPreferencesManager.getAiAvatarForCharacterCardFlow(cardId).first()
                    }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                }
            } else {
                null
            }

            val resolvedSource = directGroupAvatarSource ?: fallbackMemberAvatarSource ?: return@forEach
            val registeredAssetUrl = registerAsset(resolvedSource, guessMimeType(resolvedSource))
            if (!registeredAssetUrl.isNullOrBlank()) {
                groupAvatarUrlById[groupId] = registeredAssetUrl
            }
        }

        return histories.mapNotNull { history ->
            val groupId = history.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
            val cardName = history.characterCardName?.trim()?.takeIf { it.isNotBlank() }
            val avatarUrl = when {
                !groupId.isNullOrBlank() -> groupAvatarUrlById[groupId]
                !cardName.isNullOrBlank() -> cardAvatarUrlByName[cardName]
                else -> null
            }
            avatarUrl?.let { history.id to it }
        }.toMap()
    }

    private suspend fun buildCharacterSelectorResponse(): WebCharacterSelectorResponse {
        val allCards = characterCardManager.getAllCharacterCards()
        val allGroups = characterGroupCardManager.getAllCharacterGroupCards()
        val activePrompt = resolveActivePromptSnapshot(allCards, allGroups)

        val cards = allCards.map { card ->
            WebCharacterCardSelectorItem(
                id = card.id,
                name = card.name,
                description = card.description,
                avatarUrl = resolveCharacterCardAvatarUrl(card.id),
                createdAt = card.createdAt,
                updatedAt = card.updatedAt
            )
        }
        val groups = allGroups.map { group ->
            WebCharacterGroupSelectorItem(
                id = group.id,
                name = group.name,
                description = group.description,
                memberCount = group.members.size,
                avatarUrl = resolveCharacterGroupAvatarUrl(group),
                createdAt = group.createdAt,
                updatedAt = group.updatedAt
            )
        }

        return WebCharacterSelectorResponse(
            activePrompt = activePrompt,
            cards = cards,
            groups = groups
        )
    }

    private suspend fun resolveActivePromptSnapshot(
        allCards: List<CharacterCard>,
        allGroups: List<CharacterGroupCard>
    ): WebActivePromptSnapshot {
        val activePrompt = activePromptManager.getActivePrompt()
        return when (activePrompt) {
            is ActivePrompt.CharacterGroup -> {
                val group = allGroups.firstOrNull { it.id == activePrompt.id }
                if (group != null) {
                    WebActivePromptSnapshot(
                        type = ACTIVE_PROMPT_TYPE_CHARACTER_GROUP,
                        id = group.id,
                        name = group.name,
                        avatarUrl = resolveCharacterGroupAvatarUrl(group)
                    )
                } else {
                    resolveDefaultCharacterPromptSnapshot(allCards)
                }
            }

            is ActivePrompt.CharacterCard -> {
                val card = allCards.firstOrNull { it.id == activePrompt.id }
                if (card != null) {
                    WebActivePromptSnapshot(
                        type = ACTIVE_PROMPT_TYPE_CHARACTER_CARD,
                        id = card.id,
                        name = card.name,
                        avatarUrl = resolveCharacterCardAvatarUrl(card.id)
                    )
                } else {
                    resolveDefaultCharacterPromptSnapshot(allCards)
                }
            }
        }
    }

    private suspend fun resolveModelSelectorState(): WebModelSelectorState {
        functionalConfigManager.initializeIfNeeded()
        modelConfigManager.initializeIfNeeded()

        val configSummaries = modelConfigManager.getAllConfigSummaries()
        val activePrompt = activePromptManager.getActivePrompt()
        val lockedCard = when (activePrompt) {
            is ActivePrompt.CharacterCard -> {
                val card = characterCardManager.getCharacterCard(activePrompt.id)
                if (
                    CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode) ==
                        CharacterCardChatModelBindingMode.FIXED_CONFIG &&
                        !card.chatModelConfigId.isNullOrBlank()
                ) {
                    card
                } else {
                    null
                }
            }

            is ActivePrompt.CharacterGroup -> null
        }

        val currentConfigMapping = if (lockedCard != null) {
            FunctionConfigMapping(
                configId = lockedCard.chatModelConfigId ?: FunctionalConfigManager.DEFAULT_CONFIG_ID,
                modelIndex = lockedCard.chatModelIndex.coerceAtLeast(0)
            )
        } else {
            functionalConfigManager.getConfigMappingForFunction(FunctionType.CHAT)
        }

        val currentConfig = configSummaries.firstOrNull { it.id == currentConfigMapping.configId }
        val currentModelIndex = currentConfig?.let {
            getValidModelIndex(it.modelName, currentConfigMapping.modelIndex)
        } ?: 0
        val currentModelName = currentConfig?.let {
            getModelByIndex(it.modelName, currentModelIndex)
        }?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.not_selected)

        return WebModelSelectorState(
            currentConfigId = currentConfigMapping.configId,
            currentConfigName = currentConfig?.name,
            currentModelIndex = currentModelIndex,
            currentModelName = currentModelName,
            currentProviderType = currentConfig?.apiProviderType?.name.orEmpty(),
            lockedByCharacterCard = lockedCard != null,
            lockedCharacterCardId = lockedCard?.id,
            lockedCharacterCardName = lockedCard?.name,
            configs = configSummaries.map { config ->
                val models = getModelList(config.modelName)
                WebModelSelectorConfig(
                    id = config.id,
                    name = config.name,
                    modelName = config.modelName,
                    models = models,
                    selected = config.id == currentConfigMapping.configId,
                    selectedModelIndex = if (config.id == currentConfigMapping.configId) {
                        if (models.isEmpty()) {
                            0
                        } else {
                            getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
                        }
                    } else {
                        null
                    }
                )
            }
        )
    }

    private suspend fun resolveDefaultCharacterPromptSnapshot(
        allCards: List<CharacterCard>
    ): WebActivePromptSnapshot {
        val defaultCard = allCards.firstOrNull { it.id == CharacterCardManager.DEFAULT_CHARACTER_CARD_ID }
            ?: characterCardManager.getCharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
        return WebActivePromptSnapshot(
            type = ACTIVE_PROMPT_TYPE_CHARACTER_CARD,
            id = defaultCard.id,
            name = defaultCard.name,
            avatarUrl = resolveCharacterCardAvatarUrl(defaultCard.id)
        )
    }

    private suspend fun resolveCharacterCardAvatarUrl(cardId: String): String? {
        val avatarSource = runCatching {
            userPreferencesManager.getAiAvatarForCharacterCardFlow(cardId).first()
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return registerAsset(avatarSource, guessMimeType(avatarSource))
    }

    private suspend fun resolveCharacterGroupAvatarUrl(group: CharacterGroupCard): String? {
        val directGroupAvatarSource = runCatching {
            userPreferencesManager.getAiAvatarForCharacterGroupFlow(group.id).first()
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val fallbackMemberCardId = group.members
            .sortedBy { it.orderIndex }
            .firstOrNull()
            ?.characterCardId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val fallbackMemberAvatarSource = if (directGroupAvatarSource.isNullOrBlank()) {
            fallbackMemberCardId?.let { cardId ->
                runCatching {
                    userPreferencesManager.getAiAvatarForCharacterCardFlow(cardId).first()
                }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            }
        } else {
            null
        }

        val resolvedSource = directGroupAvatarSource ?: fallbackMemberAvatarSource ?: return null
        return registerAsset(resolvedSource, guessMimeType(resolvedSource))
    }

    private suspend fun resolveMessageAvatarUrl(
        normalizedSender: String,
        roleName: String?,
        proxyDisplayName: String?
    ): String? {
        if (normalizedSender == "assistant") {
            val assistantRoleName = roleName?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val matchedCard = characterCardManager.findCharacterCardByName(assistantRoleName) ?: return null
            return resolveCharacterCardAvatarUrl(matchedCard.id)
        }

        if (normalizedSender == "user") {
            val proxyName = proxyDisplayName?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val matchedCard = characterCardManager.findCharacterCardByName(proxyName) ?: return null
            return resolveCharacterCardAvatarUrl(matchedCard.id)
        }

        return null
    }

    private suspend fun buildWebMessage(
        chatId: String,
        message: ChatMessage,
        contentOverride: String? = null,
        structuredRenderPreferences: StructuredRenderPreferences
    ): WebChatMessage {
        val content = contentOverride ?: message.content
        val normalizedSender = normalizeSender(message.sender)
        val userRenderResult = if (normalizedSender == "user") {
            parseUserMessageContent(content)
        } else {
            null
        }
        val proxyAvatarName = userRenderResult?.displayName.takeIf { userRenderResult?.displayNameIsProxy == true }
        return WebChatMessage(
            id = "$chatId:${message.timestamp}",
            sender = normalizedSender,
            contentRaw = content,
            timestamp = message.timestamp,
            roleName = message.roleName.takeIf { it.isNotBlank() },
            provider = message.provider.takeIf { it.isNotBlank() },
            modelName = message.modelName.takeIf { it.isNotBlank() },
            displayContent = userRenderResult?.displayContent,
            displayName = userRenderResult?.displayName,
            displayNameIsProxy = userRenderResult?.displayNameIsProxy == true,
            avatarUrl = resolveMessageAvatarUrl(
                normalizedSender = normalizedSender,
                roleName = message.roleName,
                proxyDisplayName = proxyAvatarName
            ),
            replyPreview = userRenderResult?.replyPreview,
            imageLinks = userRenderResult?.imageLinks ?: emptyList(),
            contentBlocks =
                if (normalizedSender == "user") {
                    null
                } else {
                    buildContentBlocks(content, structuredRenderPreferences)
                },
            attachments = userRenderResult?.attachments ?: extractAttachments(content)
        )
    }

    private fun buildWebMessageLocatorPreview(
        preview: ChatMessageLocatorPreview
    ): WebChatMessageLocatorPreview {
        return WebChatMessageLocatorPreview(
            messageIndex = preview.messageIndex,
            timestamp = preview.timestamp,
            sender = normalizeSender(preview.sender),
            previewContent = preview.previewContent,
            contentLength = preview.contentLength,
            displayMode = preview.displayMode,
            isFavorite = preview.isFavorite
        ) 
    }

    private suspend fun latestAssistantMessage(
        chatId: String,
        returnToolStatus: Boolean
    ): WebChatMessage? {
        val structuredRenderPreferences = resolveStructuredRenderPreferences(chatId)
        val message = chatHistoryManager.loadChatMessages(chatId, order = "desc", limit = 10)
            .firstOrNull { it.sender.equals("ai", ignoreCase = true) }
            ?: return null
        val content = if (returnToolStatus) {
            message.content
        } else {
            ExternalChatResponseSanitizer.sanitize(message.content, false) ?: ""
        }
        return buildWebMessage(
            chatId,
            message,
            contentOverride = content,
            structuredRenderPreferences = structuredRenderPreferences
        )
    }

    private fun parseUserMessageContent(content: String): UserMessageRenderResult {
        var cleanedContent = content.replace(ChatMarkupRegex.memoryTag, "").trim()

        val proxySenderMatch = ChatMarkupRegex.proxySenderTag.find(cleanedContent)
        val proxySenderName = proxySenderMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        if (proxySenderMatch != null) {
            cleanedContent = cleanedContent.replace(proxySenderMatch.value, "").trim()
        }

        val imageLinksById = MediaLinkParser.extractImageLinks(cleanedContent).associateBy { it.id }
        val imageLinks = MediaLinkParser.extractImageLinkIds(cleanedContent).map { imageId ->
            val image = imageLinksById[imageId]
            WebMessageImageLink(
                id = imageId,
                assetUrl = image?.let { "data:${it.mimeType};base64,${it.base64Data}" },
                expired = image == null
            )
        }
        cleanedContent = MediaLinkParser.removeImageLinks(cleanedContent).trim()

        val mediaLinkAttachments = MediaLinkParser.extractMediaLinkTags(cleanedContent).map { tag ->
            WebMessageAttachment(
                id = "media_pool:${tag.id}",
                fileName = if (tag.type == "audio") "Audio" else "Video",
                mimeType = if (tag.type == "audio") "audio/*" else "video/*",
                fileSize = 0L
            )
        }
        cleanedContent = MediaLinkParser.removeMediaLinks(cleanedContent).trim()

        val replyMatch = ChatMarkupRegex.replyToTag.find(cleanedContent)
        val replyPreview = replyMatch?.let { match ->
            val instruction = appContext.getString(R.string.chat_reply_instruction)
            WebReplyPreview(
                sender = match.groupValues.getOrNull(1).orEmpty(),
                timestamp = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L,
                content = match.groupValues.getOrNull(3)
                    .orEmpty()
                    .removePrefix(instruction)
                    .trim()
                    .removeSurrounding("\"")
            )
        }
        if (replyMatch != null) {
            cleanedContent = cleanedContent.replace(replyMatch.value, "").trim()
        }

        val workspaceAttachments = mutableListOf<WebMessageAttachment>()
        val trailingAttachments = mutableListOf<WebMessageAttachment>()
        val workspaceMatch = ChatMarkupRegex.workspaceAttachmentTag.find(cleanedContent)
        if (workspaceMatch != null) {
            workspaceAttachments += WebMessageAttachment(
                id = "workspace_context",
                fileName = appContext.getString(R.string.chat_workspace_status),
                mimeType = "application/vnd.workspace-context+xml",
                fileSize = workspaceMatch.value.length.toLong(),
                content = workspaceMatch.value
            )
            cleanedContent = cleanedContent.replace(workspaceMatch.value, "").trim()
        }

        if (!cleanedContent.contains("<attachment")) {
            return UserMessageRenderResult(
                displayContent = cleanedContent,
                displayName = proxySenderName,
                displayNameIsProxy = !proxySenderName.isNullOrBlank(),
                replyPreview = replyPreview,
                imageLinks = imageLinks,
                attachments = workspaceAttachments + mediaLinkAttachments + trailingAttachments
            )
        }

        val pairedMatches = ChatMarkupRegex.attachmentDataTag.findAll(cleanedContent).toList()
        val selfClosingMatches = ChatMarkupRegex.attachmentDataSelfClosingTag.findAll(cleanedContent).toList()
        val allMatches = (pairedMatches.map { it to true } + selfClosingMatches.map { it to false })
            .sortedBy { it.first.range.first }
        val matches = mutableListOf<Pair<MatchResult, Boolean>>()
        var lastEnd = -1
        allMatches.forEach { (match, isPaired) ->
            if (match.range.first > lastEnd) {
                matches += match to isPaired
                lastEnd = match.range.last
            }
        }

        if (matches.isEmpty()) {
            return UserMessageRenderResult(
                displayContent = cleanedContent,
                displayName = proxySenderName,
                displayNameIsProxy = !proxySenderName.isNullOrBlank(),
                replyPreview = replyPreview,
                imageLinks = imageLinks,
                attachments = workspaceAttachments + mediaLinkAttachments + trailingAttachments
            )
        }

        val trailingAttachmentIndices = mutableSetOf<Int>()
        val contentAfterLast = cleanedContent.substring(matches.last().first.range.last + 1)
        if (contentAfterLast.isBlank()) {
            trailingAttachmentIndices += matches.lastIndex
            for (index in matches.size - 2 downTo 0) {
                val textBetween = cleanedContent.substring(
                    matches[index].first.range.last + 1,
                    matches[index + 1].first.range.first
                )
                if (textBetween.isBlank()) {
                    trailingAttachmentIndices += index
                } else {
                    break
                }
            }
        }

        val messageText = StringBuilder()
        var lastIndex = 0
        matches.forEachIndexed { index, (matchResult, _) ->
            val startIndex = matchResult.range.first
            val id = matchResult.groupValues.getOrNull(1).orEmpty()
            val fileName = matchResult.groupValues.getOrNull(2).orEmpty()
            val mimeType = matchResult.groupValues.getOrNull(3).orEmpty()
            val fileSize = matchResult.groupValues.getOrNull(4)?.toLongOrNull()
            val attachmentContent = matchResult.groupValues.getOrNull(5)?.takeIf { it.isNotBlank() }
            val attachment = WebMessageAttachment(
                id = id,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                content = attachmentContent,
                assetUrl = resolveAttachmentAssetUrl(id, mimeType, attachmentContent)
            )
            val shouldBeTrailing = trailingAttachmentIndices.contains(index)

            if (startIndex > lastIndex) {
                val textBefore = cleanedContent.substring(lastIndex, startIndex)
                if (!shouldBeTrailing || (trailingAttachmentIndices.isNotEmpty() && index == trailingAttachmentIndices.minOrNull())) {
                    messageText.append(textBefore)
                }
            }

            if (shouldBeTrailing) {
                trailingAttachments += attachment
            } else {
                messageText.append("@").append(fileName)
            }
            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < cleanedContent.length) {
            messageText.append(cleanedContent.substring(lastIndex))
        }

        return UserMessageRenderResult(
            displayContent = messageText.toString().trim(),
            displayName = proxySenderName,
            displayNameIsProxy = !proxySenderName.isNullOrBlank(),
            replyPreview = replyPreview,
            imageLinks = imageLinks,
            attachments = (workspaceAttachments + mediaLinkAttachments + trailingAttachments)
                .distinctBy { "${it.id}:${it.fileName}:${it.mimeType}" }
        )
    }

    private fun buildStreamingAssistantMessage(
        chatId: String,
        timestamp: Long,
        content: String,
        structuredRenderPreferences: StructuredRenderPreferences
    ): WebChatMessage {
        return WebChatMessage(
            id = "$chatId:assistant:streaming",
            sender = "assistant",
            contentRaw = content,
            timestamp = timestamp,
            contentBlocks = buildContentBlocks(content, structuredRenderPreferences),
            attachments = emptyList()
        )
    }

    private fun buildContentBlocks(
        content: String,
        structuredRenderPreferences: StructuredRenderPreferences
    ): List<WebMessageContentBlock>? {
        if (content.isEmpty()) {
            return null
        }

        val flatBlocks =
            StructuredAssistantContentParser.parse(content).mapNotNull { block ->
                buildStructuredContentBlock(block)
            }

        if (flatBlocks.isEmpty()) {
            return null
        }

        val groupedBlocks = groupContentBlocks(flatBlocks, structuredRenderPreferences)
        return groupedBlocks.takeIf { it.isNotEmpty() }
    }

    private fun buildStructuredContentBlock(
        block: StructuredAssistantContentParser.Block
    ): WebMessageContentBlock? {
        if (block.rawContent.isEmpty()) {
            return null
        }

        if (block.kind == StructuredAssistantContentParser.BlockKind.TEXT) {
            return WebMessageContentBlock(
                kind = "text",
                content = block.content
            )
        }

        return WebMessageContentBlock(
            kind = "xml",
            content = block.content,
            xml = block.rawContent,
            tagName = block.tagName,
            rawTagName = block.rawTagName,
            attrs = block.attrs,
            closed = block.closed
        )
    }

    private fun groupContentBlocks(
        blocks: List<WebMessageContentBlock>,
        structuredRenderPreferences: StructuredRenderPreferences
    ): List<WebMessageContentBlock> {
        val grouped = mutableListOf<WebMessageContentBlock>()
        var index = 0

        while (index < blocks.size) {
            val block = blocks[index]
            val tagName = block.tagName

            if (block.kind != "xml") {
                grouped += block
                index++
                continue
            }

            if (
                structuredRenderPreferences.showThinkingProcess &&
                (tagName == "think" || tagName == "thinking")
            ) {
                var nextIndex = index + 1
                var toolCount = 0
                var xmlToolRelatedCount = 0

                while (nextIndex < blocks.size) {
                    val next = blocks[nextIndex]
                    if (next.kind == "text" && next.content?.isBlank() == true) {
                        nextIndex++
                        continue
                    }
                    if (next.kind != "xml") {
                        break
                    }

                    val nextTagName = next.tagName
                    if (isIgnorableXmlTagForToolGrouping(nextTagName)) {
                        nextIndex++
                        continue
                    }

                    val isThinkAgain = nextTagName == "think" || nextTagName == "thinking"
                    val isToolRelated = nextTagName == "tool" || nextTagName == "tool_result"
                    if (!isThinkAgain && !isToolRelated) {
                        break
                    }

                    if (isToolRelated) {
                        val toolName = next.attrs["name"]
                        if (
                            !shouldGroupToolByName(
                                toolName = toolName,
                                toolCollapseMode = structuredRenderPreferences.toolCollapseMode
                            )
                        ) {
                            break
                        }
                        if (nextTagName == "tool") {
                            toolCount++
                        }
                        xmlToolRelatedCount++
                    }

                    nextIndex++
                }

                if (
                    shouldCollapseToolSequence(
                        toolCollapseMode = structuredRenderPreferences.toolCollapseMode,
                        toolCount = toolCount,
                        xmlToolRelatedCount = xmlToolRelatedCount
                    )
                ) {
                    grouped += WebMessageContentBlock(
                        kind = "group",
                        groupType = "think_tools",
                        children = blocks.subList(index, nextIndex).toList()
                    )
                    index = nextIndex
                    continue
                }
            }

            if (tagName == "tool" || tagName == "tool_result") {
                val firstToolName = block.attrs["name"]
                if (
                    shouldGroupToolByName(
                        toolName = firstToolName,
                        toolCollapseMode = structuredRenderPreferences.toolCollapseMode
                    )
                ) {
                    var nextIndex = index + 1
                    var toolCount = if (tagName == "tool") 1 else 0
                    var xmlToolRelatedCount = 1

                    while (nextIndex < blocks.size) {
                        val next = blocks[nextIndex]
                        if (next.kind == "text" && next.content?.isBlank() == true) {
                            nextIndex++
                            continue
                        }
                        if (next.kind != "xml") {
                            break
                        }

                        val nextTagName = next.tagName
                        if (isIgnorableXmlTagForToolGrouping(nextTagName)) {
                            nextIndex++
                            continue
                        }

                        val isToolRelated = nextTagName == "tool" || nextTagName == "tool_result"
                        if (!isToolRelated) {
                            break
                        }

                        val toolName = next.attrs["name"]
                        if (
                            !shouldGroupToolByName(
                                toolName = toolName,
                                toolCollapseMode = structuredRenderPreferences.toolCollapseMode
                            )
                        ) {
                            break
                        }

                        xmlToolRelatedCount++
                        if (nextTagName == "tool") {
                            toolCount++
                        }
                        nextIndex++
                    }

                    if (
                        shouldCollapseToolSequence(
                            toolCollapseMode = structuredRenderPreferences.toolCollapseMode,
                            toolCount = toolCount,
                            xmlToolRelatedCount = xmlToolRelatedCount
                        )
                    ) {
                        grouped += WebMessageContentBlock(
                            kind = "group",
                            groupType = "tools_only",
                            children = blocks.subList(index, nextIndex).toList()
                        )
                        index = nextIndex
                        continue
                    }
                }
            }

            grouped += block
            index++
        }

        return grouped
    }

    private fun isIgnorableXmlTagForToolGrouping(tagName: String?): Boolean {
        return tagName == "meta"
    }

    private fun shouldGroupToolByName(
        toolName: String?,
        toolCollapseMode: ToolCollapseMode
    ): Boolean {
        if (toolCollapseMode == ToolCollapseMode.ALL || toolCollapseMode == ToolCollapseMode.FULL) {
            return true
        }

        val normalized = toolName?.trim()?.lowercase(Locale.US) ?: return false
        if (normalized.contains("search")) {
            return true
        }

        return normalized in setOf(
            "list_files",
            "grep_code",
            "grep_context",
            "read_file",
            "read_file_part",
            "read_file_full",
            "read_file_binary",
            "use_package",
            "find_files",
            "visit_web"
        )
    }

    private fun shouldCollapseToolSequence(
        toolCollapseMode: ToolCollapseMode,
        toolCount: Int,
        xmlToolRelatedCount: Int
    ): Boolean {
        if (xmlToolRelatedCount <= 0) {
            return false
        }

        return when (toolCollapseMode) {
            ToolCollapseMode.FULL -> true
            ToolCollapseMode.READ_ONLY,
            ToolCollapseMode.ALL -> toolCount >= 2 && xmlToolRelatedCount >= 2
        }
    }

    private fun extractAttachments(content: String): List<WebMessageAttachment> {
        val attachments = mutableListOf<WebMessageAttachment>()

        ChatMarkupRegex.attachmentDataTag.findAll(content).forEach { match ->
            val id = match.groupValues.getOrNull(1).orEmpty()
            val fileName = match.groupValues.getOrNull(2).orEmpty()
            val mimeType = match.groupValues.getOrNull(3).orEmpty()
            val fileSize = match.groupValues.getOrNull(4)?.toLongOrNull()
            val inlineContent = match.groupValues.getOrNull(5)?.takeIf { it.isNotBlank() }
            attachments += WebMessageAttachment(
                id = id,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                content = inlineContent,
                assetUrl = resolveAttachmentAssetUrl(id, mimeType, inlineContent)
            )
        }

        ChatMarkupRegex.attachmentDataSelfClosingTag.findAll(content).forEach { match ->
            val id = match.groupValues.getOrNull(1).orEmpty()
            val fileName = match.groupValues.getOrNull(2).orEmpty()
            val mimeType = match.groupValues.getOrNull(3).orEmpty()
            val fileSize = match.groupValues.getOrNull(4)?.toLongOrNull()
            val inlineContent = match.groupValues.getOrNull(5)?.takeIf { it.isNotBlank() }
            attachments += WebMessageAttachment(
                id = id,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                content = inlineContent,
                assetUrl = resolveAttachmentAssetUrl(id, mimeType, inlineContent)
            )
        }

        return attachments.distinctBy { "${it.id}:${it.fileName}:${it.mimeType}" }
    }

    private fun resolveAttachmentAssetUrl(
        attachmentId: String,
        mimeType: String,
        inlineContent: String?
    ): String? {
        if (!inlineContent.isNullOrBlank()) {
            return null
        }
        return when {
            attachmentId.startsWith("content://") ||
                attachmentId.startsWith("file://") ||
                attachmentId.startsWith("/") ||
                File(attachmentId).exists() ->
                registerAsset(attachmentId, mimeType)

            else -> null
        }
    }

    private suspend fun resolveStructuredRenderPreferences(chatId: String): StructuredRenderPreferences {
        val snapshot = resolveThemePreferenceSnapshot(currentChatMeta(chatId))
        return StructuredRenderPreferences(
            showThinkingProcess = snapshot.showThinkingProcess,
            toolCollapseMode = displayPreferencesManager.toolCollapseMode.first()
        )
    }

    private suspend fun resolveThemePreferenceSnapshot(chat: ChatHistory?): ThemePreferenceSnapshot {
        val groupId = chat?.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        if (groupId != null) {
            return userPreferencesManager.resolveThemePreferenceSnapshot(characterGroupId = groupId)
        }

        val cardName = chat?.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        if (cardName != null) {
            val matchingCard = characterCardManager.findCharacterCardByName(cardName)
            if (matchingCard != null) {
                return userPreferencesManager.resolveThemePreferenceSnapshot(
                    characterCardId = matchingCard.id,
                )
            }
        }

        return userPreferencesManager.resolveThemePreferenceSnapshot(
            characterCardId = CharacterCardManager.DEFAULT_CHARACTER_CARD_ID,
        )
    }

    private suspend fun resolveDisplayPreferencesSnapshot(
        snapshot: ThemePreferenceSnapshot
    ): WebDisplayPreferences {
        return WebDisplayPreferences(
            showUserName = snapshot.showUserName,
            showRoleName = snapshot.showRoleName,
            showModelName = snapshot.showModelName,
            showModelProvider = snapshot.showModelProvider,
            showMessageTokenStats = snapshot.showMessageTokenStats,
            showMessageTimingStats = snapshot.showMessageTimingStats,
            showMessageTimestamp = snapshot.showMessageTimestamp,
            toolCollapseMode = displayPreferencesManager.toolCollapseMode.first().value,
            globalUserName = displayPreferencesManager.globalUserName.first()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        )
    }

    private suspend fun buildThemeSnapshot(
        snapshot: ThemePreferenceSnapshot,
        displayPreferences: WebDisplayPreferences
    ): WebThemeSnapshot {
        val backgroundUrl = snapshot.backgroundImageUri
            ?.takeIf { snapshot.useBackgroundImage }
            ?.let { registerAsset(it, guessMimeType(it)) }
        val globalUserAvatarUri = displayPreferencesManager.globalUserAvatarUri.first()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val colorScheme = resolveThemeColorScheme(appContext, snapshot)
        return WebThemeSnapshot(
            source = snapshot.source,
            sourceId = snapshot.sourceId,
            themeMode = snapshot.themeMode,
            useSystemTheme = snapshot.useSystemTheme,
            useCustomColors = snapshot.useCustomColors,
            primaryColor = colorToCss(snapshot.customPrimaryColor),
            secondaryColor = colorToCss(snapshot.customSecondaryColor),
            palette = WebThemePalette(
                backgroundColor = composeColorToCss(colorScheme.background),
                surfaceColor = composeColorToCss(colorScheme.surface),
                surfaceVariantColor = composeColorToCss(colorScheme.surfaceVariant),
                surfaceContainerColor = composeColorToCss(colorScheme.surfaceContainer),
                surfaceContainerHighColor = composeColorToCss(colorScheme.surfaceContainerHigh),
                primaryColor = composeColorToCss(colorScheme.primary),
                secondaryColor = composeColorToCss(colorScheme.secondary),
                primaryContainerColor = composeColorToCss(colorScheme.primaryContainer),
                onPrimaryContainerColor = composeColorToCss(colorScheme.onPrimaryContainer),
                onSurfaceColor = composeColorToCss(colorScheme.onSurface),
                onSurfaceVariantColor = composeColorToCss(colorScheme.onSurfaceVariant),
                outlineColor = composeColorToCss(colorScheme.outline),
                outlineVariantColor = composeColorToCss(colorScheme.outlineVariant)
            ),
            background = WebThemeBackground(
                type = when {
                    !snapshot.useBackgroundImage || snapshot.backgroundImageUri.isNullOrBlank() -> "none"
                    snapshot.backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_VIDEO -> "video"
                    else -> "image"
                },
                assetUrl = backgroundUrl,
                opacity = snapshot.backgroundImageOpacity
            ),
            header = WebHeaderTheme(
                transparent = snapshot.chatHeaderTransparent,
                overlay = snapshot.chatHeaderOverlayMode
            ),
            input = WebInputTheme(
                style = snapshot.inputStyle,
                transparent = snapshot.chatInputTransparent,
                floating = snapshot.chatInputFloating,
                liquidGlass = snapshot.chatInputLiquidGlass,
                waterGlass = snapshot.chatInputWaterGlass
            ),
            font = WebFontTheme(
                type = snapshot.fontType,
                systemFontName = snapshot.systemFontName,
                customFontAssetUrl = snapshot.customFontPath?.takeIf { snapshot.useCustomFont }?.let {
                    registerAsset(it, guessMimeType(it))
                },
                scale = snapshot.fontScale
            ),
            chatStyle = snapshot.chatStyle,
            showThinkingProcess = snapshot.showThinkingProcess,
            showStatusTags = snapshot.showStatusTags,
            showInputProcessingStatus = snapshot.showInputProcessingStatus,
            display = displayPreferences,
            bubble = WebBubbleTheme(
                showAvatar = snapshot.bubbleShowAvatar,
                wideLayout = snapshot.bubbleWideLayoutEnabled,
                cursorUserFollowTheme = snapshot.cursorUserBubbleFollowTheme,
                cursorUserColor = colorToCss(snapshot.cursorUserBubbleColor),
                userBubbleColor = colorToCss(snapshot.bubbleUserBubbleColor),
                assistantBubbleColor = colorToCss(snapshot.bubbleAiBubbleColor),
                userTextColor = colorToCss(snapshot.bubbleUserTextColor),
                assistantTextColor = colorToCss(snapshot.bubbleAiTextColor),
                cursorUserLiquidGlass =
                    snapshot.cursorUserBubbleLiquidGlass && !snapshot.cursorUserBubbleWaterGlass,
                cursorUserWaterGlass = snapshot.cursorUserBubbleWaterGlass,
                userLiquidGlass =
                    snapshot.bubbleUserBubbleLiquidGlass && !snapshot.bubbleUserBubbleWaterGlass,
                userWaterGlass = snapshot.bubbleUserBubbleWaterGlass,
                assistantLiquidGlass =
                    snapshot.bubbleAiBubbleLiquidGlass && !snapshot.bubbleAiBubbleWaterGlass,
                assistantWaterGlass = snapshot.bubbleAiBubbleWaterGlass,
                userRounded = snapshot.bubbleUserRoundedCornersEnabled,
                assistantRounded = snapshot.bubbleAiRoundedCornersEnabled,
                userPaddingLeft = snapshot.bubbleUserContentPaddingLeft,
                userPaddingRight = snapshot.bubbleUserContentPaddingRight,
                assistantPaddingLeft = snapshot.bubbleAiContentPaddingLeft,
                assistantPaddingRight = snapshot.bubbleAiContentPaddingRight,
                userImage = WebBubbleImageTheme(
                    enabled = snapshot.bubbleUserUseImage && !snapshot.bubbleUserImageUri.isNullOrBlank(),
                    assetUrl = snapshot.bubbleUserImageUri?.takeIf { snapshot.bubbleUserUseImage }?.let {
                        registerAsset(it, guessMimeType(it))
                    },
                    renderMode = snapshot.bubbleImageRenderMode
                ),
                assistantImage = WebBubbleImageTheme(
                    enabled = snapshot.bubbleAiUseImage && !snapshot.bubbleAiImageUri.isNullOrBlank(),
                    assetUrl = snapshot.bubbleAiImageUri?.takeIf { snapshot.bubbleAiUseImage }?.let {
                        registerAsset(it, guessMimeType(it))
                    },
                    renderMode = snapshot.bubbleImageRenderMode
                )
            ),
            avatars = WebAvatarTheme(
                shape = snapshot.avatarShape,
                cornerRadius = snapshot.avatarCornerRadius,
                userAvatarUrl = (snapshot.customUserAvatarUri?.takeIf { it.isNotBlank() } ?: globalUserAvatarUri)?.let {
                    registerAsset(it, guessMimeType(it))
                },
                assistantAvatarUrl = snapshot.customAiAvatarUri?.takeIf { it.isNotBlank() }?.let {
                    registerAsset(it, guessMimeType(it))
                }
            )
        )
    }

    private suspend fun currentChatMeta(chatId: String): ChatHistory? {
        return chatHistoryManager.chatHistoriesFlow.first().firstOrNull { it.id == chatId }
    }

    private suspend fun switchAppChatContext(
        chatId: String,
        syncActivePromptFromBinding: Boolean = true
    ): Boolean {
        val chatMeta = currentChatMeta(chatId) ?: return false
        val chatChanged = core.currentChatId.value != chatId
        if (chatChanged) {
            core.switchChat(chatId)
        }
        val switched = if (chatChanged) {
            withTimeoutOrNull(CHAT_SWITCH_TIMEOUT_MS) {
                while (core.currentChatId.value != chatId) {
                    delay(40)
                }
                true
            } ?: false
        } else {
            true
        }
        if (!switched) {
            return false
        }
        if (syncActivePromptFromBinding) {
            activePromptManager.activateForChatBinding(
                characterCardName = chatMeta.characterCardName,
                characterGroupId = chatMeta.characterGroupId
            )
        }
        return true
    }

    private suspend fun awaitFinalState(chatId: String): InputProcessingState? {
        val resolved: InputProcessingState? =
            withTimeoutOrNull<InputProcessingState?>(STREAM_FINAL_STATE_TIMEOUT_MS) {
                var terminalState: InputProcessingState? = null
                var resolvedState = false
                while (!resolvedState) {
                    val state = core.inputProcessingStateByChatId.value[chatId]
                    when (state) {
                        null,
                        is InputProcessingState.Idle,
                        is InputProcessingState.Completed,
                        is InputProcessingState.Error -> {
                            terminalState = state
                            resolvedState = true
                        }
                        else -> delay(50)
                    }
                }
                return@withTimeoutOrNull terminalState
            }
        return resolved ?: core.inputProcessingStateByChatId.value[chatId]
    }

    private fun normalizeSender(sender: String): String {
        return when (sender.trim().lowercase(Locale.US)) {
            "ai", "assistant" -> "assistant"
            "summary" -> "summary"
            "system" -> "system"
            else -> "user"
        }
    }

    private fun registerAsset(source: String?, mimeTypeHint: String? = null): String? {
        val normalizedSource = source?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val cacheKey = "$normalizedSource|${mimeTypeHint.orEmpty()}"
        val existingId = assetIdBySource[cacheKey]
        if (existingId != null) {
            assetsById[existingId]?.createdAt = System.currentTimeMillis()
            return "$ASSET_ROUTE_PREFIX/$existingId"
        }

        val assetId = UUID.randomUUID().toString()
        assetIdBySource[cacheKey] = assetId
        assetsById[assetId] = RegisteredAsset(
            source = normalizedSource,
            mimeTypeHint = mimeTypeHint,
            createdAt = System.currentTimeMillis()
        )
        return "$ASSET_ROUTE_PREFIX/$assetId"
    }

    private fun readRegisteredAsset(entry: RegisteredAsset): AssetPayload? {
        return try {
            val source = entry.source
            when {
                source.startsWith("file:///android_asset/") -> {
                    val assetPath = source.removePrefix("file:///android_asset/")
                    val bytes = appContext.assets.open(assetPath).use { it.readBytes() }
                    AssetPayload(bytes, guessMimeType(assetPath, entry.mimeTypeHint))
                }

                source.startsWith("android_asset/") -> {
                    val assetPath = source.removePrefix("android_asset/")
                    val bytes = appContext.assets.open(assetPath).use { it.readBytes() }
                    AssetPayload(bytes, guessMimeType(assetPath, entry.mimeTypeHint))
                }

                source.startsWith("content://") || source.startsWith("android.resource://") -> {
                    val uri = Uri.parse(source)
                    val mimeType = appContext.contentResolver.getType(uri)
                        ?: guessMimeType(source, entry.mimeTypeHint)
                    val bytes = appContext.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: return null
                    AssetPayload(bytes, mimeType)
                }

                source.startsWith("file://") -> {
                    val file = File(Uri.parse(source).path.orEmpty())
                    if (!file.exists()) {
                        null
                    } else {
                        AssetPayload(file.readBytes(), guessMimeType(file.name, entry.mimeTypeHint))
                    }
                }

                else -> {
                    val file = File(source)
                    if (!file.exists()) {
                        null
                    } else {
                        AssetPayload(file.readBytes(), guessMimeType(file.name, entry.mimeTypeHint))
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read registered asset: ${entry.source}", e)
            null
        }
    }

    private fun cleanupExpiredEntries() {
        val now = System.currentTimeMillis()

        val expiredAssets = assetsById.entries.filter { now - it.value.createdAt > ASSET_TTL_MS }
        expiredAssets.forEach { entry ->
            assetsById.remove(entry.key)
            assetIdBySource.entries.removeIf { it.value == entry.key }
        }

        val expiredUploads = uploadsById.entries.filter { now - it.value.createdAt > UPLOAD_TTL_MS }
        expiredUploads.forEach { entry ->
            uploadsById.remove(entry.key)
            runCatching { entry.value.storedFile.delete() }
        }
    }

    private fun requireBearerToken(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? {
        val expectedToken = preferences.getBearerToken().trim()
        if (expectedToken.isBlank()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                WebErrorResponse("Bearer token not configured")
            )
        }

        val authorization = session.headers.entries.firstOrNull {
            it.key.equals("authorization", ignoreCase = true)
        }?.value?.trim().orEmpty()

        val actualToken = if (authorization.startsWith("Bearer ", ignoreCase = true)) {
            authorization.substringAfter(' ').trim()
        } else {
            ""
        }

        return if (actualToken == expectedToken) {
            null
        } else {
            jsonResponse(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                WebErrorResponse("Unauthorized")
            )
        }
    }

    private fun normalizeStaticPath(uri: String): String? {
        var path = uri.substringBefore('?').trim()
        if (path.isEmpty() || path == "/") {
            return "/"
        }
        if (!path.startsWith("/")) {
            path = "/$path"
        }
        if (path.contains('\\')) {
            return null
        }
        if (path.split('/').any { it == ".." }) {
            return null
        }
        return path
    }

    private fun resolvePackagedAssetPath(path: String): String {
        val relative = if (path == "/") DEFAULT_STATIC_ASSET else "web-chat${path}"
        val hasExtension = path.substringAfterLast('/', "").contains('.')
        return if (!hasExtension) DEFAULT_STATIC_ASSET else relative.removePrefix("/")
    }

    private fun openPackagedAsset(assetPath: String): AssetPayload? {
        return try {
            val normalizedPath = assetPath.removePrefix("/")
            val bytes = appContext.assets.open(normalizedPath).use { it.readBytes() }
            AssetPayload(bytes, guessMimeType(normalizedPath))
        } catch (_: IOException) {
            null
        }
    }

    private fun sanitizeFilename(fileName: String): String {
        return fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "upload_${System.currentTimeMillis()}" }
    }

    private fun chatIdFrom(uri: String, exactPrefix: String): String? {
        val normalized = uri.removeSuffix("/")
        val prefix = exactPrefix.removeSuffix("/")
        if (!normalized.startsWith("$prefix/")) {
            return null
        }
        return normalized.removePrefix("$prefix/").takeIf { it.isNotBlank() && !it.contains('/') }
    }

    private fun chatIdFrom(
        uri: String,
        segmentPrefix: String,
        suffix: String
    ): String? {
        if (!uri.startsWith(segmentPrefix) || !uri.endsWith(suffix)) {
            return null
        }
        val raw = uri.removePrefix(segmentPrefix).removeSuffix(suffix)
        return raw.takeIf { it.isNotBlank() && !it.contains('/') }
    }

    private fun colorToCss(color: Int?): String? {
        if (color == null) {
            return null
        }
        val alpha = Color.alpha(color) / 255f
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return if (alpha >= 0.999f) {
            String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
        } else {
            String.format(Locale.US, "rgba(%d, %d, %d, %.3f)", red, green, blue, alpha)
        }
    }

    private fun composeColorToCss(color: androidx.compose.ui.graphics.Color): String {
        return requireNotNull(colorToCss(color.toArgb()))
    }

    private inline fun <reified T> jsonResponse(
        status: NanoHTTPD.Response.Status,
        body: T
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            status,
            JSON_MIME_TYPE,
            json.encodeToString(body)
        )
    }

    private fun plainTextResponse(
        status: NanoHTTPD.Response.Status,
        body: String
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, body)
    }

    private fun byteArrayResponse(
        status: NanoHTTPD.Response.Status,
        mimeType: String,
        bytes: ByteArray
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            status,
            mimeType,
            ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
    }

    private inline fun <reified T> parseJsonRequest(session: NanoHTTPD.IHTTPSession): T? {
        val requestBody = readRequestBody(session)
        if (requestBody.error != null) {
            return null
        }
        val body = requestBody.body ?: return null
        if (body.isBlank()) {
            return null
        }
        return runCatching { json.decodeFromString<T>(body) }.getOrNull()
    }

    private fun readRequestBody(session: NanoHTTPD.IHTTPSession): WebRequestBodyResult {
        return try {
            val contentLength = session.headers.entries.firstOrNull {
                it.key.equals("content-length", ignoreCase = true)
            }?.value?.trim()?.toLongOrNull()
                ?: return WebRequestBodyResult(error = "Missing or invalid Content-Length")
            if (contentLength < 0L || contentLength > Int.MAX_VALUE.toLong()) {
                return WebRequestBodyResult(error = "Unsupported Content-Length: $contentLength")
            }
            if (contentLength == 0L) {
                return WebRequestBodyResult(body = "")
            }

            val bodyBytes = ByteArray(contentLength.toInt())
            var offset = 0
            val inputStream = session.inputStream
            while (offset < bodyBytes.size) {
                val read = inputStream.read(bodyBytes, offset, bodyBytes.size - offset)
                if (read < 0) {
                    return WebRequestBodyResult(error = "Unexpected end of stream while reading request body")
                }
                offset += read
            }

            val charset = resolveRequestCharset(
                session.headers.entries.firstOrNull {
                    it.key.equals("content-type", ignoreCase = true)
                }?.value
            )
            WebRequestBodyResult(body = String(bodyBytes, charset))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read HTTP request body", e)
            WebRequestBodyResult(error = "Failed to read request body: ${e.message ?: "Unknown error"}")
        }
    }

    private fun resolveRequestCharset(contentTypeHeader: String?): Charset {
        val charsetName = contentTypeHeader
            ?.split(';')
            ?.asSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }

        return if (charsetName != null) {
            runCatching { Charset.forName(charsetName) }.getOrDefault(StandardCharsets.UTF_8)
        } else {
            StandardCharsets.UTF_8
        }
    }

    private fun writeSseEvent(writer: BufferedWriter, payload: WebChatStreamEvent) {
        val serialized = json.encodeToString(payload)
        writer.write("event: ")
        writer.write(payload.event)
        writer.newLine()
        serialized.lineSequence().forEach { line ->
            writer.write("data: ")
            writer.write(line)
            writer.newLine()
        }
        writer.newLine()
        writer.flush()
    }

    private fun guessMimeType(
        pathOrName: String,
        fallback: String? = null
    ): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(pathOrName)
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.US)
        val mimeType = extension?.let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
        } ?: URLConnection.guessContentTypeFromName(pathOrName)
        return mimeType ?: fallback ?: DEFAULT_BINARY_MIME_TYPE
    }

    private fun NanoHTTPD.Response.withCors(): NanoHTTPD.Response {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept")
        addHeader("Access-Control-Max-Age", "3600")
        return this
    }

    companion object {
        private const val TAG = "WebChatHttpBridge"
        private const val BOOTSTRAP_PATH = "/api/web/bootstrap"
        private const val CHARACTER_SELECTOR_PATH = "/api/web/character-selector"
        private const val ACTIVE_PROMPT_PATH = "/api/web/active-prompt"
        private const val MODEL_SELECTOR_PATH = "/api/web/model-selector"
        private const val MEMORY_SELECTOR_PATH = "/api/web/memory-selector"
        private const val INPUT_SETTINGS_PATH = "/api/web/input-settings"
        private const val MANUAL_MEMORY_UPDATE_PATH = "/api/web/actions/manual-memory-update"
        private const val MANUAL_CONVERSATION_SUMMARY_PATH =
            "/api/web/actions/manual-conversation-summary"
        private const val CHATS_PATH = "/api/web/chats"
        private const val CHATS_REORDER_PATH = "/api/web/chats/reorder"
        private const val CHAT_GROUP_RENAME_PATH = "/api/web/chat-groups/rename"
        private const val CHAT_GROUP_DELETE_PATH = "/api/web/chat-groups/delete"
        private const val UPLOADS_PATH = "/api/web/uploads"
        private const val ASSET_ROUTE_PREFIX = "/api/web/assets"
        private const val JSON_MIME_TYPE = "application/json; charset=utf-8"
        private const val SSE_MIME_TYPE = "text/event-stream; charset=utf-8"
        private const val DEFAULT_BINARY_MIME_TYPE = "application/octet-stream"
        private const val DEFAULT_STATIC_ASSET = "web-chat/index.html"
        private const val DEFAULT_MESSAGES_PAGE_SIZE = 24
        private const val MAX_MESSAGES_PAGE_SIZE = 120
        private const val CHAT_SWITCH_TIMEOUT_MS = 3_000L
        private const val STREAM_READY_TIMEOUT_MS = 10_000L
        private const val STREAM_FINAL_STATE_TIMEOUT_MS = 10_000L
        private const val SSE_PIPE_BUFFER_SIZE = 64 * 1024
        private const val MAX_UPLOAD_BYTES = 25L * 1024L * 1024L
        private const val ASSET_TTL_MS = 6L * 60L * 60L * 1000L
        private const val UPLOAD_TTL_MS = 2L * 60L * 60L * 1000L
        private const val STREAM_EVENT_START = "start"
        private const val STREAM_EVENT_USER_MESSAGE = "user_message"
        private const val STREAM_EVENT_ASSISTANT_DELTA = "assistant_delta"
        private const val STREAM_EVENT_ASSISTANT_DONE = "assistant_done"
        private const val STREAM_EVENT_ERROR = "error"
        private const val ACTIVE_PROMPT_TYPE_CHARACTER_CARD = "character_card"
        private const val ACTIVE_PROMPT_TYPE_CHARACTER_GROUP = "character_group"
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

private data class RegisteredAsset(
    val source: String,
    val mimeTypeHint: String? = null,
    @Volatile
    var createdAt: Long
)

private data class UploadedAttachmentEntry(
    val attachment: AttachmentInfo,
    val storedFile: File,
    val createdAt: Long
)

private data class AssetPayload(
    val bytes: ByteArray,
    val mimeType: String
)

private data class UserMessageRenderResult(
    val displayContent: String,
    val displayName: String? = null,
    val displayNameIsProxy: Boolean = false,
    val replyPreview: WebReplyPreview? = null,
    val imageLinks: List<WebMessageImageLink> = emptyList(),
    val attachments: List<WebMessageAttachment> = emptyList()
)

private data class StructuredRenderPreferences(
    val showThinkingProcess: Boolean,
    val toolCollapseMode: ToolCollapseMode
)

private data class WebRequestBodyResult(
    val body: String? = null,
    val error: String? = null
)
