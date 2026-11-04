package com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** 用屏幕和键盘上方的实际可用区域约束弹窗，不再使用平台默认窄窗口。 */
@Composable
internal fun PackageDialogLayout(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                // 全窗口布局中显式处理外部点击，内部表面拦截空白点击。
                .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 420.dp || maxWidth < 340.dp
            Surface(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight * 0.94f).coerceAtMost(900.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (compact) 16.dp else 24.dp),
                ) {
                    content(compact)
                }
            }
        }
    }
}

/** 常规窗口居中展示标题，小高度窗口改为单行标题以保留表单空间。 */
@Composable
internal fun PackageDialogHeader(
    title: String,
    icon: ImageVector,
    compact: Boolean,
    onDismiss: () -> Unit,
    logo: (@Composable () -> Unit)? = null,
) {
    val emblem: @Composable () -> Unit = {
        if (logo != null) {
            logo()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emblem()
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.pkg_close))
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                emblem()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.pkg_close))
            }
        }
    }
}
