package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact upstream release artifact bundled with the application as its default theme. */
internal object ThemePackageDefaultV1 {
    const val PACKAGE_ID = "operit.default"
    const val VERSION = "1.0.2"
    const val ARCHIVE_SHA256 = "e4b6aad585f1a79854f9f3fdc18e06445002a6629d2140df25ab87e7972667ed"

    private const val ASSET_PATH = "theme-packages/operit-default.otheme"

    val coordinate =
        ThemePackageCoordinateV1(
            packageId = ThemePackageIdV1(PACKAGE_ID),
            version = ThemePackageVersionV1(VERSION),
            archiveSha256 = ThemeArchiveSha256V1(ARCHIVE_SHA256),
        )

    suspend fun ensureInstalled(context: Context) {
        withContext(Dispatchers.IO) {
            val installer = ThemePackageInstallerV1.getInstance(context)
            if (installer.find(coordinate) != null) return@withContext

            val staged = File(context.cacheDir, "operit-default.$THEME_PACKAGE_EXTENSION")
            try {
                context.assets.open(ASSET_PATH).use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                val installed = installer.import(staged, expectedSha256 = ARCHIVE_SHA256)
                check(installed == coordinate) {
                    "Bundled default theme coordinate does not match its release lock."
                }
            } finally {
                staged.delete()
            }
        }
    }

    fun isDefault(coordinate: ThemePackageCoordinateV1): Boolean = coordinate == this.coordinate
}

internal object ThemePackageGlobalParameterIdsV1 {
    const val PRIMARY_COLOR = "primary_color"
    const val BACKGROUND_IMAGE = "background_image"
}

/** Globally consumed theme parameter values resolved from the active package manifest. */
internal data class ActiveGlobalThemeParametersV1(
    val primaryColorArgb: Long?,
    val backgroundImageUri: String?,
)

internal object ActiveGlobalThemeParameterResolverV1 {
    fun resolve(
        instance: ThemeInstanceV1,
        manifest: ThemePackageManifestV1,
    ): ActiveGlobalThemeParametersV1 =
        ActiveGlobalThemeParametersV1(
            primaryColorArgb =
                colorParameterValue(
                    instance,
                    manifest,
                    ThemePackageGlobalParameterIdsV1.PRIMARY_COLOR,
                ),
            backgroundImageUri =
                stringParameterValue(
                    instance,
                    manifest,
                    ThemePackageGlobalParameterIdsV1.BACKGROUND_IMAGE,
                ),
        )

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
