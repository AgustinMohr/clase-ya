import type { ConversationSummary, Message } from '../api';

/**
 * Merges a freshly polled page with the messages already rendered for a thread.
 * Deduplicates by id and keeps the chronological order so a poll can only add
 * messages, never drop the older history the user loaded.
 */
export function mergeMessages(current: Message[], incoming: Message[]): Message[] {
  const byId = new Map(current.map((message) => [message.id, message]));
  for (const message of incoming) {
    byId.set(message.id, message);
  }
  return [...byId.values()].sort(
    (left, right) =>
      left.createdAt.localeCompare(right.createdAt) || left.id.localeCompare(right.id),
  );
}

/**
 * Merges conversation pages without duplicates. The first array keeps its order (fresh page 0),
 * and unseen older conversations are appended.
 */
export function mergeConversations(
  primary: ConversationSummary[],
  secondary: ConversationSummary[],
): ConversationSummary[] {
  const seen = new Set(primary.map((conversation) => conversation.id));
  return [...primary, ...secondary.filter((conversation) => !seen.has(conversation.id))];
}
