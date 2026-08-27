import { useEffect, useMemo, useRef, useState } from 'react';
import {
  bootstrap,
  createChat,
  deleteChat,
  deleteGroup as deleteGroupOnServer,
  getCharacterSelector,
  getInputSettings,
  getMemorySelector,
  getMessageLocatorEntries,
  getMessages,
  getModelSelector,
  getTheme,
  listChats,
  renameGroup as renameGroupOnServer,
  reorderChats as reorderChatsOnServer,
  runManualConversationSummary as runManualConversationSummaryOnServer,
  runManualMemoryUpdate as runManualMemoryUpdateOnServer,
  selectModel as selectModelOnServer,
  selectMemoryProfile as selectMemoryProfileOnServer,
  selectChat as selectChatOnServer,
  setActivePrompt as setActivePromptOnServer,
  streamMessage,
  revealMessageWindow,
  toggleMessageFavorite as toggleMessageFavoriteOnServer,
  updateChat as updateChatOnServer,
  updateInputSettings as updateInputSettingsOnServer,
  uploadAttachment
} from '../util/chatApi';
import {
  clearStoredToken,
  readStoredToken,
  writeStoredToken
} from '../util/ConfigurationStateHolder';
import type {
  ChatStyle,
  ContextStatsSnapshot,
  HistoryDisplayMode,
  InputProcessingStage,
  InputStyle,
  PendingQueueMessageItem,
  WebActivePromptSnapshot,
  WebActivePromptTarget,
  WebBootstrapResponse,
  WebChatMessage,
  WebChatMessageLocatorPreview,
  WebChatReorderItem,
  WebCharacterSelectorResponse,
  WebChatSummary,
  WebInputSettingsState,
  WebMemorySelectorState,
  WebModelSelectorState,
  WebSelectModelResponse,
  WebThemeSnapshot,
  WebUploadedAttachment
} from '../util/chatTypes';
import {
  buildContextStats,
  buildVisibleChats,
  handleUnauthorizedError,
  normalizeError
} from './UiStateDelegate';

const INITIAL_MESSAGES_PAGE_SIZE = 24;
const HISTORY_DISPLAY_MODE_KEY = 'web_chat_history_display_mode';
const AUTO_SWITCH_CHARACTER_CARD_KEY = 'web_chat_auto_switch_character_card';
const AUTO_SWITCH_CHAT_ON_CHARACTER_SELECT_KEY = 'web_chat_auto_switch_chat_on_character_select';

function readStoredHistoryDisplayMode(): HistoryDisplayMode {
  if (typeof window === 'undefined') {
    return 'CURRENT_CHARACTER_ONLY';
  }
  const stored = window.localStorage.getItem(HISTORY_DISPLAY_MODE_KEY);
  return stored === 'BY_CHARACTER_CARD' ||
    stored === 'BY_FOLDER' ||
    stored === 'CURRENT_CHARACTER_ONLY'
    ? stored
    : 'CURRENT_CHARACTER_ONLY';
}

function readStoredBoolean(key: string, fallback = false) {
  if (typeof window === 'undefined') {
    return fallback;
  }
  const stored = window.localStorage.getItem(key);
  if (stored == null) {
    return fallback;
  }
  return stored === 'true';
}

function writeStoredValue(key: string, value: string) {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(key, value);
  }
}

function buildCreateChatBinding(activePrompt: WebActivePromptSnapshot | null) {
  if (!activePrompt) {
    return {
      character_card_name: null,
      character_group_id: null
    };
  }

  if (activePrompt.type === 'character_group') {
    return {
      character_card_name: null,
      character_group_id: activePrompt.id
    };
  }

  return {
    character_card_name: activePrompt.name,
    character_group_id: null
  };
}

function resolvePromptTargetForChat(
  chat: WebChatSummary,
  characterSelector: WebCharacterSelectorResponse | null
): WebActivePromptTarget | null {
  const groupId = normalizeBindingValue(chat.character_group_id);
  if (groupId) {
    return {
      type: 'character_group',
      id: groupId
    };
  }

  const characterCardName = normalizeBindingValue(chat.character_card_name);
  if (!characterCardName) {
    return null;
  }

  const card = characterSelector?.cards.find((item) => item.name === characterCardName);
  if (!card) {
    return null;
  }

  return {
    type: 'character_card',
    id: card.id
  };
}

function matchesPromptTarget(
  chat: WebChatSummary,
  target: WebActivePromptTarget,
  characterSelector: WebCharacterSelectorResponse | null
) {
  if (target.type === 'character_group') {
    return normalizeBindingValue(chat.character_group_id) === normalizeBindingValue(target.id);
  }

  const card = characterSelector?.cards.find((item) => item.id === target.id);
  if (!card) {
    return false;
  }
  return (
    !normalizeBindingValue(chat.character_group_id) &&
    normalizeBindingValue(chat.character_card_name) === normalizeBindingValue(card.name)
  );
}

function normalizeBindingValue(value?: string | null) {
  const normalized = value?.trim();
  return normalized ? normalized : null;
}

type DeleteReplacementTarget =
  | {
      kind: 'group';
      characterGroupId: string;
    }
  | {
      kind: 'card';
      characterCardName: string;
      includeUnboundChats: boolean;
    }
  | {
      kind: 'unbound';
    };

function resolveDeleteReplacementTarget(
  chat: WebChatSummary,
  activePrompt: WebActivePromptSnapshot | null
): DeleteReplacementTarget {
  const characterGroupId = normalizeBindingValue(chat.character_group_id);
  if (characterGroupId) {
    return {
      kind: 'group',
      characterGroupId
    };
  }

  const characterCardName = normalizeBindingValue(chat.character_card_name);
  if (characterCardName) {
    return {
      kind: 'card',
      characterCardName,
      includeUnboundChats: false
    };
  }

  if (activePrompt?.type === 'character_group') {
    const activeGroupId = normalizeBindingValue(activePrompt.id);
    if (activeGroupId) {
      return {
        kind: 'group',
        characterGroupId: activeGroupId
      };
    }
  }

  if (activePrompt?.type === 'character_card') {
    const activeCardName = normalizeBindingValue(activePrompt.name);
    if (activeCardName) {
      return {
        kind: 'card',
        characterCardName: activeCardName,
        includeUnboundChats: true
      };
    }
  }

  return {
    kind: 'unbound'
  };
}

