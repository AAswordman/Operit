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

@file:JvmName("A2uiFunctionDefinitionSerializerKt")

package androidx.a2ui.model.catalog

import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema

/**
 * Converts a function definition into an [A2uiSchema].
 *
 * @param functionDefinition the function definition to serialize
 * @return the [A2uiSchema] representation of the function definition
 */
internal fun serializeFunctionDefinitionToSchema(
    functionDefinition: A2uiFunctionDefinition
): A2uiSchema =
    A2uiObjectSchema(
        description = functionDefinition.description.ifEmpty { null },
        properties =
            mapOf(
                "call" to
                    A2uiStringSchema(
                        keywords = listOf(A2uiSchemaKeyword.Const(functionDefinition.name))
                    ),
                "args" to functionDefinition.argumentSchema,
                "returnType" to
                    A2uiStringSchema(
                        keywords =
                            listOf(A2uiSchemaKeyword.Const(functionDefinition.returnType.value))
                    ),
            ),
        required = setOf("call", "args"),
        isAdditionalPropertiesAllowed = false,
    )
