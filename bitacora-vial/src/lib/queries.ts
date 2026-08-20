import { db, newId, nowISO, todayISO } from './db';
import { formatDimensionesCompacto } from './cubicacionCalculo';
import type { NuevaPartidaDatos } from './cubicacionCalculo';
import type { EstadoTarea, Frente, MedicionCubicacion, Parte, Partida } from '../types/models';

// Several screens independently call getOrCreateTodayParte() via useLiveQuery on mount. The
// *creation* side-effect is deduplicated behind this in-flight promise (keyed by date) so two
// near-simultaneous first calls can't both see "no parte yet" and insert duplicates — but the
// actual Parte is always re-read fresh from Dexie afterwards, so useLiveQuery still stays
// reactive to later edits instead of freezing on the snapshot captured at creation time.
let ensureTodayPromise: Promise<void> | null = null;
let ensureTodayDate = '';

/** Idempotent create-if-missing side effect. Call this from a useEffect, never from inside a
 * useLiveQuery querier — Dexie's liveQuery runs queriers in a readonly transaction. */
export function ensureTodayParteExists(fecha: string): Promise<void> {
  if (!ensureTodayPromise || ensureTodayDate !== fecha) {
    ensureTodayDate = fecha;
    ensureTodayPromise = (async () => {
      const existing = await db.partes.where('fecha').equals(fecha).first();
      if (existing) return;

      const last = await db.partes.orderBy('numero').last();
      const numero = (last?.numero ?? 118) + 1;

      const parte: Parte = {
        id: newId(),
        numero,
        fecha,
        turno: 'dia',
        clima: 'soleado',
        temperaturaC: undefined,
        atrasoClimaMin: 0,
        frentesIds: [],
        tareasSeleccionadasIds: [],
        observaciones: '',
        estado: 'en_edicion',
        createdAt: nowISO(),
        updatedAt: nowISO(),
      };
      await db.partes.add(parte);
    })().catch((err) => {
      ensureTodayPromise = null; // allow a retry on genuine failure
      throw err;
    });
  }
  return ensureTodayPromise;
}

/** Returns today's Parte, creating a fresh "en_edicion" draft on first visit of the day.
 * This performs a write — call it imperatively (event handler, useEffect), never as a
 * useLiveQuery querier. For reactive reads use the `useTodayParte()` hook instead. */
export async function getOrCreateTodayParte(): Promise<Parte> {
  const fecha = todayISO();
  await ensureTodayParteExists(fecha);
  return (await db.partes.where('fecha').equals(fecha).first())!;
}

/** Cumulative quantity executed for a partida across every parte logged so far (all dates). */
export async function cumulativeForPartida(partidaId: string): Promise<number> {
  const entries = await db.cubicacionEntries.where('partidaId').equals(partidaId).toArray();
  return entries.reduce((sum, e) => sum + e.cantidadEjecutada, 0);
}

export async function cumulativeForAllPartidas(): Promise<Record<string, number>> {
  const entries = await db.cubicacionEntries.toArray();
  const totals: Record<string, number> = {};
  for (const e of entries) {
    totals[e.partidaId] = (totals[e.partidaId] ?? 0) + e.cantidadEjecutada;
  }
  return totals;
}

/**
 * A task's status is always derived, never stored: "terminada" once the accumulated
 * quantity reaches the contracted one; "en_progreso_hoy" once today logged something for
 * it; "pendiente" when earlier days made progress but today hasn't touched it yet
 * (exactly the "trabajo pendiente de días anteriores" the foreman needs to pick back up);
 * "sin_iniciar" for a task nobody has logged anything against yet.
 */
export function estadoTarea(acumulado: number, contratado: number, tieneEntradaHoy: boolean): EstadoTarea {
  if (contratado > 0 && acumulado >= contratado) return 'terminada';
  if (tieneEntradaHoy) return 'en_progreso_hoy';
  if (acumulado > 0) return 'pendiente';
  return 'sin_iniciar';
}

/** Weighted physical-progress % across every partida (weighted by contracted quantity). */
export async function overallProgressPct(): Promise<number> {
  const partidas = await db.partidas.toArray();
  const totals = await cumulativeForAllPartidas();
  let weightedDone = 0;
  let weightedTotal = 0;
  for (const p of partidas) {
    const done = Math.min(totals[p.id] ?? 0, p.cantidadContratada);
    weightedDone += done;
    weightedTotal += p.cantidadContratada;
  }
  if (weightedTotal === 0) return 0;
  return Math.round((weightedDone / weightedTotal) * 100);
}

