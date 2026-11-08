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

package androidx.a2ui.engine.catalog

import androidx.a2ui.model.protocol.A2uiInlineCatalog

internal class A2uiCoreCatalogInlineAdapter(private val catalog: A2uiCoreCatalog) :
    A2uiInlineCatalog {
    private val serializer: A2uiCoreCatalogSerializer by
        lazy(LazyThreadSafetyMode.PUBLICATION) { catalog.obtainSerializer() }

    override val id: String
        get() = catalog.id

    override fun toJsonSchemaMap(): Map<String, Any?> = serializer.jsonSchemaMap

    override fun toJsonSchemaString(): String = serializer.jsonSchemaString

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiCoreCatalogInlineAdapter) return false
        return catalog == other.catalog
    }

    override fun hashCode(): Int = catalog.hashCode()

    override fun toString(): String = "A2uiInlineCatalog(id=$id)"
}
