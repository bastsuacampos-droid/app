import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId, nowISO } from '../../lib/db';
import {
  attendanceSummaryForParte, tareasActivasAgrupadas, tareasDisponiblesParaFrentes, tareasCompletadas,
} from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { Header } from '../../components/Header';
import { IconCalendar, IconSun, IconCloudOutline, IconRain, IconChevronRight, IconPlus, IconFotos, IconX } from '../../components/Icon';
import type { Clima, EstadoTarea, Turno } from '../../types/models';

const ORDEN_RECOMENDACION: Record<EstadoTarea, number> = { pendiente: 0, en_progreso_hoy: 1, sin_iniciar: 2, terminada: 3 };

export function NuevoPartePage() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const parte = useTodayParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const [nuevoFrenteAbierto, setNuevoFrenteAbierto] = useState(false);
  const [nombreFrente, setNombreFrente] = useState('');
  const [kmFrente, setKmFrente] = useState('');
  const [dropdownFrenteAbierto, setDropdownFrenteAbierto] = useState(false);
  const dropdownFrenteRef = useRef<HTMLDivElement>(null);
  const [dropdownTareaAbierto, setDropdownTareaAbierto] = useState(false);
  const dropdownTareaRef = useRef<HTMLDivElement>(null);
  const [tabTareas, setTabTareas] = useState<'activas' | 'completadas'>('activas');

  const entriesHoy = useLiveQuery(
    () => (parte ? db.cubicacionEntries.where('parteId').equals(parte.id).toArray() : []),
    [parte?.id],
  ) ?? [];
  const seleccionadasIds = parte?.tareasSeleccionadasIds ?? [];
  const gruposActivos = useLiveQuery(
    () => (parte ? tareasActivasAgrupadas(parte.id, seleccionadasIds) : []),
    [parte?.id, seleccionadasIds.join(',')],
  ) ?? [];
  const candidatas = useLiveQuery(
    () => (parte ? tareasDisponiblesParaFrentes(parte.frentesIds, parte.id) : []),
    [parte?.id, parte?.frentesIds.join(',')],
  ) ?? [];
  const completadas = useLiveQuery(
    () => (tabTareas === 'completadas' ? tareasCompletadas() : Promise.resolve([])),
    [tabTareas],
  ) ?? [];
  const asistencia = useLiveQuery(
    () => (parte ? attendanceSummaryForParte(parte.id) : undefined),
    [parte?.id],
  );
  const fotos = useLiveQuery(
    () => (parte ? db.fotos.where('parteId').equals(parte.id).reverse().sortBy('capturedAt') : []),
    [parte?.id],
  ) ?? [];

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (dropdownFrenteRef.current && !dropdownFrenteRef.current.contains(e.target as Node)) {
        setDropdownFrenteAbierto(false);
      }
      if (dropdownTareaRef.current && !dropdownTareaRef.current.contains(e.target as Node)) {
        setDropdownTareaAbierto(false);
      }
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  // Anything that already has a cubicación entry today (e.g. logged directly from
  // Cubicación) counts as "selected" too, so it never silently disappears from the report
  // just because nobody picked it from this page's selector.
  useEffect(() => {
    if (!parte) return;
    const actuales = parte.tareasSeleccionadasIds ?? [];
    const faltantes = entriesHoy.map((e) => e.partidaId).filter((id) => !actuales.includes(id));
    if (faltantes.length > 0) {
      db.partes.update(parte.id, { tareasSeleccionadasIds: Array.from(new Set([...actuales, ...faltantes])), updatedAt: nowISO() });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [parte?.id, entriesHoy.map((e) => e.partidaId).join(',')]);

  if (!parte) return null;

  async function patch(fields: Partial<typeof parte>) {
    await db.partes.update(parte!.id, { ...fields, updatedAt: nowISO() });
  }

  function toggleFrente(id: string) {
    const set = new Set(parte!.frentesIds);
    set.has(id) ? set.delete(id) : set.add(id);
    patch({ frentesIds: Array.from(set) });
  }

  function seleccionarFrente(id: string) {
    toggleFrente(id);
    setDropdownFrenteAbierto(false);
  }

  function abrirNuevoFrente() {
    setDropdownFrenteAbierto(false);
    setNuevoFrenteAbierto(true);
  }

  function seleccionarTarea(partidaId: string) {
    patch({ tareasSeleccionadasIds: Array.from(new Set([...seleccionadasIds, partidaId])) });
    setDropdownTareaAbierto(false);
  }

  function quitarTareaSeleccionada(partidaId: string) {
    patch({ tareasSeleccionadasIds: seleccionadasIds.filter((id) => id !== partidaId) });
  }

  async function crearFrente() {
    if (!nombreFrente.trim()) return;
    const id = newId();
    await db.frentes.add({ id, nombre: nombreFrente.trim(), km: kmFrente.trim(), activo: true });
    const set = new Set(parte!.frentesIds);
    set.add(id);
    await patch({ frentesIds: Array.from(set) });
    setNombreFrente('');
    setKmFrente('');
    setNuevoFrenteAbierto(false);
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

            {parte.frentesIds.length > 0 && (
              <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 10 }}>
                {parte.frentesIds.map((id) => {
                  const f = frentes.find((x) => x.id === id);
                  if (!f) return null;
                  return (
                    <span
                      key={id}
                      className="chip selected"
                      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, paddingRight: 8 }}
                    >
                      {f.nombre}{f.km ? ` · ${f.km}` : ''}
                      <button
                        onClick={() => toggleFrente(id)}
                        aria-label={`Quitar ${f.nombre}`}
                        style={{ background: 'rgba(255,255,255,.25)', border: 'none', borderRadius: '50%', width: 16, height: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', padding: 0, flexShrink: 0 }}
                      >
                        <IconX size={9} color="#fff" />
                      </button>
                    </span>
                  );
                })}
              </div>
            )}

            {nuevoFrenteAbierto ? (
              <div style={{ background: 'var(--surface-alt)', borderRadius: 10, padding: 10 }}>
                <input
                  value={nombreFrente}
                  onChange={(e) => setNombreFrente(e.target.value)}
                  placeholder="Nombre del punto de trabajo"
                  className="field-input"
                  style={{ width: '100%', marginBottom: 8 }}
                  autoFocus
                />
                <input
                  value={kmFrente}
                  onChange={(e) => setKmFrente(e.target.value)}
                  placeholder="Km (opcional)"
                  className="field-input"
                  style={{ width: '100%', marginBottom: 8 }}
                />
                <div className="flex-row gap-8">
                  <button
                    className="btn btn-outline"
                    style={{ flex: 1 }}
                    onClick={() => { setNuevoFrenteAbierto(false); setNombreFrente(''); setKmFrente(''); }}
                  >
                    Cancelar
                  </button>
                  <button className="btn btn-primary" style={{ flex: 1 }} onClick={crearFrente}>Guardar</button>
                </div>
              </div>
            ) : (
              <div ref={dropdownFrenteRef} style={{ position: 'relative' }}>
                <button
                  type="button"
                  onClick={() => setDropdownFrenteAbierto((v) => !v)}
                  className="field-input"
                  style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center', textAlign: 'left', color: 'var(--text-soft)' }}
                >
                  Seleccionar punto de trabajo…
                  <span style={{ display: 'flex', transform: dropdownFrenteAbierto ? 'rotate(-90deg)' : 'rotate(90deg)', transition: 'transform .15s' }}>
                    <IconChevronRight size={14} color="var(--text-soft)" />
                  </span>
                </button>

                {dropdownFrenteAbierto && (
                  <div
                    className="card"
                    style={{
                      position: 'absolute', left: 0, right: 0, top: 'calc(100% + 6px)', zIndex: 20,
                      padding: 6, maxHeight: 260, overflowY: 'auto',
                      boxShadow: '0 12px 32px -10px rgba(20,23,28,.28)',
                    }}
                  >
                    {frentes.filter((f) => !parte.frentesIds.includes(f.id)).map((f) => (
                      <button
                        key={f.id}
                        type="button"
                        onClick={() => seleccionarFrente(f.id)}
                        style={{
                          display: 'block', width: '100%', textAlign: 'left', background: 'none', border: 'none',
                          borderRadius: 8, padding: '10px 10px', fontSize: 13, fontWeight: 600, color: 'var(--text)',
                        }}
                      >
                        {f.nombre}{f.km ? ` · ${f.km}` : ''}
                      </button>
                    ))}
                    <button
                      type="button"
                      onClick={abrirNuevoFrente}
                      className="flex-row gap-8"
                      style={{
                        width: '100%', background: 'var(--accent-soft)', border: 'none', borderRadius: 8,
                        padding: '10px 10px', fontSize: 13, fontWeight: 700, color: 'var(--accent-dark)', marginTop: 2,
                      }}
                    >
                      <IconPlus size={13} color="var(--accent-dark)" /> Agregar punto de trabajo…
                    </button>
                  </div>
                )}
              </div>
            )}
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
        <div className="segmented" style={{ marginBottom: 12 }}>
          <button className={tabTareas === 'activas' ? 'active' : ''} style={{ flex: 1 }} onClick={() => setTabTareas('activas')}>Activas</button>
          <button className={tabTareas === 'completadas' ? 'active' : ''} style={{ flex: 1 }} onClick={() => setTabTareas('completadas')}>Completadas</button>
        </div>

        {tabTareas === 'activas' ? (
          <div className="card" style={{ marginBottom: 20 }}>
            {gruposActivos.length === 0 ? (
              <div className="text-soft" style={{ fontSize: 13, marginBottom: 12 }}>Aún no has seleccionado tareas para hoy.</div>
            ) : (
              <div className="stack" style={{ gap: 18, marginBottom: 14 }}>
                {gruposActivos.map((g, gi) => (
                  <div key={g.id}>
                    <div className="flex-row gap-8" style={{ fontSize: 13, fontWeight: 700, marginBottom: g.agrupada ? 10 : 6 }}>
                      <span
                        style={{
                          width: 18, height: 18, borderRadius: '50%', background: 'var(--accent-soft)', color: 'var(--accent-dark)',
                          fontSize: 10, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                        }}
                      >
                        {gi + 1}
                      </span>
                      {g.titulo}
                    </div>
                    <div
                      className="stack"
                      style={g.agrupada ? { gap: 12, paddingLeft: 26, borderLeft: '2px solid var(--border)', marginLeft: 8 } : { gap: 8 }}
                    >
                      {g.items.map((t) => (
                        <div key={t.partidaId}>
                          {g.agrupada && (
                            <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
                              <span style={{ fontSize: 12, fontWeight: 600 }}>{t.nombre}</span>
                              {t.cubicada && <strong style={{ fontSize: 11.5, color: 'var(--accent-dark)' }}>{t.pct}%</strong>}
                            </div>
                          )}
                          <div className="progress-track" style={{ marginBottom: 6 }}>
                            <div className="progress-fill" style={{ width: `${t.cubicada ? t.pct : 0}%` }} />
                          </div>
                          <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                            <span className="text-soft" style={{ fontSize: 11.5 }}>
                              Hoy {t.avanceHoy.toLocaleString('es-CL')} {t.unidad}, Total {t.acumulado.toLocaleString('es-CL')} {t.unidad}
                              {t.cubicada ? ` (Avance ${t.pct}%)` : ' · '}
                              {!t.cubicada && <span style={{ color: 'var(--yellow-text)' }}>sin cubicar</span>}
                            </span>
                            <button
                              onClick={() => quitarTareaSeleccionada(t.partidaId)}
                              aria-label={`Quitar ${t.nombre}`}
                              style={{ background: 'none', border: 'none', color: 'var(--text-soft)', padding: 0, display: 'flex', flexShrink: 0 }}
                            >
                              <IconX size={11} color="var(--text-soft)" />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}

            <div ref={dropdownTareaRef} style={{ position: 'relative' }}>
              <button
                type="button"
                onClick={() => setDropdownTareaAbierto((v) => !v)}
                className="flex-row"
                style={{
                  justifyContent: 'center', gap: 6, width: '100%', background: 'var(--accent-soft)', color: 'var(--accent-dark)',
                  border: 'none', borderRadius: 10, padding: 11, fontSize: 12.5, fontWeight: 700,
                }}
              >
                <IconPlus size={15} color="var(--accent-dark)" /> Seleccionar tarea
              </button>

              {dropdownTareaAbierto && (
                <div
                  className="card"
                  style={{
                    position: 'absolute', left: 0, right: 0, top: 'calc(100% + 6px)', zIndex: 20,
                    padding: 6, maxHeight: 280, overflowY: 'auto',
                    boxShadow: '0 12px 32px -10px rgba(20,23,28,.28)',
                  }}
                >
                  {parte.frentesIds.length === 0 && (
                    <div className="text-soft" style={{ fontSize: 11.5, padding: '10px 8px' }}>
                      Primero elige un punto de trabajo en la sección 1.
                    </div>
                  )}
                  {candidatas
                    .filter((c) => !seleccionadasIds.includes(c.partidaId))
                    .sort((a, b) => ORDEN_RECOMENDACION[a.estado] - ORDEN_RECOMENDACION[b.estado])
                    .map((c) => (
                      <button
                        key={c.partidaId}
                        type="button"
                        onClick={() => seleccionarTarea(c.partidaId)}
                        className="flex-row"
                        style={{
                          justifyContent: 'space-between', alignItems: 'center', width: '100%', textAlign: 'left',
                          background: 'none', border: 'none', borderRadius: 8, padding: '10px 10px', fontSize: 12.5, fontWeight: 600, color: 'var(--text)',
                        }}
                      >
                        <span>{c.nombre}{c.padreNombre ? ` · ${c.padreNombre}` : ''}</span>
                        {(c.estado === 'pendiente' || c.estado === 'en_progreso_hoy') && (
                          <span style={{ background: 'var(--yellow-soft)', color: 'var(--yellow-text)', fontSize: 9.5, fontWeight: 700, padding: '2px 7px', borderRadius: 20, flexShrink: 0, marginLeft: 8 }}>
                            {c.estado === 'pendiente' ? 'Recomendada' : 'En progreso'}
                          </span>
                        )}
                      </button>
                    ))}
                  <button
                    type="button"
                    onClick={() => navigate('/cubicacion')}
                    className="flex-row gap-8"
                    style={{
                      width: '100%', background: 'var(--surface-alt)', border: 'none', borderRadius: 8,
                      padding: '10px 10px', fontSize: 12.5, fontWeight: 700, color: 'var(--accent-dark)', marginTop: 2,
                    }}
                  >
                    <IconPlus size={13} color="var(--accent-dark)" /> Nueva tarea (en Cubicación)
                  </button>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="card" style={{ marginBottom: 20 }}>
            {completadas.length === 0 ? (
              <div className="text-soft" style={{ fontSize: 13 }}>Aún no hay tareas completadas.</div>
            ) : (
              <div className="stack" style={{ gap: 10 }}>
                {completadas.map((t) => (
                  <div key={t.partidaId} style={{ background: 'var(--green-soft)', borderRadius: 10, padding: '10px 12px' }}>
                    <div className="flex-row" style={{ justifyContent: 'space-between', marginBottom: 3, gap: 8 }}>
                      <strong style={{ fontSize: 12.5 }}>{t.nombre}</strong>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--green)', whiteSpace: 'nowrap' }}>{t.cantidad.toLocaleString('es-CL')} {t.unidad}</span>
                    </div>
                    <div className="text-soft" style={{ fontSize: 11 }}>
                      {t.frenteNombre} · Terminada el {t.fechaCompletada ? new Date(t.fechaCompletada).toLocaleDateString('es-CL') : '—'}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

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
