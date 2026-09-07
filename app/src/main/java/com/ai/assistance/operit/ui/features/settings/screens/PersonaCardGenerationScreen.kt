package com.ai.assistance.operit.ui.features.settings.screens

import android.annotation.SuppressLint
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.PromptTagManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.PersonaCardChatHistoryManager
import com.ai.assistance.operit.data.preferences.CharacterCardBilingualData
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// --- 本地最小工具执行器：仅处理 save_character_info ---
private object LocalCharacterToolExecutor {
    const val TOOL_NAME = "save_character_info"

    fun extractInvocations(raw: String): List<Pair<String, Map<String, String>>> {
        val list = mutableListOf<Pair<String, Map<String, String>>>()
        // 简单 XML 提取：<tool name="..."> <param name="field">..</param><param name="content">..</param></tool>
        ChatMarkupRegex.toolCallPattern.findAll(raw).forEach { m ->
            val name = m.groupValues.getOrNull(2)?.trim().orEmpty()
            val body = m.groupValues.getOrNull(3) ?: ""
            val params = mutableMapOf<String, String>()
            ChatMarkupRegex.toolParamPattern.findAll(body).forEach { pm ->
                val pName = pm.groupValues.getOrNull(1)?.trim().orEmpty()
                val pVal = pm.groupValues.getOrNull(2)?.trim().orEmpty()
                params[pName] = pVal
            }
            list.add(name to params)
        }
        return list
    }

    suspend fun executeSaveCharacterInfo(
        context: android.content.Context,
        characterCardId: String,
        field: String,
        content: String
    ): ToolResult {
        return try {
            val manager = CharacterCardManager.getInstance(context)
            
            // 获取当前角色卡
            val currentCard = manager.getCharacterCard(characterCardId)
            if (currentCard == null) {
                return ToolResult(
                    toolName = TOOL_NAME,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.error_character_card_not_exist)
                )
            }
            
            // 根据字段更新对应内容
            val updatedCard = when (field) {
                "name" -> currentCard.copy(name = content)
                "description" -> currentCard.copy(description = content)
                "characterSetting" -> currentCard.copy(characterSetting = content)
                "openingStatement" -> currentCard.copy(openingStatement = content)
                "otherContentChat" -> currentCard.copy(otherContentChat = content)
                "otherContentVoice" -> currentCard.copy(otherContentVoice = content)
                "otherContent" -> currentCard.copy(otherContentChat = content)
                "advancedCustomPrompt" -> currentCard.copy(advancedCustomPrompt = content)
                "marks" -> currentCard.copy(marks = content)
                else -> {
                    return ToolResult(
                        toolName = TOOL_NAME,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.error_unsupported_field, field)
                    )
                }
            }
            
            withContext(Dispatchers.IO) { 
                manager.updateCharacterCard(updatedCard)
            }
            
            ToolResult(
                toolName = TOOL_NAME,
                success = true,
                result = StringResultData("ok"),
                error = null
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = TOOL_NAME,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        }
    }
}

