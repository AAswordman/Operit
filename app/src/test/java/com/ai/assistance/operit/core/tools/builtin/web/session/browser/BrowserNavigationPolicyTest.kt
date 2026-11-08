package com.ai.assistance.operit.core.tools.builtin.web.session.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationPolicyTest {
    @Test
    fun asynchronousLoginPopupIsAllowedOnlyOncePerDocument() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryCreatePopup(false, true, 0, 0L))
        assertFalse(policy.tryCreatePopup(false, true, 1, 0L))
        assertFalse(policy.tryCreatePopup(false, true, 0, 0L))
    }

    @Test
    fun userPopupCanRetryButCannotExceedTheLiveWindowLimit() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryCreatePopup(true, true, 0, 0L))
        assertTrue(policy.tryCreatePopup(true, true, BrowserNavigationPolicy.MAX_POPUP_SESSIONS - 1, 0L))
        assertFalse(policy.tryCreatePopup(true, true, BrowserNavigationPolicy.MAX_POPUP_SESSIONS, 0L))
        assertTrue(policy.tryCreatePopup(true, true, BrowserNavigationPolicy.MAX_POPUP_SESSIONS - 1, 0L))
    }

    @Test
    fun backgroundPagesCannotOpenWindowsOrConsumeTheActivePageBudget() {
        val policy = BrowserNavigationPolicy()
        assertFalse(policy.tryCreatePopup(false, false, 0, 0L))
        assertFalse(policy.tryCreatePopup(true, false, 0, 0L))
        assertTrue(policy.tryCreatePopup(false, true, 0, 0L))
    }

    @Test
    fun documentNavigationAllowsAPopupButCannotRepeatAnExternalRequest() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryCreatePopup(false, true, 0, 0L))
        assertTrue(policy.tryRequestExternalOpen(true, false, true, false, 0L))
        assertFalse(policy.tryCreatePopup(false, true, 0, 0L))
        policy.onDocumentStarted()
        assertTrue(policy.tryCreatePopup(false, true, 0, 0L))
        assertFalse(policy.tryRequestExternalOpen(true, false, true, false, 0L))
    }

    @Test
    fun explicitNavigationStartsANewExternalLoginAttempt() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryRequestExternalOpen(true, false, true, false, 0L))
        policy.onExplicitNavigation()
        assertTrue(policy.tryRequestExternalOpen(true, false, true, false, 0L))
        assertFalse(policy.tryRequestExternalOpen(true, false, true, false, 0L))
    }

    @Test
    fun subframeProbesAndBackgroundRedirectsDoNotConsumeAnExternalRequest() {
        val policy = BrowserNavigationPolicy()
        assertFalse(policy.tryRequestExternalOpen(false, false, true, false, 0L))
        assertFalse(policy.tryRequestExternalOpen(true, false, false, false, 0L))
        assertTrue(policy.tryRequestExternalOpen(true, false, true, false, 0L))
    }

    @Test
    fun pendingConfirmationCannotBeReplacedEvenByAnotherUserGesture() {
        val policy = BrowserNavigationPolicy()
        assertFalse(policy.tryRequestExternalOpen(true, true, true, true, 0L))
        assertTrue(policy.tryRequestExternalOpen(true, false, true, false, 0L))
        assertFalse(policy.tryRequestExternalOpen(true, true, true, true, 0L))
    }

    @Test
    fun automaticRetriesStaySuppressedAfterConfirmationOrCancellation() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryRequestExternalOpen(true, true, true, false, 0L))
        assertFalse(policy.tryRequestExternalOpen(true, false, true, false, 0L))
        assertTrue(policy.tryRequestExternalOpen(true, true, true, false, 0L))
        assertFalse(policy.tryRequestExternalOpen(true, false, true, false, 0L))
    }

    @Test
    fun explicitSubframeClicksRemainUsable() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryRequestExternalOpen(false, true, true, false, 0L))
        assertFalse(policy.tryRequestExternalOpen(false, false, true, false, 0L))
    }

    @Test
    fun asynchronousSubframeRedirectCanConsumeARecentClickOnlyOnce() {
        val policy = BrowserNavigationPolicy()
        policy.onUserInteraction(1_000L)
        assertTrue(policy.tryRequestExternalOpen(false, false, true, false, 1_500L))
        assertFalse(policy.tryRequestExternalOpen(false, false, true, false, 1_600L))
    }

    @Test
    fun expiredOrPreviousDocumentClicksCannotAuthorizeSubframeProbes() {
        val policy = BrowserNavigationPolicy()
        policy.onUserInteraction(1_000L)
        assertFalse(policy.tryRequestExternalOpen(false, false, true, false, 3_001L))
        policy.onUserInteraction(4_000L)
        policy.onDocumentStarted()
        assertFalse(policy.tryRequestExternalOpen(false, false, true, false, 4_001L))
    }

    @Test
    fun recentClickAllowsAnAsynchronousPopupRetryOnlyOnce() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryCreatePopup(false, true, 0, 0L))
        policy.onUserInteraction(1_000L)
        assertTrue(policy.tryCreatePopup(false, true, 0, 1_500L))
        assertFalse(policy.tryCreatePopup(false, true, 0, 1_600L))
    }

    @Test
    fun resolvingARequestDiscardsAnyUnusedClickButANewClickCanRetry() {
        val policy = BrowserNavigationPolicy()
        assertTrue(policy.tryRequestExternalOpen(true, false, true, false, 0L))
        policy.onUserInteraction(1_000L)
        policy.onExternalRequestResolved()
        assertFalse(policy.tryRequestExternalOpen(false, false, true, false, 1_100L))
        policy.onUserInteraction(1_200L)
        assertTrue(policy.tryRequestExternalOpen(false, false, true, false, 1_300L))
    }
}
