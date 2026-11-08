package com.ai.assistance.operit.ui.features.extensions.packages.market

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.Context
import android.content.SharedPreferences
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.MarketV2Comment
import com.ai.assistance.operit.data.api.MarketV2Reaction
import com.ai.assistance.operit.data.api.GitHubRepository
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CommentPostSuccessBehavior {
    APPEND_TO_CACHE,
    RELOAD_FROM_SERVER
}

data class MarketInteractionMessages(
    val commentLoadFailed: (String) -> String,
    val commentLoadError: (String) -> String,
    val commentPostFailed: (String) -> String,
    val commentPostError: (String) -> String,
    val reactionFailed: (String) -> String,
    val reactionError: (String) -> String,
    val commentPostSuccess: String? = null,
    val reactionSuccess: String? = null
)

class MarketInteractionController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val marketApiService: MarketStatsApiService,
    private val githubApiService: GitHubApiService,
    private val logTag: String,
    private val onError: (String) -> Unit,
    private val messages: MarketInteractionMessages,
    private val avatarCachePrefs: SharedPreferences? = null
) {
    private val _entryComments = MutableStateFlow<Map<String, List<MarketV2Comment>>>(emptyMap())
    val entryComments: StateFlow<Map<String, List<MarketV2Comment>>> = _entryComments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow<Set<String>>(emptySet())
    val isLoadingComments: StateFlow<Set<String>> = _isLoadingComments.asStateFlow()

    private val _isPostingComment = MutableStateFlow<Set<String>>(emptySet())
    val isPostingComment: StateFlow<Set<String>> = _isPostingComment.asStateFlow()

    private val _isDeletingComment = MutableStateFlow<Set<String>>(emptySet())
    val isDeletingComment: StateFlow<Set<String>> = _isDeletingComment.asStateFlow()

    private val _userAvatarCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val userAvatarCache: StateFlow<Map<String, String>> = _userAvatarCache.asStateFlow()

    private val _entryReactions = MutableStateFlow<Map<String, List<MarketV2Reaction>>>(emptyMap())
    val entryReactions: StateFlow<Map<String, List<MarketV2Reaction>>> = _entryReactions.asStateFlow()

    private val _isLoadingReactions = MutableStateFlow<Set<String>>(emptySet())
    val isLoadingReactions: StateFlow<Set<String>> = _isLoadingReactions.asStateFlow()

    private val _isReacting = MutableStateFlow<Set<String>>(emptySet())
    val isReacting: StateFlow<Set<String>> = _isReacting.asStateFlow()

    private val _repositoryCache = MutableStateFlow<Map<String, GitHubRepository>>(emptyMap())
    val repositoryCache: StateFlow<Map<String, GitHubRepository>> = _repositoryCache.asStateFlow()

    init {
        loadAvatarCacheFromPrefs()
    }

    fun clearReactionsCache() {
        _entryReactions.value = emptyMap()
    }

    fun loadEntryComments(entryId: String, perPage: Int = 50) {
        val id = entryId.trim()
        if (id.isBlank()) return
        scope.launch {
            try {
                _isLoadingComments.value = _isLoadingComments.value + id
                val result = marketApiService.getComments(id).map { comments -> comments.take(perPage) }
                result.fold(
                    onSuccess = { comments ->
                        _entryComments.value = _entryComments.value.toMutableMap().also { it[id] = comments }
                        AppLogger.d(logTag, "Loaded ${comments.size} comments for entry $id")
                    },
                    onFailure = { error ->
                        if (error.message?.contains("404") == true) {
                            _entryComments.value = _entryComments.value.toMutableMap().also { it[id] = emptyList() }
                        } else {
                            onError(messages.commentLoadFailed(error.message.orEmpty()))
                            AppLogger.e(logTag, "Failed to load comments for entry $id", error)
                        }
                    }
                )
            } catch (e: Exception) {
                onError(messages.commentLoadError(e.message.orEmpty()))
                AppLogger.e(logTag, "Exception while loading comments for entry $id", e)
            } finally {
                _isLoadingComments.value = _isLoadingComments.value - id
            }
        }
    }

    fun postEntryComment(
        entryId: String,
        body: String,
        parentId: String? = null,
        successBehavior: CommentPostSuccessBehavior = CommentPostSuccessBehavior.APPEND_TO_CACHE,
        perPage: Int = 50
    ) {
        val id = entryId.trim()
        if (id.isBlank()) return
        scope.launch {
            try {
                _isPostingComment.value = _isPostingComment.value + id
                val result = marketApiService.postComment(id, body, parentId)
                result.fold(
                    onSuccess = { newComment ->
                        when (successBehavior) {
                            CommentPostSuccessBehavior.APPEND_TO_CACHE -> {
                                val existing = _entryComments.value[id].orEmpty()
                                _entryComments.value = _entryComments.value.toMutableMap().also {
                                    it[id] = existing + newComment
                                }
                            }
                            CommentPostSuccessBehavior.RELOAD_FROM_SERVER -> loadEntryComments(id, perPage)
                        }
                        messages.commentPostSuccess?.let { OKToast.success(context, it, OKToast.LENGTH_SHORT).show() }
                        AppLogger.d(logTag, "Posted comment to entry $id")
                    },
                    onFailure = { error ->
                        onError(messages.commentPostFailed(error.message.orEmpty()))
                        AppLogger.e(logTag, "Failed to post comment to entry $id", error)
                    }
                )
            } catch (e: Exception) {
                onError(messages.commentPostError(e.message.orEmpty()))
                AppLogger.e(logTag, "Exception while posting comment to entry $id", e)
            } finally {
                _isPostingComment.value = _isPostingComment.value - id
            }
        }
    }

    fun editEntryComment(
        entryId: String,
        commentId: String,
        body: String,
        perPage: Int = 50
    ) {
        val id = entryId.trim()
        if (id.isBlank()) return
        scope.launch {
            try {
                val result = marketApiService.editComment(commentId, body)
                result.fold(
                    onSuccess = {
                        val existing = _entryComments.value[id].orEmpty()
                        _entryComments.value = _entryComments.value.toMutableMap().also {
                            it[id] = existing.map { comment ->
                                if (comment.id == commentId) comment.copy(body = body, updatedAt = java.time.Instant.now().toString())
                                else comment
                            }
                        }
                        AppLogger.d(logTag, "Edited comment $commentId on entry $id")
                    },
                    onFailure = { error ->
                        onError("Failed to edit comment: ${error.message}")
                        AppLogger.e(logTag, "Failed to edit comment $commentId on entry $id", error)
                    }
                )
            } catch (e: Exception) {
                onError("Failed to edit comment: ${e.message}")
                AppLogger.e(logTag, "Exception while editing comment $commentId on entry $id", e)
            }
        }
    }

    fun deleteEntryComment(
        entryId: String,
        commentId: String,
        perPage: Int = 50
    ) {
        val id = entryId.trim()
        if (id.isBlank()) return
        scope.launch {
            try {
                _isDeletingComment.value = _isDeletingComment.value + commentId
                val result = marketApiService.deleteComment(commentId)
                result.fold(
                    onSuccess = {
                        val existing = _entryComments.value[id].orEmpty()
                        _entryComments.value = _entryComments.value.toMutableMap().also {
                            it[id] = existing.filter { comment -> comment.id != commentId }
                        }
                        AppLogger.d(logTag, "Deleted comment $commentId on entry $id")
                    },
                    onFailure = { error ->
                        onError("Failed to delete comment: ${error.message}")
                        AppLogger.e(logTag, "Failed to delete comment $commentId on entry $id", error)
                    }
                )
            } catch (e: Exception) {
                onError("Failed to delete comment: ${e.message}")
                AppLogger.e(logTag, "Exception while deleting comment $commentId on entry $id", e)
            } finally {
                _isDeletingComment.value = _isDeletingComment.value - commentId
            }
        }
    }

    fun loadEntryReactions(entryId: String, entryLikes: Int, force: Boolean = false) {
        val id = entryId.trim()
        if (id.isBlank() || id in _isLoadingReactions.value) return
        if (!force && _entryReactions.value.containsKey(id)) return
        val reactions =
            if (entryLikes <= 0) {
                emptyList()
            } else {
                listOf(MarketV2Reaction(reaction = "+1", content = "+1", total = entryLikes))
            }
        _entryReactions.value = _entryReactions.value.toMutableMap().also {
            it[id] = reactions
        }
        AppLogger.d(logTag, "Loaded reactions for entry $id from entry payload")
    }

    fun addReactionToEntry(entryId: String) {
        val id = entryId.trim()
        if (id.isBlank() || id in _isReacting.value) return
        scope.launch {
            try {
                _isReacting.value = _isReacting.value + id
                val result = marketApiService.addReaction(id)
                result.fold(
                    onSuccess = { reaction ->
                        val existing = _entryReactions.value[id].orEmpty()
                        _entryReactions.value = _entryReactions.value.toMutableMap().also {
                            it[id] = existing + reaction
                        }
                        messages.reactionSuccess?.let { OKToast.success(context, it, OKToast.LENGTH_SHORT).show() }
                        AppLogger.d(logTag, "Added reaction to entry $id")
                    },
                    onFailure = { error ->
                        onError(messages.reactionFailed(error.message.orEmpty()))
                        AppLogger.e(logTag, "Failed to add reaction to entry $id", error)
                    }
                )
            } catch (e: Exception) {
                onError(messages.reactionError(e.message.orEmpty()))
                AppLogger.e(logTag, "Exception while adding reaction to entry $id", e)
            } finally {
                _isReacting.value = _isReacting.value - id
            }
        }
    }

    fun getCommentsForEntry(entryId: String): List<MarketV2Comment> = _entryComments.value[entryId].orEmpty()
    fun isLoadingCommentsForEntry(entryId: String): Boolean = entryId in _isLoadingComments.value
    fun isPostingCommentForEntry(entryId: String): Boolean = entryId in _isPostingComment.value
    fun getReactionsForEntry(entryId: String): List<MarketV2Reaction> = _entryReactions.value[entryId].orEmpty()
    fun isLoadingReactionsForEntry(entryId: String): Boolean = entryId in _isLoadingReactions.value
    fun isReactingToEntry(entryId: String): Boolean = entryId in _isReacting.value
    fun getLikeCount(entryId: String): Int =
        getReactionsForEntry(entryId).sumOf { reaction -> if (reaction.reactionKey() == "+1") reaction.total.coerceAtLeast(1) else 0 }

    fun fetchUserAvatar(username: String) {
        if (username.isBlank() || _userAvatarCache.value.containsKey(username)) return
        scope.launch {
            try {
                githubApiService.getUser(username).fold(
                    onSuccess = { user ->
                        _userAvatarCache.value = _userAvatarCache.value.toMutableMap().also {
                            it[username] = user.avatarUrl
                        }
                        saveAvatarToPrefs(username, user.avatarUrl)
                    },
                    onFailure = { error ->
                        AppLogger.w(logTag, "Failed to fetch avatar for user $username: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.w(logTag, "Exception while fetching avatar for user $username", e)
            }
        }
    }

    fun fetchRepositoryInfo(repositoryUrl: String) {
        if (repositoryUrl.isBlank() || _repositoryCache.value.containsKey(repositoryUrl)) return
        scope.launch {
            try {
                getRepositoryByUrl(repositoryUrl).fold(
                    onSuccess = { repository ->
                        _repositoryCache.value = _repositoryCache.value.toMutableMap().also {
                            it[repositoryUrl] = repository
                        }
                    },
                    onFailure = { error ->
                        AppLogger.w(logTag, "Failed to fetch repository info for $repositoryUrl: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.w(logTag, "Exception while fetching repository info for $repositoryUrl", e)
            }
        }
    }

    fun getUserAvatarUrl(username: String): String? = _userAvatarCache.value[username]
    fun getRepositoryInfo(repositoryUrl: String): GitHubRepository? = _repositoryCache.value[repositoryUrl]

    private fun loadAvatarCacheFromPrefs() {
        val prefs = avatarCachePrefs ?: return
        runCatching {
            val cached = prefs.all.mapNotNull { (key, value) -> if (value is String) key to value else null }.toMap()
            if (cached.isNotEmpty()) _userAvatarCache.value = cached
            if (cached.size > 500) cleanupAvatarCache()
        }.onFailure { AppLogger.e(logTag, "Failed to load avatar cache from preferences", it) }
    }

    private fun cleanupAvatarCache() {
        val prefs = avatarCachePrefs ?: return
        runCatching {
            val allEntries = prefs.all
            if (allEntries.size > 500) {
                val editor = prefs.edit()
                allEntries.keys.take(allEntries.size / 2).forEach(editor::remove)
                editor.apply()
            }
        }.onFailure { AppLogger.e(logTag, "Failed to cleanup avatar cache", it) }
    }

    private fun saveAvatarToPrefs(username: String, avatarUrl: String) {
        avatarCachePrefs?.edit()?.putString(username, avatarUrl)?.apply()
    }

    private suspend fun getRepositoryByUrl(repositoryUrl: String): Result<GitHubRepository> {
        val repoRef = parseGitHubRepositoryUrl(repositoryUrl)
            ?: return Result.failure(IllegalArgumentException("Invalid repository URL: $repositoryUrl"))
        return githubApiService.getRepository(repoRef.owner, repoRef.repo)
    }

    private fun parseGitHubRepositoryUrl(repositoryUrl: String): GitHubRepoRef? {
        val normalized = repositoryUrl.trim().removeSuffix(".git")
        if (normalized.isBlank()) return null
        val match = Regex("""(?:https?://)?(?:www\.)?github\.com/([^/\s]+)/([^/\s#?]+)""").find(normalized)
            ?: return null
        return GitHubRepoRef(owner = match.groupValues[1], repo = match.groupValues[2])
    }

    private data class GitHubRepoRef(val owner: String, val repo: String)
}





private fun MarketV2Reaction.reactionKey(): String = reaction.ifBlank { content }


