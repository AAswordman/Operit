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

package androidx.a2ui.model.catalog.functions

import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.catalog.A2uiFunctionDefinition
import androidx.a2ui.model.catalog.A2uiFunctionReturnType
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicBooleanSchema

/**
 * Negates a boolean value.
 *
 * Use this [A2uiFunction] to perform a logical NOT operation.
 */
public class A2uiNotFunction private constructor() : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "not"

            override val description: String =
                """Performs a logical NOT operation on a boolean value."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.BOOLEAN

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to
                                A2uiDynamicBooleanSchema(
                                    description = """The boolean value to negate."""
                                )
                        ),
                    required = setOf(ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Negates the boolean value provided in [args].
     *
     * @param args arguments containing the "value" boolean to negate
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return the logical negation of the input boolean, or true if value is missing
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getBooleanArg(args, ARG_VALUE_KEY)
        return !value
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiNotFunction = A2uiNotFunction()

        private const val ARG_VALUE_KEY: String = "value"
    }
}
