package com.ai.assistance.operit.core.tools

object ToolExecutionLimits {
    const val MAX_FILE_READ_BYTES = 32_000
    const val DEFAULT_FILE_READ_PART_LINES = 200
    const val MAX_TEXT_RESULT_LENGTH = 5_000

    /**
     * 整文件读取类工具（read_file / read_file_full / read_file_binary 及特殊文件抽取路径）的
     * 文件大小硬上限。超过该大小的文件统一拒绝并引导模型改用 read_file_part 按行分段读取，
     * 避免无界 readText/readBytes 造成 OOM。
     */
    const val MAX_FILE_READ_SIZE_BYTES: Long = 32L * 1024 * 1024

    /** Maximum serialized size of one tool result sent back to the model. */
    const val MAX_SINGLE_TOOL_RESULT_MESSAGE_CHARS = MAX_FILE_READ_BYTES * 2
}
