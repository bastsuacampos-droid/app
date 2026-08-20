import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId } from '../../lib/db';
import { cumulativeForAllPartidas, upsertCubicacionEntry, estadoTarea, registrarMedicion, medicionesDePartida } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { CATALOGO_PARTIDAS } from '../../lib/catalogoPartidas';
import { formatShortDate } from '../../lib/date';
import {
  TIPO_ELEMENTO_LABEL, TIPOS_POR_UNIDAD, camposDelTipo, calcularSubtotalElemento, formatDatosMedicion,
} from '../../lib/cubicacionCalculo';
import { parseNumeroDecimal } from '../../lib/numero';
import { Header } from '../../components/Header';
import { IconPlus, IconChevronRight } from '../../components/Icon';
import type { CubicacionEntry, EstadoTarea, Partida, TipoElementoMedicion } from '../../types/models';

const ESTADO_INFO: Record<EstadoTarea, { label: string; bg: string; color: string }> = {
  pendiente: { label: 'Pendiente de días anteriores', bg: 'var(--yellow-soft)', color: 'var(--yellow-text)' },
  en_progreso_hoy: { label: 'En progreso hoy', bg: 'var(--blue-soft)', color: 'var(--blue)' },
  sin_iniciar: { label: 'Nueva', bg: 'var(--surface-alt)', color: 'var(--text-soft)' },
  terminada: { label: 'Terminada', bg: 'var(--green-soft)', color: 'var(--green)' },
};

const ORDEN_ESTADO: Record<EstadoTarea, number> = { pendiente: 0, en_progreso_hoy: 1, sin_iniciar: 2, terminada: 3 };

interface MedicionInfo { tipo: TipoElementoMedicion; descripcion: string; datos: Record<string, number>; subtotal: number }
interface NuevaPartidaDatos { nombre: string; unidad: string; cantidadContratada: number; avanceHoy: number; mediciones: MedicionInfo[] }

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

  /** "Calcular por dimensiones" while logging today's progress: adds the subtotal on top of
   * whatever was already typed for hoy, and keeps the measurement for later reference. */
  async function onAgregarMedicionEjecutado(partidaId: string, unidad: string, info: MedicionInfo) {
    if (!parte || info.subtotal <= 0) return;
    const existente = entriesHoy.find((e) => e.partidaId === partidaId)?.cantidadEjecutada ?? 0;
    await upsertCubicacionEntry(parte.id, partidaId, parte.fecha, Number((existente + info.subtotal).toFixed(3)));
    await registrarMedicion({
      partidaId, fecha: parte.fecha, proposito: 'ejecutado',
      tipo: info.tipo, descripcion: info.descripcion || undefined, datos: info.datos, subtotal: info.subtotal, unidad,
    });
  }

  /** Cubicar (o corregir) la cantidad contratada por elementos: cada uno agregado suma a la
   * cantidad contratada y queda registrado, para tareas que se agregaron sin dato fijo. */
  async function onAgregarMedicionContratado(partidaId: string, unidad: string, cantidadActual: number, info: MedicionInfo) {
    if (!parte || info.subtotal <= 0) return;
    await db.partidas.update(partidaId, { cantidadContratada: Number((cantidadActual + info.subtotal).toFixed(3)) });
    await registrarMedicion({
      partidaId, fecha: parte.fecha, proposito: 'contratado',
      tipo: info.tipo, descripcion: info.descripcion || undefined, datos: info.datos, subtotal: info.subtotal, unidad,
    });
  }

  const activeFrente = frentes.find((f) => f.id === activeFrenteId);

  // Only leaf partidas (no sub-tareas of their own) carry a real, editable quantity — a
  // parent that has been subdivided stops contributing its own number to the frente total,
  // its sub-tareas do that instead (see the "cubicación directa anterior" note below).
  const partidasHoja = todasPartidas.filter((p) => !(hijosPorPadre.get(p.id)?.length));
  const contratadoTotal = partidasHoja.reduce((s, p) => s + p.cantidadContratada, 0);
  const acumuladoTotal = partidasHoja.reduce((s, p) => s + Math.min(totales[p.id] ?? 0, p.cantidadContratada), 0);
  const avancePct = contratadoTotal > 0 ? Math.round((acumuladoTotal / contratadoTotal) * 100) : 0;

  async function persistirMediciones(partidaId: string, unidad: string, mediciones: MedicionInfo[]) {
    if (!parte) return;
    for (const m of mediciones) {
      await registrarMedicion({
        partidaId, fecha: parte.fecha, proposito: 'contratado',
        tipo: m.tipo, descripcion: m.descripcion || undefined, datos: m.datos, subtotal: m.subtotal, unidad,
      });
    }
  }

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
    await persistirMediciones(id, datos.unidad, datos.mediciones);
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
    await persistirMediciones(id, datos.unidad, datos.mediciones);
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
                    onAgregarMedicionEjecutado={(info) => onAgregarMedicionEjecutado(p.id, p.unidad, info)}
                    onAgregarMedicionContratado={(info) => onAgregarMedicionContratado(p.id, p.unidad, p.cantidadContratada, info)}
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
                            onAgregarMedicionEjecutado={(info) => onAgregarMedicionEjecutado(h.id, h.unidad, info)}
                            onAgregarMedicionContratado={(info) => onAgregarMedicionContratado(h.id, h.unidad, h.cantidadContratada, info)}
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

