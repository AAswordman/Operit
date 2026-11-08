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
import androidx.a2ui.model.schema.commontypes.A2uiDynamicBooleanSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicNumberSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import java.text.NumberFormat
import java.util.Currency

/**
 * Formats a number as a currency string.
 *
 * Use this [A2uiFunction] to display monetary values.
 */
public class A2uiFormatCurrencyFunction
@JvmOverloads
public constructor(private val localeProvider: A2uiLocaleProvider = A2uiLocaleProvider.Default) :
    A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "formatCurrency"

            override val description: String = """Formats a number as a currency string."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.STRING

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to
                                A2uiDynamicNumberSchema(description = """The monetary amount."""),
                            ARG_CURRENCY_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """The ISO 4217 currency code (e.g., 'USD', 'EUR')."""
                                ),
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
                    required = setOf(ARG_CURRENCY_KEY, ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Formats the monetary number into a currency string based on [args].
     *
     * @param args arguments conforming of the schema within [definition]
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return the formatted currency string, or null if required values are missing
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getDoubleArg(args, ARG_VALUE_KEY)
        val currencyCode = A2uiFunctionArgParser.getStringArg(args, ARG_CURRENCY_KEY)
        val decimals =
            if (args.containsKey(ARG_DECIMALS_KEY)) {
                A2uiFunctionArgParser.getDoubleArg(args, ARG_DECIMALS_KEY).toInt()
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
        val currency =
            try {
                Currency.getInstance(currencyCode.uppercase())
            } catch (e: Exception) {
                throw A2uiException.A2uiRuntimeException(
                    "Function ${definition.name} was invoked with an invalid currency code: '$currencyCode'"
                )
            }
        val formatter =
            NumberFormat.getCurrencyInstance(locale).apply {
                isGroupingUsed = grouping
                this.currency = currency
                if (decimals != null) {
                    minimumFractionDigits = decimals
                    maximumFractionDigits = decimals
                }
            }
        return formatter.format(value)
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiFormatCurrencyFunction = A2uiFormatCurrencyFunction()

        private const val ARG_VALUE_KEY: String = "value"
        private const val ARG_CURRENCY_KEY: String = "currency"
        private const val ARG_DECIMALS_KEY: String = "decimals"
        private const val ARG_GROUPING_KEY: String = "grouping"
    }
}
