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

import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.compose.runtime.Stable

/**
 * Represents a fully resolved and ready for rendering A2UI component.
 *
 * @property surface The [A2uiSurfaceModel] containing the data, components, and catalog.
 * @property type The string identifier of the component's type (e.g., `"Text"`, `"Button"`).
 * @property properties The stable [A2uiComponentProperties] payload defining the component's
 *   structure and dynamic data bindings.
 * @property scope The [A2uiComponentScope] bound to this specific component instance, providing
 *   mechanisms for data binding, child resolution, action dispatching, etc.
 */
@Stable
public class A2uiComponentModel(
    public val surface: A2uiSurfaceModel,
    public val type: String,
    public val properties: A2uiComponentProperties,
    public val scope: A2uiComponentScope,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiComponentModel) return false

        if (surface != other.surface) return false
        if (type != other.type) return false
        if (properties != other.properties) return false
        if (scope != other.scope) return false

        return true
    }

    override fun hashCode(): Int {
        var result = surface.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + scope.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiComponentModel(surface='${surface.id}', type='$type', properties=$properties)"
    }
}
