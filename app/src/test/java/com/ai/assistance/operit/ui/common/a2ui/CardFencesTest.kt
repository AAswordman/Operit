package com.ai.assistance.operit.ui.common.a2ui

import org.junit.Assert.*
import org.junit.Test

class CardFencesTest {
    private val marker = "<" + "tool name='example'>payload<" + "/tool>"

    @Test fun cardTextCannotBecomeXmlToolInputAndOffsetsRemainStable() {
        val card = "```a2ui\n$marker\n```\n"
        val reply = "Before\n$card$marker"
        val masked = CardFences.forTools(reply)
        assertEquals(reply.length, masked.length)
        assertEquals(reply.count { it == '\n' }, masked.count { it == '\n' })
        assertEquals(reply.lastIndexOf(marker), masked.indexOf(marker))
        assertEquals(masked.indexOf(marker), masked.lastIndexOf(marker))
    }

    @Test fun completeAndIncompleteAlternateFencesAreExcluded() {
        for (card in listOf(
            "~~~a2ui\r\n$marker\r\n~~~",
            "   ````a2ui\n$marker\n   ````",
            "```a2ui-ref\n$marker\n```",
            "```a2ui\n$marker",
        )) assertFalse(CardFences.forTools(card).contains(marker))
    }

    @Test fun protectionRestoresCardsAfterNormalizationChangesEarlierTextLength() {
        val card = "```a2ui\n$marker\n```"
        val source = "first\n$card\nsecond\n$card"
        val protectedCards = CardFences.protect(source)
        assertFalse(protectedCards.text.contains(marker))
        assertEquals(source.replace("first", "longer prefix"),
            protectedCards.restore(protectedCards.text.replace("first", "longer prefix")))
    }

    @Test fun ordinaryTextAndOuterExampleFenceKeepTheirOriginalBehavior() {
        assertSame(marker, CardFences.forTools(marker))
        val example = "````example\n```a2ui\n$marker\n```\n````"
        assertEquals(example, CardFences.forTools(example))
    }
}