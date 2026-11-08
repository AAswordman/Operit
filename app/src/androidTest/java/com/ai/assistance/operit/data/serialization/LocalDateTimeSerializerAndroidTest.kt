package com.ai.assistance.operit.data.serialization

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDateTimeSerializerAndroidTest {

    @Test fun midnight_roundTrips() {
        val original = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        val encoded = Json.encodeToString(LocalDateTimeSerializer, original)
        val decoded = Json.decodeFromString(LocalDateTimeSerializer, encoded)
        assertEquals(original, decoded)
    }

    @Test fun endOfDay_roundTrips() {
        val original = LocalDateTime.of(2025, 12, 31, 23, 59, 59)
        val encoded = Json.encodeToString(LocalDateTimeSerializer, original)
        val decoded = Json.decodeFromString(LocalDateTimeSerializer, encoded)
        assertEquals(original, decoded)
    }
}
