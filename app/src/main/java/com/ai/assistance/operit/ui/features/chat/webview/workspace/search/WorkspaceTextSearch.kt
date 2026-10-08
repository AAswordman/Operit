package com.ai.assistance.operit.ui.features.chat.webview.workspace.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 返回原文中的字面匹配位置，高亮与复制均不修改文件内容。 */
internal fun findTextMatches(content: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<IntRange>()
    var offset = 0
    while (offset <= content.length - query.length) {
        val found = content.indexOf(query, offset, ignoreCase = true)
        if (found < 0) break
        matches += found until found + query.length
        offset = found + query.length
    }
    return matches
}

/** 重算匹配后保留当前序号；移除匹配时只调整到仍有效的范围。 */
internal fun activeSearchMatchAfterUpdate(active: Int, count: Int): Int =
    if (count == 0) -1 else active.coerceIn(0, count - 1)

@Composable
internal fun FileSearchBar(query: String, onQueryChange: (String) -> Unit, count: Int, active: Int,
    onPrevious: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(query, onQueryChange, modifier = Modifier.weight(1f).focusRequester(focus), singleLine = true,
            label = { Text(stringResource(R.string.workspace_search_content)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }))
        Text("${if (count == 0) 0 else active + 1}/$count", modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onPrevious, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.workspace_search_previous)) }
        IconButton(onClick = onNext, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.workspace_search_next)) }
        IconButton(onClick = { keyboard?.hide(); onClose() }) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
    }
}
