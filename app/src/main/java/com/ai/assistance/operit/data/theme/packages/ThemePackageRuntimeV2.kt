package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1
import java.io.File

internal class ThemePackageLinkExceptionV2(message: String) : IllegalStateException(message)

/**
 * Immutable, fully linked visual contract. Compose roots consume this object and never inspect
 * package JSON, archive contents, or an unlinked base package while rendering.
 */
internal data class LinkedThemeRuntimeV2(
    val coordinate: ThemePackageCoordinateV2,
    val packageChain: List<ThemePackageCoordinateV2>,
    val material: ThemeMaterialProjectionV2,
    val componentSkins: Map<ThemeComponentIdV2, ThemeComponentSkinV2>,
    val surfaces: Map<ThemeSurfaceIdV2, ThemeSurfaceImplementationV2>,
    val tokens: ThemeSceneTokenSetV1,
    val scenes: Map<String, ThemeSceneDefinitionV1>,
    val assets: Map<String, File>,
    val parameterDefinitions: Map<String, ThemeParameterDefinitionV2>,
)

internal data class ResolvedThemeParametersV2(
    val values: Map<String, ThemeParameterValueV2>,
) {
    fun colorArgb(id: String): Long? = (values[id] as? ThemeParameterValueV2.ColorValue)?.argb

    fun string(id: String): String? = (values[id] as? ThemeParameterValueV2.StringValue)?.value
}

internal object ThemePackageRuntimeLinkerV2 {
    fun link(
        active: PublishedThemeInstallationV2,
        catalog: PublishedThemeCatalogV2,
    ): LinkedThemeRuntimeV2 {
        val installations = catalog.installations.associateBy { installation -> installation.coordinate }
        val chain = resolveChain(active, installations, linkedSetOf())
        val merged = chain.fold(LinkedThemeAccumulatorV2()) { state, installation ->
            state.merge(installation)
        }
        return merged.toRuntime(active.coordinate, chain.map { installation -> installation.coordinate })
    }

    fun resolveParameters(
        instance: ThemeInstanceV2,
        runtime: LinkedThemeRuntimeV2,
    ): ResolvedThemeParametersV2 {
        val values = LinkedHashMap<String, ThemeParameterValueV2>()
        runtime.parameterDefinitions.forEach { (id, definition) ->
            val value = instance.parameterValues[id] ?: definition.defaultValue.toValueOrNull()
            if (value != null) {
                if (!value.matches(definition.type)) {
                    throw ThemePackageLinkExceptionV2(
                        "Theme parameter $id does not match declared type ${definition.type}.",
                    )
                }
                values[id] = value
            }
        }
        instance.parameterValues.keys.forEach { id ->
            if (id !in runtime.parameterDefinitions) {
                throw ThemePackageLinkExceptionV2(
                    "Theme instance declares unknown parameter: $id",
                )
            }
        }
        return ResolvedThemeParametersV2(values)
    }

    private fun resolveChain(
        installation: PublishedThemeInstallationV2,
        installations: Map<ThemePackageCoordinateV2, PublishedThemeInstallationV2>,
        visiting: MutableSet<ThemePackageCoordinateV2>,
    ): List<PublishedThemeInstallationV2> {
        if (!visiting.add(installation.coordinate)) {
            throw ThemePackageLinkExceptionV2(
                "Theme package basis cycle includes ${installation.coordinate.packageId.value}.",
            )
        }
        val result = buildList {
            installation.manifest.basis?.let { basis ->
                val base = installations[basis]
                    ?: throw ThemePackageLinkExceptionV2(
                        "Theme package basis is not installed: ${basis.packageId.value}@${basis.version.value}",
                    )
                addAll(resolveChain(base, installations, visiting))
            }
            add(installation)
        }
        visiting.remove(installation.coordinate)
        return result
    }
}

