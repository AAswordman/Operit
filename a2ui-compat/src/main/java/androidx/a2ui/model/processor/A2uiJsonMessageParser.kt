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

package androidx.a2ui.model.processor

import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiDeleteSurfaceMessage
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import androidx.a2ui.model.protocol.A2uiUpdateComponentsMessage
import androidx.a2ui.model.protocol.A2uiUpdateDataModelMessage

/**
 * Parses JSON strings into [A2uiServerToClientMessage] protocol messages.
 *
 * Uses streaming [A2uiJsonReader]s to process inputs memory-efficiently.
 *
 * @param jsonReaderProvider provides an [A2uiJsonReader] for a given JSON string
 */
public class A2uiJsonMessageParser(private val jsonReaderProvider: (String) -> A2uiJsonReader) :
    A2uiMessageParser<String> {

    override fun parse(input: String): A2uiServerToClientMessage {
        return try {
            // Parse the JSON twice: first to extract and validate the protocol version,
            // ensuring it is supported before we try to parse specific protocol fields.
            val version = detectVersion(jsonReaderProvider(input))
            validateVersion(version)
            jsonReaderProvider(input).use { reader -> parseMessage(reader) }
        } catch (e: A2uiException) {
            throw e
        } catch (e: Exception) {
            throw A2uiException.A2uiValidationException("Malformed JSON message: ${e.message}", "/")
        }
    }

    private fun detectVersion(reader: A2uiJsonReader): String {
        return reader.use {
            it.beginObject()
            var version: String? = null
            while (it.hasNext()) {
                when (it.nextName()) {
                    FIELD_VERSION -> {
                        version = it.nextStringSafe("/$FIELD_VERSION")
                        break
                    }
                    else -> it.skipValue()
                }
            }
            version
                ?: throw A2uiException.A2uiValidationException(
                    "Missing or empty '$FIELD_VERSION' in message envelope",
                    "/$FIELD_VERSION",
                )
        }
    }

    private fun validateVersion(version: String) {
        if (!SUPPORTED_VERSIONS.contains(version)) {
            throw A2uiException.A2uiValidationException(
                "Unsupported protocol version: $version. expected: $SUPPORTED_VERSIONS",
                "/$FIELD_VERSION",
            )
        }
    }

    private fun parseMessage(reader: A2uiJsonReader): A2uiServerToClientMessage {
        var message: A2uiServerToClientMessage? = null
        reader.beginObject()

        while (reader.hasNext()) {
            val parsed =
                when (reader.nextName()) {
                    FIELD_VERSION -> {
                        val v = reader.nextStringSafe("/$FIELD_VERSION")
                        validateVersion(v)
                        null
                    }
                    FIELD_CREATE_SURFACE -> parseCreateSurface(reader)
                    FIELD_UPDATE_COMPONENTS -> parseUpdateComponents(reader)
                    FIELD_UPDATE_DATA_MODEL -> parseUpdateDataModel(reader)
                    FIELD_DELETE_SURFACE -> parseDeleteSurface(reader)
                    else -> {
                        reader.skipValue()
                        null
                    }
                }
            if (parsed != null) {
                if (message != null) {
                    throw A2uiException.A2uiValidationException(
                        "Multiple message envelopes found in a single JSON payload",
                        "/",
                    )
                }
                message = parsed
            }
        }
        reader.endObject()
        return message
            ?: throw A2uiException.A2uiValidationException(
                "Empty or invalid A2UI message envelope",
                "/",
            )
    }

    private fun parseCreateSurface(reader: A2uiJsonReader): A2uiCreateSurfaceMessage {
        var surfaceId: String? = null
        var catalogId: String? = null
        var theme: Map<String, Any?>? = null
        var sendDataModel = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                FIELD_SURFACE_ID ->
                    surfaceId = reader.nextStringSafe("/$FIELD_CREATE_SURFACE/$FIELD_SURFACE_ID")
                FIELD_CATALOG_ID ->
                    catalogId = reader.nextStringSafe("/$FIELD_CREATE_SURFACE/$FIELD_CATALOG_ID")
                FIELD_THEME -> theme = reader.nextMapSafe("/$FIELD_CREATE_SURFACE/$FIELD_THEME")
                FIELD_SEND_DATA_MODEL ->
                    sendDataModel =
                        reader.nextBooleanSafe("/$FIELD_CREATE_SURFACE/$FIELD_SEND_DATA_MODEL")
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (surfaceId.isNullOrEmpty()) {
            throw A2uiException.A2uiValidationException(
                "Missing or empty '$FIELD_SURFACE_ID' in $FIELD_CREATE_SURFACE message",
                "/$FIELD_CREATE_SURFACE/$FIELD_SURFACE_ID",
            )
        }
        if (catalogId.isNullOrEmpty()) {
            throw A2uiException.A2uiValidationException(
                "Missing or empty '$FIELD_CATALOG_ID' in $FIELD_CREATE_SURFACE message",
                "/$FIELD_CREATE_SURFACE/$FIELD_CATALOG_ID",
            )
        }

        return A2uiCreateSurfaceMessage(surfaceId, catalogId, theme ?: emptyMap(), sendDataModel)
    }

    private fun parseUpdateComponents(reader: A2uiJsonReader): A2uiUpdateComponentsMessage {
        var surfaceId: String? = null
        val components = mutableListOf<A2uiComponentPayload>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                FIELD_SURFACE_ID ->
                    surfaceId = reader.nextStringSafe("/$FIELD_UPDATE_COMPONENTS/$FIELD_SURFACE_ID")
                FIELD_COMPONENTS -> {
                    reader.beginArray()
                    var componentIndex = 0
                    while (reader.hasNext()) {
                        var id: String? = null
                        var component: String? = null
                        val properties = mutableMapOf<String, Any?>()

                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (val name = reader.nextName()) {
                                FIELD_ID ->
                                    id =
                                        reader.nextStringSafe(
                                            "/$FIELD_UPDATE_COMPONENTS/$FIELD_COMPONENTS/$componentIndex/$FIELD_ID"
                                        )
                                FIELD_COMPONENT ->
                                    component =
                                        reader.nextStringSafe(
                                            "/$FIELD_UPDATE_COMPONENTS/$FIELD_COMPONENTS/$componentIndex/$FIELD_COMPONENT"
                                        )
                                else -> {
                                    properties[name] = reader.nextValue()
                                }
                            }
                        }
                        reader.endObject()

                        if (id.isNullOrEmpty()) {
                            throw A2uiException.A2uiValidationException(
                                "Missing or empty '$FIELD_ID' in component payload",
                                "/$FIELD_UPDATE_COMPONENTS/$FIELD_COMPONENTS/$componentIndex/$FIELD_ID",
                            )
                        }
                        if (component.isNullOrEmpty()) {
                            throw A2uiException.A2uiValidationException(
                                "Missing or empty '$FIELD_COMPONENT' in component payload",
                                "/$FIELD_UPDATE_COMPONENTS/$FIELD_COMPONENTS/$componentIndex/$FIELD_COMPONENT",
                            )
                        }

                        components.add(
                            A2uiComponentPayload(id = id, type = component, properties = properties)
                        )
                        componentIndex++
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (surfaceId.isNullOrEmpty()) {
            throw A2uiException.A2uiValidationException(
                "Missing or empty '$FIELD_SURFACE_ID' in $FIELD_UPDATE_COMPONENTS message",
                "/$FIELD_UPDATE_COMPONENTS/$FIELD_SURFACE_ID",
            )
        }

        return A2uiUpdateComponentsMessage(surfaceId, components)
    }

    private fun parseUpdateDataModel(reader: A2uiJsonReader): A2uiUpdateDataModelMessage {
        var surfaceId: String? = null
        var path = "/"
        var value: Any? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                FIELD_SURFACE_ID ->
                    surfaceId = reader.nextStringSafe("/$FIELD_UPDATE_DATA_MODEL/$FIELD_SURFACE_ID")
                FIELD_PATH -> path = reader.nextStringSafe("/$FIELD_UPDATE_DATA_MODEL/$FIELD_PATH")
                FIELD_VALUE -> value = reader.nextValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (surfaceId.isNullOrEmpty()) {
            throw A2uiException.A2uiValidationException(
                "Missing or empty '$FIELD_SURFACE_ID' in $FIELD_UPDATE_DATA_MODEL message",
                "/$FIELD_UPDATE_DATA_MODEL/$FIELD_SURFACE_ID",
            )
        }

        return A2uiUpdateDataModelMessage(surfaceId, path, value)
    }

    private fun parseDeleteSurface(reader: A2uiJsonReader): A2uiDeleteSurfaceMessage {
        var surfaceId: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                FIELD_SURFACE_ID ->
                    surfaceId = reader.nextStringSafe("/$FIELD_DELETE_SURFACE/$FIELD_SURFACE_ID")
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (surfaceId.isNullOrEmpty()) {
            throw A2uiException.A2uiValidationException(
                "Missing or empty '$FIELD_SURFACE_ID' in $FIELD_DELETE_SURFACE message",
                "/$FIELD_DELETE_SURFACE/$FIELD_SURFACE_ID",
            )
        }

        return A2uiDeleteSurfaceMessage(surfaceId)
    }

    /**
     * Reads a map or throws [A2uiException.A2uiValidationException] if the next token is not an
     * object.
     */
    private fun A2uiJsonReader.nextMapSafe(path: String): Map<String, Any?> {
        if (peek() != A2uiJsonToken.BEGIN_OBJECT) {
            throw A2uiException.A2uiValidationException(
                "Expected a JSON object but was ${peek()}",
                path,
            )
        }
        return nextMap()
    }

    /**
     * Reads a string, or throws [A2uiException.A2uiValidationException] if the next token is not a
     * string.
     */
    private fun A2uiJsonReader.nextStringSafe(path: String): String {
        val token = peek()
        if (token != A2uiJsonToken.STRING) {
            throw A2uiException.A2uiValidationException("Expected a string but was $token", path)
        }
        return nextString()
    }

    /**
     * Reads a boolean or throws [A2uiException.A2uiValidationException] if the next token is not a
     * boolean.
     */
    private fun A2uiJsonReader.nextBooleanSafe(path: String): Boolean {
        val token = peek()
        if (token != A2uiJsonToken.BOOLEAN) {
            throw A2uiException.A2uiValidationException("Expected a boolean but was $token", path)
        }
        return nextBoolean()
    }

    /** Reads a map directly, expecting the next token to be [A2uiJsonToken.BEGIN_OBJECT]. */
    private fun A2uiJsonReader.nextMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        beginObject()
        while (hasNext()) {
            map[nextName()] = this.nextValue()
        }
        endObject()
        return map
    }

    private fun A2uiJsonReader.nextList(): List<Any?> {
        val list = mutableListOf<Any?>()
        beginArray()
        while (hasNext()) {
            list.add(this.nextValue())
        }
        endArray()
        return list
    }

    private fun A2uiJsonReader.nextNumber(): Any {
        val str = nextString()
        val hasDotOrExponent = str.any { (it == '.') || (it == 'e') || (it == 'E') }
        return if (hasDotOrExponent) {
            str.toDoubleOrNull() ?: str
        } else {
            str.toIntOrNull() ?: str.toLongOrNull() ?: str
        }
    }

    private fun A2uiJsonReader.nextValue(): Any? {
        val token = peek()
        return when (token) {
            A2uiJsonToken.BEGIN_OBJECT -> nextMap()
            A2uiJsonToken.BEGIN_ARRAY -> nextList()
            A2uiJsonToken.STRING -> nextString()
            A2uiJsonToken.NUMBER -> nextNumber()
            A2uiJsonToken.BOOLEAN -> nextBoolean()
            A2uiJsonToken.NULL -> {
                nextNull()
                null
            }
            else -> {
                skipValue()
                null
            }
        }
    }

    private companion object {
        private const val FIELD_VERSION = "version"
        private const val FIELD_CREATE_SURFACE = "createSurface"
        private const val FIELD_UPDATE_COMPONENTS = "updateComponents"
        private const val FIELD_UPDATE_DATA_MODEL = "updateDataModel"
        private const val FIELD_DELETE_SURFACE = "deleteSurface"
        private const val FIELD_SURFACE_ID = "surfaceId"
        private const val FIELD_CATALOG_ID = "catalogId"
        private const val FIELD_THEME = "theme"
        private const val FIELD_SEND_DATA_MODEL = "sendDataModel"
        private const val FIELD_COMPONENTS = "components"
        private const val FIELD_ID = "id"
        private const val FIELD_COMPONENT = "component"
        private const val FIELD_PATH = "path"
        private const val FIELD_VALUE = "value"

        private val SUPPORTED_VERSIONS = listOf("v0.9", "v0.9.1")
    }
}
