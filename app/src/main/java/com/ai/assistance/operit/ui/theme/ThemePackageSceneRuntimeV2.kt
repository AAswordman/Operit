package com.ai.assistance.operit.ui.theme

import com.ai.assistance.operit.data.theme.packages.ThemePackageLinkExceptionV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceIdV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceImplementationKindV2
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1

internal fun ThemePackageUiRuntimeV2.sceneFor(surface: ThemeSurfaceIdV2): ThemeSceneStageNodeV1 {
    val implementation = linked.surfaces[surface]
        ?: throw ThemePackageLinkExceptionV2("Active theme does not implement ${surface.value}.")
    check(implementation.kind == ThemeSurfaceImplementationKindV2.SCENE) {
        "Theme surface ${surface.value} is declared as ${implementation.kind}, not a scene."
    }
    val definition = linked.scenes[requireNotNull(implementation.sceneId)]
        ?: throw ThemePackageLinkExceptionV2(
            "Theme surface ${surface.value} references missing scene ${implementation.sceneId}.",
        )
    return definition.rootNode
}
