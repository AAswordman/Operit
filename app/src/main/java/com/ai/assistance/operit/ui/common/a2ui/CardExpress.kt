package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*

/**
 * Bounded Kotlin implementation of the A2UI Express named-argument subset.
 * No evaluation, scripts, imports, lifecycle commands or remote catalogs.
 * The AndroidX v0.9 runtime consumes the compiled document, never this syntax.
 */
internal object CardExpress {
    const val MAX_SOURCE = 65_536
    private val forbidden = setOf("__proto__", "constructor", "prototype", A2uiProtocol.MARKER)
    open class Failure(val line: Int, val column: Int, val reason: String) : IllegalArgumentException(reason)
    internal class SyntaxFailure(line: Int, column: Int, reason: String) : Failure(line, column, reason)
    private data class Token(val text: String, val line: Int, val column: Int, val quoted: Boolean = false)
    private data class Node(
        val token: Token, val kind: String, val fields: Map<String, Node> = emptyMap(),
        val items: List<Node> = emptyList(),
    )

    fun report(source: String): JsonObject = try {
        val result = prepare(source)
        buildJsonObject {
            put("valid", true); put("document", result.document)
            if (result.repair != null) {
                put("repaired", true); put("repair", result.repair); put("preparedSource", result.source)
            }
        }
    } catch (e: Failure) {
        buildJsonObject {
            put("valid", false); put("code", "invalid_express")
            put("line", e.line); put("column", e.column); put("message", "Line ${e.line}, column ${e.column}: ${e.reason}")
        }
    } catch (_: Exception) {
        buildJsonObject { put("valid", false); put("code", "invalid_card"); put("message", "Card exceeds protocol limits or contains invalid component data.") }
    }

    fun compile(source: String): String {
        if (source.length > MAX_SOURCE) throw Failure(1, 1, "Card source exceeds 65536 characters.")
        return Compiler(Parser(source).parse()).compile()
    }

    internal data class Prepared(val source: String, val document: String, val repair: String? = null)

    internal fun prepare(source: String): Prepared = try {
        Prepared(source, compile(source))
    } catch (e: SyntaxFailure) {
        CardSyntaxRepair.attempt(source, e) { candidate -> Prepared(candidate, compile(candidate)) } ?: throw e
    }

    internal fun prepareRevision(source: String, changes: String, remove: List<String>): Prepared {
        fun validate(candidate: String): Prepared {
            val (revised, document) = revise(source, candidate, remove)
            return Prepared(revised, document)
        }
        return try { validate(changes) } catch (e: SyntaxFailure) {
            CardSyntaxRepair.attempt(changes, e, ::validate) ?: throw e
        }
    }

    /** Edit named declarations, then validate the complete result before publishing a new revision. */
    fun revise(source: String, changes: String, remove: List<String>): Pair<String, String> {
        if (source.length > MAX_SOURCE || changes.length > MAX_SOURCE || remove.size > 128)
            throw Failure(1, 1, "Revision exceeds source limits.")
        val base = Parser(source).parse().toMutableMap()
        val patch = Parser(changes).parse()
        if (patch.isEmpty() && remove.isEmpty()) throw Failure(1, 1, "Revision is empty.")
        remove.forEach {
            if (it !in base || it in patch) throw Failure(1, 1, "Remove only existing declarations, without also replacing them.")
            base.remove(it)
        }
        base.putAll(patch)
        fun render(n: Node): String = when (n.kind) {
            "string" -> JsonPrimitive(n.token.text).toString()
            "array" -> n.items.joinToString(", ", "[", "]", transform = ::render)
            "object" -> n.fields.entries.joinToString(", ", "{", "}") { "${JsonPrimitive(it.key)}: ${render(it.value)}" }
            "call" -> n.fields.entries.joinToString(", ", "${n.token.text}(", ")") { "${it.key}=${render(it.value)}" }
            else -> n.token.text
        }
        val revised = base.entries.joinToString("\n") { "${it.key} = ${render(it.value)}" }
        return revised to compile(revised)
    }

    private fun fail(t: Token, reason: String): Nothing = throw Failure(t.line, t.column, reason)
    private fun safeName(t: Token) {
        if (t.text in forbidden || t.text.length > 80) fail(t, "Reserved or too long a name.")
    }

