package com.ai.assistance.operit.data.db.converter

import io.objectbox.converter.PropertyConverter
import java.nio.ByteBuffer
import com.ai.assistance.operit.data.model.Embedding

/**
 * ObjectBox-compatible converter for Embedding <-> ByteArray.
 */
class EmbeddingConverter : PropertyConverter<Embedding?, ByteArray?> {
    override fun convertToEntityProperty(databaseValue: ByteArray?): Embedding? {
        if (databaseValue == null) {
            return null
        }
        val buffer = ByteBuffer.wrap(databaseValue)
        val floatBuffer = buffer.asFloatBuffer()
        val floatArray = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floatArray)
        return Embedding(floatArray)
    }

    override fun convertToDatabaseValue(entityProperty: Embedding?): ByteArray? {
        if (entityProperty == null) {
            return null
        }
        val vector = entityProperty.vector
        val buffer = ByteBuffer.allocate(vector.size * 4) // 4 bytes per float
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.put(vector)
        return buffer.array()
    }
}
