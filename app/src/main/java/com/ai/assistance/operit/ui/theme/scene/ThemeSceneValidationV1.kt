package com.ai.assistance.operit.ui.theme.scene

internal enum class ThemeSceneIssueCodeV1 {
    UNKNOWN_SCENE,
    VERSION_MISMATCH,
    DUPLICATE_NODE_ID,
    EMPTY_CHILDREN,
    INVALID_METRIC,
    INVALID_NODE,
    UNKNOWN_SLOT,
    MISSING_REQUIRED_SLOT,
    DUPLICATE_SLOT,
    REPEATED_SINGLE_SLOT,
    NODE_LIMIT_EXCEEDED,
    DEPTH_LIMIT_EXCEEDED,
    UNKNOWN_TOKEN,
    INVALID_TOKEN_REFERENCE,
}

internal data class ThemeSceneIssueV1(
    val code: ThemeSceneIssueCodeV1,
    val path: String,
    val message: String,
)

internal object ThemeSceneLimitsV1 {
    const val MAX_NODE_COUNT = 512
    const val MAX_NODE_DEPTH = 24
}

/**
 * Validates a declarative scene tree against the host scene catalog. Returns structured issues
 * instead of throwing so package authors see every problem in one pass.
 */
internal fun validateThemeSceneV1(
    definition: ThemeSceneDefinitionV1,
    contracts: Map<ThemeSceneIdV1, ThemeSceneContractV1> = ThemeSceneCatalogV1.contracts,
): List<ThemeSceneIssueV1> {
    val issues = mutableListOf<ThemeSceneIssueV1>()
    val contract = contracts[definition.sceneId]
    if (contract == null) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.UNKNOWN_SCENE,
                path = "scene",
                message = "Unknown scene: ${definition.sceneId.value}",
            )
        return issues
    }
    if (definition.version != contract.version) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.VERSION_MISMATCH,
                path = "scene",
                message =
                    "Scene ${definition.sceneId.value} requires version " +
                        "${contract.version.major}.${contract.version.minor} but declares " +
                        "${definition.version.major}.${definition.version.minor}.",
            )
    }

    val state = ThemeSceneWalkState()
    walkNode(definition.rootNode, "stage", 1, contract, state, issues)

    contract.slotContracts.forEach { slotContract ->
        val usage = state.slotUsages[slotContract.slotId] ?: 0
        when (slotContract.cardinality) {
            ThemeSceneSlotCardinalityV1.REQUIRED_SINGLE ->
                when {
                    usage == 0 ->
                        issues +=
                            ThemeSceneIssueV1(
                                code = ThemeSceneIssueCodeV1.MISSING_REQUIRED_SLOT,
                                path = "scene",
                                message =
                                    "Required slot is not placed: ${slotContract.slotId.value}",
                            )

                    usage > 1 ->
                        issues +=
                            ThemeSceneIssueV1(
                                code = ThemeSceneIssueCodeV1.DUPLICATE_SLOT,
                                path = "scene",
                                message =
                                    "Slot ${slotContract.slotId.value} is placed $usage times " +
                                        "but allows exactly one.",
                            )
                }

            ThemeSceneSlotCardinalityV1.OPTIONAL_SINGLE ->
                if (usage > 1) {
                    issues +=
                        ThemeSceneIssueV1(
                            code = ThemeSceneIssueCodeV1.REPEATED_SINGLE_SLOT,
                            path = "scene",
                            message =
                                "Slot ${slotContract.slotId.value} is placed $usage times " +
                                    "but allows at most one.",
                        )
                }

            ThemeSceneSlotCardinalityV1.REPEATED -> Unit
        }
    }
    return issues
}

private class ThemeSceneWalkState {
    val nodeIds = mutableSetOf<ThemeSceneNodeIdV1>()
    val slotUsages = mutableMapOf<ThemeSceneSlotIdV1, Int>()
    var nodeCount = 0
    var nodeLimitReported = false
}

