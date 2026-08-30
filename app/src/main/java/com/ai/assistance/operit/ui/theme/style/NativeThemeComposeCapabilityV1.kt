package com.ai.assistance.operit.ui.theme.style

import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import kotlinx.serialization.Serializable

private val COMPOSE_CAPABILITY_PROFILE_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:[._][a-z0-9_]+)+$")

@Serializable
@JvmInline
internal value class NativeThemeComposeCapabilityProfileIdV1(val value: String) {
    init {
        require(COMPOSE_CAPABILITY_PROFILE_ID_PATTERN.matches(value)) {
            "Invalid Compose capability profile ID: $value"
        }
    }
}

@Serializable
internal data class NativeThemeComposeCapabilityVersionV1(
    val major: Int,
    val minor: Int,
)

@Serializable
internal enum class NativeThemeComposeCapabilityIdV1 {
    DYNAMIC_COLOR,
    CONTENT_BLUR,
    BACKDROP_BLUR,
    LIQUID_MATERIAL,
    WATER_MATERIAL,
    INNER_SHADOW,
    OUTSIDE_BORDER,
    FONT_ASSET,
}

@Serializable
internal data class NativeThemeComposeCapabilitySurfaceRequirementV1(
    val surface: NativeThemeHostSurface,
    val minimumVersion: NativeThemeComposeCapabilityVersionV1,
)

@Serializable
internal data class NativeThemeComposeCapabilityRequirementV1(
    val id: NativeThemeComposeCapabilityIdV1,
    val surfaces: List<NativeThemeComposeCapabilitySurfaceRequirementV1>,
)

@Serializable
internal data class NativeThemeComposeCapabilitySurfaceSupportV1(
    val surface: NativeThemeHostSurface,
    val version: NativeThemeComposeCapabilityVersionV1,
)

@Serializable
internal data class NativeThemeComposeCapabilitySupportV1(
    val id: NativeThemeComposeCapabilityIdV1,
    val surfaces: List<NativeThemeComposeCapabilitySurfaceSupportV1>,
)

@Serializable
internal data class NativeThemeComposeHostCapabilityProfileV1(
    val id: NativeThemeComposeCapabilityProfileIdV1,
    val capabilities: List<NativeThemeComposeCapabilitySupportV1>,
)

internal data class NativeThemeComposeCapabilityValidationResultV1(
    val issues: List<NativeThemeStyleIssueV1>,
) {
    val isValid: Boolean
        get() = issues.isEmpty()

    fun requireValid() {
        require(isValid) {
            issues.joinToString(separator = "\n") { issue ->
                "${issue.code}: ${issue.path}: ${issue.detail}"
            }
        }
    }
}

internal object NativeThemeComposeStyleSurfacesV1 {
    val supported: Set<NativeThemeHostSurface> =
        setOf(
            NativeThemeHostSurface.MAIN,
            NativeThemeHostSurface.FLOATING,
            NativeThemeHostSurface.OVERLAY,
            NativeThemeHostSurface.OFFSCREEN,
            NativeThemeHostSurface.EDITOR_PREVIEW,
        )
}

