package com.ai.assistance.operit.ui.features.conversation.chat.viewmodel

import android.content.Intent
import com.ai.assistance.operit.core.chat.reference.AiReference
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 委托类，负责管理UI状态相关功能 */
class UiStateDelegate {
    // UI状态
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _popupMessage = MutableStateFlow<String?>(null)
    val popupMessage: StateFlow<String?> = _popupMessage.asStateFlow()

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
