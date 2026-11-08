package com.ai.assistance.operit.ui.common.oktoast

/** 请求错误留出阅读时间，长文本递增；普通操作提示不使用此规则。 */
internal object RequestErrorDuration {
    const val MIN_MILLIS = 5_000
    const val MAX_MILLIS = 12_000

    fun baseMillis(message: String): Int {
        // 按 Unicode 码点计算，避免一个表情因代理对被计作两个字符。
        val extraCharacters = (message.codePointCount(0, message.length) - 80).coerceAtLeast(0)
        return (MIN_MILLIS + extraCharacters.toLong() * 25L)
            .coerceAtMost(MAX_MILLIS.toLong()).toInt()
    }

    fun limitMillis(recommendedMillis: Int): Int =
        recommendedMillis.coerceIn(MIN_MILLIS, MAX_MILLIS)
}