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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Enables the UI layer to evaluate whether a resolved A2UI component is ready to be rendered, as
 * defined by the component implementation (e.g., all its required dynamic data bindings have been
 * resolved).
 */
public interface A2uiReadinessEvaluator {

    /**
     * Evaluates if the component represented by the given [componentModel] is ready to transition
     * to [A2uiComponentState.Success].
     */
    @Composable public fun isReady(componentModel: A2uiComponentModel): Boolean

    public companion object {
        /** Default [A2uiReadinessEvaluator] that considers all components ready to be rendered. */
        public val Default: A2uiReadinessEvaluator =
            object : A2uiReadinessEvaluator {
                @Composable override fun isReady(componentModel: A2uiComponentModel): Boolean = true
            }
    }
}

/** Provider for [A2uiReadinessEvaluator]. */
public val LocalA2uiReadinessEvaluator: ProvidableCompositionLocal<A2uiReadinessEvaluator> =
    staticCompositionLocalOf {
        A2uiReadinessEvaluator.Default
    }
