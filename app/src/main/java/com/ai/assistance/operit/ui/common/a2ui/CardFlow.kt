package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

internal data class CardFlowStep(val title: String, val caption: String, val icon: String?, val tone: String?)

internal val cardFlowIcons: Map<String, ImageVector> = mapOf(
    "check" to Icons.Outlined.CheckCircle, "info" to Icons.Outlined.Info, "settings" to Icons.Outlined.Settings,
    "person" to Icons.Outlined.Person, "home" to Icons.Outlined.Home, "star" to Icons.Outlined.StarOutline,
    "warning" to Icons.Outlined.WarningAmber, "search" to Icons.Outlined.Search,
    "calendar" to Icons.Outlined.CalendarToday, "folder" to Icons.Outlined.Folder, "edit" to Icons.Outlined.Edit,
    "calendarToday" to Icons.Outlined.CalendarToday,
    "play" to Icons.Outlined.PlayCircle, "flag" to Icons.Outlined.Flag, "time" to Icons.Outlined.Schedule,
    "list" to Icons.Outlined.Checklist, "fire" to Icons.Outlined.LocalFireDepartment,
    "food" to Icons.Outlined.Restaurant, "code" to Icons.Outlined.Code, "upload" to Icons.Outlined.CloudUpload,
    "download" to Icons.Outlined.CloudDownload, "lock" to Icons.Outlined.Lock,
    "chat" to Icons.Outlined.ChatBubbleOutline, "build" to Icons.Outlined.Build,
    "phone" to Icons.Outlined.Smartphone, "smartphone" to Icons.Outlined.Smartphone,
    "globe" to Icons.Outlined.Language, "language" to Icons.Outlined.Language,
    "browser" to Icons.Outlined.Web, "computer" to Icons.Outlined.Computer,
    "terminal" to Icons.Outlined.Terminal, "server" to Icons.Outlined.Dns,
    "storage" to Icons.Outlined.Storage, "cloud" to Icons.Outlined.Cloud,
    "extension" to Icons.Outlined.Extension, "shield" to Icons.Outlined.Shield,
    "mic" to Icons.Outlined.MicNone, "audio" to Icons.Outlined.VolumeUp,
    "image" to Icons.Outlined.Image, "video" to Icons.Outlined.Videocam,
    "document" to Icons.Outlined.Description, "link" to Icons.Outlined.Link,
    "chart" to Icons.Outlined.BarChart, "science" to Icons.Outlined.Science,
    "lightbulb" to Icons.Outlined.Lightbulb, "robot" to Icons.Outlined.SmartToy,
    "wifi" to Icons.Outlined.Wifi, "delete" to Icons.Outlined.DeleteOutline,
    // Preserve names supported by cards saved with the original AndroidX catalog.
    "accountCircle" to Icons.Outlined.AccountCircle, "add" to Icons.Outlined.Add,
    "arrowBack" to Icons.AutoMirrored.Outlined.ArrowBack, "arrowForward" to Icons.AutoMirrored.Outlined.ArrowForward,
    "attachFile" to Icons.Outlined.AttachFile, "call" to Icons.Outlined.Call,
    "camera" to Icons.Outlined.CameraAlt, "close" to Icons.Outlined.Close,
    "error" to Icons.Outlined.ErrorOutline, "event" to Icons.Outlined.Event,
    "fastForward" to Icons.Outlined.FastForward, "favorite" to Icons.Outlined.FavoriteBorder,
    "favoriteOff" to Icons.Outlined.FavoriteBorder, "help" to Icons.Outlined.HelpOutline,
    "locationOn" to Icons.Outlined.LocationOn, "lockOpen" to Icons.Outlined.LockOpen,
    "mail" to Icons.Outlined.MailOutline, "menu" to Icons.Outlined.Menu,
    "moreHoriz" to Icons.Outlined.MoreHoriz, "moreVert" to Icons.Outlined.MoreVert,
    "notifications" to Icons.Outlined.NotificationsNone, "notificationsOff" to Icons.Outlined.NotificationsOff,
    "pause" to Icons.Outlined.PauseCircleOutline, "payment" to Icons.Outlined.Payment,
    "photo" to Icons.Outlined.Photo, "print" to Icons.Outlined.Print,
    "refresh" to Icons.Outlined.Refresh, "rewind" to Icons.Outlined.FastRewind,
    "send" to Icons.AutoMirrored.Outlined.Send, "share" to Icons.Outlined.Share,
    "shoppingCart" to Icons.Outlined.ShoppingCart, "skipNext" to Icons.Outlined.SkipNext,
    "skipPrevious" to Icons.Outlined.SkipPrevious, "starHalf" to Icons.Outlined.StarHalf,
    "starOff" to Icons.Outlined.StarOutline, "stop" to Icons.Outlined.StopCircle,
    "visibility" to Icons.Outlined.Visibility, "visibilityOff" to Icons.Outlined.VisibilityOff,
    "volumeDown" to Icons.AutoMirrored.Outlined.VolumeDown, "volumeMute" to Icons.AutoMirrored.Outlined.VolumeMute,
    "volumeOff" to Icons.AutoMirrored.Outlined.VolumeOff, "volumeUp" to Icons.AutoMirrored.Outlined.VolumeUp,
)
private val flowTones = setOf("neutral", "accent", "success", "info", "warning", "error")
private val flowKeys = setOf("title", "caption", "icon", "tone")

