package com.ai.assistance.operit.ui.theme.scene

import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSceneValidationV1Test {
    @Test
    fun chatMainSceneWithAllRequiredSlotsPassesValidation() {
        val issues = validateThemeSceneV1(chatMainDefinition())
        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun missingRequiredSlotIsReported() {
        val issues = validateThemeSceneV1(chatMainDefinition(dropConfigurationGate = true))
        assertTrue(
            issues.any { it.code == ThemeSceneIssueCodeV1.MISSING_REQUIRED_SLOT },
        )
    }

    @Test
    fun duplicateNodeIdIsReported() {
        val definition =
            ThemeSceneDefinitionV1(
                sceneId = ThemeSceneCatalogV1.CHAT_MAIN,
                version = ThemeSceneCatalogV1.SCENE_VERSION_1_0,
                rootNode =
                    ThemeSceneStageNodeV1(
                        nodeId = ThemeSceneNodeIdV1("root"),
                        children =
                            listOf(
                                hostSlot("header_node", "header"),
                                hostSlot("header_node", "transcript"),
                                hostSlot("composer_node", "composer"),
                                hostSlot("gate_node", "configuration_gate"),
                                hostSlot("rail_node", "classic_settings_rail"),
                                hostSlot("overlay_node", "overlay_stack"),
                            ),
                    ),
            )
        val issues = validateThemeSceneV1(definition)
        assertTrue(issues.any { it.code == ThemeSceneIssueCodeV1.DUPLICATE_NODE_ID })
    }

    @Test
    fun unknownSlotIsReported() {
        val issues = validateThemeSceneV1(chatMainDefinition(extraSlot = "chat.unknown"))
        assertTrue(issues.any { it.code == ThemeSceneIssueCodeV1.UNKNOWN_SLOT })
    }

    @Test
    fun invalidFractionMetricIsReported() {
        val definition = chatMainDefinition()
        val issues =
            validateThemeSceneV1(
                definition.copy(
                    rootNode =
                        ThemeSceneStageNodeV1(
                            nodeId = definition.rootNode.nodeId,
                            children =
                                listOf(
                                    ThemeSceneFrameNodeV1(
                                        nodeId = ThemeSceneNodeIdV1("bad_frame"),
                                        width = ThemeSceneSizeV1.Fraction(value = 1.5f),
                                        child = hostSlot("gate_node", "configuration_gate"),
                                    )
                                ) + definition.rootNode.children.drop(1),
                        ),
                ),
            )
        assertTrue(issues.any { it.code == ThemeSceneIssueCodeV1.INVALID_METRIC })
    }

    private fun chatMainDefinition(
        dropConfigurationGate: Boolean = false,
        extraSlot: String? = null,
    ): ThemeSceneDefinitionV1 {
        val children =
            buildList {
                if (!dropConfigurationGate) {
                    add(hostSlot("gate_node", "configuration_gate"))
                }
                add(hostSlot("header_node", "header"))
                add(hostSlot("transcript_node", "transcript"))
                add(hostSlot("composer_node", "composer"))
                add(hostSlot("rail_node", "classic_settings_rail"))
                add(hostSlot("overlay_node", "overlay_stack"))
                if (extraSlot != null) {
                    add(hostSlot("extra_node", extraSlot))
                }
            }
        return ThemeSceneDefinitionV1(
            sceneId = ThemeSceneCatalogV1.CHAT_MAIN,
            version = ThemeSceneCatalogV1.SCENE_VERSION_1_0,
            rootNode =
                ThemeSceneStageNodeV1(
                    nodeId = ThemeSceneNodeIdV1("root"),
                    children = children,
                ),
        )
    }

    private fun hostSlot(
        nodeId: String,
        slotId: String,
    ) = ThemeSceneHostSlotNodeV1(
        nodeId = ThemeSceneNodeIdV1(nodeId),
        slotId = ThemeSceneSlotIdV1(slotId),
    )
}
