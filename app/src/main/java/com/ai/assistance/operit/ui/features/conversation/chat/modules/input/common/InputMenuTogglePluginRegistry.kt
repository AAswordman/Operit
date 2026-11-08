package com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common

import androidx.annotation.StringRes
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object InputMenuToggleSlots {
    const val THINKING = "thinking"
    const val MEMORY = "memory"
    const val MODEL = "model"
    const val TOOLS = "tools"
    const val GENERAL = "general"
    const val DEFAULT = "default"

    val builtInSlots = setOf(THINKING, MEMORY, MODEL, TOOLS, GENERAL, DEFAULT)

    fun normalize(slot: String?): String {
        val normalized = slot?.trim().orEmpty()
        return if (normalized in builtInSlots) normalized else DEFAULT
    }
}

data class InputMenuToggleHookParams(
    val context: android.content.Context,
    val chatId: String?,
    val featureStates: Map<String, Boolean>,
    val onToggleFeature: (String) -> Unit,
    val runtime: String? = null
)

data class InputMenuToggleDefinition(
    val id: String,
    @StringRes val titleRes: Int = 0,
    @StringRes val descriptionRes: Int = 0,
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val isChecked: Boolean,
    val isEnabled: Boolean = true,
    val slot: String? = null,
    val onToggle: () -> Unit
)

interface InputMenuTogglePlugin {
    val id: String

    fun createToggles(params: InputMenuToggleHookParams): List<InputMenuToggleDefinition>
}

object InputMenuTogglePluginRegistry {
    private val plugins = CopyOnWriteArrayList<InputMenuTogglePlugin>()
    private val changeVersionMutable = MutableStateFlow(0)
    val changeVersion: StateFlow<Int> = changeVersionMutable.asStateFlow()

    @Synchronized
    fun register(plugin: InputMenuTogglePlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
        notifyChanged()
    }

    @Synchronized
    fun unregister(pluginId: String) {
        val changed = plugins.removeAll { it.id == pluginId }
        if (changed) {
            notifyChanged()
        }
    }

    fun notifyChanged() {
        changeVersionMutable.update { current -> current + 1 }
    }

    fun createToggles(params: InputMenuToggleHookParams): List<InputMenuToggleDefinition> {
        return plugins.flatMap { plugin -> plugin.createToggles(params) }
    }
}