/** Static step diagram data. Bounded so a model cannot produce an unreadable chain. */
internal fun cardFlowSteps(raw: Any?): List<CardFlowStep> {
    val items = raw as? List<*> ?: error("Expected flow steps")
    require(items.size in 1..8) { "Flow needs 1..8 steps" }
    return items.map { item ->
        val step = item as? Map<*, *> ?: error("Invalid flow step")
        require(step.keys.all { it in flowKeys }) { "Unknown flow step field" }
        val title = step["title"] as? String ?: error("Missing flow step title")
        require(title.isNotBlank() && title.length <= 80) { "Invalid flow step title" }
        val caption = step["caption"]?.let { it as? String ?: error("Invalid flow caption") }.orEmpty()
        require(caption.length <= 160) { "Flow caption too long" }
        val icon = step["icon"]?.let { it as? String ?: error("Invalid flow icon") }
        require(icon == null || CardIcons.contains(icon)) { "Unknown flow icon" }
        val tone = step["tone"]?.let { it as? String ?: error("Invalid flow tone") }
        require(tone == null || tone in flowTones) { "Unknown flow tone" }
        CardFlowStep(title, caption, icon, tone)
    }
}

/** Native static flow: tiles joined by arrows, horizontal while each tile still fits comfortably. */
internal object CardFlow : A2uiComponent {
    override val name = "Flow"
    override val description = "Static step/flow diagram with arrows. No state or actions."
    private val label = A2uiProperty.dynamicString("label")
    private val steps = A2uiProperty.dynamicValue("steps")
    private val direction = A2uiProperty.dynamicString("direction")
    override val properties = listOf(label, steps, direction)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val raw = properties.bind(steps)
        val parsed = remember(raw) { runCatching { cardFlowSteps(raw) } }
        val description = properties.bind(label).orEmpty()
        val items = parsed.getOrNull()
        if (items == null) {
            Text(stringResource(R.string.a2ui_invalid), modifier, color = MaterialTheme.colorScheme.error)
            return
        }
        val requested = properties.bind(direction) ?: "auto"
        val fontScale = LocalDensity.current.fontScale
        // One summary for screen readers; the tile texts are not read a second time.
        BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = (listOf(description).filter(String::isNotBlank) +
                items.mapIndexed { i, s -> "${i + 1}. ${s.title}${if (s.caption.isBlank()) "" else ", ${s.caption}"}" })
                .joinToString("; ")
        }) {
            // Each horizontal tile needs room for a short title; otherwise stack vertically.
            val tileMin = 84.dp * fontScale.coerceAtLeast(1f)
            val fits = maxWidth >= tileMin * items.size + 24.dp * (items.size - 1)
            val horizontal = when (requested) { "horizontal" -> fits; "vertical" -> false; else -> fits && items.size <= 4 }
            if (horizontal) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, step ->
                    FlowTile(step, Modifier.weight(1f).fillMaxHeight(), centered = true)
                    if (i < items.lastIndex) Icon(Icons.AutoMirrored.Outlined.ArrowForward, null,
                        Modifier.padding(horizontal = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                items.forEachIndexed { i, step ->
                    FlowTile(step, Modifier.fillMaxWidth(), centered = false)
                    if (i < items.lastIndex) Icon(Icons.Outlined.ArrowDownward, null,
                        Modifier.padding(vertical = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FlowTile(step: CardFlowStep, modifier: Modifier, centered: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Accent tiles keep the accent on the icon; their title uses the container ink so it stays readable.
    val (ink, fill) = when (step.tone) {
        null, "neutral" -> scheme.onSurface to scheme.surfaceContainerHigh
        "accent" -> scheme.onPrimaryContainer to scheme.primaryContainer.copy(alpha = .55f)
        else -> cardToneColors(step.tone, scheme)
    }
    val iconTint = if (step.tone == "accent") ensureReadableOn(scheme.primary, fill.compositeOver(scheme.surface), 3.0) else ink
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = fill) {
        val icon = CardIcons.get(step.icon)
        if (centered) Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            icon?.let { Icon(it, null, Modifier.size(20.dp), tint = iconTint) }
            Text(step.title, color = ink, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (step.caption.isNotBlank()) Text(step.caption, color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        } else Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon?.let { Icon(it, null, Modifier.size(20.dp), tint = iconTint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(step.title, color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (step.caption.isNotBlank()) Text(step.caption, color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
