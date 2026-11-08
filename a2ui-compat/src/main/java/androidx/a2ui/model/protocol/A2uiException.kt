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
 * Base class for all A2UI protocol and execution errors.
 *
 * @property code An error category identifier (such as "VALIDATION_FAILED" or "RUNTIME_ERROR") that
 *   is automatically assigned by the exception subclass. This identifier is reported back to the
 *   server in the error payload, allowing the remote agent to recognize and handle the specific
 *   category of failure.
 * @property message A human-readable description of the error for debugging purposes. This message
 *   will also be passed to the agent to help it diagnose the issue.
 * @property context A map containing custom properties and error context (such as the target path).
 */
public sealed class A2uiException
private constructor(
    public val code: String,
    message: String,
    public val context: Map<String, Any?> = emptyMap(),
) : Exception(message) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiException) return false
        if (this.javaClass != other.javaClass) return false
        return (code == other.code) && (context == other.context) && (message == other.message)
    }

    override fun hashCode(): Int {
        var result = this.javaClass.hashCode()
        result = (31 * result) + code.hashCode()
        result = (31 * result) + context.hashCode()
        result = (31 * result) + (message?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        val fields =
            listOf("code=$code", "message=$message") + context.map { "${it.key}=${it.value}" }
        return "${javaClass.simpleName}(${fields.joinToString()})"
    }

    /**
     * Thrown when an incoming JSON payload from the server structurally violates its defined
     * `A2uiSchema`. The framework throws this *before* allowing corrupted data into the state
     * models.
     *
     * @param message A human-readable description of the validation error.
     * @param path The JSON pointer path identifying where the validation failure occurred.
     */
    public class A2uiValidationException(message: String, path: String) :
        A2uiException("VALIDATION_FAILED", message, mapOf("path" to path))

    /**
     * Thrown for any client-side runtime or execution failures (such as dynamic binding or function
     * evaluation failures) that should be reported to the agent and are not structural validation
     * errors
     *
     * @param message A human-readable description of the runtime error.
     * @param context A map containing custom properties and error context associated with this
     *   error.
     */
    public class A2uiRuntimeException(message: String, context: Map<String, Any?> = emptyMap()) :
        A2uiException("RUNTIME_ERROR", message, context)
}
