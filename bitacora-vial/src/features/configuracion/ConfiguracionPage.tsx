import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSettings, updateSettings } from '../../lib/useSettings';
import { Header } from '../../components/Header';
import { Toggle } from '../../components/Toggle';
import { CampoDesplegable } from '../../components/CampoDesplegable';
import {
  IconChevronRight, IconFotos, IconFolder, IconLocation, IconBell, IconCheck, IconRefresh,
} from '../../components/Icon';
import type { Tema } from '../../types/models';
import { APP_VERSION } from '../../version';
import {
  checkForUpdate, downloadAndInstallUpdate, isOnWifi, requestInstallPermission, type UpdateInfo,
} from '../../lib/appUpdater';
import AppUpdaterNative from '../../lib/nativeAppUpdater';
import { Capacitor } from '@capacitor/core';

type UpdateState =
  | { status: 'checking' | 'up-to-date' | 'unsupported' }
  | { status: 'available'; info: UpdateInfo }
  | { status: 'needs-permission'; info: UpdateInfo }
  | { status: 'downloading'; info: UpdateInfo; percent: number }
  | { status: 'error'; message: string };

export function ConfiguracionPage() {
  const navigate = useNavigate();
  const settings = useSettings();
  const [update, setUpdate] = useState<UpdateState>({ status: 'checking' });

  const notifPermission = typeof Notification !== 'undefined' ? Notification.permission : 'default';

  async function buscarActualizacion() {
    if (!Capacitor.isNativePlatform()) {
      setUpdate({ status: 'unsupported' });
      return;
    }
    setUpdate({ status: 'checking' });
    try {
      const info = await checkForUpdate();
      if (!info) {
        setUpdate({ status: 'up-to-date' });
        return;
      }
      const canInstall = await AppUpdaterNative.canInstallPackages();
      setUpdate(canInstall.value ? { status: 'available', info } : { status: 'needs-permission', info });
    } catch {
      setUpdate({ status: 'error', message: 'No se pudo buscar actualizaciones. Revisa tu conexión.' });
    }
  }

  useEffect(() => {
    buscarActualizacion();
  }, []);

  useEffect(() => {
    const progressHandle = AppUpdaterNative.addListener('downloadProgress', ({ percent }) => {
      setUpdate((prev) => (prev.status === 'downloading' || prev.status === 'available'
        ? { status: 'downloading', info: prev.info, percent }
        : prev));
    });
    const errorHandle = AppUpdaterNative.addListener('downloadError', ({ message }) => {
      setUpdate({ status: 'error', message });
    });
    return () => {
      progressHandle.then((h) => h.remove());
      errorHandle.then((h) => h.remove());
    };
  }, []);

  async function instalarAhora(info: UpdateInfo) {
    setUpdate({ status: 'downloading', info, percent: 0 });
    const result = await downloadAndInstallUpdate(info);
    if (result === 'needs-permission') setUpdate({ status: 'needs-permission', info });
    if (result === 'unsupported') setUpdate({ status: 'unsupported' });
  }

  async function pedirPermiso(info: UpdateInfo) {
    await requestInstallPermission();
    // El usuario vuelve de Configuración del sistema; reintenta al volver a esta pantalla.
    setUpdate({ status: 'available', info });
  }

  // Actualización automática: si el toggle está activo y hay Wi-Fi, descarga sola apenas hay
  // una versión disponible. Android igual va a pedirle al usuario que confirme la instalación.
  useEffect(() => {
    if (update.status !== 'available' || !settings.autoUpdate) return;
    let cancelado = false;
    isOnWifi().then((wifi) => {
      if (!cancelado && wifi) instalarAhora(update.info);
    });
    return () => { cancelado = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [update.status, settings.autoUpdate]);

  async function activarUbicacion() {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      () => updateSettings({ permisoUbicacion: true }),
      () => updateSettings({ permisoUbicacion: false }),
    );
  }

  return (
    <>
      <Header title="Configuración" back />

      <div className="content">
        <div className="card list-row" style={{ marginBottom: 20 }}>
          <div style={{ width: 46, height: 46, borderRadius: '50%', background: 'var(--accent-soft)', color: 'var(--accent-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, fontWeight: 700, flexShrink: 0 }}>
            C
          </div>
          <div style={{ flexGrow: 1 }}>
            <div style={{ fontSize: 14.5, fontWeight: 700 }}>Capataz</div>
            <div className="text-soft" style={{ fontSize: 11.5 }}>Ruta 5 Sur · Tramo Chillán–Bulnes</div>
          </div>
        </div>

        <div className="section-label">Preferencias</div>
        <div className="card" style={{ padding: '5px 14px', marginBottom: 20 }}>
          <Row border>
            <span style={{ fontSize: 13 }}>Tema</span>
            <div className="segmented">
              {(['claro', 'oscuro'] as Tema[]).map((t) => (
                <button key={t} className={settings.tema === t ? 'active' : ''} onClick={() => updateSettings({ tema: t })}>
                  {t === 'claro' ? 'Claro' : 'Oscuro'}
                </button>
              ))}
            </div>
          </Row>
          <Row border>
            <span style={{ fontSize: 13 }}>Unidades de medida</span>
            <CampoDesplegable
              valor={settings.unidades}
              opciones={[
                { value: 'metrico', label: 'Métrico (m³, m²)' },
                { value: 'imperial', label: 'Imperial (yd³, ft²)' },
              ]}
              onSeleccionar={(v) => updateSettings({ unidades: v as typeof settings.unidades })}
              claseBoton=""
              estiloBoton={{ fontSize: 13, fontWeight: 600, color: 'var(--text-soft)' }}
            />
          </Row>
          <Row>
            <div>
              <div style={{ fontSize: 13 }}>Recordatorio diario</div>
              <div className="text-soft" style={{ fontSize: 10.5 }}>Aviso a las 18:00 si no has registrado el parte</div>
            </div>
            <Toggle on={settings.recordatorioDiario} onChange={(v) => updateSettings({ recordatorioDiario: v })} />
          </Row>
        </div>

        <div className="section-label">Permisos del dispositivo</div>
        <div className="card" style={{ padding: '5px 14px', marginBottom: 20 }}>
          <PermisoRow Icon={IconFotos} title="Cámara" subtitle="Fotografiar avances de obra" concedido={settings.permisoCamara} border />
          <PermisoRow Icon={IconFolder} title="Fotos y archivos" subtitle="Guardar fotos y adjuntar documentos" concedido border />
          <PermisoRow
            Icon={IconLocation}
            title={<>Ubicación <span className="text-soft" style={{ fontWeight: 500 }}>· opcional</span></>}
            subtitle="Completar clima y temperatura del parte con un toque"
            concedido={settings.permisoUbicacion}
            action={!settings.permisoUbicacion ? <button onClick={activarUbicacion} style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 10.5, fontWeight: 700 }}>Activar</button> : undefined}
            border
          />
          <PermisoRow Icon={IconBell} title="Notificaciones" subtitle="Avisos de respaldo y recordatorios" concedido={notifPermission === 'granted'} />
        </div>

        <div className="section-label">Actualizaciones</div>
        <div className="card" style={{ marginBottom: 10 }}>
          <div
            className="list-row"
            style={{ paddingBottom: 12, marginBottom: update.status === 'available' || update.status === 'needs-permission' ? 12 : 0, borderBottom: update.status === 'available' || update.status === 'needs-permission' ? '1px solid var(--border)' : undefined }}
          >
            <div style={{ width: 38, height: 38, borderRadius: 10, background: 'var(--blue-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <IconRefresh color="var(--blue)" />
            </div>
            <div style={{ flexGrow: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700 }}>{updateTitulo(update)}</div>
              <div className="text-soft" style={{ fontSize: 11 }}>{updateSubtitulo(update)}</div>
              {update.status === 'downloading' && (
                <div style={{ height: 4, borderRadius: 2, background: 'var(--border)', marginTop: 6, overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${Math.min(100, Math.max(0, update.percent))}%`, background: 'var(--blue)', transition: 'width 0.3s' }} />
                </div>
              )}
            </div>
            {update.status !== 'checking' && update.status !== 'downloading' && (
              <button onClick={buscarActualizacion} style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 10.5, fontWeight: 700, flexShrink: 0 }}>
                Buscar
              </button>
            )}
          </div>
          {update.status === 'available' && (
            <button onClick={() => instalarAhora(update.info)} className="card list-row" style={{ width: '100%', justifyContent: 'center', background: 'var(--accent)', color: '#fff', fontWeight: 700, fontSize: 13 }}>
              Actualizar ahora
            </button>
          )}
          {update.status === 'needs-permission' && (
            <button onClick={() => pedirPermiso(update.info)} className="card list-row" style={{ width: '100%', justifyContent: 'center', background: 'var(--accent)', color: '#fff', fontWeight: 700, fontSize: 13 }}>
              Permitir instalación
            </button>
          )}
        </div>
        <div className="card list-row" style={{ marginBottom: 20 }}>
          <div>
            <div style={{ fontSize: 13 }}>Actualizar automáticamente</div>
            <div className="text-soft" style={{ fontSize: 10.5 }}>Instala nuevas versiones al conectarte a Wi-Fi</div>
          </div>
          <Toggle on={settings.autoUpdate} onChange={(v) => updateSettings({ autoUpdate: v })} />
        </div>

        <div className="section-label">Datos</div>
        <button onClick={() => navigate('/historial')} className="card list-row" style={{ width: '100%', textAlign: 'left', marginBottom: 20 }}>
          <span style={{ fontSize: 13, flexGrow: 1 }}>Respaldo y exportaciones</span>
          <IconChevronRight color="var(--text-soft)" />
        </button>

        <div className="text-soft" style={{ textAlign: 'center', fontSize: 10.5 }}>Bitácora Vial · v{APP_VERSION}</div>
      </div>
    </>
  );
}

function updateTitulo(update: UpdateState): string {
  switch (update.status) {
    case 'checking': return 'Buscando actualizaciones…';
    case 'up-to-date': return 'Estás al día';
    case 'available': return `Versión ${update.info.version} disponible`;
    case 'needs-permission': return `Versión ${update.info.version} disponible`;
    case 'downloading': return 'Descargando…';
    case 'unsupported': return 'No disponible';
    case 'error': return 'No se pudo buscar';
    default: return '';
  }
}

function updateSubtitulo(update: UpdateState): string {
  switch (update.status) {
    case 'checking': return `v${APP_VERSION}`;
    case 'up-to-date': return `v${APP_VERSION}`;
    case 'available': return 'Toca "Actualizar ahora" para descargar e instalar';
    case 'needs-permission': return 'Primero autoriza instalar desde esta app';
    case 'downloading': return `${Math.round(update.percent)}%`;
    case 'unsupported': return 'Solo disponible en la app instalada (APK)';
    case 'error': return update.message;
    default: return '';
  }
}

function Row({ children, border }: { children: React.ReactNode; border?: boolean }) {
  return (
    <div className="flex-row" style={{ justifyContent: 'space-between', padding: '12px 0', borderBottom: border ? '1px solid var(--border)' : undefined }}>
      {children}
    </div>
  );
}

function PermisoRow({
  Icon, title, subtitle, concedido, action, border,
}: {
  Icon: typeof IconBell; title: React.ReactNode; subtitle: string; concedido: boolean; action?: React.ReactNode; border?: boolean;
}) {
  const bg = concedido ? 'var(--green-soft)' : 'var(--yellow-soft)';
  const color = concedido ? 'var(--green)' : '#a5760a';
  return (
    <div className="list-row" style={{ padding: '12px 0', borderBottom: border ? '1px solid var(--border)' : undefined }}>
      <div style={{ width: 34, height: 34, borderRadius: 9, background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <Icon color={color} />
      </div>
      <div style={{ flexGrow: 1, minWidth: 0 }}>
        <div style={{ fontSize: 12.5, fontWeight: 600 }}>{title}</div>
        <div className="text-soft" style={{ fontSize: 10.5 }}>{subtitle}</div>
      </div>
      {action ?? (
        <div className="flex-row gap-8" style={{ color: concedido ? 'var(--green)' : '#a5760a' }}>
          {concedido && <IconCheck size={14} color="var(--green)" />}
          <span style={{ fontSize: 11, fontWeight: 700 }}>{concedido ? 'Concedido' : 'No concedido'}</span>
        </div>
      )}
    </div>
  );
}
