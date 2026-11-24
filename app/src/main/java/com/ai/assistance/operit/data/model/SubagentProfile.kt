package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/**
 * 子代理档案。
 *
 * 字段拆分遵循成本模型：`name` 与 `description` 每请求随名单注入主对话系统提示，是固定成本
 * （上限 10 × (40 + 200)），各自设独立上限；`instructions` 只进入子任务上下文，与名单成本无关，
 * 单独给大额度。
 *
 * `name` 直接作为模型输出中的委派标识符，不做本地化；存储名固定为用户输入原文。
 * 内置通用子代理的存储名固定为 [BUILT_IN_NAME]，[SubagentRoster.normalize] 时恢复，
 * 防止用户改名导致「未指定 agent = 通用」的路由失效。
 */
@Serializable
data class SubagentProfile(
    val id: String = newId(),
    /** 模型委派时使用的标识符，不本地化 */
    val name: String = "",
    /** 给主代理选人用的简介，随名单注入主对话 */
    val description: String = "",
    /** 给子代理执行的工作指令，仅进子任务上下文 */
    val instructions: String = "",
    /** 模型绑定模式：跟随会话窗口 / 指定模型 */
    val modelBindingMode: String = ModelBindingMode.FOLLOW_SESSION,
    /** FIXED_CONFIG 时使用的模型配置 ID */
    val modelConfigId: String? = null,
    /** FIXED_CONFIG 时使用的多模型索引 */
    val modelIndex: Int = 0,
    val enabled: Boolean = true,
    /** 披露顺序，内置通用子代理置顶 */
    val sortOrder: Int = 0,
    val isBuiltIn: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    object ModelBindingMode {
        const val FOLLOW_SESSION = "FOLLOW_SESSION"
        const val FIXED_CONFIG = "FIXED_CONFIG"

        fun normalize(mode: String?): String {
            return if (mode == FIXED_CONFIG) FIXED_CONFIG else FOLLOW_SESSION
        }
    }

    companion object {
        /**
         * 草稿 ID：编辑器以该 ID 打开时不落库，首次有效保存才写入真实 ID。
         * 无名草稿会被 [SubagentRoster.normalize] 丢弃，不会留下孤儿数据。
         */
        const val DRAFT_ID = "__new__"

        /** 内置通用子代理的稳定 ID */
        const val BUILT_IN_ID = "builtin.general"

        /**
         * 内置通用子代理的存储名，固定中文。
         *
         * 名单注入与 [SubagentRoster.resolve] 都以该名匹配「未指定代理的默认执行者」，
         * 名字漂移会让委派路由失效，所以 normalize 时强制恢复。
         */
        const val BUILT_IN_NAME = "通用子代理"

        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}

/** 字段与数量上限。名单每请求注入，成本 = 数量 × (name + description)。 */
object SubagentLimits {
    /** 含内置通用子代理在内的最大档案数 */
    const val MAX_COUNT = 10
    const val NAME = 40
    const val DESCRIPTION = 200
    const val INSTRUCTIONS = 4000
}
