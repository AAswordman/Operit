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

@Composable
internal fun renderColumnNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val spacing = props.dp("spacing")
    androidx.compose.foundation.layout.Column(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        verticalArrangement = props.verticalArrangement("verticalArrangement", spacing),
        horizontalAlignment = props.horizontalAlignment("horizontalAlignment"),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val spacing = props.dp("spacing")
    val onClick = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    Row(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver).let { modifier ->
            if (!onClick.isNullOrBlank()) {
                modifier.clickable { onAction(onClick, null) }
            } else {
                modifier
            }
        },
        horizontalArrangement = props.horizontalArrangement("horizontalArrangement", spacing),
        verticalAlignment = props.verticalAlignment("verticalAlignment")
    ) {
        renderSlotChildren(
            node = node,
            slotName = "content",
            onAction = onAction,
            nodePath = nodePath,
            modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) },
            fallbackToChildren = true
        )
    }
}

@Composable
internal fun renderBoxNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.foundation.layout.Box(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        contentAlignment = props.boxAlignment("contentAlignment"),
        propagateMinConstraints = props.bool("propagateMinConstraints", false),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> boxComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderSpacerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    Spacer(
        modifier =
            applyScopedCommonModifier(Modifier, props, modifierResolver)
                .width(props.dp("width"))
                .height(props.dp("height"))
    )
}

@Composable
internal fun renderLazyColumnNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val spacing = props.dp("spacing")
    val reverseLayout = props.bool("reverseLayout", false)
    val autoScrollToEnd = props.bool("autoScrollToEnd", false)
    val listState = rememberLazyListState()
    val contentNodes = node.slotChildren("content", fallbackToChildren = true)
    val autoScrollSignature =
        if (!autoScrollToEnd) {
            0
        } else {
            contentNodes.fold(1) { acc, child -> 31 * acc + child.autoScrollSignature() }
        }

    LaunchedEffect(nodePath, autoScrollToEnd, reverseLayout, autoScrollSignature) {
        if (autoScrollToEnd && contentNodes.isNotEmpty()) {
            listState.scrollToItem(if (reverseLayout) 0 else contentNodes.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        horizontalAlignment = props.horizontalAlignment("horizontalAlignment"),
        reverseLayout = reverseLayout,
        verticalArrangement = props.verticalArrangement("verticalArrangement", spacing),
        contentPadding = PaddingValues(0.dp)
    ) {
        itemsIndexed(
            items = contentNodes,
            key = { index, child -> child.lazyItemRenderKey(nodePath, index) }
        ) { index, child ->
            val childPath = "$nodePath/$index"
            key(child.lazyItemRenderKey(nodePath, index)) {
                renderComposeDslNode(
                    node = child,
                    onAction = onAction,
                    nodePath = childPath
                )
            }
        }
    }
}

@Composable
internal fun renderLazyRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val spacing = props.dp("spacing")
    val contentNodes = node.slotChildren("content", fallbackToChildren = true)
    androidx.compose.foundation.lazy.LazyRow(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        horizontalArrangement = props.horizontalArrangement("horizontalArrangement", spacing),
        verticalAlignment = props.verticalAlignment("verticalAlignment")
    ) {
        itemsIndexed(
            items = contentNodes,
            key = { index, child -> child.lazyItemRenderKey(nodePath, index) }
        ) { index, child ->
            val childPath = "$nodePath/$index"
            key(child.lazyItemRenderKey(nodePath, index)) {
                renderComposeDslNode(
                    node = child,
                    onAction = onAction,
                    nodePath = childPath
                )
            }
        }
    }
}

@Composable
internal fun renderTextNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val resolvedStyle = props.resolvedTextStyle("style")
    val textColor = props.colorOrNull("color")
    Text(
        text = props.string("text"),
        style = resolvedStyle,
        color = textColor ?: Color.Unspecified,
        maxLines = props.int("maxLines", Int.MAX_VALUE),
        softWrap = props.bool("softWrap", true),
        overflow = props.textOverflow("overflow"),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
    )
}

