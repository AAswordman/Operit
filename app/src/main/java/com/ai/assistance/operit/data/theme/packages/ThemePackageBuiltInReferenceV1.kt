package com.ai.assistance.operit.data.theme.packages

/**
 * The built-in baseline theme shipped with the app. It is not an archive: its manifest lives
 * in code and users customize it through the two declared parameters, which is the new-
 * protocol replacement for the retired free-form theme settings.
 */
internal object ThemePackageBuiltInReferenceV1 {
    const val PACKAGE_ID = "operit.reference"
    const val VERSION = "1.0.0"

    const val PARAM_PRIMARY_COLOR = "primary_color"
    const val PARAM_BACKGROUND_IMAGE = "background_image"

    fun manifest(): ThemePackageManifestV1 =
        ThemePackageManifestV1(
            schemaVersion = THEME_PACKAGE_SCHEMA_VERSION,
            packageId = PACKAGE_ID,
            version = VERSION,
            displayName =
                ThemePackageLocalizedTextV1(
                    values =
                        mapOf(
                            "*" to "Operit 基线",
                            "en" to "Operit Baseline",
                        ),
                ),
            author =
                ThemePackageLocalizedTextV1(
                    values = mapOf("*" to "Operit"),
                ),
            capabilities =
                ThemePackageCapabilitiesV1(
                    hostSurfaces = setOf("main"),
                    scenes = setOf("chat.main"),
                ),
            parameters =
                listOf(
                    ThemeParameterDefinitionV1(
                        id = PARAM_PRIMARY_COLOR,
                        type = ThemeParameterTypeV1.COLOR,
                        label =
                            ThemePackageLocalizedTextV1(
                                values =
                                    mapOf(
                                        "*" to "主色",
                                        "en" to "Primary color",
                                    ),
                            ),
                    ),
                    ThemeParameterDefinitionV1(
                        id = PARAM_BACKGROUND_IMAGE,
                        type = ThemeParameterTypeV1.STRING,
                        label =
                            ThemePackageLocalizedTextV1(
                                values =
                                    mapOf(
                                        "*" to "背景图片",
                                        "en" to "Background image",
                                    ),
                            ),
                    ),
                ),
        )
}

/** Globally consumed theme parameter values resolved from the active [ThemeInstanceV1]. */
internal data class ActiveGlobalThemeParametersV1(
    val primaryColorArgb: Long?,
    val backgroundImageUri: String?,
)

internal object ActiveGlobalThemeParameterResolverV1 {
    /**
     * Any package may declare the two globally consumed parameters; values are validated
     * against the declaring manifest's parameter definitions and fail fast on type drift.
     */
    fun resolve(
        instance: ThemeInstanceV1,
        manifestOf: (ThemePackageReferenceV1) -> ThemePackageManifestV1?,
    ): ActiveGlobalThemeParametersV1 {
        val manifest =
            manifestOf(instance.reference)
                ?: error("Active theme manifest is unavailable: ${instance.reference}")
        return ActiveGlobalThemeParametersV1(
            primaryColorArgb = colorParameterValue(instance, manifest, ThemePackageBuiltInReferenceV1.PARAM_PRIMARY_COLOR),
            backgroundImageUri = stringParameterValue(instance, manifest, ThemePackageBuiltInReferenceV1.PARAM_BACKGROUND_IMAGE),
        )
    }

    private fun colorParameterValue(
        instance: ThemeInstanceV1,
        manifest: ThemePackageManifestV1,
        parameterId: String,
    ): Long? {
        val definition = manifest.parameterDefinition(parameterId) ?: return null
        require(definition.type == ThemeParameterTypeV1.COLOR) {
            "Theme parameter $parameterId must be declared as COLOR."
        }
        instance.parameterValues[parameterId]?.let { value ->
            return (value as? ThemeParameterValueV1.IntegerValue ?: error(
                "Theme parameter $parameterId must be stored as an integer ARGB value.",
            )).value
        }
        return (definition.defaultValue as? ThemeParameterDefaultV1.ColorValue)?.argb
    }

    private fun stringParameterValue(
        instance: ThemeInstanceV1,
        manifest: ThemePackageManifestV1,
        parameterId: String,
    ): String? {
        val definition = manifest.parameterDefinition(parameterId) ?: return null
        require(definition.type == ThemeParameterTypeV1.STRING) {
            "Theme parameter $parameterId must be declared as STRING."
        }
        instance.parameterValues[parameterId]?.let { value ->
            return (value as? ThemeParameterValueV1.StringValue ?: error(
                "Theme parameter $parameterId must be stored as a string value.",
            )).value
        }
        return (definition.defaultValue as? ThemeParameterDefaultV1.StringValue)?.value
    }
}
