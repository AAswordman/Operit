package com.ai.assistance.operit.data.annualreport

import android.content.Context
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.Memory_
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.data.stats.TokenStatsAnnualSummary
import com.ai.assistance.operit.data.stats.TokenStatsPreferences
import com.ai.assistance.operit.data.stats.TokenStatsQueryParams
import com.ai.assistance.operit.data.stats.TokenStatsQueryService
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 按数据契约生成年度报告的用户数据。全程只读，运行在 IO 线程。
 * 任何一个数据源读取失败都直接抛出，由宿主把原因交给页面的错误页，不输出缺字段的报告。
 */
class AnnualReportCollector(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val dao = database.annualReportDao()
    private val contentDao = database.chatContentDao()

    private data class Window(
        val zone: ZoneId,
        val start: ZonedDateTime,
        val end: ZonedDateTime,
    ) {
        val startMs: Long get() = start.toInstant().toEpochMilli()
        val endMs: Long get() = end.toInstant().toEpochMilli()
    }

    suspend fun collect(year: Int): UserReport = withContext(Dispatchers.IO) {
        val window = windowOf(year)
        val startedAt = System.currentTimeMillis()
        val github = githubIdentity()
        val tokenSummary = tokenSummary(window)
        val report =
            UserReport(
                schemaVersion = ANNUAL_REPORT_SCHEMA_VERSION,
                year = year,
                generatedAt = iso(ZonedDateTime.now(window.zone)),
                window = ReportWindow(iso(window.start), iso(window.end), window.zone.id),
                install = UserInstall(firstInstallAt = iso(installTime(window.zone))),
                github = github,
                tokens = tokens(window, tokenSummary),
                messages = messages(window),
                activity = activity(window, tokenSummary.dayTotals),
                hours = hours(window),
                firstMessage = firstMessage(window),
                longestChat = dao.findLongestChat(window.startMs, window.endMs)?.let { ChatHighlight(it.title, it.messages) },
                topCharacter = dao.findTopCharacter(window.startMs, window.endMs)?.let { CharacterHighlight(it.name, it.messages, it.chats) },
                aiWork = aiWork(window),
                tools = tools(window),
                creations = creations(window),
            )
        AppLogger.d(TAG, "年度报告用户数据生成完成，耗时 ${System.currentTimeMillis() - startedAt}ms")
        report
    }

    /** 登录 GitHub 的用户在插件市场的作品。需要联网，只在页面请求时调用。 */
    suspend fun collectPersonalMarket(): PersonalMarket = withContext(Dispatchers.IO) {
        val identity = githubIdentity() ?: error("没有登录 GitHub，无法查询插件市场作品")
        val api = MarketStatsApiService()
        val summaries = api.findPublisherEntries("gh_${identity.id}").getOrThrow()
        val owned = summaries.filter { it.relation.equals("owner", ignoreCase = true) && it.stateCode.equals("approved", ignoreCase = true) }
        var downloads = 0L
        var likes = 0L
        val titles = mutableListOf<String>()
        for (summary in owned) {
            val entry = api.getEntry(summary.id).getOrThrow() ?: error("市场条目 ${summary.id} 不在公开索引里")
            val stats = entry.stats ?: error("市场条目 ${summary.id} 缺少下载统计")
            downloads += stats.downloads
            likes += stats.likes
            titles += entry.title
        }
        PersonalMarket(entries = owned.size, downloads = downloads, likes = likes, titles = titles)
    }

    private fun windowOf(year: Int): Window {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone)
        val nextYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone)
        val now = ZonedDateTime.now(zone)
        require(now.isAfter(start)) { "$year 年还没有开始，无法生成年度报告" }
        // 窗口截止到现在；如果已经过了这一年，就截止到次年 1 月 1 日零点。
        val end = if (now.isBefore(nextYear)) now else nextYear
        return Window(zone, start, end)
    }

    private fun iso(time: ZonedDateTime): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time.truncatedTo(ChronoUnit.SECONDS).toOffsetDateTime())

    private fun isoOf(epochMs: Long, zone: ZoneId): String = iso(Instant.ofEpochMilli(epochMs).atZone(zone))

    private fun installTime(zone: ZoneId): ZonedDateTime {
        @Suppress("DEPRECATION")
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return Instant.ofEpochMilli(info.firstInstallTime).atZone(zone)
    }

    private suspend fun githubIdentity(): GitHubIdentity? {
        val user = GitHubAuthPreferences.getInstance(appContext).getCurrentUserInfo() ?: return null
        return GitHubIdentity(login = user.login, id = user.id)
    }

    /** 与 Token 统计页使用同一份货币与汇率设置。 */
    private suspend fun tokenSummary(window: Window): TokenStatsAnnualSummary {
        val preferences = TokenStatsPreferences(appContext)
        val params =
            TokenStatsQueryParams(
                targetCurrency = preferences.loadTargetCurrency(),
                manualRate = preferences.loadRateWithEstimate().first,
            )
        return TokenStatsQueryService.annualSummary(
            context = appContext,
            range = TokenStatsTimeRange(window.startMs, window.endMs),
            params = params,
            zone = window.zone,
        )
    }

    private suspend fun tokens(window: Window, summary: TokenStatsAnnualSummary): UserTokens {
        val totals = summary.totals
        return UserTokens(
            coverageStart = dao.earliestTokenRecord(window.startMs, window.endMs)?.let { isoOf(it, window.zone) },
            total = totals.totalTokens.knownSum,
            input = totals.totalInput.knownSum,
            output = totals.output.knownSum,
            cachedInput = totals.cachedInput.knownSum,
            requests = totals.requests,
            cost =
                UserCost(
                    currency = totals.cost.currency.code,
                    known = totals.cost.knownAmount,
                    unpricedRequests = totals.cost.unknownContributionCount,
                ),
            byMonth = summary.monthTotals,
            topModels =
                summary.displayModels.take(TOP_MODELS).map { model ->
                    ModelUsage(model = model.displayName, tokens = model.totals.totalTokens.knownSum, requests = model.totals.requests)
                },
        )
    }

    private suspend fun messages(window: Window): UserMessages {
        // GROUP BY 不会为没有消息的发送方返回行，缺行就是这一年 0 条。
        val bySender = dao.countMessagesBySender(window.startMs, window.endMs).associate { it.sender to it.count }
        return UserMessages(
            user = bySender["user"] ?: 0L,
            ai = bySender["ai"] ?: 0L,
            chatsCreated = dao.countChatsCreated(window.startMs, window.endMs),
            regenerations = dao.countVariants(window.startMs, window.endMs),
            favorites = dao.countFavorites(window.startMs, window.endMs),
        )
    }

    private suspend fun activity(window: Window, tokenDays: Map<LocalDate, Long>): UserActivity {
        val messageDays = dao.countUserMessagesByDay(window.startMs, window.endMs).associate { LocalDate.parse(it.localDate) to it.count }
        val dates = (messageDays.keys + tokenDays.keys).toSortedSet()
        val days =
            dates.map { date ->
                // 某天只有用户消息或只有 token 记录时，另一项当天确实是 0。
                DailyActivity(date = date.toString(), messages = messageDays[date] ?: 0L, tokens = tokenDays[date] ?: 0L)
            }
        val activeDates = messageDays.filterValues { it > 0 }.keys.sorted()
        val longest = longestStreak(activeDates)
        val busiest = messageDays.maxByOrNull { it.value }?.let { BusiestDay(date = it.key.toString(), messages = it.value) }
        return UserActivity(days = days, activeDays = activeDates.size, longestStreak = longest, busiestDay = busiest)
    }

    private suspend fun hours(window: Window): UserHours {
        val counts = LongArray(24)
        for (row in dao.countUserMessagesByHour(window.startMs, window.endMs)) {
            counts[row.hour] = row.count
        }
        val latest = dao.findLatestNightMessage(window.startMs, window.endMs)
        return UserHours(byHour = counts.toList(), latestNight = latest?.let { isoOf(it, window.zone) })
    }

    private suspend fun firstMessage(window: Window): FirstMessage? {
        val row = dao.findFirstUserMessage(window.startMs, window.endMs) ?: return null
        val cleaned = cleanUserText(row.preview)
        // 只发了附件的消息清理后没有文字，如实写成"一条只有附件的消息"。
        val text = if (cleaned.isEmpty()) "一条只有附件的消息" else cleaned.take(FIRST_MESSAGE_CHARS)
        return FirstMessage(at = isoOf(row.timestamp, window.zone), text = text, chatTitle = row.chatTitle)
    }

    private suspend fun aiWork(window: Window): UserAiWork {
        val messages = dao.aiWorkInMessages(window.startMs, window.endMs)
        val variants = dao.aiWorkInVariants(window.startMs, window.endMs)
        val firstTracked = listOfNotNull(messages.firstTrackedAt, variants.firstTrackedAt).minOrNull()
        return UserAiWork(
            durationMs = messages.durationMs + variants.durationMs,
            longestReplyTokens = maxOf(messages.longestReplyTokens, variants.longestReplyTokens),
            coverageStart = firstTracked?.let { isoOf(it, window.zone) },
        )
    }

    /** 统计采用的回复和重新生成的版本里所有工具标签；正文逐条分段读取，只在内存里保留计数。 */
    private suspend fun tools(window: Window): UserTools {
        val counts = HashMap<String, Long>()
        val count = { content: String ->
            for (match in ChatMarkupRegex.namePattern.findAll(content)) {
                val name = match.groupValues[1].trim()
                if (name.isNotEmpty()) counts.merge(name, 1L) { previous, added -> previous + added }
            }
        }
        for (locator in dao.findToolMessages(window.startMs, window.endMs)) {
            val message = contentDao.getMessageByTimestamp(locator.chatId, locator.timestamp)
                ?: error("消息 ${locator.chatId}/${locator.timestamp} 在读取过程中消失")
            count(message.content)
        }
        for (locator in dao.findToolVariantMessages(window.startMs, window.endMs)) {
            for (variant in contentDao.getVariantsForMessage(locator.chatId, locator.timestamp)) {
                count(variant.content)
            }
        }
        val top =
            counts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                .take(TOP_TOOLS)
                .map { ToolUsage(name = it.key, count = it.value) }
        return UserTools(total = counts.values.sum(), top = top)
    }

    private suspend fun creations(window: Window): UserCreations {
        val start = Date(window.startMs)
        val end = Date(window.endMs)
        var memories = 0L
        for (spaceId in UserPreferencesManager.getInstance(appContext).memorySpaceListFlow.first()) {
            val box = ObjectBoxManager.get(appContext, spaceId).boxFor(Memory::class.java)
            box.query(Memory_.createdAt.greaterOrEqual(start).and(Memory_.createdAt.less(end))).build().use { query ->
                memories += query.count()
            }
        }
        val cards =
            CharacterCardManager.getInstance(appContext).getCharacterCardCreatedAtRecords().values.count { createdAt ->
                createdAt != null && createdAt >= window.startMs && createdAt < window.endMs
            }
        val workflows =
            WorkflowRepository(appContext).getAllWorkflows().getOrThrow().count { workflow ->
                workflow.createdAt >= window.startMs && workflow.createdAt < window.endMs
            }
        return UserCreations(memories = memories, characterCards = cards.toLong(), workflows = workflows.toLong())
    }

    /** 连续有用户消息的最长天数；一天都没有时为 null。 */
    private fun longestStreak(sortedDates: List<LocalDate>): StreakRange? {
        if (sortedDates.isEmpty()) return null
        var bestStart = sortedDates.first()
        var bestEnd = bestStart
        var runStart = bestStart
        var previous = bestStart
        for (date in sortedDates.drop(1)) {
            if (previous.plusDays(1) != date) runStart = date
            previous = date
            if (ChronoUnit.DAYS.between(runStart, date) > ChronoUnit.DAYS.between(bestStart, bestEnd)) {
                bestStart = runStart
                bestEnd = date
            }
        }
        return StreakRange(days = ChronoUnit.DAYS.between(bestStart, bestEnd).toInt() + 1, start = bestStart.toString(), end = bestEnd.toString())
    }

    companion object {
        private const val TAG = "AnnualReportCollector"
        private const val TOP_MODELS = 3
        private const val TOP_TOOLS = 5
        private const val FIRST_MESSAGE_CHARS = 120

        // 用户消息里由 App 拼进去的结构化内容，不属于用户亲手写的话。
        private val USER_MARKUP =
            listOf(
                Regex("<attachment\\b[\\s\\S]*?</attachment>", RegexOption.IGNORE_CASE),
                Regex("<attachment\\b[^>]*/>", RegexOption.IGNORE_CASE),
                Regex("<memory>[\\s\\S]*?</memory>", RegexOption.IGNORE_CASE),
                Regex("<reply_to\\b[\\s\\S]*?</reply_to>", RegexOption.IGNORE_CASE),
                Regex("<proxy_sender\\b[^>]*/?>", RegexOption.IGNORE_CASE),
            )
        private val WHITESPACE = Regex("\\s+")

        internal fun cleanUserText(raw: String): String {
            var text = raw
            for (pattern in USER_MARKUP) text = pattern.replace(text, " ")
            return WHITESPACE.replace(text, " ").trim()
        }
    }
}
