import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, nowISO } from '../../lib/db';
import { attendanceSummaryForParte, cumulativeForAllPartidas } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { Header } from '../../components/Header';
import { IconCalendar, IconSun, IconCloudOutline, IconRain, IconPlus, IconChevronRight, IconCubicacion, IconAsistencia, IconFotos } from '../../components/Icon';
import type { Clima, Turno } from '../../types/models';

export function NuevoPartePage() {
  const navigate = useNavigate();
  const parte = useTodayParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const tareasHoy = useLiveQuery(async () => {
    if (!parte) return [];
    const entries = await db.cubicacionEntries.where('parteId').equals(parte.id).toArray();
    const totales = await cumulativeForAllPartidas();
    const partidas = await Promise.all(entries.map((e) => db.partidas.get(e.partidaId)));
    return entries
      .map((e, i) => {
        const p = partidas[i];
        if (!p) return null;
        const acumulado = Math.min(totales[p.id] ?? 0, p.cantidadContratada);
        const pct = p.cantidadContratada > 0 ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
        return { partidaId: p.id, nombre: p.nombre, unidad: p.unidad, avanceHoy: e.cantidadEjecutada, pct, cubicada: p.cantidadContratada > 0 };
      })
      .filter((x): x is NonNullable<typeof x> => !!x);
  }, [parte?.id]) ?? [];
  const asistencia = useLiveQuery(
    () => (parte ? attendanceSummaryForParte(parte.id) : undefined),
    [parte?.id],
  );
  const fotos = useLiveQuery(
    () => (parte ? db.fotos.where('parteId').equals(parte.id).toArray() : []),
    [parte?.id],
  ) ?? [];

  if (!parte) return null;

  async function patch(fields: Partial<typeof parte>) {
    await db.partes.update(parte!.id, { ...fields, updatedAt: nowISO() });
  }

  function toggleFrente(id: string) {
    const set = new Set(parte!.frentesIds);
    set.has(id) ? set.delete(id) : set.add(id);
    patch({ frentesIds: Array.from(set) });
  }

  async function finalizar() {
    await patch({ estado: 'pendiente' });
    navigate('/');
  }

  return (
    <>
      <Header title="Nuevo Parte Diario" subtitle={`N° ${parte.numero} · ${parte.estado === 'en_edicion' ? 'borrador sin guardar' : 'finalizado'}`} back />

      <div className="content">
        <div className="section-label">Datos generales</div>
        <div className="card" style={{ marginBottom: 16 }}>
          <div className="flex-row" style={{ justifyContent: 'space-between', paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <div className="flex-row gap-8"><IconCalendar color="var(--text-soft)" /><span className="text-soft" style={{ fontSize: 13 }}>Fecha</span></div>
            <span style={{ fontSize: 13, fontWeight: 600 }}>{new Date(parte.fecha).toLocaleDateString('es-CL')}</span>
          </div>

          <div className="flex-row" style={{ justifyContent: 'space-between', paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <span className="text-soft" style={{ fontSize: 13 }}>Turno</span>
            <div className="segmented">
              {(['dia', 'noche'] as Turno[]).map((t) => (
                <button key={t} className={parte.turno === t ? 'active' : ''} onClick={() => patch({ turno: t })}>
                  {t === 'dia' ? 'Día' : 'Noche'}
                </button>
              ))}
            </div>
          </div>

          <div className="flex-row" style={{ justifyContent: 'space-between', paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <span className="text-soft" style={{ fontSize: 13 }}>Clima</span>
            <div className="flex-row gap-8">
              {([['soleado', IconSun], ['nublado', IconCloudOutline], ['lluvia', IconRain]] as [Clima, typeof IconSun][]).map(([c, Icon]) => (
                <button
                  key={c}
                  onClick={() => patch({ clima: c })}
                  style={{
                    width: 32, height: 32, borderRadius: 9, border: 'none',
                    background: parte.clima === c ? 'var(--orange)' : 'var(--surface-alt)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  <Icon color={parte.clima === c ? '#fff' : 'var(--text-soft)'} />
                </button>
              ))}
              <input
                type="number"
                value={parte.temperaturaC ?? ''}
                onChange={(e) => patch({ temperaturaC: e.target.value ? Number(e.target.value) : undefined })}
                placeholder="°C"
                style={{ width: 46, marginLeft: 2, fontSize: 13, fontWeight: 600, border: 'none', background: 'none', textAlign: 'right' }}
              />
            </div>
          </div>

          <div className="flex-row" style={{ justifyContent: 'space-between' }}>
            <div className="flex-row gap-8">
              <span style={{ color: 'var(--text-soft)' }}><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></svg></span>
              <span className="text-soft" style={{ fontSize: 13 }}>Atraso por clima</span>
            </div>
            <div className="flex-row gap-8">
              <input
                type="number"
                value={parte.atrasoClimaMin}
                onChange={(e) => patch({ atrasoClimaMin: Number(e.target.value) || 0 })}
                className="field-input"
                style={{ width: 56, textAlign: 'right' }}
              />
              <span className="text-soft" style={{ fontSize: 12 }}>min</span>
            </div>
          </div>
        </div>

        <div className="section-label">Frentes de trabajo activos hoy</div>
        <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 16 }}>
          {frentes.map((f) => (
            <button
              key={f.id}
              onClick={() => toggleFrente(f.id)}
              className={`chip${parte.frentesIds.includes(f.id) ? ' selected' : ''}`}
            >
              {f.nombre}{f.km ? ` · ${f.km}` : ''}
            </button>
          ))}
        </div>

        <div className="section-label">Resumen / observaciones</div>
        <div className="card" style={{ marginBottom: 18, padding: 13 }}>
          <textarea
            value={parte.observaciones}
            onChange={(e) => patch({ observaciones: e.target.value })}
            placeholder="Actividades del día, incidentes, novedades..."
            rows={3}
            style={{ width: '100%', border: 'none', resize: 'vertical', fontSize: 13, lineHeight: 1.5, background: 'none' }}
          />
        </div>

        <div className="section-label">Registrar detalle del día</div>
        <div className="stack" style={{ marginBottom: 8 }}>
          <div className="card">
            <button onClick={() => navigate('/cubicacion')} className="list-row" style={{ width: '100%', textAlign: 'left', background: 'none', border: 'none', padding: 0 }}>
              <div style={{ width: 38, height: 38, borderRadius: 10, background: 'var(--orange-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <IconCubicacion color="var(--orange-dark)" />
              </div>
              <div style={{ flexGrow: 1 }}>
                <div style={{ fontSize: 13.5, fontWeight: 600 }}>Cubicación de tareas</div>
                <div className="text-soft" style={{ fontSize: 11.5 }}>
                  {tareasHoy.length > 0 ? `${tareasHoy.length} tarea(s) con avance hoy` : 'Sin tareas registradas hoy'}
                </div>
              </div>
              <IconChevronRight color="var(--text-soft)" />
            </button>

            {tareasHoy.length > 0 && (
              <div className="stack" style={{ gap: 8, marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border)' }}>
                {tareasHoy.map((t) => (
                  <div key={t.partidaId} className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'baseline', gap: 8 }}>
                    <span style={{ fontSize: 12.5, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.nombre}</span>
                    <span className="text-soft" style={{ fontSize: 11.5, flexShrink: 0 }}>
                      +{t.avanceHoy.toLocaleString('es-CL')} {t.unidad}
                      {t.cubicada ? (
                        <> · <strong style={{ color: 'var(--orange-dark)' }}>{t.pct}%</strong></>
                      ) : (
                        <> · <span style={{ color: 'var(--yellow-text)' }}>sin cubicar</span></>
                      )}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
          <DetailRow
            icon={<IconAsistencia color="var(--blue)" />}
            iconBg="var(--blue-soft)"
            title="Asistencia de personal"
            subtitle={asistencia ? `${asistencia.presentes}/${asistencia.total} presentes` : 'Sin registrar'}
            onClick={() => navigate('/asistencia')}
          />
          <DetailRow
            icon={<IconFotos color="var(--green)" />}
            iconBg="var(--green-soft)"
            title="Fotografías"
            subtitle={`${fotos.length} foto(s) · ${fotos.filter((f) => f.anotada).length} con anotaciones`}
            onClick={() => navigate('/fotos')}
          />
        </div>
      </div>

      <div style={{ flexShrink: 0, background: 'var(--surface)', borderTop: '1px solid var(--border)', padding: '12px 20px 16px', display: 'flex', gap: 10 }}>
        <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => navigate('/')}>Guardar borrador</button>
        <button className="btn btn-primary" style={{ flex: 1.3 }} onClick={finalizar}>Finalizar parte</button>
      </div>
    </>
  );
}

function DetailRow({ icon, iconBg, title, subtitle, onClick }: { icon: React.ReactNode; iconBg: string; title: string; subtitle: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="card list-row" style={{ width: '100%', textAlign: 'left' }}>
      <div style={{ width: 38, height: 38, borderRadius: 10, background: iconBg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {icon}
      </div>
      <div style={{ flexGrow: 1 }}>
        <div style={{ fontSize: 13.5, fontWeight: 600 }}>{title}</div>
        <div className="text-soft" style={{ fontSize: 11.5 }}>{subtitle}</div>
      </div>
      <IconChevronRight color="var(--text-soft)" />
    </button>
  );
}
