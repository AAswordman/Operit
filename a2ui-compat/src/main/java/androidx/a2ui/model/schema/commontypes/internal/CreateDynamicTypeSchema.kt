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

package androidx.a2ui.model.schema.commontypes.internal

import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema
import androidx.a2ui.model.schema.commontypes.FunctionReturnType

/**
 * Creates a schema for dynamic values that can be either a literal, a data binding path, or a
 * function call returning a specific type.
 */
internal fun createDynamicTypeSchema(
    literalSchema: A2uiSchema,
    returnType: FunctionReturnType,
    description: String?,
): A2uiSchema =
    A2uiAnySchema(
        keywords =
            listOf(
                A2uiSchemaKeyword.OneOf(
                    listOf(
                        literalSchema,
                        A2uiDataBindingSchema.DEFAULT_INSTANCE,
                        A2uiObjectSchema(
                            keywords =
                                listOf(
                                    A2uiSchemaKeyword.AllOf(
                                        listOf(
                                            A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                                            A2uiObjectSchema(
                                                properties =
                                                    mapOf(
                                                        "returnType" to
                                                            A2uiStringSchema(
                                                                keywords =
                                                                    listOf(
                                                                        A2uiSchemaKeyword.Const(
                                                                            returnType.value
                                                                        )
                                                                    )
                                                            )
                                                    )
                                            ),
                                        )
                                    )
                                )
                        ),
                    )
                )
            ),
        description = description,
    )
