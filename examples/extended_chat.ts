/* METADATA
{
    "name": "extended_chat",

    "display_name": {
        "zh": "增强对话",
        "en": "Extended Chat"
    },
    "description": {
        "zh": "对话工具包：列出/查找/重命名/删除对话、跨话题读取消息、绑定角色卡对话并发送消息。",
        "en": "Chat toolkit: list/find/rename/delete chats, read messages across chats, bind character cards and send messages."
    },
    "enabledByDefault": true,
    "category": "Chat",
    "tools": [
        {
            "name": "list_chats",
            "description": {
                "zh": "列出并筛选对话（用于获取 chat_id）。",
                "en": "List and filter chats (to discover chat_id)."
            },
            "parameters": [
                { "name": "query", "description": { "zh": "可选：标题筛选关键字", "en": "Optional title keyword" }, "type": "string", "required": false },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false },
                { "name": "limit", "description": { "zh": "可选：最多返回条数（默认 50）", "en": "Optional max results (default 50)" }, "type": "number", "required": false },
                { "name": "sort_by", "description": { "zh": "可选：updatedAt/createdAt/messageCount（默认 updatedAt）", "en": "Optional: updatedAt/createdAt/messageCount (default updatedAt)" }, "type": "string", "required": false },
                { "name": "sort_order", "description": { "zh": "可选：asc/desc（默认 desc）", "en": "Optional: asc/desc (default desc)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "find_chat",
            "description": {
                "zh": "按标题查找一个对话并返回 chat_id。",
                "en": "Find a single chat by title and return chat_id."
            },
            "parameters": [
                { "name": "query", "description": { "zh": "标题关键字/正则", "en": "Title keyword/regex" }, "type": "string", "required": true },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false },
                { "name": "index", "description": { "zh": "可选：当匹配多个时选择第 N 个（默认 0）", "en": "Optional: pick Nth when multiple matches (default 0)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "read_messages",
            "description": {
                "zh": "读取指定对话的消息（可按 chat_id 或 chat_title 指定）。",
                "en": "Read messages from a chat (by chat_id or chat_title)."
            },
            "parameters": [
                { "name": "chat_id", "description": { "zh": "目标对话 ID（可选）", "en": "Target chat id (optional)" }, "type": "string", "required": false },
                { "name": "chat_title", "description": { "zh": "目标对话标题（可选；当 chat_id 为空时使用）", "en": "Target chat title (optional; used when chat_id is empty)" }, "type": "string", "required": false },
                { "name": "chat_query", "description": { "zh": "可选：标题筛选关键字（当 chat_id/chat_title 为空时使用）", "en": "Optional title keyword (used when chat_id/chat_title is empty)" }, "type": "string", "required": false },
                { "name": "chat_index", "description": { "zh": "可选：当筛选结果有多个时选择第 N 个（默认 0）", "en": "Optional: pick Nth when multiple matches (default 0)" }, "type": "number", "required": false },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false },
                { "name": "order", "description": { "zh": "可选：asc/desc（默认 desc）", "en": "Optional: asc/desc (default desc)" }, "type": "string", "required": false },
                { "name": "limit", "description": { "zh": "可选：返回消息条数（默认 20）", "en": "Optional: max number of messages (default 20)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "read_messages_range",
            "description": {
                "zh": "按消息序号区间读取指定对话的消息；用于超过 read_messages 单次条数限制的大范围读取。",
                "en": "Read messages from a chat by message index range; useful for reading beyond read_messages single-call limits."
            },
            "parameters": [
                { "name": "chat_id", "description": { "zh": "目标对话 ID（可选）", "en": "Target chat id (optional)" }, "type": "string", "required": false },
                { "name": "chat_title", "description": { "zh": "目标对话标题（可选；当 chat_id 为空时使用）", "en": "Target chat title (optional; used when chat_id is empty)" }, "type": "string", "required": false },
                { "name": "chat_query", "description": { "zh": "可选：标题筛选关键字（当 chat_id/chat_title 为空时使用）", "en": "Optional title keyword (used when chat_id/chat_title is empty)" }, "type": "string", "required": false },
                { "name": "chat_index", "description": { "zh": "可选：当筛选结果有多个时选择第 N 个（默认 0）", "en": "Optional: pick Nth when multiple matches (default 0)" }, "type": "number", "required": false },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false },
                { "name": "order", "description": { "zh": "可选：asc/desc（默认 asc）", "en": "Optional: asc/desc (default asc)" }, "type": "string", "required": false },
                { "name": "start", "description": { "zh": "从 0 开始的起始消息序号（包含）", "en": "Zero-based start message index (inclusive)" }, "type": "number", "required": true },
                { "name": "end", "description": { "zh": "从 0 开始的结束消息序号（包含）", "en": "Zero-based end message index (inclusive)" }, "type": "number", "required": true }
            ]
        },
        {
            "name": "rename_chat",
            "description": {
                "zh": "重命名指定对话（可按 chat_id 或 chat_title 指定）。",
                "en": "Rename a chat (by chat_id or chat_title)."
            },
            "parameters": [
                { "name": "new_title", "description": { "zh": "新的对话标题", "en": "New chat title" }, "type": "string", "required": true },
                { "name": "chat_id", "description": { "zh": "目标对话 ID（可选）", "en": "Target chat id (optional)" }, "type": "string", "required": false },
                { "name": "chat_title", "description": { "zh": "目标对话标题（可选；当 chat_id 为空时使用）", "en": "Target chat title (optional; used when chat_id is empty)" }, "type": "string", "required": false },
                { "name": "chat_query", "description": { "zh": "可选：标题筛选关键字（当 chat_id/chat_title 为空时使用）", "en": "Optional title keyword (used when chat_id/chat_title is empty)" }, "type": "string", "required": false },
                { "name": "chat_index", "description": { "zh": "可选：当筛选结果有多个时选择第 N 个（默认 0）", "en": "Optional: pick Nth when multiple matches (default 0)" }, "type": "number", "required": false },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "delete_chat",
            "description": {
                "zh": "删除指定对话（可按 chat_id 或 chat_title 指定）。",
                "en": "Delete a chat (by chat_id or chat_title)."
            },
            "parameters": [
                { "name": "chat_id", "description": { "zh": "目标对话 ID（可选）", "en": "Target chat id (optional)" }, "type": "string", "required": false },
                { "name": "chat_title", "description": { "zh": "目标对话标题（可选；当 chat_id 为空时使用）", "en": "Target chat title (optional; used when chat_id is empty)" }, "type": "string", "required": false },
                { "name": "chat_query", "description": { "zh": "可选：标题筛选关键字（当 chat_id/chat_title 为空时使用）", "en": "Optional title keyword (used when chat_id/chat_title is empty)" }, "type": "string", "required": false },
                { "name": "chat_index", "description": { "zh": "可选：当筛选结果有多个时选择第 N 个（默认 0）", "en": "Optional: pick Nth when multiple matches (default 0)" }, "type": "number", "required": false },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "chat_with_agent",
            "description": {
                "zh": "与对应角色的 agent 对话：传入角色卡名称；chat_id 为空时自动创建新对话并返回新 ID。严格执行一角色一会话，不能多个角色共用同一会话。该工具可用于将任务分担给其他 agent 或与其他角色交流，但通常只有在用户明确表达此意图时使用；多数情况下，能自行完成的任务应优先直接完成。",
                "en": "Chat with the agent for the specified character card name; if chat_id is empty, create a new chat and return its ID. Enforces one role per chat (no sharing between roles). Use this tool to delegate tasks to other agents or communicate with other roles when the user explicitly intends it; otherwise, prefer completing tasks directly without using this tool."
            },
            "parameters": [
                { "name": "message", "description": { "zh": "发送给 AI 的内容", "en": "Message to send to AI" }, "type": "string", "required": true },
                { "name": "character_card_name", "description": { "zh": "角色卡名称", "en": "Character card name" }, "type": "string", "required": true },
                { "name": "chat_id", "description": { "zh": "目标对话 ID（可选；为空时新建）", "en": "Target chat id (optional; create new if empty)" }, "type": "string", "required": false },
                { "name": "timeout", "description": { "zh": "可选：等待返回的超时秒数（默认 180）", "en": "Optional timeout seconds to wait for response (default 180)" }, "type": "number", "required": false },
                { "name": "persist_turn", "description": { "zh": "可选：是否持久化本轮用户消息和 AI 回复（默认 true）", "en": "Optional: whether to persist this turn's user message and AI reply (default true)" }, "type": "boolean", "required": false },
                { "name": "notify_reply", "description": { "zh": "可选：是否覆盖本轮回复通知开关", "en": "Optional: override reply notification for this turn" }, "type": "boolean", "required": false },
                { "name": "hide_user_message", "description": { "zh": "可选：是否在 UI 中隐藏用户消息正文并显示占位标记", "en": "Optional: hide the user message body in UI and show a placeholder marker" }, "type": "boolean", "required": false },
                { "name": "disable_warning", "description": { "zh": "可选：是否关闭本轮 AI 生成的 warning 标记", "en": "Optional: suppress AI-generated warning markup for this turn" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "get_current_chat_runtime_state",
            "description": {
                "zh": "查询当前对话运行状态。",
                "en": "Check the current runtime state of a conversation."
            },
            "parameters": [
                { "name": "chat_id", "description": { "zh": "可选：目标对话 ID；为空时查询当前对话", "en": "Optional target chat id; omit for the current conversation" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "get_global_chat_runtime_state",
            "description": {
                "zh": "查询全局聊天运行状态。",
                "en": "Check the global chat runtime state."
            },
            "parameters": []
        },
        {
            "name": "list_character_cards",
            "description": {
                "zh": "列出所有角色卡（用于获取 character_card_id）。",
                "en": "List all character cards (to discover character_card_id)."
            },
            "parameters": []
        }
    ]
}*/

