import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId } from '../../lib/db';
import { cumulativeForAllPartidas, upsertCubicacionEntry, estadoTarea } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { CATALOGO_PARTIDAS } from '../../lib/catalogoPartidas';
import { Header } from '../../components/Header';
import { IconPlus, IconChevronRight } from '../../components/Icon';
import type { EstadoTarea } from '../../types/models';

/** Units where a quantity can be computed from element dimensions rather than typed by hand. */
const UNIDADES_CON_FORMULA = new Set(['m³', 'm²', 'ml']);

const ESTADO_INFO: Record<EstadoTarea, { label: string; bg: string; color: string }> = {
  pendiente: { label: 'Pendiente de días anteriores', bg: 'var(--yellow-soft)', color: 'var(--yellow-text)' },
  en_progreso_hoy: { label: 'En progreso hoy', bg: 'var(--blue-soft)', color: 'var(--blue)' },
  sin_iniciar: { label: 'Nueva', bg: 'var(--surface-alt)', color: 'var(--text-soft)' },
  terminada: { label: 'Terminada', bg: 'var(--green-soft)', color: 'var(--green)' },
};

const ORDEN_ESTADO: Record<EstadoTarea, number> = { pendiente: 0, en_progreso_hoy: 1, sin_iniciar: 2, terminada: 3 };

function calcularSubtotal(unidad: string, largo: number, ancho: number, alto: number, cantidad: number): number {
  const n = cantidad > 0 ? cantidad : 1;
  if (unidad === 'm³') return largo * ancho * alto * n;
  if (unidad === 'm²') return largo * ancho * n;
  if (unidad === 'ml') return largo * n;
  return 0;
}

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
  const [calcOpenId, setCalcOpenId] = useState<string | null>(null);
  const [catCategoria, setCatCategoria] = useState(CATALOGO_PARTIDAS[0].categoria);

  async function agregarDesdeFormula(partidaId: string, subtotal: number) {
    if (!parte || subtotal <= 0) return;
    const existente = entriesHoy.find((e) => e.partidaId === partidaId)?.cantidadEjecutada ?? 0;
    await upsertCubicacionEntry(parte.id, partidaId, parte.fecha, Number((existente + subtotal).toFixed(3)));
  }

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
          {partidas
            .map((p) => {
              const acumuladoReal = totales[p.id] ?? 0;
              const acumulado = Math.min(acumuladoReal, p.cantidadContratada);
              const entry = entriesHoy.find((e) => e.partidaId === p.id);
              const estado = estadoTarea(acumuladoReal, p.cantidadContratada, !!entry && entry.cantidadEjecutada > 0);
              return { p, acumulado, entry, estado };
            })
            .sort((a, b) => ORDEN_ESTADO[a.estado] - ORDEN_ESTADO[b.estado])
            .map(({ p, acumulado, entry, estado }) => {
            const pct = p.cantidadContratada > 0 ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
            const estadoInfo = ESTADO_INFO[estado];
            return (
              <div key={p.id} className="card">
                <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 7 }}>
                  <span style={{ fontSize: 13.5, fontWeight: 700 }}>{p.nombre}</span>
                  <span style={{ background: 'var(--surface-alt)', color: 'var(--text-soft)', fontSize: 10.5, fontWeight: 700, padding: '3px 8px', borderRadius: 6 }}>{p.unidad}</span>
                </div>
                <div style={{ marginBottom: 9 }}>
                  <span style={{ background: estadoInfo.bg, color: estadoInfo.color, fontSize: 10, fontWeight: 700, padding: '3px 8px', borderRadius: 20 }}>
                    {estadoInfo.label}
                  </span>
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

                {UNIDADES_CON_FORMULA.has(p.unidad) && (
                  <>
                    <button
                      onClick={() => setCalcOpenId(calcOpenId === p.id ? null : p.id)}
                      style={{ background: 'none', border: 'none', color: 'var(--orange)', fontSize: 11.5, fontWeight: 700, padding: '8px 0 0', display: 'flex', alignItems: 'center', gap: 4 }}
                    >
                      Calcular por dimensiones
                      <IconChevronRight size={12} color="var(--orange)" style={{ transform: calcOpenId === p.id ? 'rotate(90deg)' : undefined }} />
                    </button>
                    {calcOpenId === p.id && (
                      <DimensionCalculator unidad={p.unidad} onAgregar={(subtotal) => agregarDesdeFormula(p.id, subtotal)} />
                    )}
                  </>
                )}
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
              <div>
                <div className="section-label" style={{ marginBottom: 6 }}>Catálogo sugerido</div>
                <select
                  value={catCategoria}
                  onChange={(e) => setCatCategoria(e.target.value)}
                  className="field-input"
                  style={{ width: '100%', marginBottom: 8 }}
                >
                  {CATALOGO_PARTIDAS.map((c) => <option key={c.categoria} value={c.categoria}>{c.categoria}</option>)}
                </select>
                <div className="flex-row gap-8" style={{ flexWrap: 'wrap' }}>
                  {CATALOGO_PARTIDAS.find((c) => c.categoria === catCategoria)?.items.map((it) => (
                    <button
                      key={it.nombre}
                      className="chip"
                      onClick={() => setNuevo({ ...nuevo, nombre: it.nombre, unidad: it.unidad })}
                    >
                      {it.nombre} · {it.unidad}
                    </button>
                  ))}
                </div>
              </div>

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

/**
 * Calcula la cantidad a partir de las dimensiones de un elemento (largo × ancho × alto,
 * según la unidad) y cuántas veces se repite, para no tener que hacer la multiplicación a
 * mano cada vez que hay varios elementos iguales (zapatas, tramos de muro, etc.).
 */
function DimensionCalculator({ unidad, onAgregar }: { unidad: string; onAgregar: (subtotal: number) => void }) {
  const [largo, setLargo] = useState('');
  const [ancho, setAncho] = useState('');
  const [alto, setAlto] = useState('');
  const [cantidad, setCantidad] = useState('1');

  const subtotal = calcularSubtotal(unidad, parseFloat(largo) || 0, parseFloat(ancho) || 0, parseFloat(alto) || 0, parseFloat(cantidad) || 1);

  function agregar() {
    onAgregar(subtotal);
    setLargo('');
    setAncho('');
    setAlto('');
    setCantidad('1');
  }

  return (
    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--border)' }}>
      <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 8 }}>
        <DimField label="Largo (m)" value={largo} onChange={setLargo} />
        {unidad !== 'ml' && <DimField label="Ancho (m)" value={ancho} onChange={setAncho} />}
        {unidad === 'm³' && <DimField label="Alto/Espesor (m)" value={alto} onChange={setAlto} />}
        <DimField label="Cantidad (veces se repite)" value={cantidad} onChange={setCantidad} />
      </div>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: 12.5 }}>
          Subtotal: <strong>{subtotal.toLocaleString('es-CL', { maximumFractionDigits: 3 })} {unidad}</strong>
        </span>
        <button className="btn btn-primary" style={{ padding: '8px 14px', fontSize: 12 }} onClick={agregar} disabled={subtotal <= 0}>
          Agregar al total de hoy
        </button>
      </div>
    </div>
  );
}

function DimField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label style={{ fontSize: 10.5, color: 'var(--text-soft)', display: 'flex', flexDirection: 'column', gap: 3, flex: '1 1 100px' }}>
      {label}
      <input
        type="number"
        inputMode="decimal"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="field-input"
        style={{ width: '100%', fontWeight: 500 }}
      />
    </label>
  );
}
