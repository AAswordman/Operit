package com.ai.assistance.operit.data.serialization

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/**
 * Configures the serialization module with custom serializers
 */
object SerializationSetup {
    /**
     * Creates and returns a SerializersModule with all custom serializers registered
     */
    val module = SerializersModule {
        // Register the IntRange serializer as a contextual serializer
        contextual(IntRangeSerializer)
    }
} 