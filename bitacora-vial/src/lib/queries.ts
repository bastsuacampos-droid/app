import { db, newId, nowISO, todayISO } from './db';
import type { Parte } from '../types/models';

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

export async function attendanceSummaryForParte(parteId: string) {
  const registros = await db.asistencias.where('parteId').equals(parteId).toArray();
  const presentes = registros.filter((r) => r.presente).length;
  return { presentes, total: registros.length };
}

export async function pendingBackupCount(): Promise<number> {
  const pendientes = await db.partes.where('estado').anyOf(['en_edicion', 'pendiente']).count();
  return pendientes;
}
