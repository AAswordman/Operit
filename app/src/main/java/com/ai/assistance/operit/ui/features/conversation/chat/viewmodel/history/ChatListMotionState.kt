package com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.history

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 删除进度归属于页面模型，滚动离屏或收起侧栏不会取消已确认的删除。 */
class ChatListMotionState {
    private val _deletions = MutableStateFlow<Map<String, Float>>(emptyMap())
    val deletions = _deletions.asStateFlow()
    private val _newChatId = MutableStateFlow<String?>(null)
    val newChatId = _newChatId.asStateFlow()

    fun onChatCreated(chatId: String) {
        _newChatId.value = chatId
    }

    fun consumeIntro(chatId: String) {
        _newChatId.compareAndSet(chatId, null)
    }

    fun delete(
        chatId: String,
        scope: CoroutineScope,
        motionEnabled: Boolean,
        performDelete: (String, (Boolean) -> Unit) -> Unit,
    ) {
        if (chatId in _deletions.value) return
        _deletions.update { it + (chatId to 0f) }
        scope.launch {
            try {
                if (motionEnabled) {
                    kotlinx.coroutines.withContext(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
                        animate(0f, 1f, animationSpec = tween(520, easing = LinearOutSlowInEasing)) { value, _ ->
                            _deletions.update { it + (chatId to value) }
                        }
                    }
                }
                performDelete(chatId) {
                    // 数据源负责真正移除行；失败时清掉效果，原始标题立即恢复。
                    _deletions.update { it - chatId }
                }
            } catch (error: Exception) {
                _deletions.update { it - chatId }
                throw error
            }
        }
    }
}
