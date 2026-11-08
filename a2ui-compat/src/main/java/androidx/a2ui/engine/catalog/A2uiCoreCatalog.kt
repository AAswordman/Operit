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

@file:JvmName("A2uiCoreCatalogKt")

package androidx.a2ui.engine.catalog

import androidx.a2ui.model.catalog.A2uiFunctionCollection
import androidx.a2ui.model.protocol.A2uiInlineCatalog
import androidx.a2ui.model.schema.A2uiSchema

/** A registry of UI components and functions that a client surface can render. */
public interface A2uiCoreCatalog {
    /**
     * A unique identifier for this catalog. Ideally, this is a URI including a version number. Full
     * guidelines regarding catalog id convention can be found in:
     * https://a2ui.org/concepts/catalogs/#catalog-naming-versioning
     */
    public val id: String

    /** The title of this catalog. */
    public val title: String?
        get() = null

    /** The description of this catalog. */
    public val description: String?
        get() = null

    /** The collection of component definitions available in this catalog. */
    public val componentDefinitions: A2uiCoreComponentDefinitionCollection

    /** The collection of functions available in this catalog. */
    public val functions: A2uiFunctionCollection

    /** The schema this catalog uses to define the theme the is applied over the components. */
    public val themeSchema: A2uiSchema?
        get() = null

    /**
     * Indicates whether this catalog's full JSON Schema should be serialized inline as part of the
     * capabilities advertisement.
     */
    public val isInline: Boolean
        get() = false
}

/**
 * Creates an [A2uiCoreCatalogSerializer] for this catalog.
 *
 * @return a new serializer instance configured for this catalog
 */
public fun A2uiCoreCatalog.obtainSerializer(): A2uiCoreCatalogSerializer =
    A2uiCoreCatalogSerializer(this)

/**
 * Adapts this [A2uiCoreCatalog] into an [A2uiInlineCatalog] for use in client capabilities
 * advertisement.
 *
 * @throws IllegalStateException If [isInline] is false.
 */
public fun A2uiCoreCatalog.toInlineCatalog(): A2uiInlineCatalog {
    check(isInline) {
        "Cannot adapt A2uiCoreCatalog(id='$id') to A2uiInlineCatalog because isInline is false."
    }
    return this as? A2uiInlineCatalog ?: A2uiCoreCatalogInlineAdapter(this)
}
