import { describe, expect, it } from 'vitest';
import type { ConversationSummary, Message } from '../api';
import { mergeConversations, mergeMessages } from './messaging';

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

describe('mergeConversations', () => {
  function conversation(id: string): ConversationSummary {
    return {
      id,
      otherParticipant: { id: `user-${id}`, displayName: id, role: 'TEACHER' },
      unreadCount: 0,
      updatedAt: '2026-11-11T10:00:00Z',
    };
  }

  it('keeps the fresh page first and appends unseen older conversations', () => {
    const primary = [conversation('c3'), conversation('c2')];
    const older = [conversation('c2'), conversation('c1')];

    expect(mergeConversations(primary, older).map((item) => item.id)).toEqual(['c3', 'c2', 'c1']);
  });

  it('does not duplicate a conversation refreshed by polling', () => {
    const refreshed = [conversation('c2')];
    const loaded = [conversation('c2'), conversation('c1')];

    expect(mergeConversations(refreshed, loaded).map((item) => item.id)).toEqual(['c2', 'c1']);
  });
});
