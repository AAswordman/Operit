import type { WebChatMessage, WebChatMessagesPage } from '../util/chatTypes';

export interface PendingHistorySync {
  temporaryIds: Set<string>;
  sending: boolean;
  // The last persisted message visible before the first unsynchronized send. Browser clocks
  // cannot identify the corresponding server record, so pagination uses this server timestamp.
  anchorTimestamp: number | null;
}

export async function loadHistoryThroughAnchor(
  loadPage: (beforeTimestamp?: number) => Promise<WebChatMessagesPage>,
  pending: PendingHistorySync | undefined
): Promise<WebChatMessagesPage> {
  let page = await loadPage();
  if (!pending) return page;

  let messages = page.messages;
  while (page.has_more_before) {
    const first = messages[0];
    if (!first) throw new Error('历史分页为空，无法完成消息同步');
    if (pending.anchorTimestamp !== null && first.timestamp <= pending.anchorTimestamp) break;

    const older = await loadPage(first.timestamp);
    const oldest = older.messages[0];
    if (!oldest || oldest.timestamp >= first.timestamp) {
      throw new Error('历史分页未向前推进，无法完成消息同步');
    }
    messages = older.messages.concat(messages);
    page = { ...page, has_more_before: older.has_more_before };
  }
  return { ...page, messages, next_before_timestamp: messages[0]?.timestamp ?? null };
}

export function mergeLatestConversationPage(
  existingMessages: WebChatMessage[],
  latestMessages: WebChatMessage[],
  completedMessageIds: ReadonlySet<string>
) {
  const retainedMessages = existingMessages.filter((message) => !completedMessageIds.has(message.id));
  if (!latestMessages.length) return retainedMessages;

  const latestIds = new Set(latestMessages.map((message) => message.id));
  const oldestLatestTimestamp = latestMessages[0].timestamp;
  return retainedMessages.filter(
    (message) => !latestIds.has(message.id) && message.timestamp < oldestLatestTimestamp
  ).concat(latestMessages);
}