/** Badges + progress bar + "ejecutado hoy" + calculadora de cubicación + memoria de cálculo
 * for one cubicated partida — reused for a top-level (leaf) tarea and for each of its
 * sub-tareas alike. */
function TareaBody({
  p, acumulado, entry, estado, calcOpenId, setCalcOpenId, editContratadoId, setEditContratadoId,
  onGuardarContratado, onEjecutadoChange, onAgregarMedicionEjecutado, onAgregarMedicionContratado,
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
  onAgregarMedicionEjecutado: (info: MedicionInfo) => void;
  onAgregarMedicionContratado: (info: MedicionInfo) => void;
}) {
  const pct = p.cantidadContratada > 0 ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
  const estadoInfo = ESTADO_INFO[estado];
  const tieneFormula = !!TIPOS_POR_UNIDAD[p.unidad];

  // "Ejecutado hoy" is a text buffer, not a direct mirror of entry.cantidadEjecutada: that value
  // comes from Dexie via useLiveQuery and round-trips through onEjecutadoChange's write on every
  // keystroke. Binding the input straight to it reformats the display mid-typing and erases the
  // decimal separator before the next digit lands (typing "3,1" collapses to "3"). The buffer
  // still needs to pick up changes made elsewhere (e.g. the calculadora's "Agregar al total de
  // hoy"), so it resyncs from the DB value — but only while this field isn't focused, so it
  // never clobbers what the user is mid-typing.
  const [textoEjecutado, setTextoEjecutado] = useState(() => entry?.cantidadEjecutada ? String(entry.cantidadEjecutada) : '');
  const ejecutadoEnfocado = useRef(false);
  useEffect(() => {
    if (!ejecutadoEnfocado.current) {
      setTextoEjecutado(entry?.cantidadEjecutada ? String(entry.cantidadEjecutada) : '');
    }
  }, [entry?.cantidadEjecutada]);

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
            onAgregarMedicion={onAgregarMedicionContratado}
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
          type="text"
          inputMode="decimal"
          aria-label="Ejecutado hoy"
          className="field-input"
          style={{ width: 70, textAlign: 'right' }}
          value={textoEjecutado}
          onFocus={() => { ejecutadoEnfocado.current = true; }}
          onBlur={() => {
            ejecutadoEnfocado.current = false;
            setTextoEjecutado(entry?.cantidadEjecutada ? String(entry.cantidadEjecutada) : '');
          }}
          onChange={(e) => {
            setTextoEjecutado(e.target.value);
            onEjecutadoChange(p.id, parseNumeroDecimal(e.target.value));
          }}
        />
        <span className="text-soft" style={{ fontSize: 12 }}>{p.unidad}</span>
      </div>

      {tieneFormula && (
        <>
          <button
            onClick={() => setCalcOpenId(calcOpenId === p.id ? null : p.id)}
            style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700, padding: '8px 0 0', display: 'flex', alignItems: 'center', gap: 4 }}
          >
            Calcular por dimensiones
            <IconChevronRight size={12} color="var(--accent)" style={{ transform: calcOpenId === p.id ? 'rotate(90deg)' : undefined }} />
          </button>
          {calcOpenId === p.id && (
            <CalculadoraCubicacion unidad={p.unidad} modo="ejecutado" onAgregar={onAgregarMedicionEjecutado} />
          )}
        </>
      )}

      <MemoriaCalculo partidaId={p.id} />
    </>
  );
}

