package com.ai.assistance.operit.ui.main.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.support.about.screens.AboutScreen
import com.ai.assistance.operit.ui.features.conversation.chat.screens.AIChatScreen
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalMainChatViewModel
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalRunMainChatAction
import com.ai.assistance.operit.ui.features.support.demo.screens.ShizukuDemoScreen
import com.ai.assistance.operit.ui.features.support.help.screens.HelpScreen
import com.ai.assistance.operit.ui.features.conversation.memory.screens.MemoryScreen
import com.ai.assistance.operit.ui.features.conversation.memory.screens.MemoryOverviewScreen

import com.ai.assistance.operit.ui.features.extensions.packages.screens.MarketHomeTab
import com.ai.assistance.operit.ui.features.extensions.packages.screens.PackageManagerScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.ArtifactPublishScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.RepoMarketPublishScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.UnifiedMarketManageScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.UnifiedMarketCategoryScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.UnifiedMarketAuthorScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.UnifiedMarketDetailEntryScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.UnifiedMarketNotificationsScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.UnifiedMarketScreen
import com.ai.assistance.operit.ui.features.extensions.packages.screens.marketCategoryLabel
import com.ai.assistance.operit.ui.features.extensions.packages.screens.toArtifactPublishClusterContext
import com.ai.assistance.operit.ui.features.extensions.packages.market.ArtifactPublishClusterContext
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.extensions.packages.market.PluginCreationIntent
import com.ai.assistance.operit.ui.features.management.databackup.DataAndBackupScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.ExternalHttpChatSettingsScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.GlobalDisplaySettingsScreen
import com.ai.assistance.operit.ui.features.management.performance.PerformanceMonitorScreen
import com.ai.assistance.operit.ui.features.extensions.packages.market.account.GitHubAccountScreen

import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceTab
import com.ai.assistance.operit.ui.features.management.settings.screens.ModelConfigScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.ModelPromptsSettingsScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.TagMarketScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.SettingsScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.SpeechServicesSettingsScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.ThemeSettingsScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.ToolPermissionSettingsScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.MnnModelDownloadScreen
import com.ai.assistance.operit.ui.features.management.settings.profile.UserProfileManagementScreen
import com.ai.assistance.operit.data.storage.StorageCategory
import com.ai.assistance.operit.ui.features.management.storage.BackupExportStorageScreen
import com.ai.assistance.operit.ui.features.management.storage.ChatHistoryStorageScreen
import com.ai.assistance.operit.ui.features.management.storage.ConfigurationResourceStorageScreen
import com.ai.assistance.operit.ui.features.management.storage.DataStorageScreen
import com.ai.assistance.operit.ui.features.management.storage.LinuxEnvironmentStorageScreen
import com.ai.assistance.operit.ui.features.management.storage.LocalModelStorageScreen
import com.ai.assistance.operit.ui.features.management.storage.MemoryLibraryStorageScreen
import com.ai.assistance.operit.ui.features.management.storage.WorkspaceMediaStorageScreen
import com.ai.assistance.operit.ui.features.models.tokenstats.TokenUsageStatisticsScreen
import com.ai.assistance.operit.ui.features.models.token.TokenConfigWebViewScreen
import com.ai.assistance.operit.ui.features.support.demo.markdown.StreamMarkdownDemoScreen
import com.ai.assistance.operit.ui.main.screens.TerminalAutoConfigToolScreen
import com.ai.assistance.operit.ui.features.automation.terminal.EmbeddedTerminalPage
import com.ai.assistance.operit.ui.features.automation.terminal.TerminalEntryScreen
import com.ai.assistance.operit.ui.common.composedsl.ToolPkgComposeDslToolScreen
import com.ai.assistance.operit.ui.main.screens.UIDebuggerToolScreen
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.htmlpackager.HtmlPackagerScreen
import com.ai.assistance.operit.ui.features.management.settings.speech.stt.SpeechToTextToolScreen
import com.ai.assistance.operit.ui.features.management.update.screens.UpdateScreen
import com.ai.assistance.operit.ui.features.automation.workflow.screens.WorkflowListScreen
import com.ai.assistance.operit.ui.features.automation.workflow.screens.WorkflowDetailScreen
import com.ai.assistance.operit.ui.main.PendingChatDraftHandler
import com.ai.assistance.operit.ui.main.navigation.AppRouterGateway

