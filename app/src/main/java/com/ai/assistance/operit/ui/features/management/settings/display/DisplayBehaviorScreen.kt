package com.ai.assistance.operit.ui.features.management.settings.display

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen

private enum class DisplayTab(
    val label: Int,
    val eyebrow: Int,
    val title: Int,
    val description: Int,
    val icon: ImageVector,
) {
    CHAT(
        R.string.display_behavior_tab_chat,
        R.string.display_behavior_chat_eyebrow,
        R.string.display_behavior_chat_title,
        R.string.display_behavior_chat_desc,
        Icons.AutoMirrored.Outlined.Chat,
    ),
    INTERFACE(
        R.string.display_behavior_tab_interface,
        R.string.display_behavior_interface_eyebrow,
        R.string.display_behavior_interface_title,
        R.string.display_behavior_interface_desc,
        Icons.Default.Computer,
    ),
    AUTOMATION(
        R.string.display_behavior_tab_automation,
        R.string.display_behavior_automation_eyebrow,
        R.string.display_behavior_automation_title,
        R.string.display_behavior_automation_desc,
        Icons.Default.AutoAwesome,
    ),
}

/** 定稿的整页布局由本页负责，宿主不再额外叠加第二个标题栏。 */
@Composable
fun DisplayBehaviorScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val controller = remember(context) { DisplayBehaviorController(context) }
    val isCurrent = LocalIsCurrentScreen.current
    var tab by rememberSaveable { mutableStateOf(DisplayTab.CHAT) }
    var editor by rememberSaveable { mutableStateOf<DisplayEditor?>(null) }
    val scroll = rememberScrollState()
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.display_behavior_save_error)
    LaunchedEffect(isCurrent) { if (isCurrent) controller.refresh() else controller.flushNumbers() }
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(tab) { scroll.scrollTo(0) }
    LaunchedEffect(controller.error) {
        if (controller.error != null) snackbar.showSnackbar(errorMessage)
    }
    val back = {
        controller.flushNumbers()
        onBackPressed()
    }
    BackHandler(isCurrent && editor == null, back)
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(back, Modifier.size(44.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.display_behavior_back),
                                Modifier.size(21.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.display_behavior_title),
                            Modifier.weight(1f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier.size(5.dp)
                                    .background(
                                        if (controller.error == null) colors.primary
                                        else colors.error,
                                        CircleShape,
                                    )
                            )
                            Text(
                                stringResource(
                                    when {
                                        controller.error != null ->
                                            R.string.display_behavior_save_failed
                                        controller.saving -> R.string.display_behavior_saving
                                        controller.saved -> R.string.display_behavior_saved
                                        else -> R.string.display_behavior_auto_save
                                    }
                                ),
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.display_behavior_intro),
                        Modifier.padding(start = 26.dp, end = 26.dp, bottom = 13.dp),
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).selectableGroup()) {
                        DisplayTab.entries.forEach { item ->
                            val selected = tab == item
                            Column(
                                Modifier.weight(1f)
                                    .selectable(
                                        selected,
                                        role = Role.Tab,
                                        onClick = {
                                            controller.flushNumbers()
                                            tab = item
                                        },
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(
                                    Modifier.heightIn(min = 43.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        item.icon,
                                        null,
                                        Modifier.size(17.dp),
                                        tint =
                                            if (selected) colors.primary
                                            else colors.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(item.label),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color =
                                            if (selected) colors.primary
                                            else colors.onSurfaceVariant,
                                    )
                                }
                                Box(
                                    Modifier.fillMaxWidth(0.54f)
                                        .height(3.dp)
                                        .background(
                                            if (selected) colors.primary else Color.Transparent,
                                            androidx.compose.foundation.shape.RoundedCornerShape(
                                                topStart = 3.dp,
                                                topEnd = 3.dp,
                                            ),
                                        )
                                )
                            }
                        }
                    }
                }
                DisplayDivider()
                if (!controller.ready) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (controller.error == null)
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else
                            Text(
                                stringResource(R.string.display_behavior_save_error),
                                Modifier.padding(24.dp),
                                fontSize = 12.sp,
                                color = colors.error,
                            )
                    }
                } else {
                    Column(
                        Modifier.weight(1f)
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 24.dp)
                    ) {
                        Column(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 16.dp)) {
                            Text(
                                stringResource(tab.eyebrow),
                                fontSize = 11.sp,
                                letterSpacing = 1.54.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                stringResource(tab.title),
                                Modifier.padding(top = 5.dp),
                                fontSize = 19.sp,
                                lineHeight = 28.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(tab.description),
                                Modifier.padding(top = 5.dp),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        val open: (DisplayEditor) -> Unit = {
                            controller.flushNumbers()
                            editor = it
                        }
                        when (tab) {
                            DisplayTab.CHAT -> DisplayChatTab(controller, open)
                            DisplayTab.INTERFACE -> DisplayInterfaceTab(controller, open)
                            DisplayTab.AUTOMATION -> DisplayAutomationTab(controller, open)
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(
                                { open(DisplayEditor.RESET) },
                                contentPadding =
                                    PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor = colors.onSurfaceVariant
                                    ),
                            ) {
                                Icon(Icons.Default.RestartAlt, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    stringResource(R.string.display_behavior_reset_link),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            if (isCurrent)
                editor?.let { active ->
                    key(active) {
                        DisplayBehaviorEditor(
                            active,
                            controller,
                            onOpen = { editor = it },
                            onDismiss = {
                                controller.flushNumbers()
                                editor = null
                            },
                        )
                    }
                }
        }
    }
}
