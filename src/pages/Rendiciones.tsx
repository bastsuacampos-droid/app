import { useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { Link, useNavigate } from 'react-router-dom'
import { db, abrirNuevaRendicion, obtenerRendicionAbierta, calcularTotales } from '../db'
import { formatCLP, formatFecha } from '../utils/format'
import type { Rendicion } from '../types'

function RendicionCard({ rendicion }: { rendicion: Rendicion }) {
  const aportes = useLiveQuery(
    () => db.aportes.where('rendicionId').equals(rendicion.id!).toArray(),
    [rendicion.id],
    [],
  )
  const gastos = useLiveQuery(
    () => db.gastos.where('rendicionId').equals(rendicion.id!).toArray(),
    [rendicion.id],
    [],
  )
  const totales = calcularTotales(aportes ?? [], gastos ?? [])
  const aFavorEmpleado = totales.saldo < 0

  return (
    <Link
      to={`/rendicion/${rendicion.id}`}
      className="flex items-center justify-between rounded-xl bg-white p-4 shadow-sm active:bg-gray-50"
    >
      <div>
        <div className="flex items-center gap-2">
          <p className="text-sm font-semibold text-gray-900">{rendicion.nombre}</p>
          {rendicion.estado === 'abierta' && (
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
              ABIERTA
            </span>
          )}
        </div>
        <p className="text-xs text-gray-500">
          {formatFecha(rendicion.fechaApertura)}
          {rendicion.fechaCierre ? ` — ${formatFecha(rendicion.fechaCierre)}` : ''}
        </p>
        <p className="text-xs text-gray-400">{totales.cantidadGastos} gastos registrados</p>
      </div>
      <p className={`text-sm font-bold ${totales.saldo === 0 ? 'text-gray-500' : aFavorEmpleado ? 'text-emerald-600' : 'text-rose-600'}`}>
        {formatCLP(Math.abs(totales.saldo))}
      </p>
    </Link>
  )
}

export default function Rendiciones() {
  const navigate = useNavigate()
  const rendiciones = useLiveQuery(() => db.rendiciones.orderBy('fechaApertura').reverse().toArray(), [], [])
  const [mostrarForm, setMostrarForm] = useState(false)
  const [nombre, setNombre] = useState('')
  const [montoInicial, setMontoInicial] = useState('')

  async function handleAbrir(e: React.FormEvent) {
    e.preventDefault()
    const abierta = await obtenerRendicionAbierta()
    if (abierta) {
      const ok = confirm(`Esto cerrará la rendición actual "${abierta.nombre}". ¿Continuar?`)
      if (!ok) return
    }
    const nuevoId = await abrirNuevaRendicion(
      nombre.trim() || `Rendición ${(rendiciones?.length ?? 0) + 1}`,
      Number(montoInicial) || undefined,
    )
    setMostrarForm(false)
    setNombre('')
    setMontoInicial('')
    navigate(`/rendicion/${nuevoId}`)
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-bold text-gray-900">Rendiciones</h1>
        <button
          onClick={() => setMostrarForm((v) => !v)}
          className="rounded-full bg-emerald-600 px-4 py-2 text-xs font-semibold text-white"
        >
          + Nueva rendición
        </button>
      </div>

      {mostrarForm && (
        <form onSubmit={handleAbrir} className="flex flex-col gap-3 rounded-xl bg-white p-4 shadow-sm">
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-gray-600">Nombre de la rendición</span>
            <input
              type="text"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              placeholder="Ej: Viaje a Concepción / Octubre 2026"
              className="rounded-xl border border-gray-200 px-4 py-2.5"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-gray-600">Monto inicial asignado (opcional)</span>
            <input
              type="number"
              inputMode="numeric"
              value={montoInicial}
              onChange={(e) => setMontoInicial(e.target.value)}
              placeholder="0"
              className="rounded-xl border border-gray-200 px-4 py-2.5"
            />
          </label>
          <button type="submit" className="rounded-full bg-emerald-600 py-2.5 text-sm font-semibold text-white">
            Abrir rendición
          </button>
        </form>
      )}

      <div className="flex flex-col gap-2">
        {(rendiciones ?? []).length === 0 && <p className="text-sm text-gray-400">No hay rendiciones aún</p>}
        {(rendiciones ?? []).map((r) => (
          <RendicionCard key={r.id} rendicion={r} />
        ))}
      </div>
    </div>
  )
}
