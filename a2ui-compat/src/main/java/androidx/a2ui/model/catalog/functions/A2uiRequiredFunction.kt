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
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema

/**
 * Validates that a value is present and not empty.
 *
 * Use this [A2uiFunction] to check for non-empty values.
 */
public class A2uiRequiredFunction private constructor() : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "required"

            override val description: String =
                """Checks that the value is not null, undefined, or empty."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.BOOLEAN

            override val acceptsUnresolvedArguments: Boolean = true

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to A2uiAnySchema(description = """The value to check.""")
                        ),
                    required = setOf(ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Checks if the value in [args] is present and not empty.
     *
     * @param args arguments containing the "value" to check
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return true if the value is not missing, not null, and not empty
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        if (!args.containsKey(ARG_VALUE_KEY)) {
            return false
        }
        val value = A2uiFunctionArgParser.getArg(args, ARG_VALUE_KEY)
        return when (value) {
            is CharSequence -> value.isNotEmpty()
            is Collection<*> -> value.isNotEmpty()
            is Map<*, *> -> value.isNotEmpty()
            is Array<*> -> value.isNotEmpty()
            is IntArray -> value.isNotEmpty()
            is LongArray -> value.isNotEmpty()
            is DoubleArray -> value.isNotEmpty()
            is BooleanArray -> value.isNotEmpty()
            is ByteArray -> value.isNotEmpty()
            is CharArray -> value.isNotEmpty()
            is FloatArray -> value.isNotEmpty()
            is ShortArray -> value.isNotEmpty()
            else -> true
        }
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiRequiredFunction = A2uiRequiredFunction()

        private const val ARG_VALUE_KEY: String = "value"
    }
}
