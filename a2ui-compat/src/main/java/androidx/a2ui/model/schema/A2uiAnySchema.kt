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

/**
 * Schema representing an unstructured JSON payload or a keyword-only schema.
 *
 * @property description semantic description of the schema
 * @property keywords JSON Schema keywords applied to this schema node
 */
public class A2uiAnySchema
@JvmOverloads
public constructor(
    public override val description: String? = null,
    public override val keywords: List<A2uiSchemaKeyword<Any>> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        putCommonKeywords(this@A2uiAnySchema)
    }

    override fun toString(): String {
        return "Any(description=$description)"
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiAnySchema = A2uiAnySchema()
    }
}