@Composable
internal fun renderTextFieldNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onValueChange"])
    val onTextInputAction = LocalComposeDslTextInputActionHandler.current
    val flushTextInputState = LocalComposeDslFlushTextInputHandler.current
    val label = props.stringOrNull("label")
    val placeholder = props.stringOrNull("placeholder")
    val externalValue = props.string("value")
    val isPassword = props.bool("isPassword", false)
    val textFieldIdentity = props["key"]?.toString()?.trim()?.ifBlank { null } ?: nodePath
    val styleMap = props["style"] as? Map<*, *>
    val hasLabelSlot = node.slotChildren("label").isNotEmpty()
    val hasPlaceholderSlot = node.slotChildren("placeholder").isNotEmpty()
    val hasPrefixSlot = node.slotChildren("prefix").isNotEmpty()
    val hasSuffixSlot = node.slotChildren("suffix").isNotEmpty()
    val hasLeadingIconSlot = node.slotChildren("leadingIcon").isNotEmpty()
    val hasTrailingIconSlot = node.slotChildren("trailingIcon").isNotEmpty()
    val hasSupportingTextSlot = node.slotChildren("supportingText").isNotEmpty()

    var textFieldValue by remember(textFieldIdentity) {
        mutableStateOf(
            TextFieldValue(
                text = externalValue,
                selection = TextRange(externalValue.length)
            )
        )
    }
    var lastAppliedExternalValue by remember(textFieldIdentity) { mutableStateOf(externalValue) }
    var isFocused by remember(textFieldIdentity) { mutableStateOf(false) }

    LaunchedEffect(textFieldIdentity, externalValue, isFocused) {
        if (externalValue == textFieldValue.text) {
            lastAppliedExternalValue = externalValue
            return@LaunchedEffect
        }
        val externalValueChanged = externalValue != lastAppliedExternalValue
        if (isFocused && !externalValueChanged) {
            return@LaunchedEffect
        }
        val start = textFieldValue.selection.start.coerceIn(0, externalValue.length)
        val end = textFieldValue.selection.end.coerceIn(0, externalValue.length)
        textFieldValue =
            TextFieldValue(
                text = externalValue,
                selection = TextRange(start, end)
            )
        lastAppliedExternalValue = externalValue
    }
    val textStyle =
        composeDslTextFieldStyleFromValue(styleMap)
    val textFieldModifier =
        applyScopedCommonModifier(Modifier.fillMaxWidth(), props, modifierResolver)
            .onFocusChanged { focusState ->
                val nextFocused = focusState.isFocused
                if (isFocused && !nextFocused && externalValue != textFieldValue.text) {
                    flushTextInputState()
                }
                isFocused = nextFocused
            }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { nextValue ->
            if (!actionId.isNullOrBlank()) {
                val previousText = textFieldValue.text
                textFieldValue = nextValue
                if (nextValue.text != previousText) {
                    onTextInputAction(actionId, nextValue.text)
                }
            }
        },
        label =
            when {
                hasLabelSlot -> {
                    {
                        renderSlotChildren(
                            node = node,
                            slotName = "label",
                            onAction = onAction,
                            nodePath = nodePath
                        )
                    }
                }
                else -> label?.let { labelText -> { Text(labelText) } }
            },
        placeholder =
            when {
                hasPlaceholderSlot -> {
                    {
                        renderSlotChildren(
                            node = node,
                            slotName = "placeholder",
                            onAction = onAction,
                            nodePath = nodePath
                        )
                    }
                }
                else -> placeholder?.let { placeholderText -> { Text(placeholderText) } }
            },
        prefix =
            if (hasPrefixSlot) {
                {
                    renderSlotChildren(
                        node = node,
                        slotName = "prefix",
                        onAction = onAction,
                        nodePath = nodePath
                    )
                }
            } else null,
        suffix =
            if (hasSuffixSlot) {
                {
                    renderSlotChildren(
                        node = node,
                        slotName = "suffix",
                        onAction = onAction,
                        nodePath = nodePath
                    )
                }
            } else null,
        leadingIcon =
            if (hasLeadingIconSlot) {
                {
                    renderSlotChildren(
                        node = node,
                        slotName = "leadingIcon",
                        onAction = onAction,
                        nodePath = nodePath
                    )
                }
            } else null,
        trailingIcon =
            if (hasTrailingIconSlot) {
                {
                    renderSlotChildren(
                        node = node,
                        slotName = "trailingIcon",
                        onAction = onAction,
                        nodePath = nodePath
                    )
                }
            } else null,
        supportingText =
            if (hasSupportingTextSlot) {
                {
                    renderSlotChildren(
                        node = node,
                        slotName = "supportingText",
                        onAction = onAction,
                        nodePath = nodePath
                    )
                }
            } else null,
        singleLine = props.bool("singleLine", false),
        minLines = props.int("minLines", 1),
        maxLines = props.int("maxLines", if (props.bool("singleLine", false)) 1 else Int.MAX_VALUE),
        readOnly = props.bool("readOnly", false),
        isError = props.bool("isError", false),
        textStyle = textStyle ?: androidx.compose.ui.text.TextStyle.Default,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        modifier = textFieldModifier
    )
}

