package com.ai.assistance.operit.data.api

import org.junit.Assert.assertSame
import org.junit.Test

class CodexOAuthTokenResponseTest {
    @Test
    fun completeTokenResponseIsAccepted() {
        val response = CodexOAuthTokenResponse(
            idToken = "id-token",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresInSeconds = 3600L,
        )

        assertSame(response, response.requireComplete())
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingRefreshTokenIsRejected() {
        CodexOAuthTokenResponse(
            idToken = "id-token",
            accessToken = "access-token",
            refreshToken = null,
            expiresInSeconds = 3600L,
        ).requireComplete()
    }
}
