import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId } from '../../lib/db';
import { cumulativeForAllPartidas, upsertCubicacionEntry } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { Header } from '../../components/Header';
import { IconPlus } from '../../components/Icon';

export function CubicacionPage() {
  const navigate = useNavigate();
  const parte = useTodayParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const [frenteId, setFrenteId] = useState<string | null>(null);
  const activeFrenteId = frenteId ?? frentes[0]?.id;

  const partidas = useLiveQuery(
    () => (activeFrenteId ? db.partidas.where('frenteId').equals(activeFrenteId).toArray() : []),
    [activeFrenteId],
  ) ?? [];

  const totales = useLiveQuery(() => cumulativeForAllPartidas(), []) ?? {};

  const entriesHoy = useLiveQuery(
    () => (parte ? db.cubicacionEntries.where('parteId').equals(parte.id).toArray() : []),
    [parte?.id],
  ) ?? [];

  const [showAdd, setShowAdd] = useState(false);
  const [nuevo, setNuevo] = useState({ nombre: '', unidad: 'm³', cantidadContratada: 0 });

  const activeFrente = frentes.find((f) => f.id === activeFrenteId);

  const contratadoTotal = partidas.reduce((s, p) => s + p.cantidadContratada, 0);
  const acumuladoTotal = partidas.reduce((s, p) => s + Math.min(totales[p.id] ?? 0, p.cantidadContratada), 0);
  const avancePct = contratadoTotal > 0 ? Math.round((acumuladoTotal / contratadoTotal) * 100) : 0;

  async function guardarPartida() {
    if (!nuevo.nombre.trim() || !activeFrenteId) return;
    await db.partidas.add({
      id: newId(),
      frenteId: activeFrenteId,
      nombre: nuevo.nombre.trim(),
      unidad: nuevo.unidad,
      cantidadContratada: nuevo.cantidadContratada,
    });
    setNuevo({ nombre: '', unidad: 'm³', cantidadContratada: 0 });
    setShowAdd(false);
  }

  if (!parte) return null;

  return (
    <>
      <Header title="Cubicación de Tareas" back>
        <div
          className="flex-row"
          style={{ justifyContent: 'space-between', background: 'var(--charcoal-3)', borderRadius: 11, padding: '10px 13px' }}
        >
          <select
            value={activeFrenteId ?? ''}
            onChange={(e) => setFrenteId(e.target.value)}
            style={{ background: 'none', border: 'none', color: '#fff', fontSize: 12.5, fontWeight: 600, width: '100%' }}
          >
            {frentes.map((f) => (
              <option key={f.id} value={f.id} style={{ color: '#000' }}>
                {f.nombre}{f.km ? ` · ${f.km}` : ''}
              </option>
            ))}
          </select>
        </div>
      </Header>

      <div className="content">
        <div className="card-dark" style={{ marginBottom: 16 }}>
          <div className="flex-row" style={{ justifyContent: 'space-between', marginBottom: 8, alignItems: 'baseline' }}>
            <span style={{ fontSize: 11, color: '#c9c3b8', textTransform: 'uppercase', letterSpacing: '.06em' }}>
              Avance en {activeFrente?.nombre ?? 'este frente'}
            </span>
            <span className="disp" style={{ fontSize: 20, fontWeight: 800, color: 'var(--amber)' }}>{avancePct}%</span>
          </div>
          <div className="progress-track" style={{ background: '#4a453c' }}>
            <div className="progress-fill progress-fill--gradient" style={{ width: `${avancePct}%` }} />
          </div>
        </div>

        <div className="stack">
          {partidas.map((p) => {
            const acumulado = Math.min(totales[p.id] ?? 0, p.cantidadContratada);
            const pct = p.cantidadContratada > 0 ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
            const entry = entriesHoy.find((e) => e.partidaId === p.id);
            return (
              <div key={p.id} className="card">
                <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 9 }}>
                  <span style={{ fontSize: 13.5, fontWeight: 700 }}>{p.nombre}</span>
                  <span style={{ background: 'var(--surface-alt)', color: 'var(--text-soft)', fontSize: 10.5, fontWeight: 700, padding: '3px 8px', borderRadius: 6 }}>{p.unidad}</span>
                </div>
                <div className="progress-track" style={{ marginBottom: 8 }}>
                  <div className="progress-fill" style={{ width: `${pct}%` }} />
                </div>
                <div className="flex-row" style={{ justifyContent: 'space-between', fontSize: 11, color: 'var(--text-soft)', marginBottom: 9 }}>
                  <span>Contratado: {p.cantidadContratada.toLocaleString('es-CL')} {p.unidad}</span>
                  <span>Acum: {acumulado.toLocaleString('es-CL')} {p.unidad} · {pct}%</span>
                </div>
                <div className="flex-row gap-8">
                  <span className="text-soft" style={{ fontSize: 12, flexGrow: 1 }}>Ejecutado hoy</span>
                  <input
                    type="number"
                    className="field-input"
                    style={{ width: 70, textAlign: 'right' }}
                    value={entry?.cantidadEjecutada ?? ''}
                    onChange={(e) => upsertCubicacionEntry(parte.id, p.id, parte.fecha, Number(e.target.value) || 0)}
                  />
                  <span className="text-soft" style={{ fontSize: 12 }}>{p.unidad}</span>
                </div>
              </div>
            );
          })}

          {!showAdd && (
            <button className="chip-dashed card" style={{ justifyContent: 'center', width: '100%', background: 'none' }} onClick={() => setShowAdd(true)}>
              <IconPlus size={15} /> Agregar partida
            </button>
          )}

          {showAdd && (
            <div className="card stack">
              <input
                placeholder="Nombre de la partida"
                value={nuevo.nombre}
                onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })}
                className="field-input"
                style={{ fontWeight: 500 }}
              />
              <div className="flex-row gap-8">
                <select
                  value={nuevo.unidad}
                  onChange={(e) => setNuevo({ ...nuevo, unidad: e.target.value })}
                  className="field-input"
                >
                  {['m³', 'm²', 'ml', 'kg', 'un'].map((u) => <option key={u} value={u}>{u}</option>)}
                </select>
                <input
                  type="number"
                  placeholder="Cantidad contratada"
                  value={nuevo.cantidadContratada || ''}
                  onChange={(e) => setNuevo({ ...nuevo, cantidadContratada: Number(e.target.value) || 0 })}
                  className="field-input"
                  style={{ flexGrow: 1 }}
                />
              </div>
              <div className="flex-row gap-8">
                <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowAdd(false)}>Cancelar</button>
                <button className="btn btn-primary" style={{ flex: 1 }} onClick={guardarPartida}>Guardar</button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div style={{ flexShrink: 0, background: 'var(--surface)', borderTop: '1px solid var(--border)', padding: '12px 20px 16px' }}>
        <button className="btn btn-primary btn-block" onClick={() => navigate('/nuevo-parte')}>Volver al parte</button>
      </div>
    </>
  );
}
