package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.data.model.theme.forMode
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager

/** 部件选择器显示当前头像和已绑定图标，未绑定时使用各自的语义图标。 */
@Composable
internal fun StudioTargetThumbnail(state: ThemeStudioState, target: StudioTarget) {
    val values by state.session.values.collectAsState()
    val context = LocalContext.current
    val display = remember(context) { DisplayPreferencesManager.getInstance(context) }
    val globalAvatar by display.globalUserAvatarUri.collectAsState(initial = null)
    val avatar = when (target.id) {
        "userAvatar" -> values.string("custom_user_avatar_uri") ?: globalAvatar
        "assistantAvatar" -> values.string("custom_ai_avatar_uri")
        else -> null
    }
    val icon = if (target.kind == StudioKind.Icon) state.bundle.manifest.appearance.forMode(state.mode).icons[target.id] else null
    when {
        avatar != null -> AsyncImage(avatar, null, Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        icon != null -> StudioAssetThumbnail(state.images.getValue(icon.asset), Modifier.size(28.dp))
        else -> Icon(target.glyph, null, Modifier.size(24.dp), tint = StudioAccent)
    }
}