/**
 * Domain models shared across the whole app.
 * Kept in one place so new features (or a future backend) reuse the same shapes.
 */

export type Turno = 'dia' | 'noche';
export type Clima = 'soleado' | 'nublado' | 'lluvia';
export type EstadoParte = 'en_edicion' | 'pendiente' | 'respaldado';

export interface Frente {
  id: string;
  nombre: string; // "Frente 1"
  km: string; // "Km 12+300"
  activo: boolean;
}

export interface Trabajador {
  id: string;
  nombre: string;
  cargo: string;
  frenteId: string;
  activo: boolean;
}

/** Derived from acumulado vs. cantidadContratada — never stored, always computed. */
export type EstadoTarea = 'sin_iniciar' | 'pendiente' | 'en_progreso_hoy' | 'terminada';

/** Catalog of work activities ("partidas") tracked per frente, e.g. "Excavación en corte". */
export interface Partida {
  id: string;
  frenteId: string;
  nombre: string;
  unidad: string; // "m³", "m²", "ml", "kg", "un"
  cantidadContratada: number;
}

/** One day's quantity logged against a partida — the source of the cumulative total. */
export interface CubicacionEntry {
  id: string;
  parteId: string;
  partidaId: string;
  fecha: string; // ISO date (yyyy-MM-dd)
  cantidadEjecutada: number;
}

export interface RegistroAsistencia {
  id: string;
  parteId: string;
  trabajadorId: string;
  frenteId: string;
  fecha: string; // ISO date (yyyy-MM-dd), denormalized for month-range queries
  presente: boolean;
  motivoAusencia?: string;
  horasNormales: number;
  horasExtra: number;
  motivoExtra?: string;
}

/** Photo stage: lets a task carry a before/during/after record, plus an issue callout. */
export type EtapaFoto = 'antes' | 'durante' | 'despues' | 'inconveniente';

export interface Foto {
  id: string;
  parteId: string;
  frenteId: string;
  /** The task (Partida) this photo documents, when taken from within a task. */
  partidaId?: string;
  etapa: EtapaFoto;
  blob: Blob;
  anotada: boolean;
  capturedAt: string; // ISO datetime
}

export type DocumentoCategoria = 'Planos' | 'Permisos' | 'Contratos' | 'Fichas técnicas' | 'Otros';

export interface Documento {
  id: string;
  nombre: string;
  categoria: DocumentoCategoria;
  mime: string;
  tamano: number; // bytes
  blob: Blob;
  createdAt: string; // ISO datetime
}

export interface Parte {
  id: string;
  numero: number;
  fecha: string; // ISO date (yyyy-MM-dd)
  turno: Turno;
  clima: Clima;
  temperaturaC?: number;
  atrasoClimaMin: number;
  frentesIds: string[];
  observaciones: string;
  estado: EstadoParte;
  createdAt: string;
  updatedAt: string;
}

export type Tema = 'claro' | 'oscuro';
export type UnidadSistema = 'metrico' | 'imperial';

export interface Settings {
  id: 'app'; // single row
  tema: Tema;
  unidades: UnidadSistema;
  recordatorioDiario: boolean;
  respaldoAutomatico: boolean;
  ultimoRespaldo?: string; // ISO datetime
  autoUpdate: boolean;
  onboardingComplete: boolean;
  permisoCamara: boolean;
  permisoNotificaciones: boolean;
  permisoUbicacion: boolean;
}
