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

package androidx.a2ui.model.schema.internal

import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.KEY_DESCRIPTION
import kotlin.collections.iterator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

internal fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
        is Array<*> -> JsonArray(this.map { it.toJsonElement() })
        is Map<*, *> -> {
            val jsonObject = mutableMapOf<String, JsonElement>()
            for ((k, v) in this) {
                jsonObject[k.toString()] = v.toJsonElement()
            }
            JsonObject(jsonObject)
        }
        else -> throw IllegalArgumentException("Unsupported type: ${this::class}")
    }

internal fun JsonObjectBuilder.putCommonKeywords(schema: A2uiSchema) {
    for (keyword in schema.keywords) {
        keyword.addToJsonObject(this)
    }
    if (schema.description != null) {
        put(KEY_DESCRIPTION, JsonPrimitive(schema.description))
    }
}
