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
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema

/**
 * Validates a string against a regular expression pattern.
 *
 * Use this [A2uiFunction] to check patterns in values.
 */
public class A2uiRegexFunction private constructor() : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "regex"

            override val description: String =
                """Checks that the value matches a regular expression string."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.BOOLEAN

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to A2uiDynamicStringSchema.DEFAULT_INSTANCE,
                            ARG_PATTERN_KEY to
                                A2uiStringSchema(
                                    description = """The regex pattern to match against."""
                                ),
                        ),
                    required = setOf(ARG_VALUE_KEY, ARG_PATTERN_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Validates that the input matches the pattern in [args].
     *
     * @param args arguments containing "value" string and "pattern" regex string
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return true if the string matches the pattern, false otherwise
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getStringArg(args, ARG_VALUE_KEY)
        val pattern = A2uiFunctionArgParser.getStringArg(args, ARG_PATTERN_KEY)

        return try {
            Regex(pattern).matches(value)
        } catch (e: Exception) {
            throw A2uiException.A2uiRuntimeException(
                "Function ${definition.name} was invoked with an invalid regular expression pattern: pattern",
                mapOf("cause" to e.message),
            )
        }
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiRegexFunction = A2uiRegexFunction()

        private const val ARG_VALUE_KEY: String = "value"
        private const val ARG_PATTERN_KEY: String = "pattern"
    }
}
