package com.ai.assistance.operit.core.prompts

import kotlinx.serialization.Serializable

/**
 * PromptProfileManager — manages system prompt profiles.
 *
 * Per PROJECT_PLAN.md §9:
 * - Store multiple system prompt sets
 * - Bindable to roles/projects
 * - Select and switch between profiles
 */
class PromptProfileManager {

    private val profiles = mutableMapOf<String, PromptProfile>()

    /**
     * Add or update a prompt profile.
     */
    fun saveProfile(profile: PromptProfile) {
        profiles[profile.id] = profile
    }

    /**
     * Get a profile by ID.
     */
    fun getProfile(profileId: String): PromptProfile? {
        return profiles[profileId]
    }

    /**
     * Get all profiles.
     */
    fun getAllProfiles(): List<PromptProfile> = profiles.values.toList()

    /**
     * Get the default profile.
     */
    fun getDefaultProfile(): PromptProfile? {
        return profiles.values.find { it.isDefault }
    }

    /**
     * Delete a profile by ID.
     */
    fun deleteProfile(profileId: String) {
        profiles.remove(profileId)
    }

    /**
     * Set a profile as default.
     */
    fun setDefault(profileId: String) {
        profiles.values.forEach { it.copy(isDefault = false) }
        profiles[profileId]?.let { profiles[profileId] = it.copy(isDefault = true) }
    }

    /**
     * Get profiles for a specific role.
     */
    fun getProfilesForRole(role: String): List<PromptProfile> {
        return profiles.values.filter { it.targetRole == role || it.targetRole == null }
    }

    /**
     * Get profiles for a specific project.
     */
    fun getProfilesForProject(projectId: String): List<PromptProfile> {
        return profiles.values.filter { it.targetProject == projectId || it.targetProject == null }
    }
}

@Serializable
data class PromptProfile(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val rules: List<String> = emptyList(),
    val targetRole: String? = null,    // null = applies to all roles
    val targetProject: String? = null,  // null = applies to all projects
    val isDefault: Boolean = false,
    val description: String = ""
)
