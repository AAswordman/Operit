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

package androidx.a2ui.compose.runtime

import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.runtime.Immutable

/**
 * An immutable representation of a component's payload as managed by the [A2uiComponentRegistry].
 *
 * This record serves as the source of truth for the reactive Compose state mapping. It ensures that
 * updates streamed from the A2UI background processor are parsed, structurally validated, and
 * compared for equality before being placed into the Compose snapshot system.
 */
@Immutable
internal sealed interface A2uiComponentRecord {

    /**
     * Represents a successfully processed and schema-validated component payload.
     *
     * @property type The string identifier of the component's type (e.g., `"Text"`, `"Button"`).
     * @property properties The stable properties wrapper containing the structural properties and
     *   dynamic data binding definitions for this component.
     */
    @Immutable
    class Valid(val type: String, val properties: A2uiComponentProperties) : A2uiComponentRecord {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Valid) return false
            if (type != other.type) return false
            return properties == other.properties
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + properties.hashCode()
            return result
        }
    }

    /**
     * Represents a component that failed schema validation, referenced an unknown type, or
     * otherwise encountered a processing error.
     *
     * @property exception The [A2uiException] detailing why this component failed to process.
     */
    @Immutable
    class Error(val exception: A2uiException) : A2uiComponentRecord {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Error) return false
            return exception == other.exception
        }

        override fun hashCode(): Int = exception.hashCode()
    }
}
