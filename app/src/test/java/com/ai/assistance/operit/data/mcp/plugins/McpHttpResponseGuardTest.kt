package com.ai.assistance.operit.data.mcp.plugins

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class McpHttpResponseGuardTest {
    @Test(timeout = 10_000)
    fun compliantNotificationAllowsToolsAndPing() = runBlocking {
        withServer(Reply("", HttpStatusCode.Accepted)) { client, warnings ->
            assertTrue(client.listTools().tools.isEmpty())
            client.ping()
            assertTrue(warnings.isEmpty())
        }
    }

    @Test(timeout = 10_000)
    fun orphanNotificationResultDoesNotAbortInitialization() = runBlocking {
        withServer(Reply(ORPHAN_RESULT)) { client, warnings ->
            assertTrue(client.listTools().tools.isEmpty())
            client.ping()
            val warning = warnings.single()
            assertTrue(warning.contains("Ignoring unmatched JSON-RPC response"))
            assertTrue(warning.contains("id=\"orphan-id\""))
            assertTrue(warning.contains("notification=\"notifications/initialized\""))
            assertTrue(warning.contains("httpStatus=200"))
            assertTrue(warning.contains("pendingRequests=0"))
            assertTrue(warning.contains("ignoredResponses=1"))
            assertFalse(warning.contains("_notification_handled"))
        }
    }

    @Test(timeout = 10_000)
    fun knownOrphanResultAlsoProducesDiagnostic() = runBlocking {
        withServer(Reply("""{"jsonrpc":"2.0","id":42,"result":{}}""")) { client, warnings ->
            client.listTools()
            client.ping()
            assertTrue(warnings.single().contains("id=42, kind=result"))
        }
    }

    @Test(timeout = 10_000)
    fun orphanErrorDoesNotFailSubsequentRequests() = runBlocking {
        withServer(Reply("""{"jsonrpc":"2.0","id":"orphan-id","error":{"code":-32601,"message":"private server detail"}}""")) { client, warnings ->
            client.listTools()
            client.ping()
            assertTrue(warnings.single().contains("kind=error"))
            assertFalse(warnings.single().contains("private server detail"))
        }
    }

    @Test(timeout = 10_000)
    fun errorWithNullIdHasNoPendingRequest() = runBlocking {
        withServer(Reply("""{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid request"}}""")) { client, warnings ->
            client.ping()
            assertTrue(warnings.single().contains("id=null, kind=error"))
        }
    }

    @Test(timeout = 10_000)
    fun matchingRequestErrorStillFailsThatRequest() = runBlocking {
        withServer(Reply(ORPHAN_RESULT), failTools = true) { client, warnings ->
            try {
                client.listTools()
                fail("Expected the matching tools/list error")
            } catch (error: McpException) {
                assertTrue(error.message.orEmpty().contains("tools unavailable"))
            }
            client.ping()
            assertEquals(1, warnings.size)
        }
    }

    @Test(timeout = 10_000)
    fun serverNotificationIsStillDispatched() = runBlocking {
        val notifications = mutableListOf<String>()
        withServer(
            Reply("""{"jsonrpc":"2.0","method":"notifications/tools/list_changed"}"""),
            receivedNotifications = notifications
        ) { client, warnings ->
            client.listTools()
            client.ping()
            assertEquals(listOf("notifications/tools/list_changed"), notifications)
            assertTrue(warnings.isEmpty())
        }
    }

    @Test(timeout = 10_000)
    fun inlineSseIsNotRewritten() = runBlocking {
        val event = "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}\n\n"
        withServer(Reply(event, contentType = ContentType.Text.EventStream)) { client, warnings ->
            client.listTools()
            client.ping()
            assertTrue(warnings.isEmpty())
        }
    }

    @Test(timeout = 10_000)
    fun malformedNotificationReplyStillFailsInitialization() = runBlocking {
        try {
            withServer(Reply("{not-json")) { _, _ -> fail("Expected invalid JSON to fail") }
            fail("Expected initialization to fail")
        } catch (error: McpException) {
            assertTrue(error.message.orEmpty().contains("Error while sending message"))
        }
    }

    @Test(timeout = 10_000)
    fun httpErrorIsNotIgnoredEvenWithOrphanBody() = runBlocking {
        try {
            withServer(Reply(ORPHAN_RESULT, HttpStatusCode.BadRequest)) { _, _ -> fail("Expected HTTP error") }
            fail("Expected initialization to fail")
        } catch (error: McpException) {
            assertTrue(error.message.orEmpty().contains("Streamable HTTP error"))
        }
    }

    @Test(timeout = 10_000)
    fun matchingIdsArePreservedIncludingUnknownResultShapes() = runBlocking {
        val pending = setOf(RequestId("orphan-id"))
        val (body, warnings) = readReply(Reply(ORPHAN_RESULT), pending)
        assertEquals(ORPHAN_RESULT, body)
        assertTrue(warnings.isEmpty())
        assertEquals(setOf(RequestId("orphan-id")), pending)
    }

    @Test(timeout = 10_000)
    fun stringAndNumericIdsAreNotInterchangeable() = runBlocking {
        val response = """{"jsonrpc":"2.0","id":"42","result":{}}"""
        val (body, warnings) = readReply(Reply(response), setOf(RequestId(42L)))
        assertEquals("", body)
        assertTrue(warnings.single().contains("pendingRequests=1"))
        val (matchingBody, matchingWarnings) = readReply(Reply(response), setOf(RequestId("42")))
        assertEquals(response, matchingBody)
        assertTrue(matchingWarnings.isEmpty())
    }

    @Test(timeout = 10_000)
    fun matchingErrorOnNotificationResponseIsPreserved() = runBlocking {
        val response = """{"jsonrpc":"2.0","id":42,"error":{"code":-32601,"message":"Not found"}}"""
        val (body, warnings) = readReply(Reply(response), setOf(RequestId(42L)))
        assertEquals(response, body)
        assertTrue(warnings.isEmpty())
    }

    @Test(timeout = 10_000)
    fun pendingRequestCanCompleteThroughNotificationHttpResponse() = runBlocking {
        val pendingId = CompletableDeferred<JsonElement>()
        val warnings = mutableListOf<String>()
        val client = Client(Implementation("Operit", "test"))
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond("", HttpStatusCode.MethodNotAllowed)
            } else {
                val message = McpJson.parseToJsonElement((request.body as TextContent).text).jsonObject
                val id = message["id"]
                val reply = when (message["method"]!!.jsonPrimitive.content) {
                    "initialize" -> {
                        val version = message["params"]!!.jsonObject["protocolVersion"]
                        """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":$version,"capabilities":{},"serverInfo":{"name":"test","version":"1"}}}"""
                    }
                    "ping" -> {
                        pendingId.complete(checkNotNull(id))
                        ""
                    }
                    "notifications/initialized" -> if (pendingId.isCompleted) {
                        """{"jsonrpc":"2.0","id":${pendingId.await()},"result":{}}"""
                    } else {
                        ""
                    }
                    else -> error("Unexpected method: $message")
                }
                respond(
                    reply,
                    if (reply.isEmpty()) HttpStatusCode.Accepted else HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val http = HttpClient(engine) {
            install(SSE)
            installMcpHttpResponseGuard({ client.responseHandlers.keys }, warnings::add)
        }
        try {
            client.connect(StreamableHttpClientTransport(http, "http://mcp.test/mcp"))
            val ping = async { client.ping() }
            pendingId.await()
            assertEquals(1, client.responseHandlers.size)
            client.notification(InitializedNotification())
            ping.await()
            assertTrue(client.responseHandlers.isEmpty())
            assertTrue(warnings.isEmpty())
        } finally {
            client.close()
            http.close()
        }
    }

    /**
     * The runtime ships with the OkHttp engine, so replay the issue scenario over a real loopback
     * connection: MockEngine alone would not prove that Ktor also hands an OkHttp response body to
     * the guard as a Source, which is what the interception relies on.
     */
    @Test(timeout = 30_000)
    fun realOkHttpEngineIgnoresUnmatchedNotificationResponse() = runBlocking {
        val warnings = mutableListOf<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "GET") {
                    return MockResponse().setResponseCode(405)
                }
                val message = McpJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                val id = message["id"]
                val reply = when (message["method"]!!.jsonPrimitive.content) {
                    "initialize" -> {
                        val version = message["params"]!!.jsonObject["protocolVersion"]
                        """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":$version,"capabilities":{"tools":{}},"serverInfo":{"name":"repro-bad-server","version":"0.1.0"}}}"""
                    }
                    "notifications/initialized" -> ORPHAN_RESULT
                    "tools/list" -> """{"jsonrpc":"2.0","id":$id,"result":{"tools":[]}}"""
                    "ping" -> """{"jsonrpc":"2.0","id":$id,"result":{}}"""
                    else -> """{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Method not found"}}"""
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    .setBody(reply)
            }
        }
        server.start()
        val client = Client(Implementation("Operit", "test"))
        val http = HttpClient(OkHttp) {
            install(SSE)
            installMcpHttpResponseGuard({ client.responseHandlers.keys }, warnings::add)
        }
        try {
            client.connect(StreamableHttpClientTransport(http, server.url("/mcp").toString()))
            assertTrue(client.listTools().tools.isEmpty())
            client.ping()
            assertTrue(warnings.single().contains("Ignoring unmatched JSON-RPC response"))
        } finally {
            client.close()
            http.close()
            server.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun responsesToRequestsAreNotRewritten() = runBlocking {
        val (body, warnings) = readReply(
            Reply(ORPHAN_RESULT),
            outgoing = """{"jsonrpc":"2.0","id":"pending","method":"ping"}"""
        )
        assertEquals(ORPHAN_RESULT, body)
        assertTrue(warnings.isEmpty())
    }

    @Test(timeout = 10_000)
    fun invalidEnvelopesAreNotTreatedAsOrphans() = runBlocking {
        for (response in listOf(
            """{"jsonrpc":"2.0","result":{}}""",
            """{"jsonrpc":"2.0","id":null,"result":{}}""",
            """{"jsonrpc":"2.0","id":true,"result":{}}""",
            """{"jsonrpc":"1.0","id":"other","result":{}}""",
            """{"jsonrpc":"2.0","id":"other","result":{},"error":{}}""",
            """{"jsonrpc":"2.0","id":"other","method":"ping"}"""
        )) {
            val (body, warnings) = readReply(Reply(response))
            assertEquals(response, body)
            assertTrue(warnings.isEmpty())
        }
    }

    @Test(timeout = 10_000)
    fun diagnosticBoundsAndEscapesServerControlledIds() = runBlocking {
        val id = JsonPrimitive("line1\nline2\r" + "x".repeat(500))
        val (_, warnings) = readReply(Reply("""{"jsonrpc":"2.0","id":$id,"result":{"secret":"do-not-log"}}"""))
        val warning = warnings.single()
        assertFalse(warning.contains('\n'))
        assertFalse(warning.contains('\r'))
        assertFalse(warning.contains("do-not-log"))
        assertFalse(warning.contains("private-token"))
        assertTrue(warning.length < 500)
    }

    @Test(timeout = 10_000)
    fun diagnosticNormalizesLongNumericIds() = runBlocking {
        val numericId = "1e" + "0".repeat(2048)
        val (_, warnings) = readReply(Reply("""{"jsonrpc":"2.0","id":$numericId,"result":{}}"""))
        val warning = warnings.single()
        assertTrue(warning.contains("id=1, kind=result"))
        assertTrue(warning.contains("ignoredResponses=1"))
        assertTrue(warning.length < 500)
    }

    private suspend fun withServer(
        initializedReply: Reply,
        failTools: Boolean = false,
        receivedNotifications: MutableList<String> = mutableListOf(),
        block: suspend (Client, List<String>) -> Unit
    ) {
        val warnings = mutableListOf<String>()
        val client = Client(Implementation("Operit", "test"))
        client.fallbackNotificationHandler = { receivedNotifications += it.method }
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond("", HttpStatusCode.MethodNotAllowed)
            } else {
                val message = McpJson.parseToJsonElement((request.body as TextContent).text).jsonObject
                val id = message["id"]
                val reply = when (message["method"]!!.jsonPrimitive.content) {
                    "initialize" -> {
                        val version = message["params"]!!.jsonObject["protocolVersion"]
                        Reply("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":$version,"capabilities":{"tools":{}},"serverInfo":{"name":"repro-bad-server","version":"0.1.0"}}}""")
                    }
                    "notifications/initialized" -> {
                        assertFalse(message.containsKey("id"))
                        initializedReply
                    }
                    "tools/list" -> if (failTools) {
                        Reply("""{"jsonrpc":"2.0","id":$id,"error":{"code":-32603,"message":"tools unavailable"}}""")
                    } else {
                        Reply("""{"jsonrpc":"2.0","id":$id,"result":{"tools":[]}}""")
                    }
                    "ping" -> Reply("""{"jsonrpc":"2.0","id":$id,"result":{}}""")
                    else -> error("Unexpected method: $message")
                }
                respond(reply.body, reply.status, headersOf(HttpHeaders.ContentType, reply.contentType.toString()))
            }
        }
        val http = HttpClient(engine) {
            install(SSE)
            installMcpHttpResponseGuard({ client.responseHandlers.keys }, warnings::add)
        }
        try {
            client.connect(StreamableHttpClientTransport(http, "http://mcp.test/mcp"))
            block(client, warnings)
        } finally {
            client.close()
            http.close()
        }
    }

    private suspend fun readReply(
        reply: Reply,
        pendingIds: Set<RequestId> = emptySet(),
        outgoing: String = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
    ): Pair<String, List<String>> {
        val warnings = mutableListOf<String>()
        val http = HttpClient(MockEngine {
            respond(reply.body, reply.status, headersOf(HttpHeaders.ContentType, reply.contentType.toString()))
        }) {
            installMcpHttpResponseGuard({ pendingIds }, warnings::add)
        }
        return try {
            val body = http.post("http://mcp.test/mcp?token=private-token") { setBody(outgoing) }.bodyAsText()
            body to warnings
        } finally {
            http.close()
        }
    }

    private data class Reply(
        val body: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
        val contentType: ContentType = ContentType.Application.Json
    )

    companion object {
        private const val ORPHAN_RESULT = """{"jsonrpc":"2.0","id":"orphan-id","result":{"_notification_handled":true}}"""
    }
}
