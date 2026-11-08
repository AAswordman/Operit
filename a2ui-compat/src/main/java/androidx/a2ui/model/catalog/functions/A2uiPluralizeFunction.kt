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
import androidx.a2ui.model.schema.commontypes.A2uiDynamicNumberSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import java.util.Locale

/**
 * Interface to format messages with arguments using the ICU MessageFormat syntax.
 *
 * The implementation of this interface should be provided by the platform (e.g., using
 * [android.icu.text.MessageFormat] or `com.ibm.icu.text.MessageFormat`).
 */
public fun interface A2uiMessageFormatter {
    /**
     * Formats the specified pattern using the given locale and arguments.
     *
     * Implementations should ignore extra keys in [arguments] that are not present in the
     * [pattern]. If a key required by the [pattern] is missing from [arguments], the implementation
     * should throw an exception.
     *
     * @param pattern the message format pattern in ICU MessageFormat syntax
     * @param locale the locale to use for formatting
     * @param arguments the map of arguments to replace in the pattern
     * @return the formatted string
     */
    public fun format(pattern: String, locale: Locale, arguments: Map<String, Any>): String
}

/**
 * Chooses a plural string form based on a numeric value.
 *
 * Use this [A2uiFunction] to get localized plural categories.
 */
public class A2uiPluralizeFunction
@JvmOverloads
public constructor(
    private val messageFormatter: A2uiMessageFormatter,
    private val localeProvider: A2uiLocaleProvider = A2uiLocaleProvider.Default,
) : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "pluralize"

            override val description: String =
                """Returns a localized string based on the Common Locale Data Repository (CLDR) plural category of the count (zero, one, two, few, many, other). Requires an 'other' fallback. For English, just use 'one' and 'other'."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.STRING

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to
                                A2uiDynamicNumberSchema(
                                    description =
                                        """The numeric value used to determine the plural category."""
                                ),
                            ARG_ZERO_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """String for the 'zero' category (e.g., 0 items)."""
                                ),
                            ARG_ONE_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """String for the 'one' category (e.g., 1 item)."""
                                ),
                            ARG_TWO_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """String for the 'two' category (used in Arabic, Welsh, etc.)."""
                                ),
                            ARG_FEW_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """String for the 'few' category (e.g., small groups in Slavic languages)."""
                                ),
                            ARG_MANY_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """String for the 'many' category (e.g., large groups in various languages)."""
                                ),
                            ARG_OTHER_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """The default/fallback string (used for general plural cases)."""
                                ),
                        ),
                    required = setOf(ARG_VALUE_KEY, ARG_OTHER_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Returns the matching plural form based on count in [args].
     *
     * @param args arguments containing the count "value" and plural form strings
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return the selected plural form, or null if count or default fallback is missing
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getDoubleArg(args, ARG_VALUE_KEY)
        val other = A2uiFunctionArgParser.getStringArg(args, ARG_OTHER_KEY)

        val zero =
            if (args.containsKey(ARG_ZERO_KEY))
                A2uiFunctionArgParser.getStringArg(args, ARG_ZERO_KEY)
            else null
        val one =
            if (args.containsKey(ARG_ONE_KEY)) A2uiFunctionArgParser.getStringArg(args, ARG_ONE_KEY)
            else null
        val two =
            if (args.containsKey(ARG_TWO_KEY)) A2uiFunctionArgParser.getStringArg(args, ARG_TWO_KEY)
            else null
        val few =
            if (args.containsKey(ARG_FEW_KEY)) A2uiFunctionArgParser.getStringArg(args, ARG_FEW_KEY)
            else null
        val many =
            if (args.containsKey(ARG_MANY_KEY))
                A2uiFunctionArgParser.getStringArg(args, ARG_MANY_KEY)
            else null

        val locale = localeProvider.getLocale()

        val pattern = buildString {
            append("{count, plural, ")
            appendCategory("zero", zero)
            appendCategory("one", one)
            appendCategory("two", two)
            appendCategory("few", few)
            appendCategory("many", many)
            appendCategory("other", other)
            append("}")
        }

        return messageFormatter.format(pattern, locale, mapOf("count" to value))
    }

    private fun StringBuilder.appendCategory(category: String, value: String?) {
        value?.let { append(category).append(" {").append(it.escapeIcu()).append("} ") }
    }

    private fun String.escapeIcu(): String = replace("'", "''")

    private companion object {
        private const val ARG_VALUE_KEY: String = "value"
        private const val ARG_ZERO_KEY: String = "zero"
        private const val ARG_ONE_KEY: String = "one"
        private const val ARG_TWO_KEY: String = "two"
        private const val ARG_FEW_KEY: String = "few"
        private const val ARG_MANY_KEY: String = "many"
        private const val ARG_OTHER_KEY: String = "other"
    }
}
