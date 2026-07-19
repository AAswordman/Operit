import type {
  WebActivePromptTarget,
  WebBootstrapResponse,
  WebChatReorderItem,
  WebChatMessageLocatorPreview,
  WebChatMessagesPage,
  WebChatStreamEvent,
  WebCharacterSelectorResponse,
  WebChatSummary,
  WebInputSettingsState,
  WebMemorySelectorState,
  WebModelSelectorState,
  WebSelectModelResponse,
  WebThemeSnapshot,
  WebUploadedAttachment
} from './chatTypes';

type JsonRequestBody = object;

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export interface StreamCallbacks {
  onEvent: (event: WebChatStreamEvent) => void;
}

function authHeaders(token: string, extra?: Record<string, string>) {
  return {
    Authorization: `Bearer ${token}`,
    ...extra
  };
}

async function parseJsonResponse<T>(response: Response): Promise<T> {
  const contentType = response.headers.get('content-type') ?? '';
  const text = await response.text();
  const payload = contentType.includes('application/json') && text
    ? (JSON.parse(text) as T & { error?: string })
    : ({} as T & { error?: string });

  if (!response.ok) {
    const message = typeof payload === 'object' && payload && 'error' in payload && payload.error
      ? String(payload.error)
      : `HTTP ${response.status}`;
    throw new ApiError(message, response.status);
  }

  return payload as T;
}

async function requestJson<T>(
  path: string,
  token: string,
  init?: RequestInit,
  body?: JsonRequestBody
): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: authHeaders(token, {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(init?.headers as Record<string, string> | undefined)
    }),
    body: body !== undefined ? JSON.stringify(body) : init?.body
  });
  return parseJsonResponse<T>(response);
}

function parseSseBlock(block: string): WebChatStreamEvent | null {
  const lines = block.split(/\r?\n/);
  let eventName = '';
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (!eventName || dataLines.length === 0) {
    return null;
  }

  const payload = JSON.parse(dataLines.join('\n')) as WebChatStreamEvent;
  return {
    ...payload,
    event: eventName as WebChatStreamEvent['event']
  };
}

export async function bootstrap(token: string): Promise<WebBootstrapResponse> {
  return requestJson<WebBootstrapResponse>('/api/web/bootstrap', token);
}

export async function getCharacterSelector(token: string): Promise<WebCharacterSelectorResponse> {
  return requestJson<WebCharacterSelectorResponse>('/api/web/character-selector', token);
}

export async function setActivePrompt(
  token: string,
  target: WebActivePromptTarget
): Promise<WebCharacterSelectorResponse> {
  return requestJson<WebCharacterSelectorResponse>(
    '/api/web/active-prompt',
    token,
    { method: 'POST' },
    target
  );
}

export async function getModelSelector(token: string): Promise<WebModelSelectorState> {
  return requestJson<WebModelSelectorState>('/api/web/model-selector', token);
}

export async function selectModel(
  token: string,
  payload: {
    config_id: string;
    model_index: number;
    confirm_character_card_switch?: boolean;
  }
): Promise<WebSelectModelResponse> {
  return requestJson<WebSelectModelResponse>(
    '/api/web/model-selector',
    token,
    { method: 'POST' },
    payload
  );
}

export async function listChats(token: string): Promise<WebChatSummary[]> {
  return requestJson<WebChatSummary[]>('/api/web/chats', token);
}

export async function createChat(
  token: string,
  payload?: {
    title?: string;
    group?: string | null;
    character_card_name?: string | null;
    character_group_id?: string | null;
    set_current?: boolean;
  }
): Promise<WebChatSummary> {
  return requestJson<WebChatSummary>('/api/web/chats', token, { method: 'POST' }, payload ?? {});
}

export async function renameChat(
  token: string,
  chatId: string,
  title: string
): Promise<WebChatSummary> {
  return updateChat(token, chatId, {
    title
  });
}

export async function updateChat(
  token: string,
  chatId: string,
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
): Promise<WebChatSummary> {
  return requestJson<WebChatSummary>(
    `/api/web/chats/${encodeURIComponent(chatId)}`,
    token,
    { method: 'PATCH' },
    payload
  );
}

export async function deleteChat(token: string, chatId: string): Promise<void> {
  await requestJson('/api/web/chats/' + encodeURIComponent(chatId), token, {
    method: 'DELETE'
  });
}

export async function selectChat(token: string, chatId: string): Promise<void> {
  await requestJson(`/api/web/chats/${encodeURIComponent(chatId)}/select`, token, {
    method: 'POST'
  });
}

export async function getMessages(
  token: string,
  chatId: string,
  options?: {
    limit?: number;
    beforeTimestamp?: number | null;
    afterTimestamp?: number | null;
  }
): Promise<WebChatMessagesPage> {
  const params = new URLSearchParams();
  if (typeof options?.limit === 'number' && Number.isFinite(options.limit)) {
    params.set('limit', String(Math.max(1, Math.floor(options.limit))));
  }
  if (typeof options?.beforeTimestamp === 'number' && Number.isFinite(options.beforeTimestamp)) {
    params.set('before_timestamp', String(Math.floor(options.beforeTimestamp)));
  }
  if (typeof options?.afterTimestamp === 'number' && Number.isFinite(options.afterTimestamp)) {
    params.set('after_timestamp', String(Math.floor(options.afterTimestamp)));
  }
  const query = params.toString();
  return requestJson<WebChatMessagesPage>(
    `/api/web/chats/${encodeURIComponent(chatId)}/messages${query ? `?${query}` : ''}`,
    token
  );
}

