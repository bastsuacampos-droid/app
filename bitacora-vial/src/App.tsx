import { lazy, Suspense, useEffect, useState } from 'react';
import { Route, Routes, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Capacitor } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import { seedIfEmpty } from './lib/db';
import { useSettings } from './lib/useSettings';
import { BottomNav } from './components/BottomNav';
import { ErrorBoundary } from './components/ErrorBoundary';

import { OnboardingPage } from './features/onboarding/OnboardingPage';
import { DashboardPage } from './features/dashboard/DashboardPage';

// Every other route is lazy — each becomes its own chunk instead of all 15+ screens (plus
// jsPDF/pdf.js-adjacent code they pull in) landing in one ~1.3MB bundle parsed before the very
// first screen can paint. Dashboard and Onboarding stay eager since one of them is always the
// first thing rendered, so lazy-loading them would just move the wait, not remove it.
const NuevoPartePage = lazy(() => import('./features/partes/NuevoPartePage').then((m) => ({ default: m.NuevoPartePage })));
const VerPartePage = lazy(() => import('./features/partes/VerPartePage').then((m) => ({ default: m.VerPartePage })));
const CubicacionPage = lazy(() => import('./features/cubicacion/CubicacionPage').then((m) => ({ default: m.CubicacionPage })));
const AsistenciaPage = lazy(() => import('./features/asistencia/AsistenciaPage').then((m) => ({ default: m.AsistenciaPage })));
const CuadrillaPage = lazy(() => import('./features/cuadrilla/CuadrillaPage').then((m) => ({ default: m.CuadrillaPage })));
const FrentesPage = lazy(() => import('./features/frentes/FrentesPage').then((m) => ({ default: m.FrentesPage })));
const FotosPage = lazy(() => import('./features/fotos/FotosPage').then((m) => ({ default: m.FotosPage })));
const EditorFotoPage = lazy(() => import('./features/fotos/EditorFotoPage').then((m) => ({ default: m.EditorFotoPage })));
const HistorialPage = lazy(() => import('./features/historial/HistorialPage').then((m) => ({ default: m.HistorialPage })));
const MasPage = lazy(() => import('./features/mas/MasPage').then((m) => ({ default: m.MasPage })));
const HorasExtraPage = lazy(() => import('./features/horasExtra/HorasExtraPage').then((m) => ({ default: m.HorasExtraPage })));
const DocumentosPage = lazy(() => import('./features/documentos/DocumentosPage').then((m) => ({ default: m.DocumentosPage })));
const ConfiguracionPage = lazy(() => import('./features/configuracion/ConfiguracionPage').then((m) => ({ default: m.ConfiguracionPage })));

const SCREENS_WITH_NAV = ['/', '/historial', '/mas'];

/** Blank-but-themed placeholder while a lazy route chunk downloads — avoids a flash of the
 * host page's default background (white) between screens on a slow connection. */
function RouteFallback() {
  return <div style={{ background: 'var(--bg)', height: '100%' }} />;
}

export default function App() {
  const [ready, setReady] = useState(false);
  const settings = useSettings();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    seedIfEmpty()
      .catch((err) => {
        // eslint-disable-next-line no-console
        console.error('seedIfEmpty failed', err, (err as { failures?: unknown }).failures);
      })
      .finally(() => setReady(true));
  }, []);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', settings.tema);
  }, [settings.tema]);

  // El botón de retroceso físico/gesto de Android cierra la app por defecto (Capacitor 8 ya no
  // navega el historial del WebView automáticamente). Si hay historial de navegación dentro de la
  // app lo recorremos primero; solo se cierra la app desde una pantalla raíz.
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const listener = CapacitorApp.addListener('backButton', () => {
      const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0;
      if (idx > 0) {
        navigate(-1);
      } else {
        CapacitorApp.exitApp();
      }
    });
    return () => { listener.then((l) => l.remove()); };
  }, [navigate]);

  if (!ready) return null;

  if (!settings.onboardingComplete) {
    return (
      <div className="app-shell">
        <Routes>
          <Route path="*" element={<OnboardingPage />} />
        </Routes>
      </div>
    );
  }

  return (
    <div className="app-shell">
      {/* Keyed by pathname: a crash on one screen no longer takes down the whole app (the
       * previous single app-wide boundary in main.tsx did) — navigating anywhere else remounts
       * this boundary fresh, and the bottom nav / header stay outside it so there's always a
       * way out even from a crashed screen. */}
      <ErrorBoundary key={location.pathname}>
        <Suspense fallback={<RouteFallback />}>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/nuevo-parte" element={<NuevoPartePage />} />
            <Route path="/parte/:parteId" element={<VerPartePage />} />
            <Route path="/cubicacion" element={<CubicacionPage />} />
            <Route path="/asistencia" element={<AsistenciaPage />} />
            <Route path="/cuadrilla" element={<CuadrillaPage />} />
            <Route path="/frentes" element={<FrentesPage />} />
            <Route path="/fotos" element={<FotosPage />} />
            <Route path="/fotos/:fotoId/editar" element={<EditorFotoPage />} />
            <Route path="/historial" element={<HistorialPage />} />
            <Route path="/mas" element={<MasPage />} />
            <Route path="/horas-extra" element={<HorasExtraPage />} />
            <Route path="/documentos" element={<DocumentosPage />} />
            <Route path="/configuracion" element={<ConfiguracionPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </ErrorBoundary>
      <NavGate />
    </div>
  );
}

/** Renders the bottom nav only on its 6 top-level screens (not on drill-down pages). */
function NavGate() {
  const location = useLocation();
  if (!SCREENS_WITH_NAV.includes(location.pathname)) return null;
  return <BottomNav />;
}
