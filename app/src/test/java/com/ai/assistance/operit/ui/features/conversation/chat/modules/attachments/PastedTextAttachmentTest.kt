package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastedTextAttachmentTest {

    @Test
    fun extractClipboardPastedText_returnsTextInsertedAtCursor() {
        val previous = TextFieldValue("Before after", selection = TextRange(7))
        val proposed = TextFieldValue("Before inserted after", selection = TextRange(16))

        val pastedText = extractClipboardPastedText(previous, proposed, "inserted ")

        assertEquals("inserted ", pastedText)
    }

    @Test
    fun extractClipboardPastedText_returnsTextReplacingSelection() {
        val previous = TextFieldValue("first second", selection = TextRange(0, 5))
        val proposed = TextFieldValue("replacement second", selection = TextRange(11))

        val pastedText = extractClipboardPastedText(previous, proposed, "replacement")

        assertEquals("replacement", pastedText)
    }

    @Test
    fun extractClipboardPastedText_acceptsNormalizedClipboardLineEndings() {
        val previous = TextFieldValue("")
        val proposed = TextFieldValue("first\nsecond", selection = TextRange(12))

        val pastedText = extractClipboardPastedText(previous, proposed, "first\r\nsecond")

        assertEquals("first\nsecond", pastedText)
    }

    @Test
    fun extractClipboardPastedText_ignoresTextThatDoesNotMatchClipboard() {
        val previous = TextFieldValue("Before after", selection = TextRange(7))
        val proposed = TextFieldValue("Before inserted after", selection = TextRange(16))

        val pastedText = extractClipboardPastedText(previous, proposed, "different")

        assertNull(pastedText)
    }

    @Test
    fun extractInsertedText_returnsTextInsertedAtCursor() {
        val previous = TextFieldValue("Before after", selection = TextRange(7))
        val proposed = TextFieldValue("Before inserted after", selection = TextRange(16))

        val insertedText = extractInsertedText(previous, proposed)

        assertEquals("inserted ", insertedText)
    }

    @Test
    fun extractInsertedText_returnsNullWhenSurroundingTextChanged() {
        val previous = TextFieldValue("Before after", selection = TextRange(7))
        val proposed = TextFieldValue("Changed inserted after", selection = TextRange(16))

        val insertedText = extractInsertedText(previous, proposed)

        assertNull(insertedText)
    }
}
