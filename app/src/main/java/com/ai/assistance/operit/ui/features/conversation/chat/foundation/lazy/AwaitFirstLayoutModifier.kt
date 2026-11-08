/*
 * Copyright 2023 The Android Open Source Project
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

package com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy

import com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy.AwaitFirstLayoutModifier.Node
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.registerOnLayoutRectChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.CompletableDeferred

/**
 * Internal modifier which allows to delay some interactions (e.g. scroll) until layout is ready.
 */
internal class AwaitFirstLayoutModifier : ModifierNodeElement<Node>() {
    private var attachedNode: Node? = null
    private var lock: CompletableDeferred<Unit>? = null

    suspend fun waitForFirstLayout() {
        val lock =
            lock
                ?: CompletableDeferred<Unit>().also {
                    this.lock = it
                    val node = attachedNode
                    if (node != null && node.isAttached) {
                        node.requestOnAfterLayoutCallback()
                    }
                }
        lock.await()
    }

    override fun create() = Node()

    override fun update(node: Node) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "AwaitFirstLayoutModifier"
    }

    override fun hashCode(): Int = 234

    override fun equals(other: Any?): Boolean = other === this

    inner class Node : Modifier.Node() {
        override fun onAttach() {
            attachedNode = this
            if (lock != null) {
                requestOnAfterLayoutCallback()
            }
        }

        private var handle: DelegatableNode.RegistrationHandle? = null

        fun requestOnAfterLayoutCallback() {
            handle =
                registerOnLayoutRectChanged(0, 0) {
                    handle?.unregister()
                    handle = null
                    lock?.complete(Unit)
                    lock = null
                }
        }

        override fun onDetach() {
            if (attachedNode === this) {
                attachedNode = null
            }
            handle?.unregister()
            handle = null
        }
    }
}
