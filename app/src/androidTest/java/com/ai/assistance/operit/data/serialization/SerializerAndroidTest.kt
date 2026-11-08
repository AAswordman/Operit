package com.ai.assistance.operit.data.serialization

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SerializerAndroidTest {

    @Test fun uriSerializer_serializesNonNullUri() {
        val value = Json.encodeToString(UriSerializer, Uri.parse("https://example.com/a"))
        assertEquals("\"https://example.com/a\"", value)
    }

    @Test fun uriSerializer_serializesNullAsEmptyString() {
        val value = Json.encodeToString(UriSerializer, null)
        assertEquals("\"\"", value)
    }

    @Test fun uriSerializer_deserializesEmptyStringToNull() {
        assertNull(Json.decodeFromString(UriSerializer, "\"\""))
    }

    @Test fun uriSerializer_deserializesHttpUri() {
        val value = Json.decodeFromString(UriSerializer, "\"https://example.com/a\"")
        assertEquals(Uri.parse("https://example.com/a"), value)
    }

    @Test fun intRangeSerializer_serializesRange() {
        val value = Json.encodeToString(IntRangeSerializer, 1..3)
        assertEquals("{\"start\":1,\"endInclusive\":3}", value)
    }

    @Test fun intRangeSerializer_deserializesRange() {
        val value = Json.decodeFromString(IntRangeSerializer, "{\"start\":1,\"endInclusive\":3}")
        assertEquals(1..3, value)
    }

    @Test fun intRangeSerializer_deserializesNegativeRange() {
        val value = Json.decodeFromString(IntRangeSerializer, "{\"start\":-3,\"endInclusive\":-1}")
        assertEquals(-3..-1, value)
    }

    @Test fun localDateTimeSerializer_serializesIsoString() {
        val value = Json.encodeToString(LocalDateTimeSerializer, LocalDateTime.of(2024, 1, 2, 3, 4, 5))
        assertEquals("\"2024-01-02T03:04:05\"", value)
    }

    @Test fun localDateTimeSerializer_deserializesIsoString() {
        val value = Json.decodeFromString(LocalDateTimeSerializer, "\"2024-01-02T03:04:05\"")
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), value)
    }

    @Test fun localDateTimeSerializer_roundTripsFractionalSeconds() {
        val original = LocalDateTime.of(2024, 1, 2, 3, 4, 5, 123_000_000)
        val encoded = Json.encodeToString(LocalDateTimeSerializer, original)
        val decoded = Json.decodeFromString(LocalDateTimeSerializer, encoded)
        assertEquals(original, decoded)
    }

    @Test fun uriSerializer_roundTripsContentUri() {
        val original = Uri.parse("content://com.ai.assistance.operit/item/1")
        val encoded = Json.encodeToString(UriSerializer, original)
        val decoded = Json.decodeFromString(UriSerializer, encoded)
        assertEquals(original, decoded)
    }
}
