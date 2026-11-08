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

/**
 * Represents the client capabilities advertisement sent to the agent during system initialization.
 *
 * @property supportedCatalogIds identifiers of external or pre-shared catalogs supported by
 *   reference. This list must not include IDs of catalogs provided in [inlineCatalogs].
 * @property inlineCatalogs full JSON Schema definitions of catalogs advertised inline
 */
public class A2uiClientCapabilities
@JvmOverloads
constructor(
    public val supportedCatalogIds: List<String>,
    public val inlineCatalogs: List<A2uiInlineCatalog> = emptyList(),
) {
    /** Converts this capability object to the A2UI JSON-compatible Map structure. */
    public fun toPayloadMap(): Map<String, Map<String, Any?>> {
        val versionPayload =
            buildMap<String, Any?> {
                put("supportedCatalogIds", supportedCatalogIds)
                if (inlineCatalogs.isNotEmpty()) {
                    put("inlineCatalogs", inlineCatalogs.map { it.toJsonSchemaMap() })
                }
            }
        return mapOf(
            "a2uiClientCapabilities" to
                mapOf(A2uiProtocolConstants.PROTOCOL_VERSION to versionPayload)
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiClientCapabilities) return false
        return supportedCatalogIds == other.supportedCatalogIds &&
            inlineCatalogs == other.inlineCatalogs
    }

    override fun hashCode(): Int {
        var result = supportedCatalogIds.hashCode()
        result = 31 * result + inlineCatalogs.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiClientCapabilities(supportedCatalogIds=$supportedCatalogIds, inlineCatalogs=$inlineCatalogs)"
    }
}
