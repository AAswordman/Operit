package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelParameterTest {

    @Test fun `create int parameter`() {
        val param = ModelParameter(
            id = "temp", name = "Temperature", apiName = "temperature",
            defaultValue = 0.7, currentValue = 0.8, isEnabled = true,
            valueType = ParameterValueType.FLOAT,
            minValue = 0.0, maxValue = 2.0,
            category = ParameterCategory.CREATIVITY,
        )
        assertEquals("temp", param.id)
        assertEquals("Temperature", param.name)
        assertEquals("temperature", param.apiName)
        assertEquals(0.7, param.defaultValue, 0.001)
        assertEquals(0.8, param.currentValue, 0.001)
        assertTrue(param.isEnabled)
        assertEquals(ParameterValueType.FLOAT, param.valueType)
        assertEquals(0.0, (param.minValue as Double), 0.001)
        assertEquals(2.0, (param.maxValue as Double), 0.001)
        assertEquals(ParameterCategory.CREATIVITY, param.category)
    }

    @Test fun `create boolean parameter`() {
        val param = ModelParameter(
            id = "stream", name = "Stream", apiName = "stream",
            defaultValue = true, currentValue = false, isEnabled = true,
            valueType = ParameterValueType.BOOLEAN,
        )
        assertEquals(true, param.defaultValue)
        assertEquals(false, param.currentValue)
        assertEquals(ParameterValueType.BOOLEAN, param.valueType)
    }

    @Test fun `create string parameter`() {
        val param = ModelParameter(
            id = "model", name = "Model", apiName = "model",
            defaultValue = "gpt-4", currentValue = "gpt-3.5", isEnabled = true,
            valueType = ParameterValueType.STRING,
        )
        assertEquals("gpt-4", param.defaultValue)
        assertEquals("gpt-3.5", param.currentValue)
        assertEquals(ParameterValueType.STRING, param.valueType)
    }

    @Test fun `description defaults to empty`() {
        val param = ModelParameter(
            id = "t", name = "T", apiName = "t",
            defaultValue = 1, currentValue = 1, isEnabled = true,
            valueType = ParameterValueType.INT,
        )
        assertEquals("", param.description)
    }

    @Test fun `min and max can be null`() {
        val param = ModelParameter(
            id = "t", name = "T", apiName = "t",
            defaultValue = 1, currentValue = 1, isEnabled = true,
            valueType = ParameterValueType.INT,
        )
        assertNull(param.minValue)
        assertNull(param.maxValue)
    }

    @Test fun `category defaults to OTHER`() {
        val param = ModelParameter(
            id = "t", name = "T", apiName = "t",
            defaultValue = 1, currentValue = 1, isEnabled = true,
            valueType = ParameterValueType.INT,
        )
        assertEquals(ParameterCategory.OTHER, param.category)
    }

    @Test fun `isCustom defaults to false`() {
        val param = ModelParameter(
            id = "t", name = "T", apiName = "t",
            defaultValue = 1, currentValue = 1, isEnabled = true,
            valueType = ParameterValueType.INT,
        )
        assertFalse(param.isCustom)
    }

    @Test fun `custom parameter`() {
        val param = ModelParameter(
            id = "custom", name = "Custom", apiName = "custom",
            defaultValue = 5, currentValue = 10, isEnabled = true,
            valueType = ParameterValueType.INT, isCustom = true,
        )
        assertTrue(param.isCustom)
    }

    @Test fun `parameter can be disabled`() {
        val param = ModelParameter(
            id = "t", name = "T", apiName = "t",
            defaultValue = 1, currentValue = 1, isEnabled = false,
            valueType = ParameterValueType.INT,
        )
        assertFalse(param.isEnabled)
    }

    @Test fun `parameter value type enum has all values`() {
        assertEquals(5, ParameterValueType.values().size)
        assertTrue(ParameterValueType.values().contains(ParameterValueType.INT))
        assertTrue(ParameterValueType.values().contains(ParameterValueType.FLOAT))
        assertTrue(ParameterValueType.values().contains(ParameterValueType.STRING))
        assertTrue(ParameterValueType.values().contains(ParameterValueType.BOOLEAN))
        assertTrue(ParameterValueType.values().contains(ParameterValueType.OBJECT))
    }

    @Test fun `parameter category enum has all values`() {
        assertEquals(4, ParameterCategory.values().size)
        assertTrue(ParameterCategory.values().contains(ParameterCategory.GENERATION))
        assertTrue(ParameterCategory.values().contains(ParameterCategory.CREATIVITY))
        assertTrue(ParameterCategory.values().contains(ParameterCategory.REPETITION))
        assertTrue(ParameterCategory.values().contains(ParameterCategory.OTHER))
    }

    @Test fun `custom parameter data with all fields`() {
        val data = CustomParameterData(
            id = "custom1", name = "Custom Param", apiName = "custom_param",
            description = "A custom parameter", defaultValue = "10",
            currentValue = "20", isEnabled = true, valueType = "INT",
            minValue = "0", maxValue = "100", category = "GENERATION",
        )
        assertEquals("custom1", data.id)
        assertEquals("Custom Param", data.name)
        assertEquals("custom_param", data.apiName)
        assertEquals("A custom parameter", data.description)
        assertEquals("10", data.defaultValue)
        assertEquals("20", data.currentValue)
        assertTrue(data.isEnabled)
        assertEquals("INT", data.valueType)
        assertEquals("0", data.minValue)
        assertEquals("100", data.maxValue)
        assertEquals("GENERATION", data.category)
    }

    @Test fun `custom parameter data with defaults`() {
        val data = CustomParameterData(
            id = "c", name = "C", apiName = "c",
            defaultValue = "1", currentValue = "1", isEnabled = true, valueType = "FLOAT",
        )
        assertEquals("", data.description)
        assertNull(data.minValue)
        assertNull(data.maxValue)
        assertEquals("OTHER", data.category)
    }

    @Test fun `custom parameter data copy`() {
        val data = CustomParameterData(
            id = "c", name = "C", apiName = "c",
            defaultValue = "1", currentValue = "2", isEnabled = true, valueType = "INT",
        )
        val copy = data.copy(currentValue = "3", isEnabled = false)
        assertEquals("3", copy.currentValue)
        assertFalse(copy.isEnabled)
        assertEquals("1", copy.defaultValue)
    }
}
