import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MessagesPage from './MessagesPage';
import { ToastProvider } from '../components/ui/Toast';

const mocks = vi.hoisted(() => ({
  user: { id: 'student-1', name: 'Ana', role: 'STUDENT' as const },
  conversations: vi.fn(),
  blockedUsers: vi.fn(),
  thread: vi.fn(),
  markRead: vi.fn(),
  sendMessage: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      conversations: mocks.conversations,
      blockedUsers: mocks.blockedUsers,
      conversationThread: mocks.thread,
      markConversationRead: mocks.markRead,
      sendMessage: mocks.sendMessage,
    },
  };
});

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: mocks.user }),
}));

vi.mock('../hooks/usePageMeta', () => ({
  usePageMeta: () => undefined,
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ conversationId: 'conversation-1' }),
    useNavigate: () => vi.fn(),
  };
});

describe('MessagesPage', () => {
  beforeEach(() => {
    mocks.conversations.mockReset();
    mocks.blockedUsers.mockReset();
    mocks.thread.mockReset();
    mocks.markRead.mockReset();
    mocks.sendMessage.mockReset();
    mocks.markRead.mockResolvedValue(undefined);

    mocks.conversations.mockResolvedValue({
      content: [
        {
          id: 'conversation-1',
          otherParticipant: { id: 'teacher-1', displayName: 'Bruno', role: 'TEACHER' },
          unreadCount: 1,
          updatedAt: '2026-11-12T12:00:00Z',
        },
      ],
      page: 0,
      size: 50,
      totalPages: 1,
      totalElements: 1,
    });
    mocks.blockedUsers.mockResolvedValue([]);
    mocks.thread.mockResolvedValue({
      id: 'conversation-1',
      otherParticipant: { id: 'teacher-1', displayName: 'Bruno', role: 'TEACHER' },
      unreadCount: 1,
      updatedAt: '2026-11-12T12:00:00Z',
      messages: [
        {
          id: 'message-1',
          conversationId: 'conversation-1',
          senderId: 'teacher-1',
          senderDisplayName: 'Bruno',
          content: 'Hola, ¿cómo estás?',
          createdAt: '2026-11-12T12:00:00Z',
        },
      ],
      hasMore: false,
    });
  });

  it('keeps the loaded thread visible when marking it read fails', async () => {
    mocks.markRead.mockRejectedValue(new Error('network down'));

    render(
      <ToastProvider>
        <MessagesPage />
      </ToastProvider>,
    );

    expect(await screen.findByText('Hola, ¿cómo estás?')).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar la conversación.')).not.toBeInTheDocument();
  });

  it('locks the composer when the other participant blocked the thread', async () => {
    const { ApiError } = await import('../api');
    mocks.sendMessage.mockRejectedValue(new ApiError(403, 'Forbidden'));

    render(
      <ToastProvider>
        <MessagesPage />
      </ToastProvider>,
    );

    fireEvent.change(await screen.findByTestId('composer'), {
      target: { value: 'Hola' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(
      (await screen.findAllByText('No podés enviar mensajes en esta conversación.')).length,
    ).toBeGreaterThan(0);
    expect(screen.queryByTestId('composer')).not.toBeInTheDocument();
  });
});
