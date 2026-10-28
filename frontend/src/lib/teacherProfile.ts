import type { TeacherProfile, TeacherProfileInput } from '../api';

/** Empty teacher profile form (a teacher without a profile yet). */
export const EMPTY_TEACHER_PROFILE: TeacherProfileInput = {
  name: '',
  bio: '',
  address: '',
  availabilityNote: '',
  pricePerHour: undefined,
  city: '',
  photoUrl: '',
  latitude: undefined,
  longitude: undefined,
};

/**
 * Maps the API profile (where every optional field can be null) to the editable form. Coordinates
 * are preserved even though this screen does not edit them.
 */
export function toProfileInput(profile: TeacherProfile | null): TeacherProfileInput {
  if (!profile) {
    return { ...EMPTY_TEACHER_PROFILE };
  }
  return {
    name: profile.name ?? '',
    bio: profile.bio ?? '',
    address: profile.address ?? '',
    availabilityNote: profile.availabilityNote ?? '',
    pricePerHour: profile.pricePerHour,
    city: profile.city ?? '',
    photoUrl: profile.photoUrl ?? '',
    latitude: profile.latitude,
    longitude: profile.longitude,
  };
}
