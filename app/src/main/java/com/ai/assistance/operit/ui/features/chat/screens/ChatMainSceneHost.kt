package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.theme.LocalActiveThemeSceneRuntime
import com.ai.assistance.operit.ui.theme.LocalResolvedGlobalTheme
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneSlotIdV1
import com.ai.assistance.operit.ui.theme.scene.render.ThemeSceneV1

/** Maps the stable chat.main semantic slots to the active declarative theme scene. */
@Composable
internal fun ChatMainSceneHost(
    configurationGate: @Composable () -> Unit,
    header: @Composable () -> Unit,
    transcript: @Composable () -> Unit,
    composer: @Composable () -> Unit,
    classicSettingsRail: @Composable () -> Unit,
    overlayStack: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = LocalActiveThemeSceneRuntime.current
    val darkTheme = LocalResolvedGlobalTheme.current.darkTheme
    val movableConfigurationGate = rememberMovableChatMainSlot(configurationGate)
    val movableHeader = rememberMovableChatMainSlot(header)
    val movableTranscript = rememberMovableChatMainSlot(transcript)
    val movableComposer = rememberMovableChatMainSlot(composer)
    val movableRail = rememberMovableChatMainSlot(classicSettingsRail)
    val movableOverlays = rememberMovableChatMainSlot(overlayStack)

    ThemeSceneV1(
        stage = runtime.chatMain,
        tokens = runtime.tokens,
        assets = runtime.assets,
        hostSlots =
            mapOf(
                ThemeSceneSlotIdV1("configuration_gate") to movableConfigurationGate,
                ThemeSceneSlotIdV1("header") to movableHeader,
                ThemeSceneSlotIdV1("transcript") to movableTranscript,
                ThemeSceneSlotIdV1("composer") to movableComposer,
                ThemeSceneSlotIdV1("classic_settings_rail") to movableRail,
                ThemeSceneSlotIdV1("overlay_stack") to movableOverlays,
            ),
        textResolver = { key ->
            error("Active chat theme has no text resource for ${key.value}.")
        },
        darkTheme = darkTheme,
        modifier = modifier,
    )
}

@Composable
private fun rememberMovableChatMainSlot(
    content: @Composable () -> Unit,
): @Composable () -> Unit {
    val latestContent = rememberUpdatedState(content)
    return remember {
        movableContentOf {
            latestContent.value()
        }
    }
}
