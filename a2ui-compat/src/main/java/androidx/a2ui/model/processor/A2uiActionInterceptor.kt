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

package androidx.a2ui.model.processor

import androidx.a2ui.model.protocol.A2uiUserAction

/**
 * Intercepts, transforms, or drops [A2uiUserAction]s before they are processed by the core layer.
 *
 * This acts as a middleware pipeline. Multiple interceptors can be registered. The output of one
 * interceptor is passed as the input to the next in the chain. The order of registration dictates
 * the order of execution. It can process both local function actions and server event actions.
 */
public fun interface A2uiActionInterceptor {
    /**
     * Called before a user action is handled by the core layer.
     *
     * Note: This method blocks the processing pipeline for the surface. Implementations should be
     * lightweight.
     *
     * @param action the incoming user action details.
     * @return The original action, a transformed/modified action to pass to the next interceptor,
     *   or `null` to consume the action entirely and stop further processing.
     */
    public suspend fun onInterceptAction(action: A2uiUserAction): A2uiUserAction?
}
