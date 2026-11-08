package com.ai.assistance.operit.ui.features.support.demo.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PermissionStatusItem(
        title: String, 
        isGranted: Boolean, 
        onClick: () -> Unit,
        isHighlighted: Boolean = false
) {
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .let {
                        if (isHighlighted) {
                            it.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .padding(horizontal = 4.dp)
                        } else {
                            it
                        }
                    }
                    .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = if (isHighlighted) 
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.bodyMedium
        )

        Text(
                text = if (isGranted) "Authorized" else "Unauthorized",
                color = if (isGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
        )
    }
}
