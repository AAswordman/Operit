package com.ai.assistance.operit.data.annualreport

import kotlinx.serialization.Serializable

// 年度报告用户数据契约，与 annual-report/src/data/types.ts 一一对应，增删字段必须两侧同步。
// 口径见 docs/TODO/annual_report_20261105/1_data_contract.md。
// 可空字段表示"今年没有这项数据"，序列化时保留为 null，前端据此决定场景是否出现。

const val ANNUAL_REPORT_SCHEMA_VERSION = 1

@Serializable
data class ReportWindow(
    val start: String,
    val end: String,
    val timeZone: String,
)

@Serializable
data class UserReport(
    val schemaVersion: Int,
    val year: Int,
    val generatedAt: String,
    val window: ReportWindow,
    val install: UserInstall,
    val github: GitHubIdentity?,
    val tokens: UserTokens,
    val messages: UserMessages,
    val activity: UserActivity,
    val hours: UserHours,
    val firstMessage: FirstMessage?,
    val longestChat: ChatHighlight?,
    val topCharacter: CharacterHighlight?,
    val aiWork: UserAiWork,
    val tools: UserTools,
    val creations: UserCreations,
)

@Serializable
data class UserInstall(
    val firstInstallAt: String,
)

@Serializable
data class GitHubIdentity(
    val login: String,
    val id: Long,
)

@Serializable
data class UserCost(
    val currency: String,
    val known: Double,
    val unpricedRequests: Long,
)

@Serializable
data class ModelUsage(
    val model: String,
    val tokens: Long,
    val requests: Long,
)

@Serializable
data class UserTokens(
    val coverageStart: String?,
    val total: Long,
    val input: Long,
    val output: Long,
    val cachedInput: Long,
    val requests: Long,
    val cost: UserCost,
    val byMonth: List<Long>,
    val topModels: List<ModelUsage>,
)

@Serializable
data class UserMessages(
    val user: Long,
    val ai: Long,
    val chatsCreated: Long,
    val regenerations: Long,
    val favorites: Long,
)

@Serializable
data class DailyActivity(
    val date: String,
    val messages: Long,
    val tokens: Long,
)

@Serializable
data class StreakRange(
    val days: Int,
    val start: String,
    val end: String,
)

@Serializable
data class BusiestDay(
    val date: String,
    val messages: Long,
)

@Serializable
data class UserActivity(
    val days: List<DailyActivity>,
    val activeDays: Int,
    val longestStreak: StreakRange?,
    val busiestDay: BusiestDay?,
)

@Serializable
data class UserHours(
    val byHour: List<Long>,
    val latestNight: String?,
)

@Serializable
data class FirstMessage(
    val at: String,
    val text: String,
    val chatTitle: String,
)

@Serializable
data class ChatHighlight(
    val title: String,
    val messages: Long,
)

@Serializable
data class CharacterHighlight(
    val name: String,
    val messages: Long,
    val chats: Long,
)

@Serializable
data class UserAiWork(
    val durationMs: Long,
    val longestReplyTokens: Long,
    val coverageStart: String?,
)

@Serializable
data class ToolUsage(
    val name: String,
    val count: Long,
)

@Serializable
data class UserTools(
    val total: Long,
    val top: List<ToolUsage>,
)

@Serializable
data class UserCreations(
    val memories: Long,
    val characterCards: Long,
    val workflows: Long,
)

/** 登录 GitHub 的用户在插件市场的作品，打开对应场景时按需请求。 */
@Serializable
data class PersonalMarket(
    val entries: Int,
    val downloads: Long,
    val likes: Long,
    val titles: List<String>,
)

/** 原生返回给页面的错误体，页面原样展示 message。 */
@Serializable
data class ReportDataError(
    val message: String,
)
