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

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.model.A2uiCoreDynamicEvaluatorImpl
import androidx.a2ui.engine.model.A2uiCoreExecutionContext
import androidx.a2ui.engine.model.A2uiCoreSurfaceGroupModel
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.engine.platform.A2uiCoreComponentRegistry
import androidx.a2ui.engine.platform.A2uiCoreDataModel
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiDeleteSurfaceMessage
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiUpdateComponentsMessage
import androidx.a2ui.model.protocol.A2uiUpdateDataModelMessage
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A sequential actor that processes incoming server messages and local user actions for a single
 * active surface.
 *
 * This class ensures that all state mutations and outbound events for a given [surfaceId] are
 * processed strictly in the order they were received. It acts as a concurrency boundary, protecting
 * the underlying [A2uiCoreSurfaceModel] from concurrent modification.
 *
 * @param surfaceId The unique identifier of the surface this actor manages.
 * @param catalogs The list of available component catalogs.
 * @param surfaceGroup The group model managing all active surfaces.
 * @param dataModelFactory Factory for creating new instances of [A2uiCoreDataModel].
 * @param componentRegistryFactory Factory for creating new instances of
 *   [A2uiCoreComponentRegistry].
 * @param actionInterceptors A list of [A2uiActionInterceptor] instances to intercept UI actions.
 * @param outboundEvents A flow of user actions and error payloads to be transmitted to the server.
 * @param onActorIdleAndDeleted Callback invoked when the actor is idle and marked for deletion.
 * @param onDispatchMessageToProcessor Callback invoked to dispatch an internal message back to the
 *   processor.
 */