/**
 * Calculadora de cubicación por elementos: prisma rectangular, sección trapezoidal,
 * cilíndrico, muro con descuento de vanos y enfierradura por diámetro. Es un cálculo
 * geométrico general de uso práctico en obra — no una transcripción de NCh 353 Of.2000
 * ("Mediciones y cubicaciones en construcción"); antes de usarla para el estado de pago
 * conviene verificar el criterio de medición exacto de cada partida en el contrato.
 */
function CalculadoraCubicacion({
  unidad, modo, onAgregar,
}: {
  unidad: string;
  modo: 'contratado' | 'ejecutado';
  onAgregar: (info: MedicionInfo) => void;
}) {
  const opciones = TIPOS_POR_UNIDAD[unidad] ?? [];
  const [tipo, setTipo] = useState<TipoElementoMedicion>(opciones[0]?.value ?? 'rectangular');
  const [descripcion, setDescripcion] = useState('');
  const [campos, setCampos] = useState<Record<string, string>>({});

  const camposActivos = camposDelTipo(tipo, unidad);
  const datos: Record<string, number> = {};
  camposActivos.forEach((c) => { datos[c.key] = parseNumeroDecimal(campos[c.key] ?? '') || (c.key === 'cantidad' ? 1 : 0); });
  const subtotal = calcularSubtotalElemento(tipo, unidad, datos);

  function elegirTipo(t: TipoElementoMedicion) {
    setTipo(t);
    setCampos({});
  }

  function agregar() {
    onAgregar({ tipo, descripcion: descripcion.trim(), datos, subtotal });
    setCampos({});
    setDescripcion('');
  }

  return (
    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--border)' }}>
      {opciones.length > 1 && (
        <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 8 }}>
          {opciones.map((o) => (
            <button
              key={o.value}
              className="chip"
              style={tipo === o.value ? { background: 'var(--accent)', borderColor: 'var(--accent)', color: '#fff' } : undefined}
              onClick={() => elegirTipo(o.value)}
            >
              {o.label}
            </button>
          ))}
        </div>
      )}
      <input
        placeholder="Descripción (opcional, ej: Zapata Z-1)"
        value={descripcion}
        onChange={(e) => setDescripcion(e.target.value)}
        className="field-input"
        style={{ width: '100%', marginBottom: 8, fontWeight: 500 }}
      />
      <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 8 }}>
        {camposActivos.map((c) => (
          <DimField key={c.key} label={c.label} value={campos[c.key] ?? ''} onChange={(v) => setCampos((prev) => ({ ...prev, [c.key]: v }))} />
        ))}
      </div>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12.5 }}>
          Subtotal: <strong>{subtotal.toLocaleString('es-CL', { maximumFractionDigits: 3 })} {unidad}</strong>
        </span>
        <button className="btn btn-primary" style={{ padding: '8px 14px', fontSize: 12 }} onClick={agregar} disabled={subtotal <= 0}>
          {modo === 'contratado' ? 'Agregar a lo contratado' : 'Agregar al total de hoy'}
        </button>
      </div>
      <div className="text-soft" style={{ fontSize: 9.5, lineHeight: 1.4 }}>
        Cálculo geométrico general de uso práctico en obra — no es una transcripción de NCh 353
        Of.2000. Verifica el criterio de medición de tu contrato antes de usarlo en un estado de pago.
      </div>
    </div>
  );
}

/** Lista colapsable de las mediciones registradas para una partida (memoria de cálculo), para
 * poder revisar de dónde salió cada cifra al volver a mirar la tarea más adelante. */
