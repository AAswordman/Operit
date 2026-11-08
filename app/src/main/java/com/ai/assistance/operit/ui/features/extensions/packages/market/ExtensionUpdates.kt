package com.ai.assistance.operit.ui.features.extensions.packages.market

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState
import com.ai.assistance.operit.data.transfer.DownloadTransferTracker
import com.ai.assistance.operit.data.resources.models.ResourceDownloadPhase
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface ExtensionUpdate {
    data class Market(val entry: MarketV2Entry) : ExtensionUpdate
    data class BuiltIn(val spec: ResourcePackSpec) : ExtensionUpdate
}

data class ExtensionUpdateScope(val type: String = "", val builtInOnly: Boolean = false) {
    fun acceptsType(value: String): Boolean = type.isEmpty() || type == value
    fun accepts(update: ExtensionUpdate): Boolean = when (update) {
        is ExtensionUpdate.Market -> !builtInOnly && acceptsType(update.entry.type.lowercase())
        is ExtensionUpdate.BuiltIn -> acceptsType(if (update.spec.extension!!.kind == "plugin") "package" else "script")
    }
}

data class ExtensionUpdateFailure(val source: String, val reason: String)

/** One check result shared by installed lists and details; installation itself has a runtime gate. */
object ExtensionUpdates {
    private val mutable = MutableStateFlow<Map<String, ExtensionUpdate>>(emptyMap())
    val available = mutable.asStateFlow()
    private val checked = MutableStateFlow<Set<ExtensionUpdateScope>>(emptySet())
    val checkedScopes = checked.asStateFlow()
    private val busy = MutableStateFlow(false)
    val checking = busy.asStateFlow()
    private val failed = MutableStateFlow<Map<ExtensionUpdateScope, List<ExtensionUpdateFailure>>>(emptyMap())
    val failures = failed.asStateFlow()

    @Synchronized private fun beginCheck(): Boolean {
        if (busy.value || !PackageUpdateState.beginCatalogCheck()) return false
        busy.value = true
        return true
    }

