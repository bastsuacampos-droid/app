import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId } from '../../lib/db';
import { cumulativeForAllPartidas, upsertCubicacionEntry, estadoTarea } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { CATALOGO_PARTIDAS } from '../../lib/catalogoPartidas';
import { Header } from '../../components/Header';
import { IconPlus, IconChevronRight } from '../../components/Icon';
import type { CubicacionEntry, EstadoTarea, Partida } from '../../types/models';

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

/** Aggregate estado for a group card (sub-tareas), so it sorts sensibly among leaf cards:
 * any pending sub-tarea surfaces the group first, an in-progress one next, all-done last. */
function estadoDeGrupo(hijos: Partida[], totales: Record<string, number>, entriesHoy: CubicacionEntry[]): EstadoTarea {
  if (hijos.length === 0) return 'sin_iniciar';
  const estados = hijos.map((h) => {
    const acumuladoReal = totales[h.id] ?? 0;
    const entry = entriesHoy.find((e) => e.partidaId === h.id);
    return estadoTarea(acumuladoReal, h.cantidadContratada, !!entry && entry.cantidadEjecutada > 0);
  });
  if (estados.some((e) => e === 'pendiente')) return 'pendiente';
  if (estados.some((e) => e === 'en_progreso_hoy')) return 'en_progreso_hoy';
  if (estados.every((e) => e === 'terminada')) return 'terminada';
  return 'sin_iniciar';
}

interface NuevaPartidaDatos { nombre: string; unidad: string; cantidadContratada: number; avanceHoy: number }

