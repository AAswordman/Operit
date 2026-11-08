package com.ai.assistance.operit.ui.common.displays

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R

/** Presentation only: raw actions, observation metadata and results stay in the agent context. */
enum class PhoneAgentUiStatus(@StringRes val labelResId: Int) {
    Scanning(R.string.phone_agent_scanning),
    Planning(R.string.phone_agent_planning),
    Tapping(R.string.phone_agent_tapping),
    Typing(R.string.phone_agent_typing),
    Swiping(R.string.phone_agent_swiping),
    EnteringApp(R.string.phone_agent_entering_app),
    Back(R.string.phone_agent_back),
    Home(R.string.phone_agent_home),
    Waiting(R.string.phone_agent_waiting),
    Working(R.string.phone_agent_working),
    Paused(R.string.phone_agent_paused),
    NeedsUser(R.string.phone_agent_needs_user),
    Completed(R.string.phone_agent_completed),
    Unconfirmed(R.string.phone_agent_unconfirmed),
    ScreenUnavailable(R.string.phone_agent_screen_unavailable);

    val needsAttention: Boolean
        get() = this == Unconfirmed || this == ScreenUnavailable

    val icon: ImageVector
        get() = when (this) {
            Scanning -> Icons.Filled.Search
            Planning -> Icons.Filled.Psychology
            Tapping, Working -> Icons.Filled.TouchApp
            Typing -> Icons.Filled.Keyboard
            Swiping -> Icons.Filled.OpenInFull
            EnteringApp -> Icons.Filled.OpenInNew
            Back -> Icons.Filled.ArrowBack
            Home -> Icons.Filled.Home
            Waiting -> Icons.Filled.HourglassBottom
            Paused -> Icons.Filled.Pause
            NeedsUser -> Icons.Filled.Person
            Completed -> Icons.Filled.CheckCircle
            Unconfirmed, ScreenUnavailable -> Icons.Filled.ErrorOutline
        }
}
