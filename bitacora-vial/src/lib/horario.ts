import { parseISO } from 'date-fns';

/** The project's real weekly schedule: lunes a jueves 8:00–18:00 (10 h), viernes 8:00–15:00
 * (7 h). Sábado se trabaja a trato (se paga según acuerdo, no por hora) — ver JornadaSabado en
 * lugar de un horario fijo. Domingo no tiene jornada estándar. */
export function horasNormalesEsperadas(fecha: string): number {
  const dia = parseISO(fecha).getDay(); // 0 = domingo … 6 = sábado
  if (dia >= 1 && dia <= 4) return 10; // lunes a jueves, 8:00–18:00
  if (dia === 5) return 7; // viernes, 8:00–15:00
  return 0; // sábado (a trato) y domingo
}

export function esSabado(fecha: string): boolean {
  return parseISO(fecha).getDay() === 6;
}

/** Short caption for the day's applicable schedule, e.g. next to the date in Asistencia —
 * so it's obvious at a glance which rule applies before anyone starts typing hours. */
export function descripcionJornada(fecha: string): string {
  const dia = parseISO(fecha).getDay();
  if (dia >= 1 && dia <= 4) return 'Jornada 8:00 a 18:00';
  if (dia === 5) return 'Jornada 8:00 a 15:00';
  if (dia === 6) return 'Trabajo a trato (según acuerdo)';
  return '';
}
