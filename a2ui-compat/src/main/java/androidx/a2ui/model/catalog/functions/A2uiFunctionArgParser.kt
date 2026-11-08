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

import androidx.a2ui.model.protocol.A2uiException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Reusable utility methods to parse and validate dynamic arguments passed to catalog functions. */
public object A2uiFunctionArgParser {

    /**
     * Retrieves a raw argument value from the [args] map under the given [key].
     *
     * @param args The map of arguments.
     * @param key The argument key to retrieve.
     * @param path The parent context path. The final error path will be constructed as
     *   `"${path}/${key}"`.
     * @return The raw argument value.
     * @throws A2uiException.A2uiValidationException if the argument is missing.
     */
    @JvmOverloads
    public fun getArg(args: Map<String, Any>, key: String, path: String = "/"): Any {
        return args[key]
            ?: throw A2uiException.A2uiValidationException(
                "Missing '$key' argument",
                concatPath(path, key),
            )
    }

    /**
     * Retrieves a String argument under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final error path will be constructed as
     *   `"${path}/${key}"`.
     * @return The resolved String value.
     * @throws A2uiException.A2uiValidationException if the argument is missing, invalid or violates
     *   constraints.
     */
    @JvmOverloads
    public fun getStringArg(args: Map<String, Any>, key: String, path: String = "/"): String {
        val raw = getArg(args, key, path)
        return parseString(raw)
    }

    /**
     * Retrieves a List of Strings under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final element path will be constructed with the
     *   index suffix, like `${path}/${key}/${index}`.
     * @return The resolved List of Strings.
     * @throws A2uiException.A2uiValidationException if the argument is missing or any element
     *   violates constraints.
     */
    @JvmOverloads
    public fun getStringListArg(
        args: Map<String, Any>,
        key: String,
        path: String = "/",
    ): List<String> {
        return getListInternal(args, key, path) { element, _ -> parseString(element) }
    }

    /**
     * Retrieves a Double argument under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final error path will be constructed as
     *   `"${path}/${key}"`.
     * @return The resolved Double value.
     * @throws A2uiException.A2uiValidationException if the argument is missing or invalid.
     */
    @JvmOverloads
    public fun getDoubleArg(args: Map<String, Any>, key: String, path: String = "/"): Double {
        val raw = getArg(args, key, path)
        return parseDouble(raw, concatPath(path, key), key)
    }

    /**
     * Retrieves a List of Doubles under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final element path will be constructed with the
     *   index suffix, like `${path}/${key}/${index}`.
     * @return The resolved List of Doubles.
     * @throws A2uiException.A2uiValidationException if the argument is missing or any element
     *   violates constraints.
     */
    @JvmOverloads
    public fun getDoubleListArg(
        args: Map<String, Any>,
        key: String,
        path: String = "/",
    ): List<Double> {
        return getListInternal(args, key, path) { element, elementPath ->
            parseDouble(element, elementPath, key)
        }
    }

    /**
     * Retrieves an Int argument under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final error path will be constructed as
     *   `"${path}/${key}"`.
     * @return The resolved Int value.
     * @throws A2uiException.A2uiValidationException if the argument is missing or invalid.
     */
    @JvmOverloads
    public fun getIntArg(args: Map<String, Any>, key: String, path: String = "/"): Int {
        val raw = getArg(args, key, path)
        return parseInt(raw, concatPath(path, key), key)
    }

    /**
     * Retrieves a List of Ints under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final element path will be constructed with the
     *   index suffix, like `${path}/${key}/${index}`.
     * @return The resolved List of Ints.
     * @throws A2uiException.A2uiValidationException if the argument is missing or any element
     *   violates constraints.
     */
    @JvmOverloads
    public fun getIntListArg(args: Map<String, Any>, key: String, path: String = "/"): List<Int> {
        return getListInternal(args, key, path) { element, elementPath ->
            parseInt(element, elementPath, key)
        }
    }

    /**
     * Retrieves a Long argument under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final error path will be constructed as
     *   `"${path}/${key}"`.
     * @return The resolved Long value.
     * @throws A2uiException.A2uiValidationException if the argument is missing or invalid.
     */
    @JvmOverloads
    public fun getLongArg(args: Map<String, Any>, key: String, path: String = "/"): Long {
        val raw = getArg(args, key, path)
        return parseLong(raw, concatPath(path, key), key)
    }

    /**
     * Retrieves a List of Longs under [key].
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final element path will be constructed with the
     *   index suffix, like `${path}/${key}/${index}`.
     * @return The resolved List of Longs.
     * @throws A2uiException.A2uiValidationException if the argument is missing or any element
     *   violates constraints.
     */
    @JvmOverloads
    public fun getLongListArg(args: Map<String, Any>, key: String, path: String = "/"): List<Long> {
        return getListInternal(args, key, path) { element, elementPath ->
            parseLong(element, elementPath, key)
        }
    }

