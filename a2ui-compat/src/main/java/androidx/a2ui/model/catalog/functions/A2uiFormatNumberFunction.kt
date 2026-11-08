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
import androidx.a2ui.model.schema.commontypes.A2uiDynamicNumberSchema
import java.text.NumberFormat

/**
 * Formats a number with grouping and decimal precision.
 *
 * Use this [A2uiFunction] to format numeric values for display.
 */
public class A2uiFormatNumberFunction
@JvmOverloads
public constructor(private val localeProvider: A2uiLocaleProvider = A2uiLocaleProvider.Default) :
    A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "formatNumber"

            override val description: String =
                """Formats a number with the specified grouping and decimal precision."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.STRING

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to
                                A2uiDynamicNumberSchema(description = """The number to format."""),
                            ARG_DECIMALS_KEY to
                                A2uiDynamicNumberSchema(
                                    description =
                                        """Optional. The number of decimal places to show. Defaults to 0 or 2 depending on locale."""
                                ),
                            ARG_GROUPING_KEY to
                                A2uiDynamicBooleanSchema(
                                    description =
                                        """Optional. If true, uses locale-specific grouping separators (e.g. '1,000'). If false, returns raw digits (e.g. '1000'). Defaults to true."""
                                ),
                        ),
                    required = setOf(ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Formats the given number using precision options in [args].
     *
     * @param args arguments containing "value" (number) and optional decimal or grouping flags
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return the formatted number string, or null if required values are missing
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getDoubleArg(args, ARG_VALUE_KEY)
        val decimals =
            if (args.containsKey(ARG_DECIMALS_KEY)) {
                A2uiFunctionArgParser.getIntArg(args, ARG_DECIMALS_KEY)
            } else {
                null
            }
        val grouping =
            if (args.containsKey(ARG_GROUPING_KEY)) {
                A2uiFunctionArgParser.getBooleanArg(args, ARG_GROUPING_KEY)
            } else {
                true
            }

        val locale = localeProvider.getLocale()
        val formatter =
            NumberFormat.getNumberInstance(locale).apply {
                isGroupingUsed = grouping
                if (decimals != null) {
                    minimumFractionDigits = decimals
                    maximumFractionDigits = decimals
                }
            }
        return formatter.format(value)
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiFormatNumberFunction = A2uiFormatNumberFunction()

        private const val ARG_VALUE_KEY: String = "value"
        private const val ARG_DECIMALS_KEY: String = "decimals"
        private const val ARG_GROUPING_KEY: String = "grouping"
    }
}
