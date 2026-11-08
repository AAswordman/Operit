/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.a2ui.compose.runtime

import android.util.JsonReader
import android.util.JsonToken
import androidx.a2ui.model.processor.A2uiJsonMessageParser
import androidx.a2ui.model.processor.A2uiJsonReader
import androidx.a2ui.model.processor.A2uiJsonToken
import androidx.a2ui.model.processor.A2uiMessageParser
import java.io.StringReader

/**
 * Creates an [androidx.a2ui.model.processor.A2uiMessageParser] configured to parse String-based
 * JSON payloads into [androidx.a2ui.model.protocol.A2uiServerToClientMessage] objects.
 *
 * @return An [A2uiMessageParser] configured for the incoming JSON payloads.
 */
public fun A2uiMessageParser(): A2uiMessageParser<String> =
    A2uiJsonMessageParser(jsonReaderProvider = ::A2uiJsonReaderImpl)

internal class A2uiJsonReaderImpl(rawJson: String) : A2uiJsonReader {

    private val delegate = JsonReader(StringReader(rawJson))

    override fun peek(): A2uiJsonToken =
        when (val token = delegate.peek()) {
            JsonToken.BEGIN_OBJECT -> A2uiJsonToken.BEGIN_OBJECT
            JsonToken.BEGIN_ARRAY -> A2uiJsonToken.BEGIN_ARRAY
            JsonToken.END_OBJECT -> A2uiJsonToken.END_OBJECT
            JsonToken.END_ARRAY -> A2uiJsonToken.END_ARRAY
            JsonToken.NAME -> A2uiJsonToken.NAME
            JsonToken.STRING -> A2uiJsonToken.STRING
            JsonToken.NUMBER -> A2uiJsonToken.NUMBER
            JsonToken.BOOLEAN -> A2uiJsonToken.BOOLEAN
            JsonToken.NULL -> A2uiJsonToken.NULL
            JsonToken.END_DOCUMENT -> A2uiJsonToken.END_DOCUMENT
            else -> throw IllegalStateException("Unexpected JSON token: $token")
        }

    override fun beginObject() = delegate.beginObject()

    override fun endObject() = delegate.endObject()

    override fun beginArray() = delegate.beginArray()

    override fun endArray() = delegate.endArray()

    override fun hasNext(): Boolean = delegate.hasNext()

    override fun nextName(): String = delegate.nextName()

    override fun nextString(): String = delegate.nextString()

    override fun nextBoolean(): Boolean = delegate.nextBoolean()

    override fun nextDouble(): Double = delegate.nextDouble()

    override fun nextInt(): Int = delegate.nextInt()

    override fun nextLong(): Long = delegate.nextLong()

    override fun nextNull() = delegate.nextNull()

    override fun skipValue() = delegate.skipValue()

    override fun close() = delegate.close()
}
