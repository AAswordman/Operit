package com.ai.assistance.operit.ui.features.extensions.github

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.api.GitHubOAuthBrokerService
import com.ai.assistance.operit.data.api.GitHubOAuthBrokerStartResponse
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.util.logging.AppLogger

class GitHubOAuthCoordinator(context: Context) {
    private val githubAuth = GitHubAuthPreferences.getInstance(context.applicationContext)
    private val oauthBrokerService = GitHubOAuthBrokerService()

    suspend fun startEmbeddedLogin(): Result<GitHubOAuthBrokerStartResponse> =
        startLogin(EMBEDDED_COMPLETION_REDIRECT_URI)

    suspend fun startLogin(completionRedirectUri: String): Result<GitHubOAuthBrokerStartResponse> {
        return try {
            val transaction = oauthBrokerService.startLogin(completionRedirectUri).getOrElse { error ->
                throw error
            }
            githubAuth.saveActiveOAuthTransaction(
                transactionId = transaction.transactionId,
                deliveryCredential = transaction.deliveryCredential,
                expiresAt = transaction.expiresAt
            )
            Result.success(transaction)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start GitHub OAuth login", e)
            Result.failure(e)
        }
    }

    suspend fun completeLogin(completionUri: Uri): Result<GitHubUser> {
        val activeTransaction = githubAuth.getActiveOAuthTransaction()
            ?: return Result.failure(IllegalStateException("No GitHub OAuth transaction is active"))
        return try {
            if (completionUri.getQueryParameter("transactionId") != activeTransaction.transactionId) {
                throw IllegalStateException("GitHub OAuth completion transaction does not match")
            }
            when (completionUri.getQueryParameter("status")) {
                "complete" -> Unit
                "denied" -> {
                    githubAuth.clearActiveOAuthTransaction()
                    return Result.failure(IllegalStateException("GitHub authorization was cancelled"))
                }
                "error" -> {
                    githubAuth.clearActiveOAuthTransaction()
                    val error = completionUri.getQueryParameter("error")
                    if (error.isNullOrBlank()) {
                        return Result.failure(
                            IllegalStateException("GitHub OAuth completion error is missing")
                        )
                    }
                    return Result.failure(IllegalStateException(error))
                }
                else -> {
                    githubAuth.clearActiveOAuthTransaction()
                    return Result.failure(IllegalStateException("GitHub OAuth completion status is invalid"))
                }
            }
            val result = oauthBrokerService.claimLogin(
                transactionId = activeTransaction.transactionId,
                deliveryCredential = activeTransaction.deliveryCredential
            ).getOrElse { error -> throw error }
            githubAuth.saveAuthInfo(
                accessToken = result.accessToken,
                tokenType = result.tokenType,
                expiresIn = result.expiresIn,
                refreshToken = result.refreshToken,
                userInfo = result.user,
                grantedScope = result.scope
            )
            githubAuth.clearActiveOAuthTransaction()
            Result.success(result.user)
        } catch (e: Exception) {
            githubAuth.clearActiveOAuthTransaction()
            AppLogger.e(TAG, "Failed to complete GitHub OAuth login", e)
            Result.failure(e)
        }
    }

    suspend fun cancelLogin() {
        githubAuth.clearActiveOAuthTransaction()
    }

    companion object {
        private const val TAG = "GitHubOAuthCoordinator"
        private const val EMBEDDED_COMPLETION_REDIRECT_URI = "https://api.operit.app/oauth/github/complete"
    }
}
