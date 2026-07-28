package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils

/**
 * Token缓存管理器，用于优化重复对话历史的token计算
 * 通过缓存之前计算过的对话历史token数量，避免重复计算相同的内容
 */
class TokenCacheManager {
    // 上一次的聊天历史
    private var previousChatHistory: List<Pair<String, String>> = emptyList()
    // 对应于previousChatHistory的token数量
    private var previousHistoryTokenCount = 0
    
    // 缓存的输入token数量（对应于previousChatHistory的公共前缀）
    private var _cachedInputTokenCount = 0
    
    // 当前请求的新增token数量
    private var _currentInputTokenCount = 0
    
    // 当前输出token数量
    private var _outputTokenCount = 0

    // DeepSeek 特有：前缀缓存命中/未命中 token 数（服务端返回）
    private var _promptCacheHitTokens = 0
    private var _promptCacheMissTokens = 0
    
    /**
     * 获取缓存的输入token数量
     */
    val cachedInputTokenCount: Int
        get() = _cachedInputTokenCount
    
    /**
     * 获取当前请求的输入token数量（不包括缓存）
     */
    val currentInputTokenCount: Int
        get() = _currentInputTokenCount
    
    /**
     * 获取总输入token数量（缓存 + 当前）
     */
    val totalInputTokenCount: Int
        get() = saturatedTokenSum(_cachedInputTokenCount, _currentInputTokenCount)
    
    /**
     * 获取输出token数量
     */
    val outputTokenCount: Int
        get() = _outputTokenCount

    /**
     * 获取 DeepSeek 前缀缓存命中 token 数（服务端返回）
     */
    val promptCacheHitTokens: Int
        get() = _promptCacheHitTokens

    /**
     * 获取 DeepSeek 前缀缓存未命中 token 数（服务端返回）
     */
    val promptCacheMissTokens: Int
        get() = _promptCacheMissTokens
    
    /**
     * 重置所有token计数和缓存
     */
    fun resetTokenCounts() {
        previousChatHistory = emptyList()
        previousHistoryTokenCount = 0
        _cachedInputTokenCount = 0
        _currentInputTokenCount = 0
        _outputTokenCount = 0
        _promptCacheHitTokens = 0
        _promptCacheMissTokens = 0
    }
    
    /**
     * 增加输出token数量
     */
    fun addOutputTokens(tokens: Int) {
        _outputTokenCount = saturatedTokenDelta(_outputTokenCount, tokens)
    }

    /**
     * 使用API返回的实际输出token数量覆盖当前估算值
     */
    fun setOutputTokens(tokens: Int) {
        _outputTokenCount = tokens.coerceAtLeast(0)
    }
    
    /**
     * 使用API返回的实际token数据更新计数
     * 用于Gemini等支持服务端缓存统计的API
     *
     * @param actualInput 实际的输入token数量（不包括缓存）
     * @param cachedInput 缓存命中的token数量
     */
    fun updateActualTokens(actualInput: Int, cachedInput: Int) {
        _currentInputTokenCount = actualInput.coerceAtLeast(0)
        _cachedInputTokenCount = cachedInput.coerceAtLeast(0)
    }

    /**
     * 更新 DeepSeek 前缀缓存统计（服务端返回）
     *
     * @param hitTokens 缓存命中 token 数
     * @param missTokens 缓存未命中 token 数
     */
    fun updatePromptCacheStats(hitTokens: Int, missTokens: Int) {
        _promptCacheHitTokens = hitTokens.coerceAtLeast(0)
        _promptCacheMissTokens = missTokens.coerceAtLeast(0)
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
    ): Int {
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
        
        val cachedTokens: Int
        val newTokens: Int

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
            cachedTokens = 0
            newTokens = historyTokens
        }

        if (updateState) {
            _cachedInputTokenCount = cachedTokens
            _currentInputTokenCount = newTokens

            // 更新缓存的历史记录 token 数量
            previousHistoryTokenCount = saturatedTokenSum(cachedTokens, newTokens)

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

        return saturatedTokenSum(cachedTokens, newTokens)
    }

    private fun saturatedTokenSum(vararg values: Int): Int {
        val total = values.fold(0L) { acc, value -> acc + value.toLong().coerceAtLeast(0L) }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun saturatedTokenDelta(current: Int, delta: Int): Int {
        return (current.toLong() + delta.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
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
    private fun calculateTokensForHistory(history: List<Pair<String, String>>): Int {
        val total = history.fold(0L) { acc, (_, content) ->
            acc + ChatUtils.estimateTokenCount(content).toLong().coerceAtLeast(0L)
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
