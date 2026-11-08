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
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiCompositeSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.internal.SCHEMA_ID_COMMON_TYPES

/**
 * Specifies child component identifiers.
 *
 * @property description semantic description of the schema
 */
public class A2uiChildListSchema(public override val description: String? = null) :
    A2uiCompositeSchema() {
    override val definitionName: String = "ChildList"
    override val schemaId: String = SCHEMA_ID_COMMON_TYPES

    public override fun getDefinition(): A2uiSchema =
        A2uiAnySchema(
            keywords =
                listOf(
                    A2uiSchemaKeyword.OneOf(
                        listOf(
                            A2uiArraySchema(
                                items = A2uiComponentIdSchema.DEFAULT_INSTANCE,
                                description = "A static list of child component IDs.",
                            ),
                            A2uiObjectSchema(
                                properties =
                                    mapOf(
                                        "componentId" to A2uiComponentIdSchema.DEFAULT_INSTANCE,
                                        "path" to
                                            A2uiStringSchema(
                                                "The path to the list of component property objects in the data model."
                                            ),
                                    ),
                                required = setOf("componentId", "path"),
                                isAdditionalPropertiesAllowed = false,
                                description =
                                    "A template for generating a dynamic list of children from a data model list. The `componentId` is the component to use as a template.",
                            ),
                        )
                    )
                )
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiChildListSchema) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        return description?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "A2uiChildListSchema(description=$description)"
    }

    public companion object {
        @JvmField public val DEFAULT_INSTANCE: A2uiChildListSchema = A2uiChildListSchema()
    }
}
