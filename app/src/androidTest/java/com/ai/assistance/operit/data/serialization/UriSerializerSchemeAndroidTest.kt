package com.ai.assistance.operit.data.serialization

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UriSerializerSchemeAndroidTest {

    @Test fun contentScheme_isPreserved() {
        val uri = Uri.parse("content://a/b")
        assertEquals(uri, Json.decodeFromString(UriSerializer, Json.encodeToString(UriSerializer, uri)))
    }

    @Test fun fileScheme_isPreserved() {
        val uri = Uri.parse("file:///a/b")
        assertEquals(uri, Json.decodeFromString(UriSerializer, Json.encodeToString(UriSerializer, uri)))
    }
}
