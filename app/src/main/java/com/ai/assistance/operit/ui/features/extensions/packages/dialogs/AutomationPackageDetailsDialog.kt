package com.ai.assistance.operit.ui.features.extensions.packages.dialogs

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationPackageDetailsDialog(
    onDismiss: () -> Unit,
    message: String? = null
) {
    val context = LocalContext.current
    val displayMessage = message ?: stringResource(R.string.pkg_automation_removed)
    AlertDialog(
        onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.pkg_tip)) },
        text = {
            Text(
                text = displayMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.ok))
            }
        }
    )
}