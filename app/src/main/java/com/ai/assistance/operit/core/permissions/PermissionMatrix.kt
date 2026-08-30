package com.ai.assistance.operit.core.permissions

import kotlinx.serialization.Serializable

/**
 * PermissionMatrix — controls permissions per agent role.
 *
 * Per PROJECT_PLAN.md §11:
 * - Leader may approve root commands
 * - Worker is restricted to sandbox
 * - Permissions defined per tool/terminal/root per role
 * - Configurable from settings UI
 */
class PermissionMatrix {

    private val permissions = mutableMapOf<RolePermissionKey, Boolean>()

    /**
     * Set permission for a role + resource combination.
     */
    fun setPermission(role: String, resource: String, allowed: Boolean) {
        permissions[RolePermissionKey(role, resource)] = allowed
    }

    /**
     * Check if a role has permission for a resource.
     * Defaults to false (deny) if not explicitly set.
     */
    fun hasPermission(role: String, resource: String): Boolean {
        return permissions[RolePermissionKey(role, resource)] ?: false
    }

    /**
     * Get all permissions for a role.
     */
    fun getRolePermissions(role: String): Map<String, Boolean> {
        return permissions.filter { it.key.role == role }
            .map { it.key.resource to it.value }
            .toMap()
    }

    /**
     * Get all roles that have a specific resource permission.
     */
    fun getRolesWithPermission(resource: String): List<String> {
        return permissions.filter { it.key.resource == resource && it.value }
            .map { it.key.role }
            .distinct()
    }

    /**
     * Set default permissions for a role.
     */
    fun setDefaults(role: String, defaults: Map<String, Boolean>) {
        for ((resource, allowed) in defaults) {
            setPermission(role, resource, allowed)
        }
    }

    /**
     * Clear all permissions for a role.
     */
    fun clearRole(role: String) {
        permissions.keys.removeAll { it.role == role }
    }

    /**
     * Clear all permissions.
     */
    fun clearAll() {
        permissions.clear()
    }

    companion object {
        // Default resource constants
        const val RESOURCE_TOOL_EXECUTION = "tool_execution"
        const val RESOURCE_TERMINAL = "terminal"
        const val RESOURCE_ROOT = "root"
        const val RESOURCE_FILE_READ = "file_read"
        const val RESOURCE_FILE_WRITE = "file_write"
        const val RESOURCE_NETWORK = "network"
        const val RESOURCE_INSTALL = "install"

        /**
         * Create a default permission matrix with standard role permissions.
         */
        fun createDefault(): PermissionMatrix {
            val matrix = PermissionMatrix()

            // Leader: full permissions
            matrix.setDefaults("leader", mapOf(
                RESOURCE_TOOL_EXECUTION to true,
                RESOURCE_TERMINAL to true,
                RESOURCE_ROOT to true,
                RESOURCE_FILE_READ to true,
                RESOURCE_FILE_WRITE to true,
                RESOURCE_NETWORK to true,
                RESOURCE_INSTALL to true
            ))

            // Worker: restricted permissions
            matrix.setDefaults("worker", mapOf(
                RESOURCE_TOOL_EXECUTION to true,
                RESOURCE_TERMINAL to true,
                RESOURCE_ROOT to false,     // No root access
                RESOURCE_FILE_READ to true,
                RESOURCE_FILE_WRITE to true,
                RESOURCE_NETWORK to true,
                RESOURCE_INSTALL to false   // No install
            ))

            // User: standard permissions
            matrix.setDefaults("user", mapOf(
                RESOURCE_TOOL_EXECUTION to true,
                RESOURCE_TERMINAL to true,
                RESOURCE_ROOT to false,
                RESOURCE_FILE_READ to true,
                RESOURCE_FILE_WRITE to true,
                RESOURCE_NETWORK to true,
                RESOURCE_INSTALL to true
            ))

            return matrix
        }
    }
}

@Serializable
private data class RolePermissionKey(
    val role: String,
    val resource: String
)
