package com.ai.assistance.operit.core.tools.packTool.metadata

import com.ai.assistance.operit.core.tools.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ToolPkgManifestWorkflowTemplate(
    val id: String,
    @SerialName("display_name") val displayName: LocalizedText = LocalizedText.of(""),
    val description: LocalizedText = LocalizedText.of(""),
    @SerialName("resource_key") val resourceKey: String
)

@Serializable
internal data class ToolPkgManifestWorkspaceTemplate(
    val id: String,
    @SerialName("display_name") val displayName: LocalizedText = LocalizedText.of(""),
    val description: LocalizedText = LocalizedText.of(""),
    @SerialName("resource_key") val resourceKey: String,
    @SerialName("project_type") val projectType: String = ""
)

internal data class ToolPkgWorkflowTemplateRuntime(
    val id: String,
    val displayName: LocalizedText,
    val description: LocalizedText,
    val resourceKey: String
)

internal data class ToolPkgWorkspaceTemplateRuntime(
    val id: String,
    val displayName: LocalizedText,
    val description: LocalizedText,
    val resourceKey: String,
    val projectType: String
)
