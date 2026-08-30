package com.ai.assistance.operit.core.hardening

/**
 * TermuxAuthFallback — handles Termux authorization failures gracefully.
 *
 * Per PROJECT_PLAN.md §10:
 * > ขอสิทธิ์ Termux ไม่สำเร็จบนบางเครื่อง (ColorOS ฯลฯ)
 * > ทำ fallback flow: ถ้า auto-authorize ไม่ยืนยันภายใน timeout
 * > ให้ขึ้น manual verify step พร้อมคำแนะนำเฉพาะยี่ห้อเครื่อง
 */
class TermuxAuthFallback {

    companion object {
        private const val AUTH_TIMEOUT_MS = 10_000L // 10 seconds

        // Known problematic device manufacturers
        private val KNOWN_ISSUES = mapOf(
            "ColorOS" to "Oppo/Realme devices may block Termux authorization. " +
                "Please go to Settings > App Management > Termux > Permissions " +
                "and enable all permissions manually.",
            "MIUI" to "Xiaomi devices may restrict Termux background activity. " +
                "Please enable 'Autostart' for Termux in Settings > Apps > Manage Apps > Termux.",
            "OneUI" to "Samsung devices may require additional battery optimization exemption. " +
                "Please disable battery optimization for Termux in Settings > Battery > Battery Optimization.",
            "EMUI" to "Huawei devices may block Termux services. " +
                "Please enable 'Run in background' for Termux in Settings > Apps > Termux > Battery.",
            "Funtouch OS" to "Vivo devices may restrict Termux permissions. " +
                "Please manually grant all permissions to Termux in Settings > App Manager."
        )
    }

    /**
     * Check if the device manufacturer is known to have Termux authorization issues.
     */
    fun getDeviceIssue(manufacturer: String): String? {
        return KNOWN_ISSUES.entries.find { (key, _) ->
            manufacturer.contains(key, ignoreCase = true)
        }?.value
    }

    /**
     * Get the authorization timeout in milliseconds.
     */
    fun getAuthTimeoutMs(): Long = AUTH_TIMEOUT_MS

    /**
     * Get manual verification instructions for a device manufacturer.
     */
    fun getManualVerifyInstructions(manufacturer: String): String {
        return getDeviceIssue(manufacturer)
            ?: "Termux authorization failed. Please check Termux permissions in your device settings."
    }

    /**
     * Check if auto-authorization is likely to work on this device.
     */
    fun isAutoAuthLikelyToWork(manufacturer: String): Boolean {
        // Devices known to have issues
        return !KNOWN_ISSUES.keys.any { manufacturer.contains(it, ignoreCase = true) }
    }
}
