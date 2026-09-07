package com.ai.assistance.operit.ui.common.composedsl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser

/**
 * AUTO-GENERATED from Compose Material3/Foundation component bindings.
 * Do not edit manually. Regenerate via tools/compose_dsl/generate_compose_dsl_artifacts.py.
 */
@Composable
internal fun defaultComposeDslModifierResolver(
    base: Modifier,
    props: Map<String, Any?>
): Modifier {
    return applyCommonModifier(base, props)
}

@Composable
internal fun RowScope.rowComposeDslModifierResolver(
    base: Modifier,
    props: Map<String, Any?>
): Modifier {
    var modifier = applyCommonModifier(base, props)
    val weightSpec = props.modifierWeightSpecOrNull()
    if (weightSpec != null) {
        modifier = modifier.weight(weightSpec.weight, weightSpec.fill)
    }
    val alignToken = props.scopeAlignToken()
    if (alignToken != null) {
        modifier = modifier.align(verticalAlignmentFromToken(alignToken))
    }
    return modifier
}

@Composable
internal fun ColumnScope.columnComposeDslModifierResolver(
    base: Modifier,
    props: Map<String, Any?>
): Modifier {
    var modifier = applyCommonModifier(base, props)
    val weightSpec = props.modifierWeightSpecOrNull()
    if (weightSpec != null) {
        modifier = modifier.weight(weightSpec.weight, weightSpec.fill)
    }
    val alignToken = props.scopeAlignToken()
    if (alignToken != null) {
        modifier = modifier.align(horizontalAlignmentFromToken(alignToken))
    }
    return modifier
}

@Composable
internal fun BoxScope.boxComposeDslModifierResolver(
    base: Modifier,
    props: Map<String, Any?>
): Modifier {
    var modifier = applyCommonModifier(base, props)
    if (props.hasModifierOp("matchparentsize")) {
        modifier = modifier.matchParentSize()
    }
    val alignToken = props.scopeAlignToken()
    if (alignToken != null) {
        modifier = modifier.align(boxAlignmentFromToken(alignToken))
    }
    return modifier
}

@Composable
internal fun applyScopedCommonModifier(
    base: Modifier,
    props: Map<String, Any?>,
    modifierResolver: ComposeDslModifierResolver
): Modifier {
    return applyComposeDslNodeDebugLayoutModifier(modifierResolver(base, props))
}

@Composable
internal fun renderComposeDslNodes(
    nodes: List<ToolPkgComposeDslNode>,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver = { base, props ->
        defaultComposeDslModifierResolver(base, props)
    }
) {
    nodes.forEachIndexed { index, child ->
        val childPath = "$nodePath/$index"
        renderComposeDslNode(
            node = child,
            onAction = onAction,
            nodePath = childPath,
            modifierResolver = modifierResolver
        )
    }
}

@Composable
internal fun renderNodeChildren(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver = { base, props ->
        defaultComposeDslModifierResolver(base, props)
    }
) {
    renderComposeDslNodes(node.children, onAction, nodePath, modifierResolver)
}

private fun ToolPkgComposeDslNode.slotChildren(
    slotName: String,
    fallbackToChildren: Boolean = false
): List<ToolPkgComposeDslNode> {
    val normalizedSlotName = slotName.trim()
    val slotNodes =
        if (normalizedSlotName.isBlank()) {
            emptyList()
        } else {
            slots[normalizedSlotName].orEmpty()
        }
    if (slotNodes.isNotEmpty()) {
        return slotNodes
    }
    return if (fallbackToChildren) children else emptyList()
}

private fun ToolPkgComposeDslNode.lazyItemRenderKey(
    parentNodePath: String,
    index: Int
): String {
    val explicitKey = props["key"]?.toString()?.trim()?.ifBlank { null }
    if (explicitKey != null) {
        return "$parentNodePath:key:$explicitKey"
    }
    return "$parentNodePath/$index:${normalizeToken(type)}"
}

@Composable
internal fun renderSlotChildren(
    node: ToolPkgComposeDslNode,
    slotName: String,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver = { base, props ->
        defaultComposeDslModifierResolver(base, props)
    },
    fallbackToChildren: Boolean = false
) {
    val slotNodes = node.slotChildren(slotName, fallbackToChildren)
    renderComposeDslNodes(slotNodes, onAction, "$nodePath:$slotName", modifierResolver)
}

@Composable
internal fun RowScope.renderRowScopeNodeChildren(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    renderNodeChildren(
        node = node,
        onAction = onAction,
        nodePath = nodePath,
        modifierResolver = { base, props -> rowComposeDslModifierResolver(base, props) }
    )
}

@Composable
internal fun ColumnScope.renderColumnScopeNodeChildren(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    renderNodeChildren(
        node = node,
        onAction = onAction,
        nodePath = nodePath,
        modifierResolver = { base, props -> columnComposeDslModifierResolver(base, props) }
    )
}

@Composable
internal fun BoxScope.renderBoxScopeNodeChildren(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    renderNodeChildren(
        node = node,
        onAction = onAction,
        nodePath = nodePath,
        modifierResolver = { base, props -> boxComposeDslModifierResolver(base, props) }
    )
}

private fun ToolPkgComposeDslNode.autoScrollSignature(): Int {
    var result = type.hashCode()
    result = 31 * result + (props["key"]?.hashCode() ?: 0)
    result = 31 * result + (props["text"]?.hashCode() ?: 0)
    result = 31 * result + (props["value"]?.hashCode() ?: 0)
    result = 31 * result + children.size
    children.forEach { child ->
        result = 31 * result + child.autoScrollSignature()
    }
    result = 31 * result + slots.size
    slots.toSortedMap().forEach { (slotName, slotChildren) ->
        result = 31 * result + slotName.hashCode()
        result = 31 * result + slotChildren.size
        slotChildren.forEach { child ->
            result = 31 * result + child.autoScrollSignature()
        }
    }
    return result
}

// __GENERATED_COMPONENT_RENDERERS__
