package com.ai.assistance.operit.ui.features.extensions.packages.market

/** Preserve the complete exception and cause messages, including the actual host and address. */
internal fun extensionErrorDetails(error: Throwable): String {
    val messages = mutableListOf<String>()
    val visited = mutableSetOf<Throwable>()
    var cause: Throwable? = error
    while (cause != null && visited.add(cause)) {
        messages += cause.toString()
        cause = cause.cause
    }
    return messages.distinct().joinToString("\n")
}
