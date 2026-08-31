package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneHostSlotNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneSlotIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneVersionV1
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * V2 覆盖门槛：主题包（或其基底链）必须覆盖全部日常 surface 与组件皮肤，
 * 否则激活链接直接失败——不允许任何界面回落到 Material 默认视觉。
 */
class ThemePackageRuntimeLinkerV2Test {
    @get:Rule
    val tmp = TemporaryFolder()

    private val colorToken =
        ThemeSceneTokenValueV1.ColorToken(0xFF111111, 0xFFEEEEEE)

    private fun fullTokenSet(): ThemeSceneTokenSetV1 =
        ThemeSceneTokenSetV1(
            tokens =
                buildMap {
                    put("color.background", colorToken)
                    ThemeComponentCatalogV2.requiredComponents.forEach { component ->
                        put("color.${component.value}", colorToken)
                    }
                    ThemeSurfaceCatalogV2.requiredDailySurfaces.forEach { surface ->
                        put("color.${surface.value.replace('.', '_')}", colorToken)
                    }
                },
        )

    private fun appShellScene(): ThemeSceneDefinitionV1 =
        ThemeSceneDefinitionV1(
            sceneId = ThemeSceneIdV1("app.shell"),
            version = ThemeSceneVersionV1(major = 1, minor = 0),
            rootNode =
                ThemeSceneStageNodeV1(
                    nodeId = ThemeSceneNodeIdV1("root"),
                    children =
                        listOf(
                            ThemeSceneHostSlotNodeV1(
                                nodeId = ThemeSceneNodeIdV1("nav"),
                                slotId = ThemeSceneSlotIdV1("app_bar.navigation"),
                            ),
                            ThemeSceneHostSlotNodeV1(
                                nodeId = ThemeSceneNodeIdV1("title"),
                                slotId = ThemeSceneSlotIdV1("app_bar.title"),
                                rowWeight = 1f,
                            ),
                            ThemeSceneHostSlotNodeV1(
                                nodeId = ThemeSceneNodeIdV1("actions"),
                                slotId = ThemeSceneSlotIdV1("app_bar.actions"),
                            ),
                            ThemeSceneHostSlotNodeV1(
                                nodeId = ThemeSceneNodeIdV1("route"),
                                slotId = ThemeSceneSlotIdV1("route.content"),
                            ),
                        ),
                ),
        )

    private fun completeManifest(packageId: String = "author.complete"): ThemePackageManifestV2 =
        ThemePackageManifestV2(
            schemaVersion = THEME_PACKAGE_SCHEMA_VERSION_V2,
            packageId = packageId,
            version = "1.0.0",
            displayName = ThemePackageLocalizedTextV2(values = mapOf("*" to packageId)),
            tokens = fullTokenSet(),
            scenes = listOf(appShellScene()),
            surfaces =
                ThemeSurfaceCatalogV2.requiredDailySurfaces
                    .minus(ThemeSurfaceCatalogV2.APP_SHELL)
                    .map { surface ->
                        ThemeSurfaceImplementationV2(
                            surfaceId = surface.value,
                            kind = ThemeSurfaceImplementationKindV2.TEMPLATE,
                        )
                    } +
                    ThemeSurfaceImplementationV2(
                        surfaceId = ThemeSurfaceCatalogV2.APP_SHELL.value,
                        kind = ThemeSurfaceImplementationKindV2.SCENE,
                        sceneId = "app.shell",
                    ),
            presentation =
                ThemePackagePresentationPatchV2(
                    material = materialProjection(),
                    componentSkins =
                        ThemeComponentCatalogV2.requiredComponents.associate { component ->
                            component.value to componentSkin("color.${component.value}")
                        },
                ),
            parameters =
                listOf(
                    ThemeParameterDefinitionV2(
                        id = "primary_color",
                        type = ThemeParameterTypeV2.COLOR,
                        defaultValue = ThemeParameterDefaultV2.ColorValue(0xFF6750A4),
                        label = ThemePackageLocalizedTextV2(values = mapOf("*" to "Primary")),
                    ),
                ),
        )

    private fun materialProjection(): ThemeMaterialProjectionV2 =
        ThemeMaterialProjectionV2(
            colors = ThemeMaterialColorSchemeV2.uniform("color.background"),
            typography = ThemeTypographyV2(),
            shapes = ThemeShapesV2(2f, 4f, 8f, 16f, 28f),
        )

    private fun componentSkin(containerToken: String): ThemeComponentSkinV2 =
        ThemeComponentSkinV2(
            normal =
                ThemeComponentStateSkinV2(
                    containerToken = containerToken,
                    contentToken = "color.background",
                    frame = ThemeComponentFrameSpecV2.RoundRect(cornerRadiusDp = 0f),
                ),
        )

    private fun installation(
        manifest: ThemePackageManifestV2,
        directory: File,
    ): PublishedThemeInstallationV2 {
        directory.mkdirs()
        val manifestJson =
            kotlinx.serialization.json.Json {
                encodeDefaults = true
                explicitNulls = false
            }.encodeToString(manifest)
        File(directory, THEME_PACKAGE_MANIFEST_ENTRY_V2).writeText(manifestJson)
        return PublishedThemeInstallationV2(
            coordinate =
                ThemePackageCoordinateV2(
                    packageId = ThemePackageIdV2(manifest.packageId),
                    version = ThemePackageVersionV2(manifest.version),
                    archiveSha256 = ThemeArchiveSha256V2(sha256Of(directory.name + manifest.version)),
                ),
            manifest = manifest,
            rootDir = directory,
        )
    }

    private fun sha256Of(seed: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> String.format(java.util.Locale.US, "%02x", byte.toInt() and 0xff) }

