package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.core.chat.reference.AiReference

/**
 * Utility for extracting references from content
 */
object ReferenceManager {
    /**
     * Extract references from content
     * 
     * @param content The content to extract references from
     * @return A list of references
     */
    fun extractReferences(content: String): List<AiReference> {
        // Use a Markdown link format: [title](url)
        val regex = "\\[([^\\]]+)\\]\\((https?://[^\\)]+)\\)".toRegex()
        val matches = regex.findAll(content)
        
        return matches.map { match ->
            val (title, url) = match.destructured
            AiReference(title, url)
        }.toList()
    }
} 