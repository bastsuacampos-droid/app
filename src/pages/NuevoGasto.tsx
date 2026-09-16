import { useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { db } from '../db'
import { CATEGORIAS, type Categoria } from '../types'
import { reconocerBoleta } from '../utils/ocr'

type Estado = 'inicial' | 'analizando' | 'listo'

export default function NuevoGasto() {
  const { id } = useParams()
  const rendicionId = Number(id)
  const navigate = useNavigate()
  const inputCamaraRef = useRef<HTMLInputElement>(null)
  const inputGaleriaRef = useRef<HTMLInputElement>(null)

  const [estado, setEstado] = useState<Estado>('inicial')
  const [imagen, setImagen] = useState<Blob | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [progreso, setProgreso] = useState(0)
  const [ocrTexto, setOcrTexto] = useState('')

  const [monto, setMonto] = useState('')
  const [fecha, setFecha] = useState(() => new Date().toISOString().slice(0, 10))
  const [comercio, setComercio] = useState('')
  const [categoria, setCategoria] = useState<Categoria>('otros')
  const [metodoPago, setMetodoPago] = useState<'asignado' | 'propio'>('asignado')
  const [descripcion, setDescripcion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function procesarImagen(file: File) {
    setError(null)
    setImagen(file)
    setPreviewUrl(URL.createObjectURL(file))
    setEstado('analizando')
    setProgreso(0)
    try {
      const datos = await reconocerBoleta(file, setProgreso)
      setOcrTexto(datos.texto)
      if (datos.monto) setMonto(String(Math.round(datos.monto)))
      if (datos.fecha) setFecha(datos.fecha.slice(0, 10))
      if (datos.comercio) setComercio(datos.comercio)
      setCategoria(datos.categoria)
    } catch (err) {
      console.error(err)
      setError('No se pudo leer automáticamente la boleta. Completa los datos manualmente.')
    } finally {
      setEstado('listo')
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) procesarImagen(file)
    e.target.value = ''
  }

  async function handleGuardar(e: React.FormEvent) {
    e.preventDefault()
    const montoNum = Number(monto)
    if (!montoNum || montoNum <= 0) return
    setGuardando(true)
    await db.gastos.add({
      rendicionId,
      fecha: new Date(fecha).toISOString(),
      monto: montoNum,
      categoria,
      comercio: comercio.trim() || 'Sin especificar',
      descripcion: descripcion.trim() || undefined,
      metodoPago,
      imagen: imagen ?? undefined,
      ocrTexto: ocrTexto || undefined,
      creadoEn: new Date().toISOString(),
    })
    navigate(`/rendicion/${rendicionId}`)
  }

  if (estado === 'inicial') {
    return (
      <div className="flex flex-col gap-4 p-4">
        <h1 className="text-lg font-bold text-gray-900">Agregar boleta o factura</h1>
        <p className="text-sm text-gray-500">
          Toma una foto y la app intentará leer el monto, la fecha, el comercio y el tipo de gasto automáticamente.
        </p>
        <button
          onClick={() => inputCamaraRef.current?.click()}
          className="flex flex-col items-center justify-center gap-2 rounded-2xl bg-emerald-600 py-10 text-white shadow-sm"
        >
          <span className="text-4xl">📷</span>
          <span className="text-sm font-semibold">Tomar foto de la boleta</span>
        </button>
        <button
          onClick={() => inputGaleriaRef.current?.click()}
          className="rounded-full bg-white py-3 text-sm font-semibold text-gray-700 shadow-sm"
        >
          Elegir imagen de la galería
        </button>
        <button
          onClick={() => setEstado('listo')}
          className="text-center text-xs font-medium text-gray-400 underline"
        >
          Ingresar gasto manualmente sin foto
        </button>
        <input
          ref={inputCamaraRef}
          type="file"
          accept="image/*"
          capture="environment"
          className="hidden"
          onChange={handleFileChange}
        />
        <input
          ref={inputGaleriaRef}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={handleFileChange}
        />
      </div>
    )
  }

  if (estado === 'analizando') {
    return (
      <div className="flex flex-col items-center gap-4 p-6 pt-16 text-center">
        {previewUrl && (
          <img src={previewUrl} alt="Boleta" className="max-h-64 rounded-xl object-contain shadow-sm" />
        )}
        <p className="text-sm font-semibold text-gray-700">Leyendo boleta…</p>
        <div className="h-2 w-full max-w-xs overflow-hidden rounded-full bg-gray-200">
          <div
            className="h-full bg-emerald-600 transition-all"
            style={{ width: `${Math.round(progreso * 100)}%` }}
          />
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      <h1 className="text-lg font-bold text-gray-900">Confirma los datos</h1>
      {previewUrl && (
        <img src={previewUrl} alt="Boleta" className="max-h-48 w-full rounded-xl object-contain bg-white shadow-sm" />
      )}
      {error && <p className="rounded-lg bg-amber-50 p-2 text-xs text-amber-700">{error}</p>}

      <form onSubmit={handleGuardar} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-gray-600">Monto</span>
          <input
            type="number"
            inputMode="numeric"
            required
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
          <span className="text-xs font-medium text-gray-600">Comercio</span>
          <input
            type="text"
            value={comercio}
            onChange={(e) => setComercio(e.target.value)}
            placeholder="Nombre del local"
            className="rounded-xl border border-gray-200 bg-white px-4 py-3"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-gray-600">Categoría</span>
          <select
            value={categoria}
            onChange={(e) => setCategoria(e.target.value as Categoria)}
            className="rounded-xl border border-gray-200 bg-white px-4 py-3"
          >
            {CATEGORIAS.map((c) => (
              <option key={c.id} value={c.id}>
                {c.icon} {c.label}
              </option>
            ))}
          </select>
        </label>

        <div className="flex flex-col gap-1">
          <span className="text-xs font-medium text-gray-600">¿Con qué dinero pagaste?</span>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setMetodoPago('asignado')}
              className={`rounded-xl border py-2.5 text-sm font-semibold ${
                metodoPago === 'asignado'
                  ? 'border-emerald-600 bg-emerald-50 text-emerald-700'
                  : 'border-gray-200 bg-white text-gray-500'
              }`}
            >
              Dinero asignado
            </button>
            <button
              type="button"
              onClick={() => setMetodoPago('propio')}
              className={`rounded-xl border py-2.5 text-sm font-semibold ${
                metodoPago === 'propio'
                  ? 'border-emerald-600 bg-emerald-50 text-emerald-700'
                  : 'border-gray-200 bg-white text-gray-500'
              }`}
            >
              Mi dinero
            </button>
          </div>
        </div>

        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-gray-600">Descripción (opcional)</span>
          <textarea
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            rows={2}
            className="rounded-xl border border-gray-200 bg-white px-4 py-3"
          />
        </label>

        <button
          type="submit"
          disabled={guardando}
          className="mt-2 rounded-full bg-emerald-600 py-3 text-sm font-semibold text-white disabled:opacity-50"
        >
          Guardar gasto
        </button>
      </form>
    </div>
  )
}
