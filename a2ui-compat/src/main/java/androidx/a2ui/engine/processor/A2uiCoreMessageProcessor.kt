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

// ConcurrentHashMap is required for atomic compute() operations and is safe for our minSdk 24.
@file:Suppress("BanConcurrentHashMap")

package androidx.a2ui.engine.processor

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.catalog.toInlineCatalog
import androidx.a2ui.engine.model.A2uiCoreSurfaceGroupModel
import androidx.a2ui.engine.platform.A2uiCoreComponentRegistry
import androidx.a2ui.engine.platform.A2uiCoreDataModel
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.processor.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiClientCapabilities
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.a2ui.model.protocol.A2uiInlineCatalog
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The central orchestration engine for the A2UI core data layer.
 *
 * It receives parsed protocol messages from the host application, routes them to the appropriate
 * sequential [A2uiCoreSurfaceActor], and exposes the outbound network events back to the host
 * framework.
 *
 * @param catalogs The list of available component catalogs.
 * @param dataModelFactory Factory for creating new instances of [A2uiCoreDataModel].
 * @param componentRegistryFactory Factory for creating new instances of
 *   [A2uiCoreComponentRegistry].
 * @param actionInterceptors A list of [A2uiActionInterceptor] instances to intercept UI actions.
 */
public class A2uiCoreMessageProcessor(
    private val catalogs: List<A2uiCoreCatalog>,
    private val dataModelFactory: () -> A2uiCoreDataModel,
    private val componentRegistryFactory: () -> A2uiCoreComponentRegistry,
    private val actionInterceptors: List<A2uiActionInterceptor> = emptyList(),
) : A2uiMessageProcessor {
    private val surfaceGroup = A2uiCoreSurfaceGroupModel()

    /**
     * The global outbound stream of messages to be sent to the server.
     *
     * We use DROP_OLDEST to prevent OutOfMemoryErrors if the network layer completely stalls, under
     * the assumption that dropping the oldest UI actions/errors is preferable to freezing the
     * entire Android app.
     */
    private val _outboundEvents =
        MutableSharedFlow<A2uiClientToServerMessage>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * An unbounded channel acting as the main ingress queue.
     *
     * We assume the core processing layer (Kotlin Coroutines) will always route messages faster
     * than the LLM Agent's network arrival rate, making an unlimited buffer safe from memory bloat.
     */
    private val inboundQueue = Channel<A2uiEngineMessage>(Channel.UNLIMITED)

    /** Tracks active actors for safe lifecycle handoffs. */
    private val activeActors = ConcurrentHashMap<String, A2uiCoreSurfaceActor>()

    /**
     * The client capabilities supported by this processor instance, derived from the registered
     * catalogs. Host applications can query this directly during system initialization to send to
     * the agent.
     */
    // TODO(b/536820249): Consider combining the catalogs before advertising the capabilities
    public val clientCapabilities: A2uiClientCapabilities = run {
        val supportedCatalogIds = mutableListOf<String>()
        val inlineCatalogs = mutableListOf<A2uiInlineCatalog>()
        for (catalog in catalogs) {
            if (catalog.isInline) {
                inlineCatalogs.add(catalog.toInlineCatalog())
            } else {
                supportedCatalogIds.add(catalog.id)
            }
        }
        A2uiClientCapabilities(
            supportedCatalogIds = supportedCatalogIds,
            inlineCatalogs = inlineCatalogs,
        )
    }

    /** A flow of user actions and error payloads to be transmitted to the server. */
    override val outboundEvents: Flow<A2uiClientToServerMessage> = _outboundEvents.asSharedFlow()

    /** A flow of the list of active surface ids currently managed by the processor. */
    override val activeSurfaces: StateFlow<List<A2uiSurfaceModel>> = surfaceGroup.activeSurfaces

    /**
     * Enqueues a parsed protocol message to be processed by the core engine.
     *
     * This method is safe to call from any thread.
     */
    override fun processMessage(message: A2uiServerToClientMessage) {
        processInternalMessage(A2uiEngineExternalMessage(message))
    }

    override fun processError(error: A2uiClientErrorMessage) {
        if (surfaceGroup.getSurface(error.surfaceId) == null) {
            _outboundEvents.tryEmit(error)
        } else {
            processInternalMessage(A2uiEngineErrorMessage(error.surfaceId, error))
        }
    }

    /** Enqueues an internal engine message to be processed by the core engine. */
    internal fun processInternalMessage(message: A2uiEngineMessage) {
        inboundQueue.trySend(message)
    }

    /**
     * Continuously consumes the inbound message queue and routes messages to surface actors.
     *
     * This function must be launched within the host application's lifecycle scope (e.g., a
     * ViewModel scope). When the scope is cancelled, this loop and all child surface actors are
     * naturally terminated.
     */
    override suspend fun collectMessages() {
        try {
            // We use coroutineScope to act as the parent for all surface actor jobs
            coroutineScope {
                for (message in inboundQueue) {
                    routeMessage(message, this)
                }
            }
        } finally {
            // When the scope is cancelled or completes normally, we must honor the dispose
            // contract for all active surfaces to prevent framework-level memory leaks.
            // Due to structured concurrency, all child actors are guaranteed to be fully
            // terminated before this block executes, ensuring race-free disposal.
            val surfacesToDispose = surfaceGroup.clear()
            surfacesToDispose.forEach { surface -> surface.dispose() }
        }
    }

    private fun routeMessage(message: A2uiEngineMessage, scope: CoroutineScope) {
        val surfaceId = message.surfaceId
        var actorToLaunch: A2uiCoreSurfaceActor? = null

        // All routing MUST acquire the compute lock.
        // This guarantees mutual exclusion with the `removeActorIfIdle` teardown sequence,
        // preventing a race condition where we attempt to enqueue a message into an actor
        // that is simultaneously verifying it is empty and closing its channel.
        activeActors.compute(surfaceId) { _, existingActor ->
            if (existingActor != null && existingActor.enqueue(message)) {
                return@compute existingActor
            }

            // Actor is deleted or doesn't exist, create a new one
            val nextActor = createActor(surfaceId)
            nextActor.enqueue(message)
            actorToLaunch = nextActor
            nextActor
        }

        actorToLaunch?.let { actor -> scope.launch { actor.runProcessingLoop() } }
    }

    private fun createActor(surfaceId: String): A2uiCoreSurfaceActor {
        return A2uiCoreSurfaceActor(
            surfaceId = surfaceId,
            catalogs = catalogs,
            surfaceGroup = surfaceGroup,
            dataModelFactory = dataModelFactory,
            componentRegistryFactory = componentRegistryFactory,
            actionInterceptors = actionInterceptors,
            outboundEvents = _outboundEvents,
            onActorIdleAndDeleted = { actorToRemove ->
                removeActorIfIdle(surfaceId, actorToRemove)
            },
            onDispatchMessageToProcessor = ::processInternalMessage,
        )
    }

    private fun removeActorIfIdle(surfaceId: String, actorToRemove: A2uiCoreSurfaceActor): Boolean {
        var removed = false
        // Synchronize with `routeMessage` to ensure we don't delete an actor
        // that is simultaneously being handed a new message.
        activeActors.compute(surfaceId) { _, currentLockedActor ->
            if (currentLockedActor === actorToRemove && actorToRemove.isPendingMessagesEmpty) {
                // Atomically close the channel while we hold the map routing lock
                // No concurrent thread can enqueue a message now.
                actorToRemove.closeChannel()
                removed = true
                null // Removes the actor from the map
            } else {
                currentLockedActor
            }
        }
        return removed
    }
}
