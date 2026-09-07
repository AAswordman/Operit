package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.core.tools.ToolPackage

internal data class ToolPkgLoadOrderResult(
    val orderedContainers: List<ToolPkgContainerRuntime>,
    val failures: Map<String, String>
)

internal object ToolPkgLoadOrderResolver {
    private sealed interface PackageReference {
        val packageName: String
        val containerPackageName: String?
        val version: String

        data class Container(
            override val packageName: String,
            override val version: String
        ) : PackageReference {
            override val containerPackageName: String = packageName
        }

        data class Package(
            override val packageName: String,
            override val version: String,
            override val containerPackageName: String? = null
        ) : PackageReference
    }

    fun resolve(
        containers: Collection<ToolPkgContainerRuntime>,
        availablePackages: Map<String, ToolPackage>,
        enabledPackageNames: Collection<String>,
        preferredOrder: List<String>
    ): ToolPkgLoadOrderResult {
        val availableByKey = linkedMapOf<String, ToolPackage>()
        availablePackages.forEach { (packageName, packageValue) ->
            availableByKey.putIfAbsent(packageName.lowercase(), packageValue)
        }

        val containerByReference = linkedMapOf<String, ToolPkgContainerRuntime>()
        val subpackageByReference = linkedMapOf<String, ToolPkgSubpackageRuntime>()
        containers.forEach { container ->
            containerByReference.putIfAbsent(container.packageName.lowercase(), container)
            container.subpackages.forEach { subpackage ->
                subpackageByReference.putIfAbsent(subpackage.packageName.lowercase(), subpackage)
                subpackageByReference.putIfAbsent(subpackage.subpackageId.lowercase(), subpackage)
            }
        }

        val enabledKeys = enabledPackageNames.map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
            .toSet()
        val selectedContainers = containers.filter { container ->
            enabledKeys.contains(container.packageName.lowercase()) ||
                container.subpackages.any { subpackage ->
                    enabledKeys.contains(subpackage.packageName.lowercase())
                }
        }
        val selectedByKey = selectedContainers.associateBy { it.packageName.lowercase() }
        val failures = linkedMapOf<String, String>()

        fun resolveReference(requirement: ToolPkgManifestRequirement): PackageReference? {
            val normalized = requirement.id.trim()
            if (normalized.isBlank()) {
                return null
            }
            containerByReference[normalized.lowercase()]?.let { container ->
                return PackageReference.Container(
                    packageName = container.packageName,
                    version = container.version
                )
            }
            subpackageByReference[normalized.lowercase()]?.let { subpackage ->
                val packageVersion = availableByKey[subpackage.packageName.lowercase()]?.version.orEmpty()
                return PackageReference.Package(
                    packageName = subpackage.packageName,
                    version = packageVersion,
                    containerPackageName = subpackage.containerPackageName
                )
            }
            availableByKey[normalized.lowercase()]?.let { packageValue ->
                return PackageReference.Package(
                    packageName = packageValue.name,
                    version = packageValue.version
                )
            }
            return null
        }

        val edges = selectedContainers.associate { it.packageName to linkedSetOf<String>() }.toMutableMap()

        fun addEdge(before: String, after: String) {
            edges.getValue(before).add(after)
        }

        fun requirementLabel(requirement: ToolPkgManifestRequirement): String {
            val constraint = requirement.versionConstraintText()
            return buildString {
                append("'")
                append(requirement.id)
                append("'")
                if (requirement.description.isNotBlank()) {
                    append(" (")
                    append(requirement.description)
                    append(")")
                }
                if (constraint != null) {
                    append(" [")
                    append(constraint)
                    append("]")
                }
            }
        }

        selectedContainers.forEach { container ->
            container.requires.forEach { requirement ->
                val target = resolveReference(requirement)
                if (target == null) {
                    failures[container.packageName] =
                        "ToolPkg '${container.packageName}' requires ${requirementLabel(requirement)}, but that package is not available."
                    return@forEach
                }

                val versionFailure = requirement.targetVersionFailure(target.version)
                if (versionFailure != null) {
                    failures[container.packageName] =
                        "ToolPkg '${container.packageName}' requires ${requirementLabel(requirement)}, but $versionFailure."
                    return@forEach
                }

                if (
                    target.packageName.equals(container.packageName, ignoreCase = true) ||
                        target.containerPackageName?.equals(container.packageName, ignoreCase = true) == true
                ) {
                    failures[container.packageName] =
                        "ToolPkg '${container.packageName}' cannot require itself."
                    return@forEach
                }

                when (target) {
                    is PackageReference.Container -> {
                        if (!selectedByKey.containsKey(target.packageName.lowercase())) {
                            failures[container.packageName] =
                                "ToolPkg '${container.packageName}' requires ${requirementLabel(requirement)} to be enabled."
                        } else {
                            addEdge(target.packageName, container.packageName)
                        }
                    }
                    is PackageReference.Package -> {
                        if (!enabledKeys.contains(target.packageName.lowercase())) {
                            failures[container.packageName] =
                                "ToolPkg '${container.packageName}' requires ${requirementLabel(requirement)} to be enabled."
                        } else {
                            target.containerPackageName
                                ?.takeIf { selectedByKey.containsKey(it.lowercase()) }
                                ?.let { addEdge(it, container.packageName) }
                        }
                    }
                }
            }
        }

        var changed: Boolean
        do {
            changed = false
            selectedContainers.forEach { container ->
                if (failures.containsKey(container.packageName)) {
                    return@forEach
                }
                val invalidRequiredContainer = container.requires.firstOrNull { requirement ->
                    val target = resolveReference(requirement)
                    target?.containerPackageName?.let { containerName ->
                        failures.containsKey(containerName)
                    } == true
                }
                if (invalidRequiredContainer != null) {
                    failures[container.packageName] =
                        "ToolPkg '${container.packageName}' requires ${requirementLabel(invalidRequiredContainer)}, which cannot be loaded."
                    changed = true
                }
            }
        } while (changed)

        val preferredOrderIndex = preferredOrder.map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
            .withIndex()
            .associate { it.value to it.index }
        val comparator = compareBy<String> {
            preferredOrderIndex[it.lowercase()] ?: Int.MAX_VALUE
        }.thenBy(String::lowercase).thenBy { it }

        val validNames = selectedContainers
            .map(ToolPkgContainerRuntime::packageName)
            .filterNot(failures::containsKey)
            .toSet()
        val indegree = validNames.associateWith { 0 }.toMutableMap()
        edges.forEach { (before, afterNames) ->
            if (!validNames.contains(before)) {
                return@forEach
            }
            afterNames.filter(validNames::contains).forEach { after ->
                indegree[after] = indegree.getValue(after) + 1
            }
        }

        val pending = validNames.toMutableSet()
        val orderedNames = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val next = pending
                .filter { indegree.getValue(it) == 0 }
                .minWithOrNull(comparator)
                ?: break
            pending.remove(next)
            orderedNames += next
            edges[next].orEmpty().filter(pending::contains).forEach { after ->
                indegree[after] = indegree.getValue(after) - 1
            }
        }

        if (pending.isNotEmpty()) {
            val cycleDescription = pending.toList().sortedWith(comparator).joinToString(", ")
            pending.forEach { packageName ->
                failures[packageName] =
                    "ToolPkg load order contains a dependency cycle involving: $cycleDescription."
            }
        }

        val containersByName = selectedContainers.associateBy { it.packageName }
        return ToolPkgLoadOrderResult(
            orderedContainers = orderedNames.mapNotNull(containersByName::get),
            failures = failures
        )
    }
}
