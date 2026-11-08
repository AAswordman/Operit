package com.ai.assistance.operit.data.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

/**
 * Custom serializer for IntRange
 */
object IntRangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IntRange") {
        element<Int>("start")
        element<Int>("endInclusive")
    }

    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.first)
            encodeIntElement(descriptor, 1, value.last)
        }
    }

    override fun deserialize(decoder: Decoder): IntRange {
        var start = 0
        var endInclusive = 0
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> start = decodeIntElement(descriptor, 0)
                    1 -> endInclusive = decodeIntElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        return IntRange(start, endInclusive)
    }
} 