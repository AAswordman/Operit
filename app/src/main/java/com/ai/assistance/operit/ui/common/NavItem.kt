package com.ai.assistance.operit.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Token
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R

// 应用导航项
sealed class NavItem(val route: String, val titleResId: Int, val icon: ImageVector) {
        object AiChat : NavItem("ai_chat", R.string.nav_ai_chat, Icons.Default.Email)
        object ShizukuCommands :
                NavItem("shizuku_commands", R.string.shizuku_commands, Icons.Default.Build)
        object Settings : NavItem("settings", R.string.nav_settings, Icons.Default.Settings)
        object ToolPermissions :
                NavItem("tool_permissions", R.string.tool_permissions, Icons.Default.Security)
        object ChatHistorySettings :
                NavItem(
                        "chat_history_settings",
                        R.string.chat_history_settings,
                        Icons.Default.History
                )
        object Packages : NavItem("packages", R.string.nav_packages, Icons.Default.Extension)
        object MemoryBase :
                NavItem("memory_base", R.string.nav_memory_base, Icons.Default.History)
        object Terminal : NavItem("terminal", R.string.terminal, Icons.Default.Terminal)
        object About : NavItem("about", R.string.nav_about, Icons.Default.Info)
        object Agreement :
                NavItem("agreement", R.string.nav_item_agreement, Icons.Default.Description)
        object Help : NavItem("help", R.string.nav_help, Icons.AutoMirrored.Filled.Help)
        object TokenConfig : NavItem("token_config", R.string.token_config, Icons.Default.Token)
        object Workflow : NavItem("workflow", R.string.nav_workflow, Icons.Default.AccountTree)
}
