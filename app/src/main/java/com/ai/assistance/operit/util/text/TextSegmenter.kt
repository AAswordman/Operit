package com.ai.assistance.operit.util.text

import com.huaban.analysis.jieba.JiebaSegmenter
import com.huaban.analysis.jieba.WordDictionary
import java.io.File
import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * 文本分词工具类 - 提供中文和多语言文本的分词功能
 */
object TextSegmenter {
    private const val TAG = "TextSegmenter"
    private const val PREWARM_TEXT = "搜索记忆 分词预热"

    // 使用延迟初始化，避免不必要的资源消耗
    private val segmenter by lazy { JiebaSegmenter() }

    private val initLock = Any()

    @Volatile
    private var baseInitialized = false

    private val loadedUserDictPaths = ConcurrentHashMap.newKeySet<String>()
    
    // 关键词缓存，提高性能
    private val segmentCache = ConcurrentHashMap<String, List<String>>()
    
    // 最大缓存大小
    private const val MAX_CACHE_SIZE = 1000
    
    /**
     * 初始化分词器，可选加载自定义词典
     * @param context 应用上下文
     * @param customDictPath 自定义词典路径（可选）
     */
    @Suppress("UNUSED_PARAMETER")
    fun initialize(context: Context, customDictPath: String? = null) {
        if (baseInitialized && customDictPath.isNullOrBlank()) return

        val startTime = System.currentTimeMillis()
        try {
            synchronized(initLock) {
                val dictionary = WordDictionary.getInstance()

                // 如果提供了自定义词典，加载它
                customDictPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { loadCustomDictionaryIfNeeded(dictionary, it) }

                if (!baseInitialized) {
                    // 通过一次真实分词触发 Jieba 词典加载，避免首个搜索请求卡顿。
                    segmenter.process(PREWARM_TEXT, JiebaSegmenter.SegMode.SEARCH)
                    baseInitialized = true
                    AppLogger.d(
                        TAG,
                        "分词器预热完成 - ${System.currentTimeMillis() - startTime}ms"
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化分词器失败", e)
        }
    }

    private fun loadCustomDictionaryIfNeeded(dictionary: WordDictionary, customDictPath: String) {
        val dictFile = File(customDictPath)
        if (!dictFile.exists()) return

        val normalizedPath = dictFile.absolutePath
        if (normalizedPath in loadedUserDictPaths) return

        dictionary.loadUserDict(dictFile.toPath())
        loadedUserDictPaths.add(normalizedPath)
        AppLogger.d(TAG, "已加载自定义词典: $normalizedPath")
    }
    
    /**
     * 对文本进行分词
     * @param text 要分词的文本
     * @param useCached 是否使用缓存
     * @return 分词后的关键词列表
     */
    fun segment(text: String, useCached: Boolean = true): List<String> {
        if (text.isBlank()) return emptyList()
        
        // 查询缓存
        if (useCached && segmentCache.containsKey(text)) {
            return segmentCache[text] ?: emptyList()
        }
        
        try {
            // 使用结巴分词器进行分词
            val result = segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)
                .map { it.word }
                .filter { it.length > 1 } // 过滤掉单字（通常噪音较多）
            
            // 缓存结果（控制缓存大小）
            if (useCached) {
                // 如果缓存太大，清除一半
                if (segmentCache.size > MAX_CACHE_SIZE) {
                    val keysToRemove = segmentCache.keys.take(MAX_CACHE_SIZE / 2)
                    keysToRemove.forEach { segmentCache.remove(it) }
                }
                segmentCache[text] = result
            }
            
            return result
        } catch (e: Exception) {
            AppLogger.e(TAG, "分词失败: ${e.message}")
            // 失败时使用简单的空格分割作为备选方案
            return text.split(Regex("\\s+|,|，|\\.|。"))
                .filter { it.length > 1 }
        }
    }
    
    /**
     * 清除分词缓存
     */
    fun clearCache() {
        segmentCache.clear()
    }
    
    /**
     * 计算文本与关键词的相关性得分
     * @param text 要检查的文本
     * @param keywords 关键词列表
     * @return 相关性得分（0-1范围）
     */
    fun calculateRelevance(text: String, keywords: List<String>): Double {
        if (text.isBlank() || keywords.isEmpty()) return 0.0
        
        // 优化: 截断过长的文本，避免处理过多数据
        val maxLength = 5000
        val textToProcess = if (text.length > maxLength) text.substring(0, maxLength) else text
        val textLower = textToProcess.lowercase()
        
        // 快速检查: 如果任何关键词包含在文本中，就说明有相关性
        val hasDirectMatch = keywords.any { keyword -> 
            textLower.contains(keyword.lowercase()) 
        }
        
        // 如果没有直接匹配，可能相关性很低，直接返回0以避免更多计算
        if (!hasDirectMatch) {
            return 0.0
        }
        
        // 匹配包含的关键词数量
        val exactMatches = keywords.count { keyword -> 
            textLower.contains(keyword.lowercase()) 
        }
        
        // 如果有足够的精确匹配，就不需要进行更昂贵的分词操作
        if (exactMatches >= keywords.size / 2 || exactMatches >= 3) {
            val quickScore = (exactMatches * 2.0) / (keywords.size * 3.0)
            return quickScore.coerceIn(0.0, 1.0)
        }
        
        // 只有必要时才进行分词处理
        val textSegments = segment(textLower)
        
        // 匹配分词后的部分
        val segmentMatches = keywords.count { keyword ->
            textSegments.any { segment -> segment.contains(keyword.lowercase()) }
        }
        
        // 计算总得分：精确匹配*2 + 分词匹配*1，然后归一化
        val totalScore = (exactMatches * 2.0 + segmentMatches) / (keywords.size * 3.0)
        return totalScore.coerceIn(0.0, 1.0) // 将得分限制在0-1范围内
    }
} 
