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
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import androidx.core.util.PatternsCompat

/**
 * Validates if a string is a valid email address.
 *
 * Use this [A2uiFunction] to check email formatting.
 */
public class A2uiEmailFunction private constructor() : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "email"

            override val description: String = """Checks that the value is a valid email address."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.BOOLEAN

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties = mapOf(ARG_VALUE_KEY to A2uiDynamicStringSchema.DEFAULT_INSTANCE),
                    required = setOf(ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Runs email format validation on the given [args].
     *
     * @param args arguments containing the "value" string to validate
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return true if the value matches the email regex, false otherwise
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getStringArg(args, ARG_VALUE_KEY)

        return PatternsCompat.EMAIL_ADDRESS.matcher(value).matches()
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiEmailFunction = A2uiEmailFunction()

        private const val ARG_VALUE_KEY: String = "value"
    }
}