    private class Parser(private val source: String) {
        private fun fail(t: Token, reason: String): Nothing = throw SyntaxFailure(t.line, t.column, reason)
        private var offset = 0
        private var line = 1
        private var column = 1
        private var nodes = 0
        private var next = read()
        private fun advance(): Char {
            val c = source[offset++]
            if (c == '\n') { line++; column = 1 } else column++
            return c
        }
        private fun read(): Token {
            while (offset < source.length && source[offset].isWhitespace()) advance()
            val row = line; val col = column
            if (offset == source.length) return Token("", row, col)
            val start = offset
            val first = advance()
            if (first == '"') {
                var escaped = false
                var closed = false
                while (offset < source.length) {
                    val c = advance()
                    if (!escaped && c == '"') { closed = true; break }
                    if (!escaped && c == '\n') fail(Token("", row, col), "Escape newlines inside strings.")
                    escaped = !escaped && c == '\\'
                }
                if (!closed) fail(Token("", row, col), "Unclosed string.")
                val raw = source.substring(start, offset)
                val text = try { Json.parseToJsonElement(raw).jsonPrimitive.content }
                catch (_: Exception) { fail(Token("", row, col), "Invalid string escape.") }
                if (text.length > 8192) fail(Token("", row, col), "String exceeds 8192 characters.")
                return Token(text, row, col, true)
            }
            if (first.isLetter() || first == '_' || first == '$' || first.isDigit() || first == '-') {
                while (offset < source.length) {
                    val c = source[offset]
                    val valid = when {
                        first == '$' -> c.isLetterOrDigit() || c in "_/"
                        first.isDigit() || first == '-' -> c.isDigit() || c == '.'
                        else -> c.isLetterOrDigit() || c == '_'
                    }
                    if (!valid) break
                    advance()
                }
            }
            return Token(source.substring(start, offset), row, col)
        }
        private fun take(): Token = next.also { next = read() }
        private fun isNext(text: String) = !next.quoted && next.text == text
        private fun expect(text: String) {
            if (!isNext(text)) fail(next, "Expected '$text'.")
            take()
        }
        fun parse(): Map<String, Node> {
            val statements = linkedMapOf<String, Node>()
            while (next.text.isNotEmpty() || next.quoted) {
                val name = take()
                if (name.quoted || !(name.text.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) || name.text.startsWith("$/")))
                    fail(name, "Expected a component name or absolute state path.")
                if (!name.text.startsWith("$/")) safeName(name)
                if (statements.size >= 256 || name.text in statements) fail(name, "Duplicate declaration or too many declarations.")
                expect("=")
                statements[name.text] = value(0)
                if (next.text.isNotEmpty() && next.line == lineOfEnd) fail(next, "Put each declaration on a new line.")
            }
            return statements
        }
        private var lineOfEnd = 1
        private fun value(depth: Int): Node {
            if (depth > 20 || ++nodes > 4096) fail(next, "Expression is too complex.")
            val t = take()
            if (t.quoted) { lineOfEnd = t.line; return Node(t, "string") }
            val node = when (t.text) {
                "[" -> {
                    val items = mutableListOf<Node>()
                    while (!isNext("]")) {
                        if (items.size >= 128) fail(next, "Array exceeds 128 items.")
                        items += value(depth + 1)
                        if (isNext("]")) break
                        expect(",")
                    }
                    lineOfEnd = take().line
                    return Node(t, "array", items = items)
                }
                "{" -> {
                    val fields = linkedMapOf<String, Node>()
                    while (!isNext("}")) {
                        val key = take()
                        safeName(key)
                        if (!key.quoted && !key.text.matches(Regex("[\\p{L}_][\\p{L}\\p{N}_]*")))
                            fail(key, "Invalid object key.")
                        if (fields.size >= 128 || key.text in fields) fail(key, "Duplicate key or too many fields.")
                        expect(":"); fields[key.text] = value(depth + 1)
                        if (isNext("}")) break
                        expect(",")
                    }
                    lineOfEnd = take().line
                    return Node(t, "object", fields)
                }
                "true", "false", "null" -> Node(t, "literal")
                "" -> fail(t, "Unexpected end of source.")
                else -> when {
                    t.text.startsWith("$/") -> Node(t, "path")
                    t.text.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) -> Node(t, "literal")
                    t.text.matches(Regex("[\\p{L}_][\\p{L}\\p{N}_]*")) -> {
                        safeName(t)
                        if (isNext("(")) {
                            take()
                            val args = linkedMapOf<String, Node>()
                            while (!isNext(")")) {
                                val key = take()
                                if (key.quoted || !key.text.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))
                                    fail(key, "Use named arguments: field=value.")
                                safeName(key)
                                if (args.size >= 32 || key.text in args) fail(key, "Duplicate argument or too many arguments.")
                                expect("="); args[key.text] = value(depth + 1)
                                if (isNext(")")) break
                                expect(",")
                            }
                            lineOfEnd = take().line
                            return Node(t, "call", args)
                        } else Node(t, "ref")
                    }
                    else -> fail(t, "Unsupported syntax. Use named arguments, literals and absolute state paths.")
                }
            }
            lineOfEnd = t.line
            return node
        }
    }

    private class Compiler(private val declarations: Map<String, Node>) {
        private val components = linkedMapOf<String, JsonObject>()
        private val resolving = mutableSetOf<String>()
        private val state = linkedMapOf<String, JsonElement>()
        private val bindings = mutableListOf<Node>()
        private var inline = 0
        private var changeContext = false
        private fun hasItem(n: Node): Boolean = n.kind == "call" && n.token.text in setOf("item", "item_state") ||
            n.fields.values.any(::hasItem) || n.items.any(::hasItem)
        private fun path(node: Node): String {
            val path = node.token.text.removePrefix("$")
            try { A2uiProtocol.validatePath(path) } catch (_: Exception) { fail(node.token, "Invalid state path.") }
            if (path == "/" || path.drop(1).split('/').any { it.isEmpty() || it in forbidden || it == CardRowState.ROOT })
                fail(node.token, "Use a nonempty, nonreserved object path.")
            return path
        }
        fun compile(): String {
            declarations.filterKeys { it.startsWith("$/") }.forEach { (name, value) ->
                val p = path(Node(value.token.copy(text = name), "path"))
                if (state.keys.any { it == p || it.startsWith("$p/") || p.startsWith("$it/") })
                    fail(value.token, "Initialize each state branch once; overlapping paths are not allowed.")
                state[p] = json(value, literal = true)
            }
            if (state.entries.sumOf { it.key.length + it.value.toString().length } > A2uiProtocol.MAX_STATE)
                throw Failure(1, 1, "Initial data exceeds 32K characters.")
            val root = declarations["root"] ?: throw Failure(1, 1, "Declare root = Card(...) or another root component.")
            component(root, "root", 0)
            declarations.filterKeys { !it.startsWith("$/") }.forEach { (id, node) ->
                if (id !in components) fail(node.token, "Unused component declaration; include it in root.")
            }
            bindings.forEach { node ->
                val p = path(node)
                val branch = state.keys.firstOrNull { p == it || p.startsWith("$it/") }
                    ?: fail(node.token, "State binding is not initialized.")
                var current: JsonElement? = state[branch]
                p.removePrefix(branch).split('/').filter(String::isNotEmpty).forEach {
                    current = (current as? JsonObject)?.get(it)
                }
                if (current == null) fail(node.token, "State binding is not initialized.")
            }
            fun event(name: String, body: JsonObject) = buildJsonObject {
                put("version", "v0.9"); put(name, body)
            }.toString()
            val source = buildList {
                add(event("createSurface", buildJsonObject { put("surfaceId", "card"); put("catalogId", A2uiProtocol.CATALOG) }))
                add(event("updateComponents", buildJsonObject { put("surfaceId", "card"); put("components", JsonArray(components.values.toList())) }))
                state.forEach { (path, value) ->
                    add(event("updateDataModel", buildJsonObject { put("surfaceId", "card"); put("path", path); put("value", value) }))
                }
            }.joinToString("\n")
            val report = A2uiProtocol.validationReport(source)
            if (report["valid"]?.jsonPrimitive?.boolean != true) fail(root.token, report["message"]!!.jsonPrimitive.content)
            return source
        }
        private fun component(node: Node, id: String, depth: Int): String {
            if (id in components) return id
            if (depth > 12 || !resolving.add(id)) fail(node.token, "Cyclic or deeply nested components.")
            if (components.size + resolving.size > 128) fail(node.token, "Too many components.")
            if (node.kind != "call") fail(node.token, "Expected a component constructor.")
            val fields = CardAuthoringCatalog.components[node.token.text]
                ?: fail(node.token, "Unknown component. Query a2ui_catalog.")
            val unknown = node.fields.keys.filter { key -> fields.none { it.name == key } }
            val missing = fields.filter { it.required && it.name !in node.fields }.map { it.name }
            if (unknown.isNotEmpty() || missing.isNotEmpty()) {
                val signature = fields.joinToString { "${it.name}${if (it.required) "" else "?"}:${it.type}" }
                fail(node.token, "Arguments for ${node.token.text}: $signature. Unknown: ${unknown.joinToString()}; missing: ${missing.joinToString()}.")
            }
            val result = buildJsonObject {
                put("id", id); put("component", node.token.text)
                fields.forEach { field ->
                    val value = node.fields[field.name] ?: return@forEach
                    val compiled = when (field.type) {
                        "child" -> JsonPrimitive(child(value, depth + 1))
                        "children" -> {
                            if (value.kind != "array" || value.items.size > 32) fail(value.token, "Use up to 32 child components.")
                            JsonArray(value.items.map { JsonPrimitive(child(it, depth + 1)) })
                        }
                        "action" -> {
                            if (value.kind != "call" || value.token.text != "Event") fail(value.token, "Use Event(name=..., context={...}).")
                            if (value.fields.keys.any { it !in setOf("name", "context") }) fail(value.token, "Unknown Event argument.")
                            val name = value.fields["name"]?.takeIf { it.kind == "string" }?.token?.text
                            if (name !in A2uiProtocol.actions) fail(value.token, "Unknown Event action.")
                            if (field.name == "onChange" && name != "local_action")
                                fail(value.token, "onChange only supports local_action.")
                            changeContext = field.name == "onChange"
                            val context = try { value.fields["context"]?.let { json(it) } ?: JsonObject(emptyMap()) }
                                finally { changeContext = false }
                            if (context !is JsonObject) fail(value.token, "Event context must be an object.")
                            buildJsonObject { put("event", buildJsonObject { put("name", name); put("context", context) }) }
                        }
                        else -> {
                            val v = json(value, literal = CardAuthoringCatalog.isStatic(node.token.text, field.name))
                            checkType(value, field.type, field.name)
                            val initial = if (hasItem(value)) JsonNull else initial(value)
                            val allowed = CardAuthoringCatalog.enums["${node.token.text}.${field.name}"]
                            if (allowed != null && !hasItem(value) && (initial as? JsonPrimitive)?.content !in allowed)
                                fail(value.token, "${field.name} must be one of ${allowed.joinToString()}.")
                            CardAuthoringCatalog.ranges["${node.token.text}.${field.name}"]?.let { range ->
                                if ((initial as? JsonPrimitive)?.intOrNull !in range)
                                    fail(value.token, "${field.name} must be an integer from ${range.first} to ${range.last}.")
                            }
                            if (field.name in setOf("labels", "columns", "options") &&
                                initial is JsonArray && initial.any { it !is JsonPrimitive || !it.isString })
                                fail(value.token, "${field.name} must contain strings.")
                            v
                        }
                    }
                    put(field.name, compiled)
                }
            }
            if (node.token.text == "Chart" && !hasItem(node)) {
                val series = node.fields.getValue("series")
                val labels = node.fields["labels"]?.let { initial(it).jsonArray.map { v -> v.jsonPrimitive.content } }.orEmpty()
                try { cardChartSeries(A2uiProtocol.toValue(initial(series)), labels, initial(node.fields.getValue("type")).jsonPrimitive.content, strict = true) }
                catch (error: Exception) { fail(series.token, "Invalid chart series: ${error.message?.take(160)}. Use {label:\"Series\",values:[1,2]}; scatter uses points:[{x:1,y:2}].") }
            }
            if (node.token.text == "Map" && !hasItem(node)) {
                val markers = node.fields.getValue("markers")
                try { cardMapMarkers(A2uiProtocol.toValue(initial(markers))) }
                catch (_: Exception) { fail(markers.token, "Invalid map markers. Check numeric latitude/longitude and the 32-marker limit.") }
            }
            if (node.token.text == "Flow" && !hasItem(node)) {
                val steps = node.fields.getValue("steps")
                try { cardFlowSteps(A2uiProtocol.toValue(initial(steps))) }
                catch (e: Exception) { fail(steps.token, "Invalid Flow steps: ${e.message ?: "use 1..8 {title,caption?,icon?,tone?} objects"}. Use a2ui_icons to find supported icon names.") }
            }
            if (node.token.text == "Table" && !hasItem(node)) {
                val rows = node.fields.getValue("rows")
                val data = initial(rows) as? JsonArray
                val count = initial(node.fields.getValue("columns")).jsonArray.size
                if (data == null || data.size > 32 || data.any { row ->
                    row !is JsonArray || row.size != count || row.any { it !is JsonPrimitive }
                }) fail(rows.token, "Table rows must contain up to 32 arrays of scalar cells, matching the columns.")
            }
            components[id] = result
            resolving.remove(id)
            return id
        }
        private fun child(node: Node, depth: Int): String {
            if (node.kind == "ref") {
                val name = node.token.text
                val target = declarations[name] ?: fail(node.token, "Undefined child component.")
                return component(target, name, depth)
            }
            if (node.kind != "call") fail(node.token, "Expected an inline component or component name.")
            var id: String
            do { id = "inline_${++inline}" } while (id in declarations || id in components || id in resolving)
            return component(node, id, depth)
        }
        private fun checkType(node: Node, type: String, field: String) {
            // Item types are checked against every row after template expansion.
            if (hasItem(node)) return
            // Both branches must fit even when only one is selected by the initial condition.
            if (node.kind == "call" && node.token.text == "if") {
                node.fields["then"]?.let { checkType(it, type, field) }
                node.fields["else"]?.let { checkType(it, type, field) }
            }
            val value = initial(node)
            val matches = when (type) {
                "string" -> value is JsonPrimitive && value.isString
                "number" -> value is JsonPrimitive && !value.isString && value.doubleOrNull?.isFinite() == true
                "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
                "array" -> value is JsonArray
                else -> false
            }
            if (!matches) fail(node.token, "$field requires an initialized $type value.")
        }
        private fun initial(node: Node): JsonElement = when (node.kind) {
            "path" -> {
                val p = path(node)
                val branch = state.keys.firstOrNull { p == it || p.startsWith("$it/") }
                    ?: fail(node.token, "State binding is not initialized.")
                var value = state[branch]
                p.removePrefix(branch).split('/').filter(String::isNotEmpty).forEach { value = (value as? JsonObject)?.get(it) }
                value ?: fail(node.token, "State binding is not initialized.")
            }
            "call" -> {
                val args = node.fields.mapValues { A2uiProtocol.toValue(initial(it.value)) }
                if (args.values.any { it == null }) JsonNull
                else {
                    @Suppress("UNCHECKED_CAST")
                    A2uiProtocol.toJson(CardFunctions.evaluate(node.token.text, args as Map<String, Any>))
                }
            }
            "array" -> JsonArray(node.items.map(::initial))
            "object" -> JsonObject(node.fields.mapValues { initial(it.value) })
            else -> json(node, literal = true)
        }
        private fun json(node: Node, literal: Boolean = false): JsonElement = when (node.kind) {
            "string" -> JsonPrimitive(node.token.text)
            "literal" -> Json.parseToJsonElement(node.token.text).also {
                if (it is JsonPrimitive && it != JsonNull && it.booleanOrNull == null &&
                    it.doubleOrNull?.isFinite() != true) fail(node.token, "Invalid number.")
            }
            "array" -> JsonArray(node.items.map { json(it, literal) })
            "object" -> JsonObject(node.fields.mapValues { json(it.value, literal) })
            "path" -> {
                if (literal) fail(node.token, "Initial state must contain only literal data.")
                bindings += node
                buildJsonObject { put("path", path(node)) }
            }
            "call" -> {
                if (literal) fail(node.token, "Initial state must contain only literal data.")
                if (node.token.text == "item") {
                    val field = node.fields["field"]
                    if (node.fields.keys != setOf("field") || field?.kind != "string")
                        fail(node.token, "Use item(field=\"fieldName\") inside a Repeat template.")
                    buildJsonObject { put("item", field.token.text) }
                } else if (node.token.text == "item_state") {
                    val field = node.fields["field"]
                    val default = node.fields["default"]
                    if (node.fields.keys != setOf("field", "default") || field?.kind != "string" || default == null)
                        fail(node.token, "Use item_state(field=\"done\", default=false) in a Repeat template.")
                    try { CardRowState.placeholder(field.token.text, json(default, literal = true)) }
                    catch (_: Exception) { fail(node.token, "item_state requires a named field and a scalar default.") }
                } else if (node.token.text == "event_value") {
                    val field = node.fields["field"]
                    if (!changeContext || node.fields.keys != setOf("field") || field?.kind != "string" ||
                        field.token.text !in setOf("value", "previous", "index"))
                        fail(node.token, "event_value(field=\"value\"|\"previous\"|\"index\") is only available in onChange.")
                    buildJsonObject { put("event_value", field.token.text) }
                } else {
                val spec = CardFunctions.specs[node.token.text] ?: fail(node.token, "Unknown local function.")
                if (!node.fields.keys.containsAll(spec.required) || node.fields.keys.any { it !in spec.required + spec.optional })
                    fail(node.token, "Missing or unknown function argument.")
                buildJsonObject { put("call", node.token.text); put("args", JsonObject(node.fields.mapValues { json(it.value) })) }
                }
            }
            else -> fail(node.token, "Bare names are only allowed in child slots; quote text values.")
        }
    }
}
