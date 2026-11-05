package com.ai.assistance.operit.core.tools.agent

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal enum class PhoneLocalAction { Tap, Type, Wait }

internal data class PhoneLocalStep(
    val action: PhoneLocalAction,
    val target: PhoneTarget?,
    val expect: PhoneTarget?,
    val text: String?,
    val timeoutMs: Long = 2000L,
) {
    fun bind(arguments: Map<String, String>): PhoneLocalStep = copy(
        target = target?.bind(arguments), expect = expect?.bind(arguments),
        text = text?.let { bindPhoneArguments(it, arguments) },
    )
}

internal data class PhoneLocalSequence(
    val packageName: String,
    val steps: List<PhoneLocalStep>,
    val saveAs: String? = null,
) {
    fun bind(arguments: Map<String, String>): PhoneLocalSequence {
        val bound = steps.map { it.bind(arguments) }
        for (step in bound) {
            if ((step.text?.length ?: 0) > 4000) throw PhoneUiException("Bound input exceeds 4000 characters")
            for (target in listOfNotNull(step.target, step.expect)) {
                if (listOfNotNull(target.id, target.text, target.desc, target.className).any { it.length > 4000 }) {
                    throw PhoneUiException("Bound selector exceeds 4000 characters")
                }
            }
        }
        return copy(steps = bound)
    }
}

internal sealed interface PhoneLocalCommand {
    data class Sequence(val screen: Long, val sequence: PhoneLocalSequence, val arguments: Map<String, String>) : PhoneLocalCommand
    data class Replay(val screen: Long, val packageName: String, val name: String, val arguments: Map<String, String>) : PhoneLocalCommand
    object Inspect : PhoneLocalCommand
}

/** Validates the whole response before any phone action is sent. No coordinate batches. */
internal object PhoneLocalPlanParser {
    fun parse(raw: String): PhoneLocalCommand {
        if (raw.length > 32768) throw PhoneUiException("Local command exceeds 32 KiB")
        try {
            val tokens = JSONTokener(raw)
            val value = tokens.nextValue()
            require(value is JSONObject && tokens.nextClean() == '\u0000') { "One complete JSON command is required" }
            val json = value
            return when (json.getString("action")) {
                "Inspect" -> {
                    json.requireKeys(setOf("action"))
                    PhoneLocalCommand.Inspect
                }
                "Sequence" -> {
                    json.requireKeys(setOf("action", "screen", "package", "steps", "arguments", "save_as"))
                    val packageName = json.phonePackage()
                    val screen = json.screen()
                    val steps = json.getJSONArray("steps")
                    require(steps.length() in 1..6) { "A sequence must contain 1 to 6 steps" }
                    val saveAs = if (json.has("save_as")) json.getString("save_as").also(::validateName) else null
                    val parsed = (0 until steps.length()).map { readStep(steps.getJSONObject(it)) }
                    if (saveAs != null) {
                        require(parsed.filter { it.action == PhoneLocalAction.Type }.all { isPhoneArgument(requireNotNull(it.text)) }) {
                            "Saved sequences must pass typed content through a {{parameter}}; argument values are never saved"
                        }
                    }
                    PhoneLocalCommand.Sequence(screen, PhoneLocalSequence(packageName, parsed, saveAs), json.arguments())
                }
                "Replay" -> {
                    json.requireKeys(setOf("action", "screen", "package", "name", "arguments"))
                    val name = json.getString("name").also(::validateName)
                    PhoneLocalCommand.Replay(json.screen(), json.phonePackage(), name, json.arguments())
                }
                else -> throw PhoneUiException("Unknown local command")
            }
        } catch (e: PhoneUiException) {
            throw e
        } catch (e: Exception) {
            throw PhoneUiException("Invalid local command: ${e.message}")
        }
    }

    private fun readStep(json: JSONObject): PhoneLocalStep {
        json.requireKeys(setOf("action", "target", "expect", "text", "timeout_ms"))
        val action = PhoneLocalAction.valueOf(json.getString("action"))
        val target = if (json.has("target")) target(json.getJSONObject("target")) else null
        val expect = if (json.has("expect")) target(json.getJSONObject("expect")) else null
        val text = if (json.has("text")) json.getString("text") else null
        val timeoutMs = if (json.has("timeout_ms")) json.strictLong("timeout_ms") else 2000L
        require(timeoutMs in 100L..5000L) { "timeout_ms must be between 100 and 5000" }
        when (action) {
            PhoneLocalAction.Tap -> require(target != null && expect != null && text == null) { "Tap requires target and expect" }
            PhoneLocalAction.Type -> {
                require(target != null && text != null && text.length <= 4000 && expect == null) { "Type requires target and text; text readback is automatic" }
                require(target.id != null || (target.className != null && target.editable == true)) { "Type needs an id or an editable class, so its target remains identifiable after editing" }
            }
            PhoneLocalAction.Wait -> require(expect != null && target == null && text == null) { "Wait requires expect only" }
        }
        return PhoneLocalStep(action, target, expect, text, timeoutMs)
    }

    private fun target(json: JSONObject): PhoneTarget {
        json.requireKeys(setOf("id", "text", "desc", "class", "editable", "focused", "enabled", "checked", "selected"))
        fun string(key: String): String? = if (json.has(key)) json.getString(key).also {
            require(it.length <= 4000) { "Selector value exceeds 4000 characters" }
        } else null
        fun boolean(key: String): Boolean? = if (json.has(key)) {
            require(json.get(key) is Boolean) { "$key must be a boolean" }; json.getBoolean(key)
        } else null
        val target = PhoneTarget(string("id"), string("text"), string("desc"), string("class"), boolean("editable"), boolean("focused"), boolean("enabled"), boolean("checked"), boolean("selected"))
        require(!target.id.isNullOrBlank() || target.text != null || !target.desc.isNullOrBlank() || !target.className.isNullOrBlank()) { "Selector needs an id, text, desc or class" }
        return target
    }

    private fun JSONObject.screen(): Long = strictLong("screen").also { require(it > 0) { "screen must be positive" } }
    private fun JSONObject.phonePackage(): String = getString("package").also {
        require(it.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+"""))) { "Invalid package" }
    }
    private fun JSONObject.strictLong(key: String): Long {
        val value = get(key)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble()) { "$key must be an integer" }
        return value.toLong()
    }
    private fun JSONObject.arguments(): Map<String, String> {
        if (!has("arguments")) return emptyMap()
        val args = getJSONObject("arguments")
        require(args.length() <= 16) { "At most 16 arguments are allowed" }
        return args.keys().asSequence().associateWith { key ->
            require(key.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Invalid argument name" }
            require(args.get(key) is String) { "Arguments must be strings" }
            args.getString(key).also { require(it.length <= 4000) { "Argument exceeds 4000 characters" } }
        }
    }
    private fun JSONObject.requireKeys(allowed: Set<String>) {
        require(keys().asSequence().all { it in allowed }) { "Unknown command or selector field" }
    }
    internal fun validateName(name: String) {
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,48}"))) { "Sequence name must contain 1 to 48 letters, digits, dots, underscores or hyphens" }
    }

    fun encode(sequence: PhoneLocalSequence): JSONObject {
        fun selector(value: PhoneTarget): JSONObject = JSONObject().apply {
            value.id?.let { put("id", it) }; value.text?.let { put("text", it) }
            value.desc?.let { put("desc", it) }; value.className?.let { put("class", it) }
            value.editable?.let { put("editable", it) }; value.focused?.let { put("focused", it) }
            value.enabled?.let { put("enabled", it) }
            value.checked?.let { put("checked", it) }; value.selected?.let { put("selected", it) }
        }
        return JSONObject().put("action", "Sequence").put("screen", 1).put("package", sequence.packageName)
            .put("steps", JSONArray().apply {
                sequence.steps.forEach { step -> put(JSONObject().apply {
                    put("action", step.action.name); put("timeout_ms", step.timeoutMs)
                    step.target?.let { put("target", selector(it)) }; step.expect?.let { put("expect", selector(it)) }
                    step.text?.let { put("text", it) }
                }) }
            })
    }
}
