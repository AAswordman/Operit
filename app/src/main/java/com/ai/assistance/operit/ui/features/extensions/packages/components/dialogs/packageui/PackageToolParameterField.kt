package com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.PackageToolParameter

/** 参数说明常驻在输入框外，填写后仍可阅读；提示词继续支持多行输入。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PackageToolParameterField(
    parameter: PackageToolParameter,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val description = parameter.description.resolve(context)
    val normalizedName = parameter.name.trim().lowercase()
    val isPrompt = parameter.type.equals("string", ignoreCase = true) &&
        (normalizedName == "prompt" || normalizedName.endsWith("_prompt"))
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        // 键盘打开或高度变化后，让当前输入框和下方说明一起滚到可见区域。
        if (focused) bringIntoView.bringIntoView()
    }
    Column(
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text(parameter.name + if (parameter.required) " *" else "") },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            singleLine = !isPrompt,
            minLines = if (isPrompt) 3 else 1,
            maxLines = if (isPrompt) 8 else 1,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
