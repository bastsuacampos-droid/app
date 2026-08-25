import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db } from '../../lib/db';
import { cumulativeForAllPartidas, upsertCubicacionEntry, estadoTarea, crearPartida, registrarMedicion, medicionesDePartida, esRellenoPorCapas } from '../../lib/queries';
import { useActiveParte } from '../../lib/useActiveParte';
import { formatShortDate } from '../../lib/date';
import { TIPO_ELEMENTO_LABEL, TIPOS_POR_UNIDAD, formatDatosMedicion, camposDesdeDatos } from '../../lib/cubicacionCalculo';
import type { MedicionInfo, NuevaPartidaDatos } from '../../lib/cubicacionCalculo';
import { parseNumeroDecimal } from '../../lib/numero';
import { Header } from '../../components/Header';
import { IconPlus, IconChevronRight } from '../../components/Icon';
import { CampoDesplegable } from '../../components/CampoDesplegable';
import { CalculadoraCubicacion } from './CalculadoraCubicacion';
import { NuevaPartidaForm } from './NuevaPartidaForm';
import { FiguraMedidas } from './FiguraMedidas';
import { RegistroCapasRelleno } from './RegistroCapasRelleno';
import type { CubicacionEntry, EstadoTarea, Partida } from '../../types/models';

const ESTADO_INFO: Record<EstadoTarea, { label: string; bg: string; color: string }> = {
  pendiente: { label: 'Pendiente de días anteriores', bg: 'var(--yellow-soft)', color: 'var(--yellow-text)' },
  en_progreso_hoy: { label: 'En progreso hoy', bg: 'var(--blue-soft)', color: 'var(--blue)' },
  sin_iniciar: { label: 'Nueva', bg: 'var(--surface-alt)', color: 'var(--text-soft)' },
  terminada: { label: 'Terminada', bg: 'var(--green-soft)', color: 'var(--green)' },
};

const ORDEN_ESTADO: Record<EstadoTarea, number> = { pendiente: 0, en_progreso_hoy: 1, sin_iniciar: 2, terminada: 3 };

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
  const parte = useActiveParte();
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
      camposPersonalizados: info.camposPersonalizados, formula: info.formula,
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
      camposPersonalizados: info.camposPersonalizados, formula: info.formula,
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

  async function guardarPartida(datos: NuevaPartidaDatos) {
    if (!datos.nombre.trim() || !activeFrenteId || !parte) return;
    await crearPartida(activeFrenteId, parte.id, parte.fecha, datos);
    setShowAdd(false);
  }

  async function guardarSubPartida(padreId: string, datos: NuevaPartidaDatos) {
    if (!datos.nombre.trim() || !activeFrenteId || !parte) return;
    await crearPartida(activeFrenteId, parte.id, parte.fecha, datos, padreId);
    setSubAddParentId(null);
  }

  async function guardarCantidadContratada(partidaId: string, valor: number, unidad?: string) {
    await db.partidas.update(partidaId, unidad ? { cantidadContratada: valor, unidad } : { cantidadContratada: valor });
    setEditContratadoId(null);
  }

  if (!parte) return null;

  return (
    <>
      <Header title="Cubicación de Tareas" back>
        <CampoDesplegable
          valor={activeFrenteId ?? ''}
          opciones={frentes.map((f) => ({ value: f.id, label: `${f.nombre}${f.km ? ` · ${f.km}` : ''}` }))}
          onSeleccionar={setFrenteId}
          claseBoton=""
          estiloBoton={{ background: 'var(--surface-alt)', border: 'none', borderRadius: 11, padding: '10px 13px', color: 'var(--text)', fontSize: 12.5, fontWeight: 600 }}
        />
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
                    parteId={parte.id}
                    fecha={parte.fecha}
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
                            parteId={parte.id}
                            fecha={parte.fecha}
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
        <button className="btn btn-primary btn-block" onClick={() => navigate(`/nuevo-parte?parte=${parte.id}`)}>Volver al parte</button>
      </div>
    </>
  );
}

/** Badges + progress bar + "ejecutado hoy" + calculadora de cubicación + memoria de cálculo
 * for one cubicated partida — reused for a top-level (leaf) tarea and for each of its
 * sub-tareas alike. */
