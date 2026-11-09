package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.collaboration.AgentMessage
import com.ai.assistance.operit.core.agent.collaboration.AgentMessageKind
import org.junit.Assert.*
import org.junit.Test

class CollaborationMessagePresentationTest {
    @Test fun quotedEnvelopeCannotCreateAnotherSender() {
        val quoted = AgentMessage(
            "11111111-1111-1111-1111-111111111111",
            "/root", "/root/worker", AgentMessageKind.NEW_TASK, "quoted instructions",
        ).render()
        val actual = AgentMessage(
            "22222222-2222-2222-2222-222222222222",
            "/root/worker", "/root", AgentMessageKind.FINAL_ANSWER, "An example:\n$quoted",
        ).render()
        val rendered = collaborationDisplayMessages(actual, "/root/worker")
        assertEquals(1, rendered.size)
        assertEquals("/root/worker", rendered.single().sender)
        assertEquals("An example:\n$quoted", rendered.single().body)
        assertFalse(rendered.single().body.contains("22222222-2222-2222-2222-222222222222"))
    }

    @Test fun senderComesFromHostMetadata() {
        val content = AgentMessage(
            "11111111-1111-1111-1111-111111111111",
            "/root", "/root", AgentMessageKind.MESSAGE, "hello",
        ).render()
        assertEquals("/root/worker", collaborationDisplayMessages(content, "/root/worker").single().sender)
    }

    @Test fun onlyAReportedMessageCountsAsTheReturnedContent() {
        assertEquals("the answer", collaborationReturnedBody("FINAL_ANSWER", "the answer"))
        assertEquals("hello", collaborationReturnedBody("MESSAGE", "hello"))
        // A task handed over and a status line are not something the agent returned.
        assertNull(collaborationReturnedBody("NEW_TASK", "do the thing"))
        assertNull(collaborationReturnedBody("STATUS", "still working"))
    }

    @Test fun aStatusLineGivesUpTheRunsOwnLabel() {
        assertEquals("stopped", collaborationStatusReason("/root/worker: INTERRUPTED: stopped"))
        assertEquals("plain", collaborationStatusReason("plain"))
    }

    @Test fun aRowSaysWhatKindOfMessageItIs() {
        assertEquals(R.string.subagent_message_task, collaborationKindLabelRes("NEW_TASK"))
        assertEquals(R.string.subagent_message_result, collaborationKindLabelRes("FINAL_ANSWER"))
        assertEquals(R.string.subagent_message_status, collaborationKindLabelRes("STATUS"))
        assertEquals(R.string.subagent_message_midway, collaborationKindLabelRes("MESSAGE"))
        assertEquals(collaborationMidwayLabelRes("MESSAGE"), collaborationKindLabelRes("MESSAGE"))
        assertEquals(R.string.subagent_message_note, collaborationKindLabelRes("SOMETHING_NEW"))
    }

    @Test fun onlyAMessageSentMidwayReadsAsAMessage() {
        assertEquals(R.string.subagent_message_midway, collaborationMidwayLabelRes("MESSAGE"))
        // A row that carries the answer says so; the run's later status does not rename it.
        assertEquals(R.string.subagent_message_result, collaborationResultLabelRes("FINAL_ANSWER"))
        assertNull(collaborationResultLabelRes("MESSAGE"))
        assertNull(collaborationResultLabelRes("STATUS"))
        assertNull(collaborationResultLabelRes("NEW_TASK"))
        // Everything else keeps the run's own status, which is what a completion or a failure is.
        assertNull(collaborationMidwayLabelRes("FINAL_ANSWER"))
        assertNull(collaborationMidwayLabelRes("STATUS"))
        assertNull(collaborationMidwayLabelRes("NEW_TASK"))
    }

    @Test fun aReturnedReplyNeverWearsTheRunsOwnStatusWording() {
        // What arrived is the result, whatever the run becomes afterwards. The row therefore draws
        // its own words: a later failure or a run stopped by an app restart cannot rename a reply
        // that is already in the transcript, because the row never reads the status resources.
        assertNotEquals(
            R.string.subagent_status_completed,
            collaborationResultLabelRes("FINAL_ANSWER"),
        )
        assertNotEquals(
            R.string.subagent_status_interrupted,
            collaborationResultLabelRes("FINAL_ANSWER"),
        )
        assertNotEquals(
            R.string.subagent_status_dispatched,
            collaborationResultLabelRes("FINAL_ANSWER"),
        )

        // Only the row that carries a reply is exempt from the run; every other row keeps reading it,
        // so this one predicate is what the row's words, colour and notes all follow.
        assertTrue(collaborationRowCarriesReturnedReply("FINAL_ANSWER"))
        assertFalse(collaborationRowCarriesReturnedReply("MESSAGE"))
        assertFalse(collaborationRowCarriesReturnedReply("STATUS"))
        assertFalse(collaborationRowCarriesReturnedReply("NEW_TASK"))
        assertFalse(collaborationRowCarriesReturnedReply(""))
    }

