"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const i18n_1 = require("../../i18n");
const worldbook_service_js_1 = require("../../shared/worldbook_service.js");
const CONTENT_SNIPPETS = [
    {
        id: "format_message_variable",
        label: "format_message_variable",
        description: "格式化输出当前消息/会话局部变量对象",
        content: "{{format_message_variable::stat_data}}"
    },
    {
        id: "format_chat_variable",
        label: "format_chat_variable",
        description: "兼容 chat 命名，效果与 message 作用域一致",
        content: "{{format_chat_variable::stat_data}}"
    },
    {
        id: "format_character_variable",
        label: "format_character_variable",
        description: "格式化输出角色变量对象",
        content: "{{format_character_variable::stat_data}}"
    },
    {
        id: "format_global_variable",
        label: "format_global_variable",
        description: "格式化输出全局变量对象",
        content: "{{format_global_variable::world_state}}"
    },
    {
        id: "get_message_variable",
        label: "get_message_variable",
        description: "读取当前消息/会话局部变量中的单个路径",
        content: "{{get_message_variable::stat_data.系统.积分}}"
    },
    {
        id: "get_chat_variable",
        label: "get_chat_variable",
        description: "兼容 chat 命名，效果与 message 作用域一致",
        content: "{{get_chat_variable::stat_data.系统.积分}}"
    },
    {
        id: "get_character_variable",
        label: "get_character_variable",
        description: "读取角色变量中的单个路径",
        content: "{{get_character_variable::stat_data.关系.好感}}"
    },
    {
        id: "get_global_variable",
        label: "get_global_variable",
        description: "读取全局变量中的单个路径",
        content: "{{get_global_variable::world_state.rules.version}}"
    },
    {
        id: "getvar_macro",
        label: "getvar",
        description: "本地变量路径读取简写",
        content: "<%= getvar('stat_data.心理.羁绊值') %>"
    },
    {
        id: "getglobalvar_macro",
        label: "getglobalvar",
        description: "全局变量路径读取简写",
        content: "<%= getglobalvar('world_state.rules.version') %>"
    },
    {
        id: "getvar_braces",
        label: "{{getvar::...}}",
        description: "本地变量路径读取的花括号版本",
        content: "{{getvar::stat_data.心理.羁绊值}}"
    },
    {
        id: "getglobalvar_braces",
        label: "{{getglobalvar::...}}",
        description: "全局变量路径读取的花括号版本",
        content: "{{getglobalvar::world_state.rules.version}}"
    },
    {
        id: "dot_shorthand",
        label: "{{.变量}}",
        description: "本地变量顶层键简写，仅支持单层变量名",
        content: "{{.stat_data}}"
    },
    {
        id: "dollar_shorthand",
        label: "{{$变量}}",
        description: "全局变量顶层键简写，仅支持单层变量名",
        content: "{{$world_state}}"
    },
    {
        id: "init_var_block",
        label: "[InitVar]初始化",
        description: "在条目中声明变量初始化区块",
        content: "[InitVar]初始化\n```json\n{\n  \"stat_data\": {\n    \"系统\": {\n      \"积分\": 0\n    }\n  }\n}\n```"
    },
    {
        id: "update_variable_patch",
        label: "UpdateVariable / JSONPatch",
        description: "在助手回复后写回变量的 JSONPatch 模板",
        content: "<UpdateVariable>\n<Analysis>描述本次变量变化原因</Analysis>\n<JSONPatch>\n```json\n[\n  {\n    \"op\": \"replace\",\n    \"path\": \"/stat_data/系统/积分\",\n    \"value\": 1\n  }\n]\n```\n</JSONPatch>\n</UpdateVariable>"
    },
    {
        id: "jsonpatch_only",
        label: "JSONPatch",
        description: "仅输出 JSONPatch 块，运行时同样会识别",
        content: "<JSONPatch>\n```json\n[\n  {\n    \"op\": \"replace\",\n    \"path\": \"/stat_data/系统/积分\",\n    \"value\": 1\n  }\n]\n```\n</JSONPatch>"
    }
];
const CHARACTER_CARD_MENU_WIDTH = 280;
const CHARACTER_CARD_MENU_MAX_HEIGHT = 320;
function resolveText() {
    const rawLocale = getLang();
    const locale = String(rawLocale || "").trim().toLowerCase();
    const preferredLocale = locale.startsWith("en") ? "en-US" : "zh-CN";
    return (0, i18n_1.resolveWorldBookI18n)(preferredLocale);
}
function normalizeFormInjectPosition(target, position) {
    if (target === "assistant") {
        return "at_depth";
    }
    return position === "prepend" || position === "at_depth" ? position : "append";
}
function appendSnippetContent(currentContent, snippetContent) {
    const normalizedSnippet = String(snippetContent || "").trim();
    if (!normalizedSnippet) {
        return currentContent;
    }
    const normalizedCurrent = String(currentContent || "");
    if (!normalizedCurrent.trim()) {
        return normalizedSnippet;
    }
    if (normalizedCurrent.endsWith("\n\n")) {
        return `${normalizedCurrent}${normalizedSnippet}`;
    }
    if (normalizedCurrent.endsWith("\n")) {
        return `${normalizedCurrent}\n${normalizedSnippet}`;
    }
    return `${normalizedCurrent}\n\n${normalizedSnippet}`;
}
function Screen(ctx) {
    const [entries, setEntries] = ctx.useState("entries", []);
    const [groups, setGroups] = ctx.useState("groups", []);
    const [expandedGroupIds, setExpandedGroupIds] = ctx.useState("expandedGroupIds", []);
    const [loading, setLoading] = ctx.useState("loading", false);
    const [hasLoadedOnce, setHasLoadedOnce] = ctx.useState("hasLoadedOnce", false);
    const [initialLoadInFlight, setInitialLoadInFlight] = ctx.useState("initialLoadInFlight", false);
    const [view, setView] = ctx.useState("view", "list");
    const [deletingEntryId, setDeletingEntryId] = ctx.useState("deletingEntryId", "");
    const [togglingEntryId, setTogglingEntryId] = ctx.useState("togglingEntryId", "");
    const [importing, setImporting] = ctx.useState("importing", false);
    const [showSearchBar, setShowSearchBar] = ctx.useState("showSearchBar", false);
    const [searchQuery, setSearchQuery] = ctx.useState("searchQuery", "");
    const [editId, setEditId] = ctx.useState("editId", "");
    const [importPath, setImportPath] = ctx.useState("importPath", "");
    const [formName, setFormName] = ctx.useState("formName", "");
    const [formContent, setFormContent] = ctx.useState("formContent", "");
    const [formKeywords, setFormKeywords] = ctx.useState("formKeywords", "");
    const [formIsRegex, setFormIsRegex] = ctx.useState("formIsRegex", false);
    const [formCaseSensitive, setFormCaseSensitive] = ctx.useState("formCaseSensitive", false);
    const [formAlwaysActive, setFormAlwaysActive] = ctx.useState("formAlwaysActive", false);
    const [formEnabled, setFormEnabled] = ctx.useState("formEnabled", true);
    const [formPriority, setFormPriority] = ctx.useState("formPriority", "50");
    const [formScanDepth, setFormScanDepth] = ctx.useState("formScanDepth", "0");
    const [formInjectTarget, setFormInjectTarget] = ctx.useState("formInjectTarget", "system");
    const [formInjectPosition, setFormInjectPosition] = ctx.useState("formInjectPosition", "append");
    const [formInsertionDepth, setFormInsertionDepth] = ctx.useState("formInsertionDepth", "0");
    const [formCharacterCardId, setFormCharacterCardId] = ctx.useState("formCharacterCardId", "");
    const [showCardPicker, setShowCardPicker] = ctx.useState("showCardPicker", false);
    const [showSnippetPicker, setShowSnippetPicker] = ctx.useState("showSnippetPicker", false);
    const [loadingCards, setLoadingCards] = ctx.useState("loadingCards", false);
    const [availableCards, setAvailableCards] = ctx.useState("availableCards", []);
    const [hasLoadedCards, setHasLoadedCards] = ctx.useState("hasLoadedCards", false);
    const formContentRef = ctx.useRef("formContentRef", "");
    formContentRef.current = formContent;
    // Group form state
    const [groupFormName, setGroupFormName] = ctx.useState("groupFormName", "");
    const [groupFormEntryIds, setGroupFormEntryIds] = ctx.useState("groupFormEntryIds", []);
    const [groupFormCardIds, setGroupFormCardIds] = ctx.useState("groupFormCardIds", []);
    const [editingGroupId, setEditingGroupId] = ctx.useState("editingGroupId", "");
    const [deletingGroupId, setDeletingGroupId] = ctx.useState("deletingGroupId", "");
    const [copyingEntryId, setCopyingEntryId] = ctx.useState("copyingEntryId", "");
    const [confirmDeleteEntryId, setConfirmDeleteEntryId] = ctx.useState("confirmDeleteEntryId", "");
    const t = resolveText();
    const colors = ctx.MaterialTheme.colorScheme;
    const { UI } = ctx;
    const effectiveInjectPosition = normalizeFormInjectPosition(formInjectTarget, formInjectPosition);
    function getFormContentValue() {
        return formContentRef.current;
    }
    function setFormContentValue(value) {
        formContentRef.current = value;
        setFormContent(value);
    }
    function resetForm() {
        setEditId("");
        setFormName("");
        setFormContentValue("");
        setFormKeywords("");
        setFormIsRegex(false);
        setFormCaseSensitive(false);
        setFormAlwaysActive(false);
        setFormEnabled(true);
        setFormPriority("50");
        setFormScanDepth("0");
        setFormInjectTarget("system");
        setFormInjectPosition("append");
        setFormInsertionDepth("0");
        setFormCharacterCardId("");
        setShowCardPicker(false);
        setShowSnippetPicker(false);
        setLoadingCards(false);
    }
    function resetImportForm() {
        setImportPath("");
        setImporting(false);
    }
    async function loadEntries(force = false) {
        if (loading || (hasLoadedOnce && !force)) {
            return;
        }
        setLoading(true);
        try {
            const [fetchedEntries, fetchedGroups] = await Promise.all([
                (0, worldbook_service_js_1.listWorldBookEntries)(),
                (0, worldbook_service_js_1.listWorldBookGroups)()
            ]);
            setEntries(fetchedEntries);
            setGroups(fetchedGroups);
        }
        catch (error) {
            ctx.showToast(`${t.toastLoadFailedPrefix}${error.message}`);
        }
        finally {
            setLoading(false);
            setHasLoadedOnce(true);
        }
    }
    async function queryCharacterCards(showErrorToast) {
        try {
            const cards = await (0, worldbook_service_js_1.listWorldBookCharacterCards)();
            setAvailableCards(cards);
            setHasLoadedCards(true);
            return { success: true, cards };
        }
        catch (error) {
            if (showErrorToast) {
                ctx.showToast(`${t.toastRoleCardLoadFailedPrefix}${error.message}`);
            }
        }
        return { success: false, cards: [] };
    }
    async function ensureCharacterCardsLoaded(showErrorToast) {
        if (hasLoadedCards) {
            return availableCards;
        }
        const result = await queryCharacterCards(showErrorToast);
        return result.cards;
    }
    function findCharacterCard(cardId) {
        const targetId = String(cardId || "").trim();
        if (!targetId) {
            return null;
        }
        return availableCards.find((card) => String(card.id || "").trim() === targetId) || null;
    }
    function resolveCharacterCardName(cardId) {
        const matchedCard = findCharacterCard(cardId);
        return String(matchedCard?.name || "").trim();
    }
    function getCharacterCardLabel(cardId) {
        return resolveCharacterCardName(cardId) || t.dropdownBoundCard;
    }
    function selectInjectTarget(target) {
        setFormInjectTarget(target);
        if (target === "assistant") {
            setFormInjectPosition("at_depth");
        }
    }
    function selectInjectPosition(position) {
        if (formInjectTarget === "assistant" && position !== "at_depth") {
            return;
        }
        setFormInjectPosition(position);
    }
    function toggleSearchBar() {
        const nextVisible = !showSearchBar;
        setShowSearchBar(nextVisible);
        if (!nextVisible) {
            setSearchQuery("");
        }
    }
    function matchesEntrySearch(entry, query) {
        const normalizedQuery = String(query || "").trim().toLowerCase();
        if (!normalizedQuery) {
            return true;
        }
        const characterCardName = resolveCharacterCardName(entry.character_card_id || "");
        const haystack = [entry.name, ...(entry.keywords || []), characterCardName]
            .map((value) => String(value || "").trim().toLowerCase())
            .filter((value) => value.length > 0);
        return haystack.some((value) => value.includes(normalizedQuery));
    }
    function insertSnippet(snippetContent) {
        setFormContentValue(appendSnippetContent(getFormContentValue(), snippetContent));
        setShowSnippetPicker(false);
        ctx.showToast(t.toastSnippetInserted);
    }
    function getInjectTargetTag(entry) {
        if (entry.inject_target === "assistant") {
            return t.tagInjectAssistant;
        }
        if (entry.inject_target === "user") {
            return t.tagInjectUser;
        }
        return t.tagInjectSystem;
    }
    function getInjectPositionTag(entry) {
        if (entry.inject_position === "prepend") {
            return t.tagPositionPrepend;
        }
        if (entry.inject_position === "at_depth") {
            return t.tagPositionAtDepth(entry.insertion_depth ?? 0);
        }
        return t.tagPositionAppend;
    }
    async function doToggle(id) {
        if (togglingEntryId === id) {
            return;
        }
        setTogglingEntryId(id);
        try {
            await (0, worldbook_service_js_1.toggleWorldBookEntry)(id);
            ctx.showToast(t.toastToggleDone);
            await loadEntries(true);
        }
        catch (error) {
            ctx.showToast(`${t.toastActionFailedPrefix}${error.message}`);
        }
        finally {
            setTogglingEntryId("");
        }
    }
    async function doDelete(id, name) {
        if (deletingEntryId === id) {
            return;
        }
        setDeletingEntryId(id);
        try {
            await (0, worldbook_service_js_1.deleteWorldBookEntry)(id);
            ctx.showToast(`${t.toastDeletedPrefix}${name}`);
            await loadEntries(true);
        }
        catch (error) {
            ctx.showToast(`${t.toastDeleteFailedPrefix}${error.message}`);
        }
        finally {
            setDeletingEntryId("");
        }
    }
    async function doEdit(id) {
        setConfirmDeleteEntryId("");
        try {
            const entry = await (0, worldbook_service_js_1.getWorldBookEntry)(id);
            setEditId(entry.id);
            setFormName(entry.name || "");
            setFormContentValue(entry.content || "");
            setFormKeywords((entry.keywords || []).join("，"));
            setFormIsRegex(entry.is_regex === true);
            setFormCaseSensitive(entry.case_sensitive === true);
            setFormAlwaysActive(entry.always_active === true);
            setFormEnabled(entry.enabled !== false);
            setFormPriority(String(entry.priority ?? 50));
            setFormScanDepth(String(entry.scan_depth ?? 0));
            setFormInjectTarget(entry.inject_target || "system");
            setFormInjectPosition(normalizeFormInjectPosition(entry.inject_target || "system", entry.inject_position));
            setFormInsertionDepth(String(entry.insertion_depth ?? 0));
            setFormCharacterCardId(entry.character_card_id || "");
            setShowCardPicker(false);
            setShowSnippetPicker(false);
            setLoadingCards(false);
            if (entry.character_card_id && !findCharacterCard(entry.character_card_id)) {
                await ensureCharacterCardsLoaded(true);
            }
            setView("edit");
        }
        catch (error) {
            ctx.showToast(`${t.toastLoadFailedPrefix}${error.message}`);
        }
    }
    async function loadCardPicker() {
        if (showCardPicker) {
            setShowCardPicker(false);
            setLoadingCards(false);
            return;
        }
        setShowCardPicker(true);
        if (hasLoadedCards) {
            setLoadingCards(false);
            return;
        }
        setLoadingCards(true);
        const result = await queryCharacterCards(true);
        setLoadingCards(false);
        if (!result.success) {
            setShowCardPicker(false);
        }
    }
    function doPickCard(cardId) {
        setFormCharacterCardId(cardId || "");
        setShowCardPicker(false);
        setLoadingCards(false);
    }
    function doClearCardBinding() {
        setFormCharacterCardId("");
        setShowCardPicker(false);
        setLoadingCards(false);
    }
    function getSelectedCardLabel() {
        if (!formCharacterCardId) {
            return t.dropdownNoCharacterCard;
        }
        return getCharacterCardLabel(formCharacterCardId);
    }
    function toggleCardPicker() {
        return loadCardPicker();
    }
    function doCreate() {
        resetForm();
        setView("create");
    }
    function doOpenImport() {
        resetImportForm();
        setView("import");
    }
    async function doPickImportFile() {
        try {
            const result = await ctx.openFilePicker({
                mimeTypes: ["application/json", "text/plain", "*/*"],
                allowMultiple: false,
                persistPermission: true
            });
            if (result.cancelled || result.files.length === 0) {
                return;
            }
            setImportPath(String(result.files[0]?.path || result.files[0]?.uri || "").trim());
        }
        catch (error) {
            ctx.showToast(`${t.toastFailedPrefix}${error.message}`);
        }
    }
    async function doImport() {
        if (importing) {
            return;
        }
        if (!importPath.trim()) {
            ctx.showToast(t.toastImportPathRequired);
            return;
        }
        const payload = {
            path: importPath.trim()
        };
        setImporting(true);
        try {
            const result = await (0, worldbook_service_js_1.importWorldBookEntries)(payload);
            ctx.showToast(t.toastImportDone(result.imported_count, result.warning_count));
            setView("list");
            resetImportForm();
            await loadEntries(true);
        }
        catch (error) {
            ctx.showToast(`${t.toastFailedPrefix}${error.message}`);
        }
        finally {
            setImporting(false);
        }
    }
    async function doSave() {
        const currentFormContent = getFormContentValue();
        if (!formName.trim()) {
            ctx.showToast(t.toastNameRequired);
            return;
        }
        if (!currentFormContent.trim()) {
            ctx.showToast(t.toastContentRequired);
            return;
        }
        const isEdit = view === "edit" && !!editId;
        const normalizedInsertionDepth = Math.max(0, Number.parseInt(formInsertionDepth, 10) || 0);
        const payload = {
            name: formName.trim(),
            content: currentFormContent.trim(),
            keywords: formKeywords.trim(),
            is_regex: formIsRegex,
            case_sensitive: formCaseSensitive,
            always_active: formAlwaysActive,
            enabled: formEnabled,
            priority: Number.parseInt(formPriority, 10) || 50,
            scan_depth: Number.parseInt(formScanDepth, 10) || 0,
            inject_target: formInjectTarget,
            inject_position: effectiveInjectPosition,
            insertion_depth: normalizedInsertionDepth,
            character_card_id: formCharacterCardId.trim()
        };
        if (isEdit) {
            payload.id = editId;
        }
        try {
            if (isEdit) {
                await (0, worldbook_service_js_1.updateWorldBookEntry)(payload);
            }
            else {
                await (0, worldbook_service_js_1.createWorldBookEntry)(payload);
            }
            ctx.showToast(isEdit ? t.toastUpdated : t.toastCreated);
            setView("list");
            resetForm();
            await loadEntries(true);
        }
        catch (error) {
            ctx.showToast(`${t.toastSaveFailedPrefix}${error.message}`);
        }
    }
    async function doRefresh() {
        await Promise.all([loadEntries(true), queryCharacterCards(false)]);
    }
    function renderTag(label, backgroundColor, textColor) {
        return UI.Box({
            modifier: ctx.Modifier.clip({ cornerRadius: 8 }).background(backgroundColor)
        }, [
            UI.Text({
                text: label,
                style: "labelSmall",
                color: textColor,
                fontSize: 9,
                maxLines: 1,
                padding: { horizontal: 6, vertical: 2 }
            })
        ]);
    }
    function renderHeaderTag(label, backgroundColor, textColor) {
        return UI.Surface({
            shape: { cornerRadius: 8 },
            containerColor: backgroundColor
        }, [
            UI.Text({
                text: label,
                style: "labelSmall",
                color: textColor,
                fontSize: 9,
                padding: { horizontal: 6, vertical: 2 }
            })
        ]);
    }
    function renderSettingRow(title, description, checked, onCheckedChange) {
        return UI.Row({
            fillMaxWidth: true,
            horizontalArrangement: "spaceBetween",
            verticalAlignment: "center"
        }, [
            UI.Column({
                weight: 1,
                spacing: 2
            }, [
                UI.Text({
                    text: title,
                    color: colors.onSurface,
                    fontWeight: "bold"
                }),
                UI.Text({
                    text: description,
                    style: "bodySmall",
                    color: colors.onSurfaceVariant
                })
            ]),
            UI.Spacer({ width: 12 }),
            UI.Switch({
                checked,
                onCheckedChange
            })
        ]);
    }
    function renderCard(entry) {
        const isDeleting = deletingEntryId === entry.id;
        const isToggling = togglingEntryId === entry.id;
        const isEntryBusy = isDeleting || isToggling;
        const keywordText = entry.keywords && entry.keywords.length > 0 ? entry.keywords.join("、") : t.keywordEmpty;
        const characterCardName = resolveCharacterCardName(entry.character_card_id || "");
        const infoPills = [
            renderTag(keywordText, colors.secondaryContainer.copy({ alpha: 0.6 }), colors.onSecondaryContainer),
            renderTag(entry.always_active ? t.tagAlwaysActive : t.tagKeywordTrigger, colors.secondaryContainer.copy({ alpha: 0.6 }), colors.onSecondaryContainer),
            renderTag(t.tagPriority(entry.priority ?? 50), colors.secondaryContainer.copy({ alpha: 0.6 }), colors.onSecondaryContainer),
            renderTag(t.tagScanDepth(entry.scan_depth ?? 0), colors.secondaryContainer.copy({ alpha: 0.6 }), colors.onSecondaryContainer),
            renderTag(getInjectTargetTag(entry), colors.tertiaryContainer.copy({ alpha: 0.7 }), colors.onTertiaryContainer),
            renderTag(getInjectPositionTag(entry), colors.tertiaryContainer.copy({ alpha: 0.55 }), colors.onTertiaryContainer),
            entry.character_card_id
                ? renderTag(characterCardName ? t.tagCharacterCard(characterCardName) : t.dropdownBoundCard, colors.primaryContainer.copy({ alpha: 0.7 }), colors.onPrimaryContainer)
                : null
        ].filter(Boolean);
        return UI.Card({
            key: entry.id,
            containerColor: colors.surface,
            elevation: 1,
            modifier: ctx.Modifier.fillMaxWidth().clickable(() => {
                if (!isEntryBusy) {
                    return doEdit(entry.id);
                }
                return undefined;
            })
        }, [
            UI.Column({
                padding: 12,
                fillMaxWidth: true
            }, [
                UI.Row({
                    fillMaxWidth: true,
                    verticalAlignment: "center"
                }, [
                    UI.Box({
                        width: 28,
                        height: 28,
                        contentAlignment: "center",
                        modifier: ctx.Modifier
                            .clip({ cornerRadius: 6 })
                            .background(colors.primaryContainer)
                    }, [
                        UI.Icon({
                            name: "extension",
                            tint: colors.onPrimaryContainer,
                            size: 16
                        })
                    ]),
                    UI.Spacer({ width: 10 }),
                    UI.Column({
                        weight: 1
                    }, [
                        UI.Row({
                            verticalAlignment: "center"
                        }, [
                            UI.Text({
                                text: entry.name,
                                style: "bodyMedium",
                                fontWeight: "medium",
                                maxLines: 1,
                                overflow: "ellipsis",
                                weight: 1,
                                weightFill: false
                            }),
                            entry.always_active ? UI.Spacer({ width: 6 }) : null,
                            entry.always_active
                                ? renderHeaderTag(t.tagPinned, colors.primary.copy({ alpha: 0.1 }), colors.primary)
                                : null,
                            entry.is_regex ? UI.Spacer({ width: 6 }) : null,
                            entry.is_regex
                                ? renderHeaderTag(t.tagRegex, colors.secondary.copy({ alpha: 0.1 }), colors.secondary)
                                : null
                        ].filter(Boolean))
                    ].filter(Boolean)),
                    UI.Switch({
                        checked: entry.enabled,
                        onCheckedChange: (_checked) => doToggle(entry.id),
                        enabled: !isEntryBusy,
                        checkedThumbColor: colors.primary,
                        checkedTrackColor: colors.primaryContainer,
                        uncheckedThumbColor: colors.outline,
                        uncheckedTrackColor: colors.surfaceVariant,
                        modifier: ctx.Modifier.scale(0.8)
                    })
                ]),
                UI.Spacer({ height: 8 }),
                UI.Box({
                    modifier: ctx.Modifier
                        .fillMaxWidth()
                        .clip({ cornerRadius: 12 })
                        .background(colors.surfaceVariant.copy({ alpha: 0.18 }))
                        .clickable(() => {
                        if (!isEntryBusy) {
                            return doEdit(entry.id);
                        }
                        return undefined;
                    })
                        .padding({ horizontal: 8, vertical: 6 })
                }, [
                    UI.Row({
                        fillMaxWidth: true,
                        verticalAlignment: "center"
                    }, [
                        UI.LazyRow({
                            weight: 1,
                            spacing: 4
                        }, infoPills),
                        UI.Spacer({ width: 6 }),
                        UI.Icon({
                            name: "arrowForward",
                            size: 14,
                            tint: colors.onSurfaceVariant.copy({ alpha: 0.7 })
                        })
                    ])
                ]),
                UI.Spacer({ height: 8 }),
                UI.Row({
                    fillMaxWidth: true,
                    spacing: 8
                }, [
                    UI.OutlinedButton({
                        onClick: () => doEdit(entry.id),
                        enabled: !isEntryBusy,
                        weight: 1,
                        height: 32,
                        contentPadding: { horizontal: 12 }
                    }, [
                        UI.Text({
                            text: t.buttonEdit,
                            style: "labelMedium",
                            fontSize: 12
                        })
                    ]),
                    UI.OutlinedButton({
                        onClick: () => doCopyEntry(entry.id),
                        enabled: !isEntryBusy,
                        weight: 1,
                        height: 32,
                        contentPadding: { horizontal: 12 }
                    }, [
                        UI.Text({
                            text: t.buttonCopy,
                            style: "labelMedium",
                            fontSize: 12
                        })
                    ]),
                    UI.OutlinedButton({
                        onClick: () => {
                            if (confirmDeleteEntryId === entry.id) {
                                setConfirmDeleteEntryId("");
                                return doDelete(entry.id, entry.name);
                            }
                            setConfirmDeleteEntryId(entry.id);
                            return undefined;
                        },
                        enabled: !isEntryBusy,
                        weight: 1,
                        height: 32,
                        contentPadding: { horizontal: 12 }
                    }, [
                        UI.Text({
                            text: confirmDeleteEntryId === entry.id ? t.groupDeleteConfirm : t.buttonDelete,
                            style: "labelMedium",
                            fontSize: 12,
                            color: confirmDeleteEntryId === entry.id ? colors.error : undefined
                        })
                    ])
                ])
            ].filter(Boolean))
        ]);
    }
    // === Group helper functions ===
    function toggleGroupExpand(groupId) {
        const current = expandedGroupIds;
        if (current.includes(groupId)) {
            setExpandedGroupIds(current.filter((id) => id !== groupId));
        }
        else {
            setExpandedGroupIds([...current, groupId]);
        }
    }
    function getEntriesByGroup(entryIds) {
        return entries.filter((e) => entryIds.includes(e.id));
    }
    function getUngroupedEntries() {
        const allGroupedIds = new Set();
        for (const g of groups) {
            for (const eid of g.entry_ids) {
                allGroupedIds.add(eid);
            }
        }
        return entries.filter((e) => !allGroupedIds.has(e.id));
    }
    function getGroupEntryCount(group) {
        return entries.filter((e) => group.entry_ids.includes(e.id)).length;
    }
    // === Group actions ===
    function doOpenGroupCreate() {
        setGroupFormName("");
        setGroupFormEntryIds([]);
        setGroupFormCardIds([]);
        setEditingGroupId("");
        setView("group_create");
    }
    function doOpenGroupEdit(group) {
        setGroupFormName(group.name);
        setGroupFormEntryIds([...group.entry_ids]);
        setGroupFormCardIds([...group.character_card_ids]);
        setEditingGroupId(group.id);
        setView("group_edit");
    }
    function doOpenGroupDelete(groupId) {
        setDeletingGroupId(groupId);
        setView("group_delete");
    }
    async function doSaveGroup() {
        if (!groupFormName.trim()) {
            ctx.showToast(t.groupNamePlaceholder);
            return;
        }
        try {
            if (editingGroupId) {
                await (0, worldbook_service_js_1.updateWorldBookGroup)({
                    id: editingGroupId,
                    name: groupFormName.trim(),
                    entry_ids: groupFormEntryIds,
                    character_card_ids: groupFormCardIds
                });
                ctx.showToast(t.groupToastUpdated);
            }
            else {
                await (0, worldbook_service_js_1.createWorldBookGroup)({
                    name: groupFormName.trim(),
                    entry_ids: groupFormEntryIds,
                    character_card_ids: groupFormCardIds
                });
                ctx.showToast(t.groupToastCreated);
            }
            setView("list");
            await loadEntries(true);
        }
        catch (error) {
            ctx.showToast(`${t.toastActionFailedPrefix}${error.message}`);
        }
    }
    async function doDeleteGroup(deleteEntries) {
        try {
            await (0, worldbook_service_js_1.deleteWorldBookGroup)(deletingGroupId, deleteEntries);
            ctx.showToast(t.groupToastDeleted);
            setView("list");
            await loadEntries(true);
        }
        catch (error) {
            ctx.showToast(`${t.toastActionFailedPrefix}${error.message}`);
        }
    }
    function doCopyEntry(entryId) {
        setConfirmDeleteEntryId("");
        setCopyingEntryId(entryId);
        setView("copy_to_group");
    }
    async function doCopyToGroup(targetGroupId) {
        try {
            await (0, worldbook_service_js_1.copyEntryToGroup)(copyingEntryId, targetGroupId);
            ctx.showToast(t.copyToastDone);
            setView("list");
            await loadEntries(true);
        }
        catch (error) {
            ctx.showToast(`${t.toastActionFailedPrefix}${error.message}`);
        }
    }
    function toggleGroupFormEntry(entryId) {
        if (groupFormEntryIds.includes(entryId)) {
            setGroupFormEntryIds(groupFormEntryIds.filter((id) => id !== entryId));
        }
        else {
            setGroupFormEntryIds([...groupFormEntryIds, entryId]);
        }
    }
    function toggleGroupFormCard(cardId) {
        if (groupFormCardIds.includes(cardId)) {
            setGroupFormCardIds(groupFormCardIds.filter((id) => id !== cardId));
        }
        else {
            setGroupFormCardIds([...groupFormCardIds, cardId]);
        }
    }
    // === Group rendering ===
    function renderGroupHeader(group) {
        const isExpanded = expandedGroupIds.includes(group.id);
        const entryCount = getGroupEntryCount(group);
        const cardNames = group.character_card_ids
            .map((cid) => resolveCharacterCardName(cid))
            .filter((n) => n.length > 0);
        return UI.Column({
            key: `group_${group.id}`,
            fillMaxWidth: true,
            spacing: 0
        }, [
            UI.Box({
                fillMaxWidth: true,
                modifier: ctx.Modifier
                    .clip({ cornerRadius: 12 })
                    .background(colors.secondaryContainer.copy({ alpha: 0.25 }))
                    .clickable(() => toggleGroupExpand(group.id))
                    .padding({ horizontal: 14, vertical: 10 })
            }, [
                UI.Row({
                    fillMaxWidth: true,
                    verticalAlignment: "center"
                }, [
                    UI.Icon({
                        name: isExpanded ? "expandLess" : "expandMore",
                        size: 20,
                        tint: colors.onSecondaryContainer
                    }),
                    UI.Spacer({ width: 8 }),
                    UI.Column({ weight: 1 }, [
                        UI.Text({
                            text: `${group.name}（${entryCount}）`,
                            style: "bodyLarge",
                            fontWeight: "bold",
                            color: colors.onSurface
                        }),
                        cardNames.length > 0
                            ? UI.Text({
                                text: cardNames.join(", "),
                                style: "bodySmall",
                                color: colors.onSurfaceVariant,
                                maxLines: 1,
                                overflow: "ellipsis"
                            })
                            : null
                    ].filter(Boolean)),
                    UI.Box({
                        modifier: ctx.Modifier.clickable(() => doOpenGroupEdit(group))
                    }, [UI.Icon({ name: "settings", size: 18, tint: colors.onSurfaceVariant })]),
                    UI.Spacer({ width: 8 }),
                    UI.Box({
                        modifier: ctx.Modifier.clickable(() => doOpenGroupDelete(group.id))
                    }, [UI.Icon({ name: "delete", size: 18, tint: colors.error })])
                ])
            ]),
            isExpanded
                ? UI.Column({ fillMaxWidth: true, spacing: 10, paddingTop: 8 }, getEntriesByGroup(group.entry_ids).map((entry) => renderCard(entry)))
                : null
        ].filter(Boolean));
    }
    function renderUngroupedSection() {
        const ungrouped = getUngroupedEntries();
        if (ungrouped.length === 0)
            return null;
        const isExpanded = expandedGroupIds.includes("__ungrouped__");
        return UI.Column({
            key: "group_ungrouped",
            fillMaxWidth: true,
            spacing: 0
        }, [
            UI.Box({
                fillMaxWidth: true,
                modifier: ctx.Modifier
                    .clip({ cornerRadius: 12 })
                    .background(colors.surfaceVariant.copy({ alpha: 0.15 }))
                    .clickable(() => toggleGroupExpand("__ungrouped__"))
                    .padding({ horizontal: 14, vertical: 10 })
            }, [
                UI.Row({
                    fillMaxWidth: true,
                    verticalAlignment: "center"
                }, [
                    UI.Icon({
                        name: isExpanded ? "expandLess" : "expandMore",
                        size: 20,
                        tint: colors.onSurfaceVariant
                    }),
                    UI.Spacer({ width: 8 }),
                    UI.Text({
                        text: `${t.groupUngrouped}（${ungrouped.length}）`,
                        style: "bodyLarge",
                        fontWeight: "bold",
                        color: colors.onSurfaceVariant
                    })
                ])
            ]),
            isExpanded
                ? UI.Column({ fillMaxWidth: true, spacing: 10, paddingTop: 8 }, ungrouped.map((entry) => renderCard(entry)))
                : null
        ].filter(Boolean));
    }
    // === Group form views ===
    function renderGroupForm() {
        const isEdit = view === "group_edit";
        return UI.Column({ padding: 12, spacing: 12, fillMaxWidth: true }, [
            UI.Row({ fillMaxWidth: true }, [
                UI.OutlinedButton({ onClick: () => setView("list"), shape: { cornerRadius: 12 } }, [
                    UI.Row({ spacing: 6, verticalAlignment: "center" }, [
                        UI.Icon({ name: "arrowBack", size: 16, tint: colors.onSurface }),
                        UI.Text({ text: t.buttonBack, color: colors.onSurface, fontWeight: "bold" })
                    ])
                ])
            ]),
            UI.Text({
                text: isEdit ? t.groupEditTitle : t.groupCreateTitle,
                style: "titleMedium",
                fontWeight: "bold",
                color: colors.onSurface
            }),
            UI.TextField({
                label: t.groupNameLabel,
                placeholder: t.groupNamePlaceholder,
                value: groupFormName,
                onValueChange: setGroupFormName,
                singleLine: true,
                fillMaxWidth: true
            }),
            // Entry selection
            UI.Text({
                text: t.groupSelectEntries,
                style: "labelLarge",
                fontWeight: "bold",
                color: colors.onSurface
            }),
            UI.Text({
                text: t.groupSelectEntriesHint,
                style: "bodySmall",
                color: colors.onSurfaceVariant
            }),
            UI.Column({ fillMaxWidth: true, spacing: 4 }, entries.filter((entry) => {
                // Show: entries already in this group's form selection, or ungrouped entries
                if (groupFormEntryIds.includes(entry.id))
                    return true;
                // Check if this entry belongs to another group
                for (const g of groups) {
                    if (g.id === editingGroupId)
                        continue; // skip current group being edited
                    if (g.entry_ids.includes(entry.id))
                        return false;
                }
                return true;
            }).map((entry) => UI.Row({
                key: `gf_entry_${entry.id}`,
                fillMaxWidth: true,
                verticalAlignment: "center",
                modifier: ctx.Modifier
                    .clip({ cornerRadius: 8 })
                    .clickable(() => toggleGroupFormEntry(entry.id))
                    .padding({ horizontal: 8, vertical: 6 })
            }, [
                UI.Switch({
                    checked: groupFormEntryIds.includes(entry.id),
                    onCheckedChange: () => toggleGroupFormEntry(entry.id),
                    modifier: ctx.Modifier.scale(0.7)
                }),
                UI.Spacer({ width: 8 }),
                UI.Text({
                    text: entry.name,
                    style: "bodyMedium",
                    color: colors.onSurface,
                    maxLines: 1,
                    overflow: "ellipsis",
                    weight: 1
                })
            ]))),
            // Character card multi-binding
            UI.Text({
                text: t.groupBindCards,
                style: "labelLarge",
                fontWeight: "bold",
                color: colors.onSurface
            }),
            UI.Text({
                text: t.groupBindCardsHint,
                style: "bodySmall",
                color: colors.onSurfaceVariant
            }),
            UI.Column({ fillMaxWidth: true, spacing: 4 }, availableCards.map((card) => UI.Row({
                key: `gf_card_${card.id}`,
                fillMaxWidth: true,
                verticalAlignment: "center",
                modifier: ctx.Modifier
                    .clip({ cornerRadius: 8 })
                    .clickable(() => toggleGroupFormCard(card.id))
                    .padding({ horizontal: 8, vertical: 6 })
            }, [
                UI.Switch({
                    checked: groupFormCardIds.includes(card.id),
                    onCheckedChange: () => toggleGroupFormCard(card.id),
                    modifier: ctx.Modifier.scale(0.7)
                }),
                UI.Spacer({ width: 8 }),
                UI.Text({
                    text: card.name || card.id,
                    style: "bodyMedium",
                    color: colors.onSurface,
                    maxLines: 1,
                    overflow: "ellipsis",
                    weight: 1
                })
            ]))),
            groupFormCardIds.length > 0
                ? UI.Text({
                    text: t.multiCardSelected(groupFormCardIds.length),
                    style: "bodySmall",
                    color: colors.primary
                })
                : null,
            UI.Button({
                text: isEdit ? t.groupSaveButton : t.groupCreateButton,
                onClick: () => doSaveGroup(),
                fillMaxWidth: true,
                shape: { cornerRadius: 14 }
            })
        ].filter(Boolean));
    }
    function renderGroupDeleteView() {
        return UI.Column({ padding: 16, spacing: 16, fillMaxWidth: true }, [
            UI.Text({
                text: t.groupDeleteTitle,
                style: "titleMedium",
                fontWeight: "bold",
                color: colors.onSurface
            }),
            UI.Button({
                text: t.groupDeleteOnlyGroup,
                onClick: () => doDeleteGroup(false),
                fillMaxWidth: true,
                shape: { cornerRadius: 14 }
            }),
            UI.OutlinedButton({
                onClick: () => doDeleteGroup(true),
                fillMaxWidth: true,
                shape: { cornerRadius: 14 }
            }, [
                UI.Text({
                    text: t.groupDeleteWithEntries,
                    color: colors.error,
                    fontWeight: "bold"
                })
            ]),
            UI.OutlinedButton({
                onClick: () => setView("list"),
                fillMaxWidth: true,
                shape: { cornerRadius: 14 }
            }, [
                UI.Text({
                    text: t.groupDeleteCancel,
                    color: colors.onSurface
                })
            ])
        ]);
    }
    function renderCopyToGroupView() {
        return UI.Column({ padding: 16, spacing: 12, fillMaxWidth: true }, [
            UI.Row({ fillMaxWidth: true }, [
                UI.OutlinedButton({ onClick: () => setView("list"), shape: { cornerRadius: 12 } }, [
                    UI.Row({ spacing: 6, verticalAlignment: "center" }, [
                        UI.Icon({ name: "arrowBack", size: 16, tint: colors.onSurface }),
                        UI.Text({ text: t.buttonBack, color: colors.onSurface, fontWeight: "bold" })
                    ])
                ])
            ]),
            UI.Text({
                text: t.copyToGroupTitle,
                style: "titleMedium",
                fontWeight: "bold",
                color: colors.onSurface
            }),
            UI.Text({
                text: t.copyToGroupHint,
                style: "bodySmall",
                color: colors.onSurfaceVariant
            }),
            // Ungrouped option
            UI.Box({
                fillMaxWidth: true,
                modifier: ctx.Modifier
                    .clip({ cornerRadius: 10 })
                    .background(colors.surfaceVariant.copy({ alpha: 0.3 }))
                    .clickable(() => doCopyToGroup(""))
                    .padding({ horizontal: 14, vertical: 12 })
            }, [
                UI.Text({
                    text: t.copyToUngrouped,
                    color: colors.onSurface
                })
            ]),
            // Group options
            ...groups.map((g) => UI.Box({
                key: `copy_g_${g.id}`,
                fillMaxWidth: true,
                modifier: ctx.Modifier
                    .clip({ cornerRadius: 10 })
                    .background(colors.secondaryContainer.copy({ alpha: 0.2 }))
                    .clickable(() => doCopyToGroup(g.id))
                    .padding({ horizontal: 14, vertical: 12 })
            }, [
                UI.Text({
                    text: `${g.name}（${getGroupEntryCount(g)}）`,
                    color: colors.onSurface
                })
            ]))
        ]);
    }
    function renderSnippetDialog() {
        return UI.Box({
            fillMaxSize: true,
            contentAlignment: "center"
        }, [
            UI.Surface({
                fillMaxSize: true,
                containerColor: colors.scrim,
                alpha: 0.35,
                onClick: () => setShowSnippetPicker(false)
            }),
            UI.Card({
                fillMaxWidth: true,
                containerColor: colors.surface,
                shape: { cornerRadius: 20 },
                elevation: 8,
                modifier: ctx.Modifier.padding({ horizontal: 20, vertical: 32 })
            }, [
                UI.Column({
                    fillMaxWidth: true,
                    padding: 16,
                    spacing: 12
                }, [
                    UI.Row({
                        fillMaxWidth: true,
                        horizontalArrangement: "spaceBetween",
                        verticalAlignment: "center"
                    }, [
                        UI.Column({
                            weight: 1,
                            spacing: 4
                        }, [
                            UI.Text({
                                text: t.fieldContentTemplates,
                                style: "titleMedium",
                                fontWeight: "bold",
                                color: colors.onSurface
                            }),
                            UI.Text({
                                text: t.fieldContentTemplatesHint,
                                style: "bodySmall",
                                color: colors.onSurfaceVariant
                            })
                        ]),
                        UI.IconButton({
                            onClick: () => setShowSnippetPicker(false),
                            icon: Icons.Close
                        }, [])
                    ]),
                    UI.LazyColumn({
                        fillMaxWidth: true,
                        spacing: 8,
                        height: 360
                    }, CONTENT_SNIPPETS.flatMap((snippet, index) => {
                        const nodes = [
                            UI.Box({
                                modifier: ctx.Modifier
                                    .fillMaxWidth()
                                    .clip({ cornerRadius: 12 })
                                    .clickable(() => insertSnippet(snippet.content))
                                    .padding({ horizontal: 2, vertical: 2 })
                            }, [
                                UI.Column({
                                    fillMaxWidth: true,
                                    padding: 12,
                                    spacing: 4
                                }, [
                                    UI.Text({
                                        text: snippet.label,
                                        color: colors.onSurface,
                                        fontWeight: "bold"
                                    }),
                                    UI.Text({
                                        text: snippet.description,
                                        style: "bodySmall",
                                        color: colors.onSurfaceVariant
                                    })
                                ])
                            ])
                        ];
                        if (index < CONTENT_SNIPPETS.length - 1) {
                            nodes.push(UI.HorizontalDivider({
                                color: colors.outlineVariant.copy({ alpha: 0.6 }),
                                thickness: 1
                            }));
                        }
                        return nodes;
                    }))
                ])
            ])
        ]);
    }
    function renderToolbarAction(iconName, contentDescription, onClick, active = false) {
        return UI.Box({
            width: 40,
            height: 40,
            contentAlignment: "center",
            modifier: ctx.Modifier.clip({ type: "circle" }).clickable(onClick)
        }, [
            UI.Icon({
                name: iconName,
                size: 18,
                tint: active ? colors.primary : colors.onSurface,
                contentDescription
            })
        ]);
    }
    function renderChoiceChip(label, selected, onClick, enabled = true) {
        const chipShape = { type: "pill" };
        const borderColor = !enabled
            ? colors.outlineVariant.copy({ alpha: 0.55 })
            : selected
                ? colors.primary.copy({ alpha: 0.22 })
                : colors.outlineVariant;
        const backgroundColor = selected ? colors.primaryContainer.copy({ alpha: 0.38 }) : colors.surface;
        const textColor = !enabled
            ? colors.onSurfaceVariant.copy({ alpha: 0.6 })
            : selected
                ? colors.primary
                : colors.onSurface;
        const chipModifier = enabled
            ? ctx.Modifier
                .clip(chipShape)
                .background(backgroundColor)
                .border(1, borderColor, chipShape)
                .clickable(onClick)
            : ctx.Modifier.clip(chipShape).background(backgroundColor).border(1, borderColor, chipShape);
        return UI.Box({
            modifier: chipModifier.padding({ horizontal: 14, vertical: 9 })
        }, [
            UI.Row({
                spacing: selected ? 6 : 0,
                verticalAlignment: "center"
            }, [
                selected
                    ? UI.Icon({
                        name: "check",
                        size: 16,
                        tint: textColor
                    })
                    : null,
                UI.Text({
                    text: label,
                    fontWeight: selected ? "bold" : "medium",
                    color: textColor
                })
            ].filter(Boolean))
        ]);
    }
    function renderForm() {
        const isEdit = view === "edit";
        return UI.Column({
            padding: 12,
            spacing: 12,
            fillMaxWidth: true
        }, [
            UI.Row({
                fillMaxWidth: true
            }, [
                UI.OutlinedButton({
                    onClick: () => setView("list"),
                    shape: { cornerRadius: 12 }
                }, [
                    UI.Row({
                        spacing: 6,
                        verticalAlignment: "center"
                    }, [
                        UI.Icon({
                            name: "arrowBack",
                            size: 16,
                            tint: colors.onSurface
                        }),
                        UI.Text({
                            text: t.buttonBack,
                            color: colors.onSurface,
                            fontWeight: "bold"
                        })
                    ])
                ])
            ]),
            UI.Card({
                containerColor: colors.surface,
                shape: { cornerRadius: 18 },
                fillMaxWidth: true
            }, [
                UI.Column({
                    padding: 16,
                    spacing: 12,
                    fillMaxWidth: true
                }, [
                    UI.Text({
                        text: t.sectionBasicInfo,
                        style: "titleMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    UI.Text({
                        text: t.sectionBasicInfoDesc,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.TextField({
                        label: t.fieldEntryName,
                        placeholder: t.fieldEntryNamePlaceholder,
                        value: formName,
                        onValueChange: setFormName,
                        singleLine: true,
                        fillMaxWidth: true
                    }),
                    UI.TextField({
                        label: t.fieldKeywords,
                        placeholder: t.fieldKeywordsPlaceholder,
                        value: formKeywords,
                        onValueChange: setFormKeywords,
                        singleLine: true,
                        fillMaxWidth: true
                    }),
                    UI.TextField({
                        label: t.fieldContent,
                        placeholder: t.fieldContentPlaceholder,
                        value: formContent,
                        onValueChange: setFormContentValue,
                        singleLine: false,
                        minLines: 5,
                        fillMaxWidth: true
                    }),
                    UI.Box({
                        fillMaxWidth: true
                    }, [
                        UI.OutlinedButton({
                            onClick: () => setShowSnippetPicker(!showSnippetPicker),
                            fillMaxWidth: true,
                            shape: { cornerRadius: 14 }
                        }, [
                            UI.Row({
                                fillMaxWidth: true,
                                horizontalArrangement: "spaceBetween",
                                verticalAlignment: "center"
                            }, [
                                UI.Text({
                                    text: t.fieldContentTemplates,
                                    color: colors.onSurface,
                                    fontWeight: "medium"
                                }),
                                UI.Icon({
                                    name: showSnippetPicker ? "arrowDropUp" : "arrowDropDown",
                                    tint: colors.onSurfaceVariant,
                                    size: 20
                                })
                            ])
                        ])
                    ]),
                    UI.Text({
                        text: t.fieldContentTemplatesHint,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.Text({
                        text: t.fieldCharacterCard,
                        style: "labelMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    UI.Box({
                        fillMaxWidth: true
                    }, [
                        UI.OutlinedButton({
                            onClick: () => toggleCardPicker(),
                            fillMaxWidth: true,
                            shape: { cornerRadius: 14 }
                        }, [
                            UI.Row({
                                fillMaxWidth: true,
                                horizontalArrangement: "spaceBetween",
                                verticalAlignment: "center"
                            }, [
                                UI.Column({ weight: 1, spacing: 2 }, [
                                    UI.Text({
                                        text: getSelectedCardLabel(),
                                        color: colors.onSurface,
                                        fontWeight: "medium",
                                        maxLines: 1,
                                        overflow: "ellipsis"
                                    }),
                                    UI.Text({
                                        text: formCharacterCardId ? t.dropdownBoundCard : t.dropdownGlobalEffective,
                                        style: "bodySmall",
                                        color: colors.onSurfaceVariant
                                    })
                                ]),
                                UI.Icon({
                                    name: showCardPicker ? "arrowDropUp" : "arrowDropDown",
                                    tint: colors.onSurfaceVariant,
                                    size: 20
                                })
                            ])
                        ]),
                        UI.DropdownMenu({
                            expanded: showCardPicker,
                            width: CHARACTER_CARD_MENU_WIDTH,
                            modifier: ctx.Modifier.heightIn({ maxHeight: CHARACTER_CARD_MENU_MAX_HEIGHT }),
                            properties: {
                                focusable: true,
                                usePlatformDefaultWidth: false
                            },
                            onDismissRequest: () => {
                                setShowCardPicker(false);
                                setLoadingCards(false);
                            }
                        }, [
                            UI.Box({
                                modifier: ctx.Modifier
                                    .fillMaxWidth()
                                    .clickable(() => doClearCardBinding())
                                    .padding({ horizontal: 16, vertical: 12 })
                            }, [
                                UI.Text({
                                    text: t.dropdownNoCharacterCard,
                                    color: colors.onSurface,
                                    fontWeight: !formCharacterCardId ? "bold" : "normal"
                                })
                            ]),
                            UI.HorizontalDivider({
                                color: colors.outlineVariant,
                                thickness: 1
                            }),
                            ...(loadingCards
                                ? [
                                    UI.Box({
                                        modifier: ctx.Modifier
                                            .fillMaxWidth()
                                            .padding({ horizontal: 16, vertical: 12 })
                                    }, [
                                        UI.Text({
                                            text: t.dropdownLoading,
                                            color: colors.onSurfaceVariant
                                        })
                                    ])
                                ]
                                : availableCards.length === 0
                                    ? [
                                        UI.Box({
                                            modifier: ctx.Modifier
                                                .fillMaxWidth()
                                                .padding({ horizontal: 16, vertical: 12 })
                                        }, [
                                            UI.Text({
                                                text: t.dropdownNoCards,
                                                color: colors.onSurfaceVariant
                                            })
                                        ])
                                    ]
                                    : availableCards.map((card) => UI.Box({
                                        modifier: ctx.Modifier
                                            .fillMaxWidth()
                                            .clickable(() => doPickCard(card.id))
                                            .padding({ horizontal: 16, vertical: 12 })
                                    }, [
                                        UI.Row({
                                            fillMaxWidth: true,
                                            horizontalArrangement: "spaceBetween",
                                            verticalAlignment: "center"
                                        }, [
                                            UI.Column({ weight: 1, spacing: 2 }, [
                                                UI.Text({
                                                    text: card.name,
                                                    color: colors.onSurface,
                                                    fontWeight: card.id === formCharacterCardId ? "bold" : "normal",
                                                    maxLines: 1,
                                                    overflow: "ellipsis"
                                                }),
                                                ...(card.description
                                                    ? [
                                                        UI.Text({
                                                            text: card.description,
                                                            style: "bodySmall",
                                                            color: colors.onSurfaceVariant,
                                                            maxLines: 1,
                                                            overflow: "ellipsis"
                                                        })
                                                    ]
                                                    : [])
                                            ]),
                                            card.id === formCharacterCardId
                                                ? UI.Icon({
                                                    name: "check",
                                                    tint: colors.primary,
                                                    size: 18
                                                })
                                                : UI.Spacer({ width: 18 })
                                        ])
                                    ])))
                        ])
                    ]),
                    UI.Text({
                        text: t.dropdownHint,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    })
                ])
            ]),
            UI.Card({
                containerColor: colors.surface,
                shape: { cornerRadius: 18 },
                fillMaxWidth: true
            }, [
                UI.Column({
                    padding: 16,
                    spacing: 12,
                    fillMaxWidth: true
                }, [
                    UI.Text({
                        text: t.sectionMatchAndEnable,
                        style: "titleMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    renderSettingRow(t.settingEnabledTitle, t.settingEnabledDesc, formEnabled, setFormEnabled),
                    UI.HorizontalDivider({
                        color: colors.outlineVariant,
                        thickness: 1
                    }),
                    renderSettingRow(t.settingAlwaysActiveTitle, t.settingAlwaysActiveDesc, formAlwaysActive, setFormAlwaysActive),
                    UI.HorizontalDivider({
                        color: colors.outlineVariant,
                        thickness: 1
                    }),
                    renderSettingRow(t.settingRegexTitle, t.settingRegexDesc, formIsRegex, setFormIsRegex),
                    UI.HorizontalDivider({
                        color: colors.outlineVariant,
                        thickness: 1
                    }),
                    renderSettingRow(t.settingCaseSensitiveTitle, t.settingCaseSensitiveDesc, formCaseSensitive, setFormCaseSensitive)
                ])
            ]),
            UI.Card({
                containerColor: colors.surface,
                shape: { cornerRadius: 18 },
                fillMaxWidth: true
            }, [
                UI.Column({
                    padding: 16,
                    spacing: 12,
                    fillMaxWidth: true
                }, [
                    UI.Text({
                        text: t.sectionInjectStrategy,
                        style: "titleMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    UI.Text({
                        text: t.sectionInjectStrategyDesc,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.Row({ fillMaxWidth: true, spacing: 12 }, [
                        UI.Column({ weight: 1 }, [
                            UI.TextField({
                                label: t.fieldPriority,
                                placeholder: t.fieldPriorityPlaceholder,
                                value: formPriority,
                                onValueChange: setFormPriority,
                                singleLine: true,
                                fillMaxWidth: true
                            })
                        ]),
                        UI.Column({ weight: 1 }, [
                            UI.TextField({
                                label: t.fieldScanDepth,
                                placeholder: t.fieldScanDepthPlaceholder,
                                value: formScanDepth,
                                onValueChange: setFormScanDepth,
                                singleLine: true,
                                fillMaxWidth: true
                            })
                        ])
                    ]),
                    UI.Text({
                        text: t.scanDepthHint,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.Spacer({ height: 8 }),
                    UI.Text({
                        text: t.injectTargetTitle,
                        style: "labelMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    UI.Text({
                        text: t.injectTargetHint,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.LazyRow({
                        fillMaxWidth: true,
                        spacing: 8
                    }, [
                        renderChoiceChip(t.injectTargetSystem, formInjectTarget === "system", () => selectInjectTarget("system")),
                        renderChoiceChip(t.injectTargetUser, formInjectTarget === "user", () => selectInjectTarget("user")),
                        renderChoiceChip(t.injectTargetAssistant, formInjectTarget === "assistant", () => selectInjectTarget("assistant"))
                    ]),
                    UI.Spacer({ height: 8 }),
                    UI.Text({
                        text: t.injectPositionTitle,
                        style: "labelMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    UI.Text({
                        text: t.injectPositionHint,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.LazyRow({
                        fillMaxWidth: true,
                        spacing: 8
                    }, [
                        renderChoiceChip(t.injectPositionPrepend, effectiveInjectPosition === "prepend", () => selectInjectPosition("prepend"), formInjectTarget !== "assistant"),
                        renderChoiceChip(t.injectPositionAppend, effectiveInjectPosition === "append", () => selectInjectPosition("append"), formInjectTarget !== "assistant"),
                        renderChoiceChip(t.injectPositionAtDepth, effectiveInjectPosition === "at_depth", () => selectInjectPosition("at_depth"))
                    ]),
                    effectiveInjectPosition === "at_depth"
                        ? UI.Column({
                            spacing: 6
                        }, [
                            UI.TextField({
                                label: t.fieldInsertionDepth,
                                placeholder: t.fieldInsertionDepthPlaceholder,
                                value: formInsertionDepth,
                                onValueChange: setFormInsertionDepth,
                                singleLine: true,
                                fillMaxWidth: true
                            }),
                            UI.Text({
                                text: t.insertionDepthHint,
                                style: "bodySmall",
                                color: colors.onSurfaceVariant
                            })
                        ])
                        : null
                ].filter(Boolean))
            ]),
            UI.Button({
                text: isEdit ? t.saveEditButton : t.createEntryButton,
                onClick: () => doSave(),
                fillMaxWidth: true,
                shape: { cornerRadius: 14 }
            })
        ]);
    }
    function renderImportForm() {
        return UI.Column({
            padding: 12,
            spacing: 12,
            fillMaxWidth: true
        }, [
            UI.Row({
                fillMaxWidth: true
            }, [
                UI.OutlinedButton({
                    onClick: () => setView("list"),
                    shape: { cornerRadius: 12 }
                }, [
                    UI.Row({
                        spacing: 6,
                        verticalAlignment: "center"
                    }, [
                        UI.Icon({
                            name: "arrowBack",
                            size: 16,
                            tint: colors.onSurface
                        }),
                        UI.Text({
                            text: t.buttonBack,
                            color: colors.onSurface,
                            fontWeight: "bold"
                        })
                    ])
                ])
            ]),
            UI.Card({
                containerColor: colors.surface,
                shape: { cornerRadius: 18 },
                fillMaxWidth: true
            }, [
                UI.Column({
                    padding: 16,
                    spacing: 12,
                    fillMaxWidth: true
                }, [
                    UI.Text({
                        text: t.sectionImport,
                        style: "titleMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    UI.Text({
                        text: t.sectionImportDesc,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.TextField({
                        label: t.fieldImportPath,
                        placeholder: t.fieldImportPathPlaceholder,
                        value: importPath,
                        onValueChange: setImportPath,
                        singleLine: false,
                        minLines: 2,
                        fillMaxWidth: true
                    }),
                    UI.OutlinedButton({
                        onClick: () => doPickImportFile(),
                        enabled: !importing,
                        fillMaxWidth: true,
                        shape: { cornerRadius: 12 }
                    }, [
                        UI.Text({
                            text: t.buttonPickImportFile,
                            fontWeight: "bold"
                        })
                    ]),
                    UI.Text({
                        text: t.importFormatsTitle,
                        style: "labelMedium",
                        fontWeight: "bold",
                        color: colors.onSurface
                    }),
                    UI.Text({
                        text: t.importFormatsDesc,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    }),
                    UI.Text({
                        text: t.importPathHint,
                        style: "bodySmall",
                        color: colors.onSurfaceVariant
                    })
                ])
            ]),
            UI.Button({
                text: importing ? `${t.importActionButton}...` : t.importActionButton,
                onClick: () => doImport(),
                enabled: !importing,
                fillMaxWidth: true,
                shape: { cornerRadius: 14 }
            })
        ]);
    }
    const hasSearchQuery = searchQuery.trim().length > 0;
    const visibleEntries = hasSearchQuery ? entries.filter((entry) => matchesEntrySearch(entry, searchQuery)) : entries;
    const items = [
        UI.Column({
            key: "actions",
            fillMaxWidth: true,
            spacing: 8,
            padding: { horizontal: 4, vertical: 4 }
        }, [
            UI.Row({
                fillMaxWidth: true,
                horizontalArrangement: "end",
                verticalAlignment: "center",
                spacing: 8
            }, [
                renderToolbarAction("search", t.actionSearch, toggleSearchBar, showSearchBar),
                renderToolbarAction("refresh", t.actionRefresh, () => doRefresh()),
                renderToolbarAction("createNewFolder", t.groupNewButton, doOpenGroupCreate),
                renderToolbarAction("uploadFile", t.buttonImport, doOpenImport),
                renderToolbarAction("add", t.newEntryButton, doCreate)
            ]),
            showSearchBar
                ? UI.TextField({
                    label: t.actionSearch,
                    placeholder: t.searchPlaceholder,
                    value: searchQuery,
                    onValueChange: setSearchQuery,
                    singleLine: true,
                    fillMaxWidth: true,
                    leadingIcon: UI.Icon({
                        name: "search",
                        size: 18,
                        tint: colors.onSurfaceVariant
                    }),
                    trailingIcon: hasSearchQuery
                        ? UI.IconButton({
                            onClick: () => setSearchQuery(""),
                            icon: "close"
                        })
                        : undefined
                })
                : null
        ].filter(Boolean))
    ];
    if (view === "group_create" || view === "group_edit") {
        return UI.LazyColumn({
            key: "worldbook_group_form",
            fillMaxSize: true,
            spacing: 12,
            padding: { horizontal: 12, vertical: 8 }
        }, [renderGroupForm()]);
    }
    if (view === "group_delete") {
        return UI.LazyColumn({
            key: "worldbook_group_delete",
            fillMaxSize: true,
            spacing: 12,
            padding: { horizontal: 12, vertical: 8 }
        }, [renderGroupDeleteView()]);
    }
    if (view === "copy_to_group") {
        return UI.LazyColumn({
            key: "worldbook_copy_to_group",
            fillMaxSize: true,
            spacing: 12,
            padding: { horizontal: 12, vertical: 8 }
        }, [renderCopyToGroupView()]);
    }
    if (view === "edit" || view === "create") {
        return UI.Box({
            fillMaxSize: true
        }, [
            UI.LazyColumn({
                key: "worldbook_form",
                fillMaxSize: true,
                spacing: 12,
                padding: { horizontal: 12, vertical: 8 }
            }, [renderForm()]),
            showSnippetPicker ? renderSnippetDialog() : null
        ].filter(Boolean));
    }
    if (view === "import") {
        return UI.LazyColumn({
            key: "worldbook_import",
            fillMaxSize: true,
            spacing: 12,
            padding: { horizontal: 12, vertical: 8 }
        }, [renderImportForm()]);
    }
    if (loading || !hasLoadedOnce) {
        items.push(UI.Column({
            key: "loading",
            fillMaxWidth: true,
            horizontalAlignment: "center",
            padding: 32
        }, [
            UI.CircularProgressIndicator({}),
            UI.Spacer({ height: 8 }),
            UI.Text({
                text: t.listLoading,
                color: colors.onSurfaceVariant
            })
        ]));
    }
    else if (entries.length === 0) {
        items.push(UI.Card({
            key: "empty",
            fillMaxWidth: true,
            containerColor: colors.surfaceVariant,
            elevation: 0
        }, [
            UI.Column({
                fillMaxWidth: true,
                horizontalAlignment: "center",
                padding: 24,
                spacing: 8
            }, [
                UI.Text({
                    text: t.emptyTitle,
                    style: "titleMedium",
                    color: colors.onSurface
                }),
                UI.Text({
                    text: t.emptyDesc,
                    style: "bodySmall",
                    color: colors.onSurfaceVariant
                }),
                UI.FilledTonalButton({
                    onClick: doCreate,
                    height: 36
                }, [
                    UI.Text({
                        text: t.emptyAction,
                        color: colors.onSecondaryContainer,
                        fontWeight: "bold"
                    })
                ])
            ])
        ]));
    }
    else if (visibleEntries.length === 0) {
        items.push(UI.Card({
            key: "search-empty",
            fillMaxWidth: true,
            containerColor: colors.surfaceVariant,
            elevation: 0
        }, [
            UI.Column({
                fillMaxWidth: true,
                horizontalAlignment: "center",
                padding: 24,
                spacing: 8
            }, [
                UI.Text({
                    text: t.searchEmptyTitle,
                    style: "titleMedium",
                    color: colors.onSurface
                }),
                UI.Text({
                    text: t.searchEmptyDesc,
                    style: "bodySmall",
                    color: colors.onSurfaceVariant
                })
            ])
        ]));
    }
    else {
        if (hasSearchQuery) {
            for (const entry of visibleEntries) {
                items.push(renderCard(entry));
            }
        }
        else {
            // Show grouped view
            for (const group of groups) {
                items.push(renderGroupHeader(group));
            }
            const ungroupedNode = renderUngroupedSection();
            if (ungroupedNode) {
                items.push(ungroupedNode);
            }
        }
    }
    return UI.LazyColumn({
        key: "worldbook_list",
        spacing: 10,
        padding: { horizontal: 12, vertical: 8 },
        fillMaxSize: true,
        onLoad: async () => {
            if (hasLoadedOnce || initialLoadInFlight) {
                return;
            }
            setInitialLoadInFlight(true);
            try {
                await loadEntries(true);
                await ensureCharacterCardsLoaded(false);
            }
            finally {
                setInitialLoadInFlight(false);
            }
        }
    }, items);
}
