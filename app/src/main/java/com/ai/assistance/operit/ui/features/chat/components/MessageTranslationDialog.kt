package com.ai.assistance.operit.ui.features.chat.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.viewmodel.TranslationUiState
import com.ai.assistance.operit.util.LocaleUtils

private data class TranslationLanguage(
    val code: String,
    val nameResId: Int,
    val promptName: String,
)

// Keep translation targets aligned with the app language set, plus Japanese.
private val supportedTranslationLanguages =
    listOf(
        TranslationLanguage("zh", R.string.conversation_language_chinese, "Chinese"),
        TranslationLanguage("en", R.string.translation_language_english, "English"),
        TranslationLanguage("ko", R.string.translation_language_korean, "Korean"),
        TranslationLanguage("es", R.string.translation_language_spanish, "Spanish"),
        TranslationLanguage("ms", R.string.translation_language_malay, "Malay"),
        TranslationLanguage("id", R.string.translation_language_indonesian, "Indonesian"),
        TranslationLanguage(
            "pt-BR",
            R.string.translation_language_portuguese,
            "Portuguese (Brazil)",
        ),
        TranslationLanguage("ro", R.string.translation_language_romanian, "Romanian"),
        TranslationLanguage("ja", R.string.translation_language_japanese, "Japanese"),
    )

@Composable
fun MessageTranslationDialog(
    originalText: String,
    translationState: TranslationUiState,
    onTargetLanguageChanged: (String) -> Unit,
    onTranslate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentLanguageCode = remember { LocaleUtils.getCurrentLanguage(context) }
    var selectedLanguage by remember(originalText) {
        mutableStateOf(
            supportedTranslationLanguages.firstOrNull { it.code == currentLanguageCode }
                ?: supportedTranslationLanguages.first()
        )
    }

    LaunchedEffect(originalText, selectedLanguage.code) {
        onTargetLanguageChanged(selectedLanguage.code)
    }

    val copyText: (String, Int) -> Unit = { text, messageResId ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show()
    }
    val translatedText = translationState.translatedText
    val hasTranslation = !translatedText.isNullOrBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.translation_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.translation_target_language),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { languageMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !translationState.isLoading,
                        ) {
                            Text(
                                text = stringResource(selectedLanguage.nameResId),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                        ) {
                            supportedTranslationLanguages.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(language.nameResId)) },
                                    onClick = {
                                        selectedLanguage = language
                                        languageMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    TranslationSectionHeader(
                        title = stringResource(R.string.translation_original),
                        copyContentDescription =
                            stringResource(R.string.translation_copy_original),
                        onCopy = {
                            copyText(originalText, R.string.translation_original_copied)
                        },
                    )
                    TranslationTextBlock(text = originalText)

                    TranslationSectionHeader(
                        title = stringResource(R.string.translation_result),
                        status =
                            if (translationState.isFromCache) {
                                stringResource(R.string.translation_cached_result)
                            } else {
                                null
                            },
                        copyContentDescription =
                            stringResource(R.string.translation_copy_result),
                        onCopy =
                            translatedText?.takeIf { it.isNotBlank() }?.let { text ->
                                { copyText(text, R.string.translation_result_copied) }
                            },
                    )
                    TranslationResultBlock(translationState = translationState)
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onTranslate(selectedLanguage.code, selectedLanguage.promptName)
                        },
                        enabled = originalText.isNotBlank() && !translationState.isLoading,
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(
                                when {
                                    translationState.errorMessage != null ->
                                        R.string.translation_retry_action
                                    hasTranslation -> R.string.translation_retranslate_action
                                    else -> R.string.translation_translate_action
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationSectionHeader(
    title: String,
    status: String? = null,
    copyContentDescription: String,
    onCopy: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (status != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = copyContentDescription,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TranslationTextBlock(text: String) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 200.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Text(
            text = text,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TranslationResultBlock(translationState: TranslationUiState) {
    val translatedText = translationState.translatedText
    val hasText = !translatedText.isNullOrBlank()
    val contentAlignment =
        if (!hasText && translationState.isLoading) {
            Alignment.Center
        } else {
            Alignment.TopStart
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 260.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            contentAlignment = contentAlignment,
        ) {
            when {
                translationState.isLoading -> {
                    if (hasText) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.translation_loading))
                            }
                            Text(
                                text = translatedText.orEmpty(),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 210.dp)
                                        .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.translation_loading))
                        }
                    }
                }
                !translatedText.isNullOrBlank() -> {
                    Text(
                        text = translatedText,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.translation_waiting),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
