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
import androidx.a2ui.model.schema.A2uiBooleanSchema
import androidx.a2ui.model.schema.A2uiCompositeSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.internal.SCHEMA_ID_COMMON_TYPES

/**
 * Standard interactions that dispatch events or call client functions.
 *
 * @property description semantic description of the schema
 */
public class A2uiActionSchema(public override val description: String? = null) :
    A2uiCompositeSchema() {
    override val definitionName: String = "Action"
    override val schemaId: String = SCHEMA_ID_COMMON_TYPES

    public override fun getDefinition(): A2uiSchema =
        A2uiAnySchema(
            keywords =
                listOf(
                    A2uiSchemaKeyword.OneOf(
                        listOf(
                            A2uiObjectSchema(
                                description = "Triggers a server-side event.",
                                properties =
                                    mapOf(
                                        "event" to
                                            A2uiObjectSchema(
                                                description =
                                                    "The event to dispatch to the server.",
                                                properties =
                                                    mapOf(
                                                        "name" to
                                                            A2uiStringSchema(
                                                                "The name of the action to be dispatched to the server."
                                                            ),
                                                        "context" to
                                                            A2uiObjectSchema(
                                                                description =
                                                                    "A JSON object containing the key-value pairs for the action context. Values can be literals or paths. Use literal values unless the value must be dynamically bound to the data model. Do NOT use paths for static IDs.",
                                                                additionalPropertiesSchema =
                                                                    A2uiDynamicValueSchema
                                                                        .DEFAULT_INSTANCE,
                                                            ),
                                                        "wantResponse" to
                                                            A2uiBooleanSchema(
                                                                description =
                                                                    "Whether a response is expected for this event."
                                                            ),
                                                        "responsePath" to
                                                            A2uiStringSchema(
                                                                description =
                                                                    "The databound path where the response should be stored."
                                                            ),
                                                    ),
                                                required = setOf("name"),
                                                isAdditionalPropertiesAllowed = false,
                                            )
                                    ),
                                required = setOf("event"),
                                isAdditionalPropertiesAllowed = false,
                            ),
                            A2uiObjectSchema(
                                description = "Executes a local client-side function.",
                                properties =
                                    mapOf(
                                        "functionCall" to A2uiFunctionCallSchema.DEFAULT_INSTANCE
                                    ),
                                required = setOf("functionCall"),
                                isAdditionalPropertiesAllowed = false,
                            ),
                        )
                    )
                ),
            description =
                "Defines an interaction handler that can either trigger a server-side event or execute a local client-side function.",
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiActionSchema) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        return description?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "A2uiActionSchema(description=$description)"
    }

    public companion object {
        @JvmField public val DEFAULT_INSTANCE: A2uiActionSchema = A2uiActionSchema()
    }
}
