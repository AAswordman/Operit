package com.ai.assistance.operit.ui.main.screens

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.RouteRuntime
import com.ai.assistance.operit.ui.main.navigation.RouteSpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

private const val INTERNAL_NATIVE_SCREEN_KEY = "__native_screen"

private object Unconvertible

private data class HostNavigationEntryDefinition(
    val entryId: String,
    val screen: Screen,
    val surface: NavigationSurface? = null,
    val launchNavItem: NavItem? = null,
    @StringRes val titleResId: Int? = null,
    @StringRes val descriptionResId: Int? = null,
    val icon: ImageVector? = null,
    val order: Int = 0
)

private fun camelToSnakeCase(name: String): String {
    return name
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("([a-z\\d])([A-Z])"), "$1_$2")
        .lowercase()
}

private fun nativeRouteIdForTypeName(typeName: String): String {
    return "native.${camelToSnakeCase(typeName)}"
}

private fun routeIdForClass(type: Class<out Screen>): String {
    return nativeRouteIdForTypeName(type.simpleName)
}

private data class ScreenRouteReflector(
    val constructor: KFunction<Screen>,
    val valueParameters: List<KParameter>
)

private data class ScreenArgExtractor(
    val valueParameters: List<KParameter>,
    val propertiesByName: Map<String, KProperty1<Any, *>>
)

private fun Class<out Screen>.isKotlinObjectClass(): Boolean {
    return declaredFields.any { field ->
        field.name == "INSTANCE" && field.type == this
    }
}

object ScreenRouteRegistry {
    private val screenTypes: List<Class<out Screen>> =
        Screen::class.java.declaredClasses
            .mapNotNull { declaredClass ->
                if (!Screen::class.java.isAssignableFrom(declaredClass)) {
                    null
                } else {
                    declaredClass.asSubclass(Screen::class.java)
                }
            }
            .sortedBy { it.simpleName }

    private val routeTypeById: Map<String, Class<out Screen>> =
        screenTypes.associateBy(::routeIdForClass)

    private val nativeRouteIds: List<String> = routeTypeById.keys.sorted()
    private val objectScreensByRouteId = ConcurrentHashMap<String, Screen>()
    private val routeReflectorsById = ConcurrentHashMap<String, ScreenRouteReflector>()
    private val argExtractorsByClass = ConcurrentHashMap<Class<out Screen>, ScreenArgExtractor>()

