import Dexie, { type Table } from 'dexie';
import type {
  Parte,
  Frente,
  Trabajador,
  Partida,
  CubicacionEntry,
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
  }
}

export const db = new BitacoraDB();

export function newId(): string {
  return crypto.randomUUID();
}

export function todayISO(): string {
  return new Date().toISOString().slice(0, 10);
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
    { id: newId(), nombre: 'Juan Muñoz', cargo: 'Operador excavadora', frenteId: 'frente-1', activo: true },
    { id: newId(), nombre: 'Rosa Sánchez', cargo: 'Prevencionista', frenteId: 'frente-1', activo: true },
    { id: newId(), nombre: 'Pedro Cárdenas', cargo: 'Obrero', frenteId: 'frente-1', activo: true },
    { id: newId(), nombre: 'Luis Torres', cargo: 'Chofer camión tolva', frenteId: 'frente-1', activo: true },
    { id: newId(), nombre: 'Marcela Fuentes', cargo: 'Obrero', frenteId: 'frente-1', activo: true },
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
