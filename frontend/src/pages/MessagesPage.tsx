import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ArrowLeft, Ban, Flag, MessageCircle, Send } from 'lucide-react';
import { useNavigate, useParams } from 'react-router';
import {
  api,
  ApiError,
  MAX_MESSAGE_LENGTH,
  type ConversationParticipant,
  type ConversationSummary,
  type Message,
} from '../api';
import { useAuth } from '../auth/AuthContext';
import { usePageMeta } from '../hooks/usePageMeta';
import { Button } from '../components/ui/Button';
import { EmptyState, Skeleton } from '../components/ui/primitives';
import Modal from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import ReportModal from '../components/ReportModal';
import { mergeConversations, mergeMessages } from '../lib/messaging';
import { ROUTES } from '../routes';

interface Thread {
  other: ConversationParticipant;
  messages: Message[];
  hasMore: boolean;
  nextBefore?: string;
}

/** AUDIT-1.7: a visible tab notices replies without a manual refresh. */
const POLL_INTERVAL_MS = 20_000;
const NEAR_BOTTOM_PX = 80;
const CONVERSATION_PAGE_SIZE = 50;

function formatWhen(value: string | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  return date.toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

/** Internal messaging view: conversation list + thread (CONTACT-001). */
export default function MessagesPage() {
  const { user } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const { conversationId: routeConversationId } = useParams();
  usePageMeta({
    title: 'Mensajes | ClaseYa',
    description: 'Tus conversaciones con profesores y estudiantes en ClaseYa.',
  });

  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [conversationPage, setConversationPage] = useState(0);
  const [conversationTotalPages, setConversationTotalPages] = useState(0);
  const [listError, setListError] = useState('');
  const [openId, setOpenId] = useState<string | null>(null);
  const [other, setOther] = useState<Thread['other'] | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loadingThread, setLoadingThread] = useState(false);
  const [threadError, setThreadError] = useState('');
  const [hasMore, setHasMore] = useState(false);
  const [nextBefore, setNextBefore] = useState<string | undefined>(undefined);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [blockedIds, setBlockedIds] = useState<Set<string>>(new Set());
  const [blockedByOther, setBlockedByOther] = useState(false);
  const [blocking, setBlocking] = useState(false);
  const [confirmBlockOpen, setConfirmBlockOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const bodyRef = useRef<HTMLDivElement | null>(null);
  const draftRef = useRef<HTMLTextAreaElement | null>(null);
  // The scroll container keeps its reading position when older messages are prepended.
  const scrollAnchorRef = useRef<{ height: number; top: number } | null>(null);
  const scrollToBottomRef = useRef(true);
  // Threads already fetched: revisiting one renders instantly instead of flashing skeletons.
  const threadsRef = useRef(new Map<string, Thread>());
  // Guards against out-of-order responses when switching conversations quickly.
  const threadSeq = useRef(0);
  // One poll at a time: a slow request must not stack behind the interval.
  const pollingRef = useRef(false);

  /** `silent` refreshes (after opening or sending) must not put the list back into skeletons. */
  const loadConversations = useCallback(async (silent = false) => {
    if (!silent) {
      setLoadingList(true);
    }
    try {
      const page = await api.conversations(0, CONVERSATION_PAGE_SIZE);
      // Polling refreshes page 0; keep older pages the user already loaded.
      setConversations((previous) =>
        silent ? mergeConversations(page.content, previous) : page.content,
      );
      if (!silent) {
        setConversationPage(0);
      }
      setConversationTotalPages(page.totalPages);
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

  async function loadMoreConversations() {
    if (loadingMore || conversationPage + 1 >= conversationTotalPages) return;
    setLoadingMore(true);
    try {
      const page = await api.conversations(conversationPage + 1, CONVERSATION_PAGE_SIZE);
      setConversations((previous) => mergeConversations(previous, page.content));
      setConversationPage((previous) => previous + 1);
      setConversationTotalPages(page.totalPages);
    } catch {
      notify('No pudimos cargar más conversaciones.', 'error');
    } finally {
      setLoadingMore(false);
    }
  }

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  useEffect(() => {
    if (!user) {
      setBlockedIds(new Set());
      return;
    }
    let active = true;
    api
      .blockedUsers()
      .then((users) => active && setBlockedIds(new Set(users.map((blocked) => blocked.userId))))
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [user]);

  const openConversation = useCallback(
    async (conversationId: string) => {
      const seq = ++threadSeq.current;
      setOpenId(conversationId);
      setThreadError('');
      setBlockedByOther(false);

      const cached = threadsRef.current.get(conversationId);
      if (cached) {
        // Show what we already have, then refresh in the background.
        setOther(cached.other);
        setMessages(cached.messages);
        setHasMore(cached.hasMore);
        setNextBefore(cached.nextBefore);
        setLoadingThread(false);
      } else {
        setOther(null);
        setMessages([]);
        setHasMore(false);
        setNextBefore(undefined);
        setLoadingThread(true);
      }
      scrollToBottomRef.current = true;

      let loaded = false;
      try {
        const thread = await api.conversationThread(conversationId);
        loaded = true;
        const next: Thread = {
          other: thread.otherParticipant,
          messages: thread.messages,
          hasMore: thread.hasMore,
          nextBefore: thread.nextBefore,
        };
        threadsRef.current.set(conversationId, next);
        if (seq === threadSeq.current) {
          setOther(next.other);
          setMessages(next.messages);
          setHasMore(next.hasMore);
          setNextBefore(next.nextBefore);
          scrollToBottomRef.current = true;
        }
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

      // Marking read is secondary: a failure here must not turn a loaded thread into an error.
      if (loaded && seq === threadSeq.current) {
        void api
          .markConversationRead(conversationId)
          .then(() => loadConversations(true))
          .catch(() => undefined);
      }
    },
    [loadConversations],
  );

  // The conversation id lives in the URL: direct entry, browser history and link clicks all
  // converge here. `/mensajes` (no id) returns to the list on desktop and mobile.
  useEffect(() => {
    if (!routeConversationId) {
      setOpenId(null);
      return;
    }
    void openConversation(routeConversationId);
  }, [routeConversationId, openConversation]);

  // AUDIT-1.7: poll the visible tab so replies and unread badges show up without a refresh.
  // Hidden tabs skip the request; the thread is merged, never replaced, to keep loaded history.
  useEffect(() => {
    if (!user) return;

    const poll = async () => {
      if (document.visibilityState === 'hidden' || pollingRef.current) return;
      pollingRef.current = true;
      const seq = threadSeq.current;
      try {
        if (openId) {
          const page = await api.conversationThread(openId);
          if (seq !== threadSeq.current) return;
          const cached = threadsRef.current.get(openId);
          const current = cached?.messages ?? [];
          const merged = mergeMessages(current, page.messages);
          if (merged.length !== current.length) {
            const body = bodyRef.current;
            const nearBottom =
              !body || body.scrollHeight - body.scrollTop - body.clientHeight < NEAR_BOTTOM_PX;
            const next: Thread = {
              other: page.otherParticipant,
              messages: merged,
              hasMore: cached?.hasMore ?? page.hasMore,
              nextBefore: cached?.nextBefore ?? page.nextBefore,
            };
            threadsRef.current.set(openId, next);
            setOther(next.other);
            setMessages(merged);
            if (nearBottom) {
              scrollToBottomRef.current = true;
              await api.markConversationRead(openId);
            }
          }
        }
        await loadConversations(true);
      } catch {
        // Best effort: keep the current view and retry on the next tick.
      } finally {
        pollingRef.current = false;
      }
    };

    const timer = window.setInterval(() => void poll(), POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [openId, user, loadConversations]);

  // Older messages are inserted above: keep the reading position instead of jumping to the end.
  const loadOlder = useCallback(async () => {
    if (!openId || !nextBefore || loadingOlder) return;
    const seq = threadSeq.current;
    const body = bodyRef.current;
    scrollAnchorRef.current = body
      ? { height: body.scrollHeight, top: body.scrollTop }
      : null;
    setLoadingOlder(true);
    setThreadError('');
    try {
      const page = await api.conversationThread(openId, nextBefore, 50);
      if (seq !== threadSeq.current) {
        scrollAnchorRef.current = null;
        return;
      }
      const cached = threadsRef.current.get(openId);
      const current = cached?.messages ?? messages;
      const seen = new Set(current.map((message) => message.id));
      const merged = [...page.messages.filter((message) => !seen.has(message.id)), ...current];
      const next: Thread = {
        other: page.otherParticipant,
        messages: merged,
        hasMore: page.hasMore,
        nextBefore: page.nextBefore,
      };
      threadsRef.current.set(openId, next);
      setOther(next.other);
      setMessages(merged);
      setHasMore(next.hasMore);
      setNextBefore(next.nextBefore);
    } catch {
      if (seq === threadSeq.current) {
        scrollAnchorRef.current = null;
        setThreadError('No pudimos cargar los mensajes anteriores.');
      }
    } finally {
      setLoadingOlder(false);
    }
  }, [openId, nextBefore, loadingOlder, messages]);

  // Scroll only the thread, never the page.
  useLayoutEffect(() => {
    const body = bodyRef.current;
    if (!body) return;
    const anchor = scrollAnchorRef.current;
    if (anchor) {
      body.scrollTop = anchor.top + (body.scrollHeight - anchor.height);
      scrollAnchorRef.current = null;
      return;
    }
    if (scrollToBottomRef.current) {
      body.scrollTop = body.scrollHeight;
      scrollToBottomRef.current = false;
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
      scrollToBottomRef.current = true;
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
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 403) {
        setBlockedByOther(true);
        setThreadError('No podés enviar mensajes en esta conversación.');
      } else {
        setThreadError('No pudimos enviar el mensaje.');
      }
    } finally {
      setSending(false);
      // Keep the caret where the user is writing (the send button may have taken the focus).
      draftRef.current?.focus();
    }
  }, [draft, openId, sending, loadConversations]);

  async function toggleBlock() {
    if (!other || blocking) return;
    setBlocking(true);
    try {
      if (blockedIds.has(other.id)) {
        await api.unblockUser(other.id);
        setBlockedIds((previous) => {
          const next = new Set(previous);
          next.delete(other.id);
          return next;
        });
        notify('Usuario desbloqueado');
      } else {
        await api.blockUser(other.id);
        setBlockedIds((previous) => new Set(previous).add(other.id));
        setConfirmBlockOpen(false);
        notify('Usuario bloqueado');
      }
    } catch {
      notify('No pudimos actualizar el bloqueo.', 'error');
    } finally {
      setBlocking(false);
    }
  }

  const blockedByMe = other !== null && blockedIds.has(other.id);

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
        onClick={() => navigate(ROUTES.home)}
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
            <>
              <ul className="space-y-2">
                {conversations.map((conversation) => (
                  <li key={conversation.id}>
                    <button
                      type="button"
                      onClick={() => navigate(ROUTES.conversation(conversation.id))}
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
              {conversationPage + 1 < conversationTotalPages && (
                <div className="mt-3 flex justify-center">
                  <Button
                    variant="outline"
                    size="sm"
                    loading={loadingMore}
                    onClick={() => void loadMoreConversations()}
                  >
                    Cargar más conversaciones
                  </Button>
                </div>
              )}
            </>
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
              <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border p-4">
                <div>
                  <p className="font-semibold">{other?.displayName || 'Conversación'}</p>
                  <p className="text-xs text-content-muted">
                    {other ? (other.role === 'TEACHER' ? 'Profesor' : 'Estudiante') : ''}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={!other}
                    onClick={() => setReportOpen(true)}
                  >
                    <Flag className="h-4 w-4" aria-hidden="true" />
                    Reportar
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    loading={blocking}
                    disabled={!other}
                    onClick={() => (blockedByMe ? void toggleBlock() : setConfirmBlockOpen(true))}
                  >
                    <Ban className="h-4 w-4" aria-hidden="true" />
                    {blockedByMe ? 'Desbloquear' : 'Bloquear'}
                  </Button>
                  <Button variant="ghost" size="sm" className="lg:hidden" onClick={() => navigate(ROUTES.messages)}>
                    Volver
                  </Button>
                </div>
              </header>

              <div ref={bodyRef} className="flex-1 overflow-y-auto overflow-x-hidden p-4" aria-busy={loadingThread} data-testid="thread-body">
                {!loadingThread && hasMore && nextBefore && (
                  <div className="mb-3 flex justify-center">
                    <Button
                      variant="ghost"
                      size="sm"
                      loading={loadingOlder}
                      onClick={() => void loadOlder()}
                    >
                      Cargar mensajes anteriores
                    </Button>
                  </div>
                )}
                {!loadingThread && threadError && messages.length > 0 && (
                  <p role="alert" className="mb-3 text-center text-sm text-error-600">
                    {threadError}
                  </p>
                )}
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

              {blockedByMe ? (
                <div className="space-y-2 border-t border-border p-3 text-sm text-content-muted">
                  <p>Bloqueaste a este usuario. No pueden enviarse mensajes.</p>
                  <Button variant="outline" size="sm" loading={blocking} onClick={() => void toggleBlock()}>
                    Desbloquear
                  </Button>
                </div>
              ) : blockedByOther ? (
                <div className="space-y-2 border-t border-border p-3 text-sm text-content-muted">
                  <p>No podés enviar mensajes en esta conversación.</p>
                </div>
              ) : (
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
              )}
            </div>
          )}
        </section>
      </div>

      <ReportModal
        open={reportOpen && other !== null}
        reportedUserId={other?.id ?? ''}
        reportedName={other?.displayName}
        onClose={() => setReportOpen(false)}
        onReported={() => {
          setReportOpen(false);
          notify('Reporte enviado. Administración lo va a revisar.');
        }}
      />

      <Modal
        open={confirmBlockOpen}
        onClose={() => setConfirmBlockOpen(false)}
        title="Bloquear usuario"
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-sm text-content-muted">
            Vas a bloquear a <strong className="text-content">{other?.displayName || 'este usuario'}</strong>.
            No van a poder iniciar conversaciones ni enviarse mensajes. El historial existente se
            conserva.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button block loading={blocking} onClick={() => void toggleBlock()}>
              Bloquear
            </Button>
            <Button type="button" variant="outline" block onClick={() => setConfirmBlockOpen(false)}>
              Cancelar
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
