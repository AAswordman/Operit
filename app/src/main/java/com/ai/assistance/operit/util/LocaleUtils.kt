package com.ai.assistance.operit.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 语言工具类，用于管理应用的国际化设置 */
object LocaleUtils {

    object LanguageCodes {
        const val AUTO = "system"
        const val CHINESE = "zh"
        const val ENGLISH = "en"
        const val KOREAN = "ko"
        const val SPANISH = "es"
        const val MALAY = "ms"
        const val INDONESIAN = "id"
        const val PORTUGUESE_BRAZIL = "pt-BR"
        const val ROMANIAN = "ro"
    }

    private val legacyLanguageCodeAliases =
            mapOf("pt" to LanguageCodes.PORTUGUESE_BRAZIL, "in" to LanguageCodes.INDONESIAN)

    /**
     * 语言信息数据类
     * @param code 语言代码（如zh、en）
     * @param displayName 显示名称（英文）
     * @param nativeName 本地名称（语言自身的称呼）
     */
    data class Language(val code: String, val displayName: String, val nativeName: String)

    private val supportedLanguages =
            listOf(
                    Language(LanguageCodes.AUTO, "Follow system", "跟随系统"),
                    Language(LanguageCodes.CHINESE, "Chinese", "中文"),
                    Language(LanguageCodes.ENGLISH, "English", "English"),
                    Language(LanguageCodes.KOREAN, "Korean", "한국어"),
                    Language(LanguageCodes.SPANISH, "Spanish", "Español"),
                    Language(LanguageCodes.MALAY, "Malay", "Bahasa Melayu"),
                    Language(LanguageCodes.INDONESIAN, "Indonesian", "Bahasa Indonesia"),
                    Language(
                            LanguageCodes.PORTUGUESE_BRAZIL,
                            "Portuguese (Brazil)",
                            "Português (Brasil)"
                    ),
                    Language(LanguageCodes.ROMANIAN, "Romanian", "Română")
            )

    private val supportedLanguageCodes =
            supportedLanguages.map { it.code }.filter { it != LanguageCodes.AUTO }.toSet()

    /** 获取支持的语言列表 */
    fun getSupportedLanguages(): List<Language> {
        return supportedLanguages
    }

    fun getLocalizedLanguageName(context: Context, languageCode: String): String {
        return when (languageCode) {
            LanguageCodes.AUTO -> context.getString(R.string.language_follow_system)
            LanguageCodes.CHINESE -> context.getString(R.string.conversation_language_chinese)
            LanguageCodes.ENGLISH -> context.getString(R.string.translation_language_english)
            "ko" -> context.getString(R.string.translation_language_korean)
            LanguageCodes.SPANISH -> context.getString(R.string.translation_language_spanish)
            LanguageCodes.MALAY -> context.getString(R.string.translation_language_malay)
            LanguageCodes.INDONESIAN -> context.getString(R.string.translation_language_indonesian)
            LanguageCodes.PORTUGUESE_BRAZIL -> context.getString(R.string.translation_language_portuguese)
            "ro" -> context.getString(R.string.translation_language_romanian)
            "ja" -> context.getString(R.string.translation_language_japanese)
            else -> languageCode
        }
    }

    fun getLocaleForLanguageCode(languageCode: String, context: Context? = null): Locale {
        val resolvedCode =
                if (languageCode.isBlank() || languageCode == LanguageCodes.AUTO) {
                    context?.let(::getCurrentSystemLanguageCode)
                            ?: resolveSupportedLanguageCode(Locale.getDefault().toLanguageTag())
                } else {
                    resolveSupportedLanguageCode(languageCode)
                }

        return Locale.forLanguageTag(resolvedCode)
                .takeIf { it.language.isNotBlank() }
                ?: Locale(resolvedCode)
    }

