package com.ai.assistance.operit.ui.features.extensions.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

private const val MARKET_AGREEMENT_VERSION = "2026-08-08.1"

@Composable
fun MarketAgreementDialog(onDismissRequest: () -> Unit) {
    // 由市场菜单主动打开，只提供阅读和关闭，不拦截进入市场。
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.market_agreement_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.market_agreement_version, MARKET_AGREEMENT_VERSION),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(stringResource(R.string.market_agreement_content), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.close)) }
        }
    )
}
