package com.ai.assistance.operit.ui.features.support.agreement.screens

import androidx.compose.ui.platform.LocalConfiguration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable

@Composable
fun AgreementScreen() {
    val context = LocalContext.current
    // 正文由 Android 的本地化 raw 资源选择，语言变化后同步重新读取。
    val localeTags = LocalConfiguration.current.locales.toLanguageTags()
    val agreementMarkdown = remember(context, localeTags) {
        context.resources
            .openRawResource(R.raw.operit_user_agreement_v02)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(R.string.agreement_document_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            MarkdownTextComposable(
                text = agreementMarkdown,
                textColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                isSelectable = true
            )
        }
    }
}
