package com.ai.assistance.operit.core.tools.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MCPToolParameterTest {

    @Test
    fun smartConvertPreservesNumericStringsWhenTypeIsString() {
        // Issue #1125: 显式声明为 "string" 的纯数字或浮点数坐标字符串不应被强转为 Double 或 Long
        val lat = "32.035599"
        val lng = "118.773097"
        val id = "10086"

        val convertedLat = MCPToolParameter.smartConvert(lat, "string")
        val convertedLng = MCPToolParameter.smartConvert(lng, "string")
        val convertedId = MCPToolParameter.smartConvert(id, "string")

        assertTrue("参数应保持为 String 类型", convertedLat is String)
        assertTrue("参数应保持为 String 类型", convertedLng is String)
        assertTrue("参数应保持为 String 类型", convertedId is String)

        assertEquals("32.035599", convertedLat)
        assertEquals("118.773097", convertedLng)
        assertEquals("10086", convertedId)
    }

    @Test
    fun smartConvertPreservesBooleanLikeStringsWhenTypeIsString() {
        val boolStr = "true"
        val converted = MCPToolParameter.smartConvert(boolStr, "string")
        assertTrue(converted is String)
        assertEquals("true", converted)
    }

    @Test
    fun smartConvertConvertsNumericStringsWhenTypeIsNumberOrInteger() {
        val numberResult = MCPToolParameter.smartConvert("32.035599", "number")
        val intResult = MCPToolParameter.smartConvert("42", "integer")

        assertTrue(numberResult is Double)
        assertEquals(32.035599, numberResult as Double, 0.000001)

        assertTrue(intResult is Int)
        assertEquals(42, intResult)
    }

    @Test
    fun smartConvertGuessesTypeOnlyWhenTypeNameIsNullOrEmpty() {
        // 未指定类型时，自动猜测
        val guessFloatNull = MCPToolParameter.smartConvert("32.035599", null)
        val guessFloatEmpty = MCPToolParameter.smartConvert("32.035599", "")
        val guessBool = MCPToolParameter.smartConvert("true", null)

        assertTrue(guessFloatNull is Double)
        assertTrue(guessFloatEmpty is Double)
        assertTrue(guessBool is Boolean)

        // 显式声明其他类型时不猜测
        val customTypeResult = MCPToolParameter.smartConvert("32.035599", "custom_id")
        assertTrue(customTypeResult is String)
        assertEquals("32.035599", customTypeResult)
    }

    @Test
    fun convertParameterValueHandlesStringExplicitly() {
        val param = MCPToolParameter(
            name = "from_lat",
            type = "string",
            description = "纬度"
        )
        val result = param.convertParameterValue("32.035599")
        assertTrue(result is String)
        assertEquals("32.035599", result)
    }
}
