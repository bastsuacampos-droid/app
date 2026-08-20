import type { TipoElementoMedicion } from '../types/models';

export interface MedicionInfo { tipo: TipoElementoMedicion; descripcion: string; datos: Record<string, number>; subtotal: number }
export interface NuevaPartidaDatos { nombre: string; unidad: string; cantidadContratada: number; avanceHoy: number; mediciones: MedicionInfo[] }

export const TIPO_ELEMENTO_LABEL: Record<TipoElementoMedicion, string> = {
  rectangular: 'Rectangular',
  trapezoidal: 'Sección trapezoidal',
  cilindrico: 'Cilíndrico',
  muro_vanos: 'Muro con vanos',
  enfierradura: 'Enfierradura',
};

/** Which element types make sense to compute for each unidad — a discrete unidad ("un", a
 * count of prefabricated pieces) has no geometry to calculate. */
export const TIPOS_POR_UNIDAD: Partial<Record<string, { value: TipoElementoMedicion; label: string }[]>> = {
  'm³': [
    { value: 'rectangular', label: 'Prisma rectangular' },
    { value: 'trapezoidal', label: 'Sección trapezoidal' },
    { value: 'cilindrico', label: 'Cilíndrico' },
  ],
  'm²': [
    { value: 'rectangular', label: 'Rectangular' },
    { value: 'muro_vanos', label: 'Muro (descuenta vanos)' },
  ],
  ml: [{ value: 'rectangular', label: 'Longitud simple' }],
  kg: [{ value: 'enfierradura', label: 'Enfierradura por diámetro' }],
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
    case 'muro_vanos':
      return Math.max(0, d.largo * d.alto * n - (d.vanos || 0));
    case 'enfierradura':
      return ((d.diametro * d.diametro) / 162) * d.longitud * n;
    default:
      return 0;
  }
}

export function formatDatosMedicion(tipo: TipoElementoMedicion, datos: Record<string, number>, unidad: string): string {
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
export function formatDimensionesCompacto(tipo: TipoElementoMedicion, datos: Record<string, number>): string {
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
    case 'muro_vanos': {
      const base = `${n(datos.largo)} × ${n(datos.alto)} m${datos.vanos ? ` (-${n(datos.vanos)} m² vanos)` : ''}`;
      return datos.cantidad > 1 ? `${base} ×${n(datos.cantidad)}` : base;
    }
    case 'enfierradura':
      return `Ø${n(datos.diametro)}mm × ${n(datos.longitud)} m ×${n(datos.cantidad)} barras`;
    default:
      return '';
  }
}
