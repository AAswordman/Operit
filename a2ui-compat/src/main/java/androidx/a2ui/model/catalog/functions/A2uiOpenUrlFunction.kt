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

package androidx.a2ui.model.catalog.functions

import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.catalog.A2uiFunctionDefinition
import androidx.a2ui.model.catalog.A2uiFunctionReturnType
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiStringSchema

/**
 * Interface to open a URL.
 *
 * The implementation of this interface should be provided by the platform (e.g., using Android's
 * Intent.ACTION_VIEW).
 */
public fun interface A2uiUrlOpener {
    /**
     * Opens the specified URL.
     *
     * @param url the URL string to open
     */
    public fun openUrl(url: String)
}

/**
 * Opens a URL in a browser or handler.
 *
 * Use this [A2uiFunction] to direct the user to an external link.
 */
public class A2uiOpenUrlFunction(private val urlOpener: A2uiUrlOpener) : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "openUrl"

            override val description: String =
                """Opens the specified URL in a browser or handler. This function has no return value."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.VOID

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            ARG_URL_KEY to A2uiStringSchema(description = """The URL to open.""")
                        ),
                    required = setOf(ARG_URL_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Hands over the URL to open in [args] to the platform-specific URL opener.
     *
     * @param args arguments containing the "url" string to open
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return null as this operation does not produce a return value
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val url = A2uiFunctionArgParser.getStringArg(args, ARG_URL_KEY)
        if (url.isEmpty()) {
            throw A2uiException.A2uiRuntimeException(
                "Function ${definition.name} was invoked with an empty URL"
            )
        }
        urlOpener.openUrl(url)
        return Unit
    }

    public companion object {
        private const val ARG_URL_KEY: String = "url"
    }
}