@Composable
internal fun renderSwitchNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    val checkedThumbColor = props.colorOrNull("checkedThumbColor")
    val checkedTrackColor = props.colorOrNull("checkedTrackColor")
    val uncheckedThumbColor = props.colorOrNull("uncheckedThumbColor")
    val uncheckedTrackColor = props.colorOrNull("uncheckedTrackColor")
    val hasThumbContentSlot = node.slotChildren("thumbContent").isNotEmpty()
    val switchColors =
        if (
            checkedThumbColor != null ||
                checkedTrackColor != null ||
                uncheckedThumbColor != null ||
                uncheckedTrackColor != null
        ) {
            androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = checkedThumbColor ?: MaterialTheme.colorScheme.primary,
                checkedTrackColor = checkedTrackColor ?: MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = uncheckedThumbColor ?: MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = uncheckedTrackColor ?: MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            androidx.compose.material3.SwitchDefaults.colors()
        }
    Switch(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!actionId.isNullOrBlank()) {
                onAction(actionId, checked)
            }
        },
        enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        thumbContent =
            if (hasThumbContentSlot) {
                {
                    renderSlotChildren(
                        node = node,
                        slotName = "thumbContent",
                        onAction = onAction,
                        nodePath = nodePath
                    )
                }
            } else null,
        colors = switchColors
    )
}

@Composable
internal fun renderCheckboxNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    Checkbox(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!actionId.isNullOrBlank()) {
                onAction(actionId, checked)
            }
        },
        enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
    )
}

@Composable
internal fun renderButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val contentColor = props.colorOrNull("contentColor")
    val disabledContainerColor = props.colorOrNull("disabledContainerColor")
    val disabledContentColor = props.colorOrNull("disabledContentColor")
    val buttonColors =
        if (
            containerColor != null ||
                contentColor != null ||
                disabledContainerColor != null ||
                disabledContentColor != null
        ) {
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = containerColor ?: Color.Unspecified,
                contentColor = contentColor ?: Color.Unspecified,
                disabledContainerColor = disabledContainerColor ?: Color.Unspecified,
                disabledContentColor = disabledContentColor ?: Color.Unspecified
            )
        } else {
            androidx.compose.material3.ButtonDefaults.buttonColors()
        }
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.Button(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.shape,
        colors = buttonColors,
        contentPadding = props.paddingValuesOrNull("contentPadding") ?: androidx.compose.material3.ButtonDefaults.ContentPadding,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                Text(props.string("text", "Button"))
            }
        }
    )
}

@Composable
internal fun renderIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.IconButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                val iconName = props.string("icon", props.string("name", "info"))
                Icon(
                    imageVector = iconFromName(iconName),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
internal fun renderCardNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val containerAlpha = props.floatOrNull("containerAlpha")
    val alpha = props.floatOrNull("alpha")
    val contentColor = props.colorOrNull("contentColor")
    val contentAlpha = props.floatOrNull("contentAlpha")
    val finalContainerColor = containerColor?.let { color ->
        when {
            containerAlpha != null -> color.copy(alpha = containerAlpha)
            alpha != null -> color.copy(alpha = alpha)
            else -> color
        }
    }
    val finalContentColor = contentColor?.let { color ->
        if (contentAlpha != null) color.copy(alpha = contentAlpha) else color
    }
    val cardColors =
        when {
            finalContainerColor != null && finalContentColor != null ->
                CardDefaults.cardColors(
                    containerColor = finalContainerColor,
                    contentColor = finalContentColor
                )
            finalContainerColor != null ->
                CardDefaults.cardColors(containerColor = finalContainerColor)
            finalContentColor != null ->
                CardDefaults.cardColors(contentColor = finalContentColor)
            else -> CardDefaults.cardColors()
        }
    Card(
        colors = cardColors,
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: CardDefaults.shape,
        border = props.borderOrNull(),
        elevation = CardDefaults.cardElevation(defaultElevation = props.dp("elevation", 1.dp))
    ) {
        renderSlotChildren(
            node = node,
            slotName = "content",
            onAction = onAction,
            nodePath = nodePath,
            fallbackToChildren = true
        )
    }
}

