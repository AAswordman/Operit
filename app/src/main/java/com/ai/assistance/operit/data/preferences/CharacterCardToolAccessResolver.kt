package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.data.model.CharacterCardToolAccessConfig
import com.ai.assistance.operit.data.skill.SkillRepository
import kotlinx.coroutines.flow.first

data class ResolvedCharacterCardToolAccess(
    val customEnabled: Boolean,
    val effectiveBuiltinToolVisibility: Map<String, Boolean>,
    val allowedPackageNames: Set<String>,
    val allowedSkillNames: Set<String>,
    val allowedMcpServerNames: Set<String>,
    val canUsePackageSystem: Boolean,
    val hasAnyAllowedExternalSource: Boolean
) {
    fun isBuiltinToolAllowed(toolName: String): Boolean {
        if (!customEnabled) {
            return effectiveBuiltinToolVisibility[toolName] ?: true
        }
        return when (toolName) {
            "package_proxy" -> hasAnyAllowedExternalSource
            else -> effectiveBuiltinToolVisibility[toolName] == true
        }
    }

    fun isExternalSourceAllowed(sourceName: String): Boolean {
        if (!customEnabled) return true
        if (!canUsePackageSystem) return false
        return allowedPackageNames.contains(sourceName) ||
            allowedSkillNames.contains(sourceName) ||
            allowedMcpServerNames.contains(sourceName)
    }
}

class CharacterCardToolAccessResolver private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: CharacterCardToolAccessResolver? = null

        fun getInstance(context: Context): CharacterCardToolAccessResolver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CharacterCardToolAccessResolver(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val apiPreferences by lazy { ApiPreferences.getInstance(context) }
    private val characterCardManager by lazy { CharacterCardManager.getInstance(context) }
    private val skillRepository by lazy { SkillRepository.getInstance(context) }

    suspend fun resolve(
        roleCardId: String?,
        packageManager: PackageManager,
        globalToolVisibility: Map<String, Boolean>? = null
    ): ResolvedCharacterCardToolAccess {
        val effectiveGlobalToolVisibility = globalToolVisibility
            ?: runCatching { apiPreferences.toolPromptVisibilityFlow.first() }.getOrElse { emptyMap() }

        val globalPackageNames = buildGlobalPackageNames(packageManager)
        val globalSkillNames = LinkedHashSet(skillRepository.getAiVisibleSkillPackages().keys)
        val globalMcpServerNames = LinkedHashSet(packageManager.getAvailableServerPackages().keys)

        val roleCardConfig = roleCardId
            ?.takeIf { it.isNotBlank() }
            ?.let { cardId ->
                runCatching { characterCardManager.getCharacterCard(cardId).toolAccessConfig.normalized() }
                    .getOrDefault(CharacterCardToolAccessConfig())
            }
            ?: CharacterCardToolAccessConfig()

        if (!roleCardConfig.enabled) {
            val hasAnyGlobalExternalSource = globalPackageNames.isNotEmpty() ||
                globalSkillNames.isNotEmpty() ||
                globalMcpServerNames.isNotEmpty()
            return ResolvedCharacterCardToolAccess(
                customEnabled = false,
                effectiveBuiltinToolVisibility = effectiveGlobalToolVisibility,
                allowedPackageNames = globalPackageNames,
                allowedSkillNames = globalSkillNames,
                allowedMcpServerNames = globalMcpServerNames,
                canUsePackageSystem = true,
                hasAnyAllowedExternalSource = hasAnyGlobalExternalSource
            )
        }

        val allowedBuiltinTools = LinkedHashSet(roleCardConfig.allowedBuiltinTools)
        val manageableBuiltinNames = SystemToolPrompts
            .getManageableToolPrompts(useEnglish = false)
            .mapTo(LinkedHashSet()) { it.name }
        val effectiveBuiltinToolVisibility = manageableBuiltinNames.associateWith { toolName ->
            (effectiveGlobalToolVisibility[toolName] ?: true) && allowedBuiltinTools.contains(toolName)
        }

        val canUsePackageSystem = effectiveBuiltinToolVisibility["use_package"] == true
        val allowedPackageNames = if (canUsePackageSystem) {
            globalPackageNames.filterTo(LinkedHashSet()) { packageName ->
                roleCardConfig.allowedPackages.contains(packageName)
            }
        } else {
            linkedSetOf()
        }
        val allowedSkillNames = if (canUsePackageSystem) {
            globalSkillNames.filterTo(LinkedHashSet()) { skillName ->
                roleCardConfig.allowedSkills.contains(skillName)
            }
        } else {
            linkedSetOf()
        }
        val allowedMcpServerNames = if (canUsePackageSystem) {
            globalMcpServerNames.filterTo(LinkedHashSet()) { serverName ->
                roleCardConfig.allowedMcpServers.contains(serverName)
            }
        } else {
            linkedSetOf()
        }
        val hasAnyAllowedExternalSource = allowedPackageNames.isNotEmpty() ||
            allowedSkillNames.isNotEmpty() ||
            allowedMcpServerNames.isNotEmpty()

        return ResolvedCharacterCardToolAccess(
            customEnabled = true,
            effectiveBuiltinToolVisibility = effectiveBuiltinToolVisibility,
            allowedPackageNames = allowedPackageNames,
            allowedSkillNames = allowedSkillNames,
            allowedMcpServerNames = allowedMcpServerNames,
            canUsePackageSystem = canUsePackageSystem,
            hasAnyAllowedExternalSource = hasAnyAllowedExternalSource
        )
    }

    private fun buildGlobalPackageNames(packageManager: PackageManager): LinkedHashSet<String> {
        return packageManager.getEnabledPackageNames()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { packageName ->
                packageManager.getPackageTools(packageName) != null &&
                    !packageManager.isToolPkgContainer(packageName)
            }
            .toCollection(LinkedHashSet())
    }
}
