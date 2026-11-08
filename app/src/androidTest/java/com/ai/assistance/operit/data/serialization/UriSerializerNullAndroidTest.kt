package com.ai.assistance.operit.data.serialization

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UriSerializerNullAndroidTest {

    @Test fun serializeNull_returnsEmptyStringPayload() {
        assertEquals("\"\"", Json.encodeToString(UriSerializer, null))
    }

    @Test fun deserializeEmptyString_returnsNull() {
        assertNull(Json.decodeFromString(UriSerializer, "\"\""))
    }
}
