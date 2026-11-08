package com.ai.assistance.operit.ui.features.models.token.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.OperitApplication
import kotlinx.serialization.Serializable

@Serializable
data class TabConfig(
    val title: String,
    val url: String
)

@Serializable
data class UrlConfig(
    val name: String = "DeepSeek",
    val signInUrl: String = "https://platform.deepseek.com/sign_in",
    val tabs: List<TabConfig> = listOf(
        TabConfig(OperitApplication.instance.getString(R.string.url_config_api_key), "https://platform.deepseek.com/api_keys"),
        TabConfig(OperitApplication.instance.getString(R.string.url_config_usage), "https://platform.deepseek.com/usage"),
        TabConfig(OperitApplication.instance.getString(R.string.url_config_top_up), "https://platform.deepseek.com/top_up"),
        TabConfig(OperitApplication.instance.getString(R.string.url_config_profile), "https://platform.deepseek.com/profile")
    )
)

// 导航目标数据类
data class NavDestination(
    val title: String, 
    val url: String, 
    val icon: ImageVector
)

// 获取导航目标的图标
fun getIconForIndex(index: Int): ImageVector = when (index) {
    0 -> Icons.Default.Key
    1 -> Icons.Default.Dashboard
    2 -> Icons.Default.CreditCard
    3 -> Icons.Default.Person
    else -> Icons.Default.Key
} 