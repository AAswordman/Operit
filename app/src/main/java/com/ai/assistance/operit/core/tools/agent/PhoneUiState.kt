package com.ai.assistance.operit.core.tools.agent

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal class PhoneUiException(message: String) : IllegalStateException(message)

internal data class PhoneBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isUsable: Boolean get() = left >= 0 && top >= 0 && right > left && bottom > top
    val centerX: Int get() = left + (right - left) / 2
    val centerY: Int get() = top + (bottom - top) / 2
}

internal data class PhoneUiNode(
    val packageName: String,
    val resourceId: String = "",
    val className: String = "",
    val text: String = "",
    val description: String = "",
    val bounds: PhoneBounds? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val focused: Boolean = false,
    val enabled: Boolean = true,
    val password: Boolean = false,
    val selected: Boolean = false,
    val checked: Boolean = false,
)

internal data class PhoneUiSnapshot(val revision: Long, val packageName: String, val nodes: List<PhoneUiNode>) {
    // A model response must not start a sequence on a different screen from the one it observed.
    val fingerprint: String by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest((packageName + nodes.toString()).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun matches(target: PhoneTarget): List<PhoneUiNode> = nodes.filter {
        it.packageName == packageName && target.matches(it)
    }

    fun toPrompt(): String {
        val visible = nodes.filter {
            it.packageName == packageName &&
                (it.clickable || it.editable || it.text.isNotEmpty() || it.description.isNotEmpty())
        }
        val items = JSONArray()
        var textBudget = 12000
        for (node in visible.take(120)) {
            val item = JSONObject().apply {
                if (node.resourceId.isNotEmpty()) put("id", node.resourceId)
                if (node.className.isNotEmpty()) put("class", node.className)
                if (node.text.isNotEmpty() && !node.password) put("text", node.text.take(160))
                if (node.description.isNotEmpty() && !node.password) put("desc", node.description.take(160))
                if (node.text.length > 160) put("text_truncated", true)
                if (node.description.length > 160) put("desc_truncated", true)
                if (node.clickable) put("clickable", true)
                if (node.editable) put("editable", true)
                if (node.focused) put("focused", true)
                if (!node.enabled) put("enabled", false)
                if (node.password) put("password", true)
                if (node.selected) put("selected", true)
                if (node.checked) put("checked", true)
            }
            textBudget -= item.toString().length
            if (textBudget < 0) break
            items.put(item)
        }
        return JSONObject().put("screen", revision).put("package", packageName)
            .put("nodes", items).put("truncated", items.length() < visible.size).toString()
    }
}

internal data class PhoneTarget(
    val id: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val className: String? = null,
    val editable: Boolean? = null,
    val focused: Boolean? = null,
    val enabled: Boolean? = null,
    val checked: Boolean? = null,
    val selected: Boolean? = null,
) {
    fun matches(node: PhoneUiNode): Boolean =
        (id == null || id == node.resourceId) &&
            (text == null || (!node.password && text == node.text)) &&
            (desc == null || (!node.password && desc == node.description)) &&
            (className == null || className == node.className) &&
            (editable == null || editable == node.editable) &&
            (focused == null || focused == node.focused) &&
            (enabled == null || enabled == node.enabled) &&
            (checked == null || checked == node.checked) &&
            (selected == null || selected == node.selected)

    fun bind(arguments: Map<String, String>): PhoneTarget = copy(
        id = id?.let { bindPhoneArguments(it, arguments) },
        text = text?.let { bindPhoneArguments(it, arguments) },
        desc = desc?.let { bindPhoneArguments(it, arguments) },
        className = className?.let { bindPhoneArguments(it, arguments) },
    )
}

private val phoneArgumentPattern = Regex("""\{\{([A-Za-z][A-Za-z0-9_]*)}}""")

internal fun bindPhoneArguments(template: String, arguments: Map<String, String>): String =
    phoneArgumentPattern.replace(template) { match ->
        arguments[match.groupValues[1]]
            ?: throw PhoneUiException("Missing sequence argument: ${match.groupValues[1]}")
    }

internal fun isPhoneArgument(template: String): Boolean = phoneArgumentPattern.matches(template)