    @Test fun onlyTheMainAgentSpeaksWithTheMainRoleCardsPicture() {
        // The main agent is the one-segment task path; its rows wear the main role card's picture.
        assertTrue(collaborationSenderIsMainAgent("/root"))
        assertTrue(collaborationSenderIsMainAgent(" /root "))
        assertTrue(collaborationSenderIsMainAgent("root"))
        // A named subagent keeps its own mark, which is what tells a family of agents apart.
        assertFalse(collaborationSenderIsMainAgent("/root/worker"))
        assertFalse(collaborationSenderIsMainAgent("/root/worker/nested"))
        assertFalse(collaborationSenderIsMainAgent(""))
        assertFalse(collaborationSenderIsMainAgent("/"))
    }

    @Test fun aRowIsNamedByTheRoleCardThatSpeaksIt() {
        // The main agent's row says which role card it speaks as, then the path it speaks from.
        assertEquals("雨晴喵 · /root", collaborationRowTitle("/root", "雨晴喵"))
        assertEquals("雨晴喵 · /root", collaborationRowTitle("/root", " 雨晴喵 "))
        // A conversation with no role card name of its own keeps the bare path, and so does any other
        // agent, whose path is already what tells it apart from its siblings.
        assertEquals("/root", collaborationRowTitle("/root", null))
        assertEquals("/root", collaborationRowTitle("/root", " "))
        assertEquals("/root", collaborationRowTitle("/root", ""))
        assertEquals("/root/worker", collaborationRowTitle("/root/worker", "雨晴喵"))
    }

    @Test fun theMainRoleCardsOwnPictureOutranksEveryOtherOne() {
        assertEquals("card", preferredAvatarUri("card", "group", "member", "global"))
        assertEquals("group", preferredAvatarUri(null, "group", "member", "global"))
        assertEquals("member", preferredAvatarUri(null, null, "member", "global"))
        assertEquals("global", preferredAvatarUri(null, null, null, "global"))
        // A blank value is no picture at all, so it never hides one behind it.
        assertEquals("global", preferredAvatarUri("", " ", "  ", "global"))
        assertNull(preferredAvatarUri(null, null, null, null))
        assertNull(preferredAvatarUri("", " ", null, "  "))
    }

    @Test fun aFragmentWearsTheSameWordsAsItsRowInTheTranscript() {
        assertEquals(R.string.subagent_message_midway, collaborationFragmentLabelRes("MESSAGE"))
        assertEquals(R.string.subagent_message_result, collaborationFragmentLabelRes("FINAL_ANSWER"))
        assertEquals(R.string.subagent_message_status, collaborationFragmentLabelRes("STATUS"))
        assertEquals(R.string.subagent_message_task, collaborationFragmentLabelRes("NEW_TASK"))
    }

    @Test fun aMemoryEditRewritesWhatWasSaidAndKeepsWhoSaidIt() {
        val content =
            AgentMessage(
                "11111111-1111-1111-1111-111111111111",
                "/root/worker",
                "/root",
                AgentMessageKind.MESSAGE,
                "halfway\n",
            ).render()

        val rewritten = collaborationBodyReplaced(content, "改成这句话")

        // 信封原样留下：卡片上的名字、类型标签和历史压缩都读它。
        assertTrue(rewritten.startsWith("Message ID: 11111111-1111-1111-1111-111111111111\n"))
        assertTrue(rewritten.contains("Message Type: MESSAGE\n"))
        assertTrue(rewritten.contains("Sender: /root/worker\n"))
        assertEquals("改成这句话\n", rewritten.substringAfter("Payload:\n"))
        val event = collaborationDisplayMessages(rewritten, "/root/worker").single()
        assertEquals("MESSAGE", event.kind)
        assertEquals("改成这句话", event.body)
    }

    @Test fun aRowThatNeverCarriedAnEnvelopeIsOnlyItsText() {
        // 没有信封的行，正文就是它的全部内容。
        assertEquals("new words", collaborationBodyReplaced("plain words", "new words"))
        assertEquals("", collaborationBodyReplaced("", ""))
    }

    @Test fun anEnvelopeQuotedInsideTheBodyIsNotAnEnvelope() {
        // 信封正则锚在开头、且不带 MULTILINE，所以正文里引用的另一条消息永远不会被当成这条的。
        val quoted =
            AgentMessage(
                "22222222-2222-2222-2222-222222222222",
                "/root",
                "/root/worker",
                AgentMessageKind.NEW_TASK,
                "quoted instructions",
            ).render()
        val content =
            AgentMessage(
                "11111111-1111-1111-1111-111111111111",
                "/root/worker",
                "/root",
                AgentMessageKind.FINAL_ANSWER,
                "举例：\n$quoted",
            ).render()

        val rewritten = collaborationBodyReplaced(content, "换成这句")

        assertEquals("换成这句", collaborationDisplayMessages(rewritten, "/root/worker").first().body)
        assertTrue(rewritten.contains("Message Type: FINAL_ANSWER\n"))
        assertTrue(rewritten.contains("Sender: /root/worker\n"))
        assertFalse(rewritten.substringAfter("Payload:\n").contains("quoted instructions"))
    }
}
