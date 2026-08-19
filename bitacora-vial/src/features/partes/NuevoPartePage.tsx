import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId, nowISO } from '../../lib/db';
import { attendanceSummaryForParte, cumulativeForAllPartidas } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { Header } from '../../components/Header';
import { IconCalendar, IconSun, IconCloudOutline, IconRain, IconChevronRight, IconPlus, IconFotos } from '../../components/Icon';
import type { Clima, Turno } from '../../types/models';

export function NuevoPartePage() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
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
        const cubicada = p.cantidadContratada > 0;
        const acumulado = cubicada ? Math.min(totales[p.id] ?? 0, p.cantidadContratada) : (totales[p.id] ?? 0);
        const pct = cubicada ? Math.round((acumulado / p.cantidadContratada) * 100) : 0;
        return {
          partidaId: p.id,
          nombre: p.nombre,
          unidad: p.unidad,
          avanceHoy: e.cantidadEjecutada,
          acumulado,
          contratado: p.cantidadContratada,
          pct,
          cubicada,
        };
      })
      .filter((x): x is NonNullable<typeof x> => !!x);
  }, [parte?.id]) ?? [];
  const asistencia = useLiveQuery(
    () => (parte ? attendanceSummaryForParte(parte.id) : undefined),
    [parte?.id],
  );
  const fotos = useLiveQuery(
    () => (parte ? db.fotos.where('parteId').equals(parte.id).reverse().sortBy('capturedAt') : []),
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

  async function onFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !parte) return;
    const frenteId = parte.frentesIds[0] ?? frentes[0]?.id ?? '';
    await db.fotos.add({
      id: newId(),
      parteId: parte.id,
      frenteId,
      etapa: 'durante',
      blob: file,
      anotada: false,
      capturedAt: nowISO(),
    });
  }

  async function finalizar() {
    await patch({ estado: 'pendiente' });
    navigate('/');
  }

  const ausentes = asistencia ? asistencia.total - asistencia.presentes : 0;

  return (
    <>
      <Header title="Nuevo Parte Diario" subtitle={`N° ${parte.numero} · ${parte.estado === 'en_edicion' ? 'borrador sin guardar' : 'finalizado'}`} back />

      <div className="content">
        <SectionHeading n={1} title="Ubicación y Fecha" />
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="flex-row" style={{ justifyContent: 'space-between', paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <div className="flex-row gap-8"><IconCalendar color="var(--text-soft)" /><span className="text-soft" style={{ fontSize: 13 }}>Fecha</span></div>
            <span style={{ fontSize: 13, fontWeight: 600 }}>{new Date(parte.fecha).toLocaleDateString('es-CL')}</span>
          </div>

          <div style={{ paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <div className="text-soft" style={{ fontSize: 13, marginBottom: 8 }}>Frentes de trabajo activos hoy</div>
            <div className="flex-row gap-8" style={{ flexWrap: 'wrap' }}>
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
                    background: parte.clima === c ? 'var(--accent)' : 'var(--surface-alt)',
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

        <SectionHeading n={2} title="Asistencia del día" />
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="flex-row" style={{ justifyContent: 'space-around', marginBottom: 14 }}>
            <Stat label="Presentes" value={asistencia?.presentes ?? 0} color="var(--green)" />
            <Stat label="Ausentes" value={ausentes} color="var(--red)" />
            <Stat label="Total" value={asistencia?.total ?? 0} color="var(--text)" />
          </div>
          <button className="btn btn-primary btn-block" onClick={() => navigate('/asistencia')}>
            Registrar Asistencia
          </button>
        </div>

        <SectionHeading n={3} title="Tareas y Avances" />
        <div className="card" style={{ marginBottom: 20 }}>
          {tareasHoy.length === 0 ? (
            <div className="text-soft" style={{ fontSize: 13, marginBottom: 12 }}>Aún no hay tareas registradas hoy.</div>
          ) : (
            <div className="stack" style={{ gap: 14, marginBottom: 14 }}>
              {tareasHoy.map((t, i) => (
                <div key={t.partidaId}>
                  <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
                    <span className="flex-row gap-8" style={{ fontSize: 13, fontWeight: 600 }}>
                      <span
                        style={{
                          width: 18, height: 18, borderRadius: '50%', background: 'var(--accent-soft)', color: 'var(--accent-dark)',
                          fontSize: 10, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                        }}
                      >
                        {i + 1}
                      </span>
                      {t.nombre}
                    </span>
                    {t.cubicada && <strong style={{ fontSize: 12.5, color: 'var(--accent-dark)' }}>{t.pct}%</strong>}
                  </div>
                  <div className="progress-track" style={{ marginBottom: 6 }}>
                    <div className="progress-fill" style={{ width: `${t.cubicada ? t.pct : 0}%` }} />
                  </div>
                  <div className="text-soft" style={{ fontSize: 11.5 }}>
                    Hoy {t.avanceHoy.toLocaleString('es-CL')} {t.unidad}, Total {t.acumulado.toLocaleString('es-CL')} {t.unidad}
                    {t.cubicada ? ` (Avance ${t.pct}%)` : ' · '}
                    {!t.cubicada && <span style={{ color: 'var(--yellow-text)' }}>sin cubicar</span>}
                  </div>
                </div>
              ))}
            </div>
          )}
          <button
            onClick={() => navigate('/cubicacion')}
            className="flex-row"
            style={{
              justifyContent: 'center', gap: 6, width: '100%', background: 'var(--accent-soft)', color: 'var(--accent-dark)',
              border: 'none', borderRadius: 10, padding: 11, fontSize: 12.5, fontWeight: 700,
            }}
          >
            <IconPlus size={15} color="var(--accent-dark)" /> Agregar tarea
          </button>
        </div>

        <div className="flex-row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
          <SectionHeading n={4} title="Registro Fotográfico" style={{ marginBottom: 0 }} />
          <button onClick={() => navigate('/fotos')} className="flex-row gap-8" style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700 }}>
            Ver todas <IconChevronRight size={15} color="var(--accent)" />
          </button>
        </div>
        <div className="photo-strip" style={{ marginBottom: 20 }}>
          {fotos.map((foto) => (
            <PhotoStripThumb key={foto.id} blob={foto.blob} onClick={() => navigate(`/fotos/${foto.id}/editar`)} />
          ))}
          <button className="photo-strip-add" onClick={() => fileInputRef.current?.click()} aria-label="Agregar fotos">
            <IconFotos size={20} color="var(--text-soft)" />
            <span style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-soft)' }}>+ AGREGAR FOTOS</span>
          </button>
        </div>
        <input ref={fileInputRef} type="file" accept="image/*" capture="environment" style={{ display: 'none' }} onChange={onFileSelected} />

        <SectionHeading n={5} title="Observaciones y Anotaciones" />
        <div className="card" style={{ marginBottom: 18, padding: 13 }}>
          <textarea
            value={parte.observaciones}
            onChange={(e) => patch({ observaciones: e.target.value })}
            placeholder="Actividades del día, incidentes, novedades..."
            rows={3}
            style={{ width: '100%', border: 'none', resize: 'vertical', fontSize: 13, lineHeight: 1.5, background: 'none' }}
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

function SectionHeading({ n, title, style }: { n: number; title: string; style?: React.CSSProperties }) {
  return (
    <div className="section-heading" style={style}>
      <div className="section-number">{n}</div>
      <div className="section-heading-title">{title}</div>
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div className="disp" style={{ fontSize: 20, fontWeight: 800, color }}>{value}</div>
      <div className="text-soft" style={{ fontSize: 10.5 }}>{label}</div>
    </div>
  );
}

function PhotoStripThumb({ blob, onClick }: { blob: Blob; onClick: () => void }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);

  return (
    <button className="photo-strip-item" onClick={onClick} style={{ background: 'none', border: 'none', padding: 0 }}>
      <div className="photo-strip-thumb" style={{ backgroundImage: url ? `url(${url})` : undefined }} />
    </button>
  );
}
