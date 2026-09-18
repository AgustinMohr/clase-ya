export interface Subject {
  id: string;
  name: string;
}

export interface TeacherSubjectView {
  careerSubjectId?: string;
  subjectId?: string;
  subjectName?: string;
  careerName?: string;
  universityName?: string;
}

export interface TeacherSummary {
  id: string;
  displayName?: string;
  bio?: string;
  ratingAverage?: number;
  ratingCount?: number;
  verificationStatus?: 'PENDING' | 'VERIFIED' | 'REJECTED';
  pricePerHour?: number;
  city?: string;
  photoUrl?: string;
  modalities?: string[];
  subjects?: TeacherSubjectView[];
}

export interface TeacherDetail extends TeacherSummary {
  availabilityNote?: string;
  education?: Array<{
    institution?: string;
    degree?: string;
    description?: string;
    startYear?: number;
    endYear?: number;
  }>;
}

export interface AvailabilityWindow {
  id: string;
  dayOfWeek?: number;
  startTime?: string;
  endTime?: string;
  mode?: string;
  dayPart?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FavoriteResponse {
  favoriteId: string;
  createdAt?: string;
  teacher: TeacherSummary;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

const TOKEN_KEY = 'claseya.token';
export const UNAUTHORIZED_EVENT = 'claseya:unauthorized';

/**
 * Backend base URL. Empty in dev (Vite proxies /api to localhost:8080); in
 * production set VITE_API_URL to the deployed backend origin.
 */
const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '');

export class ApiError extends Error {
  status: number;
  body?: unknown;
  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

async function request<T>(method: string, url: string, body?: unknown, auth = false): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${url}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 401 && auth) {
    clearToken();
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }
  if (!res.ok) {
    let parsed: any;
    try {
      parsed = await res.json();
    } catch {
      /* body no JSON (p. ej. CORS) */
    }
    throw new ApiError(res.status, parsed?.message ?? `Error ${res.status}`, parsed);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  subjects: () => request<Subject[]>('GET', '/api/subjects?size=50'),
  teachers: (params: { subjectId?: string; modality?: string; minRating?: number } = {}) => {
    const query = new URLSearchParams({ page: '0', size: '24' });
    if (params.subjectId) query.set('subjectId', params.subjectId);
    if (params.modality) query.set('modality', params.modality);
    if (params.minRating) query.set('minRating', String(params.minRating));
    return request<Page<TeacherSummary>>('GET', `/api/teachers?${query.toString()}`);
  },
  teacher: (id: string) => request<TeacherDetail>('GET', `/api/teachers/${id}`),
  availabilityByTeacher: (teacherId: string) =>
    request<Page<AvailabilityWindow>>('GET', `/api/availability?teacherId=${teacherId}&page=0&size=50`),

  login: (email: string, password: string) => request<LoginResponse>('POST', '/api/auth/login', { email, password }),
  loginWithGoogle: (idToken: string, role?: 'STUDENT' | 'TEACHER') =>
    request<LoginResponse>('POST', '/api/auth/google', role ? { idToken, role } : { idToken }),

  favorites: () => request<Page<FavoriteResponse>>('GET', '/api/favorites?page=0&size=50', undefined, true),
  addFavorite: (teacherId: string) => request<unknown>('POST', `/api/favorites/${teacherId}`, undefined, true),
  removeFavorite: (teacherId: string) => request<void>('DELETE', `/api/favorites/${teacherId}`, undefined, true),
};
