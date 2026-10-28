import { describe, expect, it } from 'vitest';
import type { TeacherProfile } from '../api';
import { EMPTY_TEACHER_PROFILE, toProfileInput } from './teacherProfile';

describe('toProfileInput', () => {
  it('returns a fresh empty form without a profile', () => {
    expect(toProfileInput(null)).toEqual(EMPTY_TEACHER_PROFILE);
    expect(toProfileInput(null)).not.toBe(EMPTY_TEACHER_PROFILE);
  });

  it('maps every editable field and keeps the coordinates', () => {
    const profile: TeacherProfile = {
      id: 'p1',
      name: 'Ana',
      bio: 'Bio',
      address: 'Calle 1',
      availabilityNote: 'Tarde',
      pricePerHour: 1500,
      city: 'Santa Fe',
      photoUrl: 'https://example.com/ana.jpg',
      latitude: -31.6,
      longitude: -60.7,
    };

    expect(toProfileInput(profile)).toEqual({
      name: 'Ana',
      bio: 'Bio',
      address: 'Calle 1',
      availabilityNote: 'Tarde',
      pricePerHour: 1500,
      city: 'Santa Fe',
      photoUrl: 'https://example.com/ana.jpg',
      latitude: -31.6,
      longitude: -60.7,
    });
  });

  it('normalizes missing strings to empty values', () => {
    expect(toProfileInput({ id: 'p2' })).toMatchObject({
      name: '',
      bio: '',
      address: '',
      availabilityNote: '',
      city: '',
      photoUrl: '',
    });
  });
});
