import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, MessageCircle, Send } from 'lucide-react';
import { api, ApiError, MAX_MESSAGE_LENGTH, type ConversationSummary, type Message } from '../api';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/Button';
import { EmptyState, Skeleton } from '../components/ui/primitives';

interface Props {
  onBack: () => void;
  /** Conversation to open on arrival (the one just created by a contact request). */
  initialConversationId?: string | null;
}

interface Thread {
  other: { displayName: string; role: string };
  messages: Message[];
}

function formatWhen(value: string | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  return date.toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

/** Internal messaging view: conversation list + thread (CONTACT-001). */
export default function MessagesPage({ onBack, initialConversationId }: Props) {
  const { user } = useAuth();
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [listError, setListError] = useState('');
  const [openId, setOpenId] = useState<string | null>(null);
  const [other, setOther] = useState<Thread['other'] | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loadingThread, setLoadingThread] = useState(false);
  const [threadError, setThreadError] = useState('');
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const bodyRef = useRef<HTMLDivElement | null>(null);
  const draftRef = useRef<HTMLTextAreaElement | null>(null);
  const openedInitial = useRef(false);
  // Threads already fetched: revisiting one renders instantly instead of flashing skeletons.
  const threadsRef = useRef(new Map<string, Thread>());
  // Guards against out-of-order responses when switching conversations quickly.
  const threadSeq = useRef(0);

  /** `silent` refreshes (after opening or sending) must not put the list back into skeletons. */
  const loadConversations = useCallback(async (silent = false) => {
    if (!silent) {
      setLoadingList(true);
    }
    try {
      const page = await api.conversations(0, 50);
      setConversations(page.content);
      setListError('');
    } catch {
      if (!silent) {
        setListError('No pudimos cargar tus conversaciones.');
      }
    } finally {
      if (!silent) {
        setLoadingList(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  const openConversation = useCallback(
    async (conversationId: string) => {
      const seq = ++threadSeq.current;
      setOpenId(conversationId);
      setThreadError('');

      const cached = threadsRef.current.get(conversationId);
      if (cached) {
        // Show what we already have, then refresh in the background.
        setOther(cached.other);
        setMessages(cached.messages);
        setLoadingThread(false);
      } else {
        setOther(null);
        setMessages([]);
        setLoadingThread(true);
      }

      try {
        const [detail, firstPage] = await Promise.all([
          api.conversation(conversationId),
          api.messages(conversationId, 0, 100),
        ]);
        // A long conversation must show its most recent messages, not the oldest page.
        const lastPageIndex = Math.max(0, firstPage.totalPages - 1);
        const page = lastPageIndex === 0 ? firstPage : await api.messages(conversationId, lastPageIndex, 100);
        const thread: Thread = { other: detail.otherParticipant, messages: page.content };
        threadsRef.current.set(conversationId, thread);
        if (seq === threadSeq.current) {
          setOther(thread.other);
          setMessages(thread.messages);
        }
        // Opening the thread marks the other participant's messages as read.
        await api.markConversationRead(conversationId);
        void loadConversations(true);
      } catch (e) {
        if (seq === threadSeq.current) {
          setThreadError(
            e instanceof ApiError && e.status === 404
              ? 'Esta conversación ya no está disponible.'
              : 'No pudimos cargar la conversación.',
          );
        }
      } finally {
        if (seq === threadSeq.current) {
          setLoadingThread(false);
        }
      }
    },
    [loadConversations],
  );

  useEffect(() => {
    if (!initialConversationId || openedInitial.current) return;
    openedInitial.current = true;
    void openConversation(initialConversationId);
  }, [initialConversationId, openConversation]);

  // Scroll only the thread, never the page.
  useEffect(() => {
    const body = bodyRef.current;
    if (body) {
      body.scrollTop = body.scrollHeight;
    }
  }, [messages, loadingThread]);

  // The composer grows with the text and never shows a scrollbar early: `scrollHeight`
  // excludes the borders, and with border-box the height would end up 2px short.
  useEffect(() => {
    const field = draftRef.current;
    if (!field) return;
    field.style.height = 'auto';
    const style = window.getComputedStyle(field);
    const borders = parseFloat(style.borderTopWidth) + parseFloat(style.borderBottomWidth);
    field.style.height = `${Math.min(field.scrollHeight + borders, 200)}px`;
  }, [draft]);

  const send = useCallback(async () => {
    const content = draft.trim();
    if (!content || !openId || sending) return;
    setSending(true);
    setThreadError('');
    try {
      const created = await api.sendMessage(openId, content);
      setMessages((previous) => {
        const next = [...previous, created];
        const cached = threadsRef.current.get(openId);
        if (cached) {
          threadsRef.current.set(openId, { ...cached, messages: next });
        }
        return next;
      });
      setDraft('');
      void loadConversations(true);
    } catch {
      setThreadError('No pudimos enviar el mensaje.');
    } finally {
      setSending(false);
      // Keep the caret where the user is writing (the send button may have taken the focus).
      draftRef.current?.focus();
    }
  }, [draft, openId, sending, loadConversations]);

  function onComposerKeyDown(event: React.KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends; Shift+Enter inserts a newline (standard chat behaviour).
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void send();
    }
  }

  return (
    <div className="container-page py-8">
      <button
        type="button"
        onClick={onBack}
        className="mb-5 inline-flex items-center gap-1.5 text-sm font-semibold text-content-muted transition-colors hover:text-primary-600"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver al inicio
      </button>

      <h1 className="mb-6 font-display text-3xl">Mensajes</h1>

      <div className="grid gap-6 lg:grid-cols-[320px_1fr]">
        {/* LISTA */}
        <section aria-label="Conversaciones" className={openId ? 'hidden lg:block' : ''}>
          {loadingList ? (
            <div className="space-y-3">
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-20" />
              ))}
            </div>
          ) : listError ? (
            <EmptyState title="Algo salió mal" description={listError} action={<Button onClick={() => void loadConversations()}>Reintentar</Button>} />
          ) : conversations.length === 0 ? (
            <EmptyState
              icon={<MessageCircle className="h-6 w-6" aria-hidden="true" />}
              title="Todavía no tenés conversaciones"
              description={
                user?.role === 'TEACHER'
                  ? 'Cuando un estudiante te contacte, vas a verlo acá.'
                  : 'Contactá a un profesor desde su perfil y la conversación aparece acá.'
              }
            />
          ) : (
            <ul className="space-y-2">
              {conversations.map((conversation) => (
                <li key={conversation.id}>
                  <button
                    type="button"
                    onClick={() => void openConversation(conversation.id)}
                    className={`w-full rounded-2xl border p-4 text-left transition-colors ${
                      openId === conversation.id
                        ? 'border-primary-300 bg-primary-50/60 dark:border-primary-800 dark:bg-primary-900/20'
                        : 'border-border bg-surface hover:bg-surface-muted'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">{conversation.otherParticipant.displayName || 'Sin nombre'}</span>
                      {conversation.unreadCount > 0 && (
                        <span className="rounded-full bg-primary-600 px-2 py-0.5 text-xs font-bold text-white">
                          {conversation.unreadCount}
                        </span>
                      )}
                    </div>
                    <p className="mt-1 truncate text-sm text-content-muted">
                      {conversation.lastMessage?.content ?? 'Sin mensajes todavía'}
                    </p>
                    <p className="mt-1 text-[11px] text-content-muted">{formatWhen(conversation.updatedAt)}</p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* HILO */}
        <section aria-label="Conversación" className={openId ? 'min-w-0' : 'hidden min-w-0 lg:block'}>
          {!openId ? (
            <div className="hidden h-[70vh] items-center justify-center rounded-2xl border border-border bg-surface p-10 text-center text-sm text-content-muted lg:flex">
              Elegí una conversación para leer los mensajes.
            </div>
          ) : (
            // Fixed-height shell: switching conversations or loading never moves the page.
            <div data-testid="thread-shell" className="flex h-[70vh] flex-col rounded-2xl border border-border bg-surface">
              <header className="flex items-center justify-between gap-3 border-b border-border p-4">
                <div>
                  <p className="font-semibold">{other?.displayName || 'Conversación'}</p>
                  <p className="text-xs text-content-muted">
                    {other ? (other.role === 'TEACHER' ? 'Profesor' : 'Estudiante') : ''}
                  </p>
                </div>
                <Button variant="ghost" size="sm" className="lg:hidden" onClick={() => setOpenId(null)}>
                  Volver
                </Button>
              </header>

              <div ref={bodyRef} className="flex-1 overflow-y-auto overflow-x-hidden p-4" aria-busy={loadingThread} data-testid="thread-body">
                {loadingThread ? (
                  <div className="space-y-3">
                    {Array.from({ length: 5 }).map((_, index) => (
                      <Skeleton key={index} className="h-12" />
                    ))}
                  </div>
                ) : threadError && messages.length === 0 ? (
                  <EmptyState
                    title="No pudimos abrir la conversación"
                    description={threadError}
                    action={<Button onClick={() => void openConversation(openId)}>Reintentar</Button>}
                  />
                ) : messages.length === 0 ? (
                  <p className="py-8 text-center text-sm text-content-muted">
                    Todavía no hay mensajes. Escribí el primero.
                  </p>
                ) : (
                  <div className="space-y-3">
                    {messages.map((message) => {
                      const mine = message.senderId === user?.id;
                      return (
                        <div key={message.id} className={mine ? 'flex min-w-0 justify-end' : 'flex min-w-0 justify-start'}>
                          <div
                            data-testid="message-bubble"
                            className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm ${
                              mine ? 'bg-primary-600 text-white' : 'bg-surface-muted text-content'
                            }`}
                          >
                            <span className="whitespace-pre-wrap break-words">{message.content}</span>
                            <span className={`mt-0.5 block text-right text-[10px] ${mine ? 'text-primary-50/80' : 'text-content-muted'}`}>
                              {formatWhen(message.createdAt)}
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              <form
                className="flex items-end gap-2 border-t border-border p-3"
                onSubmit={(event) => {
                  event.preventDefault();
                  void send();
                }}
              >
                <label className="sr-only" htmlFor="message-draft">
                  Escribí un mensaje
                </label>
                <textarea
                  id="message-draft"
                  data-testid="composer"
                  ref={draftRef}
                  rows={1}
                  maxLength={MAX_MESSAGE_LENGTH}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={onComposerKeyDown}
                  placeholder="Escribí un mensaje… (Enter para enviar)"
                  className="composer max-h-[200px] w-full resize-none rounded-xl border border-border bg-surface px-3 py-2 text-sm text-content placeholder:text-content-muted/70 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-ring/40"
                />
                <span
                  data-testid="composer-counter"
                  className="shrink-0 pb-2 text-[11px] tabular-nums text-content-muted"
                  aria-hidden="true"
                >
                  {draft.length}/{MAX_MESSAGE_LENGTH}
                </span>
                <Button type="submit" loading={sending} disabled={!draft.trim()}>
                  <Send className="h-4 w-4" aria-hidden="true" />
                  <span className="hidden sm:inline">Enviar</span>
                </Button>
              </form>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