private fun walkNode(
    node: ThemeSceneNodeV1,
    path: String,
    depth: Int,
    contract: ThemeSceneContractV1,
    state: ThemeSceneWalkState,
    issues: MutableList<ThemeSceneIssueV1>,
) {
    state.nodeCount += 1
    if (state.nodeCount > ThemeSceneLimitsV1.MAX_NODE_COUNT) {
        if (!state.nodeLimitReported) {
            state.nodeLimitReported = true
            issues +=
                ThemeSceneIssueV1(
                    code = ThemeSceneIssueCodeV1.NODE_LIMIT_EXCEEDED,
                    path = path,
                    message = "Scene node count exceeds ${ThemeSceneLimitsV1.MAX_NODE_COUNT}.",
                )
        }
        return
    }
    if (depth > ThemeSceneLimitsV1.MAX_NODE_DEPTH) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.DEPTH_LIMIT_EXCEEDED,
                path = path,
                message = "Scene node depth exceeds ${ThemeSceneLimitsV1.MAX_NODE_DEPTH}.",
            )
        return
    }
    if (!state.nodeIds.add(node.nodeId)) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.DUPLICATE_NODE_ID,
                path = path,
                message = "Duplicate node ID: ${node.nodeId.value}",
            )
    }

    when (node) {
        is ThemeSceneStageNodeV1 -> requireChildren(node.children, path, issues)

        is ThemeSceneLayerNodeV1 -> requireChildren(node.children, path, issues)

        is ThemeSceneRowNodeV1 -> {
            validateNonNegative(node.spacingDp, "$path/spacing", issues)
            requireChildren(node.children, path, issues)
        }

        is ThemeSceneColumnNodeV1 -> {
            validateNonNegative(node.spacingDp, "$path/spacing", issues)
            requireChildren(node.children, path, issues)
        }

        is ThemeSceneScaffoldNodeV1 -> Unit

        is ThemeSceneGridNodeV1 -> {
            if (node.columns < 1) {
                issues +=
                    ThemeSceneIssueV1(
                        code = ThemeSceneIssueCodeV1.INVALID_NODE,
                        path = path,
                        message = "Grid columns must be at least 1.",
                    )
            }
            validateNonNegative(node.spacingDp, "$path/spacing", issues)
            requireChildren(node.children, path, issues)
        }

        is ThemeSceneFrameNodeV1 -> {
            node.anchor?.let { validateAnchor(it, path, issues) }
            validateSize(node.width, "$path/width", issues)
            validateSize(node.height, "$path/height", issues)
            node.minWidthDp?.let { validateNonNegative(it, "$path/min_width", issues) }
            node.maxWidthDp?.let { validateNonNegative(it, "$path/max_width", issues) }
            if (node.minWidthDp != null &&
                node.maxWidthDp != null &&
                node.minWidthDp > node.maxWidthDp
            ) {
                issues +=
                    ThemeSceneIssueV1(
                        code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                        path = path,
                        message = "Frame min width exceeds max width.",
                    )
            }
            node.contentPadding?.let { validateInsets(it, path, issues) }
        }

        is ThemeSceneHostSlotNodeV1 -> {
            val known = contract.slotContracts.any { it.slotId == node.slotId }
            if (!known) {
                issues +=
                    ThemeSceneIssueV1(
                        code = ThemeSceneIssueCodeV1.UNKNOWN_SLOT,
                        path = path,
                        message = "Unknown host slot: ${node.slotId.value}",
                    )
            }
            state.slotUsages[node.slotId] = (state.slotUsages[node.slotId] ?: 0) + 1
            node.contentPadding?.let { validateInsets(it, path, issues) }
            node.rowWeight?.let { weight ->
                if (weight <= 0f) {
                    issues +=
                        ThemeSceneIssueV1(
                            code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                            path = "$path/row_weight",
                            message = "Host slot row weight must be positive.",
                        )
                }
            }
        }

        is ThemeSceneSurfaceNodeV1 -> {
            validateUnitInterval(node.opacity, "$path/opacity", issues)
            validateNonNegative(node.outlineWidthDp, "$path/outline_width", issues)
            validateNonNegative(node.cornerRadiusDp, "$path/corner_radius", issues)
        }

        is ThemeSceneNineSliceNodeV1 -> {
            validateInsets(node.destinationCapInsetsDp, path, issues)
            if (
                node.sourceCapInsetsPx.startPx + node.sourceCapInsetsPx.endPx <= 0 ||
                    node.sourceCapInsetsPx.topPx + node.sourceCapInsetsPx.bottomPx <= 0
            ) {
                issues +=
                    ThemeSceneIssueV1(
                        code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                        path = "$path/source_cap_insets",
                        message = "Nine-slice source cap insets must reserve both axes.",
                    )
            }
        }

        is ThemeScenePathNodeV1 -> {
            validateUnitInterval(node.opacity, "$path/opacity", issues)
            validateNonNegative(node.outlineWidthDp, "$path/outline_width", issues)
        }

        is ThemeSceneTransformNodeV1 -> {
            validateUnitInterval(node.alpha, "$path/alpha", issues)
            if (node.scale <= 0f) {
                issues +=
                    ThemeSceneIssueV1(
                        code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                        path = "$path/scale",
                        message = "Transform scale must be positive.",
                    )
            }
        }

        is ThemeSceneImageNodeV1 -> Unit

        is ThemeSceneTextNodeV1 -> Unit
    }

    childrenOf(node).forEachIndexed { index, child ->
        walkNode(
            node = child,
            path = "$path/${node.nodeId.value}[$index]",
            depth = depth + 1,
            contract = contract,
            state = state,
            issues = issues,
        )
    }
}

