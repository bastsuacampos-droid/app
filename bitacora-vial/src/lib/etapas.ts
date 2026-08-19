import type { EtapaFoto } from '../types/models';

export const ETAPAS: { id: EtapaFoto; label: string; color: string }[] = [
  { id: 'antes', label: 'Antes', color: 'var(--blue)' },
  { id: 'durante', label: 'Durante', color: 'var(--amber)' },
  { id: 'despues', label: 'Después', color: 'var(--green)' },
  { id: 'inconveniente', label: 'Inconveniente', color: 'var(--red)' },
];

export const ETAPA_POR_ID = new Map(ETAPAS.map((e) => [e.id, e]));
