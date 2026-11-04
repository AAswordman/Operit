package com.ai.assistance.operit.ui.features.automation.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.terminal.utils.VirtualKeyAction
import com.ai.assistance.operit.terminal.utils.VirtualKeyboardLayoutConfig

@Composable
internal fun TerminalShortcutBar(
    layout: VirtualKeyboardLayoutConfig,
    ctrl: Boolean,
    alt: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onInput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = Color.Black, contentColor = Color.White) {
        Column {
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                layout.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { button ->
                            val selected = when (button.action) {
                                VirtualKeyAction.TOGGLE_CTRL -> ctrl
                                VirtualKeyAction.TOGGLE_ALT -> alt
                                VirtualKeyAction.SEND_TEXT -> false
                            }
                            Surface(
                                onClick = {
                                    when (button.action) {
                                        VirtualKeyAction.TOGGLE_CTRL -> onToggleCtrl()
                                        VirtualKeyAction.TOGGLE_ALT -> onToggleAlt()
                                        VirtualKeyAction.SEND_TEXT -> onInput(decodeTerminalShortcut(button.value))
                                    }
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Black,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text(button.label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
