import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

/**
 * Unit/component tests (TEST-01). The Playwright suite lives in `tests/` and owns the critical
 * end-to-end flows; this runner only sees tests colocated under `src` and needs no backend.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
  },
});
