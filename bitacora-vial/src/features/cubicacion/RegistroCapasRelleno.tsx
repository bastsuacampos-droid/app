import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { capasDePartida, registrarCapa, actualizarCapa, eliminarCapa } from '../../lib/queries';
import { parseNumeroDecimal } from '../../lib/numero';
import { formatShortDate } from '../../lib/date';
import { IconCheck, IconPencil, IconPlus, IconX } from '../../components/Icon';
import type { RegistroCapaRelleno } from '../../types/models';

/** Control de compactación capa por capa para una partida de "Relleno estructural" — se
 * muestra automáticamente junto al avance normal en m³ (ver esRellenoPorCapas), como un
 * registro aparte para dejar trazabilidad ordenada de cada capa: espesor, densidad obtenida
 * y quién tomó la muestra. Compartido entre Nuevo Parte y Cubicación. El listado ya guardado
 * se muestra siempre (sin necesidad de desplegarlo) para poder revisarlo de un vistazo. */
export function RegistroCapasRelleno({ partidaId, parteId, fecha }: { partidaId: string; parteId: string; fecha: string }) {
  const capas = useLiveQuery(() => capasDePartida(partidaId), [partidaId]) ?? [];
  const [formAbierto, setFormAbierto] = useState(false);
  // Set cuando el formulario está corrigiendo una capa ya guardada (en vez de agregando una
  // nueva) — guardar() se comporta distinto en cada caso: crea vs. actualiza, y editar cierra
  // el formulario al terminar en vez de dejarlo abierto para la siguiente.
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const siguienteCapa = capas.length > 0 ? Math.max(...capas.map((c) => c.numeroCapa)) + 1 : 1;

  const [numeroCapa, setNumeroCapa] = useState('');
  const [espesor, setEspesor] = useState('');
  const [densidad, setDensidad] = useState('');
  const [muestreadoPor, setMuestreadoPor] = useState('');
  const espesorRef = useRef<HTMLInputElement>(null);

  // Confirmación visual de "sí quedó guardada" tras cada capa — se limpia sola a los 2.5s. En
  // terreno, sin esto, no queda claro si el toque en "Guardar capa" realmente surtió efecto.
  const [confirmacion, setConfirmacion] = useState<number | null>(null);
  useEffect(() => {
    if (confirmacion === null) return;
    const t = setTimeout(() => setConfirmacion(null), 2500);
    return () => clearTimeout(t);
  }, [confirmacion]);

  function abrirForm() {
    setEditandoId(null);
    setNumeroCapa(String(siguienteCapa));
    setEspesor('');
    setDensidad('');
    setMuestreadoPor('');
    setFormAbierto(true);
  }

  function editarCapa(c: RegistroCapaRelleno) {
    setEditandoId(c.id);
    setNumeroCapa(String(c.numeroCapa));
    setEspesor(String(c.espesorCm));
    setDensidad(String(c.densidad));
    setMuestreadoPor(c.muestreadoPor);
    setFormAbierto(true);
  }

  function cerrarForm() {
    setFormAbierto(false);
    setEditandoId(null);
  }

  // Al agregar, guardar deja el formulario abierto (en vez de cerrarlo) porque casi siempre se
  // registran varias capas seguidas en la misma visita — solo limpia espesor/densidad y avanza
  // el N° de capa, para poder cargarlas una tras otra sin tener que volver a tocar "Registrar
  // capa" cada vez. Al editar en cambio se corrige una sola capa a la vez, así que cierra el
  // formulario al terminar.
  async function guardar() {
    const nCapa = parseNumeroDecimal(numeroCapa);
    const nEspesor = parseNumeroDecimal(espesor);
    const nDensidad = parseNumeroDecimal(densidad);
    if (nCapa <= 0 || nEspesor <= 0 || nDensidad <= 0 || !muestreadoPor.trim()) return;
    const datos = { numeroCapa: nCapa, espesorCm: nEspesor, densidad: nDensidad, muestreadoPor: muestreadoPor.trim() };
    if (editandoId) {
      await actualizarCapa(editandoId, datos);
      setConfirmacion(nCapa);
      cerrarForm();
      return;
    }
    await registrarCapa({ parteId, partidaId, fecha, ...datos });
    setNumeroCapa(String(nCapa + 1));
    setEspesor('');
    setDensidad('');
    setConfirmacion(nCapa);
    espesorRef.current?.focus();
  }

  const formValido = parseNumeroDecimal(numeroCapa) > 0 && parseNumeroDecimal(espesor) > 0 && parseNumeroDecimal(densidad) > 0 && muestreadoPor.trim().length > 0;

  return (
    <div style={{ marginTop: 9, borderTop: '1px dashed var(--border)', paddingTop: 9 }}>
      <div className="flex-row gap-8" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 11.5, fontWeight: 700 }}>
          Registro de capas {capas.length > 0 ? `(${capas.length})` : ''}
        </span>
        {!formAbierto && (
          <button
            type="button"
            onClick={abrirForm}
            className="flex-row"
            style={{ gap: 3, alignItems: 'center', background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11, fontWeight: 700, padding: 0 }}
          >
            <IconPlus size={11} color="var(--accent)" /> Registrar capa
          </button>
        )}
      </div>

      {confirmacion !== null && (
        <div className="flex-row gap-6" style={{ alignItems: 'center', background: 'var(--green-soft)', color: 'var(--green)', borderRadius: 8, padding: '6px 9px', marginBottom: 8, fontSize: 11, fontWeight: 700 }}>
          <IconCheck size={13} color="var(--green)" /> Capa {confirmacion} guardada
        </div>
      )}

      {capas.length > 0 ? (
        <div className="stack" style={{ gap: 6, marginBottom: formAbierto ? 10 : 0 }}>
          {capas.map((c) => (
            <div
              key={c.id}
              className="flex-row"
              style={{
                justifyContent: 'space-between', alignItems: 'center', gap: 8, borderRadius: 8, padding: '7px 9px',
                background: c.numeroCapa === confirmacion ? 'var(--green-soft)' : 'var(--surface-alt)',
              }}
            >
              <div style={{ fontSize: 11 }}>
                <strong>Capa {c.numeroCapa}</strong> · {c.espesorCm.toLocaleString('es-CL')} cm · densidad {c.densidad.toLocaleString('es-CL')}
                <div className="text-soft" style={{ fontSize: 10 }}>
                  Muestreó {c.muestreadoPor} · {formatShortDate(c.fecha)}
                </div>
              </div>
              <div className="flex-row gap-8" style={{ flexShrink: 0 }}>
                <button
                  onClick={() => editarCapa(c)}
                  aria-label={`Editar registro de capa ${c.numeroCapa}`}
                  style={{ background: 'none', border: 'none', color: 'var(--text-soft)', padding: 0, display: 'flex' }}
                >
                  <IconPencil size={12} color="var(--text-soft)" />
                </button>
                <button
                  onClick={() => eliminarCapa(c.id)}
                  aria-label={`Quitar registro de capa ${c.numeroCapa}`}
                  style={{ background: 'none', border: 'none', color: 'var(--text-soft)', padding: 0, display: 'flex' }}
                >
                  <IconX size={12} color="var(--text-soft)" />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        !formAbierto && (
          <div className="text-soft" style={{ fontSize: 10.5 }}>Aún no hay capas registradas.</div>
        )
      )}

      {formAbierto && (
        <div className="stack" style={{ gap: 8 }}>
          {editandoId && (
            <div className="text-soft" style={{ fontSize: 10.5, fontWeight: 700 }}>Editando capa {numeroCapa}</div>
          )}
          <div className="flex-row gap-8">
            <label className="text-soft" style={{ fontSize: 10.5, flex: 1 }}>
              N° de capa
              <input
                type="text" inputMode="numeric" autoFocus
                value={numeroCapa} onChange={(e) => setNumeroCapa(e.target.value)}
                className="field-input" style={{ width: '100%', marginTop: 3 }}
              />
            </label>
            <label className="text-soft" style={{ fontSize: 10.5, flex: 1 }}>
              Espesor (cm)
              <input
                ref={espesorRef}
                type="text" inputMode="decimal" placeholder="Ej: 20"
                value={espesor} onChange={(e) => setEspesor(e.target.value)}
                className="field-input" style={{ width: '100%', marginTop: 3 }}
              />
            </label>
          </div>
          <div className="flex-row gap-8">
            <label className="text-soft" style={{ fontSize: 10.5, flex: 1 }}>
              Densidad (g/cm³ o % Proctor)
              <input
                type="text" inputMode="decimal" placeholder="Ej: 95"
                value={densidad} onChange={(e) => setDensidad(e.target.value)}
                className="field-input" style={{ width: '100%', marginTop: 3 }}
              />
            </label>
            <label className="text-soft" style={{ fontSize: 10.5, flex: 1 }}>
              Muestreado por
              <input
                type="text" placeholder="Nombre"
                value={muestreadoPor} onChange={(e) => setMuestreadoPor(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && formValido) { e.preventDefault(); guardar(); } }}
                className="field-input" style={{ width: '100%', marginTop: 3 }}
              />
            </label>
          </div>
          <div className="flex-row gap-8">
            <button className="btn btn-outline" style={{ flex: 1 }} onClick={cerrarForm}>{editandoId ? 'Cancelar' : 'Cerrar'}</button>
            <button className="btn btn-primary" style={{ flex: 1 }} disabled={!formValido} onClick={guardar}>
              {editandoId ? 'Guardar cambios' : 'Guardar capa'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
