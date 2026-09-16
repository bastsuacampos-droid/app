import { createWorker } from 'tesseract.js'
import { detectarCategoria } from './categorize'
import type { Categoria } from '../types'

export interface DatosExtraidos {
  texto: string
  monto: number | null
  fecha: string | null // ISO date
  comercio: string | null
  categoria: Categoria
}

let workerPromise: ReturnType<typeof createWorker> | null = null

function getWorker(onProgress?: (progreso: number) => void) {
  if (!workerPromise) {
    workerPromise = createWorker('spa', 1, {
      logger: (m) => {
        if (m.status === 'recognizing text' && onProgress) onProgress(m.progress)
      },
    })
  }
  return workerPromise
}

export async function reconocerBoleta(
  imagen: Blob | File | string,
  onProgress?: (progreso: number) => void,
): Promise<DatosExtraidos> {
  const worker = await getWorker(onProgress)
  const { data } = await worker.recognize(imagen)
  if (onProgress) onProgress(1)
  const texto = data.text || ''
  return {
    texto,
    monto: extraerMonto(texto),
    fecha: extraerFecha(texto),
    comercio: extraerComercio(texto),
    categoria: detectarCategoria(texto),
  }
}

export async function liberarWorker() {
  if (workerPromise) {
    const worker = await workerPromise
    await worker.terminate()
    workerPromise = null
  }
}

function limpiarNumero(raw: string): number {
  let s = raw.trim()
  // Formats like 12.345 (thousand sep) or 12,345 or 12.345,67 or 1234.56
  const tieneComaYPunto = s.includes(',') && s.includes('.')
  if (tieneComaYPunto) {
    // Assume last separator is decimal
    const ultimaComa = s.lastIndexOf(',')
    const ultimoPunto = s.lastIndexOf('.')
    if (ultimaComa > ultimoPunto) {
      s = s.replace(/\./g, '').replace(',', '.')
    } else {
      s = s.replace(/,/g, '')
    }
  } else if (s.includes(',')) {
    // Could be thousands (1,234) or decimal (1,50)
    const partes = s.split(',')
    if (partes[partes.length - 1].length === 2) {
      s = partes.slice(0, -1).join('') + '.' + partes[partes.length - 1]
    } else {
      s = s.replace(/,/g, '')
    }
  } else if (s.includes('.')) {
    const partes = s.split('.')
    // Chilean format uses '.' as thousands separator (e.g. 12.990 = 12990)
    if (partes[partes.length - 1].length === 3) {
      s = s.replace(/\./g, '')
    }
  }
  const n = parseFloat(s)
  return Number.isFinite(n) ? n : NaN
}

const NUMERO_REGEX = /\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?|\d+/g

export function extraerMonto(texto: string): number | null {
  const lineas = texto.split(/\r?\n/)
  const candidatosPrioritarios: number[] = []
  const candidatosGenerales: number[] = []

  const palabrasTotal = /(total\s*a\s*pagar|monto\s*total|total\s*venta|total\s*neto|^total\b|total:?$)/i
  const palabrasExcluir = /(subtotal|iva|descuento|vuelto|efectivo\s*entregado|rut|folio|n[°º]\s*\d)/i

  for (const linea of lineas) {
    const lineaLower = linea.toLowerCase()
    if (palabrasExcluir.test(lineaLower) && !/total/.test(lineaLower)) continue
    const matches = linea.match(NUMERO_REGEX)
    if (!matches) continue
    for (const m of matches) {
      const valor = limpiarNumero(m)
      if (!Number.isFinite(valor) || valor <= 0) continue
      if (valor < 10 || valor > 50_000_000) continue
      if (palabrasTotal.test(lineaLower)) {
        candidatosPrioritarios.push(valor)
      } else if (!palabrasExcluir.test(lineaLower)) {
        candidatosGenerales.push(valor)
      }
    }
  }

  if (candidatosPrioritarios.length > 0) {
    return Math.max(...candidatosPrioritarios)
  }
  if (candidatosGenerales.length > 0) {
    return Math.max(...candidatosGenerales)
  }
  return null
}

const FECHA_REGEXES = [
  /(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})/, // dd/mm/yyyy
  /(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2})\b/, // dd/mm/yy
  /(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})/, // yyyy/mm/dd
]

export function extraerFecha(texto: string): string | null {
  for (const regex of FECHA_REGEXES) {
    const match = texto.match(regex)
    if (!match) continue
    let dia: number, mes: number, anio: number
    if (regex === FECHA_REGEXES[2]) {
      anio = parseInt(match[1], 10)
      mes = parseInt(match[2], 10)
      dia = parseInt(match[3], 10)
    } else {
      dia = parseInt(match[1], 10)
      mes = parseInt(match[2], 10)
      anio = parseInt(match[3], 10)
      if (anio < 100) anio += 2000
    }
    if (mes < 1 || mes > 12 || dia < 1 || dia > 31) continue
    const fecha = new Date(anio, mes - 1, dia)
    if (Number.isNaN(fecha.getTime())) continue
    if (fecha.getFullYear() < 2000 || fecha > new Date(Date.now() + 86400000)) continue
    return fecha.toISOString()
  }
  return null
}

export function extraerComercio(texto: string): string | null {
  const lineas = texto
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0)

  for (const linea of lineas.slice(0, 6)) {
    const soloLetras = linea.replace(/[^a-zA-ZÀ-ÿ\s]/g, '').trim()
    if (soloLetras.length >= 4 && /[a-zA-ZÀ-ÿ]/.test(soloLetras)) {
      if (/rut|boleta|factura|folio|fecha|hora|www\.|http/i.test(linea)) continue
      return linea.slice(0, 60)
    }
  }
  return null
}