function matchesDeleteReplacementTarget(
  chat: WebChatSummary,
  target: DeleteReplacementTarget
) {
  const characterGroupId = normalizeBindingValue(chat.character_group_id);
  const characterCardName = normalizeBindingValue(chat.character_card_name);

  if (target.kind === 'group') {
    return characterGroupId === target.characterGroupId;
  }

  if (target.kind === 'card') {
    if (characterGroupId) {
      return false;
    }
    if (target.includeUnboundChats) {
      return characterCardName === null || characterCardName === target.characterCardName;
    }
    return characterCardName === target.characterCardName;
  }

  return characterGroupId === null && characterCardName === null;
}

function buildCreateChatBindingFromDeleteTarget(target: DeleteReplacementTarget) {
  if (target.kind === 'group') {
    return {
      character_card_name: null,
      character_group_id: target.characterGroupId
    };
  }

  if (target.kind === 'card') {
    return {
      character_card_name: target.characterCardName,
      character_group_id: null
    };
  }

  return {
    character_card_name: null,
    character_group_id: null
  };
}

function formatOutgoingAttachments(uploads: WebUploadedAttachment[]) {
  return uploads.map((upload) => ({
    id: upload.attachment_id,
    file_name: upload.file_name,
    mime_type: upload.mime_type,
    file_size: upload.file_size
  }));
}

function uniqueUploadFileName(fileName: string, usedFileNames: Set<string>) {
  if (!usedFileNames.has(fileName)) {
    return fileName;
  }

  const dotIndex = fileName.lastIndexOf('.');
  const hasExtension = dotIndex > 0 && dotIndex < fileName.length - 1;
  const baseName = hasExtension ? fileName.slice(0, dotIndex) : fileName;
  const extension = hasExtension ? fileName.slice(dotIndex) : '';

  let index = 2;
  let candidate = `${baseName}_${index}${extension}`;
  while (usedFileNames.has(candidate)) {
    index += 1;
    candidate = `${baseName}_${index}${extension}`;
  }
  return candidate;
}

function makeUploadsFileNamesUnique(
  currentUploads: WebUploadedAttachment[],
  incomingUploads: WebUploadedAttachment[]
) {
  const usedFileNames = new Set(currentUploads.map((upload) => upload.file_name));
  return incomingUploads.map((upload) => {
    const uniqueFileName = uniqueUploadFileName(upload.file_name, usedFileNames);
    usedFileNames.add(uniqueFileName);
    if (uniqueFileName === upload.file_name) {
      return upload;
    }
    return {
      ...upload,
      file_name: uniqueFileName
    };
  });
}

function mergeLatestConversationPage(
  existingMessages: WebChatMessage[],
  latestMessages: WebChatMessage[]
) {
  if (!latestMessages.length) {
    return existingMessages;
  }

  const latestIds = new Set(latestMessages.map((message) => message.id));
  const oldestLatestTimestamp = latestMessages[0]?.timestamp ?? Number.NEGATIVE_INFINITY;
  const preservedOlderMessages = existingMessages.filter(
    (message) => !latestIds.has(message.id) && message.timestamp < oldestLatestTimestamp
  );
  return preservedOlderMessages.concat(latestMessages);
}

function prependOlderMessages(
  existingMessages: WebChatMessage[],
  olderMessages: WebChatMessage[]
) {
  if (!olderMessages.length) {
    return existingMessages;
  }

  const existingIds = new Set(existingMessages.map((message) => message.id));
  return olderMessages
    .filter((message) => !existingIds.has(message.id))
    .concat(existingMessages);
}

function appendNewerMessages(
  existingMessages: WebChatMessage[],
  newerMessages: WebChatMessage[]
) {
  if (!newerMessages.length) {
    return existingMessages;
  }

  const existingIds = new Set(existingMessages.map((message) => message.id));
  return existingMessages.concat(newerMessages.filter((message) => !existingIds.has(message.id)));
}

function matchesActivePromptTarget(
  activePrompt: WebActivePromptSnapshot | null | undefined,
  target: WebActivePromptTarget
) {
  if (!activePrompt) {
    return false;
  }
  return activePrompt.type === target.type && activePrompt.id === target.id;
}

function appendStreamingAssistantDelta(
  existingMessages: WebChatMessage[],
  assistantMessageId: string,
  delta: string,
  partialMessage?: WebChatMessage | null
) {
  let found = false;
  const nextMessages = existingMessages.map((message) => {
    if (message.id !== assistantMessageId) {
      return message;
    }

    found = true;
    return {
      ...message,
      ...(partialMessage
        ? {
            ...partialMessage,
            id: assistantMessageId
          }
        : {}),
      content_raw: partialMessage?.content_raw ?? message.content_raw + delta,
      content_blocks: partialMessage?.content_blocks ?? message.content_blocks,
      streaming: true
    };
  });

  if (found) {
    return nextMessages;
  }

  return nextMessages.concat({
    ...(partialMessage
      ? {
          ...partialMessage,
          id: assistantMessageId
        }
      : {
          id: assistantMessageId,
          sender: 'assistant' as const,
          content_raw: delta,
          timestamp: Date.now(),
          attachments: []
        }),
    streaming: true
  });
}

function finalizeStreamingAssistantMessage(
  existingMessages: WebChatMessage[],
  assistantMessageId: string,
  finalMessage?: WebChatMessage | null
) {
  let found = false;
  const nextMessages = existingMessages.map((message) => {
    if (message.id !== assistantMessageId) {
      return message;
    }

    found = true;
    return finalMessage
      ? {
          ...finalMessage,
          streaming: false
        }
      : {
          ...message,
          streaming: false
        };
  });

  if (found || !finalMessage) {
    return nextMessages;
  }

  return nextMessages.concat({
    ...finalMessage,
    streaming: false
  });
}