internal class A2uiCoreSurfaceActor(
    internal val surfaceId: String,
    private val catalogs: List<A2uiCoreCatalog>,
    private val surfaceGroup: A2uiCoreSurfaceGroupModel,
    private val dataModelFactory: () -> A2uiCoreDataModel,
    private val componentRegistryFactory: () -> A2uiCoreComponentRegistry,
    private val actionInterceptors: List<A2uiActionInterceptor>,
    private val outboundEvents: MutableSharedFlow<A2uiClientToServerMessage>,
    private val onActorIdleAndDeleted: (A2uiCoreSurfaceActor) -> Boolean,
    private val onDispatchMessageToProcessor: (A2uiEngineMessage) -> Unit,
) {
    private val actionHandler =
        A2uiActionHandler(
            actionInterceptors = actionInterceptors,
            clientDataModelProvider = { surfaceGroup.getClientDataModel() },
            emitToServer = { handleOutboundEvent(it) },
        )

    private val channel = Channel<A2uiEngineMessage>(Channel.UNLIMITED)

    /** Confined to the actor's coroutine loop. Requires no synchronization. */
    private var isSurfaceCreated = false

    private val pendingMessageCount = AtomicInteger(0)

    /** True if there are zero messages currently in the channel or being actively processed. */
    internal val isPendingMessagesEmpty: Boolean
        get() = pendingMessageCount.get() == 0

    /** Permanently closes the channel, preventing any further messages from being enqueued. */
    internal fun closeChannel() {
        channel.close()
    }

    /**
     * Enqueues an incoming server update, local user action, or local error into the actor's
     * channel.
     *
     * @return `true` if successfully enqueued, `false` if the actor's channel is already closed.
     */
    internal fun enqueue(message: A2uiEngineMessage): Boolean {
        pendingMessageCount.incrementAndGet()
        val success = channel.trySend(message).isSuccess
        if (!success) {
            pendingMessageCount.decrementAndGet() // Rollback if channel was closed
        }
        return success
    }

    /**
     * Continuously consumes the queue and processes messages sequentially.
     *
     * This function suspends indefinitely while waiting for messages. If the surface is deleted, it
     * processes all remaining buffered messages in the channel without suspending before triggering
     * a safe teardown.
     */
    internal suspend fun runProcessingLoop() {
        while (true) {
            if (!isSurfaceCreated) {
                // Try to process a buffered message without suspending.
                // The continue statement ensures we keep looping until the channel is empty.
                val msg = channel.tryReceive().getOrNull()
                if (msg != null) {
                    processMessage(msg)
                    continue
                }

                // The channel seems empty. Ask the processor to lock and check.
                if (onActorIdleAndDeleted(this)) {
                    break // We are cleanly and safely terminated.
                }
            }

            // Suspend and wait for the next message
            val nextMsg = channel.receiveCatching().getOrNull() ?: break
            processMessage(nextMsg)
        }
    }

    private suspend fun processMessage(message: A2uiEngineMessage) {
        require(message.surfaceId == surfaceId) {
            "Mismatched surface ID routing. Actor expects '$surfaceId' but received '${message.surfaceId}'"
        }
        try {
            handleMessage(message)
        } catch (e: A2uiException) {
            // We only catch A2uiExceptions (e.g. ValidationFailed, intentional LLM RuntimeErrors).
            // Unexpected JVM exceptions (like NPEs) are considered SDK bugs and should crash the
            // app.
            handleA2uiException(e)
        } finally {
            pendingMessageCount.decrementAndGet()
        }
    }

    private suspend fun handleMessage(message: A2uiEngineMessage) {
        when (message) {
            is A2uiEngineExternalMessage -> {
                when (val externalMsg = message.message) {
                    is A2uiCreateSurfaceMessage -> {
                        handleCreateSurface(externalMsg)
                        isSurfaceCreated = true
                    }
                    is A2uiUpdateComponentsMessage -> handleUpdateComponents(externalMsg)
                    is A2uiUpdateDataModelMessage -> handleUpdateDataModel(externalMsg)
                    is A2uiDeleteSurfaceMessage -> {
                        isSurfaceCreated = false
                        handleDeleteSurface(externalMsg)
                    }
                }
            }
            is A2uiEngineActionMessage -> {
                // Ghost taps on deleted surfaces are safely ignored.
                if (isSurfaceCreated) handleAction(message)
            }
            is A2uiEngineErrorMessage -> {
                // Errors must always be handled, even if the surface is tearing down.
                handleOutboundEvent(message.error)
            }
        }
    }

    private fun handleCreateSurface(message: A2uiCreateSurfaceMessage) {
        if (surfaceGroup.getSurface(surfaceId) != null) {
            throw A2uiException.A2uiRuntimeException(
                "Surface '${message.surfaceId}' already exists."
            )
        }
        val catalog =
            catalogs.find { it.id == message.catalogId }
                ?: throw A2uiException.A2uiRuntimeException(
                    "Catalog with ID '${message.catalogId}' not found."
                )
        val surface =
            A2uiCoreSurfaceModel(
                id = message.surfaceId,
                catalog = catalog,
                dataModel = dataModelFactory(),
                componentRegistry = componentRegistryFactory(),
                onDispatchAction = { action ->
                    onDispatchMessageToProcessor(A2uiEngineActionMessage(message.surfaceId, action))
                },
                onDispatchError = { error ->
                    onDispatchMessageToProcessor(A2uiEngineErrorMessage(message.surfaceId, error))
                },
                theme = message.theme,
                shouldSendDataModel = message.shouldSendDataModel,
            )
        surfaceGroup.add(surface)
    }

    private fun handleUpdateComponents(message: A2uiUpdateComponentsMessage) {
        val surface =
            surfaceGroup.getSurface(message.surfaceId)
                ?: throw A2uiException.A2uiRuntimeException(
                    "Surface '${message.surfaceId}' not found."
                )
        surface.updateComponents(message.components)
    }

    private fun handleUpdateDataModel(message: A2uiUpdateDataModelMessage) {
        val surface =
            surfaceGroup.getSurface(message.surfaceId)
                ?: throw A2uiException.A2uiRuntimeException(
                    "Surface '${message.surfaceId}' not found."
                )
        surface.updateDataModel(message.path, message.value)
    }

    private fun handleDeleteSurface(message: A2uiDeleteSurfaceMessage) {
        surfaceGroup.delete(message.surfaceId)
    }

    private suspend fun handleAction(message: A2uiEngineActionMessage) {
        val surface = surfaceGroup.getSurface(surfaceId) ?: return
        val executionContext =
            A2uiCoreExecutionContext(
                dataPath = A2uiDataPath("/"),
                componentId = message.action.componentId,
                catalog = surface.catalog,
                dispatchError = { ex, cId -> surface.dispatchError(ex, cId) },
                valueResolver = { path -> surface.dataModel[path] },
                dynamicEvaluator = A2uiCoreDynamicEvaluatorImpl,
                cacheProvider = surface,
            )
        actionHandler.handleAction(message.action, executionContext)
    }

    private fun handleA2uiException(exception: A2uiException) {
        val error =
            A2uiClientErrorMessage(
                code = exception.code,
                surfaceId = surfaceId,
                message = exception.message ?: "",
                context = exception.context,
            )
        handleOutboundEvent(error)
    }

    private fun handleOutboundEvent(outboundEvent: A2uiClientToServerMessage) {
        outboundEvents.tryEmit(outboundEvent)
    }
}
