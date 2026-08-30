/**
 * Chat Manager Tool definitions for Assistance Package
 * 
 * This file provides type definitions for chat management operations,
 * including creating chats, listing chats, switching between chats, and sending messages.
 */

import {
    ChatServiceStartResultData,
    ChatCreationResultData,
    ChatListResultData,
    ChatFindResultData,
    CurrentChatRuntimeStateResultData,
    GlobalChatRuntimeStateResultData,
    ChatSwitchResultData,
    ChatTitleUpdateResultData,
    ChatDeleteResultData,
    MessageSendResultData,
    MessageSendStreamEventData,
    ChatMessagesResultData,
    CharacterCardListResultData
} from './results';

/**
 * Chat Manager namespace
 * Provides methods for managing chat conversations
 */
export namespace Chat {
    type Runtime = 'main' | 'floating';

    interface StartServiceOptions {
        initial_mode?: 'WINDOW' | 'BALL' | 'VOICE_BALL' | 'FULLSCREEN' | 'RESULT_DISPLAY' | 'SCREEN_OCR';
        auto_enter_voice_chat?: boolean;
        wake_launched?: boolean;
        timeout_ms?: number;
        keep_if_exists?: boolean;
    }

    interface SendMessageOptions {
        runtime?: Runtime;
        persist_turn?: boolean;
        notify_reply?: boolean;
        hide_user_message?: boolean;
        disable_warning?: boolean;
        timeout_ms?: number;
    }

    interface SendMessageStreamingOptions extends SendMessageOptions {
        waifu?: boolean;
        onIntermediateResult?: (event: MessageSendStreamEventData) => void;
    }

    /**
     * Start the chat service (floating window)
     * @param options - Optional service startup options
     * @returns Promise resolving to service start result
     */
    function startService(options?: StartServiceOptions): Promise<ChatServiceStartResultData>;

    /**
     * Create a new chat conversation
     * @param group - Optional group name for the new chat
     * @param setAsCurrentChat - Optional, whether to switch to the new chat (default true)
     * @param characterCardId - Optional character card id to bind for the new chat
     * @returns Promise resolving to the new chat creation result
     */
    function createNew(group?: string, setAsCurrentChat?: boolean, characterCardId?: string): Promise<ChatCreationResultData>;

    /**
     * List all chat conversations
     * @returns Promise resolving to the list of all chats
     */
    function listAll(): Promise<ChatListResultData>;

    /**
     * List chat conversations with filters
     */
    function listChats(params?: {
        query?: string;
        match?: 'contains' | 'exact' | 'regex';
        limit?: number;
        sort_by?: 'updatedAt' | 'createdAt' | 'messageCount';
        sort_order?: 'asc' | 'desc';
    }): Promise<ChatListResultData>;

    /**
     * Find a chat by title or id
     */
    function findChat(params: {
        query: string;
        match?: 'contains' | 'exact' | 'regex';
        index?: number;
    }): Promise<ChatFindResultData>;

    /**
     * Read the current runtime state of the default or specified conversation
     */
    function getCurrentChatRuntimeState(chatId?: string): Promise<CurrentChatRuntimeStateResultData>;
    /**
     * Read the global runtime state across all conversations
     */
    function getGlobalChatRuntimeState(): Promise<GlobalChatRuntimeStateResultData>;

    /**
     * Switch to a specific chat conversation
     * @param chatId - The ID of the chat to switch to
     * @returns Promise resolving to the chat switch result
     */
    function switchTo(chatId: string): Promise<ChatSwitchResultData>;

    /**
     * Update chat title
     */
    function updateTitle(chatId: string, title: string): Promise<ChatTitleUpdateResultData>;

    /**
     * Delete a chat conversation by id
     */
    function deleteChat(chatId: string): Promise<ChatDeleteResultData>;

    /**
     * Send a message to the AI
     * @param message - The message content to send
     * @param chatId - Optional chat ID to send the message to (defaults to current chat)
     * @param roleCardId - Optional role card ID to use for this send
     * @param senderName - Optional display name when AI sends as user
     * @param options - Optional per-turn controls for persistence, notification, hidden user-message display, and timeout
     * @returns Promise resolving to the message send result
     */
    function sendMessage(
        message: string,
        chatId?: string,
        roleCardId?: string,
        senderName?: string,
        options?: SendMessageOptions
    ): Promise<MessageSendResultData>;

    /**
     * Send a message to the AI and receive incremental reply chunks.
     * @param message - The message content to send
     * @param chatId - Optional chat ID to send the message to (defaults to current chat)
     * @param roleCardId - Optional role card ID to use for this send
     * @param senderName - Optional display name when AI sends as user
     * @param options - Optional per-turn controls, plus streaming callback and waifu-style chunk aggregation
     * @returns Promise resolving to the final message send result
     */
    function sendMessageStreaming(
        message: string,
        chatId?: string,
        roleCardId?: string,
        senderName?: string,
        options?: SendMessageStreamingOptions
    ): Promise<MessageSendResultData>;

    /**
     * List all character cards
     */
    function listCharacterCards(): Promise<CharacterCardListResultData>;

    /**
     * Get messages from a specific chat
     * @param chatId - The ID of the chat to read
     * @param options - Optional order/limit
     */
    function getMessages(chatId: string, options?: { order?: 'asc' | 'desc'; limit?: number }): Promise<ChatMessagesResultData>;

    /**
     * Get messages from a specific chat by zero-based message index range
     * @param chatId - The ID of the chat to read
     * @param options - Order and inclusive start/end indexes
     */
    function getMessagesRange(chatId: string, options: { order?: 'asc' | 'desc'; start: number; end: number }): Promise<ChatMessagesResultData>;
}
