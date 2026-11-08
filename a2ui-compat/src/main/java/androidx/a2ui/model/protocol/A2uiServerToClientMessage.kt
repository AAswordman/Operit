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

package androidx.a2ui.model.protocol

/** The unified interface for all messages sent from the A2A server to the A2UI client. */
public sealed interface A2uiServerToClientMessage {
    /** The unique identifier of the surface this message targets. */
    public val surfaceId: String
}

/**
 * Creates a new surface instance on the client.
 *
 * @property surfaceId The unique ID to assign to the new surface.
 * @property catalogId The ID of the component catalog to use for this surface.
 * @property theme An optional map of theme-specific property overrides (e.g., colors, spacing).
 * @property shouldSendDataModel If true, the client will append its active UI data model state tree
 *   as metadata to subsequent outgoing messages. Defaults to false.
 */
public class A2uiCreateSurfaceMessage(
    public override val surfaceId: String,
    public val catalogId: String,
    public val theme: Map<String, Any?> = emptyMap(),
    @get:JvmName("shouldSendDataModel") public val shouldSendDataModel: Boolean = false,
) : A2uiServerToClientMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiCreateSurfaceMessage) return false
        return (surfaceId == other.surfaceId) &&
            (catalogId == other.catalogId) &&
            (theme == other.theme) &&
            (shouldSendDataModel == other.shouldSendDataModel)
    }

    override fun hashCode(): Int {
        var result = surfaceId.hashCode()
        result = (31 * result) + catalogId.hashCode()
        result = (31 * result) + theme.hashCode()
        result = (31 * result) + shouldSendDataModel.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiCreateSurfaceMessage(surfaceId=$surfaceId, catalogId=$catalogId, theme=$theme, " +
            "shouldSendDataModel=$shouldSendDataModel)"
    }
}

/**
 * Updates or replaces components in the surface's registry, which can include modifying static
 * component properties (e.g., text, colors) or changing structural layout templates.
 *
 * @property surfaceId The ID of the surface to update.
 * @property components A list of component payloads to insert or replace in the registry.
 */
public class A2uiUpdateComponentsMessage(
    public override val surfaceId: String,
    public val components: List<A2uiComponentPayload>,
) : A2uiServerToClientMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiUpdateComponentsMessage) return false
        return (surfaceId == other.surfaceId) && (components == other.components)
    }

    override fun hashCode(): Int {
        var result = surfaceId.hashCode()
        result = (31 * result) + components.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiUpdateComponentsMessage(surfaceId=$surfaceId, components=$components)"
    }
}

/**
 * Updates the data model of a surface.
 *
 * @property surfaceId The ID of the surface to update.
 * @property path The absolute JSON pointer path to the data point to update. Defaults to root ("/")
 *   representing the root of the data model.
 * @property value The new value to store. If null (or omitted in JSON), the key/path is deleted
 *   from the data model.
 */
public class A2uiUpdateDataModelMessage(
    public override val surfaceId: String,
    public val path: String = "/",
    public val value: Any? = null,
) : A2uiServerToClientMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiUpdateDataModelMessage) return false
        return (surfaceId == other.surfaceId) && (path == other.path) && (value == other.value)
    }

    override fun hashCode(): Int {
        var result = surfaceId.hashCode()
        result = (31 * result) + path.hashCode()
        result = (31 * result) + (value?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "A2uiUpdateDataModelMessage(surfaceId=$surfaceId, path=$path, value=$value)"
    }
}

/**
 * Destroys an existing surface and frees its associated resources.
 *
 * @property surfaceId The ID of the surface to delete.
 */
public class A2uiDeleteSurfaceMessage(public override val surfaceId: String) :
    A2uiServerToClientMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiDeleteSurfaceMessage) return false
        return (surfaceId == other.surfaceId)
    }

    override fun hashCode(): Int {
        return surfaceId.hashCode()
    }

    override fun toString(): String {
        return "A2uiDeleteSurfaceMessage(surfaceId=$surfaceId)"
    }
}
