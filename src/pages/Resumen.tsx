import { useLiveQuery } from 'dexie-react-hooks'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { db } from '../db'
import { CATEGORIAS } from '../types'
import { formatCLP } from '../utils/format'

const COLORES = ['#059669', '#0ea5e9', '#f59e0b', '#8b5cf6', '#ec4899', '#14b8a6', '#f43f5e', '#64748b']

export default function Resumen() {
  const rendiciones = useLiveQuery(() => db.rendiciones.toArray(), [], [])
  const aportes = useLiveQuery(() => db.aportes.toArray(), [], [])
  const gastos = useLiveQuery(() => db.gastos.toArray(), [], [])

  const totalAportes = (aportes ?? []).reduce((s, a) => s + a.monto, 0)
  const totalGastos = (gastos ?? []).reduce((s, g) => s + g.monto, 0)
  const saldoGlobal = totalAportes - totalGastos
  const totalPropio = (gastos ?? []).filter((g) => g.metodoPago === 'propio').reduce((s, g) => s + g.monto, 0)

  const porCategoria = CATEGORIAS.map((c) => ({
    categoria: c,
    nombre: c.label,
    total: (gastos ?? []).filter((g) => g.categoria === c.id).reduce((s, g) => s + g.monto, 0),
  }))
    .filter((c) => c.total > 0)
    .sort((a, b) => b.total - a.total)

  const cerradas = (rendiciones ?? []).filter((r) => r.estado === 'cerrada').length
  const abiertas = (rendiciones ?? []).filter((r) => r.estado === 'abierta').length

  return (
    <div className="flex flex-col gap-4 p-4">
      <h1 className="text-lg font-bold text-gray-900">Resumen general</h1>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <p className="text-xs text-gray-500">Total aportado</p>
          <p className="text-lg font-bold text-gray-900">{formatCLP(totalAportes)}</p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <p className="text-xs text-gray-500">Total gastado</p>
          <p className="text-lg font-bold text-gray-900">{formatCLP(totalGastos)}</p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <p className="text-xs text-gray-500">Puesto de tu bolsillo</p>
          <p className="text-lg font-bold text-gray-900">{formatCLP(totalPropio)}</p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <p className="text-xs text-gray-500">Saldo global</p>
          <p className={`text-lg font-bold ${saldoGlobal < 0 ? 'text-emerald-600' : saldoGlobal > 0 ? 'text-rose-600' : 'text-gray-900'}`}>
            {formatCLP(Math.abs(saldoGlobal))}
          </p>
        </div>
      </div>

      <div className="rounded-xl bg-white p-4 shadow-sm">
        <p className="text-xs text-gray-500">Rendiciones</p>
        <p className="text-sm font-medium text-gray-800">
          {abiertas} abierta{abiertas === 1 ? '' : 's'} · {cerradas} cerrada{cerradas === 1 ? '' : 's'}
        </p>
      </div>

      {porCategoria.length > 0 && (
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <p className="mb-2 text-sm font-semibold text-gray-700">Gastos por categoría</p>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={porCategoria}
                  dataKey="total"
                  nameKey="nombre"
                  innerRadius={45}
                  outerRadius={75}
                  paddingAngle={2}
                >
                  {porCategoria.map((_, i) => (
                    <Cell key={i} fill={COLORES[i % COLORES.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => formatCLP(Number(value))} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="mt-2 flex flex-col gap-1.5">
            {porCategoria.map((c, i) => (
              <div key={c.categoria.id} className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full" style={{ background: COLORES[i % COLORES.length] }} />
                  <span className="text-gray-600">
                    {c.categoria.icon} {c.categoria.label}
                  </span>
                </div>
                <span className="font-semibold text-gray-800">{formatCLP(c.total)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {(gastos ?? []).length === 0 && (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-gray-400 shadow-sm">
          Aún no hay datos suficientes para mostrar el resumen
        </p>
      )}
    </div>
  )
}
