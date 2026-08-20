import { crearPartida } from '../../lib/queries';
import type { NuevaPartidaDatos } from '../../lib/cubicacionCalculo';
import { IconX } from '../../components/Icon';
import { NuevaPartidaForm } from './NuevaPartidaForm';

/** Full-screen "Nueva tarea" overlay opened directly from Nuevo Parte's "Cubicar nueva tarea"
 * button — just the form, none of Cubicación's own chrome (título de la pantalla, selector de
 * frente, tarjeta de avance del frente, listado de tareas ya existentes) that would only repeat
 * information the foreman already saw in "Retomar pendiente". Guardar creates the partida under
 * frenteId (the frente the foreman was actually on) and hands the new id back via onGuardado. */
export function NuevaTareaModal({
  frenteId, frenteNombre, parteId, fecha, onGuardado, onCerrar,
}: {
  frenteId: string;
  frenteNombre?: string;
  parteId: string;
  fecha: string;
  onGuardado: (partidaId: string) => void;
  onCerrar: () => void;
}) {
  async function guardar(datos: NuevaPartidaDatos) {
    if (!datos.nombre.trim()) return;
    const id = await crearPartida(frenteId, parteId, fecha, datos);
    onGuardado(id);
  }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--bg)', zIndex: 100, display: 'flex', flexDirection: 'column' }}>
      <div
        className="flex-row"
        style={{ justifyContent: 'space-between', alignItems: 'center', padding: '18px 20px 14px', flexShrink: 0, borderBottom: '1px solid var(--border)', background: 'var(--surface)' }}
      >
        <div>
          <div className="disp" style={{ fontSize: 18, fontWeight: 800 }}>Nueva tarea</div>
          {frenteNombre && <div className="text-soft" style={{ fontSize: 12 }}>{frenteNombre}</div>}
        </div>
        <button onClick={onCerrar} aria-label="Cerrar" style={{ background: 'none', border: 'none', padding: 6, display: 'flex' }}>
          <IconX size={18} color="var(--text-soft)" />
        </button>
      </div>
      <div className="content" style={{ overflowY: 'auto' }}>
        <div className="card">
          <NuevaPartidaForm onGuardar={guardar} onCancelar={onCerrar} conCatalogo />
        </div>
      </div>
    </div>
  );
}
