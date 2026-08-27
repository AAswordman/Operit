package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAIResponsesFileInputTest {
    @Test
    fun mapsPdfContentToResponsesInputFile() {
        val request = JSONObject().apply {
            put("model", "gpt-5.6-luna")
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("type", "input_file")
                                    put("filename", "report.pdf")
                                    put("file_data", "data:application/pdf;base64,ZmFrZQ==")
                                },
                            ),
                        )
                    },
                ),
            )
        }

        val converted = OpenAIResponsesPayloadAdapter.toResponsesRequest(request)
        val inputMessage = converted.getJSONArray("input").getJSONObject(0)
        val file = inputMessage.getJSONArray("content").getJSONObject(0)

        assertEquals("input_file", file.getString("type"))
        assertEquals("report.pdf", file.getString("filename"))
        assertEquals("data:application/pdf;base64,ZmFrZQ==", file.getString("file_data"))
    }
}
