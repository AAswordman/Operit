package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorMessageDetailsDefinitionV1 {
    private fun toggle(
        id: String,
        title: NativeThemeEditorTextKey,
        description: NativeThemeEditorTextKey,
        field: com.ai.assistance.operit.data.preferences.NativeThemeBooleanField,
        advanced: Boolean = false,
    ) = NativeThemeBooleanControlDefinitionV1(
        id = NativeThemeEditorItemId(id),
        title = title,
        description = description,
        field = field,
        advanced = advanced,
    )

    val reasoning =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("message_details.reasoning"),
            title = NativeThemeEditorTextKey.MESSAGE_REASONING,
            items =
                listOf(
                    toggle(
                        "message_details.reasoning.thinking",
                        NativeThemeEditorTextKey.SHOW_THINKING_PROCESS,
                        NativeThemeEditorTextKey.SHOW_THINKING_PROCESS_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showThinkingProcess,
                    ),
                    toggle(
                        "message_details.reasoning.status_tags",
                        NativeThemeEditorTextKey.SHOW_STATUS_TAGS,
                        NativeThemeEditorTextKey.SHOW_STATUS_TAGS_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showStatusTags,
                    ),
                ),
        )

    val identity =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("message_details.identity"),
            title = NativeThemeEditorTextKey.MESSAGE_IDENTITY,
            items =
                listOf(
                    toggle(
                        "message_details.identity.role_name",
                        NativeThemeEditorTextKey.SHOW_ROLE_NAME,
                        NativeThemeEditorTextKey.SHOW_ROLE_NAME_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showRoleName,
                    ),
                    toggle(
                        "message_details.identity.user_name",
                        NativeThemeEditorTextKey.SHOW_USER_NAME,
                        NativeThemeEditorTextKey.SHOW_USER_NAME_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showUserName,
                    ),
                    toggle(
                        "message_details.identity.timestamp",
                        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMESTAMP,
                        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMESTAMP_DESCRIPTION,
                    NativeThemePreferenceSchemaV1.showMessageTimestamp,
                    ),
                ),
        )

    val diagnostics =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("message_details.diagnostics"),
            title = NativeThemeEditorTextKey.MESSAGE_DIAGNOSTICS,
            items =
                listOf(
                    toggle(
                        "message_details.diagnostics.provider",
                        NativeThemeEditorTextKey.SHOW_MODEL_PROVIDER,
                        NativeThemeEditorTextKey.SHOW_MODEL_PROVIDER_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showModelProvider,
                        advanced = true,
                    ),
                    toggle(
                        "message_details.diagnostics.name",
                        NativeThemeEditorTextKey.SHOW_MODEL_NAME,
                        NativeThemeEditorTextKey.SHOW_MODEL_NAME_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showModelName,
                        advanced = true,
                    ),
                    toggle(
                        "message_details.diagnostics.token_stats",
                        NativeThemeEditorTextKey.SHOW_MESSAGE_TOKEN_STATS,
                        NativeThemeEditorTextKey.SHOW_MESSAGE_TOKEN_STATS_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showMessageTokenStats,
                        advanced = true,
                    ),
                    toggle(
                        "message_details.diagnostics.timing_stats",
                        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMING_STATS,
                        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMING_STATS_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showMessageTimingStats,
                        advanced = true,
                    ),
                ),
        )

    val activity =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("message_details.activity"),
            title = NativeThemeEditorTextKey.MESSAGE_ACTIVITY,
            items =
                listOf(
                    toggle(
                        "message_details.activity.processing",
                        NativeThemeEditorTextKey.SHOW_INPUT_PROCESSING_STATUS,
                        NativeThemeEditorTextKey.SHOW_INPUT_PROCESSING_STATUS_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showInputProcessingStatus,
                    ),
                    toggle(
                        "message_details.activity.dots",
                        NativeThemeEditorTextKey.SHOW_CHAT_FLOATING_DOTS,
                        NativeThemeEditorTextKey.SHOW_CHAT_FLOATING_DOTS_DESCRIPTION,
                        NativeThemePreferenceSchemaV1.showChatFloatingDotsAnimation,
                    ),
                ),
        )

    val section =
        NativeThemeEditorSectionDefinitionV1(
            id = NativeThemeEditorSectionId("message_details"),
            title = NativeThemeEditorTextKey.MESSAGE_DETAILS_AND_MOTION,
            groups = listOf(reasoning, identity, diagnostics, activity),
        )
}
