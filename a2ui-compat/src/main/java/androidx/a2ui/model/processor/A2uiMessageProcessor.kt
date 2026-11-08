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

@file:JvmName("A2uiMessageProcessorKt")

package androidx.a2ui.model.processor

import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiProtocolConstants
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The A2UI message processor.
 *
 * This processor is the central orchestration engine for the A2UI core data layer. It receives
 * parsed protocol messages from the host application and exposes the outbound network events back
 * to the host framework.
 *
 * TODO(b/532001163): add an sample for this API and reference it from `collectMessages` and
 *   `processMessage`.
 */
public interface A2uiMessageProcessor {

    /** A flow of the active surface models currently managed by the processor. */
    public val activeSurfaces: StateFlow<List<A2uiSurfaceModel>>

    /** A flow of user actions and error payloads to be transmitted to the server. */
    public val outboundEvents: Flow<A2uiClientToServerMessage>

    /**
     * Enqueues a parsed protocol message to be processed.
     *
     * Note that this method does not execute the processing itself. Messages are queued and will
     * only be handled while [collectMessages] is actively running.
     *
     * This method is thread-safe and can be called from any thread.
     */
    public fun processMessage(message: A2uiServerToClientMessage)

    /**
     * Enqueues a client error message to be transmitted back to the server via [outboundEvents].
     *
     * @param error The client error message to transmit.
     */
    public fun processError(error: A2uiClientErrorMessage)

    /**
     * Suspends and continuously processes all incoming messages queued by [processMessage].
     *
     * This method acts as the engine's main processing loop. It should be launched within a
     * coroutine scope tied to the host application's lifecycle (such as a ViewModel). If this
     * method is not running, any messages passed to [processMessage] will remain queued.
     *
     * When the calling coroutine scope is cancelled, the processing loop will gracefully terminate.
     */
    public suspend fun collectMessages()
}

/**
 * Parses [input] using [parser] and enqueues the resulting message into this processor.
 *
 * If parsing fails with an [A2uiException] (such as [A2uiException.A2uiValidationException]), the
 * exception is caught, converted into an [A2uiClientErrorMessage], and sent to [processError].
 * Unexpected runtime exceptions are not caught.
 *
 * @param parser The parser to use for parsing the input.
 * @param input The raw input to parse and process.
 */
public fun <T> A2uiMessageProcessor.processInput(parser: A2uiMessageParser<T>, input: T) {
    try {
        val message = parser.parse(input)
        processMessage(message)
    } catch (e: A2uiException) {
        val surfaceId = e.context["surfaceId"] as? String ?: A2uiProtocolConstants.GLOBAL_SURFACE_ID
        val errorMessage =
            A2uiClientErrorMessage(
                code = e.code,
                surfaceId = surfaceId,
                message = e.message ?: "Failed to parse input message",
                context = e.context,
            )

        processError(errorMessage)
    }
}
