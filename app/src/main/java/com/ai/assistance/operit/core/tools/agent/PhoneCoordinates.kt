package com.ai.assistance.operit.core.tools.agent

/** Legacy coordinate contract: normalized 0..999, mapped to the original physical display. */
internal fun mapPhoneNormalizedPoint(value: String, width: Int, height: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val parts = value.trim().removeSurrounding("[", "]").split(",")
    if (parts.size != 2) return null
    val x = parts[0].trim().toIntOrNull() ?: return null
    val y = parts[1].trim().toIntOrNull() ?: return null
    if (x !in 0..999 || y !in 0..999) return null
    return (x.toLong() * width / 1000).toInt() to (y.toLong() * height / 1000).toInt()
}
