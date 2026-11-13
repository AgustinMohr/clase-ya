import { describe, expect, it } from 'vitest';
import { canFavorite } from './favorites';

describe('canFavorite', () => {
  it('allows visitors to start the login flow', () => {
    expect(canFavorite(undefined)).toBe(true);
  });

  it('allows students', () => {
    expect(canFavorite('STUDENT')).toBe(true);
  });

  it('hides favorites from teachers', () => {
    expect(canFavorite('TEACHER')).toBe(false);
  });

  it('hides favorites from admins', () => {
    expect(canFavorite('ADMIN')).toBe(false);
  });
});
