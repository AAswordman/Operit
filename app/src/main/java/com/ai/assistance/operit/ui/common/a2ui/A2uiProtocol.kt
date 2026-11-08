package com.ai.assistance.operit.ui.common.a2ui

import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.json.*

/**
 * Resource limits around the official A2UI parser, not a replacement protocol.
 * Repeat templates are lowered to bounded static graphs before the official renderer.
 */
internal object A2uiProtocol {
    const val CATALOG = "https://operit.ai/a2ui/catalog/v1"
    const val MAX_SOURCE = 131_072
    const val MAX_STATE = 32_768
    const val MARKER = "_operit_ready"
    val actions = setOf("local_action", "ai_submit", "tool_action", "open_url", "copy")
    val components = setOf(
        "Text", "Icon", "Html", "Row", "Column", "Card", "Divider", "Button", "List",
        "TextField", "CheckBox", "ChoicePicker", "Slider",
        "Heading", "Header", "InfoRow", "Display", "Badge", "Grid", "Progress", "Table", "Code", "Switch",
        "Panel", "Scroll", "Popover", "Vector", "Repeat", "Frame", "Pressable", "Tooltip", "Stepper", "Formula", "Flow",
        "RadioGroup", "Select", "DatePicker", "TextArea", "Form", "Expandable",
        "Segmented", "Tabs", "Carousel", "Conditional", "Chart", "RichText",
        "Image", "Video", "Audio", "Link", "File", "Product", "Place", "Map", "Draft",
    )
    private val idPattern = Regex("[A-Za-z0-9_-]{1,80}")
    private val json = Json { isLenient = false }

    data class Batch(
        val lines: List<String>,
        val surfaceIds: Set<String>,
        val complete: Boolean,
        val componentsBySurface: Map<String, Map<String, JsonObject>>,
    )

