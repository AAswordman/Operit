package com.ai.assistance.operit.ui.features.management.settings.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.emoji.CustomEmoji
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 自定义表情管理 ViewModel
 *
 * 管理指定角色或当前活跃目标的自定义表情 UI 状态和业务逻辑。
 */
class CustomEmojiViewModel(context: Context, target: ActivePrompt? = null) : ViewModel() {

    private val repository = CustomEmojiRepository.getInstance(context)
    private val activePromptManager = ActivePromptManager.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(context)
    private val appContext = context.applicationContext
    private val tag = "CustomEmojiViewModel"

    val activePrompt: StateFlow<ActivePrompt> = if (target != null) MutableStateFlow(target).asStateFlow()
        else activePromptManager.activePromptFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
        )

    val activeTargetName: StateFlow<String> = activePrompt
        .flatMapLatest { prompt ->
            when (prompt) {
                is ActivePrompt.CharacterCard -> {
                    characterCardManager.getCharacterCardFlow(prompt.id).map { it?.name.orEmpty() }
                }
                is ActivePrompt.CharacterGroup -> {
                    characterGroupCardManager.getCharacterGroupCardFlow(prompt.id).map { it?.name.orEmpty() }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val categories: StateFlow<List<String>> = activePrompt
        .flatMapLatest { prompt ->
            flow {
                repository.initializeStorage()
                emitAll(repository.getAllCategories(prompt))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val emojisInCategory: StateFlow<List<CustomEmoji>> = combine(activePrompt, _selectedCategory) { prompt, category ->
        prompt to category
    }.flatMapLatest { (prompt, category) ->
        repository.getEmojisForCategory(prompt, category)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        viewModelScope.launch {
            categories.collect { currentCategories ->
                // 空库或删除最后一个分组时清空选择，避免继续使用已删除的分组。
                if (currentCategories.isEmpty()) {
                    _selectedCategory.value = ""
                } else if (_selectedCategory.value !in currentCategories) {
                    _selectedCategory.value = currentCategories.first()
                }
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addEmojis(category: String, uris: List<Uri>) {
        if (category !in categories.value) {
            _errorMessage.value = appContext.getString(R.string.emoji_create_category_first)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            var successCount = 0
            var failCount = 0
            val target = activePrompt.value

            uris.forEach { uri ->
                val result = repository.addCustomEmoji(target, category, uri)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                    result.exceptionOrNull()?.let { ex ->
                        AppLogger.e(tag, "Failed to add emoji from URI: $uri", ex)
                    } ?: AppLogger.e(tag, "Failed to add emoji from URI: $uri")
                }
            }

            _isLoading.value = false

            if (successCount > 0) {
                _successMessage.value = if (failCount > 0) {
                    appContext.getString(R.string.emoji_added_partial, successCount, failCount)
                } else {
                    appContext.getString(R.string.emoji_added_success, successCount)
                }
            }
            if (failCount > 0 && successCount == 0) {
                _errorMessage.value = appContext.getString(R.string.emoji_add_failed)
            }
        }
    }

    fun deleteEmoji(emojiId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            val result = repository.deleteCustomEmoji(activePrompt.value, emojiId)
            _isLoading.value = false

            if (result.isSuccess) {
                _successMessage.value = appContext.getString(R.string.emoji_deleted_success)
            } else {
                result.exceptionOrNull()?.let { ex ->
                    AppLogger.e(tag, "Failed to delete emoji: $emojiId", ex)
                } ?: AppLogger.e(tag, "Failed to delete emoji: $emojiId")
                _errorMessage.value = appContext.getString(R.string.emoji_delete_failed)
            }
        }
    }

    fun deleteCategory(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            val result = repository.deleteCategory(activePrompt.value, category)
            _isLoading.value = false

            if (result.isSuccess) {
                _successMessage.value = appContext.getString(R.string.category_deleted)
            } else {
                result.exceptionOrNull()?.let { ex ->
                    AppLogger.e(tag, "Failed to delete category: $category", ex)
                } ?: AppLogger.e(tag, "Failed to delete category: $category")
                _errorMessage.value = appContext.getString(R.string.category_delete_failed)
            }
        }
    }

    suspend fun createCategory(categoryName: String): Boolean {
        val target = activePrompt.value

        if (!repository.isValidCategoryName(categoryName)) {
            _errorMessage.value = appContext.getString(R.string.invalid_category_name)
            return false
        }

        if (repository.categoryExists(target, categoryName)) {
            _errorMessage.value = appContext.getString(R.string.category_already_exists)
            return false
        }

        repository.addCategory(target, categoryName)
        _selectedCategory.value = categoryName
        _successMessage.value = appContext.getString(R.string.category_created)
        return true
    }

    fun getEmojiUri(emoji: CustomEmoji): Uri {
        return repository.getEmojiUri(activePrompt.value, emoji)
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }
}
