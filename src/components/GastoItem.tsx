import { Link } from 'react-router-dom'
import type { Gasto } from '../types'
import { categoriaInfo } from '../types'
import { formatCLP, formatFecha } from '../utils/format'

export default function GastoItem({ gasto }: { gasto: Gasto }) {
  const cat = categoriaInfo(gasto.categoria)
  return (
    <Link
      to={`/gasto/${gasto.id}`}
      className="flex items-center gap-3 rounded-xl bg-white p-3 shadow-sm active:bg-gray-50"
    >
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gray-100 text-lg">
        {cat.icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-gray-900">{gasto.comercio || 'Gasto sin nombre'}</p>
        <p className="text-xs text-gray-500">
          {formatFecha(gasto.fecha)} · {cat.label}
          {gasto.metodoPago === 'propio' ? ' · plata propia' : ''}
        </p>
      </div>
      <p className="shrink-0 text-sm font-semibold text-gray-900">{formatCLP(gasto.monto)}</p>
    </Link>
  )
}
