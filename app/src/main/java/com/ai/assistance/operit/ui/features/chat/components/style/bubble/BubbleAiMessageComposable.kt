package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRendererState
import com.ai.assistance.operit.ui.features.chat.components.ChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.chat.components.rememberRevisableTextStream
import com.ai.assistance.operit.ui.features.chat.components.part.CustomXmlRenderer
import com.ai.assistance.operit.ui.features.chat.components.part.ThinkToolsXmlNodeGrouper
import com.ai.assistance.operit.ui.features.chat.components.LinkPreviewDialog
import com.ai.assistance.operit.util.markdown.toCharStream
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.ui.theme.ProvideAiMarkdownTextLayoutSettings
import com.ai.assistance.operit.ui.theme.applyFontFamilyToTypography
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.liquidGlass
import com.ai.assistance.operit.ui.theme.resolveConfiguredFontFamily
import com.ai.assistance.operit.ui.theme.waterGlass
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

private val ExpandedBubbleLayoutNodeTypes =
    setOf(
        MarkdownProcessorType.CODE_BLOCK,
        MarkdownProcessorType.TABLE,
        MarkdownProcessorType.XML_BLOCK,
        MarkdownProcessorType.IMAGE,
        MarkdownProcessorType.BLOCK_LATEX,
    )

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BubbleAiMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    enableLiquidGlass: Boolean = false,
    enableWaterGlass: Boolean = false,
    bubbleImageStyle: BubbleImageStyleConfig? = null,
    bubbleRoundedCornersEnabled: Boolean = true,
    bubbleContentPaddingLeft: Float = 12f,
    bubbleContentPaddingRight: Float = 12f,
    initialThinkingExpanded: Boolean = false,
    allowExpandedThinkingFullHeight: Boolean = false,
    expandThinkToolsGroups: Boolean = false,
    forceShowThinkingProcess: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    isHidden: Boolean = false,
    heightMemory: ChatMessageHeightMemory? = null,
    enableDialogs: Boolean = true,
    onAvatarLongPressMention: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val themeSnapshot = LocalThemePreferenceSnapshot.current
    val bubbleShowAvatar = themeSnapshot.bubbleShowAvatar
    val bubbleWideLayoutEnabled = themeSnapshot.bubbleWideLayoutEnabled
    val showThinkingProcess = themeSnapshot.showThinkingProcess
    val showStatusTags = themeSnapshot.showStatusTags
    val effectiveShowThinkingProcess = if (forceShowThinkingProcess) true else showThinkingProcess
    val avatarShapePref = themeSnapshot.avatarShape
    val avatarCornerRadius = themeSnapshot.avatarCornerRadius
    val bubbleAiUseCustomFont = themeSnapshot.bubbleAiUseCustomFont
    val bubbleAiFontType = themeSnapshot.bubbleAiFontType
    val bubbleAiSystemFontName = themeSnapshot.bubbleAiSystemFontName
    val bubbleAiCustomFontPath = themeSnapshot.bubbleAiCustomFontPath
    
    val showModelProvider = themeSnapshot.showModelProvider
    val showModelName = themeSnapshot.showModelName
    val showRoleName = themeSnapshot.showRoleName
    val toolCollapseMode by displayPreferencesManager.toolCollapseMode.collectAsState(initial = ToolCollapseMode.ALL)
    
    // 根据角色名获取头像
    val aiAvatarUri by remember(message.roleName, themeSnapshot.customAiAvatarUri) {
        if (message.roleName != null) {
            try {
                runBlocking {
                    val characterCard = characterCardManager.findCharacterCardByName(message.roleName)
                    if (characterCard != null) {
                        preferencesManager.getAiAvatarForCharacterCardFlow(characterCard.id)
                    } else {
                        flowOf(themeSnapshot.customAiAvatarUri)
                    }
                }
            } catch (e: Exception) {
                flowOf(themeSnapshot.customAiAvatarUri)
            }
        } else {
            flowOf(themeSnapshot.customAiAvatarUri)
        }
    }.collectAsState(initial = null)

    val avatarShape = remember(avatarShapePref, avatarCornerRadius) {
        if (avatarShapePref == UserPreferencesManager.AVATAR_SHAPE_SQUARE) {
            RoundedCornerShape(avatarCornerRadius.dp)
        } else {
            CircleShape
        }
    }
    val roleNameText = if (showRoleName && message.roleName.isNotEmpty()) message.roleName else ""
    val metadataText = buildString {
        if (showModelName && message.modelName.isNotEmpty()) {
            append(message.modelName)
        }

        if (showModelProvider && message.provider.isNotEmpty()) {
            if (showModelName && message.modelName.isNotEmpty()) {
                append(" by ")
            } else if (isNotEmpty()) {
                append(" | ")
            }
            append(message.provider)
        }
    }

    // 链接预览弹窗状态
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkToPreview by remember { mutableStateOf("") }
    
    // 创建并保存StreamMarkdownRenderer的状态，使用message.timestamp作为key确保同一条消息共享状态
    val rendererState = remember(message.timestamp) { StreamMarkdownRendererState() }

    val xmlRenderer = remember(
        effectiveShowThinkingProcess,
        showStatusTags,
        initialThinkingExpanded,
        allowExpandedThinkingFullHeight,
        enableDialogs
    ) {
        CustomXmlRenderer(
            showThinkingProcess = effectiveShowThinkingProcess,
            showStatusTags = showStatusTags,
            initialThinkingExpanded = initialThinkingExpanded,
            allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
            enableDialogs = enableDialogs
        )
    }

    val nodeGrouper = remember(effectiveShowThinkingProcess, toolCollapseMode, expandThinkToolsGroups) {
        ThinkToolsXmlNodeGrouper(
            showThinkingProcess = effectiveShowThinkingProcess,
            forceExpandGroups = expandThinkToolsGroups,
            toolCollapseMode = toolCollapseMode
        )
    }
    val rememberedOnLinkClick = remember(context, onLinkClick, enableDialogs) {
        if (!enableDialogs) {
            { _: String -> }
        } else {
            onLinkClick ?: { url ->
                linkToPreview = url
                showLinkDialog = true
            }
        }
    }

    val targetAlpha = if (isHidden) 0f else 1f
    val targetOffsetY = if (isHidden) 100f else 0f

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300)
    )
    val offsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = 300)
    )

    val imageUrl = remember(message.content, message.contentStream) {
        if (message.contentStream == null) {
            val regex = """^\s*!\[[^\]]*\]\(([^)]+)\)\s*$""".toRegex()
            regex.find(message.content)?.groups?.get(1)?.value
        } else {
            null
        }
    }
    val shouldUseExpandedBubbleLayout =
        rendererState.renderNodes.any { node -> node.type in ExpandedBubbleLayoutNodeTypes }
    val sizeTrackingModifier =
        if (isHidden) {
            Modifier
        } else {
            Modifier.onSizeChanged { size ->
                heightMemory?.updateMeasured(message.timestamp, size.height)
            }
        }
    val baseTypography = MaterialTheme.typography
    val bubbleTypography =
        remember(
            context,
            bubbleAiUseCustomFont,
            bubbleAiFontType,
            bubbleAiSystemFontName,
            bubbleAiCustomFontPath,
            baseTypography,
        ) {
            applyFontFamilyToTypography(
                baseTypography = baseTypography,
                fontFamily =
                    resolveConfiguredFontFamily(
                        context = context,
                        useCustomFont = bubbleAiUseCustomFont,
                        fontType = bubbleAiFontType,
                        systemFontName = bubbleAiSystemFontName,
                        customFontPath = bubbleAiCustomFontPath,
                    ),
            )
        }
    val waterGlassEnabled = enableWaterGlass && isWaterGlassSupported()
    val liquidGlassEnabled =
        !waterGlassEnabled && enableLiquidGlass && isLiquidGlassSupported()
    val effectiveBubbleImageStyle =
        if (liquidGlassEnabled || waterGlassEnabled) {
            null
        } else {
            bubbleImageStyle
        }

    MaterialTheme(typography = bubbleTypography) {
        ProvideAiMarkdownTextLayoutSettings {
        if (bubbleWideLayoutEnabled) {
        val headerVisible = bubbleShowAvatar || roleNameText.isNotEmpty() || metadataText.isNotEmpty()
        val avatarModifier = Modifier
            .size(32.dp)
            .clip(avatarShape)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    val roleName = message.roleName.trim()
                    if (roleName.isNotEmpty()) {
                        onAvatarLongPressMention?.invoke(roleName)
                    }
                },
            )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (bubbleShowAvatar) 0.dp else 8.dp,
                    top = 4.dp,
                    end = 0.dp,
                    bottom = 4.dp,
                )
                .then(sizeTrackingModifier)
                .alpha(alpha)
                .offset(y = offsetY.dp),
        ) {
            if (headerVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bubbleShowAvatar) {
                        if (!aiAvatarUri.isNullOrEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = Uri.parse(aiAvatarUri)),
                                contentDescription = "AI Avatar",
                                modifier = avatarModifier,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Assistant,
                                contentDescription = "AI Avatar",
                                modifier = avatarModifier,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (roleNameText.isNotEmpty()) {
                            Text(
                                text = roleNameText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (metadataText.isNotEmpty()) {
                            Text(
                                text = metadataText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxBubbleWidth = maxWidth
                if (imageUrl != null) {
                    AsyncImage(
                        model = Uri.parse(imageUrl),
                        contentDescription = "Image from AI",
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth)
                            .heightIn(max = 80.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    val bubbleShape =
                        if (bubbleRoundedCornersEnabled) {
                            RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
                        } else {
                            RoundedCornerShape(0.dp)
                        }
                    val bubbleModifier =
                        Modifier
                            .widthIn(max = maxBubbleWidth)
                            .defaultMinSize(minHeight = 44.dp)
                    val renderContent: @Composable () -> Unit = {
                        key(message.timestamp) {
                            val stream = rememberRevisableTextStream(message.contentStream)
                            if (stream != null) {
                                val charStream = remember(stream) { stream.toCharStream() }
                                StreamMarkdownRenderer(
                                    markdownStream = charStream,
                                    textColor = textColor,
                                    backgroundColor = backgroundColor,
                                    onLinkClick = rememberedOnLinkClick,
                                    xmlRenderer = xmlRenderer,
                                    nodeGrouper = nodeGrouper,
                                    enableDialogs = enableDialogs,
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 12.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 12.dp,
                                    ),
                                    state = rendererState,
                                    fillMaxWidth = shouldUseExpandedBubbleLayout,
                                )
                            } else {
                                StreamMarkdownRenderer(
                                    content = message.content,
                                    textColor = textColor,
                                    backgroundColor = backgroundColor,
                                    onLinkClick = rememberedOnLinkClick,
                                    xmlRenderer = xmlRenderer,
                                    nodeGrouper = nodeGrouper,
                                    enableDialogs = enableDialogs,
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 12.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 12.dp,
                                    ),
                                    state = rendererState,
                                    fillMaxWidth = shouldUseExpandedBubbleLayout,
                                )
                            }
                        }
                    }

                    if (effectiveBubbleImageStyle != null) {
                        BubbleImageBackgroundSurface(
                            imageStyle = effectiveBubbleImageStyle,
                            shape = bubbleShape,
                            modifier = bubbleModifier,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            renderContent()
                        }
                    } else {
                        Surface(
                            modifier =
                                bubbleModifier
                                    .waterGlass(
                                        enabled = waterGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = backgroundColor,
                                        shadowElevation = 10.dp,
                                        borderWidth = 0.7.dp,
                                        overlayAlphaBoost = 0.08f,
                                    )
                                    .liquidGlass(
                                        enabled = liquidGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = backgroundColor,
                                        shadowElevation = 10.dp,
                                        borderWidth = 0.28.dp,
                                        blurRadius = 28.dp,
                                        overlayAlphaBoost = 0.10f,
                                        enableLens = false,
                                    ),
                            shape = bubbleShape,
                            color =
                                if (liquidGlassEnabled || waterGlassEnabled) {
                                    Color.Transparent
                                } else {
                                    backgroundColor
                                },
                            tonalElevation =
                                if (liquidGlassEnabled || waterGlassEnabled) {
                                    0.dp
                                } else {
                                    2.dp
                                },
                        ) {
                            renderContent()
                        }
                    }
                }
            }
        }
    } else {
    Row(
        modifier = Modifier
            .padding(horizontal = 0.dp, vertical = 4.dp)
            .then(sizeTrackingModifier)
            .alpha(alpha)
            .offset(y = offsetY.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (bubbleShowAvatar) {
            val avatarModifier = Modifier
                .size(32.dp)
                .clip(avatarShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        val roleName = message.roleName.trim()
                        if (roleName.isNotEmpty()) {
                            onAvatarLongPressMention?.invoke(roleName)
                        }
                    }
                )
            // Avatar
            if (!aiAvatarUri.isNullOrEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(aiAvatarUri)),
                    contentDescription = "AI Avatar",
                    modifier = avatarModifier,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Assistant,
                    contentDescription = "AI Avatar",
                    modifier = avatarModifier,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 使用Column来垂直排列名称和消息气泡
        Column(
            modifier = Modifier
                .padding(
                    start = if (bubbleShowAvatar) 0.dp else 8.dp,
                    end = 0.dp
                )
                .weight(1f, fill = false)
        ) {
            // 根据用户设置显示角色名称、模型名称和供应商信息
            val displayText = buildString {
                // 根据用户设置添加角色名称
                if (showRoleName && message.roleName.isNotEmpty()) {
                    append(message.roleName)
                }
                
                // 根据用户设置添加模型名称
                if (showModelName && message.modelName.isNotEmpty()) {
                    if (isNotEmpty()) append(" | ")
                    append(message.modelName)
                }
                
                // 根据用户设置添加供应商
                if (showModelProvider && message.provider.isNotEmpty()) {
                    if (showModelName && message.modelName.isNotEmpty()) {
                        append(" by ")
                    } else if (isNotEmpty()) {
                        append(" | ")
                    }
                    append(message.provider)
                }
            }
            
            if (displayText.isNotEmpty()) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
            }
            
            BoxWithConstraints {
                val maxBubbleWidth = maxWidth * 0.85f
                if (imageUrl != null) {
                    AsyncImage(
                        model = Uri.parse(imageUrl),
                        contentDescription = "Image from AI",
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth)
                            .heightIn(max = 80.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Message bubble
                    val bubbleShape =
                        if (bubbleRoundedCornersEnabled) {
                            RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
                        } else {
                            RoundedCornerShape(0.dp)
                        }
                    val bubbleModifier =
                        Modifier
                            .widthIn(max = maxBubbleWidth)
                            .defaultMinSize(minHeight = 44.dp)
                    val renderContent: @Composable () -> Unit = {
                        // 使用 message.timestamp 作为 key，确保在重组期间，
                        // 只要是同一条消息，StreamMarkdownRenderer就不会被销毁和重建。
                        key(message.timestamp) {
                            val stream = rememberRevisableTextStream(message.contentStream)
                            if (stream != null) {
                                val charStream = remember(stream) { stream.toCharStream() }
                                StreamMarkdownRenderer(
                                    markdownStream = charStream,
                                    textColor = textColor,
                                    backgroundColor = backgroundColor,
                                    onLinkClick = rememberedOnLinkClick,
                                    xmlRenderer = xmlRenderer,
                                    nodeGrouper = nodeGrouper,
                                    enableDialogs = enableDialogs,
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 12.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 12.dp,
                                    ),
                                    state = rendererState,
                                    fillMaxWidth = shouldUseExpandedBubbleLayout,
                                )
                            } else {
                                // 对于已完成的静态消息，使用 content 参数的渲染器以支持Markdown
                                // 共享相同的state，避免重新计算nodes等状态
                                StreamMarkdownRenderer(
                                    content = message.content,
                                    textColor = textColor,
                                    backgroundColor = backgroundColor,
                                    onLinkClick = rememberedOnLinkClick,
                                    xmlRenderer = xmlRenderer,
                                    nodeGrouper = nodeGrouper,
                                    enableDialogs = enableDialogs,
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 12.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 12.dp,
                                    ),
                                    state = rendererState,
                                    fillMaxWidth = shouldUseExpandedBubbleLayout,
                                )
                            }
                        }
                    }

                    if (effectiveBubbleImageStyle != null) {
                        BubbleImageBackgroundSurface(
                            imageStyle = effectiveBubbleImageStyle,
                            shape = bubbleShape,
                            modifier = bubbleModifier,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            renderContent()
                        }
                    } else {
                        Surface(
                            modifier =
                                bubbleModifier
                                    .waterGlass(
                                        enabled = waterGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = backgroundColor,
                                        shadowElevation = 10.dp,
                                        borderWidth = 0.7.dp,
                                        overlayAlphaBoost = 0.08f,
                                    )
                                    .liquidGlass(
                                        enabled = liquidGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = backgroundColor,
                                        shadowElevation = 10.dp,
                                        borderWidth = 0.28.dp,
                                        blurRadius = 28.dp,
                                        overlayAlphaBoost = 0.10f,
                                        enableLens = false,
                                    ),
                            shape = bubbleShape,
                            color =
                                if (liquidGlassEnabled || waterGlassEnabled) {
                                    Color.Transparent
                                } else {
                                    backgroundColor
                                },
                            tonalElevation =
                                if (liquidGlassEnabled || waterGlassEnabled) {
                                    0.dp
                                } else {
                                    2.dp
                                }
                        ) {
                            renderContent()
                        }
                    }
                }
            }
        }
        }
    }
    }
    }

    // 链接预览弹窗
    if (showLinkDialog && linkToPreview.isNotEmpty() && enableDialogs) {
        LinkPreviewDialog(
            url = linkToPreview,
            onDismiss = {
                showLinkDialog = false
                linkToPreview = ""
            }
        )
    }
}
