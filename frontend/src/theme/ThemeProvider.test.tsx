import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider, useTheme } from './ThemeProvider';

function Toggle() {
  const { theme, toggle } = useTheme();
  return <button onClick={toggle}>{theme}</button>;
}

function mockMatchMedia({ dark = false, reduced = false }: { dark?: boolean; reduced?: boolean } = {}) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('prefers-color-scheme: dark')
      ? dark
      : query.includes('prefers-reduced-motion: reduce')
        ? reduced
        : false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

function mockViewTransition() {
  const start = vi.fn((callback: () => void) => {
    callback();
    return { finished: Promise.resolve() };
  });
  (document as unknown as { startViewTransition: typeof start }).startViewTransition = start;
  return start;
}

describe('ThemeProvider', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    mockMatchMedia();
  });

  afterEach(() => {
    delete (document as unknown as { startViewTransition?: unknown }).startViewTransition;
  });

  it('uses the View Transition API when available', () => {
    const start = mockViewTransition();
    render(
      <ThemeProvider>
        <Toggle />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'light' }));

    expect(start).toHaveBeenCalledTimes(1);
    expect(document.documentElement).toHaveClass('dark');
  });

  it('falls back without animation when reduced motion is requested', () => {
    mockMatchMedia({ reduced: true });
    const start = mockViewTransition();
    render(
      <ThemeProvider>
        <Toggle />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'light' }));

    expect(start).not.toHaveBeenCalled();
    expect(document.documentElement).toHaveClass('dark');
  });
});
