import Dexie, { type Table } from 'dexie'
import type { Aporte, Gasto, Rendicion } from './types'

class RendicionesDB extends Dexie {
  rendiciones!: Table<Rendicion, number>
  aportes!: Table<Aporte, number>
  gastos!: Table<Gasto, number>

  constructor() {
    super('rendiciones-db')
    this.version(1).stores({
      rendiciones: '++id, estado, fechaApertura',
      aportes: '++id, rendicionId, fecha',
      gastos: '++id, rendicionId, fecha, categoria',
    })
  }
}

export const db = new RendicionesDB()

export async function crearRendicionInicialSiNoExiste() {
  const count = await db.rendiciones.count()
  if (count === 0) {
    await db.rendiciones.add({
      nombre: 'Rendición 1',
      fechaApertura: new Date().toISOString(),
      fechaCierre: null,
      estado: 'abierta',
    })
  }
}

export async function obtenerRendicionAbierta(): Promise<Rendicion | undefined> {
  return db.rendiciones.where('estado').equals('abierta').first()
}

export async function cerrarRendicion(id: number) {
  await db.rendiciones.update(id, {
    estado: 'cerrada',
    fechaCierre: new Date().toISOString(),
  })
}

export async function abrirNuevaRendicion(nombre: string, montoInicial?: number) {
  const abierta = await obtenerRendicionAbierta()
  if (abierta && abierta.id) {
    await cerrarRendicion(abierta.id)
  }
  const nuevoId = await db.rendiciones.add({
    nombre,
    fechaApertura: new Date().toISOString(),
    fechaCierre: null,
    estado: 'abierta',
  })
  if (montoInicial && montoInicial > 0) {
    await db.aportes.add({
      rendicionId: nuevoId,
      fecha: new Date().toISOString(),
      monto: montoInicial,
      nota: 'Monto inicial asignado',
    })
  }
  return nuevoId
}

export function calcularTotales(aportes: Aporte[], gastos: Gasto[]) {
  const totalAportes = aportes.reduce((s, a) => s + a.monto, 0)
  const totalGastos = gastos.reduce((s, g) => s + g.monto, 0)
  return {
    totalAportes,
    totalGastos,
    saldo: totalAportes - totalGastos,
    cantidadGastos: gastos.length,
  }
}
