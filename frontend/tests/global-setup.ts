import { request } from '@playwright/test';

/**
 * The backend's `/actuator/health` answers before the demo seed finishes. Tests that depend on the
 * seeded teachers/conversations then race the seeder and fail intermittently. Wait for the public
 * catalog to reach the demo size before starting the suite.
 */
export default async function globalSetup(): Promise<void> {
  const api = await request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    for (let attempt = 0; attempt < 60; attempt++) {
      const response = await api.get('/api/teachers?page=0&size=1');
      if (response.ok()) {
        const body = (await response.json()) as { totalElements?: number };
        if ((body.totalElements ?? 0) >= 45) {
          return;
        }
      }
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
    throw new Error('The demo seed did not finish within 60 seconds');
  } finally {
    await api.dispose();
  }
}