export async function getMessageLocatorEntries(
  token: string,
  chatId: string,
  query = ''
): Promise<WebChatMessageLocatorPreview[]> {
  const params = new URLSearchParams();
  const normalizedQuery = query.trim();
  if (normalizedQuery) {
    params.set('query', normalizedQuery);
  }
  const queryString = params.toString();
  return requestJson<WebChatMessageLocatorPreview[]>(
    `/api/web/chats/${encodeURIComponent(chatId)}/message-locator${queryString ? `?${queryString}` : ''}`,
    token
  );
}

export async function revealMessageWindow(
  token: string,
  chatId: string,
  timestamp: number
): Promise<WebChatMessagesPage> {
  return requestJson<WebChatMessagesPage>(
    `/api/web/chats/${encodeURIComponent(chatId)}/messages/reveal`,
    token,
    { method: 'POST' },
    { timestamp }
  );
}

export async function toggleMessageFavorite(
  token: string,
  chatId: string,
  timestamp: number,
  isFavorite: boolean
): Promise<void> {
  await requestJson(
    `/api/web/chats/${encodeURIComponent(chatId)}/messages/favorite`,
    token,
    { method: 'PATCH' },
    { timestamp, is_favorite: isFavorite }
  );
}

export async function reorderChats(
  token: string,
  items: WebChatReorderItem[]
): Promise<void> {
  await requestJson('/api/web/chats/reorder', token, { method: 'POST' }, { items });
}

export async function renameGroup(
  token: string,
  payload: {
    old_name: string;
    new_name: string;
    character_card_name?: string | null;
  }
): Promise<void> {
  await requestJson('/api/web/chat-groups/rename', token, { method: 'POST' }, payload);
}

export async function deleteGroup(
  token: string,
  payload: {
    group_name: string;
    delete_chats: boolean;
    character_card_name?: string | null;
  }
): Promise<void> {
  await requestJson('/api/web/chat-groups/delete', token, { method: 'POST' }, payload);
}

export async function getInputSettings(token: string): Promise<WebInputSettingsState> {
  return requestJson<WebInputSettingsState>('/api/web/input-settings', token);
}

export async function getMemorySelector(token: string): Promise<WebMemorySelectorState> {
  return requestJson<WebMemorySelectorState>('/api/web/memory-selector', token);
}

export async function selectMemoryProfile(
  token: string,
  profileId: string
): Promise<WebMemorySelectorState> {
  return requestJson<WebMemorySelectorState>(
    '/api/web/memory-selector',
    token,
    { method: 'POST' },
    { profile_id: profileId }
  );
}

export async function updateInputSettings(
  token: string,
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
): Promise<WebInputSettingsState> {
  return requestJson<WebInputSettingsState>(
    '/api/web/input-settings',
    token,
    { method: 'PATCH' },
    payload
  );
}

export async function runManualMemoryUpdate(token: string): Promise<void> {
  await requestJson('/api/web/actions/manual-memory-update', token, { method: 'POST' });
}

export async function runManualConversationSummary(token: string): Promise<void> {
  await requestJson('/api/web/actions/manual-conversation-summary', token, { method: 'POST' });
}

export async function getTheme(token: string, chatId: string): Promise<WebThemeSnapshot> {
  return requestJson<WebThemeSnapshot>(
    `/api/web/chats/${encodeURIComponent(chatId)}/theme`,
    token
  );
}

export async function uploadAttachment(
  token: string,
  file: File
): Promise<WebUploadedAttachment> {
  const form = new FormData();
  form.append('file', file, file.name);
  const response = await fetch('/api/web/uploads', {
    method: 'POST',
    headers: authHeaders(token),
    body: form
  });
  return parseJsonResponse<WebUploadedAttachment>(response);
}

export async function streamMessage(
  token: string,
  chatId: string,
  payload: {
    message: string;
    attachment_ids: string[];
    return_tool_status: boolean;
  },
  callbacks: StreamCallbacks,
  signal: AbortSignal
): Promise<void> {
  const response = await fetch(`/api/web/chats/${encodeURIComponent(chatId)}/messages/stream`, {
    method: 'POST',
    headers: authHeaders(token, {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json'
    }),
    body: JSON.stringify(payload),
    signal
  });

  if (!response.ok || !response.body) {
    const fallback = await parseJsonResponse<{ error?: string }>(response);
    throw new ApiError(fallback.error ?? 'Streaming request failed', response.status);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? '';

    for (const block of blocks) {
      const event = parseSseBlock(block.trim());
      if (event) {
        callbacks.onEvent(event);
      }
    }
  }

  const tailEvent = parseSseBlock(buffer.trim());
  if (tailEvent) {
    callbacks.onEvent(tailEvent);
  }
}
