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

import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Represents the state of an A2UI component instance.
 *
 * Components transition between these states as their payloads are loaded, evaluated, and prepared
 * to be rendered.
 */
@Immutable
public sealed interface A2uiComponentState {

    /** Indicates that the component's definition and properties are currently loading. */
    public data object Loading : A2uiComponentState

    /**
     * Indicates that the component failed to resolve, evaluate, or render.
     *
     * @property exception The [A2uiException] detailing the failure reason.
     */
    public class Error(public val exception: A2uiException) : A2uiComponentState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Error) return false
            return exception == other.exception
        }

        override fun hashCode(): Int = exception.hashCode()

        override fun toString(): String = "ComponentState.Error(exception=$exception)"
    }

    /**
     * Indicates that the component successfully resolved its definition and properties, and is
     * ready to be emitted into the composition tree.
     *
     * This state holds a [A2uiComponentModel] instance that contains pure data and context
     * references required for rendering.
     *
     * @property component The resolved component containing properties and context.
     */
    public class Success(public val component: A2uiComponentModel) : A2uiComponentState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            if (component != other.component) return false
            return true
        }

        override fun hashCode(): Int = component.hashCode()

        override fun toString(): String {
            return "ComponentState.Success(component=$component)"
        }
    }
}

/**
 * Resolves the current [A2uiComponentState] of the root component for an A2UI surface and
 * subscribes for future updates.
 *
 * @param surface The [A2uiSurfaceModel] containing the data, components, and catalog for this UI.
 * @return The reactive state of the root component.
 */
@Composable
public fun observeA2uiComponentState(surface: A2uiSurfaceModel): A2uiComponentState =
    observeA2uiComponentState(
        id = RootComponentId,
        baseDataPath = RootComponentDataPath,
        surface = surface,
    )

@Composable
internal fun observeA2uiComponentState(
    id: String,
    baseDataPath: A2uiDataPath,
    surface: A2uiSurfaceModel,
): A2uiComponentState {
    surface as? A2uiCoreSurfaceModel
        ?: throw IllegalArgumentException("The Compose renderer requires an A2uiCoreSurfaceModel")
    val registry =
        surface.componentRegistry as? A2uiComponentRegistry
            ?: throw IllegalArgumentException(
                "The Compose renderer requires an A2uiComponentRegistry"
            )
    val record = registry.get(id)

    // Capture the current local action interceptor
    val localActionInterceptor = rememberUpdatedState(LocalA2uiActionInterceptor.current)

    val scope =
        remember(id, baseDataPath, surface) {
            A2uiComponentScopeImpl(
                id = id,
                baseDataPath = baseDataPath,
                surface = surface,
                actionInterceptorProducer = { localActionInterceptor.value },
            )
        }

    val state =
        remember(record, surface, scope) {
            when (record) {
                null -> A2uiComponentState.Loading
                is A2uiComponentRecord.Error -> A2uiComponentState.Error(record.exception)
                is A2uiComponentRecord.Valid -> {
                    A2uiComponentState.Success(
                        A2uiComponentModel(
                            surface = surface,
                            type = record.type,
                            properties = record.properties,
                            scope = scope,
                        )
                    )
                }
            }
        }

    if (state is A2uiComponentState.Success) {
        val evaluator = LocalA2uiReadinessEvaluator.current
        if (!evaluator.isReady(state.component)) {
            return A2uiComponentState.Loading
        }
    }

    return state
}

private const val RootComponentId = "root"
private val RootComponentDataPath = A2uiDataPath("/")
