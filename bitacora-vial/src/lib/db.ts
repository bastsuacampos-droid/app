import Dexie, { type Table } from 'dexie';
import type {
  Parte,
  Frente,
  Trabajador,
  Partida,
  CubicacionEntry,
  MedicionCubicacion,
  RegistroAsistencia,
  Foto,
  Documento,
  Settings,
} from '../types/models';

class BitacoraDB extends Dexie {
  partes!: Table<Parte, string>;
  frentes!: Table<Frente, string>;
  trabajadores!: Table<Trabajador, string>;
  partidas!: Table<Partida, string>;
  cubicacionEntries!: Table<CubicacionEntry, string>;
  medicionesCubicacion!: Table<MedicionCubicacion, string>;
  asistencias!: Table<RegistroAsistencia, string>;
  fotos!: Table<Foto, string>;
  documentos!: Table<Documento, string>;
  settings!: Table<Settings, string>;

  constructor() {
    super('bitacora-vial');
    this.version(1).stores({
      partes: 'id, fecha, estado, numero',
      frentes: 'id',
      trabajadores: 'id, frenteId',
      partidas: 'id, frenteId',
      cubicacionEntries: 'id, parteId, partidaId, fecha',
      asistencias: 'id, parteId, trabajadorId, frenteId, fecha',
      fotos: 'id, parteId, frenteId, capturedAt',
      documentos: 'id, categoria, createdAt',
      settings: 'id',
    });

    // v2: photos gained an "etapa" (antes/durante/despues/inconveniente) and an optional
    // link to the task (partida) they document.
    this.version(2)
      .stores({
        fotos: 'id, parteId, frenteId, partidaId, capturedAt',
      })
      .upgrade(async (tx) => {
        await tx
          .table('fotos')
          .toCollection()
          .modify((foto: Foto) => {
            if (!foto.etapa) foto.etapa = 'durante';
          });
      });

    // v3: a partida can optionally belong to a parent partida (sub-tareas cubicated on
    // their own, grouped under the parent's title). No data transform needed — existing
    // partidas simply have no partidaPadreId, which is exactly "top-level".
    this.version(3).stores({
      partidas: 'id, frenteId, partidaPadreId',
    });

    // v4: the dimension calculator can now record the measurements it used (memoria de
    // cálculo) instead of only folding the computed subtotal into the number — a new table,
    // no existing data to migrate.
    this.version(4).stores({
      medicionesCubicacion: 'id, partidaId, fecha',
    });

    // v5: trabajadores are a single global roster, no fixed "home" frente — where someone
    // works is decided per parte via RegistroAsistencia.frenteId instead. Existing rows just
    // carry a leftover frenteId property the app no longer reads; nothing to transform.
    this.version(5).stores({
      trabajadores: 'id',
    });
  }
}

export const db = new BitacoraDB();

export function newId(): string {
  return crypto.randomUUID();
}

export function todayISO(): string {
  // NOT new Date().toISOString().slice(0, 10) — that converts to UTC first, so anyone west of
  // UTC (Chile included, UTC-3/-4) gets tomorrow's date for the last few hours of every local
  // day. A foreman finishing a report in the evening would have it silently filed under the
  // wrong day, or get a second "today" parte if they reopened the app after that rollover.
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

export function nowISO(): string {
  return new Date().toISOString();
}

// A module-level guard so concurrent callers (React 18 StrictMode double-invokes effects in
// dev, and a fast remount could otherwise call this twice) always share the same in-flight
// seeding run instead of racing two inserts of the same rows.
let seedingPromise: Promise<void> | null = null;

/** Seeds the database once, on first run, with the same illustrative data used in the design mockup. */
export function seedIfEmpty(): Promise<void> {
  if (!seedingPromise) {
    seedingPromise = seedIfEmptyInner().catch((err) => {
      seedingPromise = null; // allow a retry on genuine failure
      throw err;
    });
  }
  return seedingPromise;
}

async function seedIfEmptyInner() {
  const count = await db.frentes.count();
  if (count > 0) return;

  const frentes: Frente[] = [
    { id: 'frente-1', nombre: 'Frente 1', km: 'Km 12+300', activo: true },
    { id: 'frente-2', nombre: 'Frente 2', km: 'Km 15+800', activo: true },
    { id: 'frente-planta', nombre: 'Planta de áridos', km: '', activo: true },
  ];
  await db.frentes.bulkAdd(frentes);

  const trabajadores: Trabajador[] = [
    { id: newId(), nombre: 'Juan Muñoz', cargo: 'Operador excavadora', activo: true },
    { id: newId(), nombre: 'Rosa Sánchez', cargo: 'Prevencionista', activo: true },
    { id: newId(), nombre: 'Pedro Cárdenas', cargo: 'Obrero', activo: true },
    { id: newId(), nombre: 'Luis Torres', cargo: 'Chofer camión tolva', activo: true },
    { id: newId(), nombre: 'Marcela Fuentes', cargo: 'Obrero', activo: true },
    { id: newId(), nombre: 'Ana Rojas', cargo: 'Obrero', activo: true },
  ];
  await db.trabajadores.bulkAdd(trabajadores);

  const partidas: Partida[] = [
    { id: newId(), frenteId: 'frente-1', nombre: 'Excavación en corte', unidad: 'm³', cantidadContratada: 4200 },
    { id: newId(), frenteId: 'frente-1', nombre: 'Relleno estructural', unidad: 'm³', cantidadContratada: 3100 },
    { id: newId(), frenteId: 'frente-1', nombre: 'Base granular', unidad: 'm³', cantidadContratada: 2800 },
    { id: newId(), frenteId: 'frente-1', nombre: 'Imprimación asfáltica', unidad: 'm²', cantidadContratada: 18000 },
  ];
  await db.partidas.bulkAdd(partidas);

  const settings: Settings = {
    id: 'app',
    tema: 'claro',
    unidades: 'metrico',
    recordatorioDiario: true,
    respaldoAutomatico: true,
    autoUpdate: true,
    onboardingComplete: false,
    permisoCamara: false,
    permisoNotificaciones: false,
    permisoUbicacion: false,
  };
  await db.settings.add(settings);
}
