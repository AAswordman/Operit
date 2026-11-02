package com.ai.assistance.operit.core.chat.reference

/**
 * Represents a reference in the AI response
 * This is a public model class that can be used by UI components
 *
 * @param text The text of the reference
 * @param url The URL of the reference
 */
data class AiReference(
    val text: String,
    val url: String
)
