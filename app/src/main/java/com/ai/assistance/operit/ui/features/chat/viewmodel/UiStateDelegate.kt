package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Intent
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatToastEvent(
    val id: Long,
    val message: String,
    val durationMs: Long? = null,
)

/** 委托类，负责管理UI状态相关功能 */
class UiStateDelegate {
    // UI状态
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _popupMessage = MutableStateFlow<String?>(null)
    val popupMessage: StateFlow<String?> = _popupMessage.asStateFlow()

    private val toastLock = Any()
    private val toastQueue = ArrayDeque<ChatToastEvent>()
    private var nextToastEventId = 0L
    private val _toastEvent = MutableStateFlow<ChatToastEvent?>(null)
    val toastEvent: StateFlow<ChatToastEvent?> = _toastEvent.asStateFlow()

    private val _masterPermissionLevel = MutableStateFlow(PermissionLevel.ASK)
    val masterPermissionLevel: StateFlow<PermissionLevel> = _masterPermissionLevel.asStateFlow()

    // 文件选择器请求
    private val _fileChooserRequest = MutableStateFlow<Intent?>(null)
    val fileChooserRequest: StateFlow<Intent?> = _fileChooserRequest.asStateFlow()

    /** 显示错误消息 */
    fun showErrorMessage(message: String) {
        _errorMessage.value = message
    }

    /** 清除错误消息 */
    fun clearError() {
        _errorMessage.value = null
    }

    /** 显示弹出消息 */
    fun showPopupMessage(message: String) {
        _popupMessage.value = message
    }

    /** 清除弹出消息 */
    fun clearPopupMessage() {
        _popupMessage.value = null
    }

    /** 显示Toast消息 */
    fun showToast(message: String) {
        synchronized(toastLock) {
            val event = ChatToastEvent(id = ++nextToastEventId, message = message)
            // Preserve every user-visible notice; a single String StateFlow coalesces same-text events.
            if (_toastEvent.value == null) {
                _toastEvent.value = event
            } else {
                toastQueue.addLast(event)
            }
        }
    }

    /** 显示可替换的重试提示；下一次重试状态到来时立即覆盖当前提示。 */
    fun showRetryToast(message: String, durationMs: Long) {
        if (message.isBlank()) {
            return
        }
        synchronized(toastLock) {
            _toastEvent.value =
                ChatToastEvent(
                    id = ++nextToastEventId,
                    message = message,
                    durationMs = durationMs.coerceAtLeast(1L)
                )
        }
    }

    /** 清除Toast消息 */
    fun clearToastEvent(eventId: Long) {
        synchronized(toastLock) {
            if (_toastEvent.value?.id != eventId) {
                return
            }
            _toastEvent.value = if (toastQueue.isEmpty()) null else toastQueue.removeFirst()
        }
    }

    /** 更新主权限级别 */
    fun updateMasterPermissionLevel(level: PermissionLevel) {
        _masterPermissionLevel.value = level
    }

    /** 请求文件选择器 */
    fun requestFileChooser(intent: Intent) {
        _fileChooserRequest.value = intent
    }

    /** 清除文件选择器请求 */
    fun clearFileChooserRequest() {
        _fileChooserRequest.value = null
    }
}
