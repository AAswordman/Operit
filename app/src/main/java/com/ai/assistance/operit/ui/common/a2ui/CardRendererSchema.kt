package com.ai.assistance.operit.ui.common.a2ui

import android.annotation.SuppressLint
import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.schema.A2uiCoreSchemaValidator
import androidx.a2ui.model.protocol.A2uiException
import kotlinx.serialization.json.JsonObject

/**
 * Validate the same lowered component payloads that the official renderer receives.
 *
 * The A2UI engine only ships the core validator and restricts it to the androidx library group,
 * while the public A2uiCatalog exposes no validation entry point. Checking the payload against
 * anything else would accept components the official renderer then rejects, so the suppression
 * is deliberate and stays inside this object.
 */
@SuppressLint("RestrictedApi")
internal object CardRendererSchema {
    // The AndroidX Compose catalog factory implements both public catalog interfaces.
    private val catalog by lazy { operitA2uiCatalog as A2uiCoreCatalog }
    private val validator by lazy { A2uiCoreSchemaValidator(catalog) }

    class Failure(val component: String, val field: String) :
        IllegalArgumentException("Renderer schema mismatch: $component.$field")

    fun validate(graph: Map<String, JsonObject>) {
        CardRepeat.payloads(graph).forEach { payload ->
            val definition = requireNotNull(catalog.componentDefinitions[payload.type])
            try {
                validator.validateSchema(payload.properties, definition.propertySchema, "/component")
            } catch (e: A2uiException.A2uiValidationException) {
                // Never relay validator messages, which may contain model/user values.
                val field = (e.context["path"] as? String)?.removePrefix("/component/")
                    ?.substringBefore('/')?.takeIf { candidate ->
                        CardAuthoringCatalog.components[payload.type].orEmpty().any { it.name == candidate }
                    } ?: "properties"
                throw Failure(payload.type, field)
            }
        }
    }
}
