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

/** The crew is a single global roster — nobody has a fixed "home" frente. Where someone works
 * is only decided day to day, when attendance is taken (see RegistroAsistencia.frenteId). */
export interface Trabajador {
  id: string;
  nombre: string;
  cargo: string;
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
  /** If set, this partida is a sub-tarea cubicated on its own under a parent partida
   * (which then acts as a pure title/grouping — see CubicacionPage). */
  partidaPadreId?: string;
}

/** One day's quantity logged against a partida — the source of the cumulative total. */
export interface CubicacionEntry {
  id: string;
  parteId: string;
  partidaId: string;
  fecha: string; // ISO date (yyyy-MM-dd)
  cantidadEjecutada: number;
}

/** Shape of an element measured with the dimension calculator (see CubicacionPage). Not a
 * transcription of NCh 353 Of.2000 — general geometric quantification for common site
 * elements; each contributes its subtotal to either the contracted quantity or a day's
 * executed progress. 'personalizado' is a free-form shape: the foreman defines their own named
 * measurements and a formula combining them, for a geometry that isn't one of the presets. */
export type TipoElementoMedicion =
  | 'rectangular'
  | 'trapezoidal'
  | 'cilindrico'
  | 'conico_truncado'
  | 'cuna'
  | 'triangular'
  | 'circular'
  | 'arco'
  | 'muro_vanos'
  | 'enfierradura'
  | 'personalizado';

/** One named measurement in a 'personalizado' medición — `key` is the short token (a, b, c…)
 * used in the formula, `label` is what the foreman actually called it (e.g. "Base menor"). */
export interface CampoPersonalizado {
  key: string;
  label: string;
}

/** A recorded measurement (memoria de cálculo) for a partida — kept even after its subtotal
 * has been folded into cantidadContratada or a day's cantidadEjecutada, so the dimensions
 * that produced that number stay visible when reviewing the tarea later. */
export interface MedicionCubicacion {
  id: string;
  partidaId: string;
  fecha: string; // ISO date (yyyy-MM-dd) when recorded
  /** Whether this measurement fed the contracted quantity or one day's executed progress. */
  proposito: 'contratado' | 'ejecutado';
  tipo: TipoElementoMedicion;
  descripcion?: string; // e.g. "Zapata Z-1"
  datos: Record<string, number>; // raw dimensions entered, keyed by field name
  subtotal: number;
  unidad: string;
  /** Only for tipo 'personalizado': the field labels behind each datos key, and the formula
   * (referencing those keys) used to compute subtotal — needed to redisplay a saved
   * personalizado medición later, since its "shape" isn't a fixed geometry to derive from tipo. */
  camposPersonalizados?: CampoPersonalizado[];
  formula?: string;
}

/** Sábado se trabaja "a trato" — se paga según acuerdo, no por hora — así que en vez de
 * horasNormales/horasExtra solo se anota si fue media jornada o jornada completa. */
export type JornadaSabado = 'medio' | 'completo';

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
  /** Only set (and only meaningful) when `fecha` falls on a Saturday — see JornadaSabado.
   * horasNormales/horasExtra stay 0 for a Saturday registro since that day isn't paid by the
   * hour. */
  jornadaSabado?: JornadaSabado;
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
  /** Partidas explicitly picked to show in "Tareas y Avances" today — set via the selector
   * on Nuevo Parte, not implied just by having a cubicación entry (that gets folded in too,
   * so nothing already logged silently disappears). Absent on partes created before this
   * field existed; treat as an empty list when reading. */
  tareasSeleccionadasIds?: string[];
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