export interface ChatViewModelState {
  token: string;
  tokenDraft: string;
  boot: WebBootstrapResponse | null;
  theme: WebThemeSnapshot | null;
  characterSelector: WebCharacterSelectorResponse | null;
  characterSelectorOpen: boolean;
  characterSelectorLoading: boolean;
  modelSelector: WebModelSelectorState | null;
  modelSelectorLoading: boolean;
  chats: WebChatSummary[];
  visibleChats: WebChatSummary[];
  selectedChatId: string | null;
  selectedChat: WebChatSummary | null;
  messages: WebChatMessage[];
  messageInput: string;
  search: string;
  pendingUploads: WebUploadedAttachment[];
  pendingQueueMessages: PendingQueueMessageItem[];
  isPendingQueueExpanded: boolean;
  error: string | null;
  isConnecting: boolean;
  isBusy: boolean;
  isStreaming: boolean;
  historyOpen: boolean;
  historyLoading: boolean;
  hasMoreHistoryBefore: boolean;
  hasMoreHistoryAfter: boolean;
  isLoadingHistoryBefore: boolean;
  isLoadingHistoryAfter: boolean;
  attachmentPanelOpen: boolean;
  inputProcessingStage: InputProcessingStage;
  activeChatStyle: ChatStyle;
  activeInputStyle: InputStyle;
  activeCharacterName: string;
  activeCharacterAvatarUrl?: string | null;
  showConnectionOverlay: boolean;
  activeStreamingCount: number;
  contextStats: ContextStatsSnapshot;
  autoScrollToBottom: boolean;
  historyDisplayMode: HistoryDisplayMode;
  autoSwitchCharacterCard: boolean;
  autoSwitchChatOnCharacterSelect: boolean;
  inputSettings: WebInputSettingsState | null;
  memorySelector: WebMemorySelectorState | null;
}

export interface ChatViewModelActions {
  setTokenDraft: (value: string) => void;
  submitToken: () => void;
  createConversation: (options?: { group?: string | null }) => Promise<void>;
  renameConversation: (chat: WebChatSummary, title: string) => Promise<void>;
  deleteConversation: (chat: WebChatSummary) => Promise<void>;
  selectChat: (chatId: string) => void;
  setMessageInput: (value: string) => void;
  setSearch: (value: string) => void;
  uploadFiles: (files: FileList | File[]) => Promise<void>;
  removePendingUpload: (attachmentId: string) => void;
  sendMessage: () => Promise<void>;
  cancelCurrentMessage: () => void;
  setHistoryOpen: (value: boolean) => void;
  setCharacterSelectorOpen: (value: boolean) => void;
  switchActivePrompt: (target: WebActivePromptTarget) => Promise<void>;
  selectModelConfig: (
    configId: string,
    modelIndex: number,
    confirmCharacterCardSwitch?: boolean
  ) => Promise<WebSelectModelResponse | null>;
  loadOlderMessages: () => Promise<void>;
  loadNewerMessages: () => Promise<void>;
  showLatestMessages: () => Promise<void>;
  setAttachmentPanelOpen: (value: boolean) => void;
  queueDraftMessage: () => void;
  setPendingQueueExpanded: (value: boolean) => void;
  deletePendingQueueMessage: (id: number) => void;
  editPendingQueueMessage: (id: number) => void;
  sendPendingQueueMessage: (id: number) => Promise<void>;
  setAutoScrollToBottom: (value: boolean) => void;
  setHistoryDisplayMode: (value: HistoryDisplayMode) => void;
  setAutoSwitchCharacterCard: (value: boolean) => void;
  setAutoSwitchChatOnCharacterSelect: (value: boolean) => void;
  updateConversation: (
    chat: WebChatSummary,
    payload: {
      title?: string;
      group?: string | null;
      update_group?: boolean;
      locked?: boolean;
      update_locked?: boolean;
      character_card_name?: string | null;
      character_group_id?: string | null;
      update_binding?: boolean;
    }
  ) => Promise<WebChatSummary | null>;
  reorderConversations: (items: WebChatReorderItem[]) => Promise<void>;
  renameGroup: (oldName: string, newName: string, characterCardName?: string | null) => Promise<void>;
  deleteGroup: (
    groupName: string,
    deleteChats: boolean,
    characterCardName?: string | null
  ) => Promise<void>;
  updateInputSettings: (
    payload: Partial<{
      enable_thinking_mode: boolean;
      thinking_quality_level: number;
      enable_memory_auto_update: boolean;
      enable_auto_read: boolean;
      enable_max_context_mode: boolean;
      enable_tools: boolean;
      disable_stream_output: boolean;
      disable_user_preference_description: boolean;
      permission_level: string;
    }>
  ) => Promise<void>;
  selectMemoryProfile: (profileId: string) => Promise<void>;
  runManualMemoryUpdate: () => Promise<void>;
  runManualConversationSummary: () => Promise<void>;
  loadMessageLocatorEntries: (
    chatId: string,
    query?: string
  ) => Promise<WebChatMessageLocatorPreview[]>;
  revealMessageForCurrentChat: (targetTimestamp: number) => Promise<boolean>;
  toggleMessageFavorite: (timestamp: number, isFavorite: boolean) => Promise<void>;
}

export type ChatViewModel = ChatViewModelState & ChatViewModelActions;