private data class LinkedThemeAccumulatorV2(
    val material: ThemeMaterialProjectionV2? = null,
    val componentSkins: Map<String, ThemeComponentSkinV2> = emptyMap(),
    val surfaces: Map<String, ThemeSurfaceImplementationV2> = emptyMap(),
    val tokens: Map<String, ThemeSceneTokenValueV1> = emptyMap(),
    val scenes: Map<String, ThemeSceneDefinitionV1> = emptyMap(),
    val assets: Map<String, File> = emptyMap(),
    val parameters: Map<String, ThemeParameterDefinitionV2> = emptyMap(),
) {
    fun merge(installation: PublishedThemeInstallationV2): LinkedThemeAccumulatorV2 {
        val manifest = installation.manifest
        val duplicateAssetKeys = assets.keys.intersect(manifest.assets.map { asset -> asset.key }.toSet())
        if (duplicateAssetKeys.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package ${manifest.packageId} redefines inherited asset keys: " +
                    duplicateAssetKeys.sorted().joinToString(),
            )
        }
        val duplicateParameterIds = parameters.keys.intersect(manifest.parameters.map { parameter -> parameter.id }.toSet())
        if (duplicateParameterIds.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package ${manifest.packageId} redefines inherited parameters: " +
                    duplicateParameterIds.sorted().joinToString(),
            )
        }
        return copy(
            material = manifest.presentation.material ?: material,
            componentSkins = componentSkins + manifest.presentation.componentSkins,
            surfaces = surfaces + manifest.surfaces.associateBy { surface -> surface.surfaceId },
            tokens = tokens + manifest.tokens.tokens,
            scenes = scenes + manifest.scenes.associateBy { scene -> scene.sceneId.value },
            assets =
                assets +
                    manifest.assets.associate { asset ->
                        asset.key to File(installation.rootDir, asset.path)
                    },
            parameters = parameters + manifest.parameters.associateBy { parameter -> parameter.id },
        )
    }

    fun toRuntime(
        coordinate: ThemePackageCoordinateV2,
        packageChain: List<ThemePackageCoordinateV2>,
    ): LinkedThemeRuntimeV2 {
        val resolvedMaterial = material
            ?: throw ThemePackageLinkExceptionV2("Theme package has no linked Material projection.")
        val componentSkinMap = componentSkins.mapKeys { (id, _) -> ThemeComponentIdV2(id) }
        val surfaceMap = surfaces.mapKeys { (id, _) -> ThemeSurfaceIdV2(id) }
        validateCoverage(componentSkinMap, surfaceMap)
        validateTokenReferences(resolvedMaterial, componentSkinMap, tokens)
        validateSceneReferences(surfaces, scenes, tokens, assets)
        return LinkedThemeRuntimeV2(
            coordinate = coordinate,
            packageChain = packageChain,
            material = resolvedMaterial,
            componentSkins = componentSkinMap,
            surfaces = surfaceMap,
            tokens = ThemeSceneTokenSetV1(tokens),
            scenes = scenes,
            assets = assets,
            parameterDefinitions = parameters,
        )
    }

    private fun validateCoverage(
        componentSkins: Map<ThemeComponentIdV2, ThemeComponentSkinV2>,
        surfaces: Map<ThemeSurfaceIdV2, ThemeSurfaceImplementationV2>,
    ) {
        val missingSurfaces = ThemeSurfaceCatalogV2.requiredDailySurfaces - surfaces.keys
        if (missingSurfaces.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package does not cover required daily surfaces: " +
                    missingSurfaces.map { surface -> surface.value }.sorted().joinToString(),
            )
        }
        val missingComponents = ThemeComponentCatalogV2.requiredComponents - componentSkins.keys
        if (missingComponents.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package does not provide required component skins: " +
                    missingComponents.map { component -> component.value }.sorted().joinToString(),
            )
        }
    }

    private fun validateTokenReferences(
        material: ThemeMaterialProjectionV2,
        componentSkins: Map<ThemeComponentIdV2, ThemeComponentSkinV2>,
        tokens: Map<String, ThemeSceneTokenValueV1>,
    ) {
        material.colors.tokenIds().forEach { tokenId -> requireColor(tokens, tokenId, "Material projection") }
        componentSkins.forEach { (componentId, skin) ->
            listOfNotNull(skin.normal, skin.disabled, skin.selected, skin.focused, skin.error).forEach { state ->
                requireColor(tokens, state.containerToken, "Component ${componentId.value}")
                requireColor(tokens, state.contentToken, "Component ${componentId.value}")
                state.frame.strokes().forEach { stroke ->
                    requireColor(tokens, stroke.token, "Component ${componentId.value}")
                }
            }
        }
    }

    private fun validateSceneReferences(
        surfaces: Map<String, ThemeSurfaceImplementationV2>,
        scenes: Map<String, ThemeSceneDefinitionV1>,
        tokens: Map<String, ThemeSceneTokenValueV1>,
        assets: Map<String, File>,
    ) {
        surfaces.values.forEach { surface ->
            if (surface.kind == ThemeSurfaceImplementationKindV2.SCENE) {
                val sceneId = requireNotNull(surface.sceneId)
                if (sceneId !in scenes) {
                    throw ThemePackageLinkExceptionV2(
                        "Theme surface ${surface.surfaceId} references missing linked scene $sceneId.",
                    )
                }
            }
        }
        scenes.values.forEach { scene ->
            validateSceneNode(scene.rootNode, tokens, assets)
        }
    }

    private fun validateSceneNode(
        node: ThemeSceneNodeV1,
        tokens: Map<String, ThemeSceneTokenValueV1>,
        assets: Map<String, File>,
    ) {
        when (node) {
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1 ->
                node.backgroundColorToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneSurfaceNodeV1 -> {
                node.fillToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
                node.outlineToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
            }

            is com.ai.assistance.operit.ui.theme.scene.ThemeScenePathNodeV1 -> {
                node.fillToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
                node.outlineToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
                requireAsset(assets, node.assetId.value, "Scene")
            }

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageNodeV1 ->
                requireAsset(assets, node.assetId.value, "Scene")

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneNineSliceNodeV1 ->
                requireAsset(assets, node.assetId.value, "Scene")

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTextNodeV1 -> {
                val styleToken = requireNotNull(node.styleToken) {
                    "Scene text node ${node.nodeId.value} requires a style token."
                }
                val style = tokens[styleToken.value] as? ThemeSceneTokenValueV1.TextStyleToken
                    ?: throw ThemePackageLinkExceptionV2(
                        "Scene references missing or non-text-style token: ${styleToken.value}",
                    )
                requireColor(tokens, style.color.value, "Scene text")
                style.fontAsset?.let { assetId -> requireAsset(assets, assetId.value, "Scene text") }
            }

            else -> Unit
        }
        childrenOf(node).forEach { child -> validateSceneNode(child, tokens, assets) }
    }

    private fun requireColor(
        tokens: Map<String, ThemeSceneTokenValueV1>,
        tokenId: String,
        owner: String,
    ) {
        ThemeSceneTokenIdV1(tokenId)
        if (tokens[tokenId] !is ThemeSceneTokenValueV1.ColorToken) {
            throw ThemePackageLinkExceptionV2("$owner references missing or non-color token: $tokenId")
        }
    }

    private fun requireAsset(
        assets: Map<String, File>,
        assetId: String,
        owner: String,
    ) {
        if (assets[assetId]?.isFile != true) {
            throw ThemePackageLinkExceptionV2("$owner references missing linked asset: $assetId")
        }
    }

    private fun childrenOf(node: ThemeSceneNodeV1): List<ThemeSceneNodeV1> =
        when (node) {
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneLayerNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneRowNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneColumnNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneScaffoldNodeV1 ->
                listOfNotNull(node.top, node.content, node.bottom, node.overlay)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneGridNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneFrameNodeV1 -> listOf(node.child)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTransformNodeV1 -> listOf(node.child)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneSurfaceNodeV1 ->
                node.child?.let(::listOf) ?: emptyList()

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneNineSliceNodeV1 ->
                node.child?.let(::listOf) ?: emptyList()

            else -> emptyList()
        }
}

