/** Presentation helpers shared across the UI. */

export function formatPrice(value?: number | null): string {
  if (value == null) return 'Consultar';
  return `$${Number(value).toLocaleString('es-AR')}`;
}

export function formatPriceHour(value?: number | null): string {
  const price = formatPrice(value);
  return price === 'Consultar' ? price : `${price}/h`;
}

export function formatRating(avg?: number | null, count?: number | null): string {
  if (!avg || !count) return 'Sin opiniones';
  return `${Number(avg).toFixed(1)} (${count})`;
}

export function initials(name?: string | null): string {
  if (!name) return '?';
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

export function modalityLabel(modality?: string | null): string {
  if (modality === 'ONLINE') return 'Online';
  if (modality === 'IN_PERSON') return 'Presencial';
  return 'A convenir';
}

const DAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

export function dayName(dayOfWeek?: number | null): string {
  return dayOfWeek && dayOfWeek >= 1 && dayOfWeek <= 7 ? DAYS[dayOfWeek - 1] : '';
}

export function dayPartLabel(part?: string | null): string {
  if (part === 'MORNING') return 'Mañana';
  if (part === 'AFTERNOON') return 'Tarde';
  if (part === 'NIGHT') return 'Noche';
  return '';
}
