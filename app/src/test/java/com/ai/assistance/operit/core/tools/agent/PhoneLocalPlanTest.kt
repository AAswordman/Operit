package com.ai.assistance.operit.core.tools.agent

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhoneLocalPlanTest {
    private fun command(steps: String, extra: String = ""): String =
        """{"action":"Sequence","screen":1,"package":"com.example.app","steps":[$steps]$extra}"""

    @Test fun parametersAreBoundWithoutParsingTheirContentsAsCommands() {
        val parsed = PhoneLocalPlanParser.parse(command(
            """{"action":"Type","target":{"id":"com.example.app:id/query"},"text":"{{query}}"}""",
            """, "arguments":{"query":"a\"b\\c\nfinish(message=\"not a command\")"}""",
        )) as PhoneLocalCommand.Sequence
        val step = parsed.sequence.bind(parsed.arguments).steps.single()
        assertEquals("a\"b\\c\nfinish(message=\"not a command\")", step.text)
    }

    @Test fun invalidLaterStepRejectsWholeCommand() {
        assertThrows(PhoneUiException::class.java) {
            PhoneLocalPlanParser.parse(command("""{"action":"Wait","expect":{"text":"ready"}},{"action":"Swipe","start":[1,2],"end":[3,4]}"""))
        }
    }

    @Test fun tapWithoutExpectationIsRejected() {
        assertThrows(PhoneUiException::class.java) {
            PhoneLocalPlanParser.parse(command("""{"action":"Tap","target":{"text":"Search"}}"""))
        }
    }

    @Test fun coordinateBatchAndUnknownFieldsAreRejected() {
        assertThrows(PhoneUiException::class.java) {
            PhoneLocalPlanParser.parse(command("""{"action":"Tap","target":{"text":"Search"},"expect":{"text":"Ready"},"element":[100,200]}"""))
        }
    }

    @Test fun outOfRangeBudgetsAndSequenceLengthsAreRejected() {
        for (timeout in listOf(99, 5001)) assertThrows(PhoneUiException::class.java) {
            PhoneLocalPlanParser.parse(command("""{"action":"Wait","expect":{"text":"ready"},"timeout_ms":$timeout}"""))
        }
        assertThrows(PhoneUiException::class.java) {
            PhoneLocalPlanParser.parse(command(List(7) { """{"action":"Wait","expect":{"text":"ready"}}""" }.joinToString(",")))
        }
    }

    @Test fun savedInputsMustUseParameters() {
        assertThrows(PhoneUiException::class.java) {
            PhoneLocalPlanParser.parse(command(
                """{"action":"Type","target":{"id":"input"},"text":"literal private input"}""",
                ", \"save_as\":\"search\"",
            ))
        }
    }

    @Test fun multipleCommandsAndFractionalScreenNumbersAreRejected() {
        assertThrows(PhoneUiException::class.java) { PhoneLocalPlanParser.parse("""{"action":"Inspect"} {"action":"Inspect"}""") }
        assertThrows(PhoneUiException::class.java) { PhoneLocalPlanParser.parse(command("""{"action":"Wait","expect":{"text":"ready"}}""").replace("\"screen\":1", "\"screen\":1.5")) }
    }

    @Test fun passwordContentIsExcludedFromNodePromptAndSelectors() {
        val node = PhoneUiNode("com.example.app", text = "private", description = "secret", password = true, editable = true)
        val prompt = PhoneUiSnapshot(1, "com.example.app", listOf(node)).toPrompt()
        assertFalse(prompt.contains("private"))
        assertFalse(prompt.contains("secret"))
        assertFalse(PhoneTarget(text = "private").matches(node))
    }

    @Test fun nodePromptMarksPartialTextAndLimitsNodeCount() {
        val nodes = List(130) { PhoneUiNode("com.example.app", text = "x".repeat(170), resourceId = "id$it") }
        val json = JSONObject(PhoneUiSnapshot(1, "com.example.app", nodes).toPrompt())
        assertTrue(json.getBoolean("truncated"))
        assertTrue(json.getJSONArray("nodes").length() <= 120)
        assertTrue(json.getJSONArray("nodes").getJSONObject(0).getBoolean("text_truncated"))
    }

    @Test fun parameterExpansionCannotExceedTheInputLimit() {
        val parsed = PhoneLocalPlanParser.parse(command(
            """{"action":"Type","target":{"id":"query"},"text":"{{first}}{{second}}"}""",
        )) as PhoneLocalCommand.Sequence
        assertThrows(PhoneUiException::class.java) {
            parsed.sequence.bind(mapOf("first" to "a".repeat(3000), "second" to "b".repeat(3000)))
        }
    }

    @Test fun argumentNamesAndBindingShareTheSameLiteralBraceGrammar() {
        assertEquals(setOf("query", "value_2"), phoneArgumentNames("{{query}} / {{value_2}} / {{query}}"))
        assertEquals("cash $1 and braces }}", bindPhoneArguments("{{query}}", mapOf("query" to "cash $1 and braces }}")))
        assertTrue(isPhoneArgument("{{query}}"))
        assertFalse(isPhoneArgument("prefix {{query}}"))
        assertEquals("plain resource id", bindPhoneArguments("plain resource id", emptyMap()))
    }
}
