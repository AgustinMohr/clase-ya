import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useTeacherListing } from './useTeacherListing';

const deferred = vi.hoisted(() => {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve: () => resolve() };
});

vi.mock('../api', () => ({
  api: {
    myProfile: vi.fn(() => Promise.resolve(null)),
    announcement: vi.fn(() => Promise.resolve(null)),
    myVerification: vi.fn(() => Promise.resolve(null)),
    mySubjects: vi.fn(() => Promise.resolve([])),
    myModalities: vi.fn(() => Promise.resolve([])),
    myEducation: vi.fn(() => Promise.resolve([])),
    myAvailability: vi.fn(() => Promise.resolve(null)),
    addModality: vi.fn(() => deferred.promise),
    removeModality: vi.fn(() => Promise.resolve()),
  },
  ApiError: class ApiError extends Error {
    status = 0;
  },
}));

vi.mock('../components/ui/Toast', () => ({
  useToast: () => ({ notify: vi.fn() }),
}));

describe('useTeacherListing', () => {
  it('updates the modality immediately and tracks the pending request', async () => {
    const { result } = renderHook(() => useTeacherListing());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      void result.current.toggleModality('ONLINE');
    });

    expect(result.current.modalities).toContain('ONLINE');
    expect(result.current.pendingModalities.has('ONLINE')).toBe(true);

    await act(async () => {
      deferred.resolve();
      await deferred.promise;
    });

    await waitFor(() => expect(result.current.pendingModalities.has('ONLINE')).toBe(false));
    expect(result.current.modalities).toContain('ONLINE');
  });
});
