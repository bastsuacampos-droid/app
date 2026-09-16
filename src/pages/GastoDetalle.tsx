import { useEffect, useMemo } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { useNavigate, useParams } from 'react-router-dom'
import { db } from '../db'
import { categoriaInfo } from '../types'
import { formatCLP, formatFechaHora } from '../utils/format'

export default function GastoDetalle() {
  const { id } = useParams()
  const gastoId = Number(id)
  const navigate = useNavigate()
  const gasto = useLiveQuery(() => db.gastos.get(gastoId), [gastoId])
  const imagenUrl = useMemo(() => (gasto?.imagen ? URL.createObjectURL(gasto.imagen) : null), [gasto?.imagen])

  useEffect(() => {
    return () => {
      if (imagenUrl) URL.revokeObjectURL(imagenUrl)
    }
  }, [imagenUrl])

  if (!gasto) return <div className="p-4 text-sm text-gray-400">Cargando…</div>

  const cat = categoriaInfo(gasto.categoria)

  async function handleEliminar() {
    if (!confirm('¿Eliminar este gasto?')) return
    await db.gastos.delete(gastoId)
    navigate(`/rendicion/${gasto!.rendicionId}`)
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      {imagenUrl && (
        <img src={imagenUrl} alt="Boleta" className="w-full rounded-xl bg-white object-contain shadow-sm" />
      )}
      <div className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <p className="text-2xl font-bold text-gray-900">{formatCLP(gasto.monto)}</p>
          <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold">
            {cat.icon} {cat.label}
          </span>
        </div>
        <p className="mt-2 text-sm font-medium text-gray-700">{gasto.comercio}</p>
        <p className="text-xs text-gray-500">{formatFechaHora(gasto.fecha)}</p>
        <p className="mt-1 text-xs text-gray-500">
          Pagado con: {gasto.metodoPago === 'propio' ? 'dinero propio' : 'dinero asignado'}
        </p>
        {gasto.descripcion && <p className="mt-2 text-sm text-gray-600">{gasto.descripcion}</p>}
      </div>

      <button
        onClick={handleEliminar}
        className="rounded-full border border-rose-200 bg-rose-50 py-3 text-sm font-semibold text-rose-600"
      >
        Eliminar gasto
      </button>
    </div>
  )
}
