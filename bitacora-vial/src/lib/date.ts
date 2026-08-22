import { format, parseISO } from 'date-fns';
import { es } from 'date-fns/locale';

export function formatLongDate(iso: string): string {
  return format(parseISO(iso), "EEEE d 'de' MMMM 'de' yyyy", { locale: es });
}

export function formatShortDate(iso: string): string {
  return format(parseISO(iso), 'dd MMM', { locale: es });
}

/** "dd-MM-yyyy", for a form's plain "Fecha" field — parseISO reads the date-only string as
 * local midnight, unlike `new Date(iso)` which the native Date constructor reads as UTC
 * midnight and would then display a day early in any timezone behind UTC (Chile included). */
export function formatNumericDate(iso: string): string {
  return format(parseISO(iso), 'dd-MM-yyyy');
}

export function formatMonthLabel(iso: string): string {
  const label = format(parseISO(iso), 'MMMM yyyy', { locale: es });
  return label.charAt(0).toUpperCase() + label.slice(1);
}

export function monthRangeISO(monthISO: string): { start: string; end: string } {
  // monthISO like "2026-08"
  const [y, m] = monthISO.split('-').map(Number);
  const start = `${monthISO}-01`;
  const lastDay = new Date(y, m, 0).getDate();
  const end = `${monthISO}-${String(lastDay).padStart(2, '0')}`;
  return { start, end };
}

export function currentMonthISO(): string {
  // Local getFullYear/getMonth, not toISOString() — that converts to UTC first, which on the
  // last evening of a month (after ~20:00 in Chile, UTC-4) would already report next month.
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function shiftMonthISO(monthISO: string, delta: number): string {
  const [y, m] = monthISO.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
