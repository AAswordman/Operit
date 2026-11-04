package com.ai.assistance.operit.ui.features.extensions.packages.market

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeTextField as TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.LocalTopBarActions
import com.ai.assistance.operit.ui.main.LocalTopBarTitleContent
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen

@Composable
fun BindMarketSearchToTopBar(
    enabled: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    @StringRes searchPlaceholderRes: Int,
    isSearching: Boolean = false,
    additionalActions: @Composable () -> Unit = {}
) {
    val setTopBarActions = LocalTopBarActions.current
    val setTopBarTitleContent = LocalTopBarTitleContent.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val appBarContentColor = LocalAppBarContentColor.current
    val latestSearchQuery = rememberUpdatedState(searchQuery)
    val latestSearchPlaceholderRes = rememberUpdatedState(searchPlaceholderRes)
    val latestIsSearching = rememberUpdatedState(isSearching)
    val latestAppBarContentColor = rememberUpdatedState(appBarContentColor)
    val latestOnSearchQueryChanged = rememberUpdatedState(onSearchQueryChanged)
    val latestAdditionalActions = rememberUpdatedState(additionalActions)
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val isSearchActive = enabled && (isSearchExpanded || searchQuery.isNotBlank())

    fun closeSearch(clearQuery: Boolean) {
        isSearchExpanded = false
        if (clearQuery && latestSearchQuery.value.isNotEmpty()) {
            latestOnSearchQueryChanged.value("")
        }
    }

    LaunchedEffect(isCurrentScreen, enabled) {
        if (isCurrentScreen && !enabled && (isSearchExpanded || searchQuery.isNotBlank())) {
            closeSearch(clearQuery = true)
        }
    }

    LaunchedEffect(
        isCurrentScreen,
        enabled,
        isSearchActive,
        searchQuery,
        searchPlaceholderRes,
        isSearching
    ) {
        if (!isCurrentScreen) {
            return@LaunchedEffect
        }

        if (!enabled) {
            setTopBarActions {}
            setTopBarTitleContent(null)
            return@LaunchedEffect
        }

        setTopBarActions {
            if (!isSearchActive) {
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = latestAppBarContentColor.value
                    )
                }
            }
            latestAdditionalActions.value()
        }

        setTopBarTitleContent(
            if (isSearchActive) {
                TopBarTitleContent {
                    MarketTopBarSearchField(
                        searchQuery = latestSearchQuery.value,
                        onSearchQueryChanged = latestOnSearchQueryChanged.value,
                        onCloseSearch = { closeSearch(clearQuery = true) },
                        searchPlaceholderRes = latestSearchPlaceholderRes.value,
                        contentColor = latestAppBarContentColor.value,
                        isSearching = latestIsSearching.value
                    )
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun MarketTopBarSearchField(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onCloseSearch: () -> Unit,
    @StringRes searchPlaceholderRes: Int,
    contentColor: Color,
    isSearching: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        )
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery != textFieldValue.text) {
            textFieldValue =
                TextFieldValue(
                    text = searchQuery,
                    selection = TextRange(searchQuery.length)
                )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    TextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (newValue.text != searchQuery) {
                onSearchQueryChanged(newValue.text)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = stringResource(searchPlaceholderRes),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor.copy(alpha = 0.9f)
                )
            } else {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        ),
        colors = TextFieldDefaults.colors(
            focusedTextColor = contentColor,
            unfocusedTextColor = contentColor,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            cursorColor = contentColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedPlaceholderColor = contentColor.copy(alpha = 0.72f),
            unfocusedPlaceholderColor = contentColor.copy(alpha = 0.72f),
            focusedLeadingIconColor = contentColor.copy(alpha = 0.9f),
            unfocusedLeadingIconColor = contentColor.copy(alpha = 0.9f),
            focusedTrailingIconColor = contentColor.copy(alpha = 0.9f),
            unfocusedTrailingIconColor = contentColor.copy(alpha = 0.9f)
        )
    )
}
