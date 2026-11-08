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

package androidx.a2ui.model.protocol

/**
 * Parses JSON pointer strings (RFC 6901) into segments.
 *
 * A JSON Pointer is a string syntax for identifying a specific value within a JSON document. This
 * implementation aligns with the A2UI practices, which includes deviations from strict RFC 6901:
 * - Empty string `""` and `"/"` both evaluate to the root document (empty segments).
 * - A single trailing slash is stripped. (Note: To maintain parsing parity with other A2UI
 *   renderers, exactly one trailing slash is removed. e.g., "/foo//" evaluates to "/foo/").
 * - Missing leading slashes are permitted and represent relative paths.
 *
 * @property path The raw JSON pointer string passed during construction.
 */
public class A2uiDataPath(public val path: String) {
    /** The normalized, canonical JSON pointer string. */
    public val normalizedPath: String = normalize(path)

    /** The decoded segments of the JSON pointer. */
    public val segments: List<String> = parse(normalizedPath)

    /** True if this path is absolute (starts with '/' or is empty representing root). */
    public val isAbsolute: Boolean
        get() = path.isEmpty() || path.startsWith("/")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiDataPath) return false
        return isAbsolute == other.isAbsolute && normalizedPath == other.normalizedPath
    }

    override fun hashCode(): Int {
        return 31 * normalizedPath.hashCode() + isAbsolute.hashCode()
    }

    override fun toString(): String {
        return "A2uiDataPath(path='$path', normalizedPath='$normalizedPath', isAbsolute=$isAbsolute)"
    }

    /**
     * Appends a relative path segment [other] to this path.
     *
     * If [other] starts with `/`, it is treated as an absolute path and replaces this path.
     *
     * @param other segment to append
     */
    public operator fun div(other: String): A2uiDataPath {
        return when {
            other.startsWith("/") -> A2uiDataPath(other)
            other.isEmpty() -> this
            path.endsWith("/") -> A2uiDataPath("$path$other")
            else -> A2uiDataPath("$path/$other")
        }
    }

    /**
     * Appends another [A2uiDataPath] to this path.
     *
     * If [other] is absolute, it replaces this path.
     *
     * @param other path to append
     */
    public operator fun div(other: A2uiDataPath): A2uiDataPath {
        return if (other.isAbsolute) {
            other
        } else {
            this / other.path
        }
    }

    private companion object {
        private fun normalize(path: String): String {
            if (path.isEmpty() || path == "/") return ""

            var normalized = path
            // Note: To maintain parsing parity with other A2UI renderers, we intentionally strip
            // exactly one trailing slash rather than all of them.
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length - 1)
            }
            return normalized
        }

        private fun parse(normalizedPath: String): List<String> {
            if (normalizedPath.isEmpty()) return emptyList()

            var clean = normalizedPath
            if (clean.startsWith("/")) {
                clean = clean.substring(1)
            }
            if (clean.isEmpty()) return emptyList()

            return clean.split("/").map { token -> token.replace("~1", "/").replace("~0", "~") }
        }
    }
}
