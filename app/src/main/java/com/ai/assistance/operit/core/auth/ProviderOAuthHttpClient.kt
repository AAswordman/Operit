package com.ai.assistance.operit.core.auth

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

internal class ProviderOAuthHttpClient : ProviderOAuthTokenEndpoint {
    // Do not inherit application logging/interceptors; never follow token-endpoint redirects or
    // automatically replay single-use authorization codes / rotating refresh tokens.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun exchange(config: ProviderOAuthConfig, code: OAuthAuthorizationCode): ProviderOAuthTokens =
        request(config, mapOf(
            "grant_type" to "authorization_code",
            "code" to code.code,
            "redirect_uri" to code.redirectUri,
            "code_verifier" to code.verifier,
        ))

    override suspend fun refresh(config: ProviderOAuthConfig, previous: ProviderOAuthTokens): ProviderOAuthTokens =
        request(config, mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to (previous.refreshToken ?: throw OAuthLoginRequired()),
        ), previous)

    private suspend fun request(
        config: ProviderOAuthConfig,
        parameters: Map<String, String>,
        previous: ProviderOAuthTokens? = null,
    ): ProviderOAuthTokens = suspendCancellableCoroutine { continuation ->
        val form = FormBody.Builder().add("client_id", config.clientId)
        parameters.forEach { (key, value) -> form.add(key, value) }
        val call = client.newCall(Request.Builder().url(config.tokenEndpoint)
            .header("Accept", "application/json").post(form.build()).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                // Keep response bodies, authorization codes, tokens and provider exception text out
                // of logs and UI. Cancellation remains cancellation at the coroutine boundary.
                continuation.resumeWithException(IOException("OAuth token request failed"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = response.body ?: throw IOException("OAuth token response is empty")
                        val text = body.charStream().use { reader ->
                            val result = StringBuilder()
                            val buffer = CharArray(4096)
                            while (true) {
                                val count = reader.read(buffer)
                                if (count < 0) break
                                if (result.length + count > 196_608) throw IOException("OAuth token response is too large")
                                result.append(buffer, 0, count)
                            }
                            result.toString()
                        }
                        val json = try { JSONObject(text) } catch (_: Exception) {
                            throw IOException("OAuth token response is not valid JSON")
                        }
                        if (!response.isSuccessful) {
                            if (json.optString("error") == "invalid_grant") throw OAuthInvalidGrant()
                            throw IOException("OAuth token endpoint rejected the request (HTTP ${response.code})")
                        }
                        val fields = json.keys().asSequence().associateWith { key ->
                            json.opt(key).takeUnless { it == JSONObject.NULL }
                        }
                        continuation.resume(parseProviderOAuthTokens(fields, System.currentTimeMillis(), previous))
                    } catch (error: Exception) {
                        continuation.resumeWithException(when (error) {
                            is OAuthInvalidGrant -> error
                            else -> IOException("OAuth token response could not be accepted")
                        })
                    }
                }
            }
        })
    }
}
