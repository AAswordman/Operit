/**
 * Memory management namespace
 * Provides methods for managing the memory library
 */

/**
 * Memory management tools
 */
export namespace Memory {
    interface CallerScopedOptions {
        callerCardId?: string;
    }

    interface QueryOptions extends CallerScopedOptions {
        query: string;
        folderPath?: string;
        limit?: number;
        startTime?: string;
        endTime?: string;
        snapshotId?: string;
        threshold?: number;
    }

    interface GetByTitleOptions extends CallerScopedOptions {
        title: string;
        chunkIndex?: number;
        chunkRange?: string;
        query?: string;
        limit?: number;
    }

    interface CreateOptions extends CallerScopedOptions {
        title: string;
        content: string;
        contentType?: string;
        source?: string;
        folderPath?: string;
        tags?: string;
    }

    /**
     * Query the memory library
     * @param query - Search query. Supports a natural-language question, a space-separated phrase, or `|`-separated keywords. Inside a keyword, `*` works as a fuzzy wildcard placeholder such as `error*timeout`; use only `*` to return all memories
     * @param folderPath - Optional folder path to search within
     * @param limit - Optional maximum number of results (>=1, default 20)
     * @param startTime - Optional local-time start time in `YYYY-MM-DD` or `YYYY-MM-DD HH:mm` format, filters memories by createdAt >= startTime
     * @param endTime - Optional local-time end time in `YYYY-MM-DD` or `YYYY-MM-DD HH:mm` format, filters memories by createdAt <= endTime
     * @param snapshotId - Optional snapshot id. Omit or pass empty to auto-create one; pass any non-empty id to use or create that exact snapshot so follow-up or parallel queries can exclude already returned memories
     * @param threshold - 可选相关性阈值（>=0），只返回分数不低于此值的记忆；默认 0.025
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns 结构化查询结果。普通记忆的通配符查询只返回预览，可通过 getByTitle 读取全文；排名结果包含评分分项。
     */
    function query(
        query: string,
        folderPath?: string,
        limit?: number,
        startTime?: string,
        endTime?: string,
        snapshotId?: string,
        threshold?: number,
        callerCardId?: string
    ): Promise<import('./results').MemoryQueryResultData>;
    function query(options: QueryOptions): Promise<import('./results').MemoryQueryResultData>;

    /**
     * Get a memory by exact title
     * @param title - The exact title of the memory
     * @param chunkIndex - Optional chunk index for document nodes
     * @param chunkRange - Optional chunk range for document nodes (e.g., "3-7")
     * @param query - Optional query to search within the document. Supports natural language, space-separated phrases, `|`-separated keywords, and `*` as a fuzzy wildcard inside a keyword
     * @param limit - Optional maximum number of matched chunks when using query (>=1, default 20)
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns 普通记忆返回结构化结果；请求文档分块时返回字符串。
     */
    function getByTitle(title: string, chunkIndex?: number, chunkRange?: string, query?: string, limit?: number, callerCardId?: string): Promise<import('./results').MemoryQueryResultData | string>;
    function getByTitle(options: GetByTitleOptions): Promise<import('./results').MemoryQueryResultData | string>;

    /**
     * Create a new memory
     * @param title - Memory title
     * @param content - Memory content
     * @param contentType - Optional content type (default "text/plain")
     * @param source - Optional source (default "ai_created")
     * @param folderPath - Optional folder path (default "")
     * @param tags - Optional tags (comma-separated string)
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Creation result as a string
     */
    function create(title: string, content: string, contentType?: string, source?: string, folderPath?: string, tags?: string, callerCardId?: string): Promise<string>;
    function create(options: CreateOptions): Promise<string>;

    /**
     * Update options for memory update
     */
    interface UpdateOptions {
        oldTitle?: string;
        newTitle?: string;
        content?: string;
        contentType?: string;
        source?: string;
        credibility?: number;
        importance?: number;
        folderPath?: string;
        tags?: string;
        callerCardId?: string;
    }

    /**
     * Update an existing memory
     * @param oldTitle - The current title of the memory
     * @param updates - Update options
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Update result as a string
     */
    function update(oldTitle: string, updates?: Omit<UpdateOptions, 'oldTitle'>, callerCardId?: string): Promise<string>;
    function update(options: UpdateOptions & { oldTitle: string }): Promise<string>;

    interface DeleteOptions extends CallerScopedOptions {
        title: string;
    }

