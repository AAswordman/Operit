package com.ai.assistance.operit.ui.main.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.support.diagnostics.uidebugger.UIDebuggerScreen

/** 显示终端自动配置工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAutoConfigToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        // TODO: 需要重构以适配新的终端架构
                        // TerminalAutoConfigScreen(navController = navController)
                        Text(
                            text = stringResource(R.string.tool_terminal_auto_config_under_construction),
                            modifier = Modifier.padding(16.dp)
                        )
                }
        }
}

/** 显示UI调试工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        UIDebuggerScreen(navController = navController)
                }
        }
}
