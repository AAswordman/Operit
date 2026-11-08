package com.ai.assistance.operit.core.tools.agent

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhoneSequenceStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pkg = "com.example.app"
    private fun template() = PhoneLocalSequence(pkg, listOf(
        PhoneLocalStep(PhoneLocalAction.Type, PhoneTarget(id = "query"), null, "{{query}}"),
    ), "fill-query")

    @Test fun reusableTemplateBindsFreshArgumentsOnEachUse() {
        val store = PhoneSequenceStore(temporary.newFolder()) { "v1" }
        store.save(template())
        val loaded = store.load(pkg, "fill-query")
        assertEquals("first", loaded.bind(mapOf("query" to "first")).steps.single().text)
        assertEquals("second", loaded.bind(mapOf("query" to "second")).steps.single().text)
        assertEquals("{{query}}", loaded.steps.single().text)
        assertEquals(listOf("fill-query"), store.names(pkg))
    }

    @Test fun appUpdateHidesAndRejectsPreviousVersionTemplate() {
        var version = "v1"
        val store = PhoneSequenceStore(temporary.newFolder()) { version }
        store.save(template())
        version = "v2"
        assertTrue(store.names(pkg).isEmpty())
        assertThrows(PhoneUiException::class.java) { store.load(pkg, "fill-query") }
    }

    @Test fun sameTemplateNameCannotCrossAppBoundary() {
        val store = PhoneSequenceStore(temporary.newFolder()) { "v1" }
        store.save(template())
        assertTrue(store.names("com.other.app").isEmpty())
        assertThrows(PhoneUiException::class.java) { store.load("com.other.app", "fill-query") }
    }

    @Test fun literalInputCannotBeWrittenToReusableStorage() {
        val folder = temporary.newFolder()
        val store = PhoneSequenceStore(folder) { "v1" }
        assertThrows(PhoneUiException::class.java) {
            store.save(template().copy(steps = listOf(template().steps.single().copy(text = "private input"))))
        }
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }
}
