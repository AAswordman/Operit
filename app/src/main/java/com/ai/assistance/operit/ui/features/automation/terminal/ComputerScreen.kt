package com.ai.assistance.operit.ui.features.automation.terminal

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ComputerScreen(page: EmbeddedTerminalPage, onPageChange: (EmbeddedTerminalPage) -> Unit) {
    EmbeddedTerminalScreen(page = page, onPageChange = onPageChange)
}
