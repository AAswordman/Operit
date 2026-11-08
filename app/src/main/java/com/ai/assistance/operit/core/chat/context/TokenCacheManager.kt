package com.ai.assistance.operit.core.chat.context

import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.chat.context.ChatUtils

/**
 * Token缓存管理器，用于优化重复对话历史的token计算
 * 通过缓存之前计算过的对话历史token数量，避免重复计算相同的内容
 */
class TokenCacheManager {
    // 上一次的聊天历史
    private var previousChatHistory: List<Pair<String, String>> = emptyList()
    // 对应于previousChatHistory的token数量
    private var previousHistoryTokenCount = 0L
    
    // 缓存的输入token数量（对应于previousChatHistory的公共前缀）
    private var _cachedInputTokenCount = 0L
    
    // 当前请求的新增token数量
    private var _currentInputTokenCount = 0L
    
    // 当前输出token数量
    private var _outputTokenCount = 0L
    
    /**
     * 获取缓存的输入token数量
     */
    val cachedInputTokenCount: Long
        get() = _cachedInputTokenCount
    
    /**
     * 获取当前请求的输入token数量（不包括缓存）
     */
    val currentInputTokenCount: Long
        get() = _currentInputTokenCount
    
    /**
     * 获取总输入token数量（缓存 + 当前）
     */
    val totalInputTokenCount: Long
        get() = _cachedInputTokenCount + _currentInputTokenCount
    
    /**
     * 获取输出token数量
     */
    val outputTokenCount: Long
        get() = _outputTokenCount
    
    /**
     * 重置所有token计数和缓存
     */
    fun resetTokenCounts() {
        previousChatHistory = emptyList()
        previousHistoryTokenCount = 0L
        _cachedInputTokenCount = 0L
        _currentInputTokenCount = 0L
        _outputTokenCount = 0L
    }
    
    /**
     * 增加输出token数量
     */
    fun addOutputTokens(tokens: Long) {
        _outputTokenCount += tokens
    }

    /**
     * 使用API返回的实际输出token数量覆盖当前估算值
     */
    fun setOutputTokens(tokens: Long) {
        _outputTokenCount = tokens.coerceAtLeast(0L)
    }
    
    /**
     * 使用API返回的实际token数据更新计数
     * 用于Gemini等支持服务端缓存统计的API
     * 
     * @param actualInput 实际的输入token数量（不包括缓存）
     * @param cachedInput 缓存命中的token数量
     */
    fun updateActualTokens(actualInput: Long, cachedInput: Long) {
        _currentInputTokenCount = actualInput.coerceAtLeast(0L)
        _cachedInputTokenCount = cachedInput.coerceAtLeast(0L)
    }
    
    /**
     * 计算输入token数量，利用缓存优化重复计算
     * 
     * @param chatHistory 完整的聊天历史（必须已包含本次最新输入）
     * @param toolsJson 工具定义的JSON字符串（可选）
     * @return 总的输入token数量
     */
    fun calculateInputTokens(
        chatHistory: List<Pair<String, String>>,
        toolsJson: String? = null,
        updateState: Boolean = true
    ): Long {
        // 构建包含工具定义的历史记录列表
        // 策略：将toolsJson拼接到System Prompt前面，或者作为第一条System消息
        // 这样可以利用前缀匹配机制缓存工具定义
        val historyWithTools = if (!toolsJson.isNullOrEmpty()) {
            val mutableHistory = chatHistory.toMutableList()
            val systemIndex = mutableHistory.indexOfFirst { it.first == "system" }
            
            if (systemIndex != -1) {
                // 找到System消息，拼接在前面
                val originalSystem = mutableHistory[systemIndex]
                mutableHistory[systemIndex] = originalSystem.copy(second = toolsJson + "\n" + originalSystem.second)
            } else {
                // 没有System消息，在头部插入
                mutableHistory.add(0, "system" to toolsJson)
            }
            mutableHistory.toList()
        } else {
            chatHistory
        }

        // 找到与之前历史的公共前缀长度
        // 注意：previousChatHistory现在存储的是包含工具定义的版本
        val commonPrefixLength = findCommonPrefixLength(historyWithTools, previousChatHistory)
        
        AppLogger.d("TokenCacheManager", "聊天历史比较: 当前=${historyWithTools.size}, 之前=${previousChatHistory.size}, 公共前缀=${commonPrefixLength}")
        
        val cachedTokens: Long
        val newTokens: Long

        if (commonPrefixLength > 0) {
            // 有公共前缀，可以使用缓存
            cachedTokens = if (commonPrefixLength == previousChatHistory.size) {
                // 完全匹配之前的历史，直接使用缓存
                previousHistoryTokenCount
            } else {
                // 部分匹配，重新计算公共前缀的token数量
                val commonPrefix = historyWithTools.take(commonPrefixLength)
                calculateTokensForHistory(commonPrefix)
            }
            
            // 计算新增部分的token数量 (history剩下的部分 + 当前消息)
            val newPart = historyWithTools.drop(commonPrefixLength)
            newTokens = calculateTokensForHistory(newPart)
        } else {
            // 没有公共前缀，重新计算所有token
            val historyTokens = calculateTokensForHistory(historyWithTools)
            cachedTokens = 0L
            newTokens = historyTokens
        }

        if (updateState) {
            _cachedInputTokenCount = cachedTokens
            _currentInputTokenCount = newTokens

            // 更新缓存的历史记录 token 数量
            previousHistoryTokenCount = cachedTokens + newTokens

            // 更新缓存的历史记录列表（包含工具定义）
            if (chatHistory.isNotEmpty()) {
                previousChatHistory = historyWithTools
            }

            if (cachedTokens > 0) {
                AppLogger.d("TokenCacheManager", "使用token缓存: 缓存=${_cachedInputTokenCount}, 新增=${_currentInputTokenCount}")
            } else {
                AppLogger.d("TokenCacheManager", "重新计算所有tokens: ${_currentInputTokenCount}")
            }
        } else {
            if (cachedTokens > 0) {
                AppLogger.d("TokenCacheManager", "只读预估token缓存: 缓存=$cachedTokens, 新增=$newTokens")
            } else {
                AppLogger.d("TokenCacheManager", "只读预估所有tokens: $newTokens")
            }
        }

        return cachedTokens + newTokens
    }
    
    /**
     * 找到两个聊天历史列表的公共前缀长度
     */
    private fun findCommonPrefixLength(
        current: List<Pair<String, String>>,
        previous: List<Pair<String, String>>
    ): Int {
        val minLength = minOf(current.size, previous.size)
        var commonLength = 0
        
        for (i in 0 until minLength) {
            if (current[i] == previous[i]) {
                commonLength++
            } else {
                break
            }
        }
        
        return commonLength
    }
    
    /**
     * 计算聊天历史的token数量
     */
    private fun calculateTokensForHistory(history: List<Pair<String, String>>): Long {
        val total = history.fold(0L) { acc, (_, content) ->
            acc + ChatUtils.estimateTokenCount(content)
        }
        return total
    }
}
