import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

function Boom(): never {
  throw new Error('boom');
}

describe('ErrorBoundary', () => {
  it('shows a recovery screen instead of a blank page', () => {
    // React reports the caught error through console.error; keep the test output clean.
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('heading', { name: /algo salió mal/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /recargar/i })).toBeInTheDocument();
  });

  it('renders its children when nothing throws', () => {
    render(
      <ErrorBoundary>
        <p>Contenido</p>
      </ErrorBoundary>,
    );

    expect(screen.getByText('Contenido')).toBeInTheDocument();
  });
});

describe('ErrorBoundary reporting (RT-05)', () => {
  it('reports the caught render error', async () => {
    vi.resetModules();
    const reportRenderError = vi.fn();
    vi.doMock('../observability/sentry', () => ({ reportRenderError }));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const { ErrorBoundary: Boundary } = await import('./ErrorBoundary');

    render(
      <Boundary>
        <Boom />
      </Boundary>,
    );

    expect(reportRenderError).toHaveBeenCalledTimes(1);
    expect(reportRenderError.mock.calls[0][0]).toBeInstanceOf(Error);
    vi.doUnmock('../observability/sentry');
  });
});
