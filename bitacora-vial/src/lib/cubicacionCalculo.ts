import type { CampoPersonalizado, TipoElementoMedicion } from '../types/models';

export interface MedicionInfo {
  tipo: TipoElementoMedicion;
  descripcion: string;
  datos: Record<string, number>;
  subtotal: number;
  /** Only set when tipo is 'personalizado' — see MedicionCubicacion for what these carry. */
  camposPersonalizados?: CampoPersonalizado[];
  formula?: string;
}
export interface NuevaPartidaDatos { nombre: string; unidad: string; cantidadContratada: number; avanceHoy: number; mediciones: MedicionInfo[] }

export const TIPO_ELEMENTO_LABEL: Record<TipoElementoMedicion, string> = {
  rectangular: 'Rectangular',
  trapezoidal: 'Sección trapezoidal',
  cilindrico: 'Cilíndrico',
  conico_truncado: 'Cono truncado',
  cuna: 'Cuña / talud triangular',
  triangular: 'Triangular',
  circular: 'Circular',
  muro_vanos: 'Muro con vanos',
  enfierradura: 'Enfierradura',
  personalizado: 'Personalizado',
};

const OPCION_PERSONALIZADO = { value: 'personalizado' as TipoElementoMedicion, label: 'Personalizado (fórmula propia)' };

/** Which element types make sense to compute for each unidad. Every unidad also gets
 * "Personalizado" tacked on — even a discrete unidad ("un", a count of prefabricated pieces)
 * can still benefit from a one-off formula (e.g. área de una pieza no estándar × cantidad). */
export const TIPOS_POR_UNIDAD: Partial<Record<string, { value: TipoElementoMedicion; label: string }[]>> = {
  'm³': [
    { value: 'rectangular', label: 'Prisma rectangular' },
    { value: 'trapezoidal', label: 'Sección trapezoidal' },
    { value: 'cilindrico', label: 'Cilíndrico' },
    { value: 'conico_truncado', label: 'Cono truncado' },
    { value: 'cuna', label: 'Cuña / talud triangular' },
    OPCION_PERSONALIZADO,
  ],
  'm²': [
    { value: 'rectangular', label: 'Rectangular' },
    { value: 'triangular', label: 'Triangular' },
    { value: 'circular', label: 'Circular' },
    { value: 'muro_vanos', label: 'Muro (descuenta vanos)' },
    OPCION_PERSONALIZADO,
  ],
  ml: [{ value: 'rectangular', label: 'Longitud simple' }, OPCION_PERSONALIZADO],
  kg: [{ value: 'enfierradura', label: 'Enfierradura por diámetro' }, OPCION_PERSONALIZADO],
  un: [OPCION_PERSONALIZADO],
};

export function camposDelTipo(tipo: TipoElementoMedicion, unidad: string): { key: string; label: string }[] {
  switch (tipo) {
    case 'rectangular': {
      const campos = [{ key: 'largo', label: 'Largo (m)' }];
      if (unidad !== 'ml') campos.push({ key: 'ancho', label: 'Ancho (m)' });
      if (unidad === 'm³') campos.push({ key: 'alto', label: 'Alto/Espesor (m)' });
      campos.push({ key: 'cantidad', label: 'Cantidad (veces se repite)' });
      return campos;
    }
    case 'trapezoidal':
      return [
        { key: 'baseMayor', label: 'Base mayor (m)' },
        { key: 'baseMenor', label: 'Base menor (m)' },
        { key: 'alto', label: 'Alto/Profundidad (m)' },
        { key: 'largo', label: 'Largo (m)' },
        { key: 'cantidad', label: 'Cantidad (veces se repite)' },
      ];
    case 'cilindrico':
      return [
        { key: 'diametro', label: 'Diámetro (m)' },
        { key: 'alto', label: 'Alto/Largo (m)' },
        { key: 'cantidad', label: 'Cantidad (veces se repite)' },
      ];
    case 'conico_truncado':
      return [
        { key: 'diametroMayor', label: 'Diámetro mayor (m)' },
        { key: 'diametroMenor', label: 'Diámetro menor (m)' },
        { key: 'alto', label: 'Alto (m)' },
        { key: 'cantidad', label: 'Cantidad (veces se repite)' },
      ];
    case 'cuna':
      return [
        { key: 'base', label: 'Base (m)' },
        { key: 'altura', label: 'Altura (m)' },
        { key: 'largo', label: 'Largo (m)' },
        { key: 'cantidad', label: 'Cantidad (veces se repite)' },
      ];
    case 'triangular':
      return [
        { key: 'base', label: 'Base (m)' },
        { key: 'altura', label: 'Altura (m)' },
        { key: 'cantidad', label: 'Cantidad (veces se repite)' },
      ];
    case 'circular':
      return [
        { key: 'diametro', label: 'Diámetro (m)' },
        { key: 'cantidad', label: 'Cantidad (veces se repite)' },
      ];
    case 'muro_vanos':
      return [
        { key: 'largo', label: 'Largo (m)' },
        { key: 'alto', label: 'Alto (m)' },
        { key: 'cantidad', label: 'Cantidad de paños' },
        { key: 'vanos', label: 'Área de vanos a descontar (m²)' },
      ];
    case 'enfierradura':
      return [
        { key: 'diametro', label: 'Diámetro (mm)' },
        { key: 'longitud', label: 'Longitud por barra (m)' },
        { key: 'cantidad', label: 'Cantidad de barras' },
      ];
    case 'personalizado':
      // Fields are user-defined per medición (see CampoPersonalizado), not a fixed schema —
      // CalculadoraCubicacion renders its own dynamic field editor for this tipo instead.
      return [];
    default:
      return [];
  }
}