    /**
     * Delete a memory
     * @param title - The title of the memory to delete
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Deletion result as a string
     */
    function deleteMemory(title: string, callerCardId?: string): Promise<string>;
    function deleteMemory(options: DeleteOptions): Promise<string>;

    interface MoveOptions extends CallerScopedOptions {
        targetFolderPath: string;
        titles?: string[] | string;
        sourceFolderPath?: string;
    }

    /**
     * Move memories to another folder in batch
     * @param targetFolderPath - Target folder path (empty string means uncategorized)
     * @param titles - Optional memory titles (string array or comma-separated string)
     * @param sourceFolderPath - Optional source folder path (empty string means uncategorized)
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Move result as a string
     */
    function move(targetFolderPath: string, titles?: string[] | string, sourceFolderPath?: string, callerCardId?: string): Promise<string>;
    function move(options: MoveOptions): Promise<string>;

    interface LinkOptions extends CallerScopedOptions {
        sourceTitle: string;
        targetTitle: string;
        linkType?: string;
        weight?: number;
        description?: string;
    }

    /**
     * Create a link between two memories
     * @param sourceTitle - The title of the source memory
     * @param targetTitle - The title of the target memory
     * @param linkType - Optional link type (default "related")
     * @param weight - Optional link strength (0.0-1.0, default 0.7)
     * @param description - Optional description of the relationship
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Link creation result
     */
    function link(sourceTitle: string, targetTitle: string, linkType?: string, weight?: number, description?: string, callerCardId?: string): Promise<import('./results').MemoryLinkResultData>;
    function link(options: LinkOptions): Promise<import('./results').MemoryLinkResultData>;

    interface QueryLinksOptions extends CallerScopedOptions {
        linkId?: number;
        sourceTitle?: string;
        targetTitle?: string;
        linkType?: string;
        limit?: number;
    }

    /**
     * Query memory links with optional filters
     * @param linkId - Optional link ID (exact match)
     * @param sourceTitle - Optional source memory title filter
     * @param targetTitle - Optional target memory title filter
     * @param linkType - Optional link type filter
     * @param limit - Optional maximum number of links to return (1-200, default 20)
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Link query result
     */
    function queryLinks(
        linkId?: number,
        sourceTitle?: string,
        targetTitle?: string,
        linkType?: string,
        limit?: number,
        callerCardId?: string
    ): Promise<import('./results').MemoryLinkQueryResultData>;
    function queryLinks(options: QueryLinksOptions): Promise<import('./results').MemoryLinkQueryResultData>;

    interface UpdateLinkOptions extends CallerScopedOptions {
        linkId?: number;
        sourceTitle?: string;
        targetTitle?: string;
        linkType?: string;
        newLinkType?: string;
        weight?: number;
        description?: string;
    }

    /**
     * Update an existing memory link
     * @param linkId - Optional link ID (preferred locator)
     * @param sourceTitle - Optional source memory title (used when linkId is not provided)
     * @param targetTitle - Optional target memory title (used when linkId is not provided)
     * @param linkType - Optional current link type (for unique resolution)
     * @param newLinkType - Optional new link type
     * @param weight - Optional new link strength (0.0-1.0)
     * @param description - Optional new relationship description
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Link update result
     */
    function updateLink(
        linkId?: number,
        sourceTitle?: string,
        targetTitle?: string,
        linkType?: string,
        newLinkType?: string,
        weight?: number,
        description?: string,
        callerCardId?: string
    ): Promise<import('./results').MemoryLinkResultData>;
    function updateLink(options: UpdateLinkOptions): Promise<import('./results').MemoryLinkResultData>;

    interface DeleteLinkOptions extends CallerScopedOptions {
        linkId?: number;
        sourceTitle?: string;
        targetTitle?: string;
        linkType?: string;
    }

    /**
     * Delete an existing memory link
     * @param linkId - Optional link ID (preferred locator)
     * @param sourceTitle - Optional source memory title (used when linkId is not provided)
     * @param targetTitle - Optional target memory title (used when linkId is not provided)
     * @param linkType - Optional link type (for unique resolution)
     * @param callerCardId - Optional caller role card id used to select the bound memory profile
     * @returns Deletion result as a string
     */
    function deleteLink(linkId?: number, sourceTitle?: string, targetTitle?: string, linkType?: string, callerCardId?: string): Promise<string>;
    function deleteLink(options: DeleteLinkOptions): Promise<string>;
}
