package com.ai.assistance.operit.data.model

/** 表示不同功能类型，用于指定不同功能使用的AI配置 */
enum class FunctionType {
    CHAT, // 常规对话
    SUMMARY, // 对话总结
    TITLE_GENERATION, // AI总结标题
    MEMORY, // 记忆库处理
    UI_CONTROLLER, // UI自动化控制
    TRANSLATION, // 翻译功能
    GREP, // Grep上下文检索/代码搜索规划
    ROLE_RESPONSE_PLANNER, // 角色回答顺序规划
    IMAGE_RECOGNITION, // 图像识别
    AUDIO_RECOGNITION, // 音频识别
    VIDEO_RECOGNITION, // 视频识别
    TOOL_APPROVAL // 工具调用智能审批
}
