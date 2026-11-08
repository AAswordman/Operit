package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.ui

import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptLogItem
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptSupportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class WebSessionUserscriptUiState(
    val supportState: UserscriptSupportState = UserscriptSupportState(isSupported = false),
    val installedScripts: List<UserscriptListItem> = emptyList(),
    val recentLogs: List<UserscriptLogItem> = emptyList(),
    val currentPageStatuses: Map<Long, UserscriptPageRuntimeStatus> = emptyMap(),
    val pendingInstall: UserscriptInstallPreview? = null
)

internal class WebSessionUserscriptUiStateStore(
    initialSupportState: UserscriptSupportState
) {
    private val mutableState = MutableStateFlow(WebSessionUserscriptUiState(supportState = initialSupportState))
    val state: StateFlow<WebSessionUserscriptUiState> = mutableState.asStateFlow()

    fun updateSupportState(value: UserscriptSupportState) {
        mutableState.update { current -> current.copy(supportState = value) }
    }

    fun updateScripts(items: List<UserscriptListItem>) {
        mutableState.update { current -> current.copy(installedScripts = items) }
    }

    fun updateLogs(items: List<UserscriptLogItem>) {
        mutableState.update { current -> current.copy(recentLogs = items) }
    }

    fun updateCurrentPageStatuses(items: Map<Long, UserscriptPageRuntimeStatus>) {
        mutableState.update { current -> current.copy(currentPageStatuses = items) }
    }

    fun setPendingInstall(preview: UserscriptInstallPreview?) {
        mutableState.update { current -> current.copy(pendingInstall = preview) }
    }
}
