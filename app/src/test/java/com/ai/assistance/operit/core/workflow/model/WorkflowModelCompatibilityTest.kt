package com.ai.assistance.operit.core.workflow.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工作流文件保留旧包名作为多态标识，代码迁移不能改变已有文件的读写合同。 */
class WorkflowModelCompatibilityTest {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "__type"
    }

    @Test
    fun readsEveryLegacyNodeKindAndNestedParameterValue() {
        val stored = """
            {
              "id":"legacy-workflow",
              "nodes":[
                {"__type":"com.ai.assistance.operit.data.model.TriggerNode","id":"trigger"},
                {"__type":"com.ai.assistance.operit.data.model.ExecuteNode","id":"execute",
                 "actionType":"read_file","actionConfig":{
                   "path":{"__type":"com.ai.assistance.operit.data.model.ParameterValue.StaticValue","value":"/sdcard/example.txt"},
                   "previous":{"__type":"com.ai.assistance.operit.data.model.ParameterValue.NodeReference","nodeId":"trigger"}
                 }},
                {"__type":"com.ai.assistance.operit.data.model.ConditionNode","id":"condition"},
                {"__type":"com.ai.assistance.operit.data.model.LogicNode","id":"logic"},
                {"__type":"com.ai.assistance.operit.data.model.ExtractNode","id":"extract"}
              ]
            }
        """.trimIndent()

        val workflow = json.decodeFromString<Workflow>(stored)

        assertEquals("legacy-workflow", workflow.id)
        assertEquals(
            listOf("trigger", "execute", "condition", "logic", "extract"),
            workflow.nodes.map { it.id },
        )
        assertTrue(workflow.nodes[0] is TriggerNode)
        assertTrue(workflow.nodes[2] is ConditionNode)
        assertTrue(workflow.nodes[3] is LogicNode)
        assertTrue(workflow.nodes[4] is ExtractNode)
        val execute = workflow.nodes[1] as ExecuteNode
        assertEquals("read_file", execute.actionType)
        assertEquals(ParameterValue.StaticValue("/sdcard/example.txt"), execute.actionConfig["path"])
        assertEquals(ParameterValue.NodeReference("trigger"), execute.actionConfig["previous"])
    }

    @Test
    fun writesLegacyDiscriminatorsForOldReaders() {
        val source = Workflow(
            id = "new-workflow",
            createdAt = 10L,
            updatedAt = 20L,
            nodes = listOf(
                TriggerNode(id = "trigger"),
                ExecuteNode(
                    id = "execute",
                    actionConfig = mapOf(
                        "text" to ParameterValue.StaticValue("内容"),
                        "previous" to ParameterValue.NodeReference("trigger"),
                    ),
                ),
                ConditionNode(id = "condition"),
                LogicNode(id = "logic"),
                ExtractNode(id = "extract"),
            ),
        )

        val encoded = json.encodeToString(source)
        val nodes = json.parseToJsonElement(encoded).jsonObject.getValue("nodes").jsonArray
        assertEquals(
            listOf("TriggerNode", "ExecuteNode", "ConditionNode", "LogicNode", "ExtractNode")
                .map { "com.ai.assistance.operit.data.model.$it" },
            nodes.map { it.jsonObject.getValue("__type").jsonPrimitive.content },
        )
        val parameters = nodes[1].jsonObject.getValue("actionConfig").jsonObject
        assertEquals(
            "com.ai.assistance.operit.data.model.ParameterValue.StaticValue",
            parameters.getValue("text").jsonObject.getValue("__type").jsonPrimitive.content,
        )
        assertEquals(
            "com.ai.assistance.operit.data.model.ParameterValue.NodeReference",
            parameters.getValue("previous").jsonObject.getValue("__type").jsonPrimitive.content,
        )
        assertEquals(source, json.decodeFromString<Workflow>(encoded))
    }
}
