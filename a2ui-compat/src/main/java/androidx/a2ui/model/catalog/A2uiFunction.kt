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

@file:JvmName("A2uiFunctionKt")

package androidx.a2ui.model.catalog

import androidx.a2ui.model.protocol.A2uiExecutionContext

/** Represents a function supported by A2UI including its definition and implementation. */
public interface A2uiFunction {
    /** The definition of the function. */
    public val definition: A2uiFunctionDefinition

    /**
     * Executes the function logic.
     *
     * @param args statically resolved arguments where dynamic properties are resolved beforehand
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return raw evaluated result
     */
    public fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any?
}