@Composable
internal fun renderMaterialThemeNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.MaterialTheme(
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderSurfaceNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClick = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    val contentPadding = props.commonPaddingSpecOrNull()
    val modifierProps = if (contentPadding != null) props.withoutCommonPaddingProps() else props
    val resolvedModifier =
        applyScopedCommonModifier(Modifier, modifierProps, modifierResolver).let { modifier ->
            if (!onClick.isNullOrBlank()) {
                modifier.clickable { onAction(onClick, null) }
            } else {
                modifier
            }
        }
    androidx.compose.material3.Surface(
        modifier = resolvedModifier,
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        color = (props.colorOrNull("color") ?: props.colorOrNull("containerColor")).let { baseColor -> baseColor?.let { color -> props.floatOrNull("alpha")?.let { color.copy(alpha = it) } ?: color } ?: Color.Transparent },
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        tonalElevation = props.dp("tonalElevation"),
        shadowElevation = props.dp("shadowElevation"),
        content = {
            Box(modifier = contentPadding?.applyTo(Modifier) ?: Modifier) {
                renderSlotChildren(
                    node = node,
                    slotName = "content",
                    onAction = onAction,
                    nodePath = nodePath,
                    modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                    fallbackToChildren = true
                )
            }
        }
    )
}

@Composable
internal fun renderIconNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val iconName = props.string("name", props.string("icon", "info"))
    val tint = props.colorOrNull("tint") ?: MaterialTheme.colorScheme.onSurfaceVariant
    val size = props.floatOrNull("size")
    Icon(
        imageVector = iconFromName(iconName),
        contentDescription = null,
        tint = tint,
        modifier = if (size != null) {
            applyScopedCommonModifier(Modifier, props, modifierResolver).width(size.dp).height(size.dp)
        } else {
            applyScopedCommonModifier(Modifier, props, modifierResolver)
        }
    )
}

@Composable
internal fun renderLinearProgressIndicatorNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val progress = props.floatOrNull("progress")
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = applyScopedCommonModifier(Modifier.fillMaxWidth(), props, modifierResolver)
        )
    } else {
        LinearProgressIndicator(
            modifier = applyScopedCommonModifier(Modifier.fillMaxWidth(), props, modifierResolver)
        )
    }
}

@Composable
internal fun renderCircularProgressIndicatorNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val strokeWidth = props.floatOrNull("strokeWidth")
    val color = props.colorOrNull("color")
    CircularProgressIndicator(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        strokeWidth = if (strokeWidth != null) strokeWidth.dp else 4.dp,
        color = color ?: MaterialTheme.colorScheme.primary
    )
}

@Composable
internal fun renderSnackbarHostNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    Spacer(modifier = applyScopedCommonModifier(Modifier, node.props, modifierResolver))
}