    // Host navigation is declared in one place.
    // `surface != null` means the entry is visible in navigation UI.
    // `launchNavItem != null` means the nav item can use this screen as its default entry.
    private val hostEntryDefinitions: List<HostNavigationEntryDefinition> =
        listOf(
            hostEntryDefinition(
                entryId = "main.ai_chat",
                screen = Screen.AiChat,
                surface = NavigationSurface.MAIN_SIDEBAR_AI,
                launchNavItem = NavItem.AiChat,
                icon = NavItem.AiChat.icon,
                order = 10
            ),
            hostEntryDefinition(
                entryId = "main.assistant_config",
                screen = Screen.AssistantConfig,
                surface = NavigationSurface.MAIN_SIDEBAR_AI,
                launchNavItem = NavItem.AssistantConfig,
                icon = NavItem.AssistantConfig.icon,
                order = 20
            ),
            hostEntryDefinition(
                entryId = "main.memory_base",
                screen = Screen.MemoryBase,
                surface = NavigationSurface.MAIN_SIDEBAR_AI,
                launchNavItem = NavItem.MemoryBase,
                icon = NavItem.MemoryBase.icon,
                order = 30
            ),
            hostEntryDefinition(
                entryId = "main.packages",
                screen = Screen.Packages,
                surface = NavigationSurface.MAIN_SIDEBAR_TOOLS,
                launchNavItem = NavItem.Packages,
                icon = NavItem.Packages.icon,
                order = 10
            ),
            hostEntryDefinition(
                entryId = "main.shizuku_commands",
                screen = Screen.ShizukuCommands,
                surface = NavigationSurface.MAIN_SIDEBAR_TOOLS,
                launchNavItem = NavItem.ShizukuCommands,
                icon = NavItem.ShizukuCommands.icon,
                order = 20
            ),
            hostEntryDefinition(
                entryId = "main.workflow",
                screen = Screen.Workflow,
                surface = NavigationSurface.MAIN_SIDEBAR_TOOLS,
                launchNavItem = NavItem.Workflow,
                icon = NavItem.Workflow.icon,
                order = 30
            ),
            hostEntryDefinition(
                entryId = "main.settings",
                screen = Screen.Settings,
                surface = NavigationSurface.MAIN_SIDEBAR_SYSTEM,
                launchNavItem = NavItem.Settings,
                icon = NavItem.Settings.icon,
                order = 10
            ),
            hostEntryDefinition(
                entryId = "main.help",
                screen = Screen.Help,
                surface = NavigationSurface.MAIN_SIDEBAR_SYSTEM,
                launchNavItem = NavItem.Help,
                icon = NavItem.Help.icon,
                order = 20
            ),
            hostEntryDefinition(
                entryId = "main.about",
                screen = Screen.About,
                surface = NavigationSurface.MAIN_SIDEBAR_SYSTEM,
                launchNavItem = NavItem.About,
                icon = NavItem.About.icon,
                order = 30
            ),
            hostEntryDefinition(
                entryId = "toolbox.tool_tester",
                screen = Screen.ToolTester,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_test_center,
                descriptionResId = R.string.tool_test_center_desc,
                icon = Icons.Default.Build,
                order = 10
            ),
            hostEntryDefinition(
                entryId = "toolbox.file_manager",
                screen = Screen.FileManager,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_file_manager,
                descriptionResId = R.string.tool_file_manager_desc,
                icon = Icons.Default.Folder,
                order = 20
            ),
            hostEntryDefinition(
                entryId = "toolbox.text_to_speech",
                screen = Screen.TextToSpeech,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_tts,
                descriptionResId = R.string.tool_tts_desc,
                icon = Icons.Default.RecordVoiceOver,
                order = 30
            ),
            hostEntryDefinition(
                entryId = "toolbox.speech_to_text",
                screen = Screen.SpeechToText,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_speech_recognition,
                descriptionResId = R.string.tool_speech_recognition_desc,
                icon = Icons.Default.Mic,
                order = 40
            ),
            hostEntryDefinition(
                entryId = "toolbox.app_permissions",
                screen = Screen.AppPermissions,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_permission_manager,
                descriptionResId = R.string.tool_permission_manager_desc,
                icon = Icons.Default.Security,
                order = 50
            ),
            hostEntryDefinition(
                entryId = "toolbox.agreement",
                screen = Screen.Agreement,
                surface = NavigationSurface.TOOLBOX,
                launchNavItem = NavItem.Agreement,
                titleResId = R.string.tool_user_agreement,
                descriptionResId = R.string.tool_user_agreement_desc,
                icon = Icons.Default.Policy,
                order = 60
            ),
            hostEntryDefinition(
                entryId = "toolbox.default_assistant_guide",
                screen = Screen.DefaultAssistantGuide,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_default_assistant_guide,
                descriptionResId = R.string.tool_default_assistant_guide_desc,
                icon = Icons.Default.SmartToy,
                order = 70
            ),
            hostEntryDefinition(
                entryId = "toolbox.terminal",
                screen = Screen.Terminal,
                surface = NavigationSurface.TOOLBOX,
                launchNavItem = NavItem.Terminal,
                titleResId = R.string.tool_terminal,
                descriptionResId = R.string.tool_terminal_desc,
                icon = Icons.Default.Terminal,
                order = 80
            ),
            hostEntryDefinition(
                entryId = "toolbox.ui_debugger",
                screen = Screen.UIDebugger,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_ui_debugger,
                descriptionResId = R.string.tool_ui_debugger_desc,
                icon = Icons.Default.DeviceHub,
                order = 90
            ),
            hostEntryDefinition(
                entryId = "toolbox.ffmpeg_toolbox",
                screen = Screen.FFmpegToolbox,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_ffmpeg_toolbox,
                descriptionResId = R.string.tool_ffmpeg_toolbox_desc,
                icon = Icons.Default.VideoSettings,
                order = 100
            ),
            hostEntryDefinition(
                entryId = "toolbox.shell_executor",
                screen = Screen.ShellExecutor,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_shell_executor,
                descriptionResId = R.string.tool_shell_executor_desc,
                icon = Icons.Default.Code,
                order = 110
            ),
            hostEntryDefinition(
                entryId = "toolbox.logcat",
                screen = Screen.Logcat,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_log_viewer,
                descriptionResId = R.string.tool_log_viewer_desc,
                icon = Icons.Default.DataObject,
                order = 120
            ),
            hostEntryDefinition(
                entryId = "toolbox.sql_viewer",
                screen = Screen.SqlViewer,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_sql_viewer,
                descriptionResId = R.string.tool_sql_viewer_desc,
                icon = Icons.Default.TableView,
                order = 130
            ),
            hostEntryDefinition(
                entryId = "toolbox.token_config",
                screen = Screen.TokenConfig,
                surface = NavigationSurface.TOOLBOX,
                launchNavItem = NavItem.TokenConfig,
                titleResId = R.string.token_config,
                descriptionResId = R.string.tool_token_config_desc,
                icon = Icons.Default.Token,
                order = 140
            ),
            hostEntryDefinition(
                entryId = "toolbox.process_limit_remover",
                screen = Screen.ProcessLimitRemover,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_process_limit_remover,
                descriptionResId = R.string.tool_process_limit_remover_desc,
                icon = Icons.Default.LockOpen,
                order = 150
            ),
            hostEntryDefinition(
                entryId = "toolbox.html_packager",
                screen = Screen.HtmlPackager,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_html_packager,
                descriptionResId = R.string.tool_html_packager_desc,
                icon = Icons.Default.Html,
                order = 160
            ),
            hostEntryDefinition(
                entryId = "toolbox.auto_glm_one_click",
                screen = Screen.AutoGlmOneClick,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_autoglm_one_click,
                descriptionResId = R.string.tool_autoglm_one_click_desc,
                icon = Icons.Default.AutoMode,
                order = 170
            ),
            hostEntryDefinition(
                entryId = "toolbox.auto_glm_tool",
                screen = Screen.AutoGlmTool,
                surface = NavigationSurface.TOOLBOX,
                titleResId = R.string.tool_autoglm_tool,
                descriptionResId = R.string.tool_autoglm_tool_desc,
                icon = Icons.Default.AutoMode,
                order = 180
            ),
            hostEntryDefinition(
                entryId = "hidden.toolbox",
                screen = Screen.Toolbox,
                launchNavItem = NavItem.Toolbox
            ),
            hostEntryDefinition(
                entryId = "hidden.tool_permissions",
                screen = Screen.ToolPermission,
                launchNavItem = NavItem.ToolPermissions
            ),
            hostEntryDefinition(
                entryId = "hidden.user_preferences_settings",
                screen = Screen.UserPreferencesSettings,
                launchNavItem = NavItem.UserPreferencesSettings
            ),
            hostEntryDefinition(
                entryId = "hidden.chat_history_settings",
                screen = Screen.ChatHistorySettings,
                launchNavItem = NavItem.ChatHistorySettings
            )
        )