internal fun validateNativeThemeComposeCapabilityRequirementsV1(
    requirements: List<NativeThemeComposeCapabilityRequirementV1>,
    hostProfile: NativeThemeComposeHostCapabilityProfileV1,
): NativeThemeComposeCapabilityValidationResultV1 {
    val issues = mutableListOf<NativeThemeStyleIssueV1>()
    val duplicateRequirements =
        requirements
            .groupingBy { requirement -> requirement.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    duplicateRequirements.forEach { capability ->
        issues +=
            NativeThemeStyleIssueV1(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_CAPABILITY_REQUIREMENT,
                path = "requirements",
                detail = "Capability ${capability.name} is declared more than once.",
            )
    }

    val duplicateSupport =
        hostProfile.capabilities
            .groupingBy { support -> support.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    duplicateSupport.forEach { capability ->
        issues +=
            NativeThemeStyleIssueV1(
                code = NativeThemeStyleIssueCodeV1.DUPLICATE_CAPABILITY_SUPPORT,
                path = "hostProfile.capabilities",
                detail = "Host capability ${capability.name} is registered more than once.",
            )
    }

    val supportById = hostProfile.capabilities.associateBy { support -> support.id }
    requirements.forEachIndexed { index, requirement ->
        val path = "requirements[$index]"
        if (requirement.surfaces.isEmpty()) {
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.EMPTY_CAPABILITY_SURFACE,
                    path = "$path.surfaces",
                    detail = "A capability requirement must name at least one Compose style surface.",
                )
        }
        duplicateSurfaceRequirements(requirement.surfaces, "$path.surfaces", issues)
        requirement.surfaces.forEachIndexed { surfaceIndex, surfaceRequirement ->
            val surfacePath = "$path.surfaces[$surfaceIndex]"
            validateCapabilityVersion(surfaceRequirement.minimumVersion, "$surfacePath.minimumVersion", issues)
            validateStyleSurface(surfaceRequirement.surface, "$surfacePath.surface", issues)
        }

        val support = supportById[requirement.id]
        if (support == null) {
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.MISSING_HOST_CAPABILITY,
                    path = path,
                    detail = "Host ${hostProfile.id.value} does not provide ${requirement.id.name}.",
                )
            return@forEachIndexed
        }
        requirement.surfaces.forEachIndexed { surfaceIndex, surfaceRequirement ->
            val surfacePath = "$path.surfaces[$surfaceIndex]"
            val surfaceSupport = support.surfaces.singleOrNull { it.surface == surfaceRequirement.surface }
            if (surfaceSupport == null) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.CAPABILITY_SURFACE_MISMATCH,
                        path = surfacePath,
                        detail = "Host ${hostProfile.id.value} does not provide ${requirement.id.name} on ${surfaceRequirement.surface.name}.",
                    )
            } else if (!surfaceSupport.version.satisfies(surfaceRequirement.minimumVersion)) {
                issues +=
                    NativeThemeStyleIssueV1(
                        code = NativeThemeStyleIssueCodeV1.CAPABILITY_VERSION_MISMATCH,
                        path = "$surfacePath.minimumVersion",
                        detail = "Host ${hostProfile.id.value} provides ${surfaceSupport.version.major}.${surfaceSupport.version.minor} for ${requirement.id.name} on ${surfaceRequirement.surface.name}.",
                    )
            }
        }
    }

    hostProfile.capabilities.forEachIndexed { index, support ->
        val path = "hostProfile.capabilities[$index]"
        duplicateSurfaceSupport(support.surfaces, "$path.surfaces", issues)
        support.surfaces.forEachIndexed { surfaceIndex, surfaceSupport ->
            validateCapabilityVersion(surfaceSupport.version, "$path.surfaces[$surfaceIndex].version", issues)
            validateStyleSurface(surfaceSupport.surface, "$path.surfaces[$surfaceIndex].surface", issues)
        }
    }

    return NativeThemeComposeCapabilityValidationResultV1(issues)
}

private fun NativeThemeComposeCapabilityVersionV1.satisfies(
    requirement: NativeThemeComposeCapabilityVersionV1,
): Boolean =
    major == requirement.major && minor >= requirement.minor

private fun duplicateSurfaceRequirements(
    requirements: List<NativeThemeComposeCapabilitySurfaceRequirementV1>,
    path: String,
    issues: MutableList<NativeThemeStyleIssueV1>,
) {
    requirements
        .groupingBy { requirement -> requirement.surface }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .forEach { surface ->
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.DUPLICATE_CAPABILITY_SURFACE,
                    path = path,
                    detail = "Surface ${surface.name} is declared more than once.",
                )
        }
}

private fun duplicateSurfaceSupport(
    support: List<NativeThemeComposeCapabilitySurfaceSupportV1>,
    path: String,
    issues: MutableList<NativeThemeStyleIssueV1>,
) {
    support
        .groupingBy { surfaceSupport -> surfaceSupport.surface }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .forEach { surface ->
            issues +=
                NativeThemeStyleIssueV1(
                    code = NativeThemeStyleIssueCodeV1.DUPLICATE_CAPABILITY_SURFACE,
                    path = path,
                    detail = "Surface ${surface.name} is registered more than once.",
                )
        }
}

private fun validateCapabilityVersion(
    version: NativeThemeComposeCapabilityVersionV1,
    path: String,
    issues: MutableList<NativeThemeStyleIssueV1>,
) {
    if (version.major <= 0 || version.minor < 0) {
        issues +=
            NativeThemeStyleIssueV1(
                code = NativeThemeStyleIssueCodeV1.INVALID_CAPABILITY_VERSION,
                path = path,
                detail = "Capability versions require a positive major and a non-negative minor.",
            )
    }
}

private fun validateStyleSurface(
    surface: NativeThemeHostSurface,
    path: String,
    issues: MutableList<NativeThemeStyleIssueV1>,
) {
    if (surface !in NativeThemeComposeStyleSurfacesV1.supported) {
        issues +=
            NativeThemeStyleIssueV1(
                code = NativeThemeStyleIssueCodeV1.UNSUPPORTED_STYLE_SURFACE,
                path = path,
                detail = "Style API v1 does not expose ${surface.name}.",
            )
    }
}
