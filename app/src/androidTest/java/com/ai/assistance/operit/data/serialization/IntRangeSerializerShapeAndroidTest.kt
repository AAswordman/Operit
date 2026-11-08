package com.ai.assistance.operit.data.serialization

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntRangeSerializerShapeAndroidTest {

    @Test fun serializedRangeContainsStartField() {
        val encoded = Json.encodeToString(IntRangeSerializer, 1..2)
        assertTrue(encoded.contains("\"start\":1"))
    }

    @Test fun serializedRangeContainsEndInclusiveField() {
        val encoded = Json.encodeToString(IntRangeSerializer, 1..2)
        assertTrue(encoded.contains("\"endInclusive\":2"))
    }
}
