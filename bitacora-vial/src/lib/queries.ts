import { db, newId, nowISO, todayISO } from './db';
import type { EstadoTarea, MedicionCubicacion, Parte, Partida } from '../types/models';

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

export interface TareaDelDiaItem {
  partidaId: string;
  nombre: string;
  unidad: string;
  avanceHoy: number;
  acumulado: number;
  contratado: number;
  pct: number;
  cubicada: boolean;
}

export interface TareaDelDiaGrupo {
  id: string;
  titulo: string;
  /** True when this group represents a parent partida with real sub-tareas (as opposed to
   * a plain single-item legacy partida, where the title is just that item's own name). */
  agrupada: boolean;
  items: TareaDelDiaItem[];
}

/** Today's cubicación entries, grouped by parent partida (sub-tareas nest under their
 * padre's título) so a task subdivided into sub-tareas shows as one titled group with each
 * sub-tarea's own progress, while a plain (non-subdivided) partida still renders flat. */
export async function tareasDelDiaAgrupadas(parteId: string): Promise<TareaDelDiaGrupo[]> {
  const entries = await db.cubicacionEntries.where('parteId').equals(parteId).toArray();
  if (entries.length === 0) return [];

  const totales = await cumulativeForAllPartidas();
  const partidas = await db.partidas.bulkGet(entries.map((e) => e.partidaId));
  const padreIds = Array.from(
    new Set(partidas.filter((p): p is Partida => !!p?.partidaPadreId).map((p) => p!.partidaPadreId!)),
  );
  const padres = await db.partidas.bulkGet(padreIds);
  const padreMap = new Map(padres.filter((p): p is Partida => !!p).map((p) => [p.id, p]));

  const grupos = new Map<string, TareaDelDiaGrupo>();
  entries.forEach((e, i) => {
    const p = partidas[i];
    if (!p) return;
    const cubicada = p.cantidadContratada > 0;
    const acumuladoReal = totales[p.id] ?? 0;
    const acumulado = cubicada ? Math.min(acumuladoReal, p.cantidadContratada) : acumuladoReal;
    const pct = cubicada ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
    const item: TareaDelDiaItem = {
      partidaId: p.id, nombre: p.nombre, unidad: p.unidad, avanceHoy: e.cantidadEjecutada, acumulado, contratado: p.cantidadContratada, pct, cubicada,
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

/** Records one dimension-calculator measurement (memoria de cálculo) for a partida, keeping
 * the dimensions that produced a subtotal visible after that number has already been folded
 * into cantidadContratada or a day's cantidadEjecutada. */
export async function registrarMedicion(medicion: Omit<MedicionCubicacion, 'id'>): Promise<void> {
  await db.medicionesCubicacion.add({ id: newId(), ...medicion });
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
