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

    /**
     * AI 读取提炼的最小文件字节数：低于该阈值的文件直接返回原文，
     * 提炼节省的上下文不足以抵消一次功能模型调用的延迟与费用。
     */
    const val FILE_DISTILL_MIN_BYTES = 2048

    /**
     * 单次提炼协议的输入上限（文件字节数）：read_file_full 纯文本在此以下整读进一次
     * 提炼调用，以上进入扫描协议逐块提炼
     */
    const val FILE_DISTILL_SINGLE_INPUT_BYTES = 64_000

    /** 扫描协议（read_file_part / 大文件 full 提炼）每轮送入提炼模型的文本块字符数 */
    const val FILE_DISTILL_SCAN_CHUNK_CHARS = 24_000

    /** 扫描协议最大轮数；触顶显式返回"已找到内容 + 扫描未到文件尾"，不静默截断 */
    const val FILE_DISTILL_SCAN_MAX_ROUNDS = 8

    /** 扫描结束时，累计摘录超过已扫原文该比例则视为"大部分内容都相关"，直接返回原文 */
    const val FILE_DISTILL_EXCERPT_RATIO_LIMIT = 0.6

    /** 单次提炼协议调用的总超时（毫秒） */
    const val FILE_DISTILL_SINGLE_TIMEOUT_MS = 30_000L

    /** 扫描提炼协议单轮调用的超时（毫秒） */
    const val FILE_DISTILL_SCAN_ROUND_TIMEOUT_MS = 20_000L
}
