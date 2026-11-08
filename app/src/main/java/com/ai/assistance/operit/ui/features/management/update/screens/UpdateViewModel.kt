package com.ai.assistance.operit.ui.features.management.update.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 更新界面的ViewModel
 * 负责从GitHub API获取releases信息
 */
class UpdateViewModel(private val context: Context) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Loading)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()
    
    private val apiService = GitHubApiService(context)
    
    companion object {
        private const val REPO_OWNER = "AAswordman"
        private const val REPO_NAME = "Operit"
    }
    
    init {
        loadUpdates()
    }
    
    /**
     * 加载更新历史
     */
    fun loadUpdates() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Loading
            
            apiService.getRepositoryReleases(REPO_OWNER, REPO_NAME, page = 1, perPage = 20)
                .onSuccess { releases ->
                    val updates = releases
                        .filter { !it.draft && !it.prerelease } // 过滤掉草稿和预发布
                        .mapIndexed { index, release -> 
                            parseReleaseToUpdateInfo(release, isLatest = index == 0)
                        }
                    _uiState.value = UpdateUiState.Success(updates)
                }
                .onFailure { error ->
                    _uiState.value = UpdateUiState.Error(error.message ?: context.getString(R.string.update_unknown_error))
                }
        }
    }
    
    /**
     * 将GitHub Release转换为UpdateInfo
     */
    private fun parseReleaseToUpdateInfo(release: GitHubRelease, isLatest: Boolean): UpdateInfo {
        // 解析发布时间
        val publishedAt = requireNotNull(release.published_at)
        val date = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = inputFormat.parse(publishedAt)
            parsedDate?.let { outputFormat.format(it) } ?: publishedAt.substring(0, 10)
        } catch (e: Exception) {
            publishedAt.substring(0, 10)
        }
        
        // 解析release body
        val body = release.body ?: ""
        
        // 提取标题（使用release name或第一行）
        val title = release.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.update_version_title)
        
        // 获取下载链接
        val downloadUrl = release.html_url
        
        return UpdateInfo(
            version = release.tag_name,
            date = date,
            title = title,
            description = body,
            highlights = emptyList(),
            allChanges = emptyList(),
            isLatest = isLatest,
            downloadUrl = downloadUrl,
            releaseUrl = release.html_url
        )
    }
}

/**
 * 更新界面的UI状态
 */
sealed class UpdateUiState {
    object Loading : UpdateUiState()
    data class Success(val updates: List<UpdateInfo>) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

