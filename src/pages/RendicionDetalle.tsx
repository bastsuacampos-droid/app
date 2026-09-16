import { useLiveQuery } from 'dexie-react-hooks'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { db, calcularTotales, cerrarRendicion } from '../db'
import BalanceCard from '../components/BalanceCard'
import GastoItem from '../components/GastoItem'
import { formatCLP, formatFecha } from '../utils/format'

export default function RendicionDetalle() {
  const { id } = useParams()
  const rendicionId = Number(id)
  const navigate = useNavigate()

  const rendicion = useLiveQuery(() => db.rendiciones.get(rendicionId), [rendicionId])
  const aportes = useLiveQuery(
    () => db.aportes.where('rendicionId').equals(rendicionId).reverse().sortBy('fecha'),
    [rendicionId],
    [],
  )
  const gastos = useLiveQuery(
    () => db.gastos.where('rendicionId').equals(rendicionId).reverse().sortBy('fecha'),
    [rendicionId],
    [],
  )

  if (!rendicion) return <div className="p-4 text-sm text-gray-400">Cargando…</div>

  const totales = calcularTotales(aportes ?? [], gastos ?? [])

  async function handleCerrar() {
    if (!confirm(`¿Cerrar "${rendicion!.nombre}"? Podrás abrir una nueva rendición después.`)) return
    await cerrarRendicion(rendicionId)
    navigate('/rendiciones')
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      <div>
        <h1 className="text-lg font-bold text-gray-900">{rendicion.nombre}</h1>
        <p className="text-xs text-gray-500">
          {rendicion.estado === 'abierta'
            ? `Abierta el ${formatFecha(rendicion.fechaApertura)}`
            : `${formatFecha(rendicion.fechaApertura)} — ${formatFecha(rendicion.fechaCierre!)}`}
        </p>
      </div>

      <BalanceCard saldo={totales.saldo} totalAportes={totales.totalAportes} totalGastos={totales.totalGastos} />

      {rendicion.estado === 'abierta' && (
        <div className="grid grid-cols-2 gap-3">
          <Link
            to={`/rendicion/${rendicionId}/nuevo-gasto`}
            className="flex flex-col items-center justify-center gap-1 rounded-xl bg-emerald-600 py-4 text-white shadow-sm"
          >
            <span className="text-2xl">📷</span>
            <span className="text-sm font-semibold">Agregar boleta</span>
          </Link>
          <Link
            to={`/rendicion/${rendicionId}/nuevo-aporte`}
            className="flex flex-col items-center justify-center gap-1 rounded-xl bg-white py-4 text-gray-800 shadow-sm"
          >
            <span className="text-2xl">💵</span>
            <span className="text-sm font-semibold">Registrar aporte</span>
          </Link>
        </div>
      )}

      {aportes && aportes.length > 0 && (
        <div>
          <h2 className="mb-2 text-sm font-semibold text-gray-700">Aportes recibidos</h2>
          <div className="flex flex-col gap-2">
            {aportes.map((a) => (
              <div key={a.id} className="flex items-center justify-between rounded-xl bg-white p-3 shadow-sm">
                <div>
                  <p className="text-sm font-medium text-gray-900">{a.nota || 'Aporte'}</p>
                  <p className="text-xs text-gray-500">{formatFecha(a.fecha)}</p>
                </div>
                <p className="text-sm font-semibold text-emerald-600">+{formatCLP(a.monto)}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      <div>
        <h2 className="mb-2 text-sm font-semibold text-gray-700">
          Gastos ({gastos?.length ?? 0})
        </h2>
        <div className="flex flex-col gap-2">
          {(gastos ?? []).length === 0 && (
            <p className="rounded-xl bg-white p-4 text-center text-sm text-gray-400 shadow-sm">
              Sin gastos registrados
            </p>
          )}
          {(gastos ?? []).map((g) => (
            <GastoItem key={g.id} gasto={g} />
          ))}
        </div>
      </div>

      {rendicion.estado === 'abierta' && (
        <button
          onClick={handleCerrar}
          className="mt-2 rounded-full border border-rose-200 bg-rose-50 py-3 text-sm font-semibold text-rose-600"
        >
          Cerrar esta rendición
        </button>
      )}
    </div>
  )
}
