package com.ai.assistance.operit.ui.theme.scene

import com.ai.assistance.operit.data.theme.packages.ThemePackageManifestV1
import com.ai.assistance.operit.data.theme.packages.ActiveGlobalThemeParametersV1
import com.ai.assistance.operit.ui.theme.scene.render.ThemeSceneAssetRepositoryV1
import java.io.File

/** Immutable, linked scene state consumed by one Compose host. */
internal data class ActiveThemeSceneRuntimeV1(
    val manifest: ThemePackageManifestV1,
    val parameters: ActiveGlobalThemeParametersV1,
    val chatMain: ThemeSceneStageNodeV1,
    val tokens: ThemeSceneTokenResolverV1,
    val assets: ThemeSceneAssetRepositoryV1,
)

internal object ActiveThemeSceneRuntimeFactoryV1 {
    fun create(
        manifest: ThemePackageManifestV1,
        installationRoot: File?,
        parameters: ActiveGlobalThemeParametersV1,
    ): ActiveThemeSceneRuntimeV1 {
        val chatMain =
            manifest.scenes.singleOrNull { scene ->
                scene.sceneId == ThemeSceneCatalogV1.CHAT_MAIN
            }?.rootNode
                ?: error("Active theme has no chat.main scene: ${manifest.packageId}")
        val assets =
            manifest.assets.associate { asset ->
                val root = installationRoot
                    ?: error("Built-in theme cannot declare package assets: ${asset.key}")
                asset.key to File(root, asset.path)
            }
        return ActiveThemeSceneRuntimeV1(
            manifest = manifest,
            parameters = parameters,
            chatMain = chatMain,
            tokens = ThemeSceneTokenResolverV1(manifest.tokens),
            assets = ThemeSceneAssetRepositoryV1(assets),
        )
    }
}
