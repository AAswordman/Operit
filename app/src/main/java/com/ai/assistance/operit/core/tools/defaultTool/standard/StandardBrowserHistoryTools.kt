package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Historial de Navegación AI
 * Consulta y gestiona el historial de navegación
 */
class StandardBrowserHistoryTools(private val context: Context) {

    companion object {
        private const val TAG = "BrowserHistoryTools"
    }

    /** Abrir página en el navegador */
    fun openUrlInBrowser(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value?.trim().orEmpty()
        if (url.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Missing: url")
        }

        return try {
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(toolName = tool.name, success = true, result = StringResultData("Opened: $fullUrl"))
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /** Buscar en Google (usa el dominio según la locale del usuario) */
    fun searchGoogle(tool: AITool): ToolResult {
        val query = tool.parameters.find { it.name == "query" }?.value?.trim().orEmpty()
        if (query.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Missing: query")
        }

        return try {
            // Determinar el dominio de Google según la locale
            val language = com.ai.assistance.operit.util.LocaleUtils.getCurrentLanguage(context)
            val googleDomain = when {
                language.startsWith("es") -> "https://www.google.es/search"
                language.startsWith("pt") -> "https://www.google.com.br/search"
                language.startsWith("fr") -> "https://www.google.fr/search"
                language.startsWith("de") -> "https://www.google.de/search"
                language.startsWith("ja") -> "https://www.google.co.jp/search"
                language.startsWith("ko") -> "https://www.google.co.kr/search"
                language.startsWith("zh") -> "https://www.google.com.hk/search"
                else -> "https://www.google.com/search"
            }
            val searchUrl = "$googleDomain?q=${Uri.encode(query)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(toolName = tool.name, success = true, result = StringResultData("Searching Google for: $query"))
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /** Abrir configuración del navegador */
    fun openBrowserSettings(tool: AITool): ToolResult {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:com.android.chrome")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(toolName = tool.name, success = true, result = StringResultData("Opened Chrome app settings."))
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolResult(toolName = tool.name, success = true, result = StringResultData("Opened default apps settings."))
            } catch (e2: Exception) {
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
            }
        }
    }

    /** Obtener info del navegador predeterminado */
    fun getDefaultBrowser(tool: AITool): ToolResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val browserPackage = resolveInfo?.activityInfo?.packageName ?: "Unknown"

            val info = buildString {
                appendLine("=== Default Browser ===")
                appendLine("Package: $browserPackage")
                appendLine("Name: ${resolveInfo?.loadLabel(context.packageManager) ?: "Unknown"}")
            }

            ToolResult(toolName = tool.name, success = true, result = StringResultData(info))
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }
}