function TareaBody({
  p, acumulado, entry, estado, parteId, fecha, calcOpenId, setCalcOpenId, editContratadoId, setEditContratadoId,
  onGuardarContratado, onEjecutadoChange, onAgregarMedicionEjecutado, onAgregarMedicionContratado,
}: {
  p: Partida;
  acumulado: number;
  entry: CubicacionEntry | undefined;
  estado: EstadoTarea;
  parteId: string;
  fecha: string;
  calcOpenId: string | null;
  setCalcOpenId: (id: string | null) => void;
  editContratadoId: string | null;
  setEditContratadoId: (id: string | null) => void;
  onGuardarContratado: (partidaId: string, valor: number, unidad?: string) => void;
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
        {p.unidad === '%' ? (
          <span>Tarea por % de avance</span>
        ) : editContratadoId === p.id ? (
          <ContratadoEditor
            unidad={p.unidad}
            valorInicial={p.cantidadContratada}
            onGuardar={(v, u) => onGuardarContratado(p.id, v, u)}
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
        <span>{p.unidad === '%' ? `Acumulado: ${pct}%` : `Acum: ${acumulado.toLocaleString('es-CL')} ${p.unidad} · ${pct}%`}</span>
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
      {esRellenoPorCapas(p.nombre) && <RegistroCapasRelleno partidaId={p.id} parteId={parteId} fecha={fecha} />}
    </>
  );
}

/** Lista colapsable de las mediciones registradas para una partida (memoria de cálculo), para
 * poder revisar de dónde salió cada cifra al volver a mirar la tarea más adelante. */
function MemoriaCalculo({ partidaId }: { partidaId: string }) {
  const mediciones = useLiveQuery(() => medicionesDePartida(partidaId), [partidaId]) ?? [];
  const [abierto, setAbierto] = useState(false);
  const [dibujoAbiertoId, setDibujoAbiertoId] = useState<string | null>(null);

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
              <div className="text-soft" style={{ fontSize: 10.5, marginBottom: 4 }}>
                {formatDatosMedicion(m.tipo, m.datos, m.unidad, m.camposPersonalizados)} = <strong>{m.subtotal.toLocaleString('es-CL', { maximumFractionDigits: 3 })} {m.unidad}</strong>
              </div>
              <button
                type="button"
                onClick={() => setDibujoAbiertoId((id) => (id === m.id ? null : m.id))}
                className="flex-row"
                style={{ gap: 3, alignItems: 'center', background: 'none', border: 'none', color: 'var(--accent)', fontSize: 10, fontWeight: 700, padding: 0 }}
              >
                {dibujoAbiertoId === m.id ? 'Ocultar dibujo' : 'Ver dibujo'}
                <IconChevronRight size={10} color="var(--accent)" style={{ transform: dibujoAbiertoId === m.id ? 'rotate(90deg)' : undefined }} />
              </button>
              {dibujoAbiertoId === m.id && (
                <div style={{ marginTop: 6 }}>
                  <FiguraMedidas
                    tipo={m.tipo}
                    unidad={m.unidad}
                    campos={camposDesdeDatos(m.datos)}
                    camposPersonalizados={m.camposPersonalizados}
                    formula={m.formula}
                  />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** Inline editor for a task's cantidadContratada — lets you cubicar una tarea que se agregó
 * sin dato fijo (a mano o por elementos con la calculadora), corregirlo más adelante, o
 * convertirla a seguimiento por % de avance (unidad '%', cantidadContratada fija en 100) en
 * vez de por cantidad física — onGuardar recibe la unidad también cuando cambia. */
function ContratadoEditor({
  unidad, valorInicial, onGuardar, onCancelar, onAgregarMedicion,
}: {
  unidad: string;
  valorInicial: number;
  onGuardar: (v: number, unidad?: string) => void;
  onCancelar: () => void;
  onAgregarMedicion: (info: MedicionInfo) => void;
}) {
  const [modo, setModo] = useState<'cantidad' | 'porcentaje'>('cantidad');
  const [valor, setValor] = useState(valorInicial ? String(valorInicial) : '');
  const [modoCalc, setModoCalc] = useState(false);
  const tieneFormula = !!TIPOS_POR_UNIDAD[unidad];

  return (
    <div style={{ width: '100%' }}>
      <div className="flex-row gap-8" style={{ marginBottom: 8 }}>
        <button
          type="button"
          onClick={() => setModo('cantidad')}
          className="chip"
          style={modo === 'cantidad' ? { background: 'var(--accent)', borderColor: 'var(--accent)', color: '#fff' } : undefined}
        >
          Por cantidad
        </button>
        <button
          type="button"
          onClick={() => setModo('porcentaje')}
          className="chip"
          style={modo === 'porcentaje' ? { background: 'var(--accent)', borderColor: 'var(--accent)', color: '#fff' } : undefined}
        >
          Por % de avance
        </button>
      </div>

      {modo === 'cantidad' ? (
        <>
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
        </>
      ) : (
        <div className="flex-row gap-8" style={{ alignItems: 'center' }}>
          <span className="text-soft" style={{ fontSize: 10.5, flexGrow: 1 }}>
            Esta tarea pasa a seguirse por % de avance, no por cantidad.
          </span>
          <button onClick={() => onGuardar(100, '%')} style={{ background: 'none', border: 'none', color: 'var(--accent)', fontWeight: 700, fontSize: 11 }}>Confirmar</button>
          <button onClick={onCancelar} style={{ background: 'none', border: 'none', color: 'var(--text-soft)', fontSize: 11 }}>Cancelar</button>
        </div>
      )}
    </div>
  );
}