private fun childrenOf(node: ThemeSceneNodeV1): List<ThemeSceneNodeV1> =
    when (node) {
        is ThemeSceneStageNodeV1 -> node.children
        is ThemeSceneLayerNodeV1 -> node.children
        is ThemeSceneRowNodeV1 -> node.children
        is ThemeSceneColumnNodeV1 -> node.children
        is ThemeSceneScaffoldNodeV1 ->
            listOfNotNull(node.top, node.content, node.bottom, node.overlay)
        is ThemeSceneGridNodeV1 -> node.children
        is ThemeSceneFrameNodeV1 -> listOf(node.child)
        is ThemeSceneTransformNodeV1 -> listOf(node.child)
        is ThemeSceneSurfaceNodeV1 -> node.child?.let(::listOf) ?: emptyList()
        is ThemeSceneNineSliceNodeV1 -> node.child?.let(::listOf) ?: emptyList()
        is ThemeSceneHostSlotNodeV1,
        is ThemeSceneImageNodeV1,
        is ThemeSceneTextNodeV1,
        is ThemeScenePathNodeV1,
        -> emptyList()
    }

private fun requireChildren(
    children: List<ThemeSceneNodeV1>,
    path: String,
    issues: MutableList<ThemeSceneIssueV1>,
) {
    if (children.isEmpty()) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.EMPTY_CHILDREN,
                path = path,
                message = "Layout node must declare at least one child.",
            )
    }
}

private fun validateAnchor(
    anchor: ThemeSceneAnchorV1,
    path: String,
    issues: MutableList<ThemeSceneIssueV1>,
) {
    listOf(
        "start_x" to anchor.startX,
        "start_y" to anchor.startY,
        "end_x" to anchor.endX,
        "end_y" to anchor.endY,
    ).forEach { (name, value) ->
        validateUnitInterval(value, "$path/anchor/$name", issues)
    }
    if (anchor.startX > anchor.endX || anchor.startY > anchor.endY) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                path = "$path/anchor",
                message = "Anchor start must not exceed anchor end.",
            )
    }
}

private fun validateSize(
    size: ThemeSceneSizeV1,
    path: String,
    issues: MutableList<ThemeSceneIssueV1>,
) {
    if (size is ThemeSceneSizeV1.Fraction && (size.value <= 0f || size.value > 1f)) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                path = path,
                message = "Fraction size must be within (0.0, 1.0].",
            )
    }
}

private fun validateInsets(
    insets: ThemeSceneEdgeInsetsV1,
    path: String,
    issues: MutableList<ThemeSceneIssueV1>,
) {
    listOf(
        "start" to insets.startDp,
        "top" to insets.topDp,
        "end" to insets.endDp,
        "bottom" to insets.bottomDp,
    ).forEach { (name, value) ->
        validateNonNegative(value, "$path/insets/$name", issues)
    }
}

private fun validateNonNegative(
    value: Float,
    path: String,
    issues: MutableList<ThemeSceneIssueV1>,
) {
    if (value < 0f) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                path = path,
                message = "Metric must not be negative: $value",
            )
    }
}

private fun validateUnitInterval(
    value: Float,
    path: String,
    issues: MutableList<ThemeSceneIssueV1>,
) {
    if (value < 0f || value > 1f) {
        issues +=
            ThemeSceneIssueV1(
                code = ThemeSceneIssueCodeV1.INVALID_METRIC,
                path = path,
                message = "Metric must be within [0.0, 1.0]: $value",
            )
    }
}
