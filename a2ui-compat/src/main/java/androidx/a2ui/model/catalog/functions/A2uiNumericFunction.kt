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
import androidx.a2ui.model.schema.A2uiNumberSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicNumberSchema

/**
 * Validates numeric range limits.
 *
 * Use this [A2uiFunction] to check minimum and maximum value bounds.
 */
public class A2uiNumericFunction private constructor() : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "numeric"

            override val description: String = """Checks numeric range constraints."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.BOOLEAN

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to A2uiDynamicNumberSchema.DEFAULT_INSTANCE,
                            ARG_MIN_KEY to
                                A2uiNumberSchema(description = """The minimum allowed value."""),
                            ARG_MAX_KEY to
                                A2uiNumberSchema(description = """The maximum allowed value."""),
                        ),
                    required = setOf(ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Validates that the numeric value in [args] meets range requirements.
     *
     * @param args arguments containing "value" number and optional "min" or "max" limits
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return true if the number is within bounds, false otherwise
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getDoubleArg(args, ARG_VALUE_KEY)
        val min =
            if (args.containsKey(ARG_MIN_KEY)) {
                A2uiFunctionArgParser.getDoubleArg(args, ARG_MIN_KEY)
            } else {
                Double.NEGATIVE_INFINITY
            }
        val max =
            if (args.containsKey(ARG_MAX_KEY)) {
                A2uiFunctionArgParser.getDoubleArg(args, ARG_MAX_KEY)
            } else {
                Double.POSITIVE_INFINITY
            }

        return value in min..max
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiNumericFunction = A2uiNumericFunction()

        private const val ARG_VALUE_KEY: String = "value"
        private const val ARG_MIN_KEY: String = "min"
        private const val ARG_MAX_KEY: String = "max"
    }
}
