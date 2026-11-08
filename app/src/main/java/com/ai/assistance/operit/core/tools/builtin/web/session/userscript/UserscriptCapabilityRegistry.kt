package com.ai.assistance.operit.core.tools.builtin.web.session.userscript

import androidx.webkit.WebViewFeature

internal data class UserscriptCapability(
    val canonicalGrant: String,
    val aliases: Set<String> = emptySet(),
    val runtimeSymbols: Set<String> = emptySet(),
    val hostMessages: Set<String> = emptySet(),
    val requiresDocumentStartInjection: Boolean = false,
    val requiresRequestInterception: Boolean = false,
    val blockingReason: (() -> String?)? = null
)

internal object UserscriptCapabilityRegistry {
    private val capabilities =
        listOf(
            UserscriptCapability(
                canonicalGrant = "GM.addElement",
                aliases = setOf("GM_addElement"),
                runtimeSymbols = setOf("GM.addElement", "GM_addElement")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.addStyle",
                aliases = setOf("GM_addStyle"),
                runtimeSymbols = setOf("GM.addStyle", "GM_addStyle")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.deleteValue",
                aliases = setOf("GM_deleteValue"),
                runtimeSymbols = setOf("GM.deleteValue", "GM_deleteValue"),
                hostMessages = setOf("storage_delete")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.deleteValues",
                aliases = setOf("GM_deleteValues"),
                runtimeSymbols = setOf("GM.deleteValues", "GM_deleteValues"),
                hostMessages = setOf("storage_delete_many")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.download",
                aliases = setOf("GM_download"),
                runtimeSymbols = setOf("GM.download", "GM_download"),
                hostMessages = setOf("gm_download")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.getResourceText",
                aliases = setOf("GM_getResourceText"),
                runtimeSymbols = setOf("GM.getResourceText", "GM_getResourceText")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.getResourceURL",
                aliases = setOf("GM_getResourceURL"),
                runtimeSymbols = setOf("GM.getResourceURL", "GM_getResourceURL")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.getTab",
                aliases = setOf("GM_getTab"),
                runtimeSymbols = setOf("GM.getTab", "GM_getTab"),
                hostMessages = setOf("gm_get_tab")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.getTabs",
                aliases = setOf("GM_getTabs"),
                runtimeSymbols = setOf("GM.getTabs", "GM_getTabs"),
                hostMessages = setOf("gm_get_tabs")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.getValue",
                aliases = setOf("GM_getValue"),
                runtimeSymbols = setOf("GM.getValue", "GM_getValue")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.getValues",
                aliases = setOf("GM_getValues"),
                runtimeSymbols = setOf("GM.getValues", "GM_getValues")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.info",
                aliases = setOf("GM_info"),
                runtimeSymbols = setOf("GM.info", "GM_info")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.listValues",
                aliases = setOf("GM_listValues"),
                runtimeSymbols = setOf("GM.listValues", "GM_listValues")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.log",
                aliases = setOf("GM_log"),
                runtimeSymbols = setOf("GM.log", "GM_log")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.notification",
                aliases = setOf("GM_notification"),
                runtimeSymbols = setOf("GM.notification", "GM_notification"),
                hostMessages = setOf("gm_notification")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.openInTab",
                aliases = setOf("GM_openInTab"),
                runtimeSymbols = setOf("GM.openInTab", "GM_openInTab"),
                hostMessages = setOf("gm_open_in_tab", "gm_focus_tab", "gm_close_tab")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.registerMenuCommand",
                aliases = setOf("GM_registerMenuCommand"),
                runtimeSymbols = setOf("GM.registerMenuCommand", "GM_registerMenuCommand"),
                hostMessages = setOf("register_menu_command")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.addValueChangeListener",
                aliases = setOf("GM_addValueChangeListener"),
                runtimeSymbols = setOf("GM.addValueChangeListener", "GM_addValueChangeListener")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.removeValueChangeListener",
                aliases = setOf("GM_removeValueChangeListener"),
                runtimeSymbols = setOf("GM.removeValueChangeListener", "GM_removeValueChangeListener")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.saveTab",
                aliases = setOf("GM_saveTab"),
                runtimeSymbols = setOf("GM.saveTab", "GM_saveTab"),
                hostMessages = setOf("gm_save_tab")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.setClipboard",
                aliases = setOf("GM_setClipboard"),
                runtimeSymbols = setOf("GM.setClipboard", "GM_setClipboard"),
                hostMessages = setOf("gm_set_clipboard")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.setValue",
                aliases = setOf("GM_setValue"),
                runtimeSymbols = setOf("GM.setValue", "GM_setValue"),
                hostMessages = setOf("storage_set")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.setValues",
                aliases = setOf("GM_setValues"),
                runtimeSymbols = setOf("GM.setValues", "GM_setValues"),
                hostMessages = setOf("storage_set_many")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.unregisterMenuCommand",
                aliases = setOf("GM_unregisterMenuCommand"),
                runtimeSymbols = setOf("GM.unregisterMenuCommand", "GM_unregisterMenuCommand"),
                hostMessages = setOf("unregister_menu_command")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.webRequest",
                aliases = setOf("GM_webRequest"),
                runtimeSymbols = setOf("GM.webRequest", "GM_webRequest"),
                hostMessages = setOf("gm_web_request_register", "gm_web_request_unregister"),
                requiresDocumentStartInjection = true,
                requiresRequestInterception = true
            ),
            UserscriptCapability(
                canonicalGrant = "GM.xmlHttpRequest",
                aliases = setOf("GM_xmlhttpRequest", "GM_xmlHttpRequest"),
                runtimeSymbols = setOf("GM.xmlHttpRequest", "GM_xmlhttpRequest", "GM_xmlHttpRequest"),
                hostMessages = setOf("gm_xmlhttp_request", "gm_abort_request")
            ),
            UserscriptCapability(
                canonicalGrant = "GM.audio",
                aliases = setOf("GM_audio"),
                runtimeSymbols = setOf("GM.audio", "GM_audio"),
                hostMessages = setOf("gm_audio"),
                blockingReason = {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
                        null
                    } else {
                        "Current WebView does not support GM_audio because mute-audio control is unavailable"
                    }
                }
            ),
            UserscriptCapability(
                canonicalGrant = "GM.cookie",
                aliases = setOf("GM_cookie"),
                runtimeSymbols = setOf("GM.cookie", "GM_cookie"),
                hostMessages = setOf("gm_cookie")
            ),
            UserscriptCapability("unsafeWindow", runtimeSymbols = setOf("unsafeWindow")),
            UserscriptCapability(
                canonicalGrant = "window.close",
                runtimeSymbols = setOf("window.close", "close"),
                hostMessages = setOf("gm_close_tab")
            ),
            UserscriptCapability(
                canonicalGrant = "window.focus",
                runtimeSymbols = setOf("window.focus", "focus"),
                hostMessages = setOf("gm_focus_tab")
            ),
            UserscriptCapability(
                canonicalGrant = "window.onurlchange",
                runtimeSymbols = setOf("window.onurlchange"),
                hostMessages = setOf("url_change"),
                requiresDocumentStartInjection = true
            ),
            UserscriptCapability("none")
        )

    private val capabilityByCanonical = capabilities.associateBy { it.canonicalGrant }

    private val aliasToCanonical =
        buildMap<String, String> {
            capabilities.forEach { capability ->
                put(capability.canonicalGrant, capability.canonicalGrant)
                capability.aliases.forEach { alias ->
                    put(alias, capability.canonicalGrant)
                }
            }
        }

    fun canonicalGrant(rawGrant: String): String? =
        aliasToCanonical[rawGrant.trim()]

    fun isGrantKnown(rawGrant: String): Boolean =
        canonicalGrant(rawGrant) != null

    fun knownGrants(grants: List<String>): List<String> =
        grants.mapNotNull(::canonicalGrant).distinct()

    fun unknownGrants(grants: List<String>): List<String> =
        grants.map { it.trim() }.filter { it.isNotBlank() && !isGrantKnown(it) }.distinct()

    fun blockedReasons(grants: List<String>): List<String> {
        val canonicalGrants = knownGrants(grants)
        return buildList {
            val unknown = unknownGrants(grants)
            if (unknown.isNotEmpty()) {
                add("Unknown grants: ${unknown.joinToString()}")
            }
            if ("none" in canonicalGrants && canonicalGrants.size > 1) {
                add("@grant none cannot be combined with other grants")
            }
            canonicalGrants
                .mapNotNull { capabilityByCanonical[it]?.blockingReason?.invoke() }
                .distinct()
                .forEach(::add)
        }
    }
}
