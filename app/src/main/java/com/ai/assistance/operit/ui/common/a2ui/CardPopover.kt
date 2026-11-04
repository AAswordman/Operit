package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class CardPopoverState {
    var entry by mutableStateOf<Entry?>(null)
    var bounds by mutableStateOf(Rect.Zero)
    var visibleBounds by mutableStateOf(Rect.Zero)
    var revealedEntry: Entry? = null
    var revealJob: Job? = null
    data class Entry(val owner: Any, val anchor: () -> Rect, val locals: CompositionLocalContext,
        val title: String, val content: @Composable () -> Unit)
}
private val LocalCardPopover = compositionLocalOf<CardPopoverState?> { null }

/** An overlay inside the card's existing Android window, including floating chats. */
@Composable
internal fun CardPopoverHost(content: @Composable () -> Unit) {
    val state = remember { CardPopoverState() }
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val entry = state.entry
    DisposableEffect(entry) { onDispose { state.revealJob?.cancel() } }
    val density = LocalDensity.current
    val enabled = LocalCardBrowse.current
    val interactive = LocalCardInteractive.current
    val readOnly = LocalCardReadOnly.current
    // A captured A2UI context must never keep an obsolete permission state alive.
    LaunchedEffect(enabled, interactive, readOnly) { state.entry = null }
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(enabled = entry != null) { state.entry = null }
    CompositionLocalProvider(LocalCardPopover provides state) {
        Box(Modifier.fillMaxWidth().heightIn(min = if (entry != null) 280.dp else 0.dp)
            .bringIntoViewRequester(requester)
            .onGloballyPositioned {
                state.bounds = Rect(it.positionInRoot(), androidx.compose.ui.geometry.Size(it.size.width.toFloat(),it.size.height.toFloat()))
                state.visibleBounds = it.boundsInRoot()
                state.entry?.let { current ->
                    if (state.revealedEntry !== current) {
                        state.revealedEntry = current
                        // Reveal the planned panel, not the clipped visible sliver or the entire long card.
                        val host = state.bounds
                        val anchor = current.anchor()
                        val height = minOf(host.height, with(density) { 240.dp.toPx() })
                        val below = anchor.bottom-host.top
                        val top = (if (below+height <= host.height) below else anchor.top-host.top-height)
                            .coerceIn(0f,(host.height-height).coerceAtLeast(0f))
                        val width = minOf(host.width, with(density) { 320.dp.toPx() })
                        val left = (anchor.left-host.left).coerceIn(0f,(host.width-width).coerceAtLeast(0f))
                        state.revealJob = scope.launch {
                            if (state.entry === current) requester.bringIntoView(Rect(left,top,left+width,top+height))
                        }
                    }
                }
            }) {
            content()
            if (entry != null) {
                Box(Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { state.entry = null }))
                val anchor = entry.anchor()
                val host = state.bounds
                val visible = state.visibleBounds
                val width = with(density) { host.width.toDp().coerceAtMost(320.dp) }
                val height = minOf(with(density) { 240.dp.toPx() },visible.height.coerceAtLeast(0f))
                val x = (anchor.left-host.left).coerceIn(0f, (host.width-with(density){width.toPx()}).coerceAtLeast(0f))
                val below = anchor.bottom-host.top
                val topLimit = (visible.top-host.top).coerceAtLeast(0f)
                val bottomLimit = (visible.bottom-host.top-height).coerceAtLeast(topLimit)
                val y = (if (below+height <= visible.bottom-host.top) below else anchor.top-host.top-height)
                    .coerceIn(topLimit,bottomLimit)
                CompositionLocalProvider(entry.locals) {
                CompositionLocalProvider(LocalCardInteractive provides interactive,
                    LocalCardReadOnly provides readOnly, LocalCardBrowse provides enabled,
                    LocalCardRowItem provides false) {
                    Surface(Modifier.offset { IntOffset(x.roundToInt(),y.roundToInt()) }.width(width)
                        .heightIn(max=with(density){height.toDp()})
                        .clickable(interactionSource=remember { MutableInteractionSource() },indication=null) {},
                        shape=MaterialTheme.shapes.medium,
                        color=MaterialTheme.colorScheme.surface, shadowElevation=6.dp,
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                                Text(entry.title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,
                                    maxLines=2,overflow=TextOverflow.Ellipsis)
                                IconButton(onClick={state.entry=null}) {
                                    Icon(Icons.Outlined.Close,stringResource(R.string.a2ui_close_details))
                                }
                            }
                            Box(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) { entry.content() }
                        }
                    }
                }
                }
            }
        }
    }
}

internal object CardPopover : A2uiComponent {
    override val name = "Popover"
    override val description = "Anchored details, contained inside this card. Opening is local."
    private val label = A2uiProperty.dynamicString("label")
    private val child = A2uiProperty.dynamicString("child")
    override val properties = listOf(label,child)
    @Composable
    override fun A2uiComponentScope.Content(properties:A2uiComponentProperties, modifier:Modifier) {
        val state=LocalCardPopover.current
        val title=properties.bind(label).orEmpty()
        val childId=properties.bind(child) ?: return
        val owner=remember { Any() }
        var anchor by remember { mutableStateOf(Rect.Zero) }
        val locals=currentCompositionLocalContext
        DisposableEffect(state,owner) { onDispose { if(state?.entry?.owner===owner) state.entry=null } }
        TextButton(modifier=modifier.onGloballyPositioned {
            anchor=Rect(it.positionInRoot(), androidx.compose.ui.geometry.Size(it.size.width.toFloat(),it.size.height.toFloat()))
        },
            enabled=LocalCardBrowse.current && state != null,
            onClick={
                if(state?.entry?.owner===owner) state.entry=null
                else if(state != null) state.entry=CardPopoverState.Entry(owner,{anchor},locals,title) { CardChild(childId) }
            }) { Text(title) }
    }
}

/** Uses the same in-card overlay as Popover, so floating windows need no dialog token. */
internal object CardTooltip : A2uiComponent {
    override val name = "Tooltip"
    override val description = "Tap or long-press for a short explanation."
    private val label = A2uiProperty.dynamicString("label")
    private val text = A2uiProperty.dynamicString("text")
    override val properties = listOf(label, text)
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val state = LocalCardPopover.current
        val title = properties.bind(label).orEmpty()
        val message by rememberUpdatedState(properties.bind(text).orEmpty())
        val owner = remember { Any() }
        var anchor by remember { mutableStateOf(Rect.Zero) }
        val locals = currentCompositionLocalContext
        DisposableEffect(state, owner) { onDispose { if (state?.entry?.owner === owner) state.entry = null } }
        val show = {
            if (state != null) {
                if (state.entry?.owner === owner) state.entry = null
                else state.entry = CardPopoverState.Entry(owner, { anchor }, locals, title) { Text(message) }
            }
        }
        Text(title, modifier.heightIn(min = 48.dp).onGloballyPositioned {
            anchor = Rect(it.positionInRoot(), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }.combinedClickable(enabled = LocalCardBrowse.current && state != null,
            role = androidx.compose.ui.semantics.Role.Button, onClick = show, onLongClick = show)
            .padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary)
    }
}
