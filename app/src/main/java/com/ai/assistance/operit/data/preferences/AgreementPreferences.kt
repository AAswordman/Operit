package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manages agreement-related preferences for the application */
class AgreementPreferences(context: Context) {
    private val PREFS_NAME = "agreement_preferences"
    private val KEY_ACCEPTED_AGREEMENT_VERSION = "accepted_agreement_version"

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _agreementAcceptedFlow = MutableStateFlow(isAgreementAccepted())
    val agreementAcceptedFlow: StateFlow<Boolean> = _agreementAcceptedFlow.asStateFlow()

    /** Check whether the user has accepted the current agreement version. */
    fun isAgreementAccepted(): Boolean {
        return prefs.getString(KEY_ACCEPTED_AGREEMENT_VERSION, null) == CURRENT_AGREEMENT_VERSION
    }

    /** Records acceptance of the agreement version bundled with this app release. */
    fun acceptCurrentAgreement() {
        prefs.edit()
            .putString(KEY_ACCEPTED_AGREEMENT_VERSION, CURRENT_AGREEMENT_VERSION)
            .apply()
        _agreementAcceptedFlow.value = true
    }

    companion object {
        /** Bump this value whenever the user agreement changes substantively. */
        const val CURRENT_AGREEMENT_VERSION = "2026-07-15"
    }
}
