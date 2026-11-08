package com.ai.assistance.operit.data.serialization

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDateTimeSerializerFormatAndroidTest {

    @Test fun serializedDateTimeUsesIsoSeparatorT() {
        val encoded = Json.encodeToString(LocalDateTimeSerializer, LocalDateTime.of(2024, 1, 1, 1, 2, 3))
        assertTrue(encoded.contains("T"))
    }

    @Test fun serializedDateTimeContainsYearPrefix() {
        val encoded = Json.encodeToString(LocalDateTimeSerializer, LocalDateTime.of(2024, 1, 1, 1, 2, 3))
        assertTrue(encoded.contains("2024-"))
    }
}