    fun createLocaleOverrideConfiguration(locale: Locale): Configuration {
        // Keep this override sparse so window size and orientation continue to update.
        return Configuration().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                setLocale(locale)
            }
        }
    }

    /**
     * 获取包含当前应用语言设置的上下文。
     * 对于使用applicationContext的单例或服务，这非常有用，
     * 因为它可以确保获取到最新的本地化资源。
     *
     * @param context 基础上下文.
     * @return 带有更新后语言配置的新上下文.
     */
    fun getLocalizedContext(context: Context): Context {
        val lang = getCurrentLanguage(context)
        val locale = getLocaleForLanguageCode(lang, context)
        return context.createConfigurationContext(createLocaleOverrideConfiguration(locale))
    }

    /**
     * 获取当前应用设置的语言
     * @param context 上下文
     * @return 当前语言代码，如zh、en
     */
    fun getCurrentLanguage(context: Context): String {
        try {
            val savedLanguage =
                    UserPreferencesManager.getInstance(context).getCurrentLanguage()
            if (savedLanguage.isNotEmpty() && savedLanguage != LanguageCodes.AUTO) {
                return resolveSupportedLanguageCode(savedLanguage)
            }
        } catch (e: Exception) {
            AppLogger.e("LocaleUtils", "读取应用语言设置失败", e)
        }

        return getCurrentSystemLanguage(context)
    }

    /** 获取系统当前语言 */
    private fun getCurrentSystemLanguage(context: Context): String {
        return getCurrentSystemLanguageCode(context)
    }

    /**
     * 设置应用语言
     * @param context 上下文
     * @param languageCode 语言代码，如zh、en、pt-BR
     */
    fun setAppLanguage(context: Context, languageCode: String) {
        
        try {
            val manager = UserPreferencesManager.getInstance(context)
            runBlocking(Dispatchers.IO) {
                manager.saveAppLanguage(languageCode)
            }
        } catch (e: Exception) {
            AppLogger.e("LocaleUtils", "保存应用语言设置失败: $languageCode", e)
        }

        // 根据 languageCode 获取相应的 Locale
        val localeToSet = getLocaleForLanguageCode(languageCode, context)
        
        // 设置默认语言
        Locale.setDefault(localeToSet)
        
        // 根据Android版本应用语言设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用AppCompatDelegate API
            val localeList = LocaleListCompat.create(localeToSet)
            AppCompatDelegate.setApplicationLocales(localeList)
        } else {
            // 较旧版本Android使用资源配置
            try {
                val config = Configuration(context.resources.configuration)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(localeToSet)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = localeToSet
                }
                
                // 更新上下文资源配置
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                
                // 尝试更新Activity
                try {
                    val ctx = context.applicationContext
                    if (ctx is ContextWrapper) {
                        val baseContext = ctx.baseContext
                        if (baseContext != null) {
                            @Suppress("DEPRECATION")
                            baseContext.resources.updateConfiguration(
                                config, 
                                baseContext.resources.displayMetrics
                            )
                        }
                    }
                } catch (e: Exception) {
                    // 忽略无法更新的上下文
                }
            } catch (e: Exception) {
                // 错误时静默处理
            }
        }
    }

    private fun getCurrentSystemLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    private fun getCurrentSystemLanguageCode(context: Context): String {
        return resolveSupportedLanguageCode(getCurrentSystemLocale(context).toLanguageTag())
    }

    private fun normalizeStoredLanguageCode(languageCode: String): String {
        if (languageCode.isBlank() || languageCode == LanguageCodes.AUTO) {
            return languageCode
        }

        val normalizedCode = languageCode.replace("_", "-").replace("-r", "-")
        val canonicalCode =
                Locale.forLanguageTag(normalizedCode)
                        .takeIf { it.language.isNotBlank() }
                        ?.toLanguageTag()
                        ?.takeIf { it.isNotBlank() && it != "und" }
                        ?: normalizedCode
        return legacyLanguageCodeAliases[canonicalCode] ?: canonicalCode
    }

    private fun resolveSupportedLanguageCode(languageCode: String): String {
        val normalizedCode = normalizeStoredLanguageCode(languageCode)
        if (normalizedCode.isBlank() || normalizedCode == LanguageCodes.AUTO) {
            return normalizedCode
        }

        if (normalizedCode in supportedLanguageCodes) {
            return normalizedCode
        }

        val locale =
                Locale.forLanguageTag(normalizedCode)
                        .takeIf { it.language.isNotBlank() }
                        ?: return normalizedCode
        val language = locale.language.lowercase(Locale.ROOT)

        val languageOnlyMatch =
                supportedLanguageCodes.firstOrNull { it.equals(language, ignoreCase = true) }
        if (languageOnlyMatch != null) {
            return languageOnlyMatch
        }

        val sameLanguageVariants =
                supportedLanguageCodes.filter {
                    Locale.forLanguageTag(it).language.equals(language, ignoreCase = true)
                }
        if (sameLanguageVariants.size == 1) {
            return sameLanguageVariants.first()
        }

        return normalizedCode
    }
}