    fun fingerprint(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Same bounded validator used by the renderer. No rendering, persistence or actions. */
    fun validationReport(source: String): JsonObject = try {
        val batch = parse(source, true)
        buildJsonObject {
            put("valid", true)
            put("surfaces", batch.surfaceIds.size)
            put("message", "Protocol checks passed. This does not verify visual layout or arithmetic intent.")
        }
    } catch (e: Exception) {
        val line = diagnosticLine(e)
        val code = diagnosticCode(e)
        val column = if (code == "invalid_json") generateSequence<Throwable>(e) { it.cause }
            .take(8).filterIsInstance<kotlinx.serialization.SerializationException>()
            .mapNotNull { Regex("\\boffset (\\d+)").find(it.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() }
            .firstOrNull()?.plus(1) else null
        val reason = if (code == "invalid_json")
            "Check closing braces, brackets, commas and quotes. Nested initial data is supported."
        else safeDiagnosticReason(e)
        buildJsonObject {
            put("valid", false)
            line?.let { put("line", it) }
            column?.let { put("column", it) }
            put("code", code)
            put("message", buildString {
                append(code)
                line?.let { append(" at line $it") }
                column?.let { append(", column $it") }
                append(": $reason")
            })
        }
    }

    // Only relay our fixed diagnostics, never parser excerpts or arbitrary model text.
    private fun safeDiagnosticReason(error: Exception): String {
        generateSequence<Throwable>(error) { it.cause }.take(8)
            .filterIsInstance<CardRendererSchema.Failure>().firstOrNull()?.let {
                return "Renderer schema mismatch: ${it.component}.${it.field}. Query a2ui_catalog."
            }
        val known = setOf(
            "Card exceeds 128 KiB character limit", "Too many updates", "Expected A2UI v0.9",
            "Expected one A2UI event per line", "Invalid surface ID", "Unsupported catalog",
            "Duplicate or too many surfaces", "Use the app theme", "Create the surface first",
            "Too many components", "Invalid or duplicate component ID", "Unsupported component",
            "Initial data must not contain function calls", "Data model too large", "Unknown surface",
            "Unsupported A2UI event", "Missing root component", "Functions cannot change component structure",
            "Invalid weight", "Invalid slider range", "Invalid slider steps", "Grid columns must be 1 to 4",
            "Grid spans require explicit columns and one integer span per child",
            "Row weights need one integer 0..12 per child and at least one positive weight",
            "Use up to 32 static child IDs", "Duplicate children", "Only event actions are allowed",
            "Unsupported action", "Table too large", "Invalid table rows", "Cyclic or deeply nested components",
            "Too many rendered components", "Invalid data path", "Reserved or array-index data path",
            "JSON too deeply nested", "Object too large", "Too many local calculations",
            "Local calculation count exceeds 128",
            "Unsupported expression or reserved field", "Array too large", "Text too long",
            "Unsupported function property", "Unsupported local function", "Function args must be an object",
            "Invalid function arguments", "Incorrect return type", "Decimal places must be 0 to 6",
            "Too many strings", "Unknown calculator key",
            "Invalid style token", "Style tokens must be literal", "Invalid layout size", "Invalid layout boolean",
            "Scroll needs a bounded height", "Vector needs a short accessibility label", "Vector needs 1-64 shapes",
            "Invalid vector shape", "Unsupported vector shape", "Unknown vector property", "Invalid vector tone",
            "Invalid vector fill", "Vector shape outside viewport", "Invalid vector points", "Invalid vector point count",
            "Invalid vector point", "Invalid vector text",
            "Popovers cannot be nested",
            "Invalid date bounds", "Invalid or unsupported formula", "Invalid table column widths",
            "minLines must not exceed maxLines", "Invalid step index",
            "Repeat cannot be nested", "Repeat exceeds maxItems", "Repeat items must be an array",
            "Repeat items must be objects", "Repeat keys must be unique nonempty strings",
            "item is only available inside Repeat templates", "Expanded Repeat is too large",
            "Html html must be a literal string", "Html css must be a literal string",
            "Html and CSS must each fit 8192 characters; HTML cannot be empty", "Html exceeds 256 elements",
            "Unsupported Html tag; use offline HTML/CSS without scripts, frames, images or links",
            "Unsupported Html attribute; event handlers and resource URLs are not supported",
            "Html inputs support only radio/checkbox visual toggles; use native fields for user data",
            "Use at most four Html visuals per card",
        )
        return generateSequence<Throwable>(error) { it.cause }.take(8)
            .mapNotNull { it.message?.takeIf(known::contains) }.firstOrNull()
            ?: "Invalid field type or missing required field/reference. Query a2ui_catalog and submit the complete document."
    }

    fun diagnosticLine(error: Exception): Int? =
        Regex("^Line (\\d+):").find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

    fun diagnosticCode(error: Exception): String =
        if (generateSequence<Throwable>(error) { it.cause }.take(8).any { it is kotlinx.serialization.SerializationException })
            "invalid_json" else "invalid_card"

    fun parse(source: String, closed: Boolean): Batch {
        require(source.length <= MAX_SOURCE) { "Card exceeds 128 KiB character limit" }
        val lines = source.lines().toMutableList()
        if (!closed && !source.endsWith('\n')) lines.removeLastOrNull()
        val completeLines = lines.filter { it.isNotBlank() }
        require(completeLines.size <= 256) { "Too many updates" }
        val surfaces = linkedMapOf<String, MutableMap<String, JsonObject>>()
        val callCount = intArrayOf(0)
        lines.withIndex().filter { it.value.isNotBlank() }.forEach { (index, line) ->
            try {
            checkJsonDepth(line)
            val event = json.parseToJsonElement(line).jsonObject
            require(event["version"]?.jsonPrimitive?.content == "v0.9") { "Expected A2UI v0.9" }
            require(event.size == 2) { "Expected one A2UI event per line" }
            val kind = event.keys.single { it != "version" }
            val body = event[kind]!!.jsonObject
            val surfaceId = body["surfaceId"]?.jsonPrimitive?.content.orEmpty()
            require(idPattern.matches(surfaceId)) { "Invalid surface ID" }
            checkValue(body, callCount = callCount)
            when (kind) {
                "createSurface" -> {
                    require(body["catalogId"]?.jsonPrimitive?.content == CATALOG) { "Unsupported catalog" }
                    require(surfaceId !in surfaces && surfaces.size < 4) { "Duplicate or too many surfaces" }
                    require(body["theme"] == null) { "Use the app theme" }
                    surfaces[surfaceId] = linkedMapOf()
                }
                "updateComponents" -> {
                    val graph = requireNotNull(surfaces[surfaceId]) { "Create the surface first" }
                    val updates = body["components"]!!.jsonArray
                    require(updates.size <= 128) { "Too many components" }
                    val ids = mutableSetOf<String>()
                    updates.forEach { item ->
                        val component = item.jsonObject
                        require(!component.toString().contains(CardRowState.ROOT)) { "Reserved row state" }
                        val id = component["id"]?.jsonPrimitive?.content.orEmpty()
                        require(idPattern.matches(id) && ids.add(id)) { "Invalid or duplicate component ID" }
                        require(component["component"]?.jsonPrimitive?.content in components) {
                            "Unsupported component"
                        }
                        validateComponent(component)
                        graph[id] = component
                    }
                    require(graph.size <= 128) { "Too many components" }
                    validateGraph(graph, requireReferences = false)
                }
                "updateDataModel" -> {
                    require(!containsCall(body)) { "Initial data must not contain function calls" }
                    require(surfaceId in surfaces) { "Create the surface first" }
                    validatePath(body["path"]?.jsonPrimitive?.content ?: "/")
                    require(!body.toString().contains(CardRowState.ROOT)) { "Reserved row state" }
                    require(body["value"].toString().length <= MAX_STATE) { "Data model too large" }
                }
                "deleteSurface" -> require(surfaces.remove(surfaceId) != null) { "Unknown surface" }
                else -> error("Unsupported A2UI event")
            }
            } catch (e: Exception) {
                // Keep the line number for diagnostics without copying model output into logs.
                throw IllegalArgumentException("Line ${index + 1}: ${e.message?.take(160)}", e)
            }
        }
        if (closed) surfaces.forEach { (surface, graph) ->
            val it = graph
            require("root" in it) { "Missing root component" }
            validateGraph(it, requireReferences = true)
            val data = CardRowState.prepare(it, CardRepeat.initialData(completeLines, surface), CardLiveStore.isLive(surface))
            validateExpanded(CardRepeat.expand(it, data), data, strictInitial = true)
        }
        return Batch(completeLines, surfaces.keys.toSet(), closed, surfaces.mapValues { it.value.toMap() })
    }

    private fun validateComponent(c: JsonObject, validateDisplay: Boolean = true) {
        val kind = c["component"]?.jsonPrimitive?.content.orEmpty()
        fun validateEventValues(value: JsonElement, allowed: Boolean) {
            when (value) {
                is JsonObject -> {
                    if ("event_value" in value) require(allowed && value.keys == setOf("event_value") &&
                        (value["event_value"] as? JsonPrimitive)?.content in setOf("value", "previous", "index")) {
                        "event_value is only available in onChange context"
                    }
                    value.values.forEach { validateEventValues(it, allowed) }
                }
                is JsonArray -> value.forEach { validateEventValues(it, allowed) }
                else -> Unit
            }
        }
        c.forEach { (key, value) -> validateEventValues(value, key == "onChange") }
        CardAuthoringCatalog.enums.forEach { (key, allowed) ->
            if (key.substringBefore('.') == kind) c[key.substringAfter('.')]?.let { value ->
                if (value is JsonPrimitive) require(value.isString && value.content in allowed) { "Invalid style token" }
                else require(!CardAuthoringCatalog.isStatic(kind, key.substringAfter('.'))) { "Style tokens must be literal" }
            }
        }
        CardAuthoringCatalog.ranges.forEach { (key, range) ->
            if (key.substringBefore('.') == kind) c[key.substringAfter('.')]?.let {
                require(it is JsonPrimitive && !it.isString && it.intOrNull in range) { "Invalid layout size" }
            }
        }
        if (kind == "Vector") CardVectorData.validateDefinition(c)
        if (kind == "Html") CardHtmlDocument.validateDefinition(c)
        if (kind == "Row") c["weights"]?.let { weights ->
            val refs = c["children"] as? JsonArray
            require("child" !in c && weights is JsonArray && refs != null && weights.size == refs.size &&
                weights.all { it is JsonPrimitive && !it.isString && it.intOrNull in 0..12 } &&
                weights.any { it.jsonPrimitive.int > 0 }) {
                "Row weights need one integer 0..12 per child and at least one positive weight"
            }
        }
        if (kind == "TextArea") require(
            (c["minLines"]?.jsonPrimitive?.int ?: 3) <= (c["maxLines"]?.jsonPrimitive?.int ?: 8)
        ) { "minLines must not exceed maxLines" }
        if (validateDisplay && kind == "Formula" && c["text"] is JsonPrimitive)
            require(validCardFormula(c.getValue("text").jsonPrimitive.content)) { "Invalid or unsupported formula" }
        if (validateDisplay && kind == "DatePicker" && listOf("minDate", "maxDate").all { c[it] == null || c[it] is JsonPrimitive })
            require(cardDateBounds(c["minDate"]?.jsonPrimitive?.content.orEmpty(),
                c["maxDate"]?.jsonPrimitive?.content.orEmpty()) != null) { "Invalid date bounds" }
        if (kind == "Frame") {
            fun n(key: String, fallback: Double) = c[key]?.jsonPrimitive?.double ?: fallback
            require(n("minWidth", 0.0) <= n("maxWidth", 960.0) &&
                n("minHeight", 0.0) <= n("maxHeight", 480.0)) { "Invalid layout size" }
            c["aspectRatio"]?.let {
                require(it is JsonPrimitive && !it.isString && it.doubleOrNull?.let { ratio -> ratio in 0.25..4.0 } == true) { "Invalid layout size" }
            }
        }
        for (key in listOf("wrap", "border")) c[key]?.let {
            require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) { "Invalid layout boolean" }
        }
        if (kind == "Scroll") require(c.containsKey("maxHeight")) { "Scroll needs a bounded height" }
        c.forEach { (key, value) ->
            if (key in setOf("id", "component", "child", "children", "weight", "min", "max", "type", "columnCount", "columnSpans", "steps")) {
                require(!containsCall(value)) { "Functions cannot change component structure" }
            }
        }
        c["weight"]?.jsonPrimitive?.double?.let {
            require(it.isFinite() && it > 0 && it <= 100) { "Invalid weight" }
        }
        if (c["component"]?.jsonPrimitive?.content == "Slider") {
            val min = c["min"]?.jsonPrimitive?.double ?: 0.0
            val max = c["max"]?.jsonPrimitive?.double ?: 100.0
            require(min.isFinite() && max.isFinite() && min >= -1_000_000 &&
                max <= 1_000_000 && max > min) { "Invalid slider range" }
            c["steps"]?.let { require((it as? JsonPrimitive)?.intOrNull in 0..999) { "Invalid slider steps" } }
        }
        if (c["component"]?.jsonPrimitive?.content == "Grid") c["columnCount"]?.let {
            require((it as? JsonPrimitive)?.intOrNull in 1..4) { "Grid columns must be 1 to 4" }
        }
        if (c["component"]?.jsonPrimitive?.content == "Grid") c["columnSpans"]?.let { spans ->
            val count = (c["columnCount"] as? JsonPrimitive)?.intOrNull
            val refs = c["children"] as? JsonArray
            require(count != null && count in 1..4 && refs != null && spans is JsonArray &&
                spans.size == refs.size && spans.all {
                    it is JsonPrimitive && !it.isString && it.intOrNull in 1..count
                }) { "Grid spans require explicit columns and one integer span per child" }
        }
        c["children"]?.let {
            require(it is JsonArray && it.size <= 32 && it.all { child -> child is JsonPrimitive && child.isString }) {
                "Use up to 32 static child IDs"
            }
            require(it.distinct().size == it.size) { "Duplicate children" }
        }
        c["action"]?.jsonObject?.let {
            require(it.keys == setOf("event")) { "Only event actions are allowed" }
            require(it["event"]!!.jsonObject["name"]?.jsonPrimitive?.content in actions) { "Unsupported action" }
        }
        c["onChange"]?.let { action ->
            require(kind in CardAuthoringCatalog.changeComponents && action is JsonObject &&
                action.keys == setOf("event") &&
                (action["event"] as? JsonObject)?.get("name")?.jsonPrimitive?.content == "local_action") {
                "onChange only supports local_action"
            }
        }
        if (c["component"]?.jsonPrimitive?.content == "Table") {
            val columns = c["columns"]!!.jsonArray
            require(columns.size in 1..8) { "Table too large" }
            c["columnWidths"]?.let { widths ->
                require(widths is JsonArray && widths.size == columns.size &&
                    widths.all { it is JsonPrimitive && !it.isString && it.intOrNull in 64..480 }) { "Invalid table column widths" }
            }
            // Bindings and calculations may occur in each cell or return whole rows.
            // Validate scalar cells after evaluation in validateExpanded, just like Chart
            // validates its evaluated series; inspecting raw expression objects rejects
            // otherwise valid tables before their initial data can be applied.
            (c["rows"] as? JsonArray)?.let { rows ->
                require(rows.size <= 32) { "Invalid table rows" }
            }
        }
    }

    private fun containsItem(value: JsonElement): Boolean = when (value) {
        is JsonObject -> "item" in value || "item_state" in value || value.values.any(::containsItem)
        is JsonArray -> value.any(::containsItem)
        else -> false
    }

    fun validateExpanded(graph: Map<String, JsonObject>, data: JsonObject) = validateExpanded(graph, data, false)

    fun validateExpanded(graph: Map<String, JsonObject>, data: JsonObject, strictInitial: Boolean) {
        require(graph.size <= 512) { "Too many rendered components" }
        val calls = intArrayOf(0)
        graph.values.forEach { c ->
            checkValue(c, callCount = calls)
            require(!containsItem(c)) { "item is only available inside Repeat templates" }
            validateComponent(c, validateDisplay = strictInitial)
            val kind = c.getValue("component").jsonPrimitive.content
            if (kind == "Vector") CardVectorData.parse(CardRepeat.evaluate(c, data).jsonObject)
            if (strictInitial && kind in setOf("DatePicker", "Formula"))
                validateComponent(CardRepeat.evaluate(c, data).jsonObject)
            if (kind == "Stepper") {
                val labels = CardRepeat.evaluate(c.getValue("labels"), data).jsonArray
                val current = CardRepeat.evaluate(c.getValue("current"), data).jsonPrimitive.double
                require(labels.size in 1..32 && current % 1.0 == 0.0 && current >= 0 && current < labels.size) { "Invalid step index" }
            }
            if (kind == "Chart") {
                val labels = c["labels"]?.let { CardRepeat.evaluate(it, data).jsonArray.map { v -> v.jsonPrimitive.content } }.orEmpty()
                cardChartSeries(A2uiProtocol.toValue(CardRepeat.evaluate(c.getValue("series"), data)), labels,
                    c.getValue("type").jsonPrimitive.content)
            }
            if (kind == "Map") cardMapMarkers(A2uiProtocol.toValue(CardRepeat.evaluate(c.getValue("markers"), data)))
            if (kind == "Flow") cardFlowSteps(A2uiProtocol.toValue(CardRepeat.evaluate(c.getValue("steps"), data)))
            if (kind == "Table") {
                val rows = CardRepeat.evaluate(c.getValue("rows"), data).jsonArray
                val count = c.getValue("columns").jsonArray.size
                require(rows.size <= 32 && rows.all { row ->
                    row is JsonArray && row.size == count && row.all { it is JsonPrimitive }
                }) { "Invalid table rows" }
            }
            CardAuthoringCatalog.components.getValue(kind).forEach { field ->
                if (field.type in setOf("child", "children", "action")) return@forEach
                val raw = c[field.name] ?: return@forEach
                val v = CardRepeat.evaluate(raw, data)
                if (field.name in setOf("labels", "columns", "options") && v is JsonArray)
                    require(v.all { it is JsonPrimitive && it.isString }) { "Invalid repeated string array" }
                require(when(field.type) {
                    "string" -> v is JsonPrimitive && v.isString
                    "number" -> v is JsonPrimitive && !v.isString && v.doubleOrNull?.isFinite() == true
                    "boolean" -> v is JsonPrimitive && !v.isString && v.booleanOrNull != null
                    "array" -> v is JsonArray
                    else -> false
                }) { "Invalid repeated ${field.name} value" }
            }
        }
        validateGraph(graph, true)
        // Also used by prepare_card: passing our protocol checks must not mask a
        // component payload that AndroidX will reject when the card is displayed.
        CardRendererSchema.validate(graph)
    }

    private fun references(c: JsonObject): List<String> =
        c["children"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty() +
            listOfNotNull(c["child"]?.jsonPrimitive?.content)

    private fun validateGraph(graph: Map<String, JsonObject>, requireReferences: Boolean) {
        // Count expanded occurrences, not just unique IDs: a small DAG can render exponentially.
        var htmlCount = 0
        fun visit(id: String, ancestors: Set<String>, depth: Int): Int {
            require(depth <= 12 && id !in ancestors) { "Cyclic or deeply nested components" }
            val node = graph[id]
            if (node == null) {
                require(!requireReferences) { "Missing child: $id" }
                return 1
            }
            if (node["component"]?.jsonPrimitive?.content == "Html") {
                htmlCount++
                require(htmlCount <= 4) { "Use at most four Html visuals per card" }
            }
            if (node["component"]?.jsonPrimitive?.content == "Popover")
                require(ancestors.none { graph[it]?.get("component")?.jsonPrimitive?.content == "Popover" }) {
                    "Popovers cannot be nested"
                }
            if (ancestors.any { graph[it]?.get("component")?.jsonPrimitive?.content == "Pressable" })
                require(node["component"]?.jsonPrimitive?.content in setOf("Text", "Icon", "Heading",
                    "Header", "InfoRow", "Display", "Badge", "Progress", "Divider", "Code", "Vector", "Flow",
                    "Row", "Column", "Grid", "Frame", "Panel", "Card", "Conditional", "List")) {
                    "Pressable content must not contain interactive controls"
                }
            var count = 1
            references(node).forEach {
                count += visit(it, ancestors + id, depth + 1)
                require(count <= 512) { "Too many rendered components" }
            }
            return count
        }
        graph.keys.forEach { htmlCount = 0; visit(it, emptySet(), 0) }
    }

    fun validatePath(path: String) {
        require(path.startsWith("/") && path.length <= 256 && path.count { it == '/' } <= 12) { "Invalid data path" }
        require(path.split('/').none { it == MARKER || it.all(Char::isDigit) && it.isNotEmpty() }) {
            "Reserved or array-index data path"
        }
    }

    private fun containsCall(value: JsonElement): Boolean = when (value) {
        is JsonObject -> "call" in value || value.values.any(::containsCall)
        is JsonArray -> value.any(::containsCall)
        else -> false
    }

    private fun checkValue(value: JsonElement, depth: Int = 0, callCount: IntArray = intArrayOf(0)) {
        require(depth <= 20) { "JSON too deeply nested" }
        when (value) {
            is JsonObject -> {
                require(value.size <= 128) { "Object too large" }
                if ("call" in value) {
                    require(++callCount[0] <= 128) { "Local calculation count exceeds 128" }
                    CardFunctions.validate(value)
                }
                value.forEach { (key, child) ->
                    require(key.length <= 256 && key !in setOf(MARKER, "validationRegexp", "callableFrom")) {
                        "Unsupported expression or reserved field"
                    }
                    if (key == "path") validatePath(child.jsonPrimitive.content)
                    checkValue(child, depth + 1, callCount)
                }
            }
            is JsonArray -> {
                require(value.size <= 128) { "Array too large" }
                value.forEach { checkValue(it, depth + 1, callCount) }
            }
            is JsonPrimitive -> require(value.content.length <= 8192) { "Text too long" }
        }
    }

    // Bound nesting before any recursive JSON parser sees untrusted input.
    private fun checkJsonDepth(text: String) {
        var depth = 0
        var quoted = false
        var escape = false
        for (char in text) {
            if (quoted) {
                if (escape) escape = false
                else if (char == '\\') escape = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 24) { "JSON too deeply nested" } }
                '}', ']' -> depth--
            }
        }
    }