const HistoryChat = (function () {

    interface ToolResponse<T = unknown> {
        success: boolean;
        message: string;
        data?: T;
    }

    type ResolveChatIdParams = {
        chat_id?: string;
        chat_title?: string;
        chat_query?: string;
        chat_index?: number;
        match?: string;
    };

    type ReadMessagesParams = ResolveChatIdParams & {
        order?: string;
        limit?: number;
    };

    type ReadMessagesRangeParams = ResolveChatIdParams & {
        order?: string;
        start?: number;
        end?: number;
    };

    type RenameChatParams = ResolveChatIdParams & {
        new_title: string;
    };

    type DeleteChatParams = ResolveChatIdParams;

    type ListChatsParams = {
        query?: string;
        match?: string;
        limit?: number;
        sort_by?: string;
        sort_order?: string;
    };

    type FindChatParams = {
        query: string;
        match?: string;
        index?: number;
    };

    type ChatRuntimeStateParams = {
        chat_id?: string;
    };

    type ChatWithAgentParams = {
        message: string;
        character_card_name: string;
        chat_id?: string;
        timeout?: number;
        persist_turn?: boolean;
        notify_reply?: boolean;
        hide_user_message?: boolean;
        disable_warning?: boolean;
    };

    function normalizeMatchMode(match?: string): 'contains' | 'exact' | 'regex' {
        const m = (match || '').trim().toLowerCase();
        if (m === 'exact' || m === 'regex' || m === 'contains') return m;
        return 'contains';
    }

    async function list_chats_impl(params: ListChatsParams): Promise<ToolResponse> {
        const query = (params?.query ?? '').toString().trim();
        const matchMode = normalizeMatchMode(params?.match);
        const limitRaw = params && params.limit !== undefined ? Number(params.limit) : undefined;
        const limit = limitRaw !== undefined && !isNaN(limitRaw) ? limitRaw : undefined;
        const sortBy = params?.sort_by ? params.sort_by.toString().trim() : undefined;
        const sortOrder = params?.sort_order ? params.sort_order.toString().trim().toLowerCase() : undefined;

        const listParams: ToolParams = {};
        if (query) listParams.query = query;
        if (matchMode) listParams.match = matchMode;
        if (limit !== undefined) listParams.limit = limit;
        if (sortBy) listParams.sort_by = sortBy;
        if (sortOrder) listParams.sort_order = sortOrder;

        const listResult = await Tools.Chat.listChats(listParams);
        const chats = listResult.chats;
        return {
            success: true,
            message: '对话列表获取完成',
            data: {
                totalCount: listResult?.totalCount ?? chats.length,
                currentChatId: listResult?.currentChatId ?? null,
                matchedCount: listResult?.totalCount ?? chats.length,
                chats,
            }
        };
    }

    async function find_chat_impl(params: FindChatParams): Promise<ToolResponse> {
        const query = (params?.query ?? '').toString().trim();
        if (!query) {
            throw new Error('Missing parameter: query');
        }

        const matchMode = normalizeMatchMode(params?.match);
        const indexRaw = params && params.index !== undefined ? Number(params.index) : 0;
        const index = isNaN(indexRaw) ? 0 : indexRaw;
        const findParams: Parameters<typeof Tools.Chat.findChat>[0] = { query };
        if (matchMode) findParams.match = matchMode;
        if (index !== undefined) findParams.index = index;
        const findResult = await Tools.Chat.findChat(findParams);
        const picked = findResult?.chat ?? null;
        if (!picked) {
            throw new Error(`Chat not found by query: ${query}`);
        }

        return {
            success: true,
            message: '对话查找完成',
            data: {
                chat: picked,
                matchedCount: findResult?.matchedCount ?? 1,
            }
        };
    }

    async function resolveChatId(params: ResolveChatIdParams): Promise<string> {
        if (params && typeof params.chat_id === 'string' && params.chat_id.trim()) {
            return params.chat_id.trim();
        }

        const title = params && typeof params.chat_title === 'string' ? params.chat_title.trim() : '';
        const query = params && typeof params.chat_query === 'string' ? params.chat_query.trim() : '';
        const matchMode = normalizeMatchMode(params?.match);
        const indexRaw = params && params.chat_index !== undefined ? Number(params.chat_index) : 0;
        const index = isNaN(indexRaw) ? 0 : indexRaw;

        if (!title && !query) {
            throw new Error('Missing parameter: chat_id or chat_title or chat_query is required');
        }

        const needle = title || query;
        const findParams: Parameters<typeof Tools.Chat.findChat>[0] = { query: needle };
        findParams.match = title ? 'exact' : matchMode;
        if (index !== undefined) findParams.index = index;
        const findResult = await Tools.Chat.findChat(findParams);
        const picked = findResult?.chat ?? null;
        if (!picked?.id) {
            throw new Error(`Chat not found by query: ${needle}`);
        }
        return picked.id;
    }

    async function read_messages_impl(params: ReadMessagesParams): Promise<ToolResponse> {
        const chatId = await resolveChatId(params || {});

        const orderRaw = params && params.order !== undefined ? String(params.order).trim().toLowerCase() : '';
        const order = (orderRaw === 'asc' || orderRaw === 'desc') ? orderRaw : 'desc';

        const limitRaw = params && params.limit !== undefined ? Number(params.limit) : 20;
        const limit = isNaN(limitRaw) ? 20 : limitRaw;

        const result = await Tools.Chat.getMessages(chatId, {
            order,
            limit,
        });

        const rawMessages = result.messages;
        const text = rawMessages
            .map((m) => {
                const role = (m.roleName ?? m.sender ?? '').toString() || 'message';
                const ts = (m.timestamp !== undefined && m.timestamp !== null) ? String(m.timestamp) : '';
                const header = ts ? `[${ts}] ${role}` : role;
                return `${header}:\n${(m.content ?? '').toString()}`;
            })
            .join('\n\n');

        return {
            success: true,
            message: '读取对话消息完成',
            data: {
                result,
                text,
            },
        };
    }

    async function read_messages_range_impl(params: ReadMessagesRangeParams): Promise<ToolResponse> {
        const chatId = await resolveChatId(params || {});

        const orderRaw = params && params.order !== undefined ? String(params.order).trim().toLowerCase() : '';
        const order = (orderRaw === 'asc' || orderRaw === 'desc') ? orderRaw : 'asc';

        const startRaw = params && params.start !== undefined ? Number(params.start) : Number.NaN;
        const endRaw = params && params.end !== undefined ? Number(params.end) : Number.NaN;
        if (isNaN(startRaw) || isNaN(endRaw)) {
            throw new Error('Missing parameter: start and end are required');
        }
        const start = Math.floor(startRaw);
        const end = Math.floor(endRaw);
        if (start < 0 || end < start) {
            throw new Error('Invalid parameter: range requires 0 <= start <= end');
        }

        const result = await Tools.Chat.getMessagesRange(chatId, {
            order,
            start,
            end,
        });

        const rawMessages = result.messages;
        const text = rawMessages
            .map((m) => {
                const role = (m.roleName ?? m.sender ?? '').toString() || 'message';
                const ts = (m.timestamp !== undefined && m.timestamp !== null) ? String(m.timestamp) : '';
                const header = ts ? `[${ts}] ${role}` : role;
                return `${header}:\n${(m.content ?? '').toString()}`;
            })
            .join('\n\n');

        return {
            success: true,
            message: '按区间读取对话消息完成',
            data: {
                result,
                text,
            },
        };
    }

    async function rename_chat_impl(params: RenameChatParams): Promise<ToolResponse> {
        const newTitle = (params?.new_title ?? '').toString().trim();
        if (!newTitle) {
            throw new Error('Missing parameter: new_title');
        }

        const chatId = await resolveChatId(params || {});
        const result = await Tools.Chat.updateTitle(chatId, newTitle);

        return {
            success: true,
            message: '对话重命名完成',
            data: {
                chat_id: chatId,
                title: newTitle,
                result,
            },
        };
    }

    async function delete_chat_impl(params: DeleteChatParams): Promise<ToolResponse> {
        const chatId = await resolveChatId(params || {});
        const result = await Tools.Chat.deleteChat(chatId);

        return {
            success: true,
            message: '对话删除完成',
            data: {
                chat_id: chatId,
                result,
            },
        };
    }

    async function get_current_chat_runtime_state_impl(params: ChatRuntimeStateParams): Promise<ToolResponse> {
        const chatId = (params?.chat_id ?? '').toString().trim();
        const result = await Tools.Chat.getCurrentChatRuntimeState(chatId || undefined);
        return {
            success: true,
            message: '对话当前运行状态查询完成',
            data: {
                result,
            },
        };
    }

    async function get_global_chat_runtime_state_impl(): Promise<ToolResponse> {
        const result = await Tools.Chat.getGlobalChatRuntimeState();
        return {
            success: true,
            message: '全局聊天运行状态查询完成',
            data: {
                result,
            },
        };
    }

    async function list_character_cards_impl(): Promise<ToolResponse> {
        const result = await Tools.Chat.listCharacterCards();
        const cards = result.cards;
        return {
            success: true,
            message: '角色卡列表获取完成',
            data: {
                totalCount: result?.totalCount ?? cards.length,
                cards,
            },
        };
    }

    async function chat_with_agent_impl(params: ChatWithAgentParams): Promise<ToolResponse> {
        const message = (params?.message ?? '').toString();
        const characterCardNameInput = (params?.character_card_name ?? '').toString().trim();
        if (!message.trim()) {
            throw new Error('Missing parameter: message');
        }
        if (!characterCardNameInput) {
            throw new Error('Missing parameter: character_card_name');
        }

        let characterCardName = characterCardNameInput;
        let characterCardId = '';
        try {
            const cardResult = await Tools.Chat.listCharacterCards();
            const cards = cardResult.cards;
            const targetCard = cards.find((card) => card.name === characterCardNameInput);
            if (!targetCard) {
                throw new Error(`Character card not found: ${characterCardNameInput}`);
            }
            characterCardName = targetCard.name;
            characterCardId = targetCard.id;
        } catch {
            if (!characterCardId) {
                throw new Error(`Character card not found: ${characterCardNameInput}`);
            }
        }

        try {
            await Tools.Chat.startService();
        } catch {
            // ignore service start errors to avoid blocking agent message
        }

        let chatId = (params?.chat_id ?? '').toString().trim();
        if (!chatId) {
            const lang = (getLang() || '').toLowerCase();
            const group = lang === 'zh' ? '子任务' : 'subTask';
            const creation = await Tools.Chat.createNew(
                group,
                false,
                characterCardId,
            );
            chatId = (creation?.chatId ?? '').toString().trim();
            if (!chatId) {
                throw new Error('Failed to create new chat');
            }
        } else {
            const findResult = await Tools.Chat.findChat({
                query: chatId,
                match: 'exact',
                index: 0,
            });
            const boundName = findResult?.chat?.characterCardName ?? null;
            if (boundName && boundName !== characterCardName) {
                throw new Error(`Chat ${chatId} 已绑定角色 ${boundName}，不能与 ${characterCardName} 共用会话`);
            }
        }

        const timeoutRaw = params?.timeout !== undefined ? Number(params.timeout) : 180;
        const timeoutSec = isNaN(timeoutRaw) || timeoutRaw <= 0 ? 180 : timeoutRaw;
        const timeoutMs = timeoutSec * 1000;

        const sendMessageOptions: Parameters<typeof Tools.Chat.sendMessage>[4] = {};
        if (params?.persist_turn !== undefined) {
            sendMessageOptions.persist_turn = params.persist_turn;
        }
        if (params?.notify_reply !== undefined) {
            sendMessageOptions.notify_reply = params.notify_reply;
        }
        if (params?.hide_user_message !== undefined) {
            sendMessageOptions.hide_user_message = params.hide_user_message;
        }
        if (params?.disable_warning !== undefined) {
            sendMessageOptions.disable_warning = params.disable_warning;
        }
        sendMessageOptions.timeout_ms = timeoutMs;
        const sendPromise = Tools.Chat.sendMessage(
            message,
            chatId,
            characterCardId,
            getCallerName() || characterCardName,
            sendMessageOptions,
        );

        const timeoutPromise = new Promise<null>((resolve) => {
            setTimeout(() => resolve(null), timeoutMs);
        });

        const sendResult = await Promise.race([sendPromise, timeoutPromise]);
        if (sendResult === null) {
            return {
                success: true,
                message: `已发送给 ${characterCardName}，等待响应超时（${timeoutSec}s）`,
                data: {
                    chat_id: chatId,
                    timeout: true,
                    hint: '可以通过 get_current_chat_runtime_state 查看该 agent 当前运行状态。',
                },
            };
        }

        return {
            success: true,
            message: `发消息给 ${characterCardName}`,
            data: {
                chat_id: chatId,
                result: sendResult,
            },
        };
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse>, params: P): Promise<void> {
        try {
            const result = await func(params);
            complete(result);
        } catch (error: unknown) {
            const message = error instanceof Error ? error.message : String(error);
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `读取对话消息失败: ${message}`,
            });
        }
    }

    async function wrapToolExecutionNoParams(func: () => Promise<ToolResponse>): Promise<void> {
        try {
            const result = await func();
            complete(result);
        } catch (error: unknown) {
            const message = error instanceof Error ? error.message : String(error);
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `读取对话消息失败: ${message}`,
            });
        }
    }

    async function read_messages(params: ReadMessagesParams): Promise<void> {
        return await wrapToolExecution(read_messages_impl, params);
    }

    async function read_messages_range(params: ReadMessagesRangeParams): Promise<void> {
        return await wrapToolExecution(read_messages_range_impl, params);
    }

    async function rename_chat(params: RenameChatParams): Promise<void> {
        return await wrapToolExecution(rename_chat_impl, params);
    }

    async function delete_chat(params: DeleteChatParams): Promise<void> {
        return await wrapToolExecution(delete_chat_impl, params);
    }

    async function list_chats(params: ListChatsParams): Promise<void> {
        return await wrapToolExecution(list_chats_impl, params);
    }

    async function find_chat(params: FindChatParams): Promise<void> {
        return await wrapToolExecution(find_chat_impl, params);
    }

    async function get_current_chat_runtime_state(params: ChatRuntimeStateParams): Promise<void> {
        return await wrapToolExecution(get_current_chat_runtime_state_impl, params);
    }

    async function get_global_chat_runtime_state(): Promise<void> {
        return await wrapToolExecutionNoParams(get_global_chat_runtime_state_impl);
    }

    async function list_character_cards(): Promise<void> {
        return await wrapToolExecutionNoParams(list_character_cards_impl);
    }

    async function chat_with_agent(params: ChatWithAgentParams): Promise<void> {
        return await wrapToolExecution(chat_with_agent_impl, params);
    }

    async function main(): Promise<void> {
        complete({
            success: true,
            message: 'extended_chat 工具包已加载',
            data: {
                hint: 'Use extended_chat:read_messages / rename_chat / delete_chat.',
            },
        });
    }

    return {
        list_chats,
        find_chat,
        read_messages,
        read_messages_range,
        rename_chat,
        delete_chat,
        chat_with_agent,
        get_current_chat_runtime_state,
        get_global_chat_runtime_state,
        list_character_cards,
        main,
    };
})();

exports.list_chats = HistoryChat.list_chats;
exports.find_chat = HistoryChat.find_chat;
exports.read_messages = HistoryChat.read_messages;
exports.read_messages_range = HistoryChat.read_messages_range;
exports.rename_chat = HistoryChat.rename_chat;
exports.delete_chat = HistoryChat.delete_chat;
exports.chat_with_agent = HistoryChat.chat_with_agent;
exports.get_current_chat_runtime_state = HistoryChat.get_current_chat_runtime_state;
exports.get_global_chat_runtime_state = HistoryChat.get_global_chat_runtime_state;
exports.list_character_cards = HistoryChat.list_character_cards;
exports.main = HistoryChat.main;
