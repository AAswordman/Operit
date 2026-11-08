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

import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiCompositeSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiRefSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.internal.SCHEMA_ID_COMMON_TYPES

/**
 * Configures client function invocations.
 *
 * @property description semantic description of the schema
 */
public class A2uiFunctionCallSchema(public override val description: String? = null) :
    A2uiCompositeSchema() {
    override val definitionName: String = "FunctionCall"
    override val schemaId: String = SCHEMA_ID_COMMON_TYPES

    public override fun getDefinition(): A2uiSchema =
        A2uiObjectSchema(
            properties =
                mapOf(
                    "call" to A2uiStringSchema("The name of the function to call."),
                    "callableFrom" to
                        A2uiStringSchema(
                            description = "Specifies where this function can be invoked from.",
                            keywords =
                                listOf(
                                    A2uiSchemaKeyword.Enum(
                                        FunctionCallableFrom.entries.map { it.value }
                                    )
                                ),
                        ),
                    "args" to
                        A2uiObjectSchema(
                            description = "Arguments passed to the function.",
                            additionalPropertiesSchema =
                                A2uiAnySchema(
                                    keywords =
                                        listOf(
                                            A2uiSchemaKeyword.AnyOf(
                                                listOf(
                                                    A2uiDynamicValueSchema.DEFAULT_INSTANCE,
                                                    A2uiObjectSchema(
                                                        description =
                                                            "A literal object argument (e.g. configuration)."
                                                    ),
                                                )
                                            )
                                        )
                                ),
                        ),
                    "returnType" to
                        A2uiStringSchema(
                            description = "The expected return type of the function call.",
                            keywords =
                                listOf(
                                    A2uiSchemaKeyword.Enum(
                                        FunctionReturnType.entries.map { it.value }
                                    ),
                                    A2uiSchemaKeyword.Default(FunctionReturnType.BOOLEAN.value),
                                ),
                        ),
                ),
            required = setOf("call"),
            keywords =
                listOf(
                    A2uiSchemaKeyword.OneOf(
                        listOf(A2uiRefSchema("catalog.json#/\$defs/anyFunction"))
                    )
                ),
            description = "Invokes a named function on the client.",
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiFunctionCallSchema) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        return description?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "A2uiFunctionCallSchema(description=$description)"
    }

    public companion object {
        @JvmField public val DEFAULT_INSTANCE: A2uiFunctionCallSchema = A2uiFunctionCallSchema()
    }
}

internal enum class FunctionCallableFrom(val value: String) {
    CLIENT_ONLY("clientOnly"),
    REMOTE_ONLY("remoteOnly"),
    CLIENT_OR_REMOTE("clientOrRemote"),
}

internal enum class FunctionReturnType(val value: String) {
    ANY("any"),
    ARRAY("array"),
    BOOLEAN("boolean"),
    NUMBER("number"),
    OBJECT("object"),
    STRING("string"),
    VOID("void"),
}
