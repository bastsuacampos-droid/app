import { useEffect } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, todayISO } from './db';
import { ensureTodayParteExists } from './queries';
import type { Parte } from '../types/models';

/**
 * Reactive read of today's Parte. Creating it (if it doesn't exist yet) happens as an
 * imperative side effect, kept separate from the useLiveQuery read below — Dexie forbids
 * writes inside a liveQuery querier, and this keeps every screen in sync with later edits.
 */
export function useTodayParte(): Parte | undefined {
  const fecha = todayISO();

  useEffect(() => {
    ensureTodayParteExists(fecha);
  }, [fecha]);

  return useLiveQuery(() => db.partes.where('fecha').equals(fecha).first(), [fecha]);
}
