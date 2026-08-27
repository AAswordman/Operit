package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.components.attachments.AttachmentViewerDialog
import com.ai.assistance.operit.ui.features.chat.components.attachments.ChatAttachment
import com.ai.assistance.operit.ui.features.chat.components.style.common.HiddenUserMessagePlaceholderContent
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.util.ImageBitmapLimiter
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.ui.theme.applyFontFamilyToTypography
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.liquidGlass
import com.ai.assistance.operit.ui.theme.resolveConfiguredFontFamily
import com.ai.assistance.operit.ui.theme.waterGlass
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BubbleUserMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    enableLiquidGlass: Boolean = false,
    enableWaterGlass: Boolean = false,
    bubbleImageStyle: BubbleImageStyleConfig? = null,
    bubbleRoundedCornersEnabled: Boolean = true,
    bubbleContentPaddingLeft: Float = 12f,
    bubbleContentPaddingRight: Float = 12f,
    enableDialogs: Boolean = true,
) {
    val context = LocalContext.current
    val isHiddenPlaceholder =
        message.sender == "user" &&
            message.displayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
    val effectiveBackgroundColor =
        if (isHiddenPlaceholder) {
            Color.Transparent
        } else {
            backgroundColor
        }
    val effectiveTextColor =
        textColor
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val themeSnapshot = LocalThemePreferenceSnapshot.current
    val bubbleShowAvatar = themeSnapshot.bubbleShowAvatar
    val bubbleWideLayoutEnabled = themeSnapshot.bubbleWideLayoutEnabled
    val customUserAvatarUri = themeSnapshot.customUserAvatarUri
    val globalUserAvatarUri by displayPreferencesManager.globalUserAvatarUri.collectAsState(initial = null)
    val globalUserName by displayPreferencesManager.globalUserName.collectAsState(initial = null)
    val showUserName = themeSnapshot.showUserName
    val avatarShapePref = themeSnapshot.avatarShape
    val avatarCornerRadius = themeSnapshot.avatarCornerRadius
    val bubbleUserUseCustomFont = themeSnapshot.bubbleUserUseCustomFont
    val bubbleUserFontType = themeSnapshot.bubbleUserFontType
    val bubbleUserSystemFontName = themeSnapshot.bubbleUserSystemFontName
    val bubbleUserCustomFontPath = themeSnapshot.bubbleUserCustomFontPath
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Parse message content to separate text and attachments
    val parseResult =
        remember(message.content, isHiddenPlaceholder) {
            if (isHiddenPlaceholder) {
                MessageParseResult(processedText = "", trailingAttachments = emptyList())
            } else {
                parseMessageContent(context, message.content)
            }
        }
    val textContent = parseResult.processedText
    val trailingAttachments = parseResult.trailingAttachments
    val replyInfo = parseResult.replyInfo
    val imageLinks = parseResult.imageLinks
    val proxySenderName = if (isHiddenPlaceholder) null else parseResult.proxySenderName

    val isProxySender = !proxySenderName.isNullOrBlank()
    val proxyAvatarUri by remember(proxySenderName, themeSnapshot.customAiAvatarUri) {
        if (isProxySender) {
            try {
                runBlocking {
                    val characterCard = characterCardManager.findCharacterCardByName(proxySenderName!!)
                    if (characterCard != null) {
                        preferencesManager.getAiAvatarForCharacterCardFlow(characterCard.id)
                    } else {
                        flowOf(themeSnapshot.customAiAvatarUri)
                    }
                }
            } catch (_: Exception) {
                flowOf(themeSnapshot.customAiAvatarUri)
            }
        } else {
            flowOf(themeSnapshot.customAiAvatarUri)
        }
    }.collectAsState(initial = null)

    val avatarUri = remember(customUserAvatarUri, globalUserAvatarUri, proxyAvatarUri, isProxySender) {
        when {
            isProxySender && !proxyAvatarUri.isNullOrEmpty() -> proxyAvatarUri
            isProxySender -> null
            !customUserAvatarUri.isNullOrEmpty() -> customUserAvatarUri
            !globalUserAvatarUri.isNullOrEmpty() -> globalUserAvatarUri
            else -> null
        }
    }
    val avatarShape = remember(avatarShapePref, avatarCornerRadius) {
        if (avatarShapePref == UserPreferencesManager.AVATAR_SHAPE_SQUARE) {
            RoundedCornerShape(avatarCornerRadius.dp)
        } else {
            CircleShape
        }
    }
    val resolvedDisplayName = if (isProxySender) proxySenderName else globalUserName
    val shouldShowResolvedName = !isHiddenPlaceholder && if (isProxySender) true else showUserName
    val shouldShowAvatar = !isHiddenPlaceholder && bubbleShowAvatar

    // 添加状态控制内容预览
    val showContentPreview = remember { mutableStateOf(false) }
    val selectedChatAttachment = remember { mutableStateOf<ChatAttachment?>(null) }

    // 添加状态控制图片预览
    val showImagePreview = remember { mutableStateOf(false) }
    val selectedImageBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val baseTypography = MaterialTheme.typography
    val bubbleTypography =
        remember(
            context,
            bubbleUserUseCustomFont,
            bubbleUserFontType,
            bubbleUserSystemFontName,
            bubbleUserCustomFontPath,
            baseTypography,
        ) {
            applyFontFamilyToTypography(
                baseTypography = baseTypography,
                fontFamily =
                    resolveConfiguredFontFamily(
                        context = context,
                        useCustomFont = bubbleUserUseCustomFont,
                        fontType = bubbleUserFontType,
                        systemFontName = bubbleUserSystemFontName,
                        customFontPath = bubbleUserCustomFontPath,
                    ),
            )
        }
    val waterGlassEnabled = !isHiddenPlaceholder && enableWaterGlass && isWaterGlassSupported()
    val liquidGlassEnabled =
        !isHiddenPlaceholder && !waterGlassEnabled && enableLiquidGlass && isLiquidGlassSupported()
    val effectiveBubbleImageStyle =
        if (isHiddenPlaceholder || liquidGlassEnabled || waterGlassEnabled) {
            null
        } else {
            bubbleImageStyle
        }

    MaterialTheme(typography = bubbleTypography) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = if (isHiddenPlaceholder) 0.dp else 4.dp)
    ) {
        // Display reply info above attachments if present
        replyInfo?.let { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    modifier = Modifier.padding(start = 32.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp, 8.dp, 2.dp, 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = context.getString(R.string.reply),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = "${reply.sender}: ${reply.content}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Display image links from pool
        if (imageLinks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 4.dp,
                        start = 32.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                imageLinks.forEach { imageLink ->
                    imageLink.bitmap?.let { bitmap ->
                        // 图片还在池子里，显示图片
                        Card(
                            modifier = Modifier
                                .size(120.dp)
                                .clickable(enabled = enableDialogs) {
                                    selectedImageBitmap.value = bitmap
                                    showImagePreview.value = true
                                },
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = context.getString(R.string.user_uploaded_image),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } ?: run {
                        // 图片已过期，显示为附件标签
                        AttachmentTag(
                            attachment = AttachmentData(
                                id = imageLink.id,
                                filename = context.getString(R.string.image_expired),
                                type = "image/*",
                                size = 0L,
                                content = ""
                            ),
                            textColor = textColor,
                            backgroundColor = backgroundColor,
                            enabled = enableDialogs
                        )
                    }
                }
            }
        }

        // Display trailing attachments above the message bubble
        if (trailingAttachments.isNotEmpty()) {
            // Display attachment row above the bubble
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailingAttachments.forEach { attachment ->
                    AttachmentTag(
                        attachment = attachment,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        enabled = enableDialogs,
                        onClick = { attachmentData ->
                            if (!enableDialogs) return@AttachmentTag
                            selectedChatAttachment.value =
                                ChatAttachment(
                                    id = attachmentData.id,
                                    filename = attachmentData.filename,
                                    mimeType = attachmentData.type,
                                    size = attachmentData.size,
                                    content = attachmentData.content
                                )
                            showContentPreview.value = true
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        // Message bubble
        if (bubbleWideLayoutEnabled) {
            val headerVisible = shouldShowAvatar || (shouldShowResolvedName && !resolvedDisplayName.isNullOrEmpty())

            if (headerVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (shouldShowResolvedName) {
                        resolvedDisplayName?.takeIf { it.isNotEmpty() }?.let { userName ->
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isProxySender) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (shouldShowAvatar) {
                        if (shouldShowResolvedName && !resolvedDisplayName.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (!avatarUri.isNullOrEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(avatarShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = if (isProxySender) Icons.Default.Assistant else Icons.Default.Person,
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(avatarShape),
                                tint = if (isProxySender) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxBubbleWidth = maxWidth
                val bubbleShape =
                    if (bubbleRoundedCornersEnabled) {
                        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
                    } else {
                        RoundedCornerShape(0.dp)
                    }
                val bubbleModifier =
                    Modifier
                        .widthIn(max = if (isHiddenPlaceholder) minOf(maxBubbleWidth, 320.dp) else maxBubbleWidth)
                        .defaultMinSize(minHeight = 44.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (effectiveBubbleImageStyle != null) {
                        BubbleImageBackgroundSurface(
                            imageStyle = effectiveBubbleImageStyle,
                            shape = bubbleShape,
                            modifier = bubbleModifier,
                            contentPadding =
                                PaddingValues(
                                    start = bubbleContentPaddingLeft.dp,
                                    top = if (isHiddenPlaceholder) 0.dp else 12.dp,
                                    end = bubbleContentPaddingRight.dp,
                                    bottom = if (isHiddenPlaceholder) 0.dp else 12.dp,
                                ),
                        ) {
                            if (isHiddenPlaceholder) {
                                HiddenUserMessagePlaceholderContent(
                                    titleColor = effectiveTextColor,
                                    subtitleColor = effectiveTextColor.copy(alpha = 0.72f),
                                )
                            } else {
                                Text(
                                    text = textContent,
                                    color = effectiveTextColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier =
                                bubbleModifier
                                    .waterGlass(
                                        enabled = waterGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = effectiveBackgroundColor,
                                        shadowElevation = 10.dp,
                                        borderWidth = 0.7.dp,
                                        overlayAlphaBoost = 0.08f,
                                    )
                                    .liquidGlass(
                                        enabled = liquidGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = effectiveBackgroundColor,
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
                                    effectiveBackgroundColor
                                },
                            tonalElevation =
                                if (liquidGlassEnabled || waterGlassEnabled || isHiddenPlaceholder) {
                                    0.dp
                                } else {
                                    2.dp
                                },
                        ) {
                            if (isHiddenPlaceholder) {
                                Box(
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 0.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 0.dp,
                                        ),
                                ) {
                                    HiddenUserMessagePlaceholderContent(
                                        titleColor = effectiveTextColor,
                                        subtitleColor = effectiveTextColor.copy(alpha = 0.72f),
                                    )
                                }
                            } else {
                                Text(
                                    text = textContent,
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 12.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 12.dp,
                                        ),
                                    color = effectiveTextColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            // 使用Column来垂直排列用户名和消息气泡
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(
                        start = 32.dp,
                        end = if (shouldShowAvatar) 0.dp else 8.dp
                    ),
                horizontalAlignment = Alignment.End
            ) {
                // 显示用户名（如果开启了显示选项并且设置了用户名）
                val displayName = resolvedDisplayName
                val shouldShowName = shouldShowResolvedName
                if (shouldShowName) {
                    displayName?.let { userName ->
                        if (userName.isNotEmpty()) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isProxySender) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
                
                // Message bubble
                BoxWithConstraints {
                    val maxBubbleWidth = maxWidth * 0.85f
                    val bubbleShape =
                        if (bubbleRoundedCornersEnabled) {
                            RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
                        } else {
                            RoundedCornerShape(0.dp)
                        }
                    val bubbleModifier =
                        Modifier
                            .widthIn(max = if (isHiddenPlaceholder) minOf(maxBubbleWidth, 320.dp) else maxBubbleWidth)
                            .defaultMinSize(minHeight = 44.dp)

                    if (effectiveBubbleImageStyle != null) {
                        BubbleImageBackgroundSurface(
                            imageStyle = effectiveBubbleImageStyle,
                            shape = bubbleShape,
                            modifier = bubbleModifier,
                            contentPadding =
                                PaddingValues(
                                    start = bubbleContentPaddingLeft.dp,
                                    top = if (isHiddenPlaceholder) 0.dp else 12.dp,
                                    end = bubbleContentPaddingRight.dp,
                                    bottom = if (isHiddenPlaceholder) 0.dp else 12.dp,
                                ),
                        ) {
                            if (isHiddenPlaceholder) {
                                HiddenUserMessagePlaceholderContent(
                                    titleColor = effectiveTextColor,
                                    subtitleColor = effectiveTextColor.copy(alpha = 0.72f),
                                )
                            } else {
                                Text(
                                    text = textContent,
                                    color = effectiveTextColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier =
                                bubbleModifier
                                    .waterGlass(
                                        enabled = waterGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = effectiveBackgroundColor,
                                        shadowElevation = 10.dp,
                                        borderWidth = 0.7.dp,
                                        overlayAlphaBoost = 0.08f,
                                    )
                                    .liquidGlass(
                                        enabled = liquidGlassEnabled,
                                        shape = bubbleShape,
                                        containerColor = effectiveBackgroundColor,
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
                                    effectiveBackgroundColor
                                },
                            tonalElevation =
                                if (liquidGlassEnabled || waterGlassEnabled || isHiddenPlaceholder) {
                                    0.dp
                                } else {
                                    2.dp
                                }
                        ) {
                            if (isHiddenPlaceholder) {
                                Box(
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 0.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 0.dp,
                                        ),
                                ) {
                                    HiddenUserMessagePlaceholderContent(
                                        titleColor = effectiveTextColor,
                                        subtitleColor = effectiveTextColor.copy(alpha = 0.72f),
                                    )
                                }
                            } else {
                                Text(
                                    text = textContent,
                                    modifier =
                                        Modifier.padding(
                                            start = bubbleContentPaddingLeft.dp,
                                            top = 12.dp,
                                            end = bubbleContentPaddingRight.dp,
                                            bottom = 12.dp,
                                        ),
                                    color = effectiveTextColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            if (shouldShowAvatar) {
                Spacer(modifier = Modifier.width(8.dp))
                // Avatar
                if (!avatarUri.isNullOrEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(avatarShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isProxySender) Icons.Default.Assistant else Icons.Default.Person,
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(avatarShape),
                        tint = if (isProxySender) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        }
    }
    }

    // 内容预览对话框
    if (enableDialogs && showContentPreview.value) {
        AttachmentViewerDialog(
            visible = true,
            attachment = selectedChatAttachment.value,
            onDismiss = { showContentPreview.value = false }
        )
    }

    // 图片预览对话框
    if (enableDialogs && showImagePreview.value && selectedImageBitmap.value != null) {
        Dialog(onDismissRequest = { showImagePreview.value = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 头部
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.image_preview),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        IconButton(onClick = { showImagePreview.value = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 图片显示区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        selectedImageBitmap.value?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

data class MessageParseResult(
    val processedText: String,
    val trailingAttachments: List<AttachmentData>,
    val replyInfo: ReplyInfo? = null,
    val imageLinks: List<ImageLinkData> = emptyList(),
    val proxySenderName: String? = null
)

data class ReplyInfo(
    val sender: String,
    val timestamp: Long,
    val content: String
)

data class ImageLinkData(
    val id: String,
    val bitmap: Bitmap?
)

/**
 * Parses the message content to extract text and attachments Keeps inline attachments as @filename
 * in the text Extracts trailing attachments that appear at the end of the message
 */
private fun parseMessageContent(context: android.content.Context, content: String): MessageParseResult {
    // First, strip out any <memory> tags so they are not displayed in the UI.
    var cleanedContent =
        content.replace(ChatMarkupRegex.memoryTag, "").trim()

    val proxySenderMatch = ChatMarkupRegex.proxySenderTag.find(cleanedContent)
    val proxySenderName = proxySenderMatch?.groupValues?.getOrNull(1)
    if (proxySenderMatch != null) {
        cleanedContent = cleanedContent.replace(proxySenderMatch.value, "").trim()
    }

    // Extract image link tags and load from pool
    val imageLinks = mutableListOf<ImageLinkData>()
    MediaLinkParser.extractImageLinkIds(cleanedContent).forEach { id ->
        val imageData = ImagePoolManager.getImage(id)
        val bitmap = imageData?.let {
            try {
                val bytes = Base64.decode(it.base64, Base64.DEFAULT)
                ImageBitmapLimiter.decodeDownsampledBitmap(bytes)
            } catch (e: Exception) {
                AppLogger.e("BubbleUserMessage", "Failed to decode image: $id", e)
                null
            }
        }
        imageLinks.add(ImageLinkData(id, bitmap))
    }
    cleanedContent = MediaLinkParser.removeImageLinks(cleanedContent).trim()

    val mediaLinkAttachments = mutableListOf<AttachmentData>()
    MediaLinkParser.extractMediaLinkTags(cleanedContent).forEach { tag ->
        val filename = if (tag.type == "audio") "Audio" else "Video"
        val mimeType = if (tag.type == "audio") "audio/*" else "video/*"
        mediaLinkAttachments.add(
            AttachmentData(
                id = "media_pool:${tag.id}",
                filename = filename,
                type = mimeType,
                size = 0L,
                content = ""
            )
        )
    }
    cleanedContent = MediaLinkParser.removeMediaLinks(cleanedContent).trim()

    // Extract reply information
    val replyMatch = ChatMarkupRegex.replyToTag.find(cleanedContent)
    val replyInfo = replyMatch?.let { match ->
        val fullContent = match.groupValues[3]
        // 指示语，用于从回复内容中提取纯净的预览文本
        val instruction = context.getString(R.string.chat_reply_instruction)
        val displayContent = fullContent
            .removePrefix(instruction)
            .trim()
            .removeSurrounding("\"")

        ReplyInfo(
            sender = match.groupValues[1],
            timestamp = match.groupValues[2].toLongOrNull() ?: 0L,
            content = displayContent
        )
    }
    
    // Remove reply tag from content
    cleanedContent = replyMatch?.let { 
        cleanedContent.replace(it.value, "").trim() 
    } ?: cleanedContent

    val workspaceAttachments = mutableListOf<AttachmentData>()
    // Extract workspace context as a special attachment
    val workspaceMatch = ChatMarkupRegex.workspaceAttachmentTag.find(cleanedContent)
    if (workspaceMatch != null) {
        val workspaceContent = workspaceMatch.value
        workspaceAttachments.add(
            AttachmentData(
                id = "workspace_context",
                filename = context.getString(R.string.chat_workspace_status),
                type = "application/vnd.workspace-context+xml",
                size = workspaceContent.length.toLong(),
                content = workspaceContent
            )
        )
        cleanedContent = cleanedContent.replace(workspaceContent, "").trim()
    }

    val attachments = mutableListOf<AttachmentData>()
    val trailingAttachments = mutableListOf<AttachmentData>()
    val messageText = StringBuilder()

    // 先用简单的分割方式检测有没有附件标签
    if (!cleanedContent.contains("<attachment")) {
        return MessageParseResult(
            cleanedContent,
            workspaceAttachments + mediaLinkAttachments,
            replyInfo,
            imageLinks,
            proxySenderName
        )
    }

    try {
        // Enhanced regex pattern to find attachments in both formats:
        // 1. New format (paired tags): <attachment ...>content</attachment>
        // 2. Old format (self-closing): <attachment ... content="..." />
        // 注意：优先匹配新格式（配对标签），回退到旧格式（自闭合标签）
        val pairedTagPattern = ChatMarkupRegex.attachmentDataTag
        val selfClosingPattern = ChatMarkupRegex.attachmentDataSelfClosingTag

        // Try to find matches with both patterns
        val pairedMatches = pairedTagPattern.findAll(cleanedContent).toList()
        val selfClosingMatches = selfClosingPattern.findAll(cleanedContent).toList()
        
        // Combine and sort all matches by position
        val allMatches = (pairedMatches.map { it to true } + selfClosingMatches.map { it to false })
                .sortedBy { it.first.range.first }
        
        // Remove overlapping matches (prefer paired tag format)
        val matches = mutableListOf<Pair<MatchResult, Boolean>>()
        var lastEnd = -1
        allMatches.forEach { (match, isPaired) ->
                if (match.range.first > lastEnd) {
                        matches.add(match to isPaired)
                        lastEnd = match.range.last
                }
        }
        
        if (matches.isEmpty()) {
                return MessageParseResult(
                    cleanedContent,
                    workspaceAttachments + mediaLinkAttachments,
                    replyInfo,
                    imageLinks,
                    proxySenderName
                )
        }

        // Determine which attachments form a contiguous block at the end
        val trailingAttachmentIndices = mutableSetOf<Int>()
        if (matches.isNotEmpty()) {
                val contentAfterLast = cleanedContent.substring(matches.last().first.range.last + 1)
                if (contentAfterLast.isBlank()) {
                        trailingAttachmentIndices.add(matches.size - 1)
                        for (i in matches.size - 2 downTo 0) {
                                val textBetween = cleanedContent.substring(matches[i].first.range.last + 1, matches[i + 1].first.range.first)
                                if (textBetween.isBlank()) {
                                        trailingAttachmentIndices.add(i)
                                } else {
                                        break
                                }
                        }
                }
        }

        // Process all attachments
        var lastIndex = 0
        matches.forEachIndexed { index, (matchResult, isPaired) ->
                // Add text before this attachment
                val startIndex = matchResult.range.first

                // Extract attachment data
                val id = matchResult.groupValues[1]
                val filename = matchResult.groupValues[2]
                val type = matchResult.groupValues[3]
                val size = matchResult.groupValues[4].toLongOrNull() ?: 0L
                // For paired tags, content is in group 5; for self-closing, it's also in group 5
                val attachmentContent = matchResult.groupValues[5]

                // Create attachment data object, including content if available
                val attachment =
                        AttachmentData(
                                id = id,
                                filename = filename,
                                type = type,
                                size = size,
                                content = attachmentContent
                        )

                val isTrailingAttachment = trailingAttachmentIndices.contains(index)

                // 特殊处理屏幕内容附件，始终将其作为trailing attachment
                val isScreenContent =
                        (type == "text/json" && filename == "screen_content.json")

                val shouldBeTrailing = isTrailingAttachment || isScreenContent

                if (startIndex > lastIndex) {
                        val textBefore = cleanedContent.substring(lastIndex, startIndex)
                        // Only append text if it's before an inline attachment,
                        // or if it's before the very first trailing attachment.
                        if (!shouldBeTrailing || (trailingAttachmentIndices.isNotEmpty() && index == trailingAttachmentIndices.minOrNull())) {
                                messageText.append(textBefore)
                        }
                }

                if (shouldBeTrailing) {
                        // This is a trailing attachment, extract it
                        trailingAttachments.add(attachment)
                } else {
                        // This is an inline attachment, keep it in the text as @filename
                        messageText.append("@${filename}")
                        // Also add to general attachments list for reference
                        attachments.add(attachment)
                }

                lastIndex = matchResult.range.last + 1
        }

        // Add any remaining text if the last part of the message was not a trailing attachment
        if (lastIndex < cleanedContent.length) {
                messageText.append(cleanedContent.substring(lastIndex))
        }

        trailingAttachments.addAll(0, mediaLinkAttachments)
        trailingAttachments.addAll(0, workspaceAttachments)
        return MessageParseResult(
            messageText.toString(),
            trailingAttachments,
            replyInfo,
            imageLinks,
            proxySenderName
        )
    } catch (e: Exception) {
        // 如果解析失败，返回原始内容
        com.ai.assistance.operit.util.AppLogger.e("BubbleUserMessageComposable", "Failed to parse message content", e)
        return MessageParseResult(
            cleanedContent,
            workspaceAttachments,
            replyInfo,
            imageLinks,
            proxySenderName
        )
    }
}

/** Data class for attachment information */
data class AttachmentData(
    val id: String,
    val filename: String,
    val type: String,
    val size: Long = 0,
    val content: String = "" // Added content field
)

/** Compact attachment tag component for displaying in user messages */
@Composable
private fun AttachmentTag(
    attachment: AttachmentData,
    textColor: Color,
    backgroundColor: Color,
    enabled: Boolean = true,
    onClick: (AttachmentData) -> Unit = {}
) {
    val context = LocalContext.current
    // 根据附件类型选择图标
    val icon: ImageVector =
        when {
            attachment.type.startsWith("image/") -> Icons.Default.Image
            attachment.type.startsWith("audio/") -> Icons.Default.VolumeUp
            attachment.type.startsWith("video/") -> Icons.Default.PlayArrow
            attachment.type == "text/json" && attachment.filename == "screen_content.json" ->
                Icons.Default.ScreenshotMonitor
            attachment.type == "application/vnd.workspace-context+xml" -> Icons.Default.Code
            else -> Icons.Default.Description
        }

    // 根据附件类型调整显示标签
    val displayLabel =
        when {
            attachment.type == "text/json" && attachment.filename == "screen_content.json" -> context.getString(R.string.screen_content)
            attachment.type == "application/vnd.workspace-context+xml" -> context.getString(R.string.workspace)
            else -> attachment.filename
        }

    Surface(
        modifier =
            Modifier.height(24.dp)
                .padding(vertical = 2.dp)
                .clickable(
                    enabled =
                        enabled &&
                            (
                                attachment.content.isNotEmpty() ||
                                    attachment.id.startsWith("/") ||
                                    attachment.id.startsWith("content://") ||
                                    attachment.id.startsWith("file://") ||
                                    attachment.id.startsWith("media_pool:") ||
                                    attachment.type.startsWith("image/")
                            ),
                    onClick = { onClick(attachment) }
                ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = textColor.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = displayLabel,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
    }
}
