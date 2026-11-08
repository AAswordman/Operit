package com.ai.assistance.operit.ui.common.oktoast

/** 宿主切换只清理短时操作提示，请求错误保留到可见窗口恢复。 */
internal class RetainedNoticeQueue<T>(private val retained: (T) -> Boolean) {
    private val pending = mutableListOf<T>()
    val isEmpty: Boolean get() = pending.isEmpty()

    fun add(value: T) {
        if (!retained(value) && pending.count { !retained(it) } >= 5) {
            pending.removeAt(pending.indexOfFirst { !retained(it) })
        }
        pending.add(value)
    }

    fun restore(value: T) {
        if (retained(value)) pending.add(0, value)
    }

    fun retainForHostChange() {
        pending.removeAll { !retained(it) }
    }

    fun remove(value: T) { pending.remove(value) }
    fun removeFirst(): T = pending.removeAt(0)
}
