package com.ai.assistance.operit.data.serialization

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntRangeSerializerAndroidTest {

    @Test fun singlePointRange_roundTrips() {
        val encoded = Json.encodeToString(IntRangeSerializer, 5..5)
        val decoded = Json.decodeFromString(IntRangeSerializer, encoded)
        assertEquals(5..5, decoded)
    }

    @Test fun descendingValues_preserveStoredBounds() {
        val decoded = Json.decodeFromString(IntRangeSerializer, "{\"start\":5,\"endInclusive\":3}")
        assertEquals(IntRange(5, 3), decoded)
    }
}
