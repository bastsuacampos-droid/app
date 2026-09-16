import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { db } from '../db'

export default function NuevoAporte() {
  const { id } = useParams()
  const rendicionId = Number(id)
  const navigate = useNavigate()

  const [monto, setMonto] = useState('')
  const [fecha, setFecha] = useState(() => new Date().toISOString().slice(0, 10))
  const [nota, setNota] = useState('')
  const [guardando, setGuardando] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const montoNum = Number(monto)
    if (!montoNum || montoNum <= 0) return
    setGuardando(true)
    await db.aportes.add({
      rendicionId,
      fecha: new Date(fecha).toISOString(),
      monto: montoNum,
      nota: nota.trim() || 'Aporte',
    })
    navigate(`/rendicion/${rendicionId}`)
  }

  return (
    <div className="p-4">
      <h1 className="mb-4 text-lg font-bold text-gray-900">Registrar aporte</h1>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-gray-600">Monto recibido</span>
          <input
            type="number"
            inputMode="numeric"
            required
            autoFocus
            value={monto}
            onChange={(e) => setMonto(e.target.value)}
            placeholder="0"
            className="rounded-xl border border-gray-200 bg-white px-4 py-3 text-lg font-semibold"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-gray-600">Fecha</span>
          <input
            type="date"
            required
            value={fecha}
            onChange={(e) => setFecha(e.target.value)}
            className="rounded-xl border border-gray-200 bg-white px-4 py-3"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-gray-600">Nota (opcional)</span>
          <input
            type="text"
            value={nota}
            onChange={(e) => setNota(e.target.value)}
            placeholder="Ej: Efectivo entregado por jefatura"
            className="rounded-xl border border-gray-200 bg-white px-4 py-3"
          />
        </label>
        <button
          type="submit"
          disabled={guardando}
          className="mt-2 rounded-full bg-emerald-600 py-3 text-sm font-semibold text-white disabled:opacity-50"
        >
          Guardar aporte
        </button>
      </form>
    </div>
  )
}