    private val defaultScreenByNavItem: Map<NavItem, Screen> =
        hostEntryDefinitions
            .mapNotNull { definition ->
                definition.launchNavItem?.let { navItem -> navItem to definition.screen }
            }
            .toMap()

    private val directScreenByRouteId: Map<String, Screen> =
        hostEntryDefinitions
            .map { definition -> routeIdOf(definition.screen) to definition.screen }
            .toMap()

    fun hostRouteSpecs(context: Context): List<RouteSpec> =
        nativeRouteIds.map { routeId ->
            val screen = directScreenByRouteId[routeId]
            hostSpec(
                routeId = routeId,
                title = screen?.let { resolveOptionalTitle(context, defaultTitleResId(it)) },
                navItem = screen?.navItem
            )
        }

    fun mainSidebarEntries(context: Context): List<NavigationEntrySpec> =
        hostEntryDefinitions
            .filter { definition ->
                definition.surface != null && definition.surface != NavigationSurface.TOOLBOX
            }
            .map { definition ->
            definition.toNavigationEntry(context)
            }

    fun toolboxEntries(context: Context): List<NavigationEntrySpec> =
        hostEntryDefinitions
            .filter { definition -> definition.surface == NavigationSurface.TOOLBOX }
            .map { definition ->
            definition.toNavigationEntry(context)
            }

