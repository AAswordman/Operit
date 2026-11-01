package com.ai.assistance.operit.ui.features.conversation.chat.modules.characters

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.CharacterSelectorTarget
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.flowOf

private enum class CharacterSelectorSortOption {
    DEFAULT,
    NAME_ASC,
    CREATED_DESC
}

private fun applyCharacterSelectorSort(
    cards: List<CharacterCard>,
    sortOptionName: String
): List<CharacterCard> {
    val sortOption =
        runCatching { CharacterSelectorSortOption.valueOf(sortOptionName) }
            .getOrDefault(CharacterSelectorSortOption.DEFAULT)

    return when (sortOption) {
        CharacterSelectorSortOption.DEFAULT -> cards
        CharacterSelectorSortOption.NAME_ASC -> cards.sortedBy { it.name.lowercase() }
        CharacterSelectorSortOption.CREATED_DESC -> cards.sortedByDescending { it.updatedAt }
    }
}

@Composable
fun CharacterSelectorPanel(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSelectCharacter: (CharacterSelectorTarget) -> Unit,
    onOpenCharacterSettings: () -> Unit
) {
    val context = LocalContext.current
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    var allCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var allGroups by remember { mutableStateOf<List<CharacterGroupCard>>(emptyList()) }
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )
    val activeCardId = (activePrompt as? ActivePrompt.CharacterCard)?.id
    val activeGroupId = (activePrompt as? ActivePrompt.CharacterGroup)?.id
    val sortOptionNameState = rememberLocal(
        key = "ModelPromptsSettingsScreen.CharacterCardTab.sortOption",
        defaultValue = CharacterSelectorSortOption.DEFAULT.name
    )
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sortedCards = remember(allCards, sortOptionNameState.value) {
        applyCharacterSelectorSort(allCards, sortOptionNameState.value)
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            allCards = characterCardManager.getAllCharacterCards()
            allGroups = characterGroupCardManager.getAllCharacterGroupCards()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(300, easing = androidx.compose.animation.core.EaseOutCubic)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseInCubic)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 60.dp, start = 20.dp, end = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        .clickable(enabled = false) {},
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 标题栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.getString(R.string.select_character),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = context.getString(R.string.character_count, allCards.size + allGroups.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box {
                                IconButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = context.getString(R.string.character_card_sort),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                    modifier = Modifier
                                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = context.getString(R.string.character_card_sort_default),
                                                color = Color(0xFF1F1F1F)
                                            )
                                        },
                                        onClick = {
                                            sortOptionNameState.value = CharacterSelectorSortOption.DEFAULT.name
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = context.getString(R.string.character_card_sort_by_name),
                                                color = Color(0xFF1F1F1F)
                                            )
                                        },
                                        onClick = {
                                            sortOptionNameState.value = CharacterSelectorSortOption.NAME_ASC.name
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = context.getString(R.string.character_card_sort_by_created),
                                                color = Color(0xFF1F1F1F)
                                            )
                                        },
                                        onClick = {
                                            sortOptionNameState.value = CharacterSelectorSortOption.CREATED_DESC.name
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    onOpenCharacterSettings()
                                    onDismiss()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = context.getString(R.string.edit_character_card),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        // 角色列表
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (allGroups.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.character_groups),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                    )
                                }
                                items(allGroups, key = { it.id }) { group ->
                                    CharacterGroupItem(
                                        group = group,
                                        isSelected = group.id == activeGroupId,
                                        onClick = {
                                            onSelectCharacter(CharacterSelectorTarget.CharacterGroupTarget(group.id))
                                            onDismiss()
                                        }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(4.dp)) }
                            }
                            item {
                                Text(
                                    text = stringResource(R.string.character_cards),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                            items(sortedCards, key = { it.id }) { card ->
                                CharacterItem(
                                    card = card,
                                    isSelected = activeGroupId.isNullOrBlank() && card.id == activeCardId,
                                    onClick = {
                                        onSelectCharacter(CharacterSelectorTarget.CharacterCardTarget(card.id))
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterItem(
    card: CharacterCard,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val avatarUri by userPreferencesManager.getAiAvatarForCharacterCardFlow(card.id).collectAsState(initial = null)

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像区域
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (avatarUri != null) Color.Transparent 
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = "Character Avatar",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 角色信息
            Column(
                modifier = Modifier.weight(1f).padding(end = if (isSelected) 4.dp else 0.dp)
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (card.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = card.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 选中状态指示器（右侧）
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CharacterGroupItem(
    group: CharacterGroupCard,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val groupAvatarUri by remember(group.id) {
        userPreferencesManager.getAiAvatarForCharacterGroupFlow(group.id)
    }.collectAsState(initial = null)
    val representativeMemberCardId = remember(group.members) {
        val sortedMembers = group.members.sortedBy { it.orderIndex }
        sortedMembers.firstOrNull()?.characterCardId
    }
    val fallbackMemberAvatarUri by remember(representativeMemberCardId) {
        representativeMemberCardId
            ?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val displayAvatarUri = groupAvatarUri ?: fallbackMemberAvatarUri

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (displayAvatarUri != null) Color.Transparent
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (displayAvatarUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(displayAvatarUri)),
                        contentDescription = "Group Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.Groups,
                        contentDescription = "Group Avatar",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f).padding(end = if (isSelected) 4.dp else 0.dp)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.character_group_member_count, group.members.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