@Composable
internal fun renderAssistChipNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.AssistChip(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        leadingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "leadingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        trailingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "trailingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderBadgeNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.Badge(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderBadgedBoxNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.BadgedBox(
        badge = {
            renderSlotChildren(
                node = node,
                slotName = "badge",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> boxComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> boxComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderDismissibleDrawerSheetNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.DismissibleDrawerSheet(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        drawerContainerColor = props.colorOrNull("drawerContainerColor") ?: Color.Unspecified,
        drawerContentColor = props.colorOrNull("drawerContentColor") ?: Color.Unspecified,
        drawerTonalElevation = props.dp("drawerTonalElevation"),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderDismissibleNavigationDrawerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.DismissibleNavigationDrawer(
        drawerContent = {
            renderSlotChildren(
                node = node,
                slotName = "drawerContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        gesturesEnabled = props.bool("gesturesEnabled", false),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderDividerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.Divider(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        thickness = props.dp("thickness"),
        color = props.colorOrNull("color") ?: Color.Unspecified
    )
}

@Composable
internal fun renderDropdownMenuNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onDismissRequestActionId = ToolPkgComposeDslParser.extractActionId(props["onDismissRequest"])
    androidx.compose.material3.DropdownMenu(
        expanded = props.bool("expanded", false),
        onDismissRequest = {
            if (!onDismissRequestActionId.isNullOrBlank()) {
                onAction(onDismissRequestActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        offset = DpOffset(props.dp("offset"), 0.dp),
        properties = popupPropertiesFromValue(props["properties"]),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderElevatedAssistChipNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ElevatedAssistChip(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        leadingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "leadingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        trailingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "trailingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderElevatedButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val contentColor = props.colorOrNull("contentColor")
    val disabledContainerColor = props.colorOrNull("disabledContainerColor")
    val disabledContentColor = props.colorOrNull("disabledContentColor")
    val buttonColors =
        if (
            containerColor != null ||
                contentColor != null ||
                disabledContainerColor != null ||
                disabledContentColor != null
        ) {
            androidx.compose.material3.ButtonDefaults.elevatedButtonColors(
                containerColor = containerColor ?: Color.Unspecified,
                contentColor = contentColor ?: Color.Unspecified,
                disabledContainerColor = disabledContainerColor ?: Color.Unspecified,
                disabledContentColor = disabledContentColor ?: Color.Unspecified
            )
        } else {
            androidx.compose.material3.ButtonDefaults.elevatedButtonColors()
        }
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ElevatedButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.elevatedShape,
        colors = buttonColors,
        contentPadding = props.paddingValuesOrNull("contentPadding") ?: androidx.compose.material3.ButtonDefaults.ContentPadding,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                Text(props.string("text", "ElevatedButton"))
            }
        }
    )
}

@Composable
internal fun renderElevatedCardNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.ElevatedCard(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderElevatedFilterChipNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ElevatedFilterChip(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        leadingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "leadingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        trailingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "trailingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderElevatedSuggestionChipNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ElevatedSuggestionChip(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        icon = {
            renderSlotChildren(
                node = node,
                slotName = "icon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderExtendedFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderFilledIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FilledIconButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.filledShape,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                val iconName = props.string("icon", props.string("name", "info"))
                Icon(
                    imageVector = iconFromName(iconName),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
internal fun renderFilledIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.FilledIconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.filledShape,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderFilledTonalButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val contentColor = props.colorOrNull("contentColor")
    val disabledContainerColor = props.colorOrNull("disabledContainerColor")
    val disabledContentColor = props.colorOrNull("disabledContentColor")
    val buttonColors =
        if (
            containerColor != null ||
                contentColor != null ||
                disabledContainerColor != null ||
                disabledContentColor != null
        ) {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor ?: Color.Unspecified,
                contentColor = contentColor ?: Color.Unspecified,
                disabledContainerColor = disabledContainerColor ?: Color.Unspecified,
                disabledContentColor = disabledContentColor ?: Color.Unspecified
            )
        } else {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
        }
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FilledTonalButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.filledTonalShape,
        colors = buttonColors,
        contentPadding = props.paddingValuesOrNull("contentPadding") ?: androidx.compose.material3.ButtonDefaults.ContentPadding,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                Text(props.string("text", "FilledTonalButton"))
            }
        }
    )
}

@Composable
internal fun renderFilledTonalIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FilledTonalIconButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.filledShape,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                val iconName = props.string("icon", props.string("name", "info"))
                Icon(
                    imageVector = iconFromName(iconName),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
internal fun renderFilledTonalIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.FilledTonalIconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.filledShape,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderFilterChipNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FilterChip(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        leadingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "leadingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        trailingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "trailingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderHorizontalDividerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.HorizontalDivider(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        thickness = props.dp("thickness"),
        color = props.colorOrNull("color") ?: Color.Unspecified
    )
}

@Composable
internal fun renderIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.IconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                val iconName = props.string("icon", props.string("name", "info"))
                Icon(
                    imageVector = iconFromName(iconName),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
internal fun renderInputChipNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.InputChip(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        leadingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "leadingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        avatar = {
            renderSlotChildren(
                node = node,
                slotName = "avatar",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        trailingIcon = {
            renderSlotChildren(
                node = node,
                slotName = "trailingIcon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderLargeFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.LargeFloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderLeadingIconTabNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.LeadingIconTab(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        text = {
            renderSlotChildren(
                node = node,
                slotName = "text",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        icon = {
            renderSlotChildren(
                node = node,
                slotName = "icon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        selectedContentColor = props.colorOrNull("selectedContentColor") ?: Color.Unspecified,
        unselectedContentColor = props.colorOrNull("unselectedContentColor") ?: Color.Unspecified
    )
}

@Composable
internal fun renderListItemNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.ListItem(
        headlineContent = {
            renderSlotChildren(
                node = node,
                slotName = "headlineContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        overlineContent = {
            renderSlotChildren(
                node = node,
                slotName = "overlineContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        supportingContent = {
            renderSlotChildren(
                node = node,
                slotName = "supportingContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        leadingContent = {
            renderSlotChildren(
                node = node,
                slotName = "leadingContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        trailingContent = {
            renderSlotChildren(
                node = node,
                slotName = "trailingContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        tonalElevation = props.dp("tonalElevation"),
        shadowElevation = props.dp("shadowElevation")
    )
}

@Composable
internal fun renderModalDrawerSheetNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.ModalDrawerSheet(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        drawerContainerColor = props.colorOrNull("drawerContainerColor") ?: Color.Unspecified,
        drawerContentColor = props.colorOrNull("drawerContentColor") ?: Color.Unspecified,
        drawerTonalElevation = props.dp("drawerTonalElevation"),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderModalNavigationDrawerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.ModalNavigationDrawer(
        drawerContent = {
            renderSlotChildren(
                node = node,
                slotName = "drawerContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        gesturesEnabled = props.bool("gesturesEnabled", false),
        scrimColor = props.colorOrNull("scrimColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderModalWideNavigationRailNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val spacing = props.dp("spacing")
    androidx.compose.material3.ModalWideNavigationRail(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        hideOnCollapse = props.bool("hideOnCollapse", false),
        header = {
            renderSlotChildren(
                node = node,
                slotName = "header",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        expandedHeaderTopPadding = props.dp("expandedHeaderTopPadding"),
        arrangement = props.verticalArrangement("verticalArrangement", spacing),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderNavigationBarNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.NavigationBar(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        tonalElevation = props.dp("tonalElevation"),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderNavigationDrawerItemNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.NavigationDrawerItem(
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        icon = {
            renderSlotChildren(
                node = node,
                slotName = "icon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        badge = {
            renderSlotChildren(
                node = node,
                slotName = "badge",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderNavigationRailNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.NavigationRail(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        header = {
            renderSlotChildren(
                node = node,
                slotName = "header",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderNavigationRailItemNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.NavigationRailItem(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        icon = {
            renderSlotChildren(
                node = node,
                slotName = "icon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        alwaysShowLabel = props.bool("alwaysShowLabel", false)
    )
}

@Composable
internal fun renderOutlinedButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val contentColor = props.colorOrNull("contentColor")
    val disabledContainerColor = props.colorOrNull("disabledContainerColor")
    val disabledContentColor = props.colorOrNull("disabledContentColor")
    val buttonColors =
        if (
            containerColor != null ||
                contentColor != null ||
                disabledContainerColor != null ||
                disabledContentColor != null
        ) {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor ?: Color.Unspecified,
                contentColor = contentColor ?: Color.Unspecified,
                disabledContainerColor = disabledContainerColor ?: Color.Unspecified,
                disabledContentColor = disabledContentColor ?: Color.Unspecified
            )
        } else {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
        }
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.OutlinedButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.outlinedShape,
        colors = buttonColors,
        contentPadding = props.paddingValuesOrNull("contentPadding") ?: androidx.compose.material3.ButtonDefaults.ContentPadding,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                Text(props.string("text", "OutlinedButton"))
            }
        }
    )
}

@Composable
internal fun renderOutlinedCardNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.OutlinedCard(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderOutlinedIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.OutlinedIconButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.outlinedShape,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                val iconName = props.string("icon", props.string("name", "info"))
                Icon(
                    imageVector = iconFromName(iconName),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
internal fun renderOutlinedIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.OutlinedIconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.outlinedShape,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderPermanentDrawerSheetNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.PermanentDrawerSheet(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        drawerContainerColor = props.colorOrNull("drawerContainerColor") ?: Color.Unspecified,
        drawerContentColor = props.colorOrNull("drawerContentColor") ?: Color.Unspecified,
        drawerTonalElevation = props.dp("drawerTonalElevation"),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderPermanentNavigationDrawerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.PermanentNavigationDrawer(
        drawerContent = {
            renderSlotChildren(
                node = node,
                slotName = "drawerContent",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderPrimaryScrollableTabRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.PrimaryScrollableTabRow(
        selectedTabIndex = props.int("selectedTabIndex", 0),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        edgePadding = props.dp("edgePadding"),
        indicator = {
            renderSlotChildren(
                node = node,
                slotName = "indicator",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        divider = {
            renderSlotChildren(
                node = node,
                slotName = "divider",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        tabs = {
            renderSlotChildren(
                node = node,
                slotName = "tabs",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        }
    )
}

@Composable
internal fun renderPrimaryTabRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.PrimaryTabRow(
        selectedTabIndex = props.int("selectedTabIndex", 0),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        indicator = {
            renderSlotChildren(
                node = node,
                slotName = "indicator",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        divider = {
            renderSlotChildren(
                node = node,
                slotName = "divider",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        tabs = {
            renderSlotChildren(
                node = node,
                slotName = "tabs",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        }
    )
}

@Composable
internal fun renderProvideTextStyleNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.ProvideTextStyle(
        value = props.textStyle("style"),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderPullToRefreshBoxNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onRefreshActionId = ToolPkgComposeDslParser.extractActionId(props["onRefresh"])
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = props.bool("isRefreshing", false),
        onRefresh = {
            if (!onRefreshActionId.isNullOrBlank()) {
                onAction(onRefreshActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        contentAlignment = props.boxAlignment("contentAlignment"),
        indicator = {
            renderSlotChildren(
                node = node,
                slotName = "indicator",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> boxComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> boxComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderRadioButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.RadioButton(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true)
    )
}

@Composable
internal fun renderScaffoldNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.Scaffold(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        topBar = {
            renderSlotChildren(
                node = node,
                slotName = "topBar",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        bottomBar = {
            renderSlotChildren(
                node = node,
                slotName = "bottomBar",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        snackbarHost = {
            renderSlotChildren(
                node = node,
                slotName = "snackbarHost",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        floatingActionButton = {
            renderSlotChildren(
                node = node,
                slotName = "floatingActionButton",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                renderSlotChildren(
                    node = node,
                    slotName = "content",
                    onAction = onAction,
                    nodePath = nodePath,
                    fallbackToChildren = true
                )
            }
        }
    )
}

@Composable
internal fun renderSecondaryScrollableTabRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.SecondaryScrollableTabRow(
        selectedTabIndex = props.int("selectedTabIndex", 0),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        edgePadding = props.dp("edgePadding"),
        indicator = {
            renderSlotChildren(
                node = node,
                slotName = "indicator",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        divider = {
            renderSlotChildren(
                node = node,
                slotName = "divider",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        tabs = {
            renderSlotChildren(
                node = node,
                slotName = "tabs",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        }
    )
}

@Composable
internal fun renderSecondaryTabRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.SecondaryTabRow(
        selectedTabIndex = props.int("selectedTabIndex", 0),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        indicator = {
            renderSlotChildren(
                node = node,
                slotName = "indicator",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        divider = {
            renderSlotChildren(
                node = node,
                slotName = "divider",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        tabs = {
            renderSlotChildren(
                node = node,
                slotName = "tabs",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        }
    )
}

@Composable
internal fun renderShortNavigationBarNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.ShortNavigationBar(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderShortNavigationBarItemNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ShortNavigationBarItem(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        icon = {
            renderSlotChildren(
                node = node,
                slotName = "icon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true)
    )
}

@Composable
internal fun renderSmallFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.SmallFloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderSnackbarNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.Snackbar(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        action = {
            renderSlotChildren(
                node = node,
                slotName = "action",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        dismissAction = {
            renderSlotChildren(
                node = node,
                slotName = "dismissAction",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        actionOnNewLine = props.bool("actionOnNewLine", false),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        actionContentColor = props.colorOrNull("actionContentColor") ?: Color.Unspecified,
        dismissActionContentColor = props.colorOrNull("dismissActionContentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderSuggestionChipNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.SuggestionChip(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        icon = {
            renderSlotChildren(
                node = node,
                slotName = "icon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    )
}

@Composable
internal fun renderTabNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.Tab(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        selectedContentColor = props.colorOrNull("selectedContentColor") ?: Color.Unspecified,
        unselectedContentColor = props.colorOrNull("unselectedContentColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderTextButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val contentColor = props.colorOrNull("contentColor")
    val disabledContainerColor = props.colorOrNull("disabledContainerColor")
    val disabledContentColor = props.colorOrNull("disabledContentColor")
    val buttonColors =
        if (
            containerColor != null ||
                contentColor != null ||
                disabledContainerColor != null ||
                disabledContentColor != null
        ) {
            androidx.compose.material3.ButtonDefaults.textButtonColors(
                containerColor = containerColor ?: Color.Unspecified,
                contentColor = contentColor ?: Color.Unspecified,
                disabledContainerColor = disabledContainerColor ?: Color.Unspecified,
                disabledContentColor = disabledContentColor ?: Color.Unspecified
            )
        } else {
            androidx.compose.material3.ButtonDefaults.textButtonColors()
        }
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.TextButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.textShape,
        colors = buttonColors,
        contentPadding = props.paddingValuesOrNull("contentPadding") ?: androidx.compose.material3.ButtonDefaults.ContentPadding,
        content = {
            val slotNodes = node.slotChildren("content", fallbackToChildren = true)
            if (slotNodes.isNotEmpty()) {
                renderComposeDslNodes(
                    nodes = slotNodes,
                    onAction = onAction,
                    nodePath = "$nodePath:content",
                    modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) }
                )
            } else {
                Text(props.string("text", "TextButton"))
            }
        }
    )
}

@Composable
internal fun renderTimePickerDialogNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onDismissRequestActionId = ToolPkgComposeDslParser.extractActionId(props["onDismissRequest"])
    androidx.compose.material3.TimePickerDialog(
        onDismissRequest = {
            if (!onDismissRequestActionId.isNullOrBlank()) {
                onAction(onDismissRequestActionId, null)
            }
        },
        confirmButton = {
            renderSlotChildren(
                node = node,
                slotName = "confirmButton",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        title = {
            renderSlotChildren(
                node = node,
                slotName = "title",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        modeToggleButton = {
            renderSlotChildren(
                node = node,
                slotName = "modeToggleButton",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        dismissButton = {
            renderSlotChildren(
                node = node,
                slotName = "dismissButton",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> columnComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderVerticalDividerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.VerticalDivider(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        thickness = props.dp("thickness"),
        color = props.colorOrNull("color") ?: Color.Unspecified
    )
}

@Composable
internal fun renderVerticalDragHandleNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.material3.VerticalDragHandle(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
    )
}

@Composable
internal fun renderWideNavigationRailNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val spacing = props.dp("spacing")
    androidx.compose.material3.WideNavigationRail(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        header = {
            renderSlotChildren(
                node = node,
                slotName = "header",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        arrangement = props.verticalArrangement("verticalArrangement", spacing),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderWideNavigationRailItemNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.WideNavigationRailItem(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        icon = {
            renderSlotChildren(
                node = node,
                slotName = "icon",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        label = {
            renderSlotChildren(
                node = node,
                slotName = "label",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = false
            )
        },
        railExpanded = props.bool("railExpanded", false),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        enabled = props.bool("enabled", true)
    )
}

@Composable
internal fun renderBoxWithConstraintsNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        contentAlignment = props.boxAlignment("contentAlignment"),
        propagateMinConstraints = props.bool("propagateMinConstraints", false),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderBasicTextNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val onTextLayoutActionId = ToolPkgComposeDslParser.extractActionId(props["onTextLayout"])
    androidx.compose.foundation.text.BasicText(
        text = props.string("text"),
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        style = props.resolvedTextStyle("style", includeColor = true),
        softWrap = props.bool("softWrap", false),
        maxLines = props.int("maxLines", 0),
        overflow = props.textOverflow("overflow"),
        onTextLayout = {
            if (!onTextLayoutActionId.isNullOrBlank()) {
                onAction(onTextLayoutActionId, null)
            }
        }
    )
}

@Composable
internal fun renderDisableSelectionNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.foundation.text.selection.DisableSelection(
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}

@Composable
internal fun renderImageNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    val imageModel = props.imageModelOrNull()
    val alignment = props.boxAlignment("contentAlignment")
    val alpha = props.floatOrNull("alpha") ?: 1f
    val contentScale = props.contentScale("contentScale")
    val modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
    if (imageModel != null) {
        androidx.compose.foundation.Image(
            painter = rememberAsyncImagePainter(model = imageModel),
            contentDescription = props.stringOrNull("contentDescription"),
            modifier = modifier,
            alignment = alignment,
            alpha = alpha,
            contentScale = contentScale
        )
    } else {
        androidx.compose.foundation.Image(
            painter = rememberVectorPainter(iconFromName(props.string("name", props.string("icon", "info")))),
            contentDescription = props.stringOrNull("contentDescription"),
            modifier = modifier,
            alignment = alignment,
            alpha = alpha,
            contentScale = contentScale
        )
    }
}

@Composable
internal fun renderSelectionContainerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String,
    modifierResolver: ComposeDslModifierResolver
) {
    val props = node.props
    androidx.compose.foundation.text.selection.SelectionContainer(
        modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
        content = {
            renderSlotChildren(
                node = node,
                slotName = "content",
                onAction = onAction,
                nodePath = nodePath,
                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                fallbackToChildren = true
            )
        }
    )
}
