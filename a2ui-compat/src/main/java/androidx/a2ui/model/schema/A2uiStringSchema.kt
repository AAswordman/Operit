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

package androidx.a2ui.model.schema

import androidx.a2ui.model.schema.internal.putCommonKeywords
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Schema representing a string literal.
 *
 * Use this for static text that cannot be dynamically bound.
 *
 * @property description semantic description of the schema
 * @property keywords JSON Schema keywords applied to this schema node
 */
public class A2uiStringSchema
@JvmOverloads
public constructor(
    public override val description: String? = null,
    public override val keywords: List<A2uiSchemaKeyword<String>> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put(KEY_TYPE, TYPE_STRING)
        putCommonKeywords(this@A2uiStringSchema)
    }

    override fun toString(): String {
        return "String(description=$description)"
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiStringSchema = A2uiStringSchema()

        internal const val TYPE_STRING = "string"
    }
}
