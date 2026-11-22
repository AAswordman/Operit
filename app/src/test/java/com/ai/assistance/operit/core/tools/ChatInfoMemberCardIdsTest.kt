package com.ai.assistance.operit.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatInfoMemberCardIdsTest {
    @Test
    fun `list and find results expose the same ordered member ids`() {
        val chat = chatInfo("group-1", listOf("card-b", "card-a"))
        val expected = JsonArray(listOf(JsonPrimitive("card-b"), JsonPrimitive("card-a")))

        assertEquals(expected, listChatJson(chat)["memberCardIds"])
        val findResult = Json.parseToJsonElement(ChatFindResultData(1, chat).toJson()).jsonObject
        assertEquals(expected, findResult.getValue("chat").jsonObject["memberCardIds"])
    }

    @Test
    fun `an empty group roster is serialized as an empty array`() {
        val chat = chatInfo("group-1", emptyList())

        assertEquals(JsonArray(emptyList()), listChatJson(chat)["memberCardIds"])
    }

    @Test
    fun `direct chats and deleted groups omit the unavailable roster`() {
        assertFalse(listChatJson(chatInfo(null, null)).containsKey("memberCardIds"))
        assertFalse(listChatJson(chatInfo("deleted-group", null)).containsKey("memberCardIds"))
    }

    private fun listChatJson(chat: ChatListResultData.ChatInfo): JsonObject {
        val result = ChatListResultData(1, null, listOf(chat))
        return Json.parseToJsonElement(result.toJson()).jsonObject
            .getValue("chats").jsonArray.single().jsonObject
    }

    private fun chatInfo(groupId: String?, memberCardIds: List<String>?): ChatListResultData.ChatInfo =
        ChatListResultData.ChatInfo(
            id = "chat-1",
            title = "Example",
            messageCount = 0,
            createdAt = "2026-11-22T00:00:00",
            updatedAt = "2026-11-22T00:00:00",
            isCurrent = false,
            inputTokens = 0L,
            outputTokens = 0L,
            characterGroupId = groupId,
            memberCardIds = memberCardIds,
        )
}
