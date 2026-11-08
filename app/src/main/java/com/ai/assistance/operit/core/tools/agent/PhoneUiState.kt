package com.ai.assistance.operit.core.tools.agent


internal class PhoneUiException(message: String) : IllegalStateException(message)

internal data class PhoneBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isUsable: Boolean get() = left >= 0 && top >= 0 && right > left && bottom > top
    val centerX: Int get() = left + (right - left) / 2
    val centerY: Int get() = top + (bottom - top) / 2

    fun intersect(other: PhoneBounds): PhoneBounds? = PhoneBounds(
        maxOf(left, other.left), maxOf(top, other.top), minOf(right, other.right), minOf(bottom, other.bottom),
    ).takeIf { it.isUsable }

    companion object {
        private val pattern = Regex("""\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""")

        fun parse(value: String): PhoneBounds? = pattern.matchEntire(value)
            ?.groupValues?.drop(1)?.map(String::toInt)?.let { PhoneBounds(it[0], it[1], it[2], it[3]) }
    }
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
    val ref: Int = 0,
    val parentRef: Int? = null,
    val visibleToUser: Boolean? = null,
    val hint: String = "",
    val scrollable: Boolean = false,
    val modelLabel: String = "",
) {
    // Re-resolve coordinates, but reject a changed label, type, focus or selection state.
    fun sameTargetAs(other: PhoneUiNode): Boolean = identity() == other.identity()

    private fun identity() = copy(bounds = null, ref = 0, parentRef = null, visibleToUser = null, modelLabel = "")
}

internal data class PhoneUiSnapshot(
    val revision: Long,
    val packageName: String,
    val nodes: List<PhoneUiNode>,
    val screenBounds: PhoneBounds? = null,
    val capturedAtMs: Long = System.nanoTime() / 1_000_000,
) {
    private val byRef by lazy { nodes.mapIndexed { index, node -> (index + 1) to node }.toMap() }

    fun isVisible(node: PhoneUiNode): Boolean {
        if (node.visibleToUser == false) return false
        var parent = node.parentRef
        var depth = 0
        while (parent != null && depth++ < nodes.size) {
            val ancestor = byRef[parent] ?: break
            if (ancestor.visibleToUser == false) return false
            parent = ancestor.parentRef
        }
        val bounds = node.bounds ?: return false
        return if (screenBounds != null) bounds.intersect(screenBounds) != null else bounds.isUsable
    }

    fun matches(target: PhoneTarget): List<PhoneUiNode> = nodes.filter {
        it.packageName == packageName && isVisible(it) && target.matches(it)
    }.map { node ->
        if (screenBounds == null) node else node.copy(bounds = node.bounds?.intersect(screenBounds))
    }

    fun summary(task: String = ""): PhoneUiSummary = PhoneUiSummary.build(this, task)

    fun toPrompt(): String = summary().payload
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

// Android's ICU regex parser requires literal closing braces to be escaped too.
private val phoneArgumentPattern = Regex("""\{\{([A-Za-z][A-Za-z0-9_]*)\}\}""")

internal fun bindPhoneArguments(template: String, arguments: Map<String, String>): String =
    phoneArgumentPattern.replace(template) { match ->
        arguments[match.groupValues[1]]
            ?: throw PhoneUiException("Missing sequence argument: ${match.groupValues[1]}")
    }

internal fun isPhoneArgument(template: String): Boolean = phoneArgumentPattern.matches(template)

internal fun phoneArgumentNames(template: String): Set<String> =
    phoneArgumentPattern.findAll(template).map { it.groupValues[1] }.toSet()
