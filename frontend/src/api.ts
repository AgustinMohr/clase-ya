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
  verificationStatus?: 'PENDING' | 'UNDER_REVIEW' | 'VERIFIED' | 'REJECTED' | 'MORE_INFO_REQUIRED';
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
    isVerified?: boolean;
  }>;
}

export interface AvailabilityWindow {
  id: string;
  dayOfWeek?: number;
  startTime?: string;
  endTime?: string;
  mode?: string;
  dayPart?: string;
  status?: 'AVAILABLE' | 'DISABLED';
}

// --- Academic catalog (used by the student profile form) ---------------------

export interface University {
  id: string;
  name: string;
  shortName?: string | null;
}

export interface AcademicUnit {
  id: string;
  name: string;
  code?: string | null;
}

export interface Career {
  id: string;
  name: string;
  code?: string | null;
}

export interface StudentProfile {
  id: string;
  userId: string;
  universityId: string;
  universityName: string;
  careerId: string;
  careerName: string;
  currentYear: number;
  bio?: string | null;
}

export interface StudentProfileInput {
  universityId: string;
  careerId: string;
  currentYear: number;
  bio?: string;
}

// --- Teacher own listing ("Mi anuncio", TEACHER-001) --------------------------

export type VerificationStatus = 'PENDING' | 'UNDER_REVIEW' | 'VERIFIED' | 'REJECTED' | 'MORE_INFO_REQUIRED';

export interface TeacherProfile {
  id: string;
  email?: string;
  name?: string;
  bio?: string;
  address?: string;
  availabilityNote?: string;
  pricePerHour?: number;
  city?: string;
  photoUrl?: string;
  latitude?: number;
  longitude?: number;
  verificationStatus?: VerificationStatus;
  modalities?: string[];
}

export interface TeacherProfileInput {
  name?: string;
  bio?: string;
  address?: string;
  availabilityNote?: string;
  pricePerHour?: number;
  city?: string;
  photoUrl?: string;
  latitude?: number;
  longitude?: number;
}

export interface AnnouncementStatus {
  completeness: 'INCOMPLETE' | 'PUBLISHED';
  hasName: boolean;
  hasBio: boolean;
  hasSubject: boolean;
  hasModality: boolean;
  hasAvailability: boolean;
}

export interface OwnSubject {
  id: string;
  careerSubjectId: string;
  careerName?: string;
  subjectName?: string;
  year?: number;
  semester?: number;
}

export interface OwnEducation {
  id: string;
  institution: string;
  degree: string;
  description?: string;
  startYear?: number;
  endYear?: number;
  isVerified?: boolean;
}

export interface EducationInput {
  institution: string;
  degree: string;
  description?: string;
  startYear?: number;
  endYear?: number;
}

export interface AvailabilityInput {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  mode?: string;
}

export interface VerificationCredential {
  educationId: string;
  institution?: string;
  degree?: string;
  status: VerificationStatus;
  submittedAt?: string;
  documentCount: number;
  requirement?: string;
}

export interface VerificationDecisionView {
  id: string;
  educationId?: string | null;
  decision: string;
  previousStatus?: VerificationStatus | null;
  newStatus: VerificationStatus;
  method: string;
  reason?: string;
  decidedAt: string;
}

export interface TeacherVerification {
  profileStatus: VerificationStatus;
  credentials: VerificationCredential[];
  history: VerificationDecisionView[];
}

export interface CareerSubject {
  id: string;
  careerId: string;
  careerName?: string;
  subjectId: string;
  subjectName: string;
  year?: number;
  semester?: number;
}

export type DocumentType =
  | 'DIPLOMA'
  | 'ENROLLMENT_CERTIFICATE'
  | 'ANALYTICAL_CERTIFICATE'
  | 'POSTGRADUATE_CERTIFICATE'
  | 'PROFESSIONAL_LICENSE'
  | 'FOREIGN_DEGREE';

