package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue

@Composable
fun GlassyChip(
    selected: Boolean,
    text: String,
    icon: ImageVector,
    showIcon: Boolean,
    showText: Boolean = true,
    iconContentDescription: String? = null,
    onClick: () -> Unit
) {
    // Brighter, high-contrast cyan for better visibility
    val accentColor = Color(0xFF00E5FF) 
    
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.25f),
        animationSpec = tween(300), 
        label = "chipBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(300),
        label = "chipBorder"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (selected) accentColor else Color.White.copy(alpha = 0.8f),
        animationSpec = tween(300),
        label = "chipContent"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                if (showText) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            if (showText) {
                Text(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)
                )
            }
        }
    }
}
