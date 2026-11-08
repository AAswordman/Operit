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

import androidx.compose.runtime.Immutable

/**
 * Represents a pointer to an A2UI component with its optional data scope override.
 *
 * @property id The unique ID of the component.
 * @property baseDataPath An optional relative or absolute data path to override the component's
 *   data context.
 */
@Immutable
public class A2uiComponentReference(
    public val id: String,
    public val baseDataPath: String? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiComponentReference) return false

        if (id != other.id) return false
        if (baseDataPath != other.baseDataPath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + baseDataPath.hashCode()
        return result
    }

    override fun toString(): String {
        return "ComponentReference(id='$id', baseDataPath='$baseDataPath')"
    }
}
