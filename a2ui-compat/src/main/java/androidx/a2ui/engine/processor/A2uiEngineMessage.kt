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

import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import androidx.a2ui.model.protocol.A2uiUserAction

/**
 * The unified message type used internally by the [A2uiCoreSurfaceActor]'s sequential queue. This
 * encapsulates both external messages received from the server and internal events generated
 * locally.
 */
internal sealed interface A2uiEngineMessage {
    val surfaceId: String
}

/** Wrapper for public external messages received from the server. */
internal data class A2uiEngineExternalMessage(val message: A2uiServerToClientMessage) :
    A2uiEngineMessage {
    override val surfaceId: String
        get() = message.surfaceId
}

/**
 * An internal message used to route user actions sequentially through the [A2uiCoreSurfaceActor]
 * queue.
 */
internal data class A2uiEngineActionMessage(
    override val surfaceId: String,
    val action: A2uiUserAction,
) : A2uiEngineMessage

/**
 * An internal message used to route client errors sequentially through the [A2uiCoreSurfaceActor]
 * queue so they can be emitted to the server via a suspending call from within the actor coroutine.
 */
internal data class A2uiEngineErrorMessage(
    override val surfaceId: String,
    val error: A2uiClientErrorMessage,
) : A2uiEngineMessage
