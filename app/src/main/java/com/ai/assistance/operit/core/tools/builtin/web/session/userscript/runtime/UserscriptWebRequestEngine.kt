package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.runtime

import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

internal data class UserscriptWebRequestAction(
    val cancel: Boolean = false,
    val redirectUrl: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null
) {
    fun requiresInterception(): Boolean =
        cancel || !redirectUrl.isNullOrBlank() || requestHeaders.isNotEmpty() || responseHeaders.isNotEmpty() || responseBody != null
}

internal data class UserscriptWebRequestRule(
    val matchers: List<String>,
    val types: Set<String>,
    val action: UserscriptWebRequestAction
)

internal data class UserscriptWebRequestRegistration(
    val registrationId: String,
    val scriptId: Long,
    val sessionId: String,
    val rules: List<UserscriptWebRequestRule>,
    val source: String,
    val order: Long
)

internal data class UserscriptWebRequestMatch(
    val registrationId: String,
    val scriptId: Long,
    val action: UserscriptWebRequestAction,
    val source: String
)

internal data class UserscriptWebRequestResolution(
    val matches: List<UserscriptWebRequestMatch> = emptyList(),
    val mergedAction: UserscriptWebRequestAction = UserscriptWebRequestAction()
)

internal class UserscriptWebRequestEngine {
    private val registrations = ConcurrentHashMap<String, UserscriptWebRequestRegistration>()
    private val registrationOrder = AtomicLong(0L)

    fun register(
        scriptId: Long,
        sessionId: String,
        rulesJson: String,
        source: String
    ): String {
        val rules = parseRules(rulesJson)
        val registrationId = "wr_" + UUID.randomUUID().toString().replace("-", "")
        registrations[registrationId] =
            UserscriptWebRequestRegistration(
                registrationId = registrationId,
                scriptId = scriptId,
                sessionId = sessionId,
                rules = rules,
                source = source,
                order = registrationOrder.incrementAndGet()
            )
        return registrationId
    }

    fun unregister(registrationId: String): Boolean =
        registrations.remove(registrationId) != null

    fun clearSession(sessionId: String) {
        registrations.entries.removeIf { it.value.sessionId == sessionId }
    }

    fun resolve(
        sessionId: String,
        url: String,
        requestType: String
    ): UserscriptWebRequestResolution {
        val normalizedType = requestType.trim().lowercase(Locale.ROOT).ifBlank { "other" }
        val matches =
            registrations.values
                .sortedBy { it.order }
                .filter { it.sessionId == sessionId }
                .flatMap { registration ->
                    registration.rules.filter { rule ->
                        matchesType(rule, normalizedType) && matchesUrl(rule, url)
                    }.map { rule ->
                        UserscriptWebRequestMatch(
                            registrationId = registration.registrationId,
                            scriptId = registration.scriptId,
                            action = rule.action,
                            source = registration.source
                        )
                    }
                }

        if (matches.isEmpty()) {
            return UserscriptWebRequestResolution()
        }

        val mergedAction =
            matches.fold(UserscriptWebRequestAction()) { acc, match ->
                UserscriptWebRequestAction(
                    cancel = acc.cancel || match.action.cancel,
                    redirectUrl = match.action.redirectUrl ?: acc.redirectUrl,
                    requestHeaders = acc.requestHeaders + match.action.requestHeaders,
                    responseHeaders = acc.responseHeaders + match.action.responseHeaders,
                    responseBody = match.action.responseBody ?: acc.responseBody
                )
            }

        return UserscriptWebRequestResolution(matches = matches, mergedAction = mergedAction)
    }

    private fun parseRules(rulesJson: String): List<UserscriptWebRequestRule> {
        val trimmed = rulesJson.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }
        val array =
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONArray().put(JSONObject(trimmed))
                else -> JSONArray().put(JSONObject().put("selector", trimmed))
            }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                when (item) {
                    is JSONObject -> parseRuleObject(item)?.let(::add)
                    is String -> add(UserscriptWebRequestRule(matchers = listOf(item), types = emptySet(), action = UserscriptWebRequestAction()))
                }
            }
        }
    }

    private fun parseRuleObject(raw: JSONObject): UserscriptWebRequestRule? {
        val selectors = mutableListOf<String>()
        listOf("selector", "match", "url").forEach { key ->
            raw.optString(key).trim().takeIf { it.isNotBlank() }?.let(selectors::add)
        }
        raw.optJSONArray("urls")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(selectors::add)
            }
        }
        val actionJson = raw.optJSONObject("action") ?: raw
        val requestHeaders = actionJson.optJSONObject("requestHeaders").toStringMap()
        val responseHeaders = actionJson.optJSONObject("responseHeaders").toStringMap()
        val types =
            buildSet {
                raw.optString("type").trim().takeIf { it.isNotBlank() }?.let { add(it.lowercase(Locale.ROOT)) }
                raw.optJSONArray("types")?.let { array ->
                    for (index in 0 until array.length()) {
                        array.optString(index).trim().takeIf { it.isNotBlank() }?.let {
                            add(it.lowercase(Locale.ROOT))
                        }
                    }
                }
            }
        val action =
            UserscriptWebRequestAction(
                cancel = actionJson.optBoolean("cancel", false),
                redirectUrl =
                    actionJson.optString("redirectUrl").ifBlank {
                        actionJson.optString("redirect").ifBlank { null }
                    },
                requestHeaders = requestHeaders,
                responseHeaders = responseHeaders,
                responseBody =
                    actionJson.optString("responseBody").ifBlank {
                        actionJson.optString("body").ifBlank { null }
                    }
            )
        val finalSelectors = selectors.ifEmpty { listOf("*") }
        return UserscriptWebRequestRule(
            matchers = finalSelectors.distinct(),
            types = types,
            action = action
        )
    }

    private fun matchesType(
        rule: UserscriptWebRequestRule,
        requestType: String
    ): Boolean = rule.types.isEmpty() || requestType in rule.types

    private fun matchesUrl(
        rule: UserscriptWebRequestRule,
        url: String
    ): Boolean =
        rule.matchers.any { matcher ->
            val trimmed = matcher.trim()
            when {
                trimmed == "*" -> true
                trimmed.startsWith("/") && trimmed.endsWith("/") && trimmed.length > 2 ->
                    runCatching { Regex(trimmed.removePrefix("/").removeSuffix("/"), RegexOption.IGNORE_CASE).containsMatchIn(url) }
                        .getOrDefault(false)
                trimmed.contains("*") ->
                    globToRegex(trimmed).matches(url)
                else -> url.contains(trimmed, ignoreCase = true)
            }
        }

    private fun globToRegex(pattern: String): Regex {
        val builder = StringBuilder("^")
        pattern.forEach { ch ->
            when (ch) {
                '*' -> builder.append(".*")
                '.', '?', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' ->
                    builder.append('\\').append(ch)
                else -> builder.append(ch)
            }
        }
        builder.append('$')
        return Regex(builder.toString(), RegexOption.IGNORE_CASE)
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) {
            return emptyMap()
        }
        return buildMap {
            keys().forEach { key ->
                put(key, optString(key))
            }
        }
    }
}
