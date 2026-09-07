package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import java.util.concurrent.atomic.AtomicBoolean

internal object ToolPkgAiProviderRegistry {
    private val installed = AtomicBoolean(false)

    @Volatile
    private var providersById: Map<String, ToolPkgAiProviderRegistration> = emptyMap()

    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            syncToolPkgRegistrations(activeContainers)
        }

    fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
    }

    fun get(providerId: String): ToolPkgAiProviderRegistration? {
        register()
        return providersById[providerId.trim().lowercase()]
    }

    fun list(): List<ToolPkgAiProviderRegistration> {
        register()
        return providersById.values.toList()
    }

    fun releasedTokenProviderAliases(): Map<String, String> =
        buildMap {
            list().forEach { registration ->
                registration.releasedTokenProviderAliases.forEach { (alias, identity) ->
                    val previous = put(alias, identity)
                    require(previous == null || previous == identity) {
                        "Conflicting ToolPkg token provider alias: $alias"
                    }
                }
            }
        }

    private fun syncToolPkgRegistrations(activeContainers: List<ToolPkgContainerRuntime>) {
        val registrations =
            activeContainers
                .flatMap { runtime ->
                    runtime.aiProviders.map { provider ->
                        ToolPkgAiProviderRegistration(
                            containerPackageName = runtime.packageName,
                            providerId = provider.id,
                            displayName = provider.displayName,
                            description = provider.description,
                            listModelsFunctionName = provider.listModelsHandler.function,
                            listModelsFunctionSource = provider.listModelsHandler.functionSource,
                            sendMessageFunctionName = provider.sendMessageHandler.function,
                            sendMessageFunctionSource = provider.sendMessageHandler.functionSource,
                            testConnectionFunctionName = provider.testConnectionHandler.function,
                            testConnectionFunctionSource =
                                provider.testConnectionHandler.functionSource,
                            calculateInputTokensFunctionName =
                                provider.calculateInputTokensHandler.function,
                            calculateInputTokensFunctionSource =
                                provider.calculateInputTokensHandler.functionSource
                        )
                    }
                }
                .sortedByToolPkgLoadOrder(
                    activeContainers = activeContainers,
                    containerPackageName = ToolPkgAiProviderRegistration::containerPackageName,
                    registrationId = ToolPkgAiProviderRegistration::providerId
                )
        providersById = registrations.associateBy { registration -> registration.providerId.trim().lowercase() }
    }
}