    fun safeUrl(text: String): Boolean = runCatching {
        val uri = URI(text)
        text.length <= 2048 && uri.scheme in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    fun toJson(value: Any?, depth: Int = 0): JsonElement {
        require(depth <= 16) { "State too deeply nested" }
        return when (value) {
            null -> JsonNull
            is Map<*, *> -> {
                require(value.size <= 128) { "State object too large" }
                JsonObject(value.entries.associate { (key, item) ->
                    require(key is String && key.length <= 256)
                    key to toJson(item, depth + 1)
                })
            }
            is List<*> -> {
                require(value.size <= 128) { "State array too large" }
                JsonArray(value.map { toJson(it, depth + 1) })
            }
            is String -> { require(value.length <= 8192); JsonPrimitive(value) }
            is Boolean -> JsonPrimitive(value)
            is Number -> { require(value.toDouble().isFinite()); JsonPrimitive(value) }
            else -> error("Unsupported state value")
        }
    }

    fun toValue(value: JsonElement): Any? = when (value) {
        is JsonObject -> value.mapValues { toValue(it.value) }
        is JsonArray -> value.map(::toValue)
        JsonNull -> null
        is JsonPrimitive -> if (value.isString) value.content
            else value.booleanOrNull ?: value.longOrNull ?: value.double
    }
}
