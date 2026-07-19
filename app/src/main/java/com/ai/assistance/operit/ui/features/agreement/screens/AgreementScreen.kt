package com.ai.assistance.operit.ui.features.agreement.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import kotlinx.coroutines.delay

@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit) {
        val scrollState = rememberScrollState()
        var isButtonEnabled by remember { mutableStateOf(false) }
        var remainingSeconds by remember { mutableStateOf(5) }

        // Timer to enable the button after 5 seconds
        LaunchedEffect(Unit) {
                repeat(5) {
                        delay(1000)
                        remainingSeconds--
                }
                isButtonEnabled = true
        }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(16.dp)
                                .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        text = stringResource(R.string.agreement_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text = stringResource(R.string.agreement_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = stringResource(
                                R.string.agreement_version,
                                AgreementPreferences.CURRENT_AGREEMENT_VERSION
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Agreement text content
                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                shape = MaterialTheme.shapes.medium
                                        )
                                        .padding(16.dp)
                ) {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                                // Human-readable version
                                Text(
                                        text = stringResource(R.string.agreement_human_readable_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = stringResource(R.string.agreement_human_readable_content),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(24.dp))

                                // Serious version
                                Text(
                                        text = stringResource(R.string.agreement_serious_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                val typography = MaterialTheme.typography.bodyMedium
                                AndroidView(
                                        factory = { context ->
                                                android.widget.TextView(context).apply {
                                                        setTextColor(textColor.toArgb())
                                                        textSize = typography.fontSize.value
                                                        val lineHeightInPixels = (typography.lineHeight.value * context.resources.displayMetrics.scaledDensity).toInt()
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                                                lineHeight = lineHeightInPixels
                                                        } else {
                                                                // Fallback for older APIs
                                                                setLineSpacing(lineHeightInPixels - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent.toFloat(), 1.0f)
                                                        }
                                                }
                                        },
                                        update = { textView ->
                                                textView.text = HtmlCompat.fromHtml(
                                                        textView.context.getString(R.string.agreement_serious_content),
                                                        HtmlCompat.FROM_HTML_MODE_COMPACT
                                                )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(24.dp))

                                // Disclaimer
                                Text(
                                        text = stringResource(R.string.agreement_disclaimer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Agreement button
                Button(
                        onClick = onAgreementAccepted,
                        enabled = isButtonEnabled,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.5f
                                                ),
                                        disabledContentColor =
                                                MaterialTheme.colorScheme.onPrimary.copy(
                                                        alpha = 0.7f
                                                )
                                )
                ) {
                        Text(
                                text =
                                        if (isButtonEnabled)
                                            stringResource(R.string.agreement_accept)
                                        else
                                            stringResource(R.string.agreement_wait, remainingSeconds),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))
        }
}
