import type { EstadoParte } from '../types/models';

const LABELS: Record<EstadoParte, string> = {
  en_edicion: 'En edición',
  pendiente: 'Pendiente',
  respaldado: 'Respaldado',
};

const CLASSES: Record<EstadoParte, string> = {
  en_edicion: 'badge badge-blue',
  pendiente: 'badge badge-amber',
  respaldado: 'badge badge-green',
};

export function StatusBadge({ estado }: { estado: EstadoParte }) {
  return <span className={CLASSES[estado]}>{LABELS[estado]}</span>;
}