export interface DiaHorasExtra {
  fecha: string;
  horas: number;
  motivo?: string;
}

export interface HorasExtraPorTrabajador {
  trabajadorId: string;
  nombre: string;
  cargo: string;
  totalHoras: number;
  dias: DiaHorasExtra[];
}

/** Groups every overtime record within a month (yyyy-MM) by trabajador, with day-level detail. */
export async function monthlyOvertimeReport(monthISO: string): Promise<HorasExtraPorTrabajador[]> {
  const registros = await db.asistencias
    .filter((r) => r.fecha.startsWith(monthISO) && r.horasExtra > 0)
    .toArray();

  const trabajadores = await db.trabajadores.toArray();
  const byId = new Map(trabajadores.map((t) => [t.id, t]));

  const grouped = new Map<string, HorasExtraPorTrabajador>();
  for (const r of registros) {
    const trabajador = byId.get(r.trabajadorId);
    if (!trabajador) continue;
    if (!grouped.has(r.trabajadorId)) {
      grouped.set(r.trabajadorId, {
        trabajadorId: r.trabajadorId,
        nombre: trabajador.nombre,
        cargo: trabajador.cargo,
        totalHoras: 0,
        dias: [],
      });
    }
    const entry = grouped.get(r.trabajadorId)!;
    entry.totalHoras += r.horasExtra;
    entry.dias.push({ fecha: r.fecha, horas: r.horasExtra, motivo: r.motivoExtra });
  }

  return Array.from(grouped.values())
    .map((e) => ({ ...e, dias: e.dias.sort((a, b) => a.fecha.localeCompare(b.fecha)) }))
    .sort((a, b) => b.totalHoras - a.totalHoras);
}

export async function monthlyOvertimeTotals(monthISO: string) {
  const report = await monthlyOvertimeReport(monthISO);
  const totalHoras = report.reduce((sum, r) => sum + r.totalHoras, 0);
  const totalJornadas = report.reduce((sum, r) => sum + r.dias.length, 0);
  return { totalHoras, totalJornadas };
}

/** Creates or updates today's execution entry for a partida (one entry per parte+partida). */
export async function upsertCubicacionEntry(parteId: string, partidaId: string, fecha: string, cantidad: number) {
  const existing = await db.cubicacionEntries
    .where('parteId').equals(parteId)
    .filter((e) => e.partidaId === partidaId)
    .first();

  if (existing) {
    await db.cubicacionEntries.update(existing.id, { cantidadEjecutada: cantidad });
  } else if (cantidad !== 0) {
    await db.cubicacionEntries.add({ id: newId(), parteId, partidaId, fecha, cantidadEjecutada: cantidad });
  }
}

/** Adds `incremento` on top of today's existing entry — reads the current value from Dexie
 * inside the same read-write transaction (not from whatever the caller's React state last
 * rendered), so two increments fired close together can never race and clobber one another
 * or double-count. This is what the "Avance de hoy" +/Enter control in Nuevo Parte uses. */
export async function incrementarCubicacionEntry(parteId: string, partidaId: string, fecha: string, incremento: number) {
  if (incremento <= 0) return;
  await db.transaction('rw', db.cubicacionEntries, async () => {
    const existing = await db.cubicacionEntries
      .where('parteId').equals(parteId)
      .filter((e) => e.partidaId === partidaId)
      .first();
    const nuevaCantidad = Number(((existing?.cantidadEjecutada ?? 0) + incremento).toFixed(3));
    if (existing) {
      await db.cubicacionEntries.update(existing.id, { cantidadEjecutada: nuevaCantidad });
    } else {
      await db.cubicacionEntries.add({ id: newId(), parteId, partidaId, fecha, cantidadEjecutada: nuevaCantidad });
    }
  });
}

export interface TareaDelDiaItem {
  partidaId: string;
  nombre: string;
  unidad: string;
  avanceHoy: number;
  acumulado: number;
  contratado: number;
  pct: number;
  cubicada: boolean;
  estado: EstadoTarea;
  /** Dimensions of the most recent measurement recorded for this partida — only for
   * non-linear unidades (m³, m², kg); a plain "ml" task has nothing to show beyond its
   * quantity, so faltanteLineal is used there instead. */
  dimensionesTexto?: string;
  /** Remaining linear meters to reach cantidadContratada — only for unidad === 'ml'. */
  faltanteLineal?: number;
}

