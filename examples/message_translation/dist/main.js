"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.openTranslateDialog = openTranslateDialog;
const TRANSLATE_DIALOG_SCREEN = "dist/ui/translate_dialog.ui.js";
function registerToolPkg() {
    ToolPkg.registerChatMessageMenuItem({
        id: "translate_message",
        title: {
            zh: "翻译",
            en: "Translate",
        },
        icon: "translate",
        order: 20,
        senders: ["user", "ai"],
        dialog: {
            screen: TRANSLATE_DIALOG_SCREEN,
            title: {
                zh: "翻译消息",
                en: "Translate Message",
            },
        },
        function: openTranslateDialog,
    });
    return true;
}
function openTranslateDialog(event) {
    const payload = event.eventPayload;
    return {
        dialog: {
            state: {
                chatId: payload.chatId,
                messageIndex: payload.messageIndex,
                message: payload.message,
            },
            moduleSpec: {
                id: "translate_message_dialog",
                source: "builtin_message_translation",
            },
        },
    };
}
