import { useLiveQuery } from 'dexie-react-hooks';
import { db } from './db';
import type { Settings } from '../types/models';

const FALLBACK: Settings = {
  id: 'app',
  tema: 'claro',
  unidades: 'metrico',
  recordatorioDiario: true,
  respaldoAutomatico: true,
  autoUpdate: true,
  onboardingComplete: false,
  permisoCamara: false,
  permisoNotificaciones: false,
  permisoUbicacion: false,
};

export function useSettings(): Settings {
  const settings = useLiveQuery(() => db.settings.get('app'), []);
  return settings ?? FALLBACK;
}

export async function updateSettings(patch: Partial<Settings>) {
  await db.settings.update('app', patch);
}