function MemoriaCalculo({ partidaId }: { partidaId: string }) {
  const mediciones = useLiveQuery(() => medicionesDePartida(partidaId), [partidaId]) ?? [];
  const [abierto, setAbierto] = useState(false);

  if (mediciones.length === 0) return null;

  return (
    <div style={{ marginTop: 9 }}>
      <button
        onClick={() => setAbierto((v) => !v)}
        style={{ background: 'none', border: 'none', color: 'var(--text-soft)', fontSize: 11, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4, padding: 0 }}
      >
        Ver mediciones ({mediciones.length})
        <IconChevronRight size={11} color="var(--text-soft)" style={{ transform: abierto ? 'rotate(90deg)' : undefined }} />
      </button>
      {abierto && (
        <div className="stack" style={{ gap: 6, marginTop: 8 }}>
          {mediciones.map((m) => (
            <div key={m.id} style={{ background: 'var(--surface-alt)', borderRadius: 8, padding: '7px 9px' }}>
              <div className="flex-row" style={{ justifyContent: 'space-between', marginBottom: 2, gap: 8 }}>
                <strong style={{ fontSize: 11 }}>{m.descripcion || TIPO_ELEMENTO_LABEL[m.tipo]}</strong>
                <span style={{ fontSize: 10, fontWeight: 700, color: m.proposito === 'contratado' ? 'var(--accent-dark)' : 'var(--green)', whiteSpace: 'nowrap' }}>
                  {m.proposito === 'contratado' ? 'Contratado' : 'Ejecutado'} · {formatShortDate(m.fecha)}
                </span>
              </div>
              <div className="text-soft" style={{ fontSize: 10.5 }}>
                {formatDatosMedicion(m.tipo, m.datos, m.unidad)} = <strong>{m.subtotal.toLocaleString('es-CL', { maximumFractionDigits: 3 })} {m.unidad}</strong>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** Inline editor for a task's cantidadContratada — lets you cubicar una tarea que se agregó
 * sin dato fijo (a mano o por elementos con la calculadora), o corregirlo más adelante. */
function ContratadoEditor({
  unidad, valorInicial, onGuardar, onCancelar, onAgregarMedicion,
}: {
  unidad: string;
  valorInicial: number;
  onGuardar: (v: number) => void;
  onCancelar: () => void;
  onAgregarMedicion: (info: MedicionInfo) => void;
}) {
  const [valor, setValor] = useState(valorInicial ? String(valorInicial) : '');
  const [modoCalc, setModoCalc] = useState(false);
  const tieneFormula = !!TIPOS_POR_UNIDAD[unidad];

  return (
    <div style={{ width: '100%' }}>
      <div className="flex-row gap-8" style={{ alignItems: 'center' }}>
        <input
          type="text"
          inputMode="decimal"
          autoFocus
          placeholder={`Cantidad contratada (${unidad})`}
          value={valor}
          onChange={(e) => setValor(e.target.value)}
          className="field-input"
          style={{ width: 120 }}
        />
        <button onClick={() => onGuardar(parseNumeroDecimal(valor))} style={{ background: 'none', border: 'none', color: 'var(--accent)', fontWeight: 700, fontSize: 11 }}>Guardar</button>
        <button onClick={onCancelar} style={{ background: 'none', border: 'none', color: 'var(--text-soft)', fontSize: 11 }}>Cancelar</button>
      </div>
      {tieneFormula && (
        <button
          onClick={() => setModoCalc((v) => !v)}
          style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 10.5, fontWeight: 700, padding: '7px 0 0' }}
        >
          {modoCalc ? 'Ocultar calculadora' : '¿Prefieres cubicar por medidas?'}
        </button>
      )}
      {modoCalc && <CalculadoraCubicacion unidad={unidad} modo="contratado" onAgregar={onAgregarMedicion} />}
    </div>
  );
}

function DimField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label style={{ fontSize: 10.5, color: 'var(--text-soft)', display: 'flex', flexDirection: 'column', gap: 3, flex: '1 1 100px' }}>
      {label}
      <input
        type="text"
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
 * catálogo) and, more compactly, for a sub-tarea under an existing one. Cantidad contratada
 * can be typed directly or built up from elements with the calculadora; either way, the
 * measurements used are carried into onGuardar so they land in the memoria de cálculo once
 * the partida (and its id) exists. */
function NuevaPartidaForm({ onGuardar, onCancelar, conCatalogo }: { onGuardar: (datos: NuevaPartidaDatos) => void; onCancelar: () => void; conCatalogo?: boolean }) {
  const [catCategoria, setCatCategoria] = useState(CATALOGO_PARTIDAS[0].categoria);
  const [nombre, setNombre] = useState('');
  const [unidad, setUnidad] = useState('m³');
  // Text buffers, not numbers: the input's displayed value must echo exactly what the user
  // typed. Deriving a number and feeding it back into `value` on every keystroke reformats the
  // field mid-typing and erases the decimal separator before the next digit lands (e.g. typing
  // "5,5" collapses to "55"). Only the calculadora's programmatic add/subtract needs a number,
  // so it reads/writes through these same buffers via parseNumeroDecimal.
  const [cantidadContratadaTexto, setCantidadContratadaTexto] = useState('');
  const [avanceHoyTexto, setAvanceHoyTexto] = useState('');
  const [mostrarCalc, setMostrarCalc] = useState(false);
  const [mediciones, setMediciones] = useState<MedicionInfo[]>([]);
  const tieneFormula = !!TIPOS_POR_UNIDAD[unidad];
  const cantidadContratada = parseNumeroDecimal(cantidadContratadaTexto);
  const avanceHoy = parseNumeroDecimal(avanceHoyTexto);

  function cambiarUnidad(u: string) {
    setUnidad(u);
    // Pending measurements were computed for the previous unidad's geometry — they don't
    // carry over cleanly, so start the memoria de cálculo over rather than show stale data.
    setMediciones([]);
    setCantidadContratadaTexto('');
    setMostrarCalc(false);
  }

  function agregarMedicion(info: MedicionInfo) {
    setMediciones((m) => [...m, info]);
    setCantidadContratadaTexto((t) => String(Number((parseNumeroDecimal(t) + info.subtotal).toFixed(3))));
  }

  function quitarMedicion(idx: number) {
    setMediciones((m) => {
      setCantidadContratadaTexto((t) => String(Number((parseNumeroDecimal(t) - m[idx].subtotal).toFixed(3))));
      return m.filter((_, i) => i !== idx);
    });
  }

  function guardar() {
    onGuardar({ nombre, unidad, cantidadContratada, avanceHoy, mediciones });
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
                onClick={() => { setNombre(it.nombre); cambiarUnidad(it.unidad); }}
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
        <select value={unidad} onChange={(e) => cambiarUnidad(e.target.value)} className="field-input">
          {['m³', 'm²', 'ml', 'kg', 'un'].map((u) => <option key={u} value={u}>{u}</option>)}
        </select>
        <input
          type="text"
          inputMode="decimal"
          placeholder="Cantidad contratada (si no la sabes, déjala en blanco)"
          value={cantidadContratadaTexto}
          onChange={(e) => setCantidadContratadaTexto(e.target.value)}
          className="field-input"
          style={{ flexGrow: 1 }}
        />
      </div>

      {tieneFormula && (
        <button
          onClick={() => setMostrarCalc((v) => !v)}
          style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700, padding: 0, textAlign: 'left', display: 'flex', alignItems: 'center', gap: 4 }}
        >
          {mostrarCalc ? 'Ocultar calculadora' : '¿Prefieres cubicar por medidas?'}
          <IconChevronRight size={12} color="var(--accent)" style={{ transform: mostrarCalc ? 'rotate(90deg)' : undefined }} />
        </button>
      )}
      {mostrarCalc && <CalculadoraCubicacion unidad={unidad} modo="contratado" onAgregar={agregarMedicion} />}
      {mediciones.length > 0 && (
        <div className="stack" style={{ gap: 5 }}>
          {mediciones.map((m, i) => (
            <div key={i} className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', fontSize: 11, background: 'var(--surface-alt)', borderRadius: 7, padding: '6px 9px' }}>
              <span>{m.descripcion || TIPO_ELEMENTO_LABEL[m.tipo]}: <strong>{m.subtotal.toLocaleString('es-CL', { maximumFractionDigits: 3 })} {unidad}</strong></span>
              <button onClick={() => quitarMedicion(i)} aria-label="Quitar medición" style={{ background: 'none', border: 'none', color: 'var(--red)', fontWeight: 800, fontSize: 14, lineHeight: 1, padding: '0 2px' }}>×</button>
            </div>
          ))}
        </div>
      )}

      <label className="text-soft" style={{ fontSize: 11.5 }}>
        Avance de hoy en esta tarea (opcional)
        <input
          type="text"
          inputMode="decimal"
          placeholder="0"
          value={avanceHoyTexto}
          onChange={(e) => setAvanceHoyTexto(e.target.value)}
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
