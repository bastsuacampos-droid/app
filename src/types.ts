export type Categoria =
  | 'alimentacion'
  | 'transporte'
  | 'combustible'
  | 'alojamiento'
  | 'oficina'
  | 'comunicaciones'
  | 'salud'
  | 'otros'

export const CATEGORIAS: { id: Categoria; label: string; icon: string }[] = [
  { id: 'alimentacion', label: 'Alimentación', icon: '🍽️' },
  { id: 'transporte', label: 'Transporte', icon: '🚕' },
  { id: 'combustible', label: 'Combustible', icon: '⛽' },
  { id: 'alojamiento', label: 'Alojamiento', icon: '🏨' },
  { id: 'oficina', label: 'Materiales de oficina', icon: '🖇️' },
  { id: 'comunicaciones', label: 'Comunicaciones', icon: '📶' },
  { id: 'salud', label: 'Salud', icon: '💊' },
  { id: 'otros', label: 'Otros', icon: '🧾' },
]

export function categoriaInfo(id: Categoria) {
  return CATEGORIAS.find((c) => c.id === id) ?? CATEGORIAS[CATEGORIAS.length - 1]
}

export interface Rendicion {
  id?: number
  nombre: string
  fechaApertura: string // ISO date
  fechaCierre: string | null
  estado: 'abierta' | 'cerrada'
  notas?: string
}

export interface Aporte {
  id?: number
  rendicionId: number
  fecha: string // ISO date
  monto: number
  nota?: string
}

export interface Gasto {
  id?: number
  rendicionId: number
  fecha: string // ISO date
  monto: number
  categoria: Categoria
  comercio: string
  descripcion?: string
  metodoPago: 'asignado' | 'propio'
  imagen?: Blob
  ocrTexto?: string
  creadoEn: string // ISO datetime
}

export interface RendicionTotales {
  totalAportes: number
  totalGastos: number
  saldo: number // totalAportes - totalGastos. Positivo = sobra plata (a favor de la empresa). Negativo = empleado puso plata (a favor del empleado)
  cantidadGastos: number
}
