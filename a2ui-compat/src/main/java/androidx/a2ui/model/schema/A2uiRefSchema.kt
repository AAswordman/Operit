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
 * Schema representing a reference to another schema definition.
 *
 * @property ref reference path
 * @property description semantic description of the schema
 * @property keywords JSON Schema keywords applied to this schema node
 */
public class A2uiRefSchema
@JvmOverloads
public constructor(
    public val ref: String,
    public override val description: String? = null,
    public override val keywords: List<A2uiSchemaKeyword<Any>> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put(KEY_REF, ref)
        putCommonKeywords(this@A2uiRefSchema)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (!super.equals(other)) return false
        other as A2uiRefSchema
        return ref == other.ref
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + ref.hashCode()
        return result
    }

    override fun toString(): String {
        return "Ref(ref=$ref, description=$description)"
    }

    internal companion object {
        internal const val KEY_REF = "\$ref"
    }
}
