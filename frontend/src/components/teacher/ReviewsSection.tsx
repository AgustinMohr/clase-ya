import { useCallback, useEffect, useState } from 'react';
import { Loader2, MessageSquare, Star } from 'lucide-react';
import { api, ApiError, type MyReview, type Review } from '../../api';
import { useAuth } from '../../auth/AuthContext';
import { Card, Skeleton } from '../ui/primitives';
import { Button } from '../ui/Button';
import { Pagination } from '../ui/Pagination';
import { Textarea } from '../ui/Field';
import ReviewModal from '../ReviewModal';

interface Props {
  teacherId: string;
  teacherName?: string;
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString('es-AR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

function Stars({ rating }: { rating: number }) {
  return (
    <span className="inline-flex items-center gap-0.5" aria-label={`${rating} de 5 estrellas`}>
      {Array.from({ length: 5 }).map((_, index) => (
        <Star
          key={index}
          className={
            index < rating
              ? 'h-3.5 w-3.5 fill-accent-400 text-accent-400'
              : 'h-3.5 w-3.5 text-content-muted/40'
          }
          aria-hidden="true"
        />
      ))}
    </span>
  );
}

/** Public reviews plus the author/teacher actions allowed by OPINION-001. */
export default function ReviewsSection({ teacherId, teacherName }: Props) {
  const { user } = useAuth();
  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [myReview, setMyReview] = useState<MyReview | null>(null);
  const [myReviewChecked, setMyReviewChecked] = useState(false);
  const [isOwner, setIsOwner] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [responses, setResponses] = useState<Record<string, string>>({});
  const [savingResponse, setSavingResponse] = useState<string | null>(null);
  const [responseError, setResponseError] = useState('');

  const loadReviews = useCallback(
    async (nextPage: number) => {
      setLoading(true);
      setError('');
      try {
        const result = await api.teacherReviews(teacherId, nextPage);
        setReviews(result.content);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setTotal(result.totalElements);
      } catch {
        setError('No pudimos cargar las opiniones.');
        setReviews([]);
        setTotal(0);
        setTotalPages(0);
      } finally {
        setLoading(false);
      }
    },
    [teacherId],
  );

  useEffect(() => {
    void loadReviews(0);
  }, [loadReviews]);

  useEffect(() => {
    let active = true;
    setMyReview(null);
    setMyReviewChecked(false);
    setIsOwner(false);

    if (!user) {
      setMyReviewChecked(true);
      return;
    }
    if (user.role === 'STUDENT') {
      api
        .myReview(teacherId)
        .then((review) => active && setMyReview(review))
        .catch((caught) => {
          if (active && !(caught instanceof ApiError && caught.status === 404)) {
            setResponseError('No pudimos comprobar tu opinión.');
          }
        })
        .finally(() => active && setMyReviewChecked(true));
      return;
    }
    if (user.role === 'TEACHER') {
      api
        .myProfile()
        .then((profile) => active && setIsOwner(profile.id === teacherId))
        .catch(() => undefined);
    }
    setMyReviewChecked(true);
    return () => {
      active = false;
    };
  }, [teacherId, user]);

  function handleSaved(review: Review) {
    setModalOpen(false);
    setMyReview((current) =>
      current
        ? { ...current, ...review }
        : { ...review, status: 'PUBLISHED' as const },
    );
    setMyReviewChecked(true);
    void loadReviews(0);
  }

  async function respond(review: Review) {
    const value = (responses[review.id] ?? '').trim();
    if (!value) return;
    setSavingResponse(review.id);
    setResponseError('');
    try {
      const saved = await api.respondToReview(review.id, value);
      setReviews((current) =>
        current.map((item) =>
          item.id === review.id
            ? { ...item, teacherResponse: saved.teacherResponse, teacherRespondedAt: saved.teacherRespondedAt }
            : item,
        ),
      );
      setResponses((current) => ({ ...current, [review.id]: '' }));
    } catch (caught) {
      setResponseError(
        caught instanceof ApiError && caught.status === 409
          ? 'No podés responder una opinión oculta.'
          : 'No pudimos guardar la respuesta.',
      );
    } finally {
      setSavingResponse(null);
    }
  }

  const canWrite =
    user?.role === 'STUDENT' && myReviewChecked && myReview?.status !== 'HIDDEN';

  return (
    <Card className="p-6">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 font-display text-xl">
            <MessageSquare className="h-5 w-5 text-primary-600" aria-hidden="true" /> Opiniones
          </h2>
          <p className="mt-1 text-sm text-content-muted">
            {total === 0 ? 'Todavía no hay opiniones.' : `${total} opinion${total === 1 ? '' : 'es'} de estudiantes.`}
          </p>
        </div>
        {canWrite && (
          <Button
            variant={myReview ? 'outline' : 'primary'}
            size="sm"
            onClick={() => setModalOpen(true)}
          >
            {myReview ? 'Editar mi opinión' : 'Dejar opinión'}
          </Button>
        )}
      </div>

      {myReview?.status === 'HIDDEN' && (
        <p role="status" className="mb-4 rounded-xl border border-border bg-surface-muted px-3 py-2 text-sm text-content-muted">
          Tu opinión está oculta por moderación. Podés editarla cuando sea restaurada.
        </p>
      )}

      {loading ? (
        <div className="space-y-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      ) : error ? (
        <div className="rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm text-error-700 dark:bg-error-700/20 dark:text-error-500">
          {error}{' '}
          <button type="button" className="font-semibold underline" onClick={() => void loadReviews(page)}>
            Reintentar
          </button>
        </div>
      ) : reviews.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border px-4 py-6 text-center text-sm text-content-muted">
          Las opiniones aparecen cuando un estudiante contacta al profesor y recibe una respuesta.
        </p>
      ) : (
        <ul className="space-y-3">
          {reviews.map((review) => (
            <li key={review.id} className="rounded-xl border border-border bg-surface-muted p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold">{review.authorDisplayName}</span>
                  <Stars rating={review.rating} />
                </div>
                <span className="text-xs text-content-muted">{formatDate(review.createdAt)}</span>
              </div>
              {review.comment && <p className="mt-2 text-sm text-content-muted">{review.comment}</p>}

              {review.teacherResponse ? (
                <div className="mt-3 rounded-lg border border-border bg-surface px-3 py-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-content-muted">
                    Respuesta del profesor
                  </p>
                  <p className="mt-1 text-sm text-content-muted">{review.teacherResponse}</p>
                </div>
              ) : isOwner ? (
                <div className="mt-3 space-y-2">
                  <Textarea
                    rows={2}
                    maxLength={1000}
                    value={responses[review.id] ?? ''}
                    onChange={(event) =>
                      setResponses((current) => ({ ...current, [review.id]: event.target.value }))
                    }
                    placeholder="Respondé públicamente a esta opinión"
                    aria-label="Respuesta pública"
                  />
                  <Button
                    size="sm"
                    onClick={() => void respond(review)}
                    disabled={savingResponse === review.id || !(responses[review.id] ?? '').trim()}
                  >
                    {savingResponse === review.id ? (
                      <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                    ) : (
                      'Responder'
                    )}
                  </Button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {responseError && (
        <p role="alert" className="mt-3 rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm text-error-700 dark:bg-error-700/20 dark:text-error-500">
          {responseError}
        </p>
      )}

      <Pagination page={page} totalPages={totalPages} loading={loading} onChange={(next) => void loadReviews(next)} label="Paginación de opiniones" />

      <ReviewModal
        open={modalOpen}
        teacherId={teacherId}
        teacherName={teacherName}
        initial={myReview}
        onClose={() => setModalOpen(false)}
        onSaved={handleSaved}
      />
    </Card>
  );
}
