import { describe, expect, it } from 'vitest';
import type { Message } from '../api';
import { mergeMessages } from './messaging';

function message(id: string, createdAt: string, content = id): Message {
  return {
    id,
    conversationId: 'conversation-1',
    senderId: 'sender-1',
    senderDisplayName: 'Ana',
    content,
    createdAt,
  };
}

describe('mergeMessages', () => {
  it('appends new messages in chronological order', () => {
    const current = [message('m2', '2026-11-11T10:02:00Z')];
    const incoming = [message('m3', '2026-11-11T10:03:00Z'), message('m1', '2026-11-11T10:01:00Z')];

    expect(mergeMessages(current, incoming).map((item) => item.id)).toEqual(['m1', 'm2', 'm3']);
  });

  it('deduplicates messages already rendered', () => {
    const current = [message('m1', '2026-11-11T10:01:00Z'), message('m2', '2026-11-11T10:02:00Z')];

    const merged = mergeMessages(current, [message('m2', '2026-11-11T10:02:00Z')]);

    expect(merged.map((item) => item.id)).toEqual(['m1', 'm2']);
  });

  it('keeps older history loaded by the user', () => {
    const current = [message('m1', '2026-11-11T10:01:00Z')];
    const latestPage = [message('m9', '2026-11-11T10:09:00Z')];

    expect(mergeMessages(current, latestPage).map((item) => item.id)).toEqual(['m1', 'm9']);
  });
});
