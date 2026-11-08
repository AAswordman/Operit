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

@file:JvmName("A2uiFunctionDefinitionKt")

package androidx.a2ui.model.catalog

import androidx.a2ui.model.schema.A2uiSchema

/** The JSON-compatible return types a function can advertise to the AI agent. */
public enum class A2uiFunctionReturnType(public val value: String) {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object"),
    ANY("any"),
    VOID("void"),
}

/** Represents the definition and schema of a function supported by A2UI. */
public interface A2uiFunctionDefinition {
    /** Function name that would be provided to the AI agent. */
    public val name: String

    /** Describes the function behavior so the AI agent knows when to use it. */
    public val description: String

    /** The schema of the arguments expected by the component. */
    public val argumentSchema: A2uiSchema

    /** The type of the value returned by the function. */
    public val returnType: A2uiFunctionReturnType

    /**
     * Indicates if this function should execute even if some of its data-bound arguments cannot be
     * resolved (e.g., data is not yet available for a dynamic property).
     *
     * When false (default), if any argument cannot be resolved, evaluation halts and returns null
     * indicating to the component that it should wait in a loading state.
     *
     * When true, arguments whose data bindings cannot be resolved are omitted from the arguments
     * map passed to [A2uiFunction.execute], allowing the function to execute with the remaining
     * resolved arguments.
     */
    @get:Suppress("GetterSetterNames")
    public val acceptsUnresolvedArguments: Boolean
        get() = false
}

/**
 * Converts this function definition into an [A2uiSchema].
 *
 * @return the [A2uiSchema] representation of this function definition
 */
public fun A2uiFunctionDefinition.toSchema(): A2uiSchema = serializeFunctionDefinitionToSchema(this)
