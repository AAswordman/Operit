package com.ai.assistance.operit.data.updates.patch

import java.io.IOException

internal data class PatchByteRange(val start: Long, val end: Long, val total: Long) {
    val length: Long get() = end - start + 1L
}

/** 分段下载必须使用服务端确认的原始字节区间，不能把普通页面或错误区间当作补丁。 */
internal fun requirePatchByteRange(
    status: Int,
    contentRange: String?,
    requestedStart: Long,
    requestedEnd: Long,
    expectedTotal: Long? = null,
): PatchByteRange {
    if (status != 206) throw IOException("Patch server must support HTTP byte ranges: HTTP $status")
    val match = Regex("""bytes (\d+)-(\d+)/(\d+)""").matchEntire(contentRange.orEmpty().trim())
        ?: throw IOException("Patch server returned an invalid Content-Range")
    val values = match.groupValues.drop(1).map {
        it.toLongOrNull() ?: throw IOException("Patch server returned an invalid byte range")
    }
    val range = PatchByteRange(values[0], values[1], values[2])
    if (range.total <= 0L || range.start != requestedStart || range.end < range.start ||
        range.end != minOf(requestedEnd, range.total - 1L) ||
        (expectedTotal != null && range.total != expectedTotal)
    ) throw IOException("Patch server returned a different byte range than requested")
    return range
}
