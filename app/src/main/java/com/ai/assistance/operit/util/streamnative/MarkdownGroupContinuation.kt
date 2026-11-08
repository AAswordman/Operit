package com.ai.assistance.operit.util.streamnative

import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** 仅让恢复前未结束的块接续一次；原生结束标记会清除接续资格。 */
internal class MarkdownGroupContinuation(initialSegments: IntArray) {
    private var pendingTag: Int? = null

    init {
        var index = 0
        while (index + 2 < initialSegments.size) {
            val tag = initialSegments[index]
            pendingTag = tag.takeIf { it >= 0 }
            index += 3
        }
    }

    fun takeFor(type: MarkdownProcessorType): Boolean {
        val continues = pendingTag == type.ordinal
        pendingTag = null
        return continues
    }

    fun onBoundary() {
        pendingTag = null
    }
}
