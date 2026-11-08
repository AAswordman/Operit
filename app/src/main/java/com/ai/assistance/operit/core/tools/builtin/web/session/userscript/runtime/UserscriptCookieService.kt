package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.runtime

import android.webkit.CookieManager
import java.net.HttpCookie
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

internal data class UserscriptCookieRecord(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val session: Boolean = true,
    val expirationDate: Double? = null,
    val url: String? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("name", name)
            .put("value", value)
            .also { json ->
                domain?.let { json.put("domain", it) }
                path?.let { json.put("path", it) }
                url?.let { json.put("url", it) }
                json.put("secure", secure)
                json.put("httpOnly", httpOnly)
                json.put("session", session)
                expirationDate?.let { json.put("expirationDate", it) }
            }
}

internal class UserscriptCookieService(
    private val cookieManager: CookieManager
) {
    private val mirror = ConcurrentHashMap<String, UserscriptCookieRecord>()

    fun list(
        details: JSONObject,
        fallbackUrl: String
    ): List<UserscriptCookieRecord> {
        val targetUrl = details.optString("url").ifBlank { fallbackUrl }
        val targetUri = runCatching { URI(targetUrl) }.getOrNull()
        val targetHost = targetUri?.host?.lowercase(Locale.ROOT)
        val targetPath = targetUri?.path?.ifBlank { "/" } ?: "/"
        val requestedName = details.optString("name").ifBlank { null }

        val visibleCookies =
            cookieManager.getCookie(targetUrl)
                .orEmpty()
                .split(';')
                .mapNotNull { raw ->
                    val part = raw.trim()
                    if (part.isBlank() || !part.contains('=')) {
                        return@mapNotNull null
                    }
                    val name = part.substringBefore('=').trim()
                    val value = part.substringAfter('=', "")
                    if (name.isBlank()) {
                        return@mapNotNull null
                    }
                    val mirrored =
                        mirror.values.firstOrNull { record ->
                            record.name == name &&
                                domainMatches(targetHost, record.domain) &&
                                pathMatches(targetPath, record.path)
                        }
                    UserscriptCookieRecord(
                        name = name,
                        value = value,
                        domain = mirrored?.domain ?: targetHost,
                        path = mirrored?.path ?: "/",
                        secure = mirrored?.secure == true,
                        httpOnly = mirrored?.httpOnly == true,
                        session = mirrored?.session ?: true,
                        expirationDate = mirrored?.expirationDate,
                        url = mirrored?.url ?: targetUrl
                    )
                }

        val merged =
            (visibleCookies + mirror.values.filter { record ->
                domainMatches(targetHost, record.domain) &&
                    pathMatches(targetPath, record.path)
            }).distinctBy { cookie ->
                listOf(cookie.name, cookie.domain.orEmpty(), cookie.path.orEmpty()).joinToString("::")
            }

        return merged.filter { cookie ->
            requestedName == null || cookie.name == requestedName
        }
    }

    fun set(
        details: JSONObject,
        fallbackUrl: String
    ): UserscriptCookieRecord {
        val targetUrl = details.optString("url").ifBlank { fallbackUrl }
        val targetUri = runCatching { URI(targetUrl) }.getOrNull()
        val name = details.optString("name").trim()
        require(name.isNotBlank()) { "GM_cookie.set requires name" }
        val value = details.optString("value")
        val domain = details.optString("domain").ifBlank { targetUri?.host }
        val path = details.optString("path").ifBlank { "/" }
        val secure = details.optBoolean("secure", false)
        val httpOnly = details.optBoolean("httpOnly", false)
        val expirationDate = details.optDouble("expirationDate").takeIf { !it.isNaN() }
        val session = details.optBoolean("session", expirationDate == null)

        val cookieString =
            buildString {
                append(name)
                append('=')
                append(value)
                append("; Path=")
                append(path)
                domain?.takeIf { it.isNotBlank() }?.let {
                    append("; Domain=")
                    append(it)
                }
                if (secure) {
                    append("; Secure")
                }
                if (httpOnly) {
                    append("; HttpOnly")
                }
                if (expirationDate != null) {
                    append("; Expires=")
                    append(httpDate(expirationDate))
                }
            }
        cookieManager.setCookie(targetUrl, cookieString)
        flush()

        val record =
            UserscriptCookieRecord(
                name = name,
                value = value,
                domain = domain,
                path = path,
                secure = secure,
                httpOnly = httpOnly,
                session = session,
                expirationDate = expirationDate,
                url = targetUrl
            )
        mirror[scopeKey(record)] = record
        return record
    }

    fun delete(
        details: JSONObject,
        fallbackUrl: String
    ): Boolean {
        val targetUrl = details.optString("url").ifBlank { fallbackUrl }
        val targetUri = runCatching { URI(targetUrl) }.getOrNull()
        val name = details.optString("name").trim()
        require(name.isNotBlank()) { "GM_cookie.delete requires name" }
        val domain = details.optString("domain").ifBlank { targetUri?.host }
        val path = details.optString("path").ifBlank { "/" }
        val cookieString =
            buildString {
                append(name)
                append("=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                append("; Path=")
                append(path)
                domain?.takeIf { it.isNotBlank() }?.let {
                    append("; Domain=")
                    append(it)
                }
            }
        cookieManager.setCookie(targetUrl, cookieString)
        flush()
        mirror.entries.removeIf { (_, value) ->
            value.name == name &&
                value.path.orEmpty() == path &&
                value.domain.orEmpty().equals(domain.orEmpty(), ignoreCase = true)
        }
        return true
    }

    private fun flush() {
        runCatching { cookieManager.flush() }
    }

    private fun scopeKey(record: UserscriptCookieRecord): String =
        listOf(
            record.name,
            record.domain.orEmpty().lowercase(Locale.ROOT),
            record.path.orEmpty()
        ).joinToString("::")

    private fun httpDate(epochSeconds: Double): String {
        val formatter =
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
        return formatter.format(Date((epochSeconds * 1000.0).toLong()))
    }

    private fun domainMatches(
        host: String?,
        domain: String?
    ): Boolean {
        if (domain.isNullOrBlank()) {
            return true
        }
        val normalizedDomain = domain.removePrefix(".").lowercase(Locale.ROOT)
        val normalizedHost = host?.lowercase(Locale.ROOT) ?: return false
        return normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
    }

    private fun pathMatches(
        path: String,
        cookiePath: String?
    ): Boolean {
        val normalizedCookiePath = cookiePath?.ifBlank { "/" } ?: "/"
        return path == normalizedCookiePath || path.startsWith(normalizedCookiePath)
    }
}