    /**
     * Retrieves a Boolean argument under [key] supporting either actual Booleans or the
     * case-insensitive strings "true" and "false".
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final error path will be constructed as
     *   `"${path}/${key}"`.
     * @return The resolved Boolean value.
     * @throws A2uiException.A2uiValidationException if the argument is missing or invalid.
     */
    @JvmOverloads
    public fun getBooleanArg(args: Map<String, Any>, key: String, path: String = "/"): Boolean {
        val raw = getArg(args, key, path)
        return parseBoolean(raw, concatPath(path, key), key)
    }

    /**
     * Retrieves a List of Booleans under [key] supporting either actual Booleans or the
     * case-insensitive strings "true" and "false".
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final element path will be constructed with the
     *   index suffix, like `${path}/${key}/${index}`.
     * @return The resolved List of Booleans.
     * @throws A2uiException.A2uiValidationException if the argument is missing or any element
     *   violates constraints.
     */
    @JvmOverloads
    public fun getBooleanListArg(
        args: Map<String, Any>,
        key: String,
        path: String = "/",
    ): List<Boolean> {
        return getListInternal(args, key, path) { element, elementPath ->
            parseBoolean(element, elementPath, key)
        }
    }

    /**
     * Retrieves a UTC epoch millisecond timestamp argument under [key], supporting numeric
     * timestamps (in seconds or milliseconds) and ISO 8601 date / date-time strings.
     *
     * Date-only strings (e.g., `"2026-06-15"`) are normalized to UTC start-of-day. Numeric values
     * less than 10,000,000,000 are treated as epoch seconds, otherwise as epoch milliseconds.
     *
     * @param args The map of arguments.
     * @param key The argument key.
     * @param path The parent context path. The final error path will be constructed as
     *   `"${path}/${key}"`.
     * @return The resolved timestamp in UTC epoch milliseconds.
     * @throws A2uiException.A2uiValidationException if the argument is missing or cannot be parsed
     *   as a date or timestamp.
     */
    @JvmOverloads
    public fun getEpochMillisArg(
        args: Map<String, Any>,
        key: String,
        path: String = "/",
    ): Long {
        val raw = getArg(args, key, path)
        val argPath = concatPath(path, key)

        val num =
            when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }
        if (num != null) {
            return if (num < MAX_EPOCH_SECONDS) {
                (num * 1000L).toLong()
            } else {
                num.toLong()
            }
        }

        if (raw is String) {
            val trimmed = raw.trim()
            val utcZone = TimeZone.getTimeZone("UTC")
            for (pattern in ISO_PATTERNS) {
                try {
                    val parser =
                        SimpleDateFormat(pattern, Locale.US).apply {
                            timeZone = utcZone
                            isLenient = false
                        }
                    val date = parser.parse(trimmed)
                    if (date != null) {
                        return date.time
                    }
                } catch (_: ParseException) {
                    // Ignore and try next pattern
                }
            }
        }

        throw A2uiException.A2uiValidationException(
            "Invalid '$key' argument, expected date or timestamp",
            argPath,
        )
    }

    private fun parseString(raw: Any): String {
        return raw.toString()
    }

    private fun parseDouble(raw: Any, path: String, key: String): Double {
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
            ?: throw A2uiException.A2uiValidationException(
                "Invalid '$key' argument, expected double",
                path,
            )
    }

    private fun parseInt(raw: Any, path: String, key: String): Int {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
            ?: throw A2uiException.A2uiValidationException(
                "Invalid '$key' argument, expected integer",
                path,
            )
    }

    private fun parseLong(raw: Any, path: String, key: String): Long {
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
            ?: throw A2uiException.A2uiValidationException(
                "Invalid '$key' argument, expected long",
                path,
            )
    }

    private fun parseBoolean(raw: Any, path: String, key: String): Boolean {
        return when {
            raw is Boolean -> raw
            raw is String && raw.equals("true", ignoreCase = true) -> true
            raw is String && raw.equals("false", ignoreCase = true) -> false
            else ->
                throw A2uiException.A2uiValidationException(
                    "Invalid '$key' argument, expected boolean",
                    path,
                )
        }
    }

    private fun <T> getListInternal(
        args: Map<String, Any>,
        key: String,
        path: String = "/",
        itemParser: (Any, String) -> T,
    ): List<T> {
        val raw =
            args[key]
                ?: throw A2uiException.A2uiValidationException(
                    "Missing '$key' argument",
                    concatPath(path, key),
                )
        val rawList =
            raw as? List<*>
                ?: throw A2uiException.A2uiValidationException(
                    "Missing or invalid '$key' argument, expected list",
                    concatPath(path, key),
                )
        val finalPath = concatPath(path, key)
        return rawList.mapIndexed { index, element ->
            if (element == null) {
                throw A2uiException.A2uiValidationException(
                    "Null item in '$key' list",
                    concatPath(finalPath, index.toString()),
                )
            }
            itemParser(element, concatPath(finalPath, index.toString()))
        }
    }

    private fun concatPath(path: String, segment: String): String =
        if (path.endsWith("/")) "$path$segment" else "$path/$segment"

    private const val MAX_EPOCH_SECONDS: Long = 10_000_000_000L

    private val ISO_PATTERNS =
        arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mmX",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd",
        )
}
