package com.ai.assistance.operit.data.mcp.plugins

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun HttpClientConfig<*>.installMcpHttpResponseGuard(
    pendingRequestIds: () -> Set<RequestId>,
    logWarning: (String) -> Unit
) {
    install(createClientPlugin("McpHttpResponseGuard") {
        val ignoredResponses = AtomicLong()

        // bodyAsText uses Source, which Ktor parses before its transformResponseBody hooks.
        client.responsePipeline.intercept(HttpResponsePipeline.Transform) {
            val response = context.response
            if (subject.expectedType.type != Source::class ||
                response.call.request.method != HttpMethod.Post ||
                !response.status.isSuccess() ||
                response.status == HttpStatusCode.Accepted ||
                response.contentType()?.withoutParameters() != ContentType.Application.Json
            ) {
                return@intercept
            }
            val outgoing = response.call.request.content as? TextContent ?: return@intercept
            val notification = McpJson.parseToJsonElement(outgoing.text) as? JsonObject
                ?: return@intercept
            val method = notification["method"] as? JsonPrimitive ?: return@intercept
            if ("id" in notification || !method.isString) return@intercept

            val body = subject.response as? Source ?: return@intercept
            if (body.exhausted()) return@intercept
            val envelope = McpJson.parseToJsonElement(body.peek().use { it.readByteArray().decodeToString() }) as? JsonObject
                ?: return@intercept
            if (envelope["jsonrpc"] != JsonPrimitive("2.0") || "method" in envelope ||
                ("result" in envelope) == ("error" in envelope)
            ) {
                return@intercept
            }

            val rawId = envelope["id"] as? JsonPrimitive ?: return@intercept
            val id = when {
                rawId is JsonNull && "error" in envelope -> null
                rawId is JsonNull -> return@intercept
                rawId.isString -> RequestId(rawId.content)
                else -> RequestId(rawId.longOrNull ?: return@intercept)
            }
            val pendingIds = pendingRequestIds()
            if (id in pendingIds) return@intercept

            // The SDK decodes result types before correlating IDs, so orphan payloads can abort initialization.
            val kind = if ("error" in envelope) "error" else "result"
            val safeId = when (id) {
                is RequestId.StringId -> JsonPrimitive(id.value.take(128)).toString()
                is RequestId.NumberId -> id.value.toString()
                null -> "null"
            }
            logWarning(
                "Ignoring unmatched JSON-RPC response: id=$safeId, kind=$kind, " +
                    "notification=${JsonPrimitive(method.content.take(128))}, " +
                    "httpStatus=${response.status.value}, pendingRequests=${pendingIds.size}, " +
                    "ignoredResponses=${ignoredResponses.incrementAndGet()}; " +
                    "no pending request matches this response (server protocol violation)"
            )
            body.close()
            proceedWith(HttpResponseContainer(subject.expectedType, Buffer()))
        }
    })
}
