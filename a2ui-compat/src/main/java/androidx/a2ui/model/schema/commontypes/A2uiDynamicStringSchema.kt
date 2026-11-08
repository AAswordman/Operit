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

package androidx.a2ui.model.schema.commontypes

import androidx.a2ui.model.schema.A2uiCompositeSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.internal.SCHEMA_ID_COMMON_TYPES
import androidx.a2ui.model.schema.commontypes.internal.createDynamicTypeSchema

/**
 * Represents string values from literals, data bindings, or function calls.
 *
 * @property description semantic description of the schema
 */
public class A2uiDynamicStringSchema(public override val description: String? = null) :
    A2uiCompositeSchema() {
    override val definitionName: String = "DynamicString"
    override val schemaId: String = SCHEMA_ID_COMMON_TYPES

    public override fun getDefinition(): A2uiSchema =
        createDynamicTypeSchema(
            literalSchema = A2uiStringSchema(),
            returnType = FunctionReturnType.STRING,
            description =
                "A string value that can be a literal, a path to a string in the data model, or a function call returning a string.",
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiDynamicStringSchema) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        return description?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "A2uiDynamicStringSchema(description=$description)"
    }

    public companion object {
        @JvmField public val DEFAULT_INSTANCE: A2uiDynamicStringSchema = A2uiDynamicStringSchema()
    }
}
