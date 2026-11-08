package com.ai.assistance.operit.ui.common.composedsl

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeDslTextFieldEchoGuardTest {

    @Test
    fun `stale echo of own deletion is ignored while focused`() {
        // Regression test for https://github.com/AAswordman/Operit/issues/1150:
        // rapid deletions dispatch one edit per keystroke, and a tree snapshot built after
        // only the first deletion used to overwrite the field while the user kept deleting.
        val guard = ComposeDslTextFieldEchoGuard("hello world")
        guard.onLocalEditDispatched("hello worl")
        guard.onLocalEditDispatched("hello wor")

        val decision =
            guard.reconcile(externalText = "hello worl", fieldText = "hello wor", isFocused = true)

        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.OWN_ECHO, decision)
    }

    @Test
    fun `converged tree clears pending echoes`() {
        val guard = ComposeDslTextFieldEchoGuard("abc")
        guard.onLocalEditDispatched("ab")
        guard.onLocalEditDispatched("a")

        val decision = guard.reconcile(externalText = "a", fieldText = "a", isFocused = true)

        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.CONVERGED, decision)
    }

    @Test
    fun `value unknown to the edit history is applied while focused`() {
        // e.g. the worldbook "insert template" action appends a snippet to the content.
        val guard = ComposeDslTextFieldEchoGuard("abc")
        guard.onLocalEditDispatched("abcd")

        val decision =
            guard.reconcile(
                externalText = "abcd\n\n{{getvar::stat_data}}",
                fieldText = "abcd",
                isFocused = true
            )

        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.EXTERNAL_CHANGE, decision)
    }

    @Test
    fun `tree from before the editing session is ignored while focused`() {
        val guard = ComposeDslTextFieldEchoGuard("abc")
        guard.onLocalEditDispatched("ab")

        val decision = guard.reconcile(externalText = "abc", fieldText = "ab", isFocused = true)

        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.OWN_ECHO, decision)
    }

    @Test
    fun `any differing value is applied while unfocused`() {
        val guard = ComposeDslTextFieldEchoGuard("abc")
        guard.onLocalEditDispatched("ab")

        val decision = guard.reconcile(externalText = "ab", fieldText = "abc", isFocused = false)

        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.EXTERNAL_CHANGE, decision)
    }

    @Test
    fun `pre-edit tree snapshots are ignored repeatedly while focused`() {
        val guard = ComposeDslTextFieldEchoGuard("abc")
        guard.onLocalEditDispatched("ab")

        assertEquals(
            ComposeDslTextFieldEchoGuard.ExternalUpdate.OWN_ECHO,
            guard.reconcile(externalText = "abc", fieldText = "ab", isFocused = true)
        )
        assertEquals(
            ComposeDslTextFieldEchoGuard.ExternalUpdate.OWN_ECHO,
            guard.reconcile(externalText = "abc", fieldText = "ab", isFocused = true)
        )
    }

    @Test
    fun `delete then retype keeps later echoes classified`() {
        val guard = ComposeDslTextFieldEchoGuard("ab")
        guard.onLocalEditDispatched("a")
        guard.onLocalEditDispatched("ab")

        assertEquals(
            ComposeDslTextFieldEchoGuard.ExternalUpdate.OWN_ECHO,
            guard.reconcile(externalText = "a", fieldText = "ab", isFocused = true)
        )
        assertEquals(
            ComposeDslTextFieldEchoGuard.ExternalUpdate.CONVERGED,
            guard.reconcile(externalText = "ab", fieldText = "ab", isFocused = true)
        )
    }
}
