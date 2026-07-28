package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import kotlinx.coroutines.yield

internal enum class ThemeSettingsTab(val titleRes: Int) {
    BASIC(R.string.theme_tab_basic),
    BACKGROUND(R.string.theme_tab_background),
    CHAT(R.string.theme_tab_chat),
    INPUT(R.string.theme_tab_input),
    INTERFACE(R.string.theme_tab_interface),
}

@Composable
internal fun ThemeSettingsTabbedContent(
    selectedTab: ThemeSettingsTab,
    onSelectedTabChange: (ThemeSettingsTab) -> Unit,
    basicContent: @Composable () -> Unit,
    backgroundContent: @Composable () -> Unit,
    chatContent: @Composable () -> Unit,
    inputContent: @Composable () -> Unit,
    interfaceContent: @Composable () -> Unit,
    footerContent: @Composable () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var renderedTab by remember { mutableStateOf(selectedTab) }
    var isSwitchingTab by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (renderedTab != selectedTab) {
            isSwitchingTab = true
            yield()
            scrollState.scrollTo(0)
            renderedTab = selectedTab
            isSwitchingTab = false
        }
    }

    Column(modifier = modifier) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemeSettingsTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onSelectedTabChange(tab) },
                    text = { Text(text = stringResource(id = tab.titleRes)) },
                )
            }
        }

        if (isSwitchingTab) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
        ) {
            when (renderedTab) {
                ThemeSettingsTab.BASIC -> basicContent()
                ThemeSettingsTab.BACKGROUND -> backgroundContent()
                ThemeSettingsTab.CHAT -> chatContent()
                ThemeSettingsTab.INPUT -> inputContent()
                ThemeSettingsTab.INTERFACE -> interfaceContent()
            }

            footerContent()
        }
    }
}

@Composable
internal fun ThemeSettingsFooter(
    showSaveSuccessMessage: Boolean,
    onShowSaveSuccessMessageChange: (Boolean) -> Unit,
    saveEnabled: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Button(
        onClick = onSave,
        enabled = saveEnabled,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(stringResource(id = R.string.save_action))
    }

    OutlinedButton(
        onClick = onReset,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
    ) {
        Text(stringResource(id = R.string.theme_reset))
    }

    if (showSaveSuccessMessage) {
        LaunchedEffect(key1 = showSaveSuccessMessage) {
            kotlinx.coroutines.delay(2000)
            onShowSaveSuccessMessageChange(false)
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(id = R.string.theme_saved),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
