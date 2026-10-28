import { describe, expect, it } from 'vitest';
import {
  dayName,
  dayPartLabel,
  formatPrice,
  formatPriceHour,
  formatRating,
  initials,
  modalityLabel,
} from './format';

describe('formatPrice', () => {
  it('formats an amount for Argentina', () => {
    expect(formatPrice(1500)).toBe('$1.500');
  });

  it('falls back to Consultar without a price', () => {
    expect(formatPrice(null)).toBe('Consultar');
    expect(formatPrice(undefined)).toBe('Consultar');
  });
});

describe('formatPriceHour', () => {
  it('appends the hourly suffix only when there is a price', () => {
    expect(formatPriceHour(1500)).toBe('$1.500/h');
    expect(formatPriceHour(null)).toBe('Consultar');
  });
});

describe('formatRating', () => {
  it('formats average and count', () => {
    expect(formatRating(4.5, 10)).toBe('4.5 (10)');
  });

  it('returns the empty label without ratings', () => {
    expect(formatRating(null, null)).toBe('Sin opiniones');
    expect(formatRating(4.5, 0)).toBe('Sin opiniones');
  });
});

describe('initials', () => {
  it('takes the first letter of up to two words', () => {
    expect(initials('Ana Gómez')).toBe('AG');
    expect(initials('Ana')).toBe('A');
  });

  it('has a placeholder without a name', () => {
    expect(initials(null)).toBe('?');
    expect(initials(undefined)).toBe('?');
  });
});

describe('labels', () => {
  it('maps modalities', () => {
    expect(modalityLabel('ONLINE')).toBe('Online');
    expect(modalityLabel('IN_PERSON')).toBe('Presencial');
    expect(modalityLabel('OTHER')).toBe('A convenir');
  });

  it('maps days and day parts', () => {
    expect(dayName(1)).toBe('Lunes');
    expect(dayName(7)).toBe('Domingo');
    expect(dayName(8)).toBe('');
    expect(dayPartLabel('MORNING')).toBe('Mañana');
    expect(dayPartLabel('AFTERNOON')).toBe('Tarde');
    expect(dayPartLabel('NIGHT')).toBe('Noche');
    expect(dayPartLabel('OTHER')).toBe('');
  });
});
