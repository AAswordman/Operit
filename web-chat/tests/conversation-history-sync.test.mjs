import assert from 'node:assert/strict';
import test from 'node:test';
import { loadHistoryThroughAnchor, mergeLatestConversationPage } from '../src/ui/features/chat/viewmodel/ConversationHistorySync.ts';

const message = (id, timestamp, sender = 'assistant') => ({
  id, timestamp, sender, content_raw: 'same text', attachments: []
});
const page = (messages, hasMore = false) => ({
  messages, has_more_before: hasMore, has_more_after: false
});
const loader = (history) => async (before) => {
  const eligible = history.filter((item) => before === undefined || item.timestamp < before);
  return page(eligible.slice(-24), eligible.length > 24);
};

test('one long Waifu response keeps the user message and the entire interval after older history', async () => {
  const old = message('old', 1);
  const user = message('user', 2, 'user');
  const history = [old, user, ...Array.from({ length: 50 }, (_, i) => message(`reply-${i}`, i + 3))];
  const draft = message('local-user', 1.5, 'user');
  const pending = { temporaryIds: new Set([draft.id]), anchorTimestamp: old.timestamp, sending: false };
  const loaded = await loadHistoryThroughAnchor(loader(history), pending);
  assert.deepEqual(mergeLatestConversationPage([old, draft], loaded.messages, pending.temporaryIds), history);
});

test('a failed sync preserves the pending identities for the next successful sync', async () => {
  const draft = message('local-user', 1, 'user');
  const persisted = message('user', 2, 'user');
  const pending = { temporaryIds: new Set([draft.id]), anchorTimestamp: null, sending: false };
  await assert.rejects(loadHistoryThroughAnchor(async () => { throw new Error('network'); }, pending));
  assert.equal(pending.temporaryIds.has(draft.id), true);
  const loaded = await loadHistoryThroughAnchor(loader([persisted]), pending);
  assert.deepEqual(mergeLatestConversationPage([draft], loaded.messages, pending.temporaryIds), [persisted]);
});

test('identical prompts with distinct persisted IDs remain distinct', () => {
  const first = message('first', 1, 'user');
  const second = message('second', 2, 'user');
  assert.deepEqual(mergeLatestConversationPage([], [first, second], new Set()), [first, second]);
});

test('a stalled pagination cursor fails the handoff instead of deleting the draft', async () => {
  const pending = { temporaryIds: new Set(['draft']), anchorTimestamp: 0, sending: false };
  await assert.rejects(loadHistoryThroughAnchor(async () => page([message('latest', 10)], true), pending));
  assert.equal(pending.temporaryIds.has('draft'), true);
});
