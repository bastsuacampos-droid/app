import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db } from '../../lib/db';
import { overallProgressPct, pendingBackupCount, attendanceSummaryForParte } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { formatLongDate } from '../../lib/date';
import { Header } from '../../components/Header';
import { StatusBadge } from '../../components/StatusBadge';
import { IconLogo, IconCloud, IconGear, IconPlus } from '../../components/Icon';
import type { Parte } from '../../types/models';

const THUMB_GRADIENTS = [
  'linear-gradient(135deg,#cfd6da,#8a9aa3)',
  'linear-gradient(135deg,#e3c9a5,#a9835a)',
  'linear-gradient(135deg,#c7d8c9,#6f9873)',
  'linear-gradient(135deg,#a9b6c2,#63707c)',
  'linear-gradient(135deg,#cbb490,#8a6c4c)',
];

export function DashboardPage() {
  const navigate = useNavigate();

  const hoy = useTodayParte();
  const avance = useLiveQuery(() => overallProgressPct(), []);
  const pendientes = useLiveQuery(() => pendingBackupCount(), []);
  const asistenciaHoy = useLiveQuery(
    () => (hoy ? attendanceSummaryForParte(hoy.id) : undefined),
    [hoy?.id],
  );
  const recientes = useLiveQuery(
    () => db.partes.orderBy('fecha').reverse().limit(3).toArray(),
    [],
  );

  return (
    <>
      <div className="header">
        <div className="header-row">
          <IconLogo stroke="var(--amber)" />
          <div style={{ flexGrow: 1 }}>
            <div className="disp header-title">Bitácora Vial</div>
            <div className="header-subtitle">Ruta 5 Sur · Tramo Chillán–Bulnes</div>
          </div>
          <div className="header-actions">
            <div style={{ position: 'relative' }}>
              <IconCloud />
              {!!pendientes && pendientes > 0 && (
                <div
                  style={{
                    position: 'absolute', top: -3, right: -3, width: 9, height: 9, borderRadius: '50%',
                    background: 'var(--amber)', border: '2px solid var(--header-bg)',
                  }}
                />
              )}
            </div>
            <button className="header-back" onClick={() => navigate('/configuracion')} aria-label="Configuración">
              <IconGear />
            </button>
          </div>
        </div>
      </div>

      <div className="content">
        <div style={{ marginBottom: 16 }}>
          <div className="disp" style={{ fontSize: 20, fontWeight: 800 }}>Hola, Capataz</div>
          <div className="text-soft" style={{ fontSize: 13 }}>
            {formatLongDate(new Date().toISOString().slice(0, 10))}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginBottom: 16 }}>
          <div className="card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, padding: '12px 8px' }}>
            <div
              style={{
                width: 44, height: 44, borderRadius: '50%',
                background: `conic-gradient(var(--accent) ${avance ?? 0}%, var(--surface-alt) 0)`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--surface)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10.5, fontWeight: 700 }}>
                {avance ?? 0}%
              </div>
            </div>
            <div className="text-soft" style={{ fontSize: 10, textAlign: 'center', lineHeight: 1.2 }}>Avance contrato</div>
          </div>

          <div className="card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, padding: '12px 8px' }}>
            <div className="disp" style={{ fontSize: 19, fontWeight: 800, color: 'var(--green)' }}>
              {asistenciaHoy ? `${asistenciaHoy.presentes}/${asistenciaHoy.total}` : '—'}
            </div>
            <div className="text-soft" style={{ fontSize: 10, textAlign: 'center', lineHeight: 1.2 }}>Personal presente</div>
          </div>

          <div className="card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, padding: '12px 8px' }}>
            <div className="disp" style={{ fontSize: 19, fontWeight: 800, color: 'var(--amber)' }}>{pendientes ?? 0}</div>
            <div className="text-soft" style={{ fontSize: 10, textAlign: 'center', lineHeight: 1.2 }}>Partes sin respaldar</div>
          </div>
        </div>

        <button
          className="btn btn-primary btn-block"
          style={{ marginBottom: 22 }}
          onClick={() => navigate('/nuevo-parte')}
        >
          <IconPlus color="#fff" /> Nuevo Parte Diario
        </button>

        <div className="flex-row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
          <div className="disp" style={{ fontSize: 14.5, fontWeight: 700 }}>Partes recientes</div>
          <button
            onClick={() => navigate('/historial')}
            style={{ background: 'none', border: 'none', fontSize: 12, color: 'var(--accent)', fontWeight: 600 }}
          >
            Ver todos
          </button>
        </div>

        <div className="stack">
          {(recientes ?? []).map((parte: Parte, i: number) => (
            <button
              key={parte.id}
              onClick={() => navigate(parte.estado === 'en_edicion' ? '/nuevo-parte' : '/historial')}
              className="card list-row"
              style={{
                textAlign: 'left', width: '100%',
                border: parte.estado === 'en_edicion' ? '1.5px solid var(--blue)' : '1px solid var(--border)',
              }}
            >
              <div style={{ width: 46, height: 46, borderRadius: 10, background: THUMB_GRADIENTS[i % THUMB_GRADIENTS.length], flexShrink: 0 }} />
              <div style={{ flexGrow: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13.5, fontWeight: 600 }}>
                  Parte N° {parte.numero} · {parte.fecha === new Date().toISOString().slice(0, 10) ? 'Hoy' : new Date(parte.fecha).toLocaleDateString('es-CL', { day: '2-digit', month: 'short' })}
                </div>
                <div className="text-soft" style={{ fontSize: 11.5 }}>
                  {parte.frentesIds.length > 0 ? `${parte.frentesIds.length} frente(s) activos` : 'Sin frentes asignados'}
                </div>
              </div>
              <StatusBadge estado={parte.estado} />
            </button>
          ))}
          {recientes?.length === 0 && <div className="text-soft" style={{ fontSize: 13 }}>Aún no hay partes registrados.</div>}
        </div>
      </div>
    </>
  );
}
