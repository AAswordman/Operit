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
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicBooleanSchema

/**
 * Evaluates a list of boolean values using logical AND.
 *
 * Use this [A2uiFunction] to evaluate if all given values are true.
 */
public class A2uiAndFunction private constructor() : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "and"

            override val description: String =
                """Performs a logical AND operation on a list of boolean values."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.BOOLEAN

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUES_KEY to
                                A2uiArraySchema(
                                    items = A2uiDynamicBooleanSchema.DEFAULT_INSTANCE,
                                    description = """The list of boolean values to evaluate.""",
                                )
                        ),
                    required = setOf(ARG_VALUES_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Runs the logical AND operation on the given [args].
     *
     * @param args arguments containing the "values" list of booleans to check
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return true if all values in the list are true, false otherwise
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val values = A2uiFunctionArgParser.getBooleanListArg(args, ARG_VALUES_KEY)
        return values.all { it }
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiAndFunction = A2uiAndFunction()

        private const val ARG_VALUES_KEY: String = "values"
    }
}
