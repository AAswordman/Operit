package com.ai.assistance.operit.data.serialization

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SerializerRoundTripAndroidTest {

    @Test fun uriSerializer_roundTripsHttpUri() {
        val original = Uri.parse("https://example.com")
        assertEquals(original, Json.decodeFromString(UriSerializer, Json.encodeToString(UriSerializer, original)))
    }

    @Test fun intRangeSerializer_roundTripsNegativeRange() {
        val original = -2..2
        assertEquals(original, Json.decodeFromString(IntRangeSerializer, Json.encodeToString(IntRangeSerializer, original)))
    }

    @Test fun localDateTimeSerializer_roundTripsSimpleValue() {
        val original = LocalDateTime.of(2024, 6, 1, 12, 0, 0)
        assertEquals(original, Json.decodeFromString(LocalDateTimeSerializer, Json.encodeToString(LocalDateTimeSerializer, original)))
    }
}
