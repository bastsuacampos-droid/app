import { useEffect } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useSearchParams } from 'react-router-dom';
import { db, todayISO } from './db';
import { ensureTodayParteExists } from './queries';
import type { Parte } from '../types/models';

/**
 * Resolves which parte a screen (Nuevo Parte, Asistencia, Cubicación, Fotos) should read and
 * write: the one named by a `?parte=<id>` query param, set when reopening a finalized parte
 * from Historial for editing, or today's draft otherwise — the same fallback useTodayParte
 * always used. Every editing screen shares this so reopening one parte carries through to all
 * of them consistently instead of silently falling back to today's.
 */
export function useActiveParte(): Parte | undefined {
  const [searchParams] = useSearchParams();
  const parteIdParam = searchParams.get('parte');
  const fecha = todayISO();

  useEffect(() => {
    if (!parteIdParam) ensureTodayParteExists(fecha);
  }, [parteIdParam, fecha]);

  const porId = useLiveQuery(
    () => (parteIdParam ? db.partes.get(parteIdParam) : undefined),
    [parteIdParam],
  );
  const deHoy = useLiveQuery(
    () => (parteIdParam ? undefined : db.partes.where('fecha').equals(fecha).first()),
    [parteIdParam, fecha],
  );

  return parteIdParam ? porId : deHoy;
}