    fun defaultScreenForNavItem(navItem: NavItem): Screen {
        return requireNotNull(defaultScreenByNavItem[navItem]) {
            "No default native screen registered for nav item ${navItem::class.simpleName}"
        }
    }

    fun initialEntry(navItem: NavItem): RouteEntry {
        return toEntry(defaultScreenForNavItem(navItem))
    }

    fun routeIdOf(screen: Screen): String {
        return routeIdForClass(screen.javaClass)
    }

    fun toEntry(
        screen: Screen,
        source: RouteEntrySource = RouteEntrySource.DEFAULT
    ): RouteEntry {
        val args = linkedMapOf<String, Any?>()
        args[INTERNAL_NATIVE_SCREEN_KEY] = screen
        args.putAll(extractConstructorArgs(screen))
        return RouteEntry(routeId = routeIdOf(screen), args = args, source = source)
    }

    fun screenFromEntry(entry: RouteEntry): Screen? {
        val directScreen = entry.args[INTERNAL_NATIVE_SCREEN_KEY] as? Screen
        if (directScreen != null) {
            return directScreen
        }
        return buildScreen(entry.routeId, entry.args)
    }

    fun buildScreen(routeId: String, args: Map<String, Any?>): Screen? {
        if (args.isEmpty()) {
            directScreenByRouteId[routeId]?.let { return it }
        }
        objectScreensByRouteId[routeId]?.let { return it }
        createObjectScreen(routeId)?.let { return it }

        val reflector = routeReflectorsById[routeId] ?: createRouteReflector(routeId) ?: return null
        val constructor = reflector.constructor
        val callArgs = linkedMapOf<KParameter, Any?>()

        for (parameter in reflector.valueParameters) {
            val paramName = parameter.name ?: return null
            if (!args.containsKey(paramName)) {
                if (parameter.isOptional) {
                    continue
                }
                if (parameter.type.isMarkedNullable) {
                    callArgs[parameter] = null
                    continue
                }
                return null
            }

            val rawValue = args[paramName]
            if (rawValue == null) {
                if (parameter.type.isMarkedNullable || parameter.isOptional) {
                    callArgs[parameter] = null
                    continue
                }
                return null
            }

            val converted = convertArg(rawValue, parameter.type)
            if (converted === Unconvertible) {
                return null
            }
            callArgs[parameter] = converted
        }

        return runCatching { constructor.callBy(callArgs) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractConstructorArgs(screen: Screen): Map<String, Any?> {
        val screenClass = screen.javaClass
        if (screenClass.isKotlinObjectClass()) {
            return emptyMap()
        }

        val extractor = argExtractorsByClass[screenClass] ?: createArgExtractor(screenClass)
        val valueParameters = extractor?.valueParameters ?: return emptyMap()
        val propertiesByName = extractor.propertiesByName
        val args = linkedMapOf<String, Any?>()

        valueParameters.forEach { parameter ->
            val paramName = parameter.name ?: return@forEach
            val property = propertiesByName[paramName] ?: return@forEach
            args[paramName] = property.get(screen)
        }
        return args
    }

    private fun convertArg(value: Any, targetType: KType): Any? {
        val targetClass = targetType.classifier as? KClass<*> ?: return value
        if (targetClass.isInstance(value)) {
            return value
        }

        if (targetClass.java.isEnum) {
            val constants = targetClass.java.enumConstants ?: return Unconvertible
            if (value is String) {
                return constants.firstOrNull { constant ->
                    (constant as Enum<*>).name.equals(value, ignoreCase = true)
                } ?: Unconvertible
            }
            if (value is Number) {
                return constants.getOrNull(value.toInt()) ?: Unconvertible
            }
            return Unconvertible
        }

        return when (targetClass) {
            String::class -> value.toString()
            Int::class -> (value as? Number)?.toInt() ?: value.toString().toIntOrNull() ?: Unconvertible
            Long::class -> (value as? Number)?.toLong() ?: value.toString().toLongOrNull() ?: Unconvertible
            Float::class -> (value as? Number)?.toFloat() ?: value.toString().toFloatOrNull() ?: Unconvertible
            Double::class -> (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: Unconvertible
            Boolean::class ->
                when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    else ->
                        when (value.toString().trim().lowercase()) {
                            "true", "1", "yes", "y" -> true
                            "false", "0", "no", "n" -> false
                            else -> Unconvertible
                        }
                }
            else ->
                if (targetClass.java.isAssignableFrom(value.javaClass)) {
                    value
                } else {
                    Unconvertible
                }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createRouteReflector(routeId: String): ScreenRouteReflector? {
        routeReflectorsById[routeId]?.let { return it }

        val screenType = routeTypeById[routeId] ?: return null
        val constructor = screenType.kotlin.primaryConstructor ?: return null
        val reflector =
            ScreenRouteReflector(
                constructor = constructor as KFunction<Screen>,
                valueParameters = constructor.parameters.filter { it.kind == KParameter.Kind.VALUE }
            )
        routeReflectorsById[routeId] = reflector
        return reflector
    }

    private fun createObjectScreen(routeId: String): Screen? {
        val screenType = routeTypeById[routeId] ?: return null
        val instanceField =
            screenType.declaredFields.firstOrNull { field ->
                field.name == "INSTANCE" && field.type == screenType
            } ?: return null
        instanceField.isAccessible = true
        val screen = instanceField.get(null) as? Screen ?: return null
        objectScreensByRouteId[routeId] = screen
        return screen
    }

    @Suppress("UNCHECKED_CAST")
    private fun createArgExtractor(screenClass: Class<out Screen>): ScreenArgExtractor? {
        argExtractorsByClass[screenClass]?.let { return it }

        val constructor = screenClass.kotlin.primaryConstructor ?: return null
        val extractor =
            ScreenArgExtractor(
                valueParameters = constructor.parameters.filter { it.kind == KParameter.Kind.VALUE },
                propertiesByName =
                    screenClass.kotlin.memberProperties.associate { property ->
                        property.name to (property as KProperty1<Any, *>)
                    }
            )
        argExtractorsByClass[screenClass] = extractor
        return extractor
    }

    private fun hostSpec(
        routeId: String,
        title: String? = null,
        navItem: NavItem? = null
    ): RouteSpec =
        RouteSpec(routeId = routeId, runtime = RouteRuntime.NATIVE, title = title, icon = navItem?.icon)

    private fun hostEntryDefinition(
        entryId: String,
        screen: Screen,
        surface: NavigationSurface? = null,
        launchNavItem: NavItem? = null,
        @StringRes titleResId: Int? = null,
        @StringRes descriptionResId: Int? = null,
        icon: ImageVector? = null,
        order: Int = 0
    ): HostNavigationEntryDefinition =
        HostNavigationEntryDefinition(
            entryId = entryId,
            screen = screen,
            surface = surface,
            launchNavItem = launchNavItem,
            titleResId = titleResId,
            descriptionResId = descriptionResId,
            icon = icon,
            order = order
        )

    private fun HostNavigationEntryDefinition.toNavigationEntry(context: Context): NavigationEntrySpec =
        NavigationEntrySpec(
            entryId = entryId,
            routeId = routeIdOf(screen),
            surface = requireNotNull(surface) {
                "Missing navigation surface for visible host navigation entry $entryId"
            },
            title = resolveRequiredTitle(context, entryId, titleResId ?: defaultTitleResId(screen)),
            description = resolveOptionalTitle(context, descriptionResId),
            icon = requireNotNull(icon) {
                "Missing icon for visible host navigation entry $entryId"
            },
            order = order
        )

    private fun defaultTitleResId(screen: Screen): Int? {
        return screen.titleRes ?: screen.navItem?.titleResId
    }

    private fun resolveOptionalTitle(context: Context, @StringRes titleResId: Int?): String? {
        return titleResId?.let(context::getString)
    }

    private fun resolveRequiredTitle(
        context: Context,
        entryId: String,
        @StringRes titleResId: Int?
    ): String {
        val resolvedTitleResId =
            requireNotNull(titleResId) {
                "Missing localized title resource for host navigation entry $entryId"
            }
        return context.getString(resolvedTitleResId)
    }
}
