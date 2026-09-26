package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.data.model.ContextSummarySettings
import com.ai.assistance.operit.data.model.SummarySectionOverride
import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 存量总结配置迁移的解析测试。
 *
 * 旧总结设置挂在 ModelConfigData 上，字段已从该类删除，迁移只能读原始 JSON 再手工映射。
 * 少映射一个字段，改过该配置的存量用户升级后就会静默退回默认值，所以逐字段校验。
 * 旧 JSON 里还带着大量与本迁移无关的模型配置键，测试用 ignoreUnknownKeys 覆盖这一点。
 */
class ContextSummarySettingsMigrationTest {

    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun setUp() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        // 解析失败分支会写日志；单元测试里 android.util.Log 未实现，必须关掉系统日志。
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun tearDown() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun `full legacy model config json maps every summary field`() {
        val legacyJson =
            """
            {
              "id": "custom-1",
              "name": "自定义配置",
              "apiKey": "sk-test",
              "modelName": "deepseek-chat",
              "enableSummary": false,
              "summaryTokenThreshold": 0.45,
              "enableSummaryByMessageCount": false,
              "summaryMessageCountThreshold": 7,
              "summaryCustomRules": "先列工程状态再列下一步",
              "summarySectionOverrides": [
                {
                  "id": "core_task",
                  "enabled": false,
                  "title": "工程状态",
                  "instruction": "简述工程状态"
                }
              ],
              "enableSummaryDialogueReview": false,
              "summaryDialogueReviewTitle": "对话回顾"
            }
            """.trimIndent()

        val settings = ContextSummarySettingsMigration.parseLegacySummarySettings(legacyJson)

        assertEquals(
            ContextSummarySettings(
                enableSummary = false,
                summaryTokenThreshold = 0.45f,
                enableSummaryByMessageCount = false,
                summaryMessageCountThreshold = 7,
                summaryCustomRules = "先列工程状态再列下一步",
                summarySectionOverrides =
                    listOf(
                        SummarySectionOverride(
                            id = "core_task",
                            enabled = false,
                            title = "工程状态",
                            instruction = "简述工程状态"
                        )
                    ),
                dialogueReviewEnabled = false,
                dialogueReviewTitle = "对话回顾"
            ),
            settings
        )
    }

    @Test
    fun `dialogue review fields are read from their legacy names`() {
        val legacyJson =
            """
            {
              "enableSummaryDialogueReview": false,
              "summaryDialogueReviewTitle": "复盘"
            }
            """.trimIndent()

        val settings = ContextSummarySettingsMigration.parseLegacySummarySettings(legacyJson)

        assertEquals(false, settings?.dialogueReviewEnabled)
        assertEquals("复盘", settings?.dialogueReviewTitle)
    }

    @Test
    fun `missing dialogue review fields fall back to legacy defaults`() {
        val legacyJson = """{ "summaryCustomRules": "只改规则" }"""

        val settings = ContextSummarySettingsMigration.parseLegacySummarySettings(legacyJson)

        assertEquals("只改规则", settings?.summaryCustomRules)
        assertEquals(true, settings?.dialogueReviewEnabled)
        assertEquals("", settings?.dialogueReviewTitle)
    }

    @Test
    fun `legacy json without any summary field yields default settings`() {
        val legacyJson =
            """
            {
              "id": "default",
              "name": "默认配置",
              "apiKey": "",
              "apiEndpoint": "https://example.invalid",
              "temperature": 0.7
            }
            """.trimIndent()

        val settings = ContextSummarySettingsMigration.parseLegacySummarySettings(legacyJson)

        assertEquals(ContextSummarySettings(), settings)
    }

    @Test
    fun `partially specified summary fields keep defaults for the rest`() {
        val legacyJson =
            """
            {
              "summaryTokenThreshold": 0.3,
              "summaryMessageCountThreshold": 4
            }
            """.trimIndent()

        val settings = ContextSummarySettingsMigration.parseLegacySummarySettings(legacyJson)

        assertEquals(
            ContextSummarySettings(
                summaryTokenThreshold = 0.3f,
                summaryMessageCountThreshold = 4
            ),
            settings
        )
    }

    @Test
    fun `section overrides pass through including null fields`() {
        val legacyJson =
            """
            {
              "summarySectionOverrides": [
                { "id": "core_task", "enabled": false },
                { "id": "next_step", "title": "下一步", "instruction": "列出下一步", "enabled": true },
                { "id": "untouched" }
              ]
            }
            """.trimIndent()

        val settings = ContextSummarySettingsMigration.parseLegacySummarySettings(legacyJson)

        assertEquals(
            listOf(
                SummarySectionOverride(id = "core_task", enabled = false),
                SummarySectionOverride(
                    id = "next_step",
                    enabled = true,
                    title = "下一步",
                    instruction = "列出下一步"
                ),
                SummarySectionOverride(id = "untouched")
            ),
            settings?.summarySectionOverrides
        )
    }

    @Test
    fun `empty overrides list stays empty`() {
        val legacyJson = """{ "summarySectionOverrides": [] }"""

        val settings = ContextSummarySettingsMigration.parseLegacySummarySettings(legacyJson)

        assertTrue(settings?.summarySectionOverrides?.isEmpty() == true)
    }

    @Test
    fun `absent blank or malformed json yields no migration`() {
        assertNull(ContextSummarySettingsMigration.parseLegacySummarySettings(null))
        assertNull(ContextSummarySettingsMigration.parseLegacySummarySettings(""))
        assertNull(ContextSummarySettingsMigration.parseLegacySummarySettings("   "))
        assertNull(ContextSummarySettingsMigration.parseLegacySummarySettings("{"))
        assertNull(ContextSummarySettingsMigration.parseLegacySummarySettings("[]"))
        assertNull(ContextSummarySettingsMigration.parseLegacySummarySettings("\"just a string\""))
    }
}