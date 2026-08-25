import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { capasDePartida, registrarCapa, eliminarCapa } from '../../lib/queries';
import { parseNumeroDecimal } from '../../lib/numero';
import { formatShortDate } from '../../lib/date';
import { IconChevronRight, IconPlus, IconX } from '../../components/Icon';

/** Control de compactación capa por capa para una partida de "Relleno estructural" — se
 * muestra automáticamente junto al avance normal en m³ (ver esRellenoPorCapas), como un
 * registro aparte para dejar trazabilidad ordenada de cada capa: espesor, densidad obtenida
 * y quién tomó la muestra. Compartido entre Nuevo Parte y Cubicación. */
export function RegistroCapasRelleno({ partidaId, parteId, fecha }: { partidaId: string; parteId: string; fecha: string }) {
  const capas = useLiveQuery(() => capasDePartida(partidaId), [partidaId]) ?? [];
  const [formAbierto, setFormAbierto] = useState(false);
  const [listaAbierta, setListaAbierta] = useState(false);
  const siguienteCapa = capas.length > 0 ? Math.max(...capas.map((c) => c.numeroCapa)) + 1 : 1;

  const [numeroCapa, setNumeroCapa] = useState('');
  const [espesor, setEspesor] = useState('');
  const [densidad, setDensidad] = useState('');
  const [muestreadoPor, setMuestreadoPor] = useState('');

  function abrirForm() {
    setNumeroCapa(String(siguienteCapa));
    setEspesor('');
    setDensidad('');
    setMuestreadoPor('');
    setFormAbierto(true);
  }

  async function guardar() {
    const nCapa = parseNumeroDecimal(numeroCapa);
    const nEspesor = parseNumeroDecimal(espesor);
    const nDensidad = parseNumeroDecimal(densidad);
    if (nCapa <= 0 || nEspesor <= 0 || nDensidad <= 0 || !muestreadoPor.trim()) return;
    await registrarCapa({
      parteId, partidaId, fecha,
      numeroCapa: nCapa, espesorCm: nEspesor, densidad: nDensidad, muestreadoPor: muestreadoPor.trim(),
    });
    setFormAbierto(false);
  }

  const formValido = parseNumeroDecimal(numeroCapa) > 0 && parseNumeroDecimal(espesor) > 0 && parseNumeroDecimal(densidad) > 0 && muestreadoPor.trim().length > 0;

  return (
    <div style={{ marginTop: 9, borderTop: '1px dashed var(--border)', paddingTop: 9 }}>
      <div className="flex-row gap-8" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: formAbierto || (listaAbierta && capas.length > 0) ? 8 : 0 }}>
        <button
          type="button"
          onClick={() => capas.length > 0 && setListaAbierta((v) => !v)}
          className="flex-row"
          style={{ gap: 4, alignItems: 'center', background: 'none', border: 'none', color: 'var(--text)', fontSize: 11.5, fontWeight: 700, padding: 0 }}
        >
          Registro de capas {capas.length > 0 ? `(${capas.length})` : ''}
          {capas.length > 0 && (
            <IconChevronRight size={11} color="var(--text-soft)" style={{ transform: listaAbierta ? 'rotate(90deg)' : undefined }} />
          )}
        </button>
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

      {listaAbierta && capas.length > 0 && (
        <div className="stack" style={{ gap: 6, marginBottom: formAbierto ? 10 : 0 }}>
          {capas.map((c) => (
            <div key={c.id} className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', gap: 8, background: 'var(--surface-alt)', borderRadius: 8, padding: '7px 9px' }}>
              <div style={{ fontSize: 11 }}>
                <strong>Capa {c.numeroCapa}</strong> · {c.espesorCm.toLocaleString('es-CL')} cm · densidad {c.densidad.toLocaleString('es-CL')}
                <div className="text-soft" style={{ fontSize: 10 }}>
                  Muestreó {c.muestreadoPor} · {formatShortDate(c.fecha)}
                </div>
              </div>
              <button
                onClick={() => eliminarCapa(c.id)}
                aria-label={`Quitar registro de capa ${c.numeroCapa}`}
                style={{ background: 'none', border: 'none', color: 'var(--text-soft)', padding: 0, display: 'flex', flexShrink: 0 }}
              >
                <IconX size={12} color="var(--text-soft)" />
              </button>
            </div>
          ))}
        </div>
      )}

      {formAbierto && (
        <div className="stack" style={{ gap: 8 }}>
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
                className="field-input" style={{ width: '100%', marginTop: 3 }}
              />
            </label>
          </div>
          <div className="flex-row gap-8">
            <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setFormAbierto(false)}>Cancelar</button>
            <button className="btn btn-primary" style={{ flex: 1 }} disabled={!formValido} onClick={guardar}>Guardar capa</button>
          </div>
        </div>
      )}
    </div>
  );
}