    @Test
    fun completePresentationLinks() {
        val installation = installation(completeManifest(), tmp.newFolder("complete"))
        val linked = ThemePackageRuntimeLinkerV2.link(
            installation,
            PublishedThemeCatalogV2(listOf(installation), emptyList()),
        )

        assertEquals(ThemeSurfaceCatalogV2.requiredDailySurfaces.size, linked.surfaces.size)
        assertEquals(ThemeComponentCatalogV2.requiredComponents.size, linked.componentSkins.size)
        assertTrue(linked.scenes.containsKey("app.shell"))
    }

    @Test
    fun missingSurfaceCoverageIsRejected() {
        val manifest =
            completeManifest().copy(
                surfaces =
                    completeManifest().surfaces.filterNot {
                        it.surfaceId == ThemeSurfaceCatalogV2.OVERLAY_SNACKBAR.value
                    },
            )
        val installation = installation(manifest, tmp.newFolder("missing-surface"))

        val error =
            assertThrows(ThemePackageLinkExceptionV2::class.java) {
                ThemePackageRuntimeLinkerV2.link(
                    installation,
                    PublishedThemeCatalogV2(listOf(installation), emptyList()),
                )
            }
        assertTrue(error.message!!.contains("overlay.snackbar"))
    }

    @Test
    fun missingComponentSkinIsRejected() {
        val manifest =
            completeManifest().copy(
                presentation =
                    completeManifest().presentation.copy(
                        componentSkins =
                            completeManifest().presentation.componentSkins -
                                ThemeComponentCatalogV2.DIALOG.value,
                    ),
            )
        val installation = installation(manifest, tmp.newFolder("missing-skin"))

        assertThrows(ThemePackageLinkExceptionV2::class.java) {
            ThemePackageRuntimeLinkerV2.link(
                installation,
                PublishedThemeCatalogV2(listOf(installation), emptyList()),
            )
        }
    }

    @Test
    fun missingMaterialProjectionIsRejected() {
        val manifest =
            completeManifest().copy(
                presentation = ThemePackagePresentationPatchV2(),
            )
        val installation = installation(manifest, tmp.newFolder("no-material"))

        assertThrows(ThemePackageLinkExceptionV2::class.java) {
            ThemePackageRuntimeLinkerV2.link(
                installation,
                PublishedThemeCatalogV2(listOf(installation), emptyList()),
            )
        }
    }

    @Test
    fun basisChainInheritsCoverageAndRejectsConflicts() {
        val baseDir = tmp.newFolder("base")
        val base = installation(completeManifest("author.base"), baseDir)
        val baseSha = base.coordinate.archiveSha256.value

        // 子包只覆盖 app.shell 场景，其余 surface 与皮肤从基底继承。
        val childManifest =
            completeManifest("author.child").copy(
                basis =
                    ThemePackageCoordinateV2(
                        packageId = ThemePackageIdV2("author.base"),
                        version = ThemePackageVersionV2("1.0.0"),
                        archiveSha256 = ThemeArchiveSha256V2(baseSha),
                    ),
                surfaces =
                    listOf(
                        ThemeSurfaceImplementationV2(
                            surfaceId = ThemeSurfaceCatalogV2.APP_SHELL.value,
                            kind = ThemeSurfaceImplementationKindV2.SCENE,
                            sceneId = "app.shell",
                        ),
                    ),
                presentation = ThemePackagePresentationPatchV2(),
                parameters = emptyList(),
            )
        val child = installation(childManifest, tmp.newFolder("child"))
        val linked =
            ThemePackageRuntimeLinkerV2.link(
                child,
                PublishedThemeCatalogV2(listOf(base, child), emptyList()),
            )

        assertEquals(ThemeSurfaceCatalogV2.requiredDailySurfaces.size, linked.surfaces.size)
        assertEquals(listOf(base.coordinate, child.coordinate), linked.packageChain)

        // 子包重复声明基底参数是链接错误：参数语义不允许二义性。
        val conflictingChild =
            childManifest.copy(
                parameters = completeManifest().parameters,
            )
        val conflict = installation(conflictingChild, tmp.newFolder("child-conflict"))
        assertThrows(ThemePackageLinkExceptionV2::class.java) {
            ThemePackageRuntimeLinkerV2.link(
                conflict,
                PublishedThemeCatalogV2(listOf(base, conflict), emptyList()),
            )
        }
    }

    @Test
    fun resolveParametersUsesInstanceValuesAndDefaults() {
        val manifest = completeManifest()
        val installation = installation(manifest, tmp.newFolder("params"))
        val linked =
            ThemePackageRuntimeLinkerV2.link(
                installation,
                PublishedThemeCatalogV2(listOf(installation), emptyList()),
            )

        val defaults =
            ThemePackageRuntimeLinkerV2.resolveParameters(
                ThemeInstanceV2(reference = ThemePackageReferenceV2(installation.coordinate)),
                linked,
            )
        assertEquals(0xFF6750A4L, defaults.colorArgb("primary_color"))

        val overridden =
            ThemePackageRuntimeLinkerV2.resolveParameters(
                ThemeInstanceV2(
                    reference = ThemePackageReferenceV2(installation.coordinate),
                    parameterValues =
                        mapOf("primary_color" to ThemeParameterValueV2.ColorValue(0xFF00FF00)),
                ),
                linked,
            )
        assertEquals(0xFF00FF00L, overridden.colorArgb("primary_color"))

        assertThrows(IllegalStateException::class.java) {
            ThemePackageRuntimeLinkerV2.resolveParameters(
                ThemeInstanceV2(
                    reference = ThemePackageReferenceV2(installation.coordinate),
                    parameterValues =
                        mapOf("unknown_parameter" to ThemeParameterValueV2.StringValue("x")),
                ),
                linked,
            )
        }
    }
}