export function CubicacionPage() {
  const navigate = useNavigate();
  const parte = useTodayParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const [frenteId, setFrenteId] = useState<string | null>(null);
  const activeFrenteId = frenteId ?? frentes[0]?.id;

  const todasPartidas = useLiveQuery(
    () => (activeFrenteId ? db.partidas.where('frenteId').equals(activeFrenteId).toArray() : []),
    [activeFrenteId],
  ) ?? [];

  const totales = useLiveQuery(() => cumulativeForAllPartidas(), []) ?? {};

  const entriesHoy = useLiveQuery(
    () => (parte ? db.cubicacionEntries.where('parteId').equals(parte.id).toArray() : []),
    [parte?.id],
  ) ?? [];

  const [showAdd, setShowAdd] = useState(false);
  const [subAddParentId, setSubAddParentId] = useState<string | null>(null);
  const [calcOpenId, setCalcOpenId] = useState<string | null>(null);
  const [editContratadoId, setEditContratadoId] = useState<string | null>(null);

  const partidasTop = todasPartidas.filter((p) => !p.partidaPadreId);
  const hijosPorPadre = new Map<string, Partida[]>();
  todasPartidas.forEach((p) => {
    if (!p.partidaPadreId) return;
    const arr = hijosPorPadre.get(p.partidaPadreId) ?? [];
    arr.push(p);
    hijosPorPadre.set(p.partidaPadreId, arr);
  });

  async function onEjecutadoChange(partidaId: string, valor: number) {
    if (!parte) return;
    await upsertCubicacionEntry(parte.id, partidaId, parte.fecha, valor);
  }

  async function onAgregarFormula(partidaId: string, subtotal: number) {
    if (!parte || subtotal <= 0) return;
    const existente = entriesHoy.find((e) => e.partidaId === partidaId)?.cantidadEjecutada ?? 0;
    await upsertCubicacionEntry(parte.id, partidaId, parte.fecha, Number((existente + subtotal).toFixed(3)));
  }

  const activeFrente = frentes.find((f) => f.id === activeFrenteId);

  // Only leaf partidas (no sub-tareas of their own) carry a real, editable quantity — a
  // parent that has been subdivided stops contributing its own number to the frente total,
  // its sub-tareas do that instead (see the "cubicación directa anterior" note below).
  const partidasHoja = todasPartidas.filter((p) => !(hijosPorPadre.get(p.id)?.length));
  const contratadoTotal = partidasHoja.reduce((s, p) => s + p.cantidadContratada, 0);
  const acumuladoTotal = partidasHoja.reduce((s, p) => s + Math.min(totales[p.id] ?? 0, p.cantidadContratada), 0);
  const avancePct = contratadoTotal > 0 ? Math.round((acumuladoTotal / contratadoTotal) * 100) : 0;

  async function guardarPartida(datos: NuevaPartidaDatos) {
    if (!datos.nombre.trim() || !activeFrenteId || !parte) return;
    const id = newId();
    await db.partidas.add({
      id,
      frenteId: activeFrenteId,
      nombre: datos.nombre.trim(),
      unidad: datos.unidad,
      cantidadContratada: datos.cantidadContratada,
    });
    if (datos.avanceHoy > 0) {
      await upsertCubicacionEntry(parte.id, id, parte.fecha, datos.avanceHoy);
    }
    setShowAdd(false);
  }

  async function guardarSubPartida(padreId: string, datos: NuevaPartidaDatos) {
    if (!datos.nombre.trim() || !activeFrenteId || !parte) return;
    const id = newId();
    await db.partidas.add({
      id,
      frenteId: activeFrenteId,
      nombre: datos.nombre.trim(),
      unidad: datos.unidad,
      cantidadContratada: datos.cantidadContratada,
      partidaPadreId: padreId,
    });
    if (datos.avanceHoy > 0) {
      await upsertCubicacionEntry(parte.id, id, parte.fecha, datos.avanceHoy);
    }
    setSubAddParentId(null);
  }

  async function guardarCantidadContratada(partidaId: string, valor: number) {
    await db.partidas.update(partidaId, { cantidadContratada: valor });
    setEditContratadoId(null);
  }

  if (!parte) return null;

  return (
    <>
      <Header title="Cubicación de Tareas" back>
        <div
          className="flex-row"
          style={{ justifyContent: 'space-between', background: 'var(--surface-alt)', borderRadius: 11, padding: '10px 13px' }}
        >
          <select
            value={activeFrenteId ?? ''}
            onChange={(e) => setFrenteId(e.target.value)}
            style={{ background: 'none', border: 'none', color: 'var(--text)', fontSize: 12.5, fontWeight: 600, width: '100%' }}
          >
            {frentes.map((f) => (
              <option key={f.id} value={f.id} style={{ color: 'var(--text)' }}>
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
          <div className="progress-track" style={{ background: 'var(--dark-card-bg-2)' }}>
            <div className="progress-fill progress-fill--gradient" style={{ width: `${avancePct}%` }} />
          </div>
        </div>

        <div className="stack">
          {partidasTop
            .map((p) => {
              const hijos = hijosPorPadre.get(p.id) ?? [];
              const tieneHijos = hijos.length > 0;
              const acumuladoReal = totales[p.id] ?? 0;
              const acumulado = Math.min(acumuladoReal, p.cantidadContratada);
              const entry = entriesHoy.find((e) => e.partidaId === p.id);
              const estado = tieneHijos
                ? estadoDeGrupo(hijos, totales, entriesHoy)
                : estadoTarea(acumuladoReal, p.cantidadContratada, !!entry && entry.cantidadEjecutada > 0);
              return { p, hijos, tieneHijos, acumuladoReal, acumulado, entry, estado };
            })
            .sort((a, b) => ORDEN_ESTADO[a.estado] - ORDEN_ESTADO[b.estado])
            .map(({ p, hijos, tieneHijos, acumuladoReal, acumulado, entry, estado }) => (
              <div key={p.id} className="card">
                <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 7 }}>
                  <span style={{ fontSize: 13.5, fontWeight: 700 }}>{p.nombre}</span>
                  {tieneHijos ? (
                    <span style={{ background: 'var(--accent-soft)', color: 'var(--accent-dark)', fontSize: 10.5, fontWeight: 700, padding: '3px 8px', borderRadius: 6 }}>
                      {hijos.length} sub-tarea{hijos.length === 1 ? '' : 's'}
                    </span>
                  ) : (
                    <span style={{ background: 'var(--surface-alt)', color: 'var(--text-soft)', fontSize: 10.5, fontWeight: 700, padding: '3px 8px', borderRadius: 6 }}>{p.unidad}</span>
                  )}
                </div>

                {!tieneHijos && (
                  <TareaBody
                    p={p}
                    acumulado={acumulado}
                    entry={entry}
                    estado={estado}
                    calcOpenId={calcOpenId}
                    setCalcOpenId={setCalcOpenId}
                    editContratadoId={editContratadoId}
                    setEditContratadoId={setEditContratadoId}
                    onGuardarContratado={guardarCantidadContratada}
                    onEjecutadoChange={onEjecutadoChange}
                    onAgregarFormula={onAgregarFormula}
                  />
                )}

                {tieneHijos && acumuladoReal > 0 && (
                  <div className="text-soft" style={{ fontSize: 10.5, marginBottom: 10 }}>
                    Cubicación directa registrada antes de dividir en sub-tareas: {acumuladoReal.toLocaleString('es-CL')} {p.unidad}
                  </div>
                )}

                {tieneHijos && (
                  <div className="stack" style={{ gap: 12, marginBottom: 4 }}>
                    {hijos.map((h) => {
                      const acumuladoRealH = totales[h.id] ?? 0;
                      const acumuladoH = Math.min(acumuladoRealH, h.cantidadContratada);
                      const entryH = entriesHoy.find((e) => e.partidaId === h.id);
                      const estadoH = estadoTarea(acumuladoRealH, h.cantidadContratada, !!entryH && entryH.cantidadEjecutada > 0);
                      return (
                        <div key={h.id} style={{ background: 'var(--surface-alt)', borderRadius: 10, padding: 11 }}>
                          <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 7 }}>
                            <span style={{ fontSize: 12.5, fontWeight: 700 }}>{h.nombre}</span>
                            <span style={{ background: 'var(--surface)', color: 'var(--text-soft)', fontSize: 10, fontWeight: 700, padding: '3px 8px', borderRadius: 6 }}>{h.unidad}</span>
                          </div>
                          <TareaBody
                            p={h}
                            acumulado={acumuladoH}
                            entry={entryH}
                            estado={estadoH}
                            calcOpenId={calcOpenId}
                            setCalcOpenId={setCalcOpenId}
                            editContratadoId={editContratadoId}
                            setEditContratadoId={setEditContratadoId}
                            onGuardarContratado={guardarCantidadContratada}
                            onEjecutadoChange={onEjecutadoChange}
                            onAgregarFormula={onAgregarFormula}
                          />
                        </div>
                      );
                    })}
                  </div>
                )}

                {subAddParentId === p.id ? (
                  <NuevaPartidaForm
                    onGuardar={(datos) => guardarSubPartida(p.id, datos)}
                    onCancelar={() => setSubAddParentId(null)}
                  />
                ) : (
                  <button
                    onClick={() => setSubAddParentId(p.id)}
                    style={{
                      background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700,
                      padding: tieneHijos ? '4px 0 0' : '8px 0 0', display: 'flex', alignItems: 'center', gap: 4,
                    }}
                  >
                    <IconPlus size={12} color="var(--accent)" /> Agregar sub-tarea
                  </button>
                )}
              </div>
            ))}

          {!showAdd && (
            <button className="chip-dashed card" style={{ justifyContent: 'center', width: '100%', background: 'none' }} onClick={() => setShowAdd(true)}>
              <IconPlus size={15} /> Agregar tarea
            </button>
          )}

          {showAdd && (
            <div className="card">
              <NuevaPartidaForm onGuardar={guardarPartida} onCancelar={() => setShowAdd(false)} conCatalogo />
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

/** Badges + progress bar + "ejecutado hoy" + optional dimension calculator for one cubicated
 * partida — reused for a top-level (leaf) tarea and for each of its sub-tareas alike. */
function TareaBody({
  p, acumulado, entry, estado, calcOpenId, setCalcOpenId, editContratadoId, setEditContratadoId, onGuardarContratado, onEjecutadoChange, onAgregarFormula,
}: {
  p: Partida;
  acumulado: number;
  entry: CubicacionEntry | undefined;
  estado: EstadoTarea;
  calcOpenId: string | null;
  setCalcOpenId: (id: string | null) => void;
  editContratadoId: string | null;
  setEditContratadoId: (id: string | null) => void;
  onGuardarContratado: (partidaId: string, valor: number) => void;
  onEjecutadoChange: (partidaId: string, valor: number) => void;
  onAgregarFormula: (partidaId: string, subtotal: number) => void;
}) {
  const pct = p.cantidadContratada > 0 ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
  const estadoInfo = ESTADO_INFO[estado];

  return (
    <>
      <div className="flex-row gap-8" style={{ marginBottom: 9, flexWrap: 'wrap' }}>
        <span style={{ background: estadoInfo.bg, color: estadoInfo.color, fontSize: 10, fontWeight: 700, padding: '3px 8px', borderRadius: 20 }}>
          {estadoInfo.label}
        </span>
        {p.cantidadContratada === 0 && (
          <span style={{ background: 'var(--yellow-soft)', color: 'var(--yellow-text)', fontSize: 10, fontWeight: 700, padding: '3px 8px', borderRadius: 20 }}>
            Sin cubicar aún
          </span>
        )}
      </div>
      <div className="progress-track" style={{ marginBottom: 8 }}>
        <div className="progress-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', fontSize: 11, color: 'var(--text-soft)', marginBottom: 9 }}>
        {editContratadoId === p.id ? (
          <ContratadoEditor
            unidad={p.unidad}
            valorInicial={p.cantidadContratada}
            onGuardar={(v) => onGuardarContratado(p.id, v)}
            onCancelar={() => setEditContratadoId(null)}
          />
        ) : (
          <button
            onClick={() => setEditContratadoId(p.id)}
            style={{ background: 'none', border: 'none', color: p.cantidadContratada === 0 ? 'var(--accent)' : 'var(--text-soft)', fontSize: 11, fontWeight: p.cantidadContratada === 0 ? 700 : 400, padding: 0 }}
          >
            {p.cantidadContratada === 0
              ? 'Cubicar esta tarea →'
              : `Contratado: ${p.cantidadContratada.toLocaleString('es-CL')} ${p.unidad} (editar)`}
          </button>
        )}
        <span>Acum: {acumulado.toLocaleString('es-CL')} {p.unidad} · {pct}%</span>
      </div>
      <div className="flex-row gap-8">
        <span className="text-soft" style={{ fontSize: 12, flexGrow: 1 }}>Ejecutado hoy</span>
        <input
          type="number"
          className="field-input"
          style={{ width: 70, textAlign: 'right' }}
          value={entry?.cantidadEjecutada ?? ''}
          onChange={(e) => onEjecutadoChange(p.id, Number(e.target.value) || 0)}
        />
        <span className="text-soft" style={{ fontSize: 12 }}>{p.unidad}</span>
      </div>

      {UNIDADES_CON_FORMULA.has(p.unidad) && (
        <>
          <button
            onClick={() => setCalcOpenId(calcOpenId === p.id ? null : p.id)}
            style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700, padding: '8px 0 0', display: 'flex', alignItems: 'center', gap: 4 }}
          >
            Calcular por dimensiones
            <IconChevronRight size={12} color="var(--accent)" style={{ transform: calcOpenId === p.id ? 'rotate(90deg)' : undefined }} />
          </button>
          {calcOpenId === p.id && (
            <DimensionCalculator unidad={p.unidad} onAgregar={(subtotal) => onAgregarFormula(p.id, subtotal)} />
          )}
        </>
      )}
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

/** Inline editor for a task's cantidadContratada — lets you cubicar una tarea que se agregó
 * sin dato fijo, o corregirlo más adelante, sin tener que recrear la tarea. */
function ContratadoEditor({ unidad, valorInicial, onGuardar, onCancelar }: { unidad: string; valorInicial: number; onGuardar: (v: number) => void; onCancelar: () => void }) {
  const [valor, setValor] = useState(valorInicial ? String(valorInicial) : '');
  return (
    <div className="flex-row gap-8" style={{ alignItems: 'center' }}>
      <input
        type="number"
        autoFocus
        placeholder={`Cantidad contratada (${unidad})`}
        value={valor}
        onChange={(e) => setValor(e.target.value)}
        className="field-input"
        style={{ width: 120 }}
      />
      <button onClick={() => onGuardar(Number(valor) || 0)} style={{ background: 'none', border: 'none', color: 'var(--accent)', fontWeight: 700, fontSize: 11 }}>Guardar</button>
      <button onClick={onCancelar} style={{ background: 'none', border: 'none', color: 'var(--text-soft)', fontSize: 11 }}>Cancelar</button>
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

/** Form to create a new partida — used both for a top-level tarea (with the suggested
 * catálogo) and, more compactly, for a sub-tarea under an existing one. */
function NuevaPartidaForm({ onGuardar, onCancelar, conCatalogo }: { onGuardar: (datos: NuevaPartidaDatos) => void; onCancelar: () => void; conCatalogo?: boolean }) {
  const [catCategoria, setCatCategoria] = useState(CATALOGO_PARTIDAS[0].categoria);
  const [nombre, setNombre] = useState('');
  const [unidad, setUnidad] = useState('m³');
  const [cantidadContratada, setCantidadContratada] = useState(0);
  const [avanceHoy, setAvanceHoy] = useState(0);

  function guardar() {
    onGuardar({ nombre, unidad, cantidadContratada, avanceHoy });
  }

  return (
    <div className="stack" style={{ marginTop: conCatalogo ? 0 : 10, paddingTop: conCatalogo ? 0 : 10, borderTop: conCatalogo ? undefined : '1px solid var(--border)' }}>
      {conCatalogo && (
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
                onClick={() => { setNombre(it.nombre); setUnidad(it.unidad); }}
              >
                {it.nombre} · {it.unidad}
              </button>
            ))}
          </div>
        </div>
      )}

      <input
        placeholder="Nombre de la partida"
        value={nombre}
        onChange={(e) => setNombre(e.target.value)}
        className="field-input"
        style={{ fontWeight: 500 }}
        autoFocus={!conCatalogo}
      />
      <div className="flex-row gap-8">
        <select value={unidad} onChange={(e) => setUnidad(e.target.value)} className="field-input">
          {['m³', 'm²', 'ml', 'kg', 'un'].map((u) => <option key={u} value={u}>{u}</option>)}
        </select>
        <input
          type="number"
          placeholder="Cantidad contratada (si no la sabes, déjala en blanco)"
          value={cantidadContratada || ''}
          onChange={(e) => setCantidadContratada(Number(e.target.value) || 0)}
          className="field-input"
          style={{ flexGrow: 1 }}
        />
      </div>
      <label className="text-soft" style={{ fontSize: 11.5 }}>
        Avance de hoy en esta tarea (opcional)
        <input
          type="number"
          placeholder="0"
          value={avanceHoy || ''}
          onChange={(e) => setAvanceHoy(Number(e.target.value) || 0)}
          className="field-input"
          style={{ width: '100%', marginTop: 4, fontWeight: 500 }}
        />
      </label>
      <div className="flex-row gap-8">
        <button className="btn btn-outline" style={{ flex: 1 }} onClick={onCancelar}>Cancelar</button>
        <button className="btn btn-primary" style={{ flex: 1 }} onClick={guardar}>Guardar tarea</button>
      </div>
    </div>
  );
}
