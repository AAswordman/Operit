package com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatHistoryDisplayMode
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.flowOf

// 主侧栏和主聊天页共用同一实例，避免筛选设置和角色自动切换各自保存一份状态。
val LocalMainChatViewModel = staticCompositionLocalOf<ChatViewModel> { error("未提供主聊天 ViewModel") }
val LocalMainChatSidebarState = compositionLocalOf<ChatSidebarState> { error("未提供主侧栏状态") }
val LocalOpenMainChatSidebar = staticCompositionLocalOf<() -> Unit> { { error("未提供主侧栏入口") } }

// 导航离页检查通过后再执行对话操作，避免被阻止的跳转先修改当前对话。
val LocalRunMainChatAction = staticCompositionLocalOf<(() -> Unit) -> Unit> { { error("未提供主聊天导航") } }

data class ChatSidebarState(
    val activePrompt: ActivePrompt,
    val activeCharacterCard: CharacterCard?,
    val activeCharacterGroup: CharacterGroupCard?,
    val displayedChatHistories: List<ChatHistory>,
    val historyDisplayMode: ChatHistoryDisplayMode,
    val onDisplayModeChange: (ChatHistoryDisplayMode) -> Unit,
    val autoSwitchCharacterCard: Boolean,
    val onAutoSwitchCharacterCardChange: (Boolean) -> Unit,
    val autoSwitchChatOnCharacterSelect: Boolean,
    val onAutoSwitchChatOnCharacterSelectChange: (Boolean) -> Unit,
)

@Composable
fun rememberChatSidebarState(actualViewModel: ChatViewModel): ChatSidebarState {
    val context = LocalContext.current
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )
    val activeCharacterCard by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
            is ActivePrompt.CharacterGroup -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCharacterGroup by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterGroup -> characterGroupCardManager.getCharacterGroupCardFlow(prompt.id)
            is ActivePrompt.CharacterCard -> flowOf(null)
        }
    }.collectAsState(initial = null)
    var historyDisplayMode by rememberLocal(
        "chat_history_display_mode",
        ChatHistoryDisplayMode.BY_FOLDER
    )
    var autoSwitchCharacterCard by rememberLocal(
        "chat_history_auto_switch_character_card",
        false
    )
    var autoSwitchChatOnCharacterSelect by rememberLocal(
        "chat_history_auto_switch_chat_on_character_select",
        false
    )
    // 筛选不触发导航，否则会绕过离页检查并重新打开用户刚清空的对话。
    val displayedChatHistories = remember(
        chatHistories,
        activePrompt,
        activeCharacterCard,
        activeCharacterGroup,
        historyDisplayMode
    ) {
        when (historyDisplayMode) {
            ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY -> {
                when (activePrompt) {
                    is ActivePrompt.CharacterGroup -> {
                        val group = activeCharacterGroup ?: return@remember emptyList()
                        chatHistories.filter { history ->
                            history.characterGroupId == group.id
                        }
                    }
                    is ActivePrompt.CharacterCard -> {
                        val activeCard = activeCharacterCard ?: return@remember emptyList()
                        chatHistories.filter { history ->
                            val historyCard = history.characterCardName
                            if (activeCard.isDefault) {
                                history.characterGroupId == null && (historyCard == null || historyCard == activeCard.name)
                            } else {
                                history.characterGroupId == null && historyCard == activeCard.name
                            }
                        }
                    }
                }
            }
            else -> chatHistories
        }
    }
    LaunchedEffect(autoSwitchCharacterCard) {
        actualViewModel.setAutoSwitchCharacterCard(autoSwitchCharacterCard)
    }
    LaunchedEffect(autoSwitchChatOnCharacterSelect) {
        actualViewModel.setAutoSwitchChatOnCharacterSelect(autoSwitchChatOnCharacterSelect)
    }
    return ChatSidebarState(
        activePrompt = activePrompt,
        activeCharacterCard = activeCharacterCard,
        activeCharacterGroup = activeCharacterGroup,
        displayedChatHistories = displayedChatHistories,
        historyDisplayMode = historyDisplayMode,
        onDisplayModeChange = { historyDisplayMode = it },
        autoSwitchCharacterCard = autoSwitchCharacterCard,
        onAutoSwitchCharacterCardChange = { autoSwitchCharacterCard = it },
        autoSwitchChatOnCharacterSelect = autoSwitchChatOnCharacterSelect,
        onAutoSwitchChatOnCharacterSelectChange = { autoSwitchChatOnCharacterSelect = it },
    )
}
