import type { SessionUser } from '../api';

export function canFavorite(role: SessionUser['role'] | null | undefined): boolean {
  return role == null || role === 'STUDENT';
}
