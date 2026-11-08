package com.ai.assistance.operit.data.stats

/**
 * 计费方式枚举
 * 用于定义AI模型的计费模式
 */
enum class BillingMode {
    /**
     * 按Token计费
     * 根据输入/输出token数量和对应价格计算费用
     */
    TOKEN,

    /**
     * 按次计费
     * 每次API请求收取固定费用
     */
    COUNT;

    companion object {
        /**
         * 从字符串解析计费方式
         * @param value 字符串值
         * @return 对应的计费方式，如果无法解析则返回TOKEN
         */
        fun fromString(value: String?): BillingMode {
            return when (value?.uppercase()) {
                "COUNT" -> COUNT
                "TOKEN" -> TOKEN
                else -> TOKEN // 默认使用TOKEN模式
            }
        }
    }
}
