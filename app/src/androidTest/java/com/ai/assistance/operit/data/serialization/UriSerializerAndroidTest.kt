package com.ai.assistance.operit.data.serialization

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UriSerializerAndroidTest {

    @Test fun fileUri_roundTrips() {
        val original = Uri.parse("file:///sdcard/test.txt")
        val encoded = Json.encodeToString(UriSerializer, original)
        val decoded = Json.decodeFromString(UriSerializer, encoded)
        assertEquals(original, decoded)
    }

    @Test fun customScheme_roundTrips() {
        val original = Uri.parse("operit://open/chat")
        val encoded = Json.encodeToString(UriSerializer, original)
        val decoded = Json.decodeFromString(UriSerializer, encoded)
        assertEquals(original, decoded)
    }
}