private fun ThemeParameterDefaultV2.toValueOrNull(): ThemeParameterValueV2? =
    when (this) {
        is ThemeParameterDefaultV2.ColorValue -> ThemeParameterValueV2.ColorValue(argb)
        is ThemeParameterDefaultV2.BooleanValue -> ThemeParameterValueV2.BooleanValue(value)
        is ThemeParameterDefaultV2.IntegerValue -> ThemeParameterValueV2.IntegerValue(value)
        is ThemeParameterDefaultV2.DecimalValue -> ThemeParameterValueV2.DecimalValue(value)
        is ThemeParameterDefaultV2.StringValue -> ThemeParameterValueV2.StringValue(value)
        ThemeParameterDefaultV2.Unset -> null
    }

private fun ThemeParameterValueV2.matches(type: ThemeParameterTypeV2): Boolean =
    when (type) {
        ThemeParameterTypeV2.COLOR -> this is ThemeParameterValueV2.ColorValue
        ThemeParameterTypeV2.BOOLEAN -> this is ThemeParameterValueV2.BooleanValue
        ThemeParameterTypeV2.INTEGER -> this is ThemeParameterValueV2.IntegerValue
        ThemeParameterTypeV2.DECIMAL -> this is ThemeParameterValueV2.DecimalValue
        ThemeParameterTypeV2.STRING -> this is ThemeParameterValueV2.StringValue
    }