private data class CharacterChatMessage(
    val role: String, // "user" | "assistant"
    var content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaCardGenerationScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToModelConfig: () -> Unit = {},
    onNavigateToModelPrompts: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val TAG = "CharacterCardGeneration"

    // 引导文案（顶部说明）
    val characterAssistantIntro = remember {
        val locale = Locale.getDefault().language
        if (locale == "zh" || locale == "zh-CN" || locale == "zh-TW") {
            """
            嗨嗨～这里是你的角色卡小助手(｡･ω･｡)ﾉ♡ 我会陪你一起把专属角色慢慢捏出来～
            我们按部就班来哦：先告诉我你的称呼，再说说你想要的角色大方向，比方说：
            - 角色名字和身份大概是怎样的？
            - 有哪些可爱的性格关键词？
            - 长相/发型/瞳色/穿搭想要什么感觉？
            - 有没有特别的小设定或能力？
            - 跟其他角色的关系要不要安排一点点？

            接下来我会一步步问你关键问题，帮你把细节补齐～
            """.trimIndent()
        } else {
            """
            Hi there~ This is your character card assistant (｡･ω･｡)ﾉ♡ I\'ll help you create your unique character step by step~
            Let\'s take it step by step: first tell me your name, then tell me what kind of character you want, for example:
            - What should the character\'s name and identity be?
            - What are some cute personality keywords?
            - What kind of look/hairstyle/eye color/outfit do you want?
            - Any special settings or abilities?
            - Should we arrange some relationships with other characters?

            Next, I\'ll ask you some key questions step by step to help you fill in the details~
            """.trimIndent()
        }
    }

    val listState = rememberLazyListState()
    val chatMessages = remember { mutableStateListOf<CharacterChatMessage>() }
    var userInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    // 角色卡数据
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val tagManager = remember { PromptTagManager.getInstance(context) }
    val chatHistoryManager = remember { PersonaCardChatHistoryManager.getInstance(context) }
    var allCharacterCards by remember { mutableStateOf(listOf<CharacterCard>()) }
    var allTags by remember { mutableStateOf(listOf<PromptTag>()) }
    var activeCardId by remember { mutableStateOf("") }
    var activeCard by remember { mutableStateOf<CharacterCard?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showMessageLimitWarning by remember { mutableStateOf(false) }
    var newCardName by remember { mutableStateOf("") }
    
    // 对话数量限制
    val MESSAGE_LIMIT = 40

    // 编辑器值
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editCharacterSetting by remember { mutableStateOf("") }
    var editOpeningStatement by remember { mutableStateOf("") }
    var editOtherContentChat by remember { mutableStateOf("") }
    var editOtherContentVoice by remember { mutableStateOf("") }
    var editAdvancedCustomPrompt by remember { mutableStateOf("") }
    var editMarks by remember { mutableStateOf("") }

    // 1. 一次性初始化：加载所有卡片和标签，并确定初始活跃卡片ID
    LaunchedEffect(Unit) {
        val initResult = withContext(Dispatchers.IO) {
            val cards = characterCardManager.getAllCharacterCards()
            val tags = tagManager.getAllTags()

            val currentPrompt = activePromptManager.getActivePrompt()
            var currentId = when (currentPrompt) {
                is ActivePrompt.CharacterCard -> currentPrompt.id
                is ActivePrompt.CharacterGroup -> null
            }

            // 如果没有活跃角色卡，则使用第一张卡
            if (currentId == null && cards.isNotEmpty()) {
                val firstCardId = cards.first().id
                activePromptManager.setActivePrompt(ActivePrompt.CharacterCard(firstCardId))
                currentId = firstCardId
            }

            Triple(cards, tags, currentId ?: "")
        }

        withContext(Dispatchers.Main) {
            allCharacterCards = initResult.first
            allTags = initResult.second
            activeCardId = initResult.third
        }
    }

    // 2. 响应式效果：当 activeCardId 变化时（初始化或切换），加载卡片详情并重置对话
    LaunchedEffect(activeCardId) {
        if (activeCardId.isBlank()) {
            // 没有活跃卡片的情况
            activeCard = null
            editName = ""; editDescription = ""; editCharacterSetting = ""; editOpeningStatement = ""
            editOtherContentChat = ""; editOtherContentVoice = ""; editAdvancedCustomPrompt = ""; editMarks = ""
            chatMessages.clear()
            chatMessages.add(CharacterChatMessage("assistant", context.getString(R.string.please_select_or_create_card)))
            return@LaunchedEffect
        }

        val loadResult = withContext(Dispatchers.IO) {
            val card = characterCardManager.getCharacterCard(activeCardId)
            val savedHistory = chatHistoryManager.loadChatHistory(activeCardId)
            Pair(card, savedHistory)
        }
        val cardResult = loadResult.first
        val savedHistory = loadResult.second

        withContext(Dispatchers.Main) {
            activeCard = cardResult

            // 更新编辑器内容
            cardResult?.let {
                editName = it.name
                editDescription = it.description
                editCharacterSetting = it.characterSetting
                editOpeningStatement = it.openingStatement
                editOtherContentChat = it.otherContentChat
                editOtherContentVoice = it.otherContentVoice
                editAdvancedCustomPrompt = it.advancedCustomPrompt
                editMarks = it.marks
            } ?: run {
                // 如果卡片加载失败，则清空编辑器
                editName = ""; editDescription = ""; editCharacterSetting = ""; editOpeningStatement = ""
                editOtherContentChat = ""; editOtherContentVoice = ""; editAdvancedCustomPrompt = ""; editMarks = ""
            }

            // 加载该角色卡的聊天历史
            chatMessages.clear()
            if (savedHistory.isNotEmpty()) {
                // 转换为界面使用的消息格式
                savedHistory.forEach { msg ->
                    chatMessages.add(CharacterChatMessage(msg.role, msg.content, msg.timestamp))
                }
            } else {
                // 如果没有历史记录，添加欢迎消息
                chatMessages.add(CharacterChatMessage("assistant",
                    context.getString(R.string.persona_generation_welcome, 
                    cardResult?.name ?: context.getString(R.string.new_character))
                ))
            }
        }
    }

    fun refreshData() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val cards = characterCardManager.getAllCharacterCards()
                val currentPrompt = activePromptManager.getActivePrompt()
                var id = when (currentPrompt) {
                    is ActivePrompt.CharacterCard -> currentPrompt.id
                    is ActivePrompt.CharacterGroup -> null
                }

                // 如果没有活跃角色卡，则使用第一张卡
                if (id == null && cards.isNotEmpty()) {
                    val firstCardId = cards.first().id
                    activePromptManager.setActivePrompt(ActivePrompt.CharacterCard(firstCardId))
                    id = firstCardId
                }

                Triple(cards, id ?: "", characterCardManager.getCharacterCard(id ?: ""))
            }

            withContext(Dispatchers.Main) {
                allCharacterCards = result.first
                activeCardId = result.second
                activeCard = result.third

                activeCard?.let { card ->
                    editName = card.name
                    editDescription = card.description
                    editCharacterSetting = card.characterSetting
                    editOpeningStatement = card.openingStatement
                    editOtherContentChat = card.otherContentChat
                    editOtherContentVoice = card.otherContentVoice
                    editAdvancedCustomPrompt = card.advancedCustomPrompt
                    editMarks = card.marks
                }
            }
        }
    }

    // 构建稳定的系统提示词
    fun buildSystemPrompt(): String {
        val useEnglish = !Locale.getDefault().language.lowercase().startsWith("zh")
        return FunctionalPrompts.personaCardGenerationSystemPrompt(useEnglish)
    }
    
    // 检查是否所有字段都已完成
    fun isCharacterCardComplete(): Boolean {
        return activeCard?.let { card ->
            listOf(
                card.name,
                card.description, 
                card.characterSetting,
                card.openingStatement,
                card.otherContentChat,
                card.advancedCustomPrompt,
                card.marks
            ).all { it.isNotBlank() }
        } ?: false
    }

    // 通过默认底层 AIService 发送消息
    suspend fun requestFromDefaultService(
        prompt: String,
        historyPairs: List<Pair<String, String>>,
        systemPrompt: String? = null
    ): Pair<Stream<String>, AIService> = withContext(Dispatchers.IO) {
        val aiService = EnhancedAIService
            .getInstance(context)
            .getAIServiceForFunction(FunctionType.CHAT)

        val fullHistory = mutableListOf<Pair<String, String>>()
        if (systemPrompt != null) {
            fullHistory.add("system" to systemPrompt)
        }
        fullHistory.addAll(historyPairs)

        val stream = aiService.sendMessage(
            context = context,
            chatHistory = (fullHistory + ("user" to prompt)).toPromptTurns(),
        )
        Pair(stream, aiService)
    }

    // 解析并执行工具调用
    suspend fun processToolInvocations(rawContent: String, assistantIndex: Int) {
        try {
            val invList = LocalCharacterToolExecutor.extractInvocations(rawContent)
            if (invList.isEmpty()) return

            AppLogger.d(TAG, "Found ${invList.size} tool invocation(s).")
            invList.forEach { (name, params) ->
                AppLogger.d(TAG, "Tool invocation: name='$name', params=$params")

                if (name != LocalCharacterToolExecutor.TOOL_NAME) {
                    AppLogger.w(TAG, "Skipping unknown tool: '$name'")
                    return@forEach
                }
                val field = params["field"].orEmpty().trim()
                val content = params["content"].orEmpty().trim()
                val cardId = activeCardId

                if (field.isBlank() || content.isBlank()) {
                    AppLogger.w(TAG, "Skipping tool call with blank field or content.")
                    return@forEach
                }

                val result = LocalCharacterToolExecutor.executeSaveCharacterInfo(context, cardId, field, content)
                if (result.success) {
                    AppLogger.d(TAG, "Tool '$name' executed successfully for field '$field'.")
                } else {
                    AppLogger.e(TAG, "Tool '$name' execution failed for field '$field': ${result.error}")
                }

                // 刷新数据
                withContext(Dispatchers.Main) {
                    refreshData()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Local tool processing failed: ${e.message}", e)
        }
    }

    // 保存聊天历史
    fun saveChatHistory() {
        scope.launch {
            val cardId = activeCardId
            val chatSnapshot = withContext(Dispatchers.Main.immediate) {
                chatMessages.toList()
            }
            val messages = chatSnapshot.map { msg ->
                PersonaCardChatHistoryManager.ChatMessage(msg.role, msg.content, msg.timestamp)
            }
            withContext(Dispatchers.IO) {
                chatHistoryManager.saveChatHistory(cardId, messages)
            }
        }
    }

    fun sendMessage() {
        if (userInput.isBlank() || isGenerating) return
        
        // 检查对话数量限制
        if (chatMessages.size >= MESSAGE_LIMIT) {
            showMessageLimitWarning = true
            return
        }
        
        val input = userInput
        userInput = ""

        scope.launch(Dispatchers.Main) {
            chatMessages.add(CharacterChatMessage("user", input))
            saveChatHistory() // 保存用户消息
            isGenerating = true

            // 检查是否已完成，如果已完成则直接结束
            if (isCharacterCardComplete()) {
                chatMessages.add(CharacterChatMessage("assistant", context.getString(R.string.character_card_complete)))
                saveChatHistory() // 保存完成消息
                isGenerating = false
                scope.launch { listState.animateScrollToItem(chatMessages.lastIndex) }
                return@launch
            }

            // 构建稳定的上下文
            val systemPrompt = buildSystemPrompt()
            // val characterStatus = buildCharacterStatus() // REMOVED: 不再每次都发送状态
            
            val historySnapshot = chatMessages.toList()
            val historyPairs = withContext(Dispatchers.Default) {
                historySnapshot.map { it.role to it.content }
            }

            val (stream, aiService) = requestFromDefaultService(input, historyPairs, systemPrompt)

            // 提前插入占位的"生成中…"助手消息
            val generatingText = context.getString(R.string.generating)
            chatMessages.add(CharacterChatMessage("assistant", generatingText))
            val assistantIndex = chatMessages.lastIndex

            val toolTagRegex = Regex(
                """\s*(?:${ChatMarkupRegex.toolTag.pattern})\s*""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val toolResultRegex = Regex(
                """\s*(?:${ChatMarkupRegex.toolResultTag.pattern})\s*""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val statusRegex = Regex("(?s)\\s*<status\\b[^>]*>[\\s\\S]*?</status>\\s*")

            // 原始缓冲，用于工具解析
            val rawBuffer = StringBuilder()
            var firstChunkReceived = false

            try {
                withContext(Dispatchers.IO) {
                    stream.collect { chunk ->
                        rawBuffer.append(chunk)
                        withContext(Dispatchers.Main) {
                            if (!firstChunkReceived) {
                                firstChunkReceived = true
                                isGenerating = false
                            }
                            val sanitized = (chatMessages[assistantIndex].content.replace(generatingText, "") + chunk)
                                .replace(toolTagRegex, "")
                                .replace(toolResultRegex, "")
                                .replace(statusRegex, "")
                                .replace(Regex("(\\r?\\n){2,}"), "\n")
                            chatMessages[assistantIndex] = chatMessages[assistantIndex].copy(content = sanitized)
                            scope.launch { listState.animateScrollToItem(chatMessages.lastIndex) }
                        }
                    }
                }

                // 流结束后解析并执行工具
                withContext(Dispatchers.IO) {
                    processToolInvocations(rawBuffer.toString(), assistantIndex)
                }
                
                // 保存助手回复
                saveChatHistory()
            } catch (e: Exception) {
                chatMessages.add(
                    CharacterChatMessage(
                        role = "assistant",
                        content = context.getString(R.string.send_failed, e.message ?: "Unknown error")
                    )
                )
                saveChatHistory() // 保存错误消息
            } finally {
                isGenerating = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(windowInsets = WindowInsets(0, 0, 0, 0)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 关闭按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.close() } }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = context.getString(R.string.close)
                            )
                        }
                    }

                    Text(context.getString(R.string.character_card_config), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    // 选择不同角色卡
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = activeCard?.name ?: context.getString(R.string.no_character_card),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(context.getString(R.string.current_character_card)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            allCharacterCards.forEach { card ->
                                DropdownMenuItem(
                                    text = { Text(card.name) },
                                    onClick = {
                                        expanded = false
                                        scope.launch {
                                            activePromptManager.setActivePrompt(ActivePrompt.CharacterCard(card.id))
                                            activeCardId = card.id // 更新ID以触发Effect
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.create_new_character_card)) },
                                onClick = {
                                    expanded = false
                                    showCreateDialog = true
                                }
                            )
                        }
                    }

                    // 删除当前角色卡（默认卡不可删）
                    if (activeCard?.isDefault == false) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(context.getString(R.string.delete_current_character_card))
                            }
                        }
                    }

                    // 新建角色卡弹窗
                    if (showCreateDialog) {
                        AlertDialog(
                            onDismissRequest = { showCreateDialog = false },
                            title = { Text(context.getString(R.string.new_character_card)) },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = newCardName,
                                        onValueChange = { newCardName = it },
                                        singleLine = true,
                                        label = { Text(context.getString(R.string.character_card_name)) },
                                        placeholder = { Text(context.getString(R.string.character_card_name_example)) }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val name = newCardName.trim().ifBlank { context.getString(R.string.new_character) }
                                    showCreateDialog = false
                                    newCardName = ""
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val newCard = CharacterCard(
                                                id = "",
                                                name = name,
                                                description = "",
                                                characterSetting = CharacterCardBilingualData.getDefaultCharacterSetting(context),
                                                otherContentChat = CharacterCardBilingualData.getDefaultOtherContentChat(context),
                                                otherContentVoice = "",
                                                attachedTagIds = emptyList(),
                                                advancedCustomPrompt = "",
                                                isDefault = false
                                            )
                                            val newId = characterCardManager.createCharacterCard(newCard)
                                            activePromptManager.setActivePrompt(ActivePrompt.CharacterCard(newId))
                                        }
                                        refreshData()
                                    }
                                }) { Text(context.getString(R.string.create)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCreateDialog = false }) { Text(context.getString(R.string.cancel)) }
                            }
                        )
                    }

                    // 删除角色卡确认对话框
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text(context.getString(R.string.delete_character_card)) },
                            text = { Text(context.getString(R.string.confirm_delete_character_card)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    scope.launch {
                                        activeCard?.let { card ->
                                            withContext(Dispatchers.IO) {
                                                characterCardManager.deleteCharacterCard(card.id)
                                                // 删除后，如果没有活跃角色卡，则会回退到列表中的第一项
                                            }
                                            refreshData()
                                        }
                                    }
                                }) { Text(context.getString(R.string.delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text(context.getString(R.string.cancel)) }
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(context.getString(R.string.current_character_card_content), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    
                    // 角色名称
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { newValue ->
                            editName = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(name = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 角色描述
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { newValue ->
                            editDescription = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(description = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 角色设定
                    OutlinedTextField(
                        value = editCharacterSetting,
                        onValueChange = { newValue ->
                            editCharacterSetting = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(characterSetting = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_setting)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 开场白
                    OutlinedTextField(
                        value = editOpeningStatement,
                        onValueChange = { newValue ->
                            editOpeningStatement = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(openingStatement = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.opening_statement)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 其他内容（聊天）
                    OutlinedTextField(
                        value = editOtherContentChat,
                        onValueChange = { newValue ->
                            editOtherContentChat = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(otherContentChat = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.other_content_chat)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )

                    Spacer(Modifier.height(8.dp))

                    // 其他内容（语音）
                    OutlinedTextField(
                        value = editOtherContentVoice,
                        onValueChange = { newValue ->
                            editOtherContentVoice = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(otherContentVoice = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.other_content_voice)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 高级自定义提示词
                    OutlinedTextField(
                        value = editAdvancedCustomPrompt,
                        onValueChange = { newValue ->
                            editAdvancedCustomPrompt = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(advancedCustomPrompt = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.advanced_custom_prompt)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 备注信息
                    OutlinedTextField(
                        value = editMarks,
                        onValueChange = { newValue ->
                            editMarks = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(marks = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_marks)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.persona_card_generation_title), 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showClearHistoryConfirm = true }) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = context.getString(R.string.clear_chat_history)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { scope.launch { drawerState.open() } }) {
                    Text(activeCard?.name ?: context.getString(R.string.no_character_card))
                }
            }

            // 聊天列表
            val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            LaunchedEffect(chatMessages.size) {
                if (chatMessages.isNotEmpty()) {
                    listState.animateScrollToItem(chatMessages.lastIndex)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                items(chatMessages) { msg ->
                    val isUser = msg.role == "user"
                    val bubbleContainer = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val bubbleTextColor = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (!isUser) {
                            Card(colors = CardDefaults.cardColors(containerColor = bubbleContainer)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer {
                                        Text(msg.content, color = bubbleTextColor)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        timeFormatter.format(Date(msg.timestamp)),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                            Card(colors = CardDefaults.cardColors(containerColor = bubbleContainer)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer {
                                        Text(msg.content, color = bubbleTextColor)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        timeFormatter.format(Date(msg.timestamp)),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            // 底部输入栏
            Surface(color = Color.Transparent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            placeholder = { Text(if (isGenerating) context.getString(R.string.currently_generating) else context.getString(R.string.describe_character_hint)) },
                            enabled = !isGenerating && chatMessages.size < MESSAGE_LIMIT,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent
                            )
                        )
                        // 对话计数器 - 右上角小标签
                        Text(
                            text = "${chatMessages.size}/$MESSAGE_LIMIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chatMessages.size >= MESSAGE_LIMIT) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 12.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { if (!isGenerating) sendMessage() },
                        enabled = !isGenerating && chatMessages.size < MESSAGE_LIMIT
                    ) {
                        Icon(
                            imageVector = if (isGenerating) Icons.Filled.HourglassBottom else Icons.Filled.Send,
                            contentDescription = if (isGenerating) context.getString(R.string.generating) else context.getString(R.string.send)
                        )
                    }
                }
            }
        }
    }
    
    // 对话数量限制警告对话框
    if (showMessageLimitWarning) {
        AlertDialog(
            onDismissRequest = { showMessageLimitWarning = false },
            title = { Text(context.getString(R.string.message_limit_reached_title)) },
            text = { 
                Text(context.getString(R.string.message_limit_reached_message, MESSAGE_LIMIT))
            },
            confirmButton = {
                TextButton(onClick = {
                    showMessageLimitWarning = false
                    showClearHistoryConfirm = true
                }) { Text(context.getString(R.string.go_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showMessageLimitWarning = false }) { Text(context.getString(R.string.cancel)) }
            }
        )
    }
    
    // 清空对话记录确认对话框
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text(context.getString(R.string.clear_chat_history)) },
            text = { Text(context.getString(R.string.confirm_clear_chat_history)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            chatHistoryManager.clearChatHistory(activeCardId)
                        }
                        // 清空界面显示的消息
                        chatMessages.clear()
                        // 添加欢迎消息
                        chatMessages.add(CharacterChatMessage("assistant",
                            context.getString(R.string.persona_generation_welcome, 
                            activeCard?.name ?: context.getString(R.string.new_character))
                        ))
                        saveChatHistory()
                    }
                }) { Text(context.getString(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text(context.getString(R.string.cancel)) }
            }
        )
    }
} 
