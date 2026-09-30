import { useEffect, useState } from 'react';
import { Field, Input } from './Field';

/** Bounds of the price slider. Must stay in sync with the backend cap (SEARCH-001, D4). */
export const PRICE_MIN = 0;
export const PRICE_MAX = 30000;
const PRICE_STEP = 250;

export interface PriceValue {
  min: number | null;
  max: number | null;
}

interface Props {
  value: PriceValue;
  /** Every change (drag or typing) only updates the draft: nothing is applied here. */
  onChange: (next: PriceValue) => void;
}

function toDigits(raw: string): string {
  return raw.replace(/\D/g, '');
}

/** Digits only, clamped to the global bounds. Empty means "no bound". */
function toNumber(digits: string): number | null {
  if (digits === '') return null;
  const value = Number(digits);
  if (Number.isNaN(value)) return null;
  return Math.min(Math.max(value, PRICE_MIN), PRICE_MAX);
}

function percent(value: number): string {
  return `${((value - PRICE_MIN) / (PRICE_MAX - PRICE_MIN)) * 100}%`;
}

/**
 * Dual-handle price range built from two overlaid native range inputs: no UI
 * dependency added, and keyboard/screen-reader behaviour comes for free.
 * The upper end means "sin tope" and is reported as `max: null`.
 *
 * Typing and dragging are deliberately different: dragging clamps one handle
 * against the other (they can never cross), but a typed value is never rewritten
 * by the opposite bound — otherwise the first keystroke in "Hasta" would be
 * replaced by the current "Desde". Applying filters is the caller's job: this
 * control never triggers a request.
 */
export default function PriceRange({ value, onChange }: Props) {
  // Raw text lives here so what the user typed is never overwritten mid-typing.
  const [lowText, setLowText] = useState(value.min == null ? '' : String(value.min));
  const [highText, setHighText] = useState(value.max == null ? '' : String(value.max));

  // Slider drags (or "Limpiar filtros") change the value from outside: mirror it.
  useEffect(() => {
    setLowText(value.min == null ? '' : String(value.min));
  }, [value.min]);
  useEffect(() => {
    setHighText(value.max == null ? '' : String(value.max));
  }, [value.max]);

  // The ordered pair drives the bar so it can never render inverted.
  const low = Math.min(value.min ?? PRICE_MIN, value.max ?? PRICE_MAX);
  const high = Math.max(value.min ?? PRICE_MIN, value.max ?? PRICE_MAX);

  function typeLow(raw: string) {
    const digits = toDigits(raw);
    setLowText(digits);
    onChange({ min: toNumber(digits), max: value.max });
  }

  function typeHigh(raw: string) {
    const digits = toDigits(raw);
    setHighText(digits);
    onChange({ min: value.min, max: toNumber(digits) });
  }

  function dragLow(next: number) {
    const clamped = Math.min(Math.max(next, PRICE_MIN), high);
    onChange({ min: clamped <= PRICE_MIN ? null : clamped, max: value.max });
  }

  function dragHigh(next: number) {
    const clamped = Math.max(Math.min(next, PRICE_MAX), low);
    onChange({ min: value.min, max: clamped >= PRICE_MAX ? null : clamped });
  }

  return (
    <div className="space-y-3">
      <div className="price-range relative h-6">
        <span aria-hidden="true" className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-surface-muted" />
        <span
          aria-hidden="true"
          className="absolute top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-primary-600"
          style={{ left: percent(low), width: `calc(${percent(high)} - ${percent(low)})` }}
        />
        <input
          type="range"
          min={PRICE_MIN}
          max={PRICE_MAX}
          step={PRICE_STEP}
          value={low}
          onChange={(e) => dragLow(Number(e.target.value))}
          aria-label="Precio mínimo"
          aria-valuetext={low <= PRICE_MIN ? 'Sin mínimo' : `$${low}`}
        />
        <input
          type="range"
          min={PRICE_MIN}
          max={PRICE_MAX}
          step={PRICE_STEP}
          value={high}
          onChange={(e) => dragHigh(Number(e.target.value))}
          aria-label="Precio máximo"
          aria-valuetext={high >= PRICE_MAX ? 'Sin máximo' : `$${high}`}
        />
      </div>

      <div className="flex items-end gap-2">
        <Field label="Desde" hint="ARS por hora">
          {(props) => (
            <Input
              {...props}
              inputMode="numeric"
              value={lowText}
              placeholder={String(PRICE_MIN)}
              onChange={(e) => typeLow(e.target.value)}
            />
          )}
        </Field>
        <Field label="Hasta" hint="vacío = sin tope">
          {(props) => (
            <Input
              {...props}
              inputMode="numeric"
              value={highText}
              placeholder="Sin tope"
              onChange={(e) => typeHigh(e.target.value)}
            />
          )}
        </Field>
      </div>
    </div>
  );
}