export interface TareaDelDiaGrupo {
  id: string;
  titulo: string;
  /** True when this group represents a parent partida with real sub-tareas (as opposed to
   * a plain single-item legacy partida, where the title is just that item's own name). */
  agrupada: boolean;
  items: TareaDelDiaItem[];
}

/** Groups a set of leaf partidas by parent partida (sub-tareas nest under their padre's
 * título) so a task subdivided into sub-tareas shows as one titled group with each
 * sub-tarea's own progress, while a plain (non-subdivided) partida still renders flat. */
async function agruparPorPadre(partidas: Partida[], entriesPorPartida: Map<string, number>): Promise<TareaDelDiaGrupo[]> {
  if (partidas.length === 0) return [];

  const totales = await cumulativeForAllPartidas();
  const padreIds = Array.from(new Set(partidas.filter((p) => p.partidaPadreId).map((p) => p.partidaPadreId!)));
  const padres = await db.partidas.bulkGet(padreIds);
  const padreMap = new Map(padres.filter((p): p is Partida => !!p).map((p) => [p.id, p]));

  // Non-linear tasks show their most recent measurement's dimensions; ml tasks have no
  // useful "shape" to show, so they get "faltante lineal" instead (computed per item below).
  const idsNoLineales = partidas.filter((p) => p.unidad !== 'ml').map((p) => p.id);
  const mediciones = idsNoLineales.length > 0
    ? await db.medicionesCubicacion.where('partidaId').anyOf(idsNoLineales).toArray()
    : [];
  const ultimaMedicionPorPartida = new Map<string, MedicionCubicacion>();
  for (const m of mediciones) {
    const actual = ultimaMedicionPorPartida.get(m.partidaId);
    if (!actual) { ultimaMedicionPorPartida.set(m.partidaId, m); continue; }
    // Prefer a 'contratado' measurement (describes the physical element) over 'ejecutado'
    // (a day's progress entry); among ties, the most recently recorded one.
    const actualEsContratado = actual.proposito === 'contratado';
    const mEsContratado = m.proposito === 'contratado';
    if ((mEsContratado && !actualEsContratado) || (mEsContratado === actualEsContratado && m.fecha >= actual.fecha)) {
      ultimaMedicionPorPartida.set(m.partidaId, m);
    }
  }

  const grupos = new Map<string, TareaDelDiaGrupo>();
  partidas.forEach((p) => {
    const cubicada = p.cantidadContratada > 0;
    const acumuladoReal = totales[p.id] ?? 0;
    const avanceHoy = entriesPorPartida.get(p.id) ?? 0;
    const acumulado = cubicada ? Math.min(acumuladoReal, p.cantidadContratada) : acumuladoReal;
    const pct = cubicada ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
    const estado = estadoTarea(acumuladoReal, p.cantidadContratada, avanceHoy > 0);

    const ultimaMedicion = ultimaMedicionPorPartida.get(p.id);
    const item: TareaDelDiaItem = {
      partidaId: p.id, nombre: p.nombre, unidad: p.unidad, avanceHoy, acumulado, contratado: p.cantidadContratada, pct, cubicada, estado,
      dimensionesTexto: p.unidad !== 'ml' && ultimaMedicion ? formatDimensionesCompacto(ultimaMedicion.tipo, ultimaMedicion.datos) : undefined,
      faltanteLineal: p.unidad === 'ml' && cubicada ? Math.max(0, p.cantidadContratada - acumulado) : undefined,
    };

    const padre = p.partidaPadreId ? padreMap.get(p.partidaPadreId) : undefined;
    const groupId = padre?.id ?? p.id;
    if (!grupos.has(groupId)) {
      grupos.set(groupId, { id: groupId, titulo: padre?.nombre ?? p.nombre, agrupada: !!padre, items: [] });
    }
    grupos.get(groupId)!.items.push(item);
  });
  return Array.from(grupos.values());
}

/** Tasks explicitly picked for today's report (Parte.tareasSeleccionadasIds), grouped by
 * parent partida. Terminada tasks are excluded — once a task is fully done it moves to
 * tareasCompletadas()'s "Completadas" view instead of staying in the daily log. */
export async function tareasActivasAgrupadas(parteId: string, seleccionadasIds: string[]): Promise<TareaDelDiaGrupo[]> {
  if (seleccionadasIds.length === 0) return [];
  const partidas = (await db.partidas.bulkGet(seleccionadasIds)).filter((p): p is Partida => !!p);
  const entries = await db.cubicacionEntries.where('parteId').equals(parteId).toArray();
  const entriesPorPartida = new Map(entries.map((e) => [e.partidaId, e.cantidadEjecutada]));

  const grupos = await agruparPorPadre(partidas, entriesPorPartida);
  return grupos
    .map((g) => ({ ...g, items: g.items.filter((i) => i.estado !== 'terminada') }))
    .filter((g) => g.items.length > 0);
}

