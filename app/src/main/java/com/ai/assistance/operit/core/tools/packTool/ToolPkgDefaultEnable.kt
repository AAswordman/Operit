package com.ai.assistance.operit.core.tools.packTool

/**
 * Pure decision helpers for default-enable behaviour.
 *
 * Extracted from [PackageManager] so the rule can be unit tested without an
 * Android `Context` / SharedPreferences. Behaviour is unchanged from the
 * inline logic: a package participates in default-enable when it is either a
 * built-in package or a ToolPkg container, its manifest asks for
 * `enabled_by_default`, it is not a subpackage, and the user has not turned it
 * off before.
 *
 * External ToolPkg containers are intentionally included. Gating on `isBuiltIn`
 * alone left externally installed ToolPkgs permanently disabled even though
 * their manifest asked to be enabled by default (issue #1258), so their prompt
 * hooks never reached the active container list and never dispatched.
 */
internal fun shouldAutoEnableDefaultPackage(
    isBuiltIn: Boolean,
    isToolPkgContainer: Boolean,
    enabledByDefault: Boolean,
    isSubpackage: Boolean,
    isUserDisabled: Boolean,
): Boolean {
    val participatesInDefaultEnable = isBuiltIn || isToolPkgContainer
    return participatesInDefaultEnable &&
        enabledByDefault &&
        !isSubpackage &&
        !isUserDisabled
}

/**
 * Whether turning a package off should be remembered across restarts.
 *
 * Mirrors [shouldAutoEnableDefaultPackage]'s participation rule: if a package
 * would have been auto-enabled, its disable must be persisted so the next
 * startup does not silently undo the user's choice.
 */
internal fun shouldRememberDisabledDefaultPackage(
    isBuiltIn: Boolean,
    isToolPkgContainer: Boolean,
    enabledByDefault: Boolean,
): Boolean {
    val participatesInDefaultEnable = isBuiltIn || isToolPkgContainer
    return participatesInDefaultEnable && enabledByDefault
}
