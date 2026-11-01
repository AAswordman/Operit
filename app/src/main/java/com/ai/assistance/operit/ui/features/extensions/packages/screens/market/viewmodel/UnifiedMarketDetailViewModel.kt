package com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2Reaction
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.extensions.packages.market.CommentPostSuccessBehavior
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketEntryInstallController
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInstallProgress
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInstallStateStore
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketLocalInstallState
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInteractionController
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInteractionMessages
import com.ai.assistance.operit.ui.features.extensions.packages.market.resolveMarketLocalInstallStates
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnifiedMarketDetailViewModel(
    private val context: Context
) : ViewModel() {
    private val marketStatsApiService = MarketStatsApiService()
    private val githubApiService = GitHubApiService(context)
    private val githubAuth = GitHubAuthPreferences.getInstance(context)
    private val installController = MarketEntryInstallController(context, marketStatsApiService)
    private val packageManager =
        PackageManager.getInstance(context.applicationContext, AIToolHandler.getInstance(context.applicationContext))

    private val _entry = MutableStateFlow<MarketV2Entry?>(null)
    val entry: StateFlow<MarketV2Entry?> = _entry.asStateFlow()

    private val _isLoadingEntry = MutableStateFlow(false)
    val isLoadingEntry: StateFlow<Boolean> = _isLoadingEntry.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val installStates: StateFlow<Map<String, MarketInstallProgress>> = MarketInstallStateStore.installStates
    private val _localInstallStates = MutableStateFlow<Map<String, MarketLocalInstallState>>(emptyMap())
    val localInstallStates: StateFlow<Map<String, MarketLocalInstallState>> = _localInstallStates.asStateFlow()
    private val marketInteractionController =
        MarketInteractionController(
            scope = viewModelScope,
            context = context,
            marketApiService = marketStatsApiService,
            githubApiService = githubApiService,
            logTag = TAG,
            onError = { _errorMessage.value = it },
            messages =
                MarketInteractionMessages(
                    commentLoadFailed = { context.getString(R.string.skillmarket_load_comments_failed, it) },
                    commentLoadError = { context.getString(R.string.skillmarket_load_comments_failed, it) },
                    commentPostFailed = { context.getString(R.string.skillmarket_post_comment_failed, it) },
                    commentPostError = { context.getString(R.string.skillmarket_post_comment_failed, it) },
                    reactionFailed = { context.getString(R.string.skillmarket_like_failed, it) },
                    reactionError = { context.getString(R.string.skillmarket_like_failed, it) }
                )
        )

    val entryComments = marketInteractionController.entryComments
    val isLoadingComments = marketInteractionController.isLoadingComments
    val isPostingComment = marketInteractionController.isPostingComment
    val isDeletingComment = marketInteractionController.isDeletingComment
    val entryReactions: StateFlow<Map<String, List<MarketV2Reaction>>> = marketInteractionController.entryReactions
    val isLoadingReactions = marketInteractionController.isLoadingReactions
    val isReacting = marketInteractionController.isReacting
    val userAvatarCache = marketInteractionController.userAvatarCache
    val repositoryCache = marketInteractionController.repositoryCache

    fun loadEntry(initialEntry: MarketV2Entry) {
        val entryId = initialEntry.id
        if (entryId.isBlank()) {
            _entry.value = initialEntry
            return
        }

        _entry.value = initialEntry
        _isLoadingEntry.value = false
        viewModelScope.launch {
            refreshLocalInstallStates(initialEntry)
        }
    }

    fun loadEntryComments(entryId: String) {
        marketInteractionController.loadEntryComments(entryId, perPage = 50)
    }

    fun postEntryComment(entryId: String, body: String, parentId: String? = null) {
        val text = body.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.mcp_plugin_login_required)
                return@launch
            }
            marketInteractionController.postEntryComment(
                entryId = entryId,
                body = text,
                parentId = parentId,
                successBehavior = CommentPostSuccessBehavior.RELOAD_FROM_SERVER,
                perPage = 50
            )
        }
    }

    fun editComment(entryId: String, commentId: String, body: String) {
        val text = body.trim()
        if (text.isBlank() || commentId.isBlank()) return
        marketInteractionController.editEntryComment(entryId, commentId, text)
    }

    fun deleteComment(entryId: String, commentId: String) {
        if (commentId.isBlank()) return
        marketInteractionController.deleteEntryComment(entryId, commentId)
    }

    fun loadEntryReactions(entry: MarketV2Entry) {
        marketInteractionController.loadEntryReactions(
            entryId = entry.id,
            entryLikes = entry.marketLikeCount()
        )
    }

    fun addReactionToEntry(entryId: String) {
        marketInteractionController.addReactionToEntry(entryId)
    }

    fun installEntry(entry: MarketV2Entry) {
        val entryId = entry.id.trim()
        if (!MarketInstallStateStore.start(entryId)) return
        viewModelScope.launch {
            try {
                installController.install(entry) { stage, transfer ->
                    MarketInstallStateStore.update(entryId, stage, transfer)
                }
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.mcp_market_install_failed_with_error, e.message ?: "")
                AppLogger.e(TAG, "Failed to install market entry ${entry.id}", e)
            } finally {
                try {
                    refreshLocalInstallStates(entry)
                } finally {
                    MarketInstallStateStore.finish(entryId)
                }
            }
        }
    }

    private suspend fun refreshLocalInstallStates(entry: MarketV2Entry) {
        _localInstallStates.value =
            withContext(Dispatchers.IO) {
                resolveMarketLocalInstallStates(
                    context = context.applicationContext,
                    packageManager = packageManager,
                    entries = listOf(entry)
                )
            }
    }

    fun fetchUserAvatar(username: String) {
        marketInteractionController.fetchUserAvatar(username)
    }

    fun fetchRepositoryInfo(repositoryUrl: String) {
        marketInteractionController.fetchRepositoryInfo(repositoryUrl)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UnifiedMarketDetailViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UnifiedMarketDetailViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "UnifiedMarketDetailViewModel"
    }
}

private fun MarketV2Entry.marketLikeCount(): Int {
    return stats?.likes ?: reactions.sumOf { reaction ->
        val key = reaction.reaction.ifBlank { reaction.content }
        if (key == "+1" || key.equals("like", ignoreCase = true)) reaction.total.coerceAtLeast(1) else 0
    }
}