    suspend fun check(context: Context, manager: PackageManager, scope: ExtensionUpdateScope = ExtensionUpdateScope()) {
        if (!beginCheck()) return
        val errors = mutableListOf<ExtensionUpdateFailure>()
        try {
            val packages = withContext(Dispatchers.IO) {
                manager.getTopLevelAvailablePackages().filterKeys { name ->
                    scope.acceptsType(if (manager.isToolPkgContainer(name)) "package" else "script")
                }.toMap()
            }
            val results = coroutineScope {
                val limit = Semaphore(4)
                packages.keys.filter { !scope.builtInOnly }.map { name -> async(Dispatchers.IO) {
                    limit.withPermit {
                        val result = try { checkMarketUpdate(readMarketInstallMarkerForPackage(manager, name)) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            AppLogger.e("ExtensionUpdates", "Check failed: $name", error)
                            MarketUpdateCheckResult.Failed
                        }
                        name to result
                    }
                } }.awaitAll()
            }
            for ((name, result) in results) {
                if (result == MarketUpdateCheckResult.Failed || result == MarketUpdateCheckResult.EntryMissing) {
                    errors += ExtensionUpdateFailure(name,
                        context.getString(if (result == MarketUpdateCheckResult.EntryMissing)
                            com.ai.assistance.operit.R.string.pkg_check_update_entry_missing
                        else com.ai.assistance.operit.R.string.pkg_check_update_failed))
                }
            }
            val updates: MutableMap<String, ExtensionUpdate> = results.mapNotNull { (name, result) ->
                (result as? MarketUpdateCheckResult.UpdateAvailable)?.let { name to ExtensionUpdate.Market(it.entry) }
            }.toMap().toMutableMap()
            try {
                withContext(Dispatchers.IO) {
                    val installed = BuiltInExtensionResources.specs(context).filter { spec ->
                        scope.accepts(ExtensionUpdate.BuiltIn(spec)) && PackResources.isReady(context, spec.id)
                    }
                    // Installed lists only check their own sources; the built-in market also refreshes its catalog.
                    if (scope.builtInOnly || installed.isNotEmpty()) {
                        BuiltInExtensionResources.refreshCatalog(context)
                        for (spec in BuiltInExtensionResources.specs(context)) {
                            if (scope.accepts(ExtensionUpdate.BuiltIn(spec)) && PackResources.isReady(context, spec.id) &&
                                !PackResources.isCurrentReady(context, spec.id, true)) {
                                updates[spec.extension!!.packageNames.first()] = ExtensionUpdate.BuiltIn(spec)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                AppLogger.e("ExtensionUpdates", "Built-in catalog check failed", error)
                errors += ExtensionUpdateFailure(context.getString(com.ai.assistance.operit.R.string.builtin),
                    error.message ?: error.javaClass.simpleName)
            }
            mutable.update { current -> current.filterValues { !scope.accepts(it) } + updates }
            checked.update { it + scope }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            AppLogger.e("ExtensionUpdates", "Update check failed", error)
            errors += ExtensionUpdateFailure(context.getString(com.ai.assistance.operit.R.string.pkg_check_update),
                error.message ?: error.javaClass.simpleName)
        } finally {
            failed.update { it + (scope to errors.toList()) }
            busy.value = false
            PackageUpdateState.endCatalogCheck()
        }
    }

    suspend fun install(context: Context, manager: PackageManager, name: String, update: ExtensionUpdate) {
        when (update) {
            is ExtensionUpdate.Market -> {
                val entry = update.entry
                val names = manager.updatePackageNames(name)
                check(PackageUpdateState.start(names, if (entry.type.lowercase() == "package") names else emptySet())) {
                    "Update already running"
                }
                var transferStarted = false
                try {
                    check(MarketInstallStateStore.start(entry.id)) { "Update already running" }
                    transferStarted = true
                    withContext(Dispatchers.IO) { manager.preparePackageUpdate(names) }
                    MarketEntryInstallController(context, MarketStatsApiService()).install(entry, heldUpdateNames = names) { stage, transfer ->
                        MarketInstallStateStore.update(entry.id, stage, transfer)
                    }
                } finally {
                    if (transferStarted) MarketInstallStateStore.finish(entry.id)
                    withContext(NonCancellable + Dispatchers.IO) { manager.completePackageUpdate(names) }
                }
            }
            is ExtensionUpdate.BuiltIn -> installBuiltIn(context, manager, update.spec)
        }
        mutable.update { it - name }
    }

    suspend fun installBuiltIn(context: Context, manager: PackageManager, spec: ResourcePackSpec) {
        check(spec == PackResources.spec(context, spec.id)) { "The catalog changed; select the current version" }
        val extension = requireNotNull(spec.extension)
        // Removed children must stay unavailable until the old installation has been fully replaced.
        val names = withContext(Dispatchers.IO) {
            manager.updatePackageNames(extension.packageNames.first()) +
                PackResources.installedSpec(context, spec.id)?.extension?.packageNames.orEmpty() +
                extension.packageNames
        }
        check(PackageUpdateState.start(names, if (extension.kind == "plugin") names else emptySet())) { "Update already running" }
        var transferStarted = false
        try {
            check(MarketInstallStateStore.start(spec.id)) { "Install already running" }
            transferStarted = true
            withContext(Dispatchers.IO) { manager.preparePackageUpdate(names) }
            val tracker = DownloadTransferTracker(spec.archive.bytes)
            var installing = false
            PackResources.download(context, spec.id, phase = { phase ->
                installing = phase == ResourceDownloadPhase.INSTALLING
                val stage = when (phase) {
                    ResourceDownloadPhase.CONNECTING -> MarketInstallStage.CONNECTING
                    ResourceDownloadPhase.DOWNLOADING -> MarketInstallStage.DOWNLOADING
                    ResourceDownloadPhase.INSTALLING -> MarketInstallStage.INSTALLING
                }
                MarketInstallStateStore.update(spec.id, stage)
            }) { done, total ->
                // The installer also reports completion bytes; keep its installation stage visible.
                if (!installing) tracker.sample(done, force = done == 0L || done == total)?.let { transfer ->
                    MarketInstallStateStore.update(spec.id, MarketInstallStage.DOWNLOADING, transfer)
                }
            }
        } finally {
            if (transferStarted) MarketInstallStateStore.finish(spec.id)
            withContext(NonCancellable + Dispatchers.IO) { manager.completePackageUpdate(names) }
        }
        mutable.update { it - extension.packageNames.first() }
    }
}
