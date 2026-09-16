import { useLiveQuery } from 'dexie-react-hooks'
import { Link, useNavigate } from 'react-router-dom'
import { db, calcularTotales } from '../db'
import BalanceCard from '../components/BalanceCard'
import GastoItem from '../components/GastoItem'
import { formatFecha } from '../utils/format'

export default function Dashboard() {
  const navigate = useNavigate()
  const rendicionAbierta = useLiveQuery(() => db.rendiciones.where('estado').equals('abierta').first())

  const aportes = useLiveQuery(
    () => (rendicionAbierta?.id ? db.aportes.where('rendicionId').equals(rendicionAbierta.id).toArray() : []),
    [rendicionAbierta?.id],
    [],
  )
  const gastos = useLiveQuery(
    () =>
      rendicionAbierta?.id
        ? db.gastos.where('rendicionId').equals(rendicionAbierta.id).reverse().sortBy('fecha')
        : [],
    [rendicionAbierta?.id],
    [],
  )

  if (rendicionAbierta === undefined) {
    return <div className="p-4 text-sm text-gray-400">Cargando…</div>
  }

  if (!rendicionAbierta) {
    return (
      <div className="flex flex-col items-center gap-4 p-6 pt-16 text-center">
        <p className="text-lg font-semibold">No tienes una rendición abierta</p>
        <button
          onClick={() => navigate('/rendiciones')}
          className="rounded-full bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white"
        >
          Abrir una rendición
        </button>
      </div>
    )
  }

  const totales = calcularTotales(aportes ?? [], gastos ?? [])

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-gray-900">{rendicionAbierta.nombre}</h1>
          <p className="text-xs text-gray-500">Abierta el {formatFecha(rendicionAbierta.fechaApertura)}</p>
        </div>
        <Link
          to={`/rendicion/${rendicionAbierta.id}`}
          className="rounded-full bg-gray-100 px-3 py-1.5 text-xs font-semibold text-gray-700"
        >
          Ver detalle
        </Link>
      </div>

      <BalanceCard saldo={totales.saldo} totalAportes={totales.totalAportes} totalGastos={totales.totalGastos} />

      <div className="grid grid-cols-2 gap-3">
        <Link
          to={`/rendicion/${rendicionAbierta.id}/nuevo-gasto`}
          className="flex flex-col items-center justify-center gap-1 rounded-xl bg-emerald-600 py-4 text-white shadow-sm active:opacity-90"
        >
          <span className="text-2xl">📷</span>
          <span className="text-sm font-semibold">Agregar boleta</span>
        </Link>
        <Link
          to={`/rendicion/${rendicionAbierta.id}/nuevo-aporte`}
          className="flex flex-col items-center justify-center gap-1 rounded-xl bg-white py-4 text-gray-800 shadow-sm active:bg-gray-50"
        >
          <span className="text-2xl">💵</span>
          <span className="text-sm font-semibold">Registrar aporte</span>
        </Link>
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-700">Últimos gastos</h2>
          <Link to={`/rendicion/${rendicionAbierta.id}`} className="text-xs font-medium text-emerald-600">
            Ver todos
          </Link>
        </div>
        <div className="flex flex-col gap-2">
          {(gastos ?? []).length === 0 && (
            <p className="rounded-xl bg-white p-4 text-center text-sm text-gray-400 shadow-sm">
              Aún no registras gastos en esta rendición
            </p>
          )}
          {(gastos ?? []).slice(0, 5).map((g) => (
            <GastoItem key={g.id} gasto={g} />
          ))}
        </div>
      </div>
    </div>
  )
}
