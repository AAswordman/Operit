package com.ai.assistance.operit.ui.common.a2ui

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Collection operations are field-based, not executable lambdas. */
internal object CardDataFunctions {
    private fun bounded(value: Any?, depth: Int = 0): Boolean = depth <= 12 && when (value) {
        null, is Boolean -> true
        is String -> value.length <= 8192
        is Number -> value.toDouble().isFinite() && kotlin.math.abs(value.toDouble()) <= 1e12
        is List<*> -> value.size <= 128 && value.all { bounded(it, depth + 1) }
        is Map<*, *> -> value.size <= 128 && value.all { (k, v) -> k is String && k !in setOf("__proto__", "constructor", "prototype") && bounded(v, depth + 1) }
        else -> false
    }
    fun safeValue(value: Any?): Any? = value?.takeIf {
        bounded(it) && runCatching { A2uiProtocol.toJson(it).toString().length <= 32768 }.getOrDefault(false)
    }
    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 1e12 }
    private fun date(value: Any?): LocalDate? = (value as? String)?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it.year in 1900..2100 }
    private fun field(item: Any?, path: String?): Any? {
        if (path == null) return item
        val parts = path.split('/')
        if (parts.size > 12 || parts.any { !it.matches(Regex("[\\p{L}_][\\p{L}\\p{N}_]*")) || it in setOf("__proto__", "constructor", "prototype") }) return null
        var value = item
        for (p in parts) value = (value as? Map<*, *>)?.get(p) ?: return null
        return value
    }
    fun evaluate(name: String, args: Map<String, Any>): Any? = runCatching {
        if (args.values.any { !bounded(it) }) return null
        val result: Any? = when (name) {
            "arraySort", "arrayFind", "arrayUpdateByKey" -> {
                val items = (args["items"] as? List<*>)?.takeIf { safeValue(it) != null } ?: return null
                fun scalar(value: Any?) = value is String || value is Boolean || value is Number
                fun equal(a: Any?, b: Any?) = if (a is Number && b is Number) number(a) == number(b) else a == b
                when (name) {
                    "arraySort" -> {
                        val path = if ("field" in args) args["field"] as? String ?: return null else null
                        val direction = args["direction"] ?: "asc"
                        if (direction !in setOf("asc", "desc")) return null
                        val pairs = items.map { it to (field(it, path) ?: return null) }
                        val sample = pairs.firstOrNull()?.second
                        if (pairs.any { (_, v) -> !scalar(v) ||
                            (v is Number) != (sample is Number) ||
                            (v is String) != (sample is String) }) return null
                        val sorted = pairs.sortedWith { a, b ->
                            val comparison = when (val v = a.second) {
                                is Number -> v.toDouble().compareTo((b.second as Number).toDouble())
                                is String -> v.compareTo(b.second as String)
                                is Boolean -> v.compareTo(b.second as Boolean)
                                else -> 0
                            }
                            if (direction == "desc") -comparison else comparison
                        }
                        sorted.map { it.first }
                    }
                    "arrayFind" -> {
                        val path = args["field"] as? String ?: return null
                        val target = args["value"]
                        if (!scalar(target)) return null
                        // Validate every row even if an early match exists.
                        val values = items.map { field(it, path)?.takeIf(::scalar) ?: return null }
                        val index = values.indexOfFirst { equal(it, target) }
                        if (index < 0) args["fallback"] else items[index]
                    }
                    else -> {
                        val key = args["key"] as? String ?: return null
                        if (!key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) ||
                            key in setOf("__proto__", "constructor", "prototype")) return null
                        val patch = args["patch"] as? Map<*, *> ?: return null
                        if (key in patch || patch.keys.any { it !is String }) return null
                        val target = args["value"]
                        if (!scalar(target)) return null
                        val rows = items.map { it as? Map<*, *> ?: return null }
                        val keys = rows.map { field(it, key)?.takeIf(::scalar) ?: return null }
                        if (keys.indices.any { i -> keys.take(i).any { equal(it, keys[i]) } }) return null
                        rows.mapIndexed { i, row -> if (equal(keys[i], target)) row + patch else row }
                    }
                }
            }
            "arrayLength", "arrayMap", "arrayFilter", "arraySum", "arrayAverage", "arrayJoin" -> {
                val items = (args["items"] as? List<*>)?.takeIf { safeValue(it) != null } ?: return null
                val path = if ("field" in args) args["field"] as? String ?: return null else null
                when (name) {
                    "arrayLength" -> items.size
                    "arrayMap" -> {
                        if (path == null) return null
                        items.map { field(it, path) ?: return null }
                    }
                    "arrayFilter" -> {
                        if (path == null) return null
                        val operator = args["operator"] as? String ?: return null
                        if (operator !in setOf("eq", "ne", "gt", "gte", "lt", "lte", "contains")) return null
                        val target = args["value"]
                        items.filter { item ->
                            val actual = field(item, path) ?: return null
                            when (operator) {
                                "eq", "ne" -> {
                                    if (actual !is String && actual !is Boolean && actual !is Number) return null
                                    if (target !is String && target !is Boolean && target !is Number) return null
                                    val equal = if (actual is Number && target is Number) number(actual) == number(target) else actual == target
                                    if (operator == "eq") equal else !equal
                                }
                                "contains" -> (actual as? String ?: return null).contains(target as? String ?: return null)
                                else -> {
                                    val a = number(actual) ?: return null
                                    val b = number(target) ?: return null
                                    when (operator) { "gt" -> a > b; "gte" -> a >= b; "lt" -> a < b; else -> a <= b }
                                }
                            }
                        }
                    }
                    "arraySum", "arrayAverage" -> {
                        if (name == "arrayAverage" && items.isEmpty()) return null
                        val values = items.map { number(field(it, path)) ?: return null }
                        val sum = values.sum()
                        if (name == "arraySum") sum else sum / values.size
                    }
                    else -> {
                        val separator = (args["separator"] as? String)?.takeIf { it.length <= 256 } ?: return null
                        if (items.any { it !is String && it !is Number && it !is Boolean }) return null
                        if (items.sumOf { it.toString().length } + separator.length * (items.size - 1).coerceAtLeast(0) > 8192) return null
                        items.joinToString(separator)
                    }
                }
            }
            "stringTrim" -> (args["text"] as? String)?.trim()
            "stringContains" -> {
                if ("ignoreCase" in args && args["ignoreCase"] !is Boolean) return null
                (args["text"] as? String ?: return null).contains(args["query"] as? String ?: return null, args["ignoreCase"] == true)
            }
            "stringReplace" -> {
                val text = args["text"] as? String ?: return null
                val search = (args["search"] as? String)?.takeIf(String::isNotEmpty) ?: return null
                val replacement = args["replacement"] as? String ?: return null
                // Bound before allocating: a small repeated needle must not produce a huge string.
                var from = 0
                var count = 0
                while (true) {
                    val found = text.indexOf(search, from)
                    if (found < 0) break
                    count++; from = found + search.length
                }
                if (text.length.toLong() + count.toLong() * (replacement.length - search.length) > 8192) return null
                text.replace(search, replacement)
            }
            "dateAddDays" -> {
                val start = date(args["date"]) ?: return null
                val days = number(args["days"])?.takeIf { it % 1.0 == 0.0 && kotlin.math.abs(it) <= 73413 } ?: return null
                start.plusDays(days.toLong()).takeIf { it.year in 1900..2100 }?.toString()
            }
            "dateDiffDays" -> ChronoUnit.DAYS.between(date(args["start"]) ?: return null, date(args["end"]) ?: return null)
            "formatDate" -> {
                val d = date(args["date"]) ?: return null
                when (args["format"]) {
                    "iso" -> d.toString()
                    "yearMonth" -> d.toString().take(7)
                    "monthDay" -> d.toString().substring(5)
                    else -> null
                }
            }
            else -> null
        }
        safeValue(result)
    }.getOrNull()
}
