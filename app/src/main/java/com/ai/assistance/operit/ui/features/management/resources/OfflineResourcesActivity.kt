package com.ai.assistance.operit.ui.features.management.resources

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.ui.features.management.resources.hub.ResourceHubScreen
import com.ai.assistance.operit.ui.features.management.resources.hub.ResourceHubViewModel
import com.ai.assistance.operit.ui.theme.OperitTheme
import kotlinx.coroutines.flow.MutableStateFlow

class OfflineResourcesActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GROUP = "resource_group"
    }
    private val resumeEvents = MutableStateFlow(0)
    override fun onResume() {
        super.onResume()
        resumeEvents.value += 1
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[ResourceHubViewModel::class.java]
        setContent {
            OperitTheme {
                val resume by resumeEvents.collectAsState()
                val maintenance by TerminalManager.environmentMaintenanceState.collectAsState()
                LaunchedEffect(resume, maintenance) { model.onResume(maintenance) }
                ResourceHubScreen(model, intent.getStringExtra(EXTRA_GROUP), maintenance, ::finish)
            }
        }
    }
}
