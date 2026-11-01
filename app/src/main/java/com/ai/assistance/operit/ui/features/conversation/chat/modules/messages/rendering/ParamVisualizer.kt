package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ParamItem(val name: String, val value: String)

private fun unescapeXml(input: String): String {
    var result = input

    if (result.startsWith("<![CDATA[") && result.endsWith("]]>") ) {
        result = result.substring(9, result.length - 3)
    }

    if (result.endsWith("]]>") ) {
        result = result.substring(0, result.length - 3)
    }

    if (result.startsWith("<![CDATA[") ) {
        result = result.substring(9)
    }

    return result.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}

@Composable
fun ParamVisualizer(xmlContent: String) {
    val params = remember(xmlContent) {
        val paramRegex = """<param\s+name="([^"]+)">(.*?)</param>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        paramRegex.findAll(xmlContent).map {
            ParamItem(name = it.groupValues[1], value = unescapeXml(it.groupValues[2].trim()))
        }.toList()
    }

    if (params.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(params) { param ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    val clipboardManager = LocalClipboardManager.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = param.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            modifier = Modifier.size(24.dp), // 限制IconButton的大小
                            onClick = {
                                clipboardManager.setText(AnnotatedString(param.value))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy value",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp) // 限制Icon的大小
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = param.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        // 如果无法解析出参数，或者内容不是预期的XML格式，则直接显示原始内容
        Text(
            text = xmlContent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}