package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 管理首次启动流程的完成状态，并兼容已经完成旧协议引导的用户。 */
class AgreementPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("agreement_preferences", Context.MODE_PRIVATE)
    private val onboardingCompletedKey = "onboarding_completed"
    private val legacyAgreementAcceptedKey = "agreement_accepted"
    private val legacyAgreementVersionKey = "accepted_agreement_version"
    private val _onboardingCompletedFlow = MutableStateFlow(isOnboardingCompleted())
    val onboardingCompletedFlow: StateFlow<Boolean> = _onboardingCompletedFlow.asStateFlow()

    fun isOnboardingCompleted(): Boolean {
        migrateLegacyCompletion()
        return prefs.getBoolean(onboardingCompletedKey, false)
    }

    fun markOnboardingCompleted() {
        prefs.edit().putBoolean(onboardingCompletedKey, true).apply()
        _onboardingCompletedFlow.value = true
    }

    private fun migrateLegacyCompletion() {
        val hadLegacyCompletion =
            prefs.getBoolean(legacyAgreementAcceptedKey, false) ||
                prefs.contains(legacyAgreementVersionKey)
        if (hadLegacyCompletion) {
            // 旧版本已经走完首次引导，直接迁移为流程完成，不要求用户重复进入应用。
            prefs.edit()
                .putBoolean(onboardingCompletedKey, true)
                .remove(legacyAgreementAcceptedKey)
                .remove(legacyAgreementVersionKey)
                .apply()
        }
    }
}
