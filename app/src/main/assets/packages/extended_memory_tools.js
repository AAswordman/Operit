/* METADATA
{
    "name": "extended_memory_tools",

    "display_name": {
        "zh": "增强记忆工具",
        "en": "Extended Memory Tools"
    },
    "description": {
        "zh": "拓展记忆工具包：提供创建/更新/删除/查询/链接记忆，以及更新用户偏好的能力（默认工具中仅保留 query/get/query_links）。",
        "en": "Extended memory tools: create/update/delete/query/link memories and update user preferences (default tools only keep query/get/query_links)."
    },
    "category": "Memory",
    "enabledByDefault": true,
    "tools": [
        {
            "name": "create_memory",
            "description": { "zh": "创建新的记忆节点。", "en": "Create a new memory node." },
            "parameters": [
                { "name": "title", "description": { "zh": "记忆标题", "en": "Memory title" }, "type": "string", "required": true },
                { "name": "content", "description": { "zh": "记忆内容", "en": "Memory content" }, "type": "string", "required": true },
                { "name": "content_type", "description": { "zh": "可选：内容类型，默认 text/plain", "en": "Optional: content type (default: text/plain)" }, "type": "string", "required": false },
                { "name": "source", "description": { "zh": "可选：来源，默认 ai_created", "en": "Optional: source (default: ai_created)" }, "type": "string", "required": false },
                { "name": "folder_path", "description": { "zh": "可选：文件夹路径，默认空", "en": "Optional: folder path (default: empty)" }, "type": "string", "required": false },
                { "name": "tags", "description": { "zh": "可选：标签（逗号分隔字符串）", "en": "Optional: tags (comma-separated string)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "update_memory",
            "description": { "zh": "按标题更新已有记忆节点。", "en": "Update an existing memory node by title." },
            "parameters": [
                { "name": "old_title", "description": { "zh": "原始标题（用于定位记忆）", "en": "Old title (to locate the memory)" }, "type": "string", "required": true },
                { "name": "new_title", "description": { "zh": "可选：新标题（重命名）", "en": "Optional: new title (rename)" }, "type": "string", "required": false },
                { "name": "content", "description": { "zh": "可选：新内容", "en": "Optional: new content" }, "type": "string", "required": false },
                { "name": "content_type", "description": { "zh": "可选：内容类型", "en": "Optional: content type" }, "type": "string", "required": false },
                { "name": "source", "description": { "zh": "可选：来源", "en": "Optional: source" }, "type": "string", "required": false },
                { "name": "credibility", "description": { "zh": "可选：可信度 0-1", "en": "Optional: credibility 0-1" }, "type": "number", "required": false },
                { "name": "importance", "description": { "zh": "可选：重要性 0-1", "en": "Optional: importance 0-1" }, "type": "number", "required": false },
                { "name": "folder_path", "description": { "zh": "可选：文件夹路径", "en": "Optional: folder path" }, "type": "string", "required": false },
                { "name": "tags", "description": { "zh": "可选：标签（逗号分隔字符串）", "en": "Optional: tags (comma-separated string)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "delete_memory",
            "description": { "zh": "按标题删除记忆节点（不可逆）。", "en": "Delete a memory node by title (irreversible)." },
            "parameters": [
                { "name": "title", "description": { "zh": "要删除的记忆标题", "en": "Memory title to delete" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "move_memory",
            "description": { "zh": "批量移动记忆到新文件夹。可按标题列表和/或来源文件夹筛选。", "en": "Move memories to another folder in batch. Filter by titles and/or source folder." },
            "parameters": [
                { "name": "target_folder_path", "description": { "zh": "目标文件夹路径（空字符串表示未分类）", "en": "Target folder path (empty string means uncategorized)" }, "type": "string", "required": true },
                { "name": "titles", "description": { "zh": "可选：标题列表（逗号或换行分隔）", "en": "Optional: title list (comma/newline separated)" }, "type": "string", "required": false },
                { "name": "source_folder_path", "description": { "zh": "可选：来源文件夹路径（空字符串表示未分类）", "en": "Optional: source folder path (empty string means uncategorized)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "link_memories",
            "description": { "zh": "创建两条记忆之间的语义链接。", "en": "Create a semantic link between two memories." },
            "parameters": [
                { "name": "source_title", "description": { "zh": "源记忆标题", "en": "Source memory title" }, "type": "string", "required": true },
                { "name": "target_title", "description": { "zh": "目标记忆标题", "en": "Target memory title" }, "type": "string", "required": true },
                { "name": "link_type", "description": { "zh": "可选：关系类型，默认 related", "en": "Optional: link type (default: related)" }, "type": "string", "required": false },
                { "name": "weight", "description": { "zh": "可选：强度 0-1，默认 0.7", "en": "Optional: weight 0-1 (default: 0.7)" }, "type": "number", "required": false },
                { "name": "description", "description": { "zh": "可选：关系描述", "en": "Optional: relationship description" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "query_memory_links",
            "description": { "zh": "查询记忆链接（可按 ID/源标题/目标标题/关系类型过滤）。", "en": "Query memory links (filter by id/source/target/type)." },
            "parameters": [
                { "name": "link_id", "description": { "zh": "可选：链接ID", "en": "Optional: link id" }, "type": "number", "required": false },
                { "name": "source_title", "description": { "zh": "可选：源记忆标题", "en": "Optional: source memory title" }, "type": "string", "required": false },
                { "name": "target_title", "description": { "zh": "可选：目标记忆标题", "en": "Optional: target memory title" }, "type": "string", "required": false },
                { "name": "link_type", "description": { "zh": "可选：关系类型", "en": "Optional: link type" }, "type": "string", "required": false },
                { "name": "limit", "description": { "zh": "可选：返回上限 1-200，默认20", "en": "Optional: limit 1-200, default 20" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "update_memory_link",
            "description": { "zh": "更新记忆链接（按 link_id 或 source/target/link_type 定位）。", "en": "Update a memory link (by link_id or source/target/link_type)." },
            "parameters": [
                { "name": "link_id", "description": { "zh": "可选：链接ID（优先使用）", "en": "Optional: link ID (preferred)" }, "type": "number", "required": false },
                { "name": "source_title", "description": { "zh": "可选：源记忆标题（未提供 link_id 时使用）", "en": "Optional: source title (used when link_id is not provided)" }, "type": "string", "required": false },
                { "name": "target_title", "description": { "zh": "可选：目标记忆标题（未提供 link_id 时使用）", "en": "Optional: target title (used when link_id is not provided)" }, "type": "string", "required": false },
                { "name": "link_type", "description": { "zh": "可选：当前关系类型（用于唯一定位）", "en": "Optional: current relation type (for unique resolution)" }, "type": "string", "required": false },
                { "name": "new_link_type", "description": { "zh": "可选：新的关系类型", "en": "Optional: new relation type" }, "type": "string", "required": false },
                { "name": "weight", "description": { "zh": "可选：新的强度 0-1", "en": "Optional: new weight 0-1" }, "type": "number", "required": false },
                { "name": "description", "description": { "zh": "可选：新的关系描述", "en": "Optional: new relationship description" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "delete_memory_link",
            "description": { "zh": "删除记忆链接（按 link_id 或 source/target/link_type 定位）。", "en": "Delete a memory link (by link_id or source/target/link_type)." },
            "parameters": [
                { "name": "link_id", "description": { "zh": "可选：链接ID（优先使用）", "en": "Optional: link ID (preferred)" }, "type": "number", "required": false },
                { "name": "source_title", "description": { "zh": "可选：源记忆标题（未提供 link_id 时使用）", "en": "Optional: source title (used when link_id is not provided)" }, "type": "string", "required": false },
                { "name": "target_title", "description": { "zh": "可选：目标记忆标题（未提供 link_id 时使用）", "en": "Optional: target title (used when link_id is not provided)" }, "type": "string", "required": false },
                { "name": "link_type", "description": { "zh": "可选：关系类型（用于唯一定位）", "en": "Optional: relation type (for unique resolution)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "update_user_profile",
            "description": { "zh": "用完整 Markdown 更新 user.md。", "en": "Update user.md with the complete Markdown document." },
            "parameters": [
                { "name": "markdown", "description": { "zh": "必需：user.md 的完整内容", "en": "Required: complete contents of user.md" }, "type": "string", "required": true }
            ]
        }
    ]
}*/
const ExtendedMemoryTools = (function () {
    function resolveCallerCardId() {
        if (typeof getCallerCardId !== 'function') {
            return undefined;
        }
        const callerCardId = String(getCallerCardId() || '').trim();
        return callerCardId || undefined;
    }
    async function create_memory(params) {
        const result = await Tools.Memory.create({
            title: params.title,
            content: params.content,
            contentType: params.content_type,
            source: params.source,
            folderPath: params.folder_path,
            tags: params.tags,
            callerCardId: resolveCallerCardId(),
        });
        return { success: typeof result === 'string' && result.length > 0, message: '记忆创建完成', data: result };
    }
    async function update_memory(params) {
        const result = await Tools.Memory.update({
            oldTitle: params.old_title,
            newTitle: params.new_title,
            content: params.content,
            contentType: params.content_type,
            source: params.source,
            credibility: params.credibility,
            importance: params.importance,
            folderPath: params.folder_path,
            tags: params.tags,
            callerCardId: resolveCallerCardId(),
        });
        return { success: typeof result === 'string' && result.length > 0, message: '记忆更新完成', data: result };
    }
    async function delete_memory(params) {
        const result = await Tools.Memory.deleteMemory({
            title: params.title,
            callerCardId: resolveCallerCardId(),
        });
        return { success: typeof result === 'string' && result.length > 0, message: '记忆删除完成', data: result };
    }
    async function move_memory(params) {
        const titles = params.titles
            ? params.titles.split(/[,\n|]/).map(s => s.trim()).filter(Boolean)
            : undefined;
        const result = await Tools.Memory.move({
            targetFolderPath: params.target_folder_path,
            titles,
            sourceFolderPath: params.source_folder_path,
            callerCardId: resolveCallerCardId(),
        });
        return { success: typeof result === 'string' && result.length > 0, message: '记忆移动完成', data: result };
    }
    async function link_memories(params) {
        const result = await Tools.Memory.link({
            sourceTitle: params.source_title,
            targetTitle: params.target_title,
            linkType: params.link_type,
            weight: params.weight,
            description: params.description,
            callerCardId: resolveCallerCardId(),
        });
        return { success: !!result, message: '记忆链接创建完成', data: result };
    }
    async function query_memory_links(params) {
        const result = await Tools.Memory.queryLinks({
            linkId: params.link_id,
            sourceTitle: params.source_title,
            targetTitle: params.target_title,
            linkType: params.link_type,
            limit: params.limit,
            callerCardId: resolveCallerCardId(),
        });
        return { success: !!result, message: '记忆链接查询完成', data: result };
    }
    async function update_memory_link(params) {
        const result = await Tools.Memory.updateLink({
            linkId: params.link_id,
            sourceTitle: params.source_title,
            targetTitle: params.target_title,
            linkType: params.link_type,
            newLinkType: params.new_link_type,
            weight: params.weight,
            description: params.description,
            callerCardId: resolveCallerCardId(),
        });
        return { success: !!result, message: '记忆链接更新完成', data: result };
    }
    async function delete_memory_link(params) {
        const result = await Tools.Memory.deleteLink({
            linkId: params.link_id,
            sourceTitle: params.source_title,
            targetTitle: params.target_title,
            linkType: params.link_type,
            callerCardId: resolveCallerCardId(),
        });
        return { success: typeof result === 'string' ? result.length > 0 : !!result, message: '记忆链接删除完成', data: result };
    }
    async function update_user_profile(params) {
        const result = await toolCall({
            name: "update_user_profile",
            params: { markdown: params.markdown },
        });
        const success = typeof result === 'string' ? result.length > 0 : !!result;
        return { success, message: '用户偏好更新完成', data: result };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main() {
        const results = [];
        // 这些工具都可能修改记忆/偏好，默认不做自动化演示，避免污染用户数据。
        results.push({ tool: 'create_memory', result: { success: null, message: '未测试（会写入记忆库）' } });
        results.push({ tool: 'update_memory', result: { success: null, message: '未测试（会修改记忆库）' } });
        results.push({ tool: 'delete_memory', result: { success: null, message: '未测试（会删除记忆库数据）' } });
        results.push({ tool: 'move_memory', result: { success: null, message: '未测试（会批量修改记忆文件夹）' } });
        results.push({ tool: 'link_memories', result: { success: null, message: '未测试（会修改记忆库链接）' } });
        results.push({ tool: 'query_memory_links', result: { success: null, message: '未测试（只读查询）' } });
        results.push({ tool: 'update_memory_link', result: { success: null, message: '未测试（会修改记忆库链接）' } });
        results.push({ tool: 'delete_memory_link', result: { success: null, message: '未测试（会删除记忆库链接）' } });
        results.push({ tool: 'update_user_profile', result: { success: null, message: '未测试（会修改 user.md）' } });
        complete({
            success: true,
            message: "拓展记忆工具包加载完成（未执行破坏性测试）",
            data: { results }
        });
    }
    return {
        create_memory: (params) => wrapToolExecution(create_memory, params),
        update_memory: (params) => wrapToolExecution(update_memory, params),
        delete_memory: (params) => wrapToolExecution(delete_memory, params),
        move_memory: (params) => wrapToolExecution(move_memory, params),
        link_memories: (params) => wrapToolExecution(link_memories, params),
        query_memory_links: (params) => wrapToolExecution(query_memory_links, params),
        update_memory_link: (params) => wrapToolExecution(update_memory_link, params),
        delete_memory_link: (params) => wrapToolExecution(delete_memory_link, params),
        update_user_profile: (params) => wrapToolExecution(update_user_profile, params),
        main,
    };
})();
exports.create_memory = ExtendedMemoryTools.create_memory;
exports.update_memory = ExtendedMemoryTools.update_memory;
exports.delete_memory = ExtendedMemoryTools.delete_memory;
exports.move_memory = ExtendedMemoryTools.move_memory;
exports.link_memories = ExtendedMemoryTools.link_memories;
exports.query_memory_links = ExtendedMemoryTools.query_memory_links;
exports.update_memory_link = ExtendedMemoryTools.update_memory_link;
exports.delete_memory_link = ExtendedMemoryTools.delete_memory_link;
exports.update_user_profile = ExtendedMemoryTools.update_user_profile;
exports.main = ExtendedMemoryTools.main;
