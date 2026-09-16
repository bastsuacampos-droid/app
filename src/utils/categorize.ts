import type { Categoria } from '../types'

const PALABRAS_CLAVE: Record<Categoria, string[]> = {
  combustible: [
    'copec',
    'shell',
    'petrobras',
    'esso',
    'bencina',
    'gasolina',
    'diesel',
    'combustible',
    'servicentro',
    'estacion de servicio',
  ],
  transporte: [
    'uber',
    'cabify',
    'didi',
    'taxi',
    'radio taxi',
    'peaje',
    'autopista',
    'estacionamiento',
    'parking',
    'metro',
    'micro',
    'bus',
    'pasaje',
    'transantiago',
    'red movilidad',
  ],
  alimentacion: [
    'restaurant',
    'restorán',
    'restauran',
    'cafe',
    'café',
    'comida',
    'almuerzo',
    'cena',
    'menu',
    'menú',
    'pizza',
    'sushi',
    'panaderia',
    'panadería',
    'supermercado',
    'jumbo',
    'lider',
    'líder',
    'santa isabel',
    'unimarc',
    'tottus',
    'mcdonald',
    'burger',
    'starbucks',
    'juan valdez',
  ],
  alojamiento: ['hotel', 'hostal', 'motel', 'residencial', 'booking', 'airbnb', 'hospedaje'],
  oficina: [
    'libreria',
    'librería',
    'papeleria',
    'papelería',
    'oficina',
    'imprenta',
    'toner',
    'tóner',
    'lapiz',
    'lápiz',
    'cuaderno',
  ],
  comunicaciones: ['movistar', 'entel', 'claro', 'wom', 'vtr', 'internet', 'telefonia', 'telefonía', 'recarga'],
  salud: ['farmacia', 'cruz verde', 'salcobrand', 'ahumada', 'clinica', 'clínica', 'medico', 'médico', 'consulta'],
  otros: [],
}

export function detectarCategoria(texto: string): Categoria {
  const t = texto.toLowerCase()
  for (const categoria of Object.keys(PALABRAS_CLAVE) as Categoria[]) {
    if (categoria === 'otros') continue
    for (const palabra of PALABRAS_CLAVE[categoria]) {
      if (t.includes(palabra)) return categoria
    }
  }
  return 'otros'
}