export interface TareaCandidata {
  partidaId: string;
  nombre: string;
  unidad: string;
  frenteId: string;
  padreNombre?: string;
  estado: EstadoTarea;
}

/** Leaf partidas (no sub-tareas of their own) belonging to the given frentes, for the
 * "Seleccionar tarea" picker on Nuevo Parte — terminadas excluded, since those live in the
 * Completadas tab instead. */
export async function tareasDisponiblesParaFrentes(frentesIds: string[], parteId: string): Promise<TareaCandidata[]> {
  if (frentesIds.length === 0) return [];
  const partidas = await db.partidas.where('frenteId').anyOf(frentesIds).toArray();
  const hijosPadreIds = new Set(partidas.filter((p) => p.partidaPadreId).map((p) => p.partidaPadreId!));
  const leaves = partidas.filter((p) => !hijosPadreIds.has(p.id));

  const padreIds = Array.from(new Set(leaves.filter((p) => p.partidaPadreId).map((p) => p.partidaPadreId!)));
  const padres = await db.partidas.bulkGet(padreIds);
  const padreMap = new Map(padres.filter((p): p is Partida => !!p).map((p) => [p.id, p]));

  const totales = await cumulativeForAllPartidas();
  const entries = await db.cubicacionEntries.where('parteId').equals(parteId).toArray();
  const entriesPorPartida = new Map(entries.map((e) => [e.partidaId, e.cantidadEjecutada]));

  return leaves
    .map((p) => {
      const acumulado = totales[p.id] ?? 0;
      const avanceHoy = entriesPorPartida.get(p.id) ?? 0;
      const estado = estadoTarea(acumulado, p.cantidadContratada, avanceHoy > 0);
      return {
        partidaId: p.id, nombre: p.nombre, unidad: p.unidad, frenteId: p.frenteId,
        padreNombre: p.partidaPadreId ? padreMap.get(p.partidaPadreId)?.nombre : undefined,
        estado,
      };
    })
    .filter((t) => t.estado !== 'terminada');
}

export interface TareaCompletada {
  partidaId: string;
  nombre: string;
  unidad: string;
  frenteId: string;
  frenteNombre: string;
  cantidad: number;
  /** The day the accumulated total first reached cantidadContratada. */
  fechaCompletada: string;
}

/** Every leaf partida that reached its cantidadContratada, with dónde (frente) and cuándo
 * (the day its running total first got there) — the "Completadas" tab of Tareas y Avances. */
export async function tareasCompletadas(): Promise<TareaCompletada[]> {
  const partidas = await db.partidas.toArray();
  const hijosPadreIds = new Set(partidas.filter((p) => p.partidaPadreId).map((p) => p.partidaPadreId!));
  const leaves = partidas.filter((p) => !hijosPadreIds.has(p.id) && p.cantidadContratada > 0);
  const totales = await cumulativeForAllPartidas();
  const completadas = leaves.filter((p) => (totales[p.id] ?? 0) >= p.cantidadContratada);
  if (completadas.length === 0) return [];

  const frentes = await db.frentes.bulkGet(Array.from(new Set(completadas.map((p) => p.frenteId))));
  const frenteMap = new Map(frentes.filter((f): f is Frente => !!f).map((f) => [f.id, f]));

  const resultados: TareaCompletada[] = [];
  for (const p of completadas) {
    const entries = (await db.cubicacionEntries.where('partidaId').equals(p.id).toArray())
      .sort((a, b) => a.fecha.localeCompare(b.fecha));
    let acumulado = 0;
    let fechaCompletada = '';
    for (const e of entries) {
      acumulado += e.cantidadEjecutada;
      if (acumulado >= p.cantidadContratada) {
        fechaCompletada = e.fecha;
        break;
      }
    }
    if (!fechaCompletada && entries.length > 0) fechaCompletada = entries[entries.length - 1].fecha;
    resultados.push({
      partidaId: p.id, nombre: p.nombre, unidad: p.unidad, frenteId: p.frenteId,
      frenteNombre: frenteMap.get(p.frenteId)?.nombre ?? '', cantidad: p.cantidadContratada, fechaCompletada,
    });
  }
  return resultados.sort((a, b) => b.fechaCompletada.localeCompare(a.fechaCompletada));
}

