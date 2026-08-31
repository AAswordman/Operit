package com.ai.assistance.operit.ui.theme.scene.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneHostSlotNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneScaffoldNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneSlotIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenResolverV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSceneScaffoldAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun intrinsicComposerLeavesAVisibleTranscriptRegion() {
        composeTestRule.setContent {
            MaterialTheme {
                ThemeSceneV1(
                    stage =
                        ThemeSceneStageNodeV1(
                            nodeId = ThemeSceneNodeIdV1("root"),
                            children =
                                listOf(
                                    ThemeSceneScaffoldNodeV1(
                                        nodeId = ThemeSceneNodeIdV1("scaffold"),
                                        top = hostSlot("header_node", "header"),
                                        content = hostSlot("transcript_node", "transcript"),
                                        bottom = hostSlot("composer_node", "composer"),
                                    ),
                                ),
                        ),
                    tokens = ThemeSceneTokenResolverV1(ThemeSceneTokenSetV1()),
                    assets = ThemeSceneAssetRepositoryV1(emptyMap()),
                    hostSlots =
                        mapOf(
                            ThemeSceneSlotIdV1("header") to {
                                Box(Modifier.fillMaxWidth().height(48.dp).testTag("header"))
                            },
                            ThemeSceneSlotIdV1("transcript") to {
                                Box(Modifier.fillMaxSize().testTag("transcript"))
                            },
                            ThemeSceneSlotIdV1("composer") to {
                                // The chat host must provide this intrinsic-height shape, not fillMaxSize().
                                Box(Modifier.fillMaxWidth().height(72.dp).testTag("composer"))
                            },
                        ),
                    textResolver = { key -> key.value },
                    darkTheme = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.onNodeWithTag("composer").assertHeightIsAtLeast(72.dp)
        composeTestRule.onNodeWithTag("transcript").assertHeightIsAtLeast(1.dp)
    }

    private fun hostSlot(
        nodeId: String,
        slotId: String,
    ): ThemeSceneHostSlotNodeV1 =
        ThemeSceneHostSlotNodeV1(
            nodeId = ThemeSceneNodeIdV1(nodeId),
            slotId = ThemeSceneSlotIdV1(slotId),
        )
}
