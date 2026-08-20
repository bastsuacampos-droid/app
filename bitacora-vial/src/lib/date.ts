import { format, parseISO } from 'date-fns';
import { es } from 'date-fns/locale';

export function formatLongDate(iso: string): string {
  return format(parseISO(iso), "EEEE d 'de' MMMM 'de' yyyy", { locale: es });
}

export function formatShortDate(iso: string): string {
  return format(parseISO(iso), 'dd MMM', { locale: es });
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
  return new Date().toISOString().slice(0, 7);
}

export function shiftMonthISO(monthISO: string, delta: number): string {
  const [y, m] = monthISO.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
