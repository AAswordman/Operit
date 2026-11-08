package com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.components.NavigationDrawerAppearance
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.theme.getTextColorForBackground

/** 插件区独立滚动并可折叠，聊天数量不会改变入口的可用性。 */
@Composable
internal fun SidebarPluginSection(
    entries: List<NavigationEntrySpec>,
    selectedRouteId: String,
    appearance: NavigationDrawerAppearance,
    maxListHeight: Dp,
    onEntryClick: (NavigationEntrySpec) -> Unit,
) {
    if (entries.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    val expansionLabel = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { stateDescription = expansionLabel }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.nav_group_plugins), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = appearance.titleColor)
            Text(entries.size.toString(), style = MaterialTheme.typography.labelMedium,
                color = appearance.itemColor.copy(alpha = 0.7f))
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = appearance.itemColor, modifier = Modifier.size(20.dp))
        }
        if (expanded) {
            // 按抽屉剩余高度约束列表，保留聊天区域，不再固定为 108dp。
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxListHeight),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries, key = { it.routeId }) { entry ->
                    val selected = selectedRouteId == entry.routeId
                    val container = if (selected) appearance.selectedContainerColor else appearance.buttonContainerColor
                    SidebarButtonSurface(
                        onClick = { onEntryClick(entry) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        appearance = appearance,
                        containerColor = container,
                        contentColor = getTextColorForBackground(container),
                        selected = selected,
                        border = BorderStroke(1.dp, appearance.dividerColor.copy(alpha = 0.6f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(entry.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(entry.title, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