// 路由配置类
typealias ScreenNavigationHandler = (Screen) -> Unit

// 重构的Screen类，添加了路由相关属性和内容渲染函数
sealed class Screen(
        // 对应的导航项，仅用于侧边栏高亮归属。
        // 它不再承担“这个 nav item 默认打开哪个页面”的职责。
        open val navItem: NavItem? = null,
        // 屏幕标题资源ID
        open val titleRes: Int? = null,
        // 是否参与 AppContent 的跨页淡入淡出。
        // 某些包含实时渲染视图的页面在转场中保留上一页会产生明显残影。
        open val participatesInCrossfadeTransition: Boolean = true,
        open val keepAlive: Boolean = false,
        /**
         * 是否使用路由级 ViewModelStore（AppContent 按 screenKey 通过
         * [com.ai.assistance.operit.ui.main.navigation.ScreenRouteViewModelStoreOwnerManager]
         * 管理）：配置变化保留实例，路由出栈/替换/清栈时 store.clear() 触发
         * onCleared（viewModelScope 取消）。
         * 默认 false 保持 Activity 级语义，防止影响其他页面。
         */
        open val usesRouteViewModelStore: Boolean = false
) {
    open fun stableScreenKey(): String? = null

    /**
     * AppContent 使用的屏幕键：keepAlive 屏幕用 [stableScreenKey]（同路由
     * 实例复用），否则用路由实例 id（每次进入独立）。
     */
    fun screenKey(routeInstanceId: String): String =
        if (keepAlive) stableScreenKey() ?: routeInstanceId else routeInstanceId

    // 屏幕内容渲染函数
    @Composable
    open fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
    ) {
        // 子类实现具体内容
    }

    // Main screens (primary)
    // 聊天内容离页立即退出绘制，避免透明设置页和插件页透出聊天记录。
    data object AiChat : Screen(
        navItem = NavItem.AiChat,
        participatesInCrossfadeTransition = false,
    ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AIChatScreen(
                    padding = PaddingValues(0.dp),
                    viewModel = LocalMainChatViewModel.current,
                    isFloatingMode = false,
                    hasBackgroundImage = hasBackgroundImage,
                    onNavigateToSettings = { navigateTo(Settings) },
                    onNavigateToMemoryBase = { navigateTo(MemoryBase) },
                    onNavigateToModelConfig = { navigateTo(ModelConfig) },
                    onNavigateToModelPrompts = { navigateTo(ModelPromptsSettings) },
                    onNavigateToPackageManager = { navigateTo(Packages) },
                    onNavigateToHtmlPackager = { navigateTo(HtmlPackager) },
                    onLoading = onLoading,
                    onError = onError,
                    onGestureConsumed = onGestureConsumed
            )
        }
    }
    data object MemoryBase : Screen(navItem = NavItem.Settings, titleRes = R.string.memory_management_title) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MemoryOverviewScreen(onOpenSpace = { id -> navigateTo(MemorySpaceDetail(id)) }, onOpenRecent = { id, uuid -> navigateTo(MemorySpaceDetail(id, startInList = true, memoryUuid = uuid)) })
        }
    }

    data class MemorySpaceDetail(val spaceId: String, val startInList: Boolean = false, val memoryUuid: String? = null) :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_memory_base) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MemoryScreen(spaceId = spaceId, onConfigureEmbedding = { navigateTo(FunctionalConfig) }, initialList = startInList, initialMemoryUuid = memoryUuid)
        }
    }

    data object Packages : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            val context = LocalContext.current
            PackageManagerScreen(
                onNavigateToMCPMarket = { navigateTo(Market(MarketHomeTab.ALL)) },
                onNavigateToSkillMarket = { navigateTo(Market(MarketHomeTab.ALL)) },
                onNavigateToArtifactMarket = { navigateTo(Market(MarketHomeTab.ALL)) },
                onOpenMarketDetail = { entry ->
                    navigateTo(MarketEntryDetail(entry = entry))
                },
                onStartPluginCreation = { intent ->
                    PendingChatDraftHandler.setPendingDraft(intent.toPrompt(context))
                    navigateTo(AiChat)
                },
                onOpenToolPkgPluginConfig = { containerPackageName, uiModuleId, title, keepAlive ->
                    navigateTo(
                        ToolPkgPluginConfig(
                            containerPackageName = containerPackageName,
                            uiModuleId = uiModuleId,
                            title = title,
                            keepAlive = keepAlive
                        )
                    )
                }
            )
        }
    }

    data class Market(val initialTab: MarketHomeTab = MarketHomeTab.ALL) :
            Screen(navItem = NavItem.Packages, titleRes = R.string.screen_title_market) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UnifiedMarketScreen(
                initialTab = initialTab,
                onNavigateToArtifactPublish = { navigateTo(ArtifactPublish) },
                onNavigateToRepoPublish = { type -> navigateTo(RepoPublish(type)) },
                onNavigateToMarketManage = { navigateTo(MarketManage) },
                onNavigateToDetail = { issue ->
                    navigateTo(MarketEntryDetail(issue))
                },
                onNavigateToCategory = { categoryId ->
                    navigateTo(MarketCategory(categoryId))
                },
                onNavigateToNotifications = {
                    navigateTo(MarketNotifications)
                }
            )
        }
    }

    data class MarketCategory(val categoryId: String) :
            Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UnifiedMarketCategoryScreen(
                categoryId = categoryId,
                onNavigateToArtifactDetail = { issue ->
                    navigateTo(MarketEntryDetail(issue))
                },
                onNavigateToSkillDetail = { issue ->
                    navigateTo(MarketEntryDetail(issue))
                },
                onNavigateToMcpDetail = { issue ->
                    navigateTo(MarketEntryDetail(issue))
                }
            )
        }

        @Composable
        override fun getTitle(): String = marketCategoryLabel(categoryId)
    }

    data object MarketNotifications :
            Screen(navItem = NavItem.Packages, titleRes = R.string.market_notifications_title) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UnifiedMarketNotificationsScreen()
        }
    }

    data class MarketEntryDetail(
        val entry: com.ai.assistance.operit.data.api.MarketV2Entry,
        val fromManage: Boolean = false
    ) : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UnifiedMarketDetailEntryScreen(
                initialEntry = entry,
                fromManage = fromManage,
                onNavigateBack = onGoBack,
                onPublishNewVersion = { target ->
                    when (target.type.lowercase()) {
                        "script", "package" -> navigateTo(ArtifactContinuePublish(target.toArtifactPublishClusterContext()))
                        "skill" -> navigateTo(RepoPublishVersion(MarketStatsType.SKILL, target))
                        "mcp" -> navigateTo(RepoPublishVersion(MarketStatsType.MCP, target))
                    }
                },
                onNavigateToAuthor = { authorId, authorLogin, authorAvatarUrl ->
                    navigateTo(
                        MarketAuthor(
                            authorId = authorId,
                            authorLogin = authorLogin,
                            authorAvatarUrl = authorAvatarUrl
                        )
                    )
                }
            )
        }
    }

    data class MarketAuthor(
        val authorId: String,
        val authorLogin: String,
        val authorAvatarUrl: String
    ) : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UnifiedMarketAuthorScreen(
                authorId = authorId,
                authorLogin = authorLogin,
                authorAvatarUrl = authorAvatarUrl,
                onNavigateToDetail = { issue ->
                    navigateTo(MarketEntryDetail(issue))
                }
            )
        }

        @Composable
        override fun getTitle(): String =
            authorLogin.ifBlank { authorId.removePrefix("gh_") }
    }

    data object MarketManage : Screen(navItem = NavItem.Packages, titleRes = R.string.market_section_manage) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UnifiedMarketManageScreen(
                onNavigateToGitHubAccount = { navigateTo(GitHubAccount) },
                onNavigateToEditArtifact = { issue ->
                    navigateTo(ArtifactEdit(issue))
                },
                onNavigateToEditRepo = { type, issue ->
                    navigateTo(RepoEdit(type, issue))
                },
                onNavigateToPublishArtifactVersion = { issue, canEditEntry ->
                    navigateTo(ArtifactContinuePublish(issue.toArtifactPublishClusterContext(canEditEntry)))
                },
                onNavigateToPublishRepoVersion = { type, issue, canEditEntry ->
                    navigateTo(RepoPublishVersion(type, issue, canEditEntry))
                },
                onNavigateToPublishArtifact = { navigateTo(ArtifactPublish) },
                onNavigateToPublishRepo = { type -> navigateTo(RepoPublish(type)) },
                onNavigateToDetail = { issue ->
                    navigateTo(MarketEntryDetail(issue, fromManage = true))
                }
            )
        }
    }

    data object ArtifactPublish : Screen(navItem = NavItem.Packages, titleRes = R.string.screen_title_artifact_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ArtifactPublishScreen(onNavigateBack = onGoBack)
        }
    }

    data class ArtifactContinuePublish(
        val publishContext: ArtifactPublishClusterContext
    ) : Screen(navItem = NavItem.Packages, titleRes = R.string.screen_title_artifact_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ArtifactPublishScreen(
                onNavigateBack = onGoBack,
                publishContext = publishContext
            )
        }
    }

    data class ArtifactEdit(val editingEntry: com.ai.assistance.operit.data.api.MarketV2Entry) :
            Screen(navItem = NavItem.Packages, titleRes = R.string.screen_title_artifact_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ArtifactPublishScreen(
                onNavigateBack = onGoBack,
                editingEntry = editingEntry
            )
        }
    }

    data class RepoPublish(val type: MarketStatsType) : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            RepoMarketPublishScreen(
                type = type,
                onNavigateBack = onGoBack
            )
        }

        @Composable
        override fun getTitle(): String {
            return stringResource(
                if (type == MarketStatsType.SKILL) {
                    R.string.screen_title_skill_publish
                } else {
                    R.string.screen_title_mcp_publish
                }
            )
        }
    }

    data class RepoEdit(
        val type: MarketStatsType,
        val editingEntry: com.ai.assistance.operit.data.api.MarketV2Entry
    ) : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            RepoMarketPublishScreen(
                type = type,
                onNavigateBack = onGoBack,
                editingEntry = editingEntry
            )
        }

        @Composable
        override fun getTitle(): String {
            return stringResource(
                if (type == MarketStatsType.SKILL) {
                    R.string.screen_title_skill_publish
                } else {
                    R.string.screen_title_mcp_publish
                }
            )
        }
    }

    data class RepoPublishVersion(
        val type: MarketStatsType,
        val editingEntry: com.ai.assistance.operit.data.api.MarketV2Entry,
        val canEditEntry: Boolean = false
    ) : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            RepoMarketPublishScreen(
                type = type,
                onNavigateBack = onGoBack,
                editingEntry = editingEntry,
                publishVersionOnly = true,
                canEditEntry = canEditEntry
            )
        }

        @Composable
        override fun getTitle(): String {
            return stringResource(R.string.market_publish_new_version)
        }
    }

    data object ShizukuCommands : Screen(navItem = NavItem.ShizukuCommands) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ShizukuDemoScreen(navigateTo = navigateTo)
        }
    }

    data object Settings : Screen(navItem = NavItem.Settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SettingsScreen(
                    navigateToToolPermissions = { navigateTo(ToolPermission) },
                    navigateToModelConfig = { navigateTo(ModelConfig) },
                    navigateToThemeSettings = { navigateTo(ThemeSettings) },
                    navigateToGlobalDisplaySettings = { navigateTo(GlobalDisplaySettings) },
                    navigateToModelPrompts = { navigateTo(ModelPromptsSettings) },
                    navigateToMemory = { navigateTo(MemoryBase) },
                    navigateToDataStorageManagement = { navigateTo(DataStorageManagement) },
                    navigateToDataAndBackup = { navigateTo(ChatBackupSettings) },

                    navigateToSpeechServicesSettings = { navigateTo(SpeechServicesSettings) },
                    navigateToExternalHttpChatSettings = { navigateTo(ExternalHttpChatSettings) },
                    navigateToPerformanceMonitor = { navigateTo(PerformanceMonitor) },
                    navigateToAbout = { navigateTo(About) }
            )
        }
    }

    data object GitHubAccount : Screen(navItem = NavItem.Packages, titleRes = R.string.github_account) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            GitHubAccountScreen()
        }
    }

    data object Help : Screen(navItem = NavItem.Settings, titleRes = R.string.nav_help) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            HelpScreen(onBackPressed = onGoBack)
        }
    }

    data object About : Screen(navItem = NavItem.Settings, titleRes = R.string.nav_about) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AboutScreen(
                navigateToUpdateHistory = {
                    navigateTo(UpdateHistory)
                },
                navigateToHelp = { navigateTo(Help) },
                navigateToAgreement = { navigateTo(Agreement) }
            )
        }
    }

    data object Agreement : Screen(navItem = NavItem.Settings, titleRes = R.string.tool_user_agreement) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.support.agreement.screens.AgreementScreen()
        }
    }

    data object UpdateHistory :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.update_history
            ) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            UpdateScreen(onNavigateToThemeSettings = { navigateTo(ThemeSettings) })
        }
    }

    data object Workflow : Screen(navItem = NavItem.Workflow) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            WorkflowListScreen(
                onNavigateToDetail = { workflowId ->
                    navigateTo(WorkflowDetail(workflowId))
                }
            )
        }
    }

    data class WorkflowDetail(val workflowId: String) : Screen(navItem = NavItem.Workflow, titleRes = R.string.nav_workflow) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            WorkflowDetailScreen(
                workflowId = workflowId,
                onNavigateBack = onGoBack
            )
        }
    }

    data object TokenConfig : Screen(navItem = NavItem.TokenConfig) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TokenConfigWebViewScreen(onNavigateBack = onGoBack)
        }
    }

    // Secondary screens - Settings
    data class UserPreferencesSettings(val initialProfileId: String? = null) :
            Screen(navItem = NavItem.Settings, titleRes = R.string.settings_user_preferences) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UserProfileManagementScreen(
                onNavigateBack = onGoBack,
                onNavigateToMemory = { id -> navigateTo(MemorySpaceDetail(id)) },
                initialProfileId = initialProfileId
            )
        }
    }

    data object ToolPermission :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_tool_permissions) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolPermissionSettingsScreen(navigateBack = onGoBack)
        }
    }

    data object ModelConfig :
            Screen(navItem = NavItem.Settings, titleRes = R.string.model_workspace_title) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelConfigScreen(
                navigateToMnnModelDownload = { navigateTo(MnnModelDownload) }
            )
        }
    }

    // 添加SpeechServicesSettings屏幕定义
    data object SpeechServicesSettings :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_speech_services_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SpeechServicesSettingsScreen(
                onBackPressed = onGoBack
            )
        }
    }
    
    data object ExternalHttpChatSettings :
        Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_external_http_chat_settings) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ExternalHttpChatSettingsScreen(onBackPressed = onGoBack)
        }
    }
    
    // MNN模型下载屏幕
    data object MnnModelDownload :
        Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_mnn_model_download) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            MnnModelDownloadScreen(onBackPressed = onGoBack)
        }
    }
    
    // 新增：人设卡生成页面
    data object PersonaCardGeneration :
        Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_persona_card_generation) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.management.settings.screens.PersonaCardGenerationScreen(
                onNavigateToSettings = { navigateTo(Settings) },
                onNavigateToModelConfig = { navigateTo(ModelConfig) },
                onNavigateToModelPrompts = { navigateTo(ModelPromptsSettings) }
            )
        }
    }

    // 新增：Waifu模式设置页面
    data object WaifuModeSettings :
        Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_waifu_mode_settings) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.management.settings.screens.WaifuModeSettingsScreen(
                onNavigateBack = onGoBack,
                onNavigateToCustomEmoji = { navigateTo(CustomEmojiManagement) }
            )
        }
    }
    
    // 自定义表情管理页面
    data object CustomEmojiManagement :
        Screen(navItem = NavItem.Settings, titleRes = R.string.manage_custom_emoji) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.management.settings.screens.CustomEmojiManagementScreen(
                onNavigateBack = onGoBack
            )
        }
    }
    
    data object TagMarket :
        Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_tag_market) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TagMarketScreen(onBackPressed = onGoBack)
        }
    }

    data object ModelPromptsSettings :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_model_prompts_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelPromptsSettingsScreen(
                onBackPressed = onGoBack,
                onNavigateToMarket = { navigateTo(TagMarket) },
                onNavigateToPersonaGeneration = { navigateTo(PersonaCardGeneration) },
                onNavigateToChatManagement = { navigateTo(ChatHistorySettings) },
                onNavigateToUserProfiles = { id -> navigateTo(UserPreferencesSettings(id)) }
            )
        }
    }

    data object FunctionalConfig :
            Screen(navItem = NavItem.Settings, titleRes = R.string.model_workspace_title) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelConfigScreen(
                navigateToMnnModelDownload = { navigateTo(MnnModelDownload) },
                initialTab = ModelWorkspaceTab.FUNCTIONS,
            )
        }
    }

    data object ThemeSettings :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_theme_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ThemeSettingsScreen()
        }
    }

    data object GlobalDisplaySettings :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_global_display_settings,
                    // 本页自带标题栏，跨页淡出会让旧页面按已移除的宿主栏留白重新排版。
                    participatesInCrossfadeTransition = false
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            GlobalDisplaySettingsScreen(onBackPressed = onGoBack)
        }
    }

    data object DataStorageManagement :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_data_storage_management,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            DataStorageScreen(
                    onManageCategory = { category ->
                        when (category) {
                            StorageCategory.LINUX_ENVIRONMENT -> navigateTo(LinuxEnvironmentStorage)
                            StorageCategory.LOCAL_MODELS -> navigateTo(LocalModelStorage)
                            StorageCategory.WORKSPACES_AND_MEDIA -> navigateTo(WorkspaceMediaStorage)
                            StorageCategory.CHAT_HISTORY -> navigateTo(ChatHistoryStorage)
                            StorageCategory.MEMORY_LIBRARY -> navigateTo(MemoryLibraryStorage)
                            StorageCategory.BACKUPS_AND_EXPORTS -> navigateTo(BackupExportStorage)
                            StorageCategory.CONFIGURATION,
                            StorageCategory.PACKAGES_AND_PLUGINS -> navigateTo(ConfigurationResourceStorage)
                            StorageCategory.CACHE_AND_TEMPORARY,
                            StorageCategory.OTHER -> Unit
                        }
                    }
            )
        }
    }

    data object LocalModelStorage :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_local_model_storage,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            LocalModelStorageScreen(
                onDownloadMnn = { navigateTo(MnnModelDownload) },
            )
        }
    }

    data object LinuxEnvironmentStorage :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_linux_environment_storage,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            LinuxEnvironmentStorageScreen()
        }
    }

    data object WorkspaceMediaStorage :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_workspace_media_storage,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            WorkspaceMediaStorageScreen()
        }
    }

    data object ChatHistoryStorage :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_chat_history_storage,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ChatHistoryStorageScreen()
        }
    }

    data object MemoryLibraryStorage :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_memory_library_storage,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MemoryLibraryStorageScreen()
        }
    }

    data object BackupExportStorage :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_backup_export_storage,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            BackupExportStorageScreen()
        }
    }

    data object ConfigurationResourceStorage :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_configuration_resource_storage,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ConfigurationResourceStorageScreen()
        }
    }

    data object ChatHistorySettings :
            Screen(navItem = NavItem.Settings, titleRes = R.string.data_backup_title, usesRouteViewModelStore = true) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            DataAndBackupScreen(initialTab = 1, onNavigateToTokenStatistics = { navigateTo(TokenUsageStatistics) })
        }
    }

    data object ChatBackupSettings :
            Screen(navItem = NavItem.Settings, titleRes = R.string.data_backup_title, usesRouteViewModelStore = true) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            DataAndBackupScreen(onNavigateToTokenStatistics = { navigateTo(TokenUsageStatistics) })
        }
    }

    // 保留已发布的语言路由标识，入口统一显示含语言下拉菜单的主题页。
    data object LanguageSettings :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_theme_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ThemeSettingsScreen()
        }
    }

    data object TokenUsageStatistics :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.settings_token_usage_stats,
                    usesRouteViewModelStore = true
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TokenUsageStatisticsScreen(
                    onBackPressed = onGoBack,
            )
        }
    }

    data object PerformanceMonitor :
            Screen(
                    navItem = NavItem.Settings,
                    titleRes = R.string.screen_title_performance_monitor
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            PerformanceMonitorScreen()
        }
    }

    data object ContextSummarySettings :
            Screen(navItem = NavItem.Settings, titleRes = R.string.model_workspace_title) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelConfigScreen(
                navigateToMnnModelDownload = { navigateTo(MnnModelDownload) },
                initialTab = ModelWorkspaceTab.SUMMARY,
            )
        }
    }

    data class ToolPkgComposeDsl(
        val containerPackageName: String,
        val uiModuleId: String,
        val title: String,
        override val keepAlive: Boolean = false
    ) : Screen() {
        override fun stableScreenKey(): String? =
            "toolpkg_keepalive:$containerPackageName:$uiModuleId"

        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolPkgComposeDslToolScreen(
                navController = navController,
                routeInstanceId = "screen:$containerPackageName:$uiModuleId",
                containerPackageName = containerPackageName,
                uiModuleId = uiModuleId,
                fallbackTitle = title
            )
        }

        @Composable
        override fun getTitle(): String = title
    }

    data class ToolPkgPluginConfig(
        val containerPackageName: String,
        val uiModuleId: String,
        val title: String,
        override val keepAlive: Boolean = false
    ) : Screen(navItem = NavItem.Packages) {
        override fun stableScreenKey(): String? =
            "toolpkg_keepalive:$containerPackageName:$uiModuleId"

        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolPkgComposeDslToolScreen(
                navController = navController,
                routeInstanceId = "legacy:$containerPackageName:$uiModuleId",
                containerPackageName = containerPackageName,
                uiModuleId = uiModuleId,
                fallbackTitle = title
            )
        }

        @Composable
        override fun getTitle(): String = title
    }

    data object Terminal :
            Screen(navItem = NavItem.Terminal, titleRes = R.string.screen_title_terminal) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            val viewModel = LocalMainChatViewModel.current
            val runMainChatAction = LocalRunMainChatAction.current
            TerminalEntryScreen(
                initialPage = EmbeddedTerminalPage.CONSOLE,
                onOpenTerminal = { page ->
                    runMainChatAction { viewModel.openTerminalFromHub(page) }
                },
                onGoBack = onGoBack,
                onError = onError,
            )
        }
    }

    data object TerminalSetup :
            Screen(navItem = NavItem.Terminal, titleRes = R.string.screen_title_terminal) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            val viewModel = LocalMainChatViewModel.current
            val runMainChatAction = LocalRunMainChatAction.current
            TerminalEntryScreen(
                initialPage = EmbeddedTerminalPage.SETUP,
                onOpenTerminal = { page ->
                    runMainChatAction { viewModel.openTerminalFromHub(page) }
                },
                onGoBack = onGoBack,
                onError = onError,
            )
        }
    }

    data object TerminalAutoConfig :
            Screen(navItem = NavItem.Terminal, titleRes = R.string.screen_title_terminal_auto_config) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TerminalAutoConfigToolScreen(navController = navController)
        }
    }

    data object UIDebugger :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_ui_debugger) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UIDebuggerToolScreen(navController = navController)
        }
    }

    data object Logcat :
            Screen(navItem = NavItem.Settings, titleRes = R.string.data_backup_title, usesRouteViewModelStore = true) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            DataAndBackupScreen(initialTab = 2, onNavigateToTokenStatistics = { navigateTo(TokenUsageStatistics) })
        }
    }

    // 流式Markdown演示屏幕
    data object MarkdownDemo :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_markdown_demo) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            StreamMarkdownDemoScreen(onBackClick = onGoBack)
        }
    }

    // Tools screens
    data object SpeechToText :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_speech_to_text) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SpeechToTextToolScreen(navController = navController)
        }
    }

    data object DefaultAssistantGuide :
            Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_default_assistant_guide) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SpeechServicesSettingsScreen(
                onBackPressed = onGoBack,
                initialTabIndex = 2,
            )
        }
    }


    data object HtmlPackager : Screen(navItem = NavItem.AiChat, titleRes = R.string.screen_title_html_packager) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            HtmlPackagerScreen(onGoBack = onGoBack)
        }
    }

    // 获取屏幕标题
    @Composable
    open fun getTitle(): String = titleRes?.let { stringResource(it) } ?: ""
}

// 全局的手势状态持有者，用于在不同组件间共享手势状态
object GestureStateHolder {
    // 聊天界面手势是否被消费的状态
    var isChatScreenGestureConsumed: Boolean = false
}