export interface VerificationDocument {
  id: string;
  educationId: string;
  type: DocumentType;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  uploadedAt: string;
}

// --- Messaging ---------------------------------------------------------------

export interface ConversationParticipant {
  id: string;
  displayName: string;
  role: string;
}

export interface LastMessage {
  id: string;
  content: string;
  createdAt: string;
  senderId: string;
}

export interface ConversationSummary {
  id: string;
  otherParticipant: ConversationParticipant;
  lastMessage?: LastMessage;
  unreadCount: number;
  updatedAt: string;
}

export interface Conversation {
  id: string;
  otherParticipant: ConversationParticipant;
  unreadCount: number;
  updatedAt: string;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  senderDisplayName: string;
  content: string;
  createdAt: string;
  readAt?: string;
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

/** Maximum length of a chat message, mirrors the backend validation (TEACHER/CONTACT). */
export const MAX_MESSAGE_LENGTH = 256;

/**
 * Bio caps for the profile textareas, mirroring the backend DTO validation
 * (student 1000, teacher 2000). Kept in sync so the UI never lets a user type
 * past what the API accepts (which would come back as a 400).
 */
export const MAX_STUDENT_BIO_LENGTH = 1000;
export const MAX_TEACHER_BIO_LENGTH = 2000;

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

async function requestForm<T>(url: string, formData: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}${url}`, { method: 'POST', headers, body: formData });

  if (res.status === 401) {
    clearToken();
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }
  if (!res.ok) {
    let parsed: unknown;
    try {
      parsed = await res.json();
    } catch {
      /* body no JSON */
    }
    const message = (parsed as { message?: string } | undefined)?.message ?? `Error ${res.status}`;
    throw new ApiError(res.status, message, parsed);
  }
  return (await res.json()) as T;
}

export const api = {
  subjects: () => request<Subject[]>('GET', '/api/subjects?size=50'),
  teachers: (params: {
    subjectId?: string;
    modality?: string;
    minRating?: number;
    minPrice?: number;
    maxPrice?: number;
    onlyVerified?: boolean;
    page?: number;
  } = {}) => {
    const query = new URLSearchParams({ page: String(params.page ?? 0), size: '24' });
    if (params.subjectId) query.set('subjectId', params.subjectId);
    if (params.modality) query.set('modality', params.modality);
    if (params.minRating) query.set('minRating', String(params.minRating));
    if (params.minPrice != null) query.set('minPrice', String(params.minPrice));
    if (params.maxPrice != null) query.set('maxPrice', String(params.maxPrice));
    if (params.onlyVerified) query.set('onlyVerified', 'true');
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

  // --- Student profile -------------------------------------------------------
  universities: () => request<University[]>('GET', '/api/universities'),
  academicUnits: (universityId: string) =>
    request<AcademicUnit[]>('GET', `/api/universities/${universityId}/academic-units`),
  careers: (academicUnitId: string) =>
    request<Career[]>('GET', `/api/academic-units/${academicUnitId}/careers`),
  studentProfile: () => request<StudentProfile>('GET', '/api/students/me', undefined, true),
  createStudentProfile: (body: StudentProfileInput) =>
    request<StudentProfile>('POST', '/api/students/profile', body, true),
  updateStudentProfile: (body: StudentProfileInput) =>
    request<StudentProfile>('PUT', '/api/students/me', body, true),

  // --- Messaging (CONTACT-001) ----------------------------------------------
  /** Starts (or reuses) the conversation with a teacher, optionally with the first message. */
  startConversation: (teacherId: string, message?: string) =>
    request<Conversation>('POST', '/api/conversations', message ? { teacherId, message } : { teacherId }, true),
  conversations: (page = 0, size = 20) =>
    request<Page<ConversationSummary>>('GET', `/api/conversations?page=${page}&size=${size}`, undefined, true),
  conversation: (conversationId: string) =>
    request<Conversation>('GET', `/api/conversations/${conversationId}`, undefined, true),
  messages: (conversationId: string, page = 0, size = 100) =>
    request<Page<Message>>(
      'GET',
      `/api/conversations/${conversationId}/messages?page=${page}&size=${size}`,
      undefined,
      true,
    ),
  sendMessage: (conversationId: string, content: string) =>
    request<Message>('POST', `/api/conversations/${conversationId}/messages`, { content }, true),
  markConversationRead: (conversationId: string) =>
    request<void>('PATCH', `/api/conversations/${conversationId}/read`, undefined, true),

  // --- Teacher own listing ("Mi anuncio", TEACHER-001) ------------------------
  announcement: () => request<AnnouncementStatus>('GET', '/api/teachers/me/announcement', undefined, true),
  myProfile: () => request<TeacherProfile>('GET', '/api/teachers/me', undefined, true),
  createTeacherProfile: (body: TeacherProfileInput) =>
    request<TeacherProfile>('POST', '/api/teachers/profile', body, true),
  updateTeacherProfile: (body: TeacherProfileInput) =>
    request<TeacherProfile>('PUT', '/api/teachers/me', body, true),
  myVerification: () => request<TeacherVerification>('GET', '/api/teachers/me/verification', undefined, true),
  mySubjects: () => request<OwnSubject[]>('GET', '/api/teachers/me/subjects', undefined, true),
  addSubject: (careerSubjectId: string) =>
    request<OwnSubject>('POST', '/api/teachers/me/subjects', { careerSubjectId }, true),
  removeSubject: (careerSubjectId: string) =>
    request<void>('DELETE', `/api/teachers/me/subjects/${careerSubjectId}`, undefined, true),
  myModalities: () =>
    request<{ id: string; modality: string }[]>('GET', '/api/teachers/me/modalities', undefined, true),
  addModality: (modality: string) =>
    request<unknown>('POST', '/api/teachers/me/modalities', { modality }, true),
  removeModality: (modality: string) =>
    request<void>('DELETE', `/api/teachers/me/modalities/${modality}`, undefined, true),
  myEducation: () => request<OwnEducation[]>('GET', '/api/teachers/me/education', undefined, true),
  addEducation: (body: EducationInput) =>
    request<OwnEducation>('POST', '/api/teachers/me/education', body, true),
  removeEducation: (educationId: string) =>
    request<void>('DELETE', `/api/teachers/me/education/${educationId}`, undefined, true),
  myAvailability: () =>
    request<Page<AvailabilityWindow>>('GET', '/api/availability/me?page=0&size=50', undefined, true),
  addAvailability: (body: AvailabilityInput) =>
    request<AvailabilityWindow>('POST', '/api/availability', body, true),
  disableAvailability: (windowId: string) =>
    request<void>('POST', `/api/availability/${windowId}/disable`, undefined, true),
  enableAvailability: (windowId: string) =>
    request<void>('POST', `/api/availability/${windowId}/enable`, undefined, true),
  careerSubjects: (careerId: string) =>
    request<CareerSubject[]>('GET', `/api/careers/${careerId}/subjects`),

  // --- Verification (TEACHER-001, slice B) -----------------------------------
  myDocuments: (educationId: string) =>
    request<VerificationDocument[]>('GET', `/api/teachers/me/education/${educationId}/documents`, undefined, true),
  uploadDocument: (educationId: string, type: DocumentType, file: File) => {
    const form = new FormData();
    form.append('file', file);
    form.append('type', type);
    return requestForm<VerificationDocument>(`/api/teachers/me/education/${educationId}/documents`, form);
  },
  deleteDocument: (documentId: string) =>
    request<void>('DELETE', `/api/teachers/me/documents/${documentId}`, undefined, true),
  submitCredential: (educationId: string) =>
    request<VerificationCredential>('POST', `/api/teachers/me/education/${educationId}/submit`, undefined, true),
  submitAllCredentials: () =>
    request<VerificationCredential[]>('POST', '/api/teachers/me/verification/submit', undefined, true),
};
