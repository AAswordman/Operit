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

@file:JvmName("A2uiCoreComponentDefinitionKt")

package androidx.a2ui.engine.catalog

import androidx.a2ui.model.schema.A2uiSchema

/** Defines a single UI component and its schema. */
public interface A2uiCoreComponentDefinition {
    /** Component name that would be provided to the AI agent. */
    public val name: String

    /** Describes the component behavior so the AI agent knows when to use it. */
    public val description: String

    /** The schema of the properties of the component. */
    public val propertySchema: A2uiSchema
}

/**
 * Converts this component definition into an [A2uiSchema].
 *
 * @return the [A2uiSchema] representation of this component definition
 */
public fun A2uiCoreComponentDefinition.toSchema(): A2uiSchema =
    serializeComponentDefinitionToSchema(this)
