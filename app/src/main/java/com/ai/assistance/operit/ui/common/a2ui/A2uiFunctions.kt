package com.ai.assistance.operit.ui.common.a2ui

import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.catalog.A2uiFunctionDefinition
import androidx.a2ui.model.catalog.A2uiFunctionReturnType
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import kotlinx.serialization.json.*

/** Pure, bounded functions. No Context, I/O, evaluation recursion or data-model writes. */
internal object CardFunctions {
    data class Spec(val required: Set<String>, val optional: Set<String>, val type: A2uiFunctionReturnType)
    val specs = buildMap {
        listOf("add", "subtract", "multiply", "divide", "min", "max").forEach {
            put(it, Spec(setOf("a", "b"), emptySet(), A2uiFunctionReturnType.NUMBER))
        }
        listOf("equals", "greaterThan", "lessThan", "and", "or").forEach {
            put(it, Spec(setOf("a", "b"), emptySet(), A2uiFunctionReturnType.BOOLEAN))
        }
        put("not", Spec(setOf("value"), emptySet(), A2uiFunctionReturnType.BOOLEAN))
        put("if", Spec(setOf("condition", "then", "else"), emptySet(), A2uiFunctionReturnType.ANY))
        put("formatNumber", Spec(setOf("value"), setOf("decimals", "grouping"), A2uiFunctionReturnType.STRING))
        put("concat", Spec(setOf("values"), emptySet(), A2uiFunctionReturnType.STRING))
        put("calculatorStep", Spec(setOf("state", "key"), emptySet(), A2uiFunctionReturnType.OBJECT))
        put("arrayLength", Spec(setOf("items"), emptySet(), A2uiFunctionReturnType.NUMBER))
        put("arrayMap", Spec(setOf("items", "field"), emptySet(), A2uiFunctionReturnType.ANY))
        put("arrayFilter", Spec(setOf("items", "field", "operator", "value"), emptySet(), A2uiFunctionReturnType.ANY))
        listOf("arraySum", "arrayAverage").forEach { put(it, Spec(setOf("items"), setOf("field"), A2uiFunctionReturnType.NUMBER)) }
        put("arrayJoin", Spec(setOf("items", "separator"), emptySet(), A2uiFunctionReturnType.STRING))
        put("arraySort", Spec(setOf("items"), setOf("field", "direction"), A2uiFunctionReturnType.ANY))
        put("arrayFind", Spec(setOf("items", "field", "value", "fallback"), emptySet(), A2uiFunctionReturnType.ANY))
        put("arrayUpdateByKey", Spec(setOf("items", "key", "value", "patch"), emptySet(), A2uiFunctionReturnType.ANY))
        put("stringContains", Spec(setOf("text", "query"), setOf("ignoreCase"), A2uiFunctionReturnType.BOOLEAN))
        put("stringReplace", Spec(setOf("text", "search", "replacement"), emptySet(), A2uiFunctionReturnType.STRING))
        put("stringTrim", Spec(setOf("text"), emptySet(), A2uiFunctionReturnType.STRING))
        put("dateAddDays", Spec(setOf("date", "days"), emptySet(), A2uiFunctionReturnType.STRING))
        put("dateDiffDays", Spec(setOf("start", "end"), emptySet(), A2uiFunctionReturnType.NUMBER))
        put("formatDate", Spec(setOf("date", "format"), emptySet(), A2uiFunctionReturnType.STRING))
    }
    val functions: List<A2uiFunction> = specs.map { (functionName, spec) ->
        object : A2uiFunction {
            override val definition = object : A2uiFunctionDefinition {
                override val name = functionName
                override val description = "Pure local $functionName; invalid input is unresolved, never executes tools."
                override val returnType = spec.type
                override val argumentSchema = A2uiObjectSchema(
                    properties = (spec.required + spec.optional).associateWith { A2uiAnySchema.INSTANCE },
                    required = spec.required, isAdditionalPropertiesAllowed = false,
                )
            }
            override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? = evaluate(functionName, args)
        }
    }

    fun validate(call: JsonObject) {
        require(call.keys.all { it in setOf("call", "args", "returnType") }) { "Unsupported function property" }
        val name = call["call"]?.jsonPrimitive?.content
        val spec = requireNotNull(specs[name]) { "Unsupported local function" }
        val args = call["args"] as? JsonObject ?: error("Function args must be an object")
        require(args.keys.containsAll(spec.required) && args.keys.all { it in spec.required + spec.optional }) { "Invalid function arguments" }
        call["returnType"]?.let { require(it.jsonPrimitive.content == spec.type.value) { "Incorrect return type" } }
        if (name == "formatNumber") (args["decimals"] as? JsonPrimitive)?.let {
            require(it.intOrNull in 0..6) { "Decimal places must be 0 to 6" }
        }
        if (name == "concat") (args["values"] as? JsonArray)?.let { require(it.size <= 16) { "Too many strings" } }
        if (name == "calculatorStep") (args["key"] as? JsonPrimitive)?.let {
            require(it.content in CardCalculator.keys) { "Unknown calculator key" }
        }
    }

    fun evaluate(name: String, args: Map<String, Any>): Any? {
        fun number(key: String): Double? = when (val v = args[key]) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 1e12 }
        fun scalar(v: Any?) = v is String || v is Number || v is Boolean
        val result: Any? = when (name) {
            "calculatorStep" -> CardCalculator.step(args["state"], args["key"] as? String ?: return null)
            "add", "subtract", "multiply", "divide", "min", "max", "greaterThan", "lessThan" -> {
                val a = number("a") ?: return null
                val b = number("b") ?: return null
                when (name) {
                    "add" -> a + b
                    "subtract" -> a - b
                    "multiply" -> a * b
                    "divide" -> if (b == 0.0) return null else a / b
                    "min" -> minOf(a, b)
                    "max" -> maxOf(a, b)
                    "greaterThan" -> a > b
                    else -> a < b
                }
            }
            "equals" -> if (scalar(args["a"]) && scalar(args["b"])) {
                if (args["a"] is Number && args["b"] is Number) {
                    val a = number("a") ?: return null
                    val b = number("b") ?: return null
                    a == b
                } else args["a"] == args["b"]
            } else return null
            "and", "or" -> {
                val a = args["a"] as? Boolean ?: return null
                val b = args["b"] as? Boolean ?: return null
                if (name == "and") a && b else a || b
            }
            "not" -> !(args["value"] as? Boolean ?: return null)
            "if" -> CardDataFunctions.safeValue(args[if (args["condition"] as? Boolean ?: return null) "then" else "else"])
            "formatNumber" -> {
                val value = number("value") ?: return null
                val decimals = if ("decimals" in args) number("decimals")?.takeIf { it % 1 == 0.0 && it in 0.0..6.0 }?.toInt() ?: return null else 2
                java.text.NumberFormat.getNumberInstance().apply {
                    minimumFractionDigits = decimals; maximumFractionDigits = decimals
                    isGroupingUsed = args["grouping"] as? Boolean ?: true
                }.format(value)
            }
            "concat" -> {
                val values = args["values"] as? List<*> ?: return null
                if (values.size > 16 || values.any { !scalar(it) }) return null
                if (values.sumOf { it.toString().length } > 8192) return null
                values.joinToString("")
            }
            else -> CardDataFunctions.evaluate(name, args)
        }
        return if (result is Number) result.takeIf { it.toDouble().isFinite() && kotlin.math.abs(it.toDouble()) <= 1e12 } else result
    }
}
