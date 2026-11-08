package com.ai.assistance.operit.integrations.appwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.ui.theme.OperitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopWidgetHost

class ToolPkgDesktopWidgetConfigActivity : ComponentActivity() {
    private var configuredAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var shouldRefreshAfterFinish: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        configuredAppWidgetId = appWidgetId

        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            OperitTheme {
                ToolPkgDesktopWidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    onCancel = { finish() },
                    onSelect = { widget ->
                        val saved = ToolPkgDesktopWidgetHost.saveSelection(
                            context = this,
                            appWidgetId = appWidgetId,
                            widget = widget
                        )
                        if (!saved) {
                            setResult(RESULT_CANCELED)
                            finish()
                            return@ToolPkgDesktopWidgetConfigScreen
                        }
                        shouldRefreshAfterFinish = true
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing || !shouldRefreshAfterFinish) {
            return
        }
        shouldRefreshAfterFinish = false
        val targetContext = applicationContext
        lifecycleScope.launch {
            // Wait until the launcher has created and attached the widget instance.
            delay(300)
            ToolPkgDesktopWidgetHost.refreshAll(targetContext)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ToolPkgDesktopWidgetConfigScreen(
    appWidgetId: Int,
    onCancel: () -> Unit,
    onSelect: (PackageManager.ToolPkgDesktopWidget) -> Unit
) {
    val context = LocalContext.current
    val widgets by produceState<List<PackageManager.ToolPkgDesktopWidget>>(initialValue = emptyList(), appWidgetId) {
        value =
            withContext(Dispatchers.IO) {
                ToolPkgDesktopWidgetHost.listAvailableWidgets(context)
            }
    }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text(stringResource(R.string.toolpkg_widget_picker_title)) },
                actions = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.toolpkg_widget_picker_cancel))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (widgets.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.toolpkg_widget_picker_empty_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.toolpkg_widget_picker_empty_body),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = widgets,
                key = { widget ->
                    ToolPkgDesktopWidgetHost.buildSelectionKey(
                        widget.containerPackageName,
                        widget.widgetId
                    )
                }
            ) { widget ->
                val selectionKey =
                    ToolPkgDesktopWidgetHost.buildSelectionKey(
                        widget.containerPackageName,
                        widget.widgetId
                    )
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedKey = selectionKey
                                onSelect(widget)
                            }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = widget.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (widget.subtitle.isNotBlank()) {
                            Text(
                                text = widget.subtitle,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = widget.containerPackageName,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (widget.description.isNotBlank()) {
                            Text(
                                text = widget.description,
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selectedKey == selectionKey) {
                            Text(
                                text = stringResource(R.string.toolpkg_widget_picker_selecting),
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
