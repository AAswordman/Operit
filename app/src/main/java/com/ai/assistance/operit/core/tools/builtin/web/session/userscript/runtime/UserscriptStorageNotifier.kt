package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.runtime

import org.json.JSONObject

internal data class UserscriptStorageChange(
    val scriptId: Long,
    val key: String,
    val oldValueJson: String?,
    val newValueJson: String?,
    val remote: Boolean
)

internal class UserscriptStorageNotifier {
    fun toPayload(change: UserscriptStorageChange): JSONObject =
        JSONObject()
            .put("scriptId", change.scriptId)
            .put("key", change.key)
            .put("remote", change.remote)
            .also { payload ->
                payload.put("oldValueJson", change.oldValueJson)
                payload.put("newValueJson", change.newValueJson)
            }
}
