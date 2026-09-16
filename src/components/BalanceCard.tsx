import { formatCLP } from '../utils/format'

export default function BalanceCard({
  saldo,
  totalAportes,
  totalGastos,
}: {
  saldo: number
  totalAportes: number
  totalGastos: number
}) {
  const aFavorEmpleado = saldo < 0
  const equilibrado = saldo === 0

  return (
    <div
      className={`rounded-2xl p-5 text-white shadow-sm ${
        equilibrado ? 'bg-slate-600' : aFavorEmpleado ? 'bg-emerald-600' : 'bg-rose-600'
      }`}
    >
      <p className="text-sm opacity-80">
        {equilibrado ? 'Saldo cuadrado' : aFavorEmpleado ? 'Saldo a tu favor' : 'Debes devolver a la empresa'}
      </p>
      <p className="mt-1 text-3xl font-bold tracking-tight">{formatCLP(Math.abs(saldo))}</p>
      <p className="mt-1 text-xs opacity-80">
        {aFavorEmpleado
          ? 'Pusiste plata de tu bolsillo, la empresa te la debe'
          : equilibrado
            ? 'Los aportes cubren exactamente tus gastos'
            : 'Te sobró dinero de lo asignado'}
      </p>
      <div className="mt-4 flex justify-between border-t border-white/20 pt-3 text-xs">
        <div>
          <p className="opacity-70">Aportes recibidos</p>
          <p className="font-semibold">{formatCLP(totalAportes)}</p>
        </div>
        <div className="text-right">
          <p className="opacity-70">Gastos registrados</p>
          <p className="font-semibold">{formatCLP(totalGastos)}</p>
        </div>
      </div>
    </div>
  )
}
