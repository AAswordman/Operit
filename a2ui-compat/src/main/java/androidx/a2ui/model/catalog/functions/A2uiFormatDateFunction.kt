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
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicValueSchema
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats a timestamp into a date string.
 *
 * Use this [A2uiFunction] to format date values with a pattern.
 */
public class A2uiFormatDateFunction
@JvmOverloads
public constructor(private val localeProvider: A2uiLocaleProvider = A2uiLocaleProvider.Default) :
    A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "formatDate"

            override val description: String =
                """Formats a timestamp into a string using a pattern."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.STRING

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_VALUE_KEY to
                                A2uiDynamicValueSchema(description = """The date to format."""),
                            ARG_FORMAT_KEY to
                                A2uiDynamicStringSchema(
                                    description =
                                        """
                                        A Unicode TR35 date pattern string.

                                        Token Reference:
                                        - Year: 'yy' (26), 'yyyy' (2026)
                                        - Month: 'M' (1), 'MM' (01), 'MMM' (Jan), 'MMMM' (January)
                                        - Day: 'd' (1), 'dd' (01), 'E' (Tue), 'EEEE' (Tuesday)
                                        - Hour (12h): 'h' (1-12), 'hh' (01-12) - requires 'a' for AM/PM
                                        - Hour (24h): 'H' (0-23), 'HH' (00-23) - Military Time
                                        - Minute: 'mm' (00-59)
                                        - Second: 'ss' (00-59)
                                        - Period: 'a' (AM/PM)

                                        Examples:
                                        - 'MMM dd, yyyy' -> 'Jan 16, 2026'
                                        - 'HH:mm' -> '14:30' (Military)
                                        - 'h:mm a' -> '2:30 PM'
                                        - 'EEEE, d MMMM' -> 'Friday, 16 January'
                                        """
                                            .trimIndent()
                                ),
                        ),
                    required = setOf(ARG_FORMAT_KEY, ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Formats the given timestamp or ISO date string using the pattern in [args].
     *
     * @param args arguments containing "value" (date string or numeric timestamp) and "format"
     *   (pattern string)
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return the formatted date string, or null if required values are missing
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val epochMillis = A2uiFunctionArgParser.getEpochMillisArg(args, ARG_VALUE_KEY)
        val format = A2uiFunctionArgParser.getStringArg(args, ARG_FORMAT_KEY)

        val date = Date(epochMillis)
        val utcZone = TimeZone.getTimeZone("UTC")

        return if (format == FORMAT_ISO) {
            val sdf =
                SimpleDateFormat(ISO_FORMAT_PATTERN, Locale.US).apply {
                    timeZone = utcZone
                }
            sdf.format(date)
        } else {
            try {
                val locale = localeProvider.getLocale()
                val sdf =
                    SimpleDateFormat(format, locale).apply {
                        timeZone = utcZone
                    }
                sdf.format(date)
            } catch (e: Exception) {
                throw A2uiException.A2uiRuntimeException(
                    "Function ${definition.name} failed to format date with pattern: '$format'",
                    mapOf("cause" to e.message),
                )
            }
        }
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiFormatDateFunction = A2uiFormatDateFunction()

        private const val ARG_VALUE_KEY: String = "value"
        private const val ARG_FORMAT_KEY: String = "format"
        private const val FORMAT_ISO: String = "ISO"
        private const val ISO_FORMAT_PATTERN: String = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
}
