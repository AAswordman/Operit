package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.data.model.FunctionType
import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionalConfigMappingRepairTest {
    @Test
    fun remapsAllFunctionsUsingDeletedConfigToDefault() {
        val mapping = mapOf(
            FunctionType.CHAT to FunctionConfigMapping("deleted", 4),
            FunctionType.SUMMARY to FunctionConfigMapping("default", 2),
            FunctionType.TITLE_GENERATION to FunctionConfigMapping("deleted", 1),
        )

        val repair = remapDeletedConfigReferences(mapping, "deleted")

        assertEquals(
            listOf(FunctionType.CHAT, FunctionType.TITLE_GENERATION),
            repair.affectedFunctions,
        )
        assertEquals(FunctionConfigMapping("default", 0), repair.mapping[FunctionType.CHAT])
        assertEquals(FunctionConfigMapping("default", 0), repair.mapping[FunctionType.TITLE_GENERATION])
        assertEquals(FunctionConfigMapping("default", 2), repair.mapping[FunctionType.SUMMARY])
    }

    @Test
    fun leavesMappingUnchangedWhenDeletedConfigIsNotReferenced() {
        val mapping = mapOf(
            FunctionType.CHAT to FunctionConfigMapping("default", 0),
            FunctionType.SUMMARY to FunctionConfigMapping("other", 2),
        )

        val repair = remapDeletedConfigReferences(mapping, "deleted")

        assertEquals(emptyList<FunctionType>(), repair.affectedFunctions)
        assertEquals(mapping, repair.mapping)
    }
}
