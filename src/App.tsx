import { Suspense, lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import Nav from './components/Nav'
import Dashboard from './pages/Dashboard'
import RendicionDetalle from './pages/RendicionDetalle'
import NuevoAporte from './pages/NuevoAporte'
import Rendiciones from './pages/Rendiciones'
import GastoDetalle from './pages/GastoDetalle'

const NuevoGasto = lazy(() => import('./pages/NuevoGasto'))
const Resumen = lazy(() => import('./pages/Resumen'))

export default function App() {
  return (
    <>
      <main className="flex-1 overflow-y-auto pb-20">
        <Suspense fallback={<div className="p-4 text-sm text-gray-400">Cargando…</div>}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/rendicion/:id" element={<RendicionDetalle />} />
            <Route path="/rendicion/:id/nuevo-gasto" element={<NuevoGasto />} />
            <Route path="/rendicion/:id/nuevo-aporte" element={<NuevoAporte />} />
            <Route path="/gasto/:id" element={<GastoDetalle />} />
            <Route path="/rendiciones" element={<Rendiciones />} />
            <Route path="/resumen" element={<Resumen />} />
          </Routes>
        </Suspense>
      </main>
      <Nav />
    </>
  )
}
