import { useNavigate } from 'react-router-dom';
import { useSettings, updateSettings } from '../../lib/useSettings';
import { Header } from '../../components/Header';
import { Toggle } from '../../components/Toggle';
import {
  IconChevronRight, IconFotos, IconFolder, IconLocation, IconBell, IconCheck, IconRefresh,
} from '../../components/Icon';
import type { Tema } from '../../types/models';

const APP_VERSION: string = '1.0';
const LATEST_VERSION: string = '1.1';

export function ConfiguracionPage() {
  const navigate = useNavigate();
  const settings = useSettings();

  const notifPermission = typeof Notification !== 'undefined' ? Notification.permission : 'default';

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
          <div style={{ width: 46, height: 46, borderRadius: '50%', background: 'var(--orange-soft)', color: 'var(--orange-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, fontWeight: 700, flexShrink: 0 }}>
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
            <select
              value={settings.unidades}
              onChange={(e) => updateSettings({ unidades: e.target.value as typeof settings.unidades })}
              style={{ border: 'none', background: 'none', fontSize: 13, fontWeight: 600, color: 'var(--text-soft)' }}
            >
              <option value="metrico">Métrico (m³, m²)</option>
              <option value="imperial">Imperial (yd³, ft²)</option>
            </select>
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
            subtitle="Registrar la progresiva (Km) automáticamente"
            concedido={settings.permisoUbicacion}
            action={!settings.permisoUbicacion ? <button onClick={activarUbicacion} style={{ background: 'none', border: 'none', color: 'var(--orange)', fontSize: 10.5, fontWeight: 700 }}>Activar</button> : undefined}
            border
          />
          <PermisoRow Icon={IconBell} title="Notificaciones" subtitle="Avisos de respaldo y recordatorios" concedido={notifPermission === 'granted'} />
        </div>

        <div className="section-label">Actualizaciones</div>
        <div className="card" style={{ marginBottom: 10 }}>
          <div className="list-row" style={{ paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <div style={{ width: 38, height: 38, borderRadius: 10, background: 'var(--blue-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <IconRefresh color="var(--blue)" />
            </div>
            <div style={{ flexGrow: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 700 }}>
                {APP_VERSION === LATEST_VERSION ? 'Estás al día' : 'Nueva versión disponible'}
              </div>
              <div className="text-soft" style={{ fontSize: 11 }}>
                {APP_VERSION === LATEST_VERSION ? `v${APP_VERSION}` : `v${LATEST_VERSION} · Tienes instalada v${APP_VERSION}`}
              </div>
            </div>
          </div>
          {APP_VERSION !== LATEST_VERSION && (
            <>
              <div className="text-soft" style={{ fontSize: 11.5, lineHeight: 1.5, marginBottom: 12 }}>
                Novedades: reporte de horas extra con detalle diario, sección de Documentos y Configuración, y mejoras de estabilidad.
              </div>
              <button className="btn btn-primary btn-block" onClick={() => window.location.reload()}>Actualizar ahora</button>
            </>
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