/** Records one dimension-calculator measurement (memoria de cálculo) for a partida, keeping
 * the dimensions that produced a subtotal visible after that number has already been folded
 * into cantidadContratada or a day's cantidadEjecutada. */
export async function registrarMedicion(medicion: Omit<MedicionCubicacion, 'id'>): Promise<void> {
  await db.medicionesCubicacion.add({ id: newId(), ...medicion });
}

/** Creates a new partida — optionally as a sub-tarea of padreId — logging today's avance and
 * persisting any mediciones used to build cantidadContratada in one write path. Shared by
 * Cubicación's inline "Agregar tarea" form and Nuevo Parte's "Cubicar nueva tarea" modal, so
 * both stay backed by the exact same logic instead of two copies that could drift apart. */
export async function crearPartida(
  frenteId: string, parteId: string, fecha: string, datos: NuevaPartidaDatos, padreId?: string,
): Promise<string> {
  const id = newId();
  await db.partidas.add({
    id,
    frenteId,
    nombre: datos.nombre.trim(),
    unidad: datos.unidad,
    cantidadContratada: datos.cantidadContratada,
    ...(padreId ? { partidaPadreId: padreId } : {}),
  });
  if (datos.avanceHoy > 0) {
    await upsertCubicacionEntry(parteId, id, fecha, datos.avanceHoy);
  }
  for (const m of datos.mediciones) {
    await registrarMedicion({
      partidaId: id, fecha, proposito: 'contratado',
      tipo: m.tipo, descripcion: m.descripcion || undefined, datos: m.datos, subtotal: m.subtotal, unidad: datos.unidad,
    });
  }
  return id;
}

/** All measurements recorded for a partida, most recent first. */
export async function medicionesDePartida(partidaId: string): Promise<MedicionCubicacion[]> {
  const rows = await db.medicionesCubicacion.where('partidaId').equals(partidaId).toArray();
  return rows.sort((a, b) => b.fecha.localeCompare(a.fecha));
}

/** Makes sure every active trabajador of a frente has an attendance row for this parte (defaults to presente). */
export async function ensureAsistenciaForFrente(parteId: string, frenteId: string, fecha: string) {
  const trabajadores = await db.trabajadores.where('frenteId').equals(frenteId).toArray();
  const existentes = await db.asistencias.where('parteId').equals(parteId).toArray();
  const yaRegistrados = new Set(existentes.map((r) => r.trabajadorId));

  const faltantes = trabajadores.filter((t) => t.activo && !yaRegistrados.has(t.id));
  if (faltantes.length === 0) return;

  await db.asistencias.bulkAdd(
    faltantes.map((t) => ({
      id: newId(),
      parteId,
      trabajadorId: t.id,
      frenteId,
      fecha,
      presente: true,
      horasNormales: 8,
      horasExtra: 0,
    })),
  );
}

/** Sends a worker to another frente for today: their existing attendance row just moves —
 * they stop appearing under their home frente's list and start appearing under the
 * destination's, badged there as "prestado". */
export async function moveTrabajadorAFrente(registroId: string, nuevoFrenteId: string) {
  await db.asistencias.update(registroId, { frenteId: nuevoFrenteId });
}

/** Brings a worker from another crew into `frenteDestinoId` for today. Reuses their
 * existing row for this parte if they already have one (so they only ever have one
 * attendance row per day, wherever they end up working), otherwise creates it. */
export async function agregarTrabajadorPrestado(parteId: string, trabajadorId: string, frenteDestinoId: string, fecha: string) {
  const existente = await db.asistencias
    .where('parteId').equals(parteId)
    .filter((r) => r.trabajadorId === trabajadorId)
    .first();

  if (existente) {
    await db.asistencias.update(existente.id, { frenteId: frenteDestinoId });
    return;
  }

  await db.asistencias.add({
    id: newId(),
    parteId,
    trabajadorId,
    frenteId: frenteDestinoId,
    fecha,
    presente: true,
    horasNormales: 8,
    horasExtra: 0,
  });
}

export async function attendanceSummaryForParte(parteId: string) {
  const registros = await db.asistencias.where('parteId').equals(parteId).toArray();
  const presentes = registros.filter((r) => r.presente).length;
  return { presentes, total: registros.length };
}

export async function pendingBackupCount(): Promise<number> {
  const pendientes = await db.partes.where('estado').anyOf(['en_edicion', 'pendiente']).count();
  return pendientes;
}
