import { useEffect, useState } from 'react';
import { Route, Routes, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Capacitor } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import { seedIfEmpty } from './lib/db';
import { useSettings } from './lib/useSettings';
import { BottomNav } from './components/BottomNav';

import { OnboardingPage } from './features/onboarding/OnboardingPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { NuevoPartePage } from './features/partes/NuevoPartePage';
import { CubicacionPage } from './features/cubicacion/CubicacionPage';
import { AsistenciaPage } from './features/asistencia/AsistenciaPage';
import { CuadrillaPage } from './features/cuadrilla/CuadrillaPage';
import { FrentesPage } from './features/frentes/FrentesPage';
import { FotosPage } from './features/fotos/FotosPage';
import { EditorFotoPage } from './features/fotos/EditorFotoPage';
import { HistorialPage } from './features/historial/HistorialPage';
import { MasPage } from './features/mas/MasPage';
import { HorasExtraPage } from './features/horasExtra/HorasExtraPage';
import { DocumentosPage } from './features/documentos/DocumentosPage';
import { ConfiguracionPage } from './features/configuracion/ConfiguracionPage';

const SCREENS_WITH_NAV = ['/', '/historial', '/mas'];

export default function App() {
  const [ready, setReady] = useState(false);
  const settings = useSettings();
  const navigate = useNavigate();

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
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/nuevo-parte" element={<NuevoPartePage />} />
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