/** General geometric quantification for common site elements — NOT a transcription of
 * NCh 353 Of.2000 (mediciones y cubicaciones en construcción); verify the measurement
 * criteria that apply to your contract. The rebar formula (kg/m ≈ d²/162, d en mm) is the
 * standard steel-density calculation, not specific to any one norm. */
export function calcularSubtotalElemento(tipo: TipoElementoMedicion, unidad: string, d: Record<string, number>): number {
  const n = d.cantidad > 0 ? d.cantidad : 1;
  switch (tipo) {
    case 'rectangular':
      if (unidad === 'm³') return d.largo * d.ancho * d.alto * n;
      if (unidad === 'm²') return d.largo * d.ancho * n;
      if (unidad === 'ml') return d.largo * n;
      return 0;
    case 'trapezoidal':
      return ((d.baseMayor + d.baseMenor) / 2) * d.alto * d.largo * n;
    case 'cilindrico':
      return Math.PI * (d.diametro / 2) ** 2 * d.alto * n;
    case 'conico_truncado': {
      const r1 = d.diametroMayor / 2;
      const r2 = d.diametroMenor / 2;
      return ((Math.PI * d.alto) / 3) * (r1 ** 2 + r1 * r2 + r2 ** 2) * n;
    }
    case 'cuna':
      return 0.5 * d.base * d.altura * d.largo * n;
    case 'triangular':
      return 0.5 * d.base * d.altura * n;
    case 'circular':
      return Math.PI * (d.diametro / 2) ** 2 * n;
    case 'muro_vanos':
      return Math.max(0, d.largo * d.alto * n - (d.vanos || 0));
    case 'enfierradura':
      return ((d.diametro * d.diametro) / 162) * d.longitud * n;
    case 'personalizado':
      // Computed by the caller via evaluarFormula() instead — there's no fixed formula per
      // tipo here, it depends on the user's own field names and formula for this instance.
      return 0;
    default:
      return 0;
  }
}

export function formatDatosMedicion(
  tipo: TipoElementoMedicion,
  datos: Record<string, number>,
  unidad: string,
  camposPersonalizados?: CampoPersonalizado[],
): string {
  if (tipo === 'personalizado') {
    const campos = camposPersonalizados ?? Object.keys(datos).map((key) => ({ key, label: key }));
    return campos.map((c) => `${c.label || c.key}: ${datos[c.key]?.toLocaleString('es-CL') ?? 0}`).join(' · ');
  }
  return camposDelTipo(tipo, unidad)
    .map((c) => `${c.label.replace(/\s*\(.*\)/, '')}: ${datos[c.key]?.toLocaleString('es-CL') ?? 0}`)
    .join(' · ');
}

/** Turns a saved medición's parsed dimensions back into the string-keyed "campos" shape
 * FiguraMedidas expects, so a past measurement's cota drawing can be redrawn from exactly
 * what was saved, not just its text summary. */
export function camposDesdeDatos(datos: Record<string, number>): Record<string, string> {
  return Object.fromEntries(Object.entries(datos).map(([k, v]) => [k, v.toLocaleString('es-CL')]));
}

/** Compact one-line rendering of a measurement's dimensions, for contexts (like Nuevo
 * Parte's Tareas y Avances) that only have room to show the element's shape at a glance
 * rather than the full labeled breakdown from formatDatosMedicion. */
export function formatDimensionesCompacto(
  tipo: TipoElementoMedicion,
  datos: Record<string, number>,
  camposPersonalizados?: CampoPersonalizado[],
): string {
  const n = (v?: number) => (v ?? 0).toLocaleString('es-CL');
  switch (tipo) {
    case 'rectangular': {
      const partes = [n(datos.largo)];
      if (datos.ancho !== undefined) partes.push(n(datos.ancho));
      if (datos.alto !== undefined) partes.push(n(datos.alto));
      const base = `${partes.join(' × ')} m`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'trapezoidal': {
      const base = `${n(datos.baseMayor)}/${n(datos.baseMenor)} × ${n(datos.alto)} m, largo ${n(datos.largo)} m`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'cilindrico': {
      const base = `Ø${n(datos.diametro)} × ${n(datos.alto)} m`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'conico_truncado': {
      const base = `Ø${n(datos.diametroMayor)}/${n(datos.diametroMenor)} × ${n(datos.alto)} m`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'cuna': {
      const base = `${n(datos.base)} × ${n(datos.altura)} m, largo ${n(datos.largo)} m`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'triangular': {
      const base = `${n(datos.base)} × ${n(datos.altura)} m`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'circular': {
      const base = `Ø${n(datos.diametro)} m`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'muro_vanos': {
      const base = `${n(datos.largo)} × ${n(datos.alto)} m${datos.vanos ? ` (-${n(datos.vanos)} m² vanos)` : ''}`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'enfierradura':
      return `Ø${n(datos.diametro)}mm × ${n(datos.longitud)} m ×${n(datos.cantidad)} barras`;
    case 'personalizado': {
      const campos = camposPersonalizados ?? Object.keys(datos).map((key) => ({ key, label: key }));
      return campos.map((c) => `${c.label || c.key} ${n(datos[c.key])}`).join(' · ');
    }
    default:
      return '';
  }
}
