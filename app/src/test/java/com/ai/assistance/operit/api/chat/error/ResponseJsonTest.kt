package com.ai.assistance.operit.api.chat.error

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ResponseJsonTest {
    @Test
    fun invalidStreamingAndNonStreamingJsonHaveDifferentCodes() {
        listOf(true to 5080, false to 5081).forEach { (streaming, expected) ->
            val error = runCatching { ResponseJson.parseObject("{broken", streaming) }.exceptionOrNull()!!
            assertEquals(expected, RequestFailure.describe(error)!!.reason!!.code)
        }
    }

    @Test
    fun validJsonWithMissingRequiredFieldsIsAShapeError() {
        val error = runCatching { ResponseJson.parseObject("{}", false).getJSONArray("choices") }.exceptionOrNull() as JSONException
        assertEquals(5084, RequestFailure.describe(ResponseJson.schemaFailure(error))!!.reason!!.code)
    }

    @Test
    fun malformedJsonArrayUsesTheSameSyntaxDomainAsAnObject() {
        val error = runCatching { ResponseJson.parseArray("[broken", true) }.exceptionOrNull()!!
        assertEquals(5080, RequestFailure.describe(error)!!.reason!!.code)
    }

    @Test
    fun anUnexpectedClientExceptionIsNotRelabelledAsJsonSyntaxFailure() {
        val original = IllegalStateException("unexpected state")
        assertSame(original, ResponseJson.schemaFailure(original))
    }
}
