import { useEffect, useRef } from 'react';

interface TurnstileOptions {
  sitekey: string;
  callback: (token: string) => void;
  'error-callback'?: () => void;
  'expired-callback'?: () => void;
  theme?: 'auto' | 'light' | 'dark';
}

interface TurnstileApi {
  render: (container: HTMLElement, options: TurnstileOptions) => string;
  remove: (widgetId: string) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

const SITE_KEY = (import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined) || '';

/**
 * Cloudflare Turnstile widget (SEC-04). It only renders when the public site key is configured;
 * the backend still enforces the token when CAPTCHA_ENABLED=true.
 */
export default function TurnstileWidget({
  onToken,
  onError,
}: {
  onToken: (token: string) => void;
  onError?: (message: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<string | null>(null);
  const onTokenRef = useRef(onToken);
  const onErrorRef = useRef(onError);
  onTokenRef.current = onToken;
  onErrorRef.current = onError;

  useEffect(() => {
    if (!SITE_KEY || !container.current) return;
    let cancelled = false;

    const render = () => {
      if (cancelled || !window.turnstile || !container.current) return;
      widgetId.current = window.turnstile.render(container.current, {
        sitekey: SITE_KEY,
        callback: (token) => onTokenRef.current(token),
        'error-callback': () => onErrorRef.current?.('No pudimos verificar el CAPTCHA.'),
        'expired-callback': () => onTokenRef.current(''),
        theme: 'auto',
      });
    };

    if (window.turnstile) {
      render();
    } else {
      let script = document.querySelector<HTMLScriptElement>('script[data-turnstile]');
      if (!script) {
        script = document.createElement('script');
        script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
        script.async = true;
        script.defer = true;
        script.dataset.turnstile = 'true';
        document.head.appendChild(script);
      }
      script.addEventListener('load', render, { once: true });
    }

    return () => {
      cancelled = true;
      if (widgetId.current && window.turnstile) {
        window.turnstile.remove(widgetId.current);
        widgetId.current = null;
      }
    };
  }, []);

  if (!SITE_KEY) {
    return import.meta.env.DEV ? (
      <p className="text-xs text-content-muted">
        CAPTCHA no configurado: definí <code>VITE_TURNSTILE_SITE_KEY</code> para habilitarlo.
      </p>
    ) : null;
  }

  return <div ref={container} className="min-h-[65px]" />;
}