export function useChatViewModel(): ChatViewModel {
  const [token, setToken] = useState<string>(() => readStoredToken());
  const [tokenDraft, setTokenDraft] = useState<string>(() => readStoredToken());
  const [boot, setBoot] = useState<WebBootstrapResponse | null>(null);
  const [theme, setTheme] = useState<WebThemeSnapshot | null>(null);
  const [characterSelector, setCharacterSelector] = useState<WebCharacterSelectorResponse | null>(null);
  const [characterSelectorOpen, setCharacterSelectorOpenState] = useState(false);
  const [characterSelectorLoading, setCharacterSelectorLoading] = useState(false);
  const [modelSelector, setModelSelector] = useState<WebModelSelectorState | null>(null);
  const [modelSelectorLoading, setModelSelectorLoading] = useState(false);
  const [chats, setChats] = useState<WebChatSummary[]>([]);
  const [selectedChatId, setSelectedChatId] = useState<string | null>(null);
  const [messages, setMessages] = useState<WebChatMessage[]>([]);
  const [messageInput, setMessageInput] = useState('');
  const [search, setSearch] = useState('');
  const [pendingUploads, setPendingUploads] = useState<WebUploadedAttachment[]>([]);
  const [pendingQueueMessages, setPendingQueueMessages] = useState<PendingQueueMessageItem[]>([]);
  const [isPendingQueueExpanded, setPendingQueueExpanded] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isConnecting, setConnecting] = useState(Boolean(token));
  const [isBusy, setBusy] = useState(false);
  const [isStreaming, setStreaming] = useState(false);
  const [historyOpen, setHistoryOpenState] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const [hasMoreHistoryBefore, setHasMoreHistoryBefore] = useState(false);
  const [hasMoreHistoryAfter, setHasMoreHistoryAfter] = useState(false);
  const [isLoadingHistoryBefore, setLoadingHistoryBefore] = useState(false);
  const [isLoadingHistoryAfter, setLoadingHistoryAfter] = useState(false);
  const [attachmentPanelOpen, setAttachmentPanelOpen] = useState(false);
  const [inputProcessingStage, setInputProcessingStage] = useState<InputProcessingStage>('idle');
  const [autoScrollToBottom, setAutoScrollToBottom] = useState(true);
  const [historyDisplayMode, setHistoryDisplayModeState] = useState<HistoryDisplayMode>(
    readStoredHistoryDisplayMode
  );
  const [autoSwitchCharacterCard, setAutoSwitchCharacterCardState] = useState(() =>
    readStoredBoolean(AUTO_SWITCH_CHARACTER_CARD_KEY, false)
  );
  const [autoSwitchChatOnCharacterSelect, setAutoSwitchChatOnCharacterSelectState] = useState(() =>
    readStoredBoolean(AUTO_SWITCH_CHAT_ON_CHARACTER_SELECT_KEY, false)
  );
  const [inputSettings, setInputSettings] = useState<WebInputSettingsState | null>(null);
  const [memorySelector, setMemorySelector] = useState<WebMemorySelectorState | null>(null);
  const streamAbortRef = useRef<AbortController | null>(null);
  const queueIdRef = useRef(1);
  const skipNextConversationLoadRef = useRef(false);
  const selectedChatIdRef = useRef<string | null>(null);
  const messageInputRef = useRef('');
  const messageDraftsByChatIdRef = useRef<Record<string, string>>({});

  function saveMessageDraft(chatId: string, value: string) {
    if (value.length === 0) {
      delete messageDraftsByChatIdRef.current[chatId];
      return;
    }

    messageDraftsByChatIdRef.current[chatId] = value;
  }

  function clearMessageDraft(chatId: string | null) {
    if (chatId != null) {
      delete messageDraftsByChatIdRef.current[chatId];
    }
  }

  function updateMessageInput(value: string) {
    messageInputRef.current = value;
    setMessageInput(value);
    const chatId = selectedChatIdRef.current;
    if (chatId != null) {
      saveMessageDraft(chatId, value);
    }
  }

  function activateChat(chatId: string | null, saveCurrentDraft = true) {
    const previousChatId = selectedChatIdRef.current;
    if (previousChatId === chatId) {
      setSelectedChatId(chatId);
      return;
    }

    if (previousChatId != null && saveCurrentDraft) {
      saveMessageDraft(previousChatId, messageInputRef.current);
    }

    selectedChatIdRef.current = chatId;
    setSelectedChatId(chatId);
    const nextInput = chatId != null ? messageDraftsByChatIdRef.current[chatId] ?? '' : '';
    messageInputRef.current = nextInput;
    setMessageInput(nextInput);
  }

  function resetChatDrafts() {
    messageDraftsByChatIdRef.current = {};
    selectedChatIdRef.current = null;
    messageInputRef.current = '';
    setSelectedChatId(null);
    setMessageInput('');
  }

  function handleApiFailure(loadError: unknown) {
    if (handleUnauthorizedError(loadError, () => setToken(''))) {
      setTokenDraft('');
      clearStoredToken();
    }
    setError(normalizeError(loadError));
  }

  async function refreshCharacterSelector(currentToken: string) {
    setCharacterSelectorLoading(true);
    try {
      const selectorData = await getCharacterSelector(currentToken);
      setCharacterSelector(selectorData);
    } finally {
      setCharacterSelectorLoading(false);
    }
  }

  async function refreshModelSelector(currentToken: string) {
    setModelSelectorLoading(true);
    try {
      const selectorData = await getModelSelector(currentToken);
      setModelSelector(selectorData);
      return selectorData;
    } finally {
      setModelSelectorLoading(false);
    }
  }

  async function refreshMemorySelector(currentToken: string) {
    const selectorData = await getMemorySelector(currentToken);
    setMemorySelector(selectorData);
    return selectorData;
  }

  async function refreshChats(currentToken: string) {
    const chatList = await listChats(currentToken);
    setChats(chatList);
    setHistoryLoaded(true);
    return chatList;
  }

  async function ensureChatsLoaded(currentToken: string, force = false) {
    if (historyLoading || (!force && historyLoaded)) {
      return;
    }

    setHistoryLoading(true);
    try {
      await refreshChats(currentToken);
    } finally {
      setHistoryLoading(false);
    }
  }

  async function loadBootstrap(currentToken: string, preferredChatId?: string | null) {
    setConnecting(true);
    setInputProcessingStage('connecting');

    try {
      const [bootData, selectorData, modelSelectorData, inputSettingsData, memorySelectorData] =
        await Promise.all([
        bootstrap(currentToken),
        getCharacterSelector(currentToken),
        getModelSelector(currentToken),
        getInputSettings(currentToken),
        getMemorySelector(currentToken)
      ]);
      setBoot(bootData);
      setCharacterSelector(selectorData);
      setModelSelector(modelSelectorData);
      setInputSettings(inputSettingsData);
      setMemorySelector(memorySelectorData);
      const nextChatId = preferredChatId ?? bootData.current_chat_id ?? null;
      activateChat(nextChatId);
      if (!nextChatId) {
        setTheme(null);
        setMessages([]);
        setHasMoreHistoryBefore(false);
        setHasMoreHistoryAfter(false);
      }
      setError(null);
    } finally {
      setConnecting(false);
      setInputProcessingStage(isStreaming ? 'streaming' : 'idle');
    }
  }

  async function loadConversation(
    currentToken: string,
    chatId: string,
    mode: 'replace' | 'merge-latest' = 'replace'
  ) {
    setConnecting(true);
    setInputProcessingStage((currentStage) => (isStreaming ? currentStage : 'connecting'));

    try {
      if (mode === 'replace') {
        await selectChatOnServer(currentToken, chatId);
      }
      const [themeData, messagePage, selectorData, modelSelectorData, inputSettingsData] =
        await Promise.all([
        getTheme(currentToken, chatId),
        getMessages(currentToken, chatId, { limit: INITIAL_MESSAGES_PAGE_SIZE }),
        getCharacterSelector(currentToken),
        getModelSelector(currentToken),
        getInputSettings(currentToken)
      ]);
      setTheme(themeData);
      setCharacterSelector(selectorData);
      setModelSelector(modelSelectorData);
      setInputSettings(inputSettingsData);
      setMessages((currentMessages) =>
        mode === 'merge-latest'
          ? mergeLatestConversationPage(currentMessages, messagePage.messages)
          : messagePage.messages
      );
      setHasMoreHistoryBefore(messagePage.has_more_before);
      setHasMoreHistoryAfter(messagePage.has_more_after);
      setError(null);
    } finally {
      setConnecting(false);
      setInputProcessingStage(isStreaming ? 'streaming' : 'idle');
    }
  }

  useEffect(() => {
    if (!token) {
      setBoot(null);
      setTheme(null);
      setCharacterSelector(null);
      setCharacterSelectorOpenState(false);
      setCharacterSelectorLoading(false);
      setModelSelector(null);
      setModelSelectorLoading(false);
      setChats([]);
      resetChatDrafts();
      setMessages([]);
      setPendingUploads([]);
      setPendingQueueMessages([]);
      setConnecting(false);
      setHistoryLoaded(false);
      setHistoryLoading(false);
      setHasMoreHistoryBefore(false);
      setHasMoreHistoryAfter(false);
      setLoadingHistoryBefore(false);
      setLoadingHistoryAfter(false);
      setInputSettings(null);
      setInputProcessingStage('idle');
      return;
    }

    void loadBootstrap(token).catch((loadError: unknown) => {
      handleApiFailure(loadError);
      setConnecting(false);
      setInputProcessingStage('idle');
    });
  }, [token]);

  useEffect(() => {
    if (!token || !selectedChatId) {
      return;
    }
    if (skipNextConversationLoadRef.current) {
      skipNextConversationLoadRef.current = false;
      return;
    }

    void loadConversation(token, selectedChatId).catch((loadError: unknown) => {
      handleApiFailure(loadError);
    });
  }, [selectedChatId, token]);

  async function loadOlderMessages() {
    if (!token || !selectedChatId || !hasMoreHistoryBefore || isLoadingHistoryBefore) {
      return;
    }

    const oldestTimestamp = messages[0]?.timestamp;
    if (!oldestTimestamp) {
      return;
    }

    setLoadingHistoryBefore(true);
    try {
      const page = await getMessages(token, selectedChatId, {
        beforeTimestamp: oldestTimestamp,
        limit: INITIAL_MESSAGES_PAGE_SIZE
      });
      setMessages((currentMessages) => prependOlderMessages(currentMessages, page.messages));
      setHasMoreHistoryBefore(page.has_more_before);
      setHasMoreHistoryAfter(page.has_more_after);
    } catch (loadError: unknown) {
      handleApiFailure(loadError);
    } finally {
      setLoadingHistoryBefore(false);
    }
  }

  async function loadNewerMessages() {
    if (!token || !selectedChatId || !hasMoreHistoryAfter || isLoadingHistoryAfter) {
      return;
    }

    const newestTimestamp = messages[messages.length - 1]?.timestamp;
    if (!newestTimestamp) {
      return;
    }

    setLoadingHistoryAfter(true);
    try {
      const page = await getMessages(token, selectedChatId, {
        afterTimestamp: newestTimestamp,
        limit: INITIAL_MESSAGES_PAGE_SIZE
      });
      setMessages((currentMessages) => appendNewerMessages(currentMessages, page.messages));
      setHasMoreHistoryBefore(page.has_more_before);
      setHasMoreHistoryAfter(page.has_more_after);
    } catch (loadError: unknown) {
      handleApiFailure(loadError);
    } finally {
      setLoadingHistoryAfter(false);
    }
  }

  async function showLatestMessages() {
    if (!token || !selectedChatId) {
      return;
    }
    await loadConversation(token, selectedChatId, 'merge-latest');
    setAutoScrollToBottom(true);
  }

  function shouldConfirmContextLimit(outgoingText: string) {
    if (!inputSettings || inputSettings.max_window_tokens <= 0) {
      return true;
    }
    const projectedTokens = inputSettings.current_window_tokens + outgoingText.trim().length;
    if (projectedTokens <= inputSettings.max_window_tokens) {
      return true;
    }
    if (typeof window === 'undefined') {
      return true;
    }
    return window.confirm(
      `当前消息可能超过上下文上限（${projectedTokens} / ${inputSettings.max_window_tokens}）。仍要发送吗？`
    );
  }

  async function syncActivePromptForChat(currentToken: string, chat: WebChatSummary) {
    if (!autoSwitchCharacterCard) {
      return;
    }

    const target = resolvePromptTargetForChat(chat, characterSelector);
    if (!target || matchesActivePromptTarget(characterSelector?.active_prompt, target)) {
      return;
    }

    const selectorData = await setActivePromptOnServer(currentToken, target);
    setCharacterSelector(selectorData);
    await refreshModelSelector(currentToken);

    if (selectedChatId === chat.id) {
      const themeData = await getTheme(currentToken, chat.id);
      setTheme(themeData);
    }
  }

  function selectChatForPrompt(
    target: WebActivePromptTarget,
    selectorData: WebCharacterSelectorResponse | null
  ) {
    const matchedChat = chats.find((chat) => matchesPromptTarget(chat, target, selectorData));
    if (!matchedChat || matchedChat.id === selectedChatId) {
      return false;
    }

    setMessages([]);
    setTheme(null);
    setHasMoreHistoryBefore(false);
    setHasMoreHistoryAfter(false);
    setAutoScrollToBottom(true);
    activateChat(matchedChat.id);
    return true;
  }

  async function sendPreparedMessage(text: string, uploadsSnapshot: WebUploadedAttachment[]) {
    if (!token || isStreaming || (!text.trim() && uploadsSnapshot.length === 0)) {
      return;
    }
    setBusy(true);
    setStreaming(true);
    setError(null);
    setInputProcessingStage('streaming');
    let targetChatId = selectedChatId;

    try {
      if (!targetChatId) {
        const chat = await createChat(token, {
          ...buildCreateChatBinding(characterSelector?.active_prompt ?? null),
          set_current: true
        });
        targetChatId = chat.id;
        skipNextConversationLoadRef.current = true;
        activateChat(chat.id);
        if (historyLoaded) {
          setChats((currentChats) => [chat, ...currentChats.filter((item) => item.id !== chat.id)]);
        }
      }

      if (!targetChatId) {
        throw new Error('无法创建会话');
      }

      const optimisticUserMessage: WebChatMessage = {
        id: `local-user-${Date.now()}`,
        sender: 'user',
        content_raw: text.trim(),
        display_content: text.trim(),
        timestamp: Date.now(),
        attachments: formatOutgoingAttachments(uploadsSnapshot)
      };

      const optimisticAssistantMessage: WebChatMessage = {
        id: `local-assistant-${Date.now()}`,
        sender: 'assistant',
        content_raw: '',
        timestamp: Date.now() + 1,
        attachments: [],
        streaming: true
      };

      setMessages((currentMessages) =>
        currentMessages.concat(optimisticUserMessage, optimisticAssistantMessage)
      );
      setAutoScrollToBottom(true);
      streamAbortRef.current = new AbortController();

      await streamMessage(
        token,
        targetChatId,
        {
          message: text,
          attachment_ids: uploadsSnapshot.map((item) => item.attachment_id),
          return_tool_status: true
        },
        {
          onEvent: (event) => {
            if (event.event === 'user_message' && event.message) {
              setMessages((currentMessages) =>
                currentMessages.map((item) =>
                  item.id === optimisticUserMessage.id ? event.message ?? item : item
                )
              );
            }

            if (event.event === 'assistant_delta') {
              setMessages((currentMessages) =>
                appendStreamingAssistantDelta(
                  currentMessages,
                  optimisticAssistantMessage.id,
                  event.delta ?? '',
                  event.message
                )
              );
            }

            if (event.event === 'assistant_done') {
              setMessages((currentMessages) =>
                finalizeStreamingAssistantMessage(
                  currentMessages,
                  optimisticAssistantMessage.id,
                  event.message
                )
              );
            }

            if (event.event === 'error' && event.error) {
              setMessages((currentMessages) =>
                finalizeStreamingAssistantMessage(currentMessages, optimisticAssistantMessage.id)
              );
              setError(event.error);
            }
          }
        },
        streamAbortRef.current.signal
      );
    } catch (sendError: unknown) {
      const domException = sendError as DOMException;
      if (domException.name !== 'AbortError') {
        handleApiFailure(sendError);
      }
    } finally {
      setBusy(false);
      setStreaming(false);
      setInputProcessingStage('idle');
      streamAbortRef.current = null;
      setAttachmentPanelOpen(false);
      if (token && targetChatId) {
        await loadConversation(token, targetChatId, 'merge-latest');
        if (historyLoaded) {
          await ensureChatsLoaded(token, true);
        }
      }
    }
  }

  function submitToken() {
    const normalizedToken = tokenDraft.trim();
    if (!normalizedToken) {
      setError('请输入 Bearer Token');
      return;
    }

    writeStoredToken(normalizedToken);
    setToken(normalizedToken);
    setError(null);
  }

  async function createConversation(options?: { group?: string | null }) {
    if (!token) {
      return;
    }
    setBusy(true);

    try {
      const chat = await createChat(token, {
        group: options?.group ?? null,
        ...buildCreateChatBinding(characterSelector?.active_prompt ?? null),
        set_current: true
      });
      if (historyLoaded) {
        await ensureChatsLoaded(token, true);
      }
      setMessages([]);
      setTheme(null);
      setHasMoreHistoryBefore(false);
      setHasMoreHistoryAfter(false);
      activateChat(chat.id);
      setHistoryOpenState(false);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    } finally {
      setBusy(false);
    }
  }

  async function renameConversation(chat: WebChatSummary, title: string) {
    if (!token) {
      return;
    }

    const nextTitle = title.trim();
    if (!nextTitle || nextTitle === chat.title) {
      return;
    }

    try {
      await updateConversation(chat, {
        title: nextTitle
      });
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function deleteConversation(chat: WebChatSummary) {
    if (!token) {
      return;
    }

    try {
      const isDeletingSelectedChat = selectedChatId === chat.id;
      const deleteReplacementTarget = resolveDeleteReplacementTarget(
        chat,
        characterSelector?.active_prompt ?? null
      );
      await deleteChat(token, chat.id);
      clearMessageDraft(chat.id);
      setChats((currentChats) => currentChats.filter((item) => item.id !== chat.id));
      const nextChats = await refreshChats(token);
      const fallbackId = nextChats[0]?.id ?? null;
      const hasSelectedChat = selectedChatId
        ? nextChats.some((item) => item.id === selectedChatId)
        : false;
      if (isDeletingSelectedChat) {
        const replacementChat =
          nextChats.find((item) => matchesDeleteReplacementTarget(item, deleteReplacementTarget)) ??
          null;

        setMessages([]);
        setTheme(null);
        setHasMoreHistoryBefore(false);
        setHasMoreHistoryAfter(false);
        setAutoScrollToBottom(true);

        if (replacementChat) {
          activateChat(replacementChat.id, false);
          return;
        }

        const createdChat = await createChat(token, {
          ...buildCreateChatBindingFromDeleteTarget(deleteReplacementTarget),
          set_current: true
        });
        const refreshedChats = await refreshChats(token);
        const nextSelectedChat =
          refreshedChats.find((item) => item.id === createdChat.id) ?? createdChat;
        activateChat(nextSelectedChat.id, false);
        return;
      }

      if (!hasSelectedChat) {
        setMessages([]);
        setTheme(null);
        setHasMoreHistoryBefore(false);
        setHasMoreHistoryAfter(false);
        setAutoScrollToBottom(true);
        activateChat(fallbackId, false);
      }
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  function selectChat(chatId: string) {
    if (chatId === selectedChatId) {
      setHistoryOpenState(false);
      return;
    }

    const targetChat = chats.find((item) => item.id === chatId) ?? null;
    setMessages([]);
    setTheme(null);
    setHasMoreHistoryBefore(false);
    setHasMoreHistoryAfter(false);
    setHistoryOpenState(false);
    setAutoScrollToBottom(true);
    activateChat(chatId);

    if (token && targetChat) {
      void syncActivePromptForChat(token, targetChat).catch((actionError: unknown) => {
        handleApiFailure(actionError);
      });
    }
  }

  async function uploadFiles(files: FileList | File[]) {
    if (!token) {
      return;
    }

    const normalizedFiles = Array.from(files);
    if (!normalizedFiles.length) {
      return;
    }

    setBusy(true);
    setInputProcessingStage('uploading');

    try {
      const uploaded: WebUploadedAttachment[] = [];
      for (const file of normalizedFiles) {
        uploaded.push(await uploadAttachment(token, file));
      }
      setPendingUploads((currentUploads) =>
        currentUploads.concat(makeUploadsFileNamesUnique(currentUploads, uploaded))
      );
      setAttachmentPanelOpen(false);
    } catch (uploadError: unknown) {
      handleApiFailure(uploadError);
    } finally {
      setBusy(false);
      setInputProcessingStage(isStreaming ? 'streaming' : 'idle');
    }
  }

  function removePendingUpload(attachmentId: string) {
    setPendingUploads((currentUploads) =>
      currentUploads.filter((item) => item.attachment_id !== attachmentId)
    );
  }

  async function sendMessage() {
    const outgoingText = messageInputRef.current.trim();
    const uploadsSnapshot = [...pendingUploads];
    if (!shouldConfirmContextLimit(outgoingText)) {
      return;
    }
    updateMessageInput('');
    setPendingUploads([]);
    await sendPreparedMessage(outgoingText, uploadsSnapshot);
  }

  function cancelCurrentMessage() {
    streamAbortRef.current?.abort();
    setStreaming(false);
    setInputProcessingStage('idle');
  }

  function setHistoryOpen(value: boolean) {
    if (value) {
      setCharacterSelectorOpenState(false);
    }
    setHistoryOpenState(value);
    if (value && token) {
      void ensureChatsLoaded(token).catch((loadError: unknown) => {
        handleApiFailure(loadError);
      });
    }
  }

  function setCharacterSelectorOpen(value: boolean) {
    if (value) {
      setHistoryOpenState(false);
    }
    setCharacterSelectorOpenState(value);
    if (value && token) {
      void refreshCharacterSelector(token).catch((loadError: unknown) => {
        handleApiFailure(loadError);
      });
    }
  }

  async function switchActivePrompt(target: WebActivePromptTarget) {
    if (!token) {
      return;
    }

    setCharacterSelectorLoading(true);
    try {
      const selectorData = await setActivePromptOnServer(token, target);
      setCharacterSelector(selectorData);
      const didSwitchChat =
        autoSwitchChatOnCharacterSelect && selectChatForPrompt(target, selectorData);
      const requests: Array<Promise<unknown>> = [refreshModelSelector(token)];
      if (!didSwitchChat && selectedChatId) {
        requests.push(
          getTheme(token, selectedChatId).then((themeData) => {
            setTheme(themeData);
            return null;
          })
        );
      }
      await Promise.all(requests);
      setCharacterSelectorOpenState(false);
      setError(null);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    } finally {
      setCharacterSelectorLoading(false);
    }
  }

  function queueDraftMessage() {
    const draftText = messageInputRef.current.trim();
    if (!draftText) {
      return;
    }

    const nextItem: PendingQueueMessageItem = {
      id: queueIdRef.current,
      text: draftText
    };

    queueIdRef.current += 1;
    setPendingQueueMessages((currentQueue) => currentQueue.concat(nextItem));
    setPendingQueueExpanded(true);
    updateMessageInput('');
  }

  function deletePendingQueueMessage(id: number) {
    setPendingQueueMessages((currentQueue) => currentQueue.filter((item) => item.id !== id));
  }

  function editPendingQueueMessage(id: number) {
    setPendingQueueMessages((currentQueue) => {
      const target = currentQueue.find((item) => item.id === id);
      if (target) {
        updateMessageInput(target.text);
      }
      return currentQueue.filter((item) => item.id !== id);
    });
  }

  async function sendPendingQueueMessage(id: number) {
    const target = pendingQueueMessages.find((item) => item.id === id);
    if (!target) {
      return;
    }

    if (!shouldConfirmContextLimit(target.text)) {
      return;
    }
    setPendingQueueMessages((currentQueue) => currentQueue.filter((item) => item.id !== id));
    await sendPreparedMessage(target.text, []);
  }

  function setHistoryDisplayMode(value: HistoryDisplayMode) {
    setHistoryDisplayModeState(value);
    writeStoredValue(HISTORY_DISPLAY_MODE_KEY, value);
  }

  function setAutoSwitchCharacterCard(value: boolean) {
    setAutoSwitchCharacterCardState(value);
    writeStoredValue(AUTO_SWITCH_CHARACTER_CARD_KEY, String(value));

    if (value && token && selectedChat) {
      void syncActivePromptForChat(token, selectedChat).catch((actionError: unknown) => {
        handleApiFailure(actionError);
      });
    }
  }

  function setAutoSwitchChatOnCharacterSelect(value: boolean) {
    setAutoSwitchChatOnCharacterSelectState(value);
    writeStoredValue(AUTO_SWITCH_CHAT_ON_CHARACTER_SELECT_KEY, String(value));

    if (value && characterSelector?.active_prompt) {
      selectChatForPrompt(characterSelector.active_prompt, characterSelector);
    }
  }

  async function updateConversation(
    chat: WebChatSummary,
    payload: {
      title?: string;
      group?: string | null;
      update_group?: boolean;
      locked?: boolean;
      update_locked?: boolean;
      character_card_name?: string | null;
      character_group_id?: string | null;
      update_binding?: boolean;
    }
  ) {
    if (!token) {
      return null;
    }

    try {
      const updated = await updateChatOnServer(token, chat.id, payload);
      const refreshedChats = await refreshChats(token);
      const nextChat = refreshedChats.find((item) => item.id === updated.id) ?? updated;

      if (selectedChatId === updated.id && payload.update_binding) {
        if (autoSwitchCharacterCard) {
          await syncActivePromptForChat(token, nextChat);
        } else {
          await refreshCharacterSelector(token);
        }
      }

      return nextChat;
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
      return null;
    }
  }

  async function reorderConversations(items: WebChatReorderItem[]) {
    if (!token || items.length === 0) {
      return;
    }

    try {
      await reorderChatsOnServer(token, items);
      await refreshChats(token);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function renameGroup(
    oldName: string,
    newName: string,
    characterCardName?: string | null
  ) {
    if (!token) {
      return;
    }

    const normalizedOldName = oldName.trim();
    const normalizedNewName = newName.trim();
    if (!normalizedOldName || !normalizedNewName || normalizedOldName === normalizedNewName) {
      return;
    }

    try {
      await renameGroupOnServer(token, {
        old_name: normalizedOldName,
        new_name: normalizedNewName,
        character_card_name: characterCardName ?? null
      });
      await refreshChats(token);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function deleteGroup(
    groupName: string,
    deleteChats: boolean,
    characterCardName?: string | null
  ) {
    if (!token) {
      return;
    }

    const normalizedGroupName = groupName.trim();
    if (!normalizedGroupName) {
      return;
    }

    try {
      await deleteGroupOnServer(token, {
        group_name: normalizedGroupName,
        delete_chats: deleteChats,
        character_card_name: characterCardName ?? null
      });
      const refreshedChats = await refreshChats(token);
      if (selectedChatId && !refreshedChats.some((chat) => chat.id === selectedChatId)) {
        const fallbackChatId = refreshedChats[0]?.id ?? null;
        setMessages([]);
        setTheme(null);
        setHasMoreHistoryBefore(false);
        setHasMoreHistoryAfter(false);
        setAutoScrollToBottom(true);
        activateChat(fallbackChatId, false);
      }
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function updateInputSettings(
    payload: Partial<{
      enable_thinking_mode: boolean;
      thinking_quality_level: number;
      enable_memory_auto_update: boolean;
      enable_auto_read: boolean;
      enable_max_context_mode: boolean;
      enable_tools: boolean;
      disable_stream_output: boolean;
      disable_user_preference_description: boolean;
      permission_level: string;
    }>
  ) {
    if (!token) {
      return;
    }

    try {
      const nextSettings = await updateInputSettingsOnServer(token, payload);
      setInputSettings(nextSettings);
      setError(null);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function selectMemoryProfile(profileId: string) {
    if (!token) {
      return;
    }

    try {
      const nextSelector = await selectMemoryProfileOnServer(token, profileId);
      setMemorySelector(nextSelector);
      setError(null);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function runManualMemoryUpdate() {
    if (!token) {
      return;
    }

    try {
      await runManualMemoryUpdateOnServer(token);
      setError(null);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function runManualConversationSummary() {
    if (!token) {
      return;
    }

    try {
      await runManualConversationSummaryOnServer(token);
      setError(null);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function loadMessageLocatorEntries(chatId: string, query = '') {
    if (!token) {
      return [];
    }

    try {
      return await getMessageLocatorEntries(token, chatId, query);
    } catch (actionError: unknown) {
      console.error('加载消息定位列表失败', actionError);
      handleApiFailure(actionError);
      throw actionError;
    }
  }

  async function revealMessageForCurrentChat(targetTimestamp: number) {
    if (!token || !selectedChatId) {
      return false;
    }

    try {
      const page = await revealMessageWindow(token, selectedChatId, targetTimestamp);
      setMessages(page.messages);
      setHasMoreHistoryBefore(page.has_more_before);
      setHasMoreHistoryAfter(page.has_more_after);
      setAutoScrollToBottom(false);
      return page.messages.some((message) => message.timestamp === targetTimestamp);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
      return false;
    }
  }

  async function toggleMessageFavorite(timestamp: number, isFavorite: boolean) {
    if (!token || !selectedChatId) {
      return;
    }

    try {
      await toggleMessageFavoriteOnServer(token, selectedChatId, timestamp, isFavorite);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  const visibleChats = useMemo(() => {
    return buildVisibleChats(chats, search);
  }, [chats, search]);

  const selectedChat = useMemo(
    () => chats.find((item) => item.id === selectedChatId) ?? null,
    [chats, selectedChatId]
  );

  const activeChatStyle: ChatStyle = theme?.chat_style === 'bubble' ? 'bubble' : 'cursor';
  const activeInputStyle: InputStyle = theme?.input.style === 'agent' ? 'agent' : 'classic';

  const activeCharacterName = characterSelector?.active_prompt.name ?? '当前角色';
  const activeCharacterAvatarUrl = characterSelector?.active_prompt.avatar_url ?? null;

  const activeStreamingCount = useMemo(
    () => chats.filter((chat) => chat.active_streaming).length,
    [chats]
  );

  const contextStats = useMemo<ContextStatsSnapshot>(() => {
    return buildContextStats(messages, messageInput, inputSettings);
  }, [inputSettings, messageInput, messages]);

  async function selectModelConfig(
    configId: string,
    modelIndex: number,
    confirmCharacterCardSwitch = false
  ) {
    if (!token) {
      return null;
    }

    setModelSelectorLoading(true);
    try {
      const response = await selectModelOnServer(token, {
        config_id: configId,
        model_index: modelIndex,
        confirm_character_card_switch: confirmCharacterCardSwitch
      });
      setModelSelector(response.selector);
      setError(null);
      return response;
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
      return null;
    } finally {
      setModelSelectorLoading(false);
    }
  }

  return {
    token,
    tokenDraft,
    boot,
    theme,
    characterSelector,
    characterSelectorOpen,
    characterSelectorLoading,
    modelSelector,
    modelSelectorLoading,
    chats,
    visibleChats,
    selectedChatId,
    selectedChat,
    messages,
    messageInput,
    search,
    pendingUploads,
    pendingQueueMessages,
    isPendingQueueExpanded,
    error,
    isConnecting,
    isBusy,
    isStreaming,
    historyOpen,
    historyLoading,
    hasMoreHistoryBefore,
    hasMoreHistoryAfter,
    isLoadingHistoryBefore,
    isLoadingHistoryAfter,
    attachmentPanelOpen,
    inputProcessingStage,
    activeChatStyle,
    activeInputStyle,
    activeCharacterName,
    activeCharacterAvatarUrl,
    showConnectionOverlay: !token,
    activeStreamingCount,
    contextStats,
    autoScrollToBottom,
    historyDisplayMode,
    autoSwitchCharacterCard,
    autoSwitchChatOnCharacterSelect,
    inputSettings,
    memorySelector,
    setTokenDraft,
    submitToken,
    createConversation,
    renameConversation,
    deleteConversation,
    selectChat,
    setMessageInput: updateMessageInput,
    setSearch,
    uploadFiles,
    removePendingUpload,
    sendMessage,
    cancelCurrentMessage,
    setHistoryOpen,
    setCharacterSelectorOpen,
    switchActivePrompt,
    selectModelConfig,
    loadOlderMessages,
    loadNewerMessages,
    showLatestMessages,
    setAttachmentPanelOpen,
    queueDraftMessage,
    setPendingQueueExpanded,
    deletePendingQueueMessage,
    editPendingQueueMessage,
    sendPendingQueueMessage,
    setAutoScrollToBottom,
    setHistoryDisplayMode,
    setAutoSwitchCharacterCard,
    setAutoSwitchChatOnCharacterSelect,
    updateConversation,
    reorderConversations,
    renameGroup,
    deleteGroup,
    updateInputSettings,
    selectMemoryProfile,
    runManualMemoryUpdate,
    runManualConversationSummary,
    loadMessageLocatorEntries,
    revealMessageForCurrentChat,
    toggleMessageFavorite
  };
}
