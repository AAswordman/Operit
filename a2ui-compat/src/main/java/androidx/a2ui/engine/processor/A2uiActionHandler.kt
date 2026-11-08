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

package androidx.a2ui.engine.processor

import androidx.a2ui.engine.model.A2uiCoreExecutionContext
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.protocol.A2uiClientDataModel
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.a2ui.model.protocol.A2uiFunctionCallAction
import androidx.a2ui.model.protocol.A2uiUserAction

/**
 * Processes user interactions on A2UI components.
 *
 * Routes [A2uiUserAction]s to the server via [A2uiEventAction] or executes them locally via
 * [A2uiFunctionCallAction]. Passes actions through a chain of [A2uiActionInterceptor]s before final
 * execution.
 */
internal class A2uiActionHandler(
    private val actionInterceptors: List<A2uiActionInterceptor>,
    private val clientDataModelProvider: () -> A2uiClientDataModel?,
    private val emitToServer: (A2uiClientToServerMessage) -> Unit,
) {

    /** Executes the given user action within the specified execution context. */
    suspend fun handleAction(action: A2uiUserAction, executionContext: A2uiCoreExecutionContext) {
        var currentAction: A2uiUserAction = action
        for (interceptor in actionInterceptors) {
            currentAction = interceptor.onInterceptAction(currentAction) ?: return
        }

        when (currentAction) {
            is A2uiEventAction -> {
                val resolvedContext = resolvePayloadMap(executionContext, currentAction.context)
                val eventMessage =
                    A2uiClientEventMessage(
                        type = currentAction.eventName,
                        surfaceId = currentAction.surfaceId,
                        componentId = currentAction.componentId,
                        timestamp = currentAction.timestamp,
                        context = resolvedContext,
                        clientDataModel = clientDataModelProvider(),
                    )
                emitToServer(eventMessage)
            }
            is A2uiFunctionCallAction -> {
                val resolvedArgs = resolvePayloadMap(executionContext, currentAction.args)
                executionContext.executeFunction(currentAction.functionName, resolvedArgs)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolvePayloadMap(
        executionContext: A2uiCoreExecutionContext,
        payload: Map<String, Any?>,
    ): Map<String, Any> {
        return executionContext.evaluatePayload(payload) as? Map<String, Any> ?: emptyMap()
    }
}
