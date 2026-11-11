import type { Message } from '../api';

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
