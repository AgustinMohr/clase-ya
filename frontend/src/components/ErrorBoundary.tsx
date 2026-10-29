import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Last-resort boundary: a render error must not leave a blank page (A11Y-01).
 * The stack is not shown to the user; React already reports it in development.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(_error: Error, _info: ErrorInfo): void {
    // Intentionally no request call here: the boundary must not depend on the API.
  }

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <main className="container-page py-16">
        <div className="mx-auto max-w-lg rounded-2xl border border-border bg-surface p-8 text-center shadow-card">
          <h1 className="text-2xl font-bold">Algo salió mal</h1>
          <p className="mt-2 text-content-muted">
            La página falló inesperadamente. Recargá para volver a intentar.
          </p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-6 rounded-xl bg-primary-600 px-5 py-2.5 font-semibold text-white transition-colors hover:bg-primary-700"
          >
            Recargar
          </button>
        </div>
      </main>
    );
  }
}
