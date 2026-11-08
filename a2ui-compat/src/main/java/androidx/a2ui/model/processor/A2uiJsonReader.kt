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

package androidx.a2ui.model.processor

/**
 * Reads a stream of JSON tokens.
 *
 * Use this interface to pull JSON tokens sequentially without loading the entire document.
 */
public interface A2uiJsonReader : AutoCloseable {
    /** Returns the type of the next token without consuming it. */
    public fun peek(): A2uiJsonToken

    /** Consumes the opening delimiter of a JSON object. */
    public fun beginObject()

    /** Consumes the closing delimiter of a JSON object. */
    public fun endObject()

    /** Consumes the opening delimiter of a JSON array. */
    public fun beginArray()

    /** Consumes the closing delimiter of a JSON array. */
    public fun endArray()

    /** Returns true if the current object or array has more elements. */
    public fun hasNext(): Boolean

    /** Consumes and returns the next property name. */
    public fun nextName(): String

    /** Consumes and returns the next string value. */
    public fun nextString(): String

    /** Consumes and returns the next boolean value. */
    public fun nextBoolean(): Boolean

    /** Consumes and returns the next double value. */
    public fun nextDouble(): Double

    /** Consumes and returns the next int value. */
    public fun nextInt(): Int

    /** Consumes and returns the next long value. */
    public fun nextLong(): Long

    /** Consumes the next null token. */
    public fun nextNull()

    /** Skips the next value and all its children. */
    public fun skipValue()
}

/** Identifies the type of the next JSON token. */
public enum class A2uiJsonToken {
    /** Indicates the start of a JSON array. */
    BEGIN_ARRAY,
    /** Indicates the end of a JSON array. */
    END_ARRAY,
    /** Indicates the start of a JSON object. */
    BEGIN_OBJECT,
    /** Indicates the end of a JSON object. */
    END_OBJECT,
    /** Indicates a JSON property name. */
    NAME,
    /** Indicates a JSON string value. */
    STRING,
    /** Indicates a JSON numeric value. */
    NUMBER,
    /** Indicates a JSON boolean value. */
    BOOLEAN,
    /** Indicates a JSON null literal. */
    NULL,
    /** Indicates the end of the JSON document. */
    END_DOCUMENT,
}
