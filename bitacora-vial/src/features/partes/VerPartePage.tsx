import { useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate, useParams } from 'react-router-dom';
import { db, nowISO } from '../../lib/db';
import { attendanceSummaryForParte, medicionesDePartida, tareasActivasAgrupadas } from '../../lib/queries';
import type { TareaDelDiaItem } from '../../lib/queries';
import { camposDesdeDatos } from '../../lib/cubicacionCalculo';
import { formatLongDate } from '../../lib/date';
import { ETAPA_POR_ID } from '../../lib/etapas';
import { FiguraMedidas } from '../cubicacion/FiguraMedidas';
import { Header } from '../../components/Header';
import { StatusBadge } from '../../components/StatusBadge';
import { IconSun, IconCloudOutline, IconRain, IconChevronRight, IconFotos } from '../../components/Icon';
import type { Clima, EtapaFoto, RegistroAsistencia, Trabajador } from '../../types/models';

const CLIMA_ICON: Record<Clima, typeof IconSun> = { soleado: IconSun, nublado: IconCloudOutline, lluvia: IconRain };
const CLIMA_LABEL: Record<Clima, string> = { soleado: 'Soleado', nublado: 'Nublado', lluvia: 'Lluvia' };

/** Read-only view of a finalized parte — the report as it stood when the foreman closed it
 * out, laid out to be read start to finish rather than operated. NuevoPartePage still exists
 * for actively editing a draft; this is where "Historial" and "Partes recientes" send a
 * pendiente/respaldado parte instead, since dimming that same edit form (inputs, dropdowns,
 * "agregar" buttons) at 55% opacity made a finished report awkward to actually read. */
export function VerPartePage() {
  const { parteId } = useParams();
  const navigate = useNavigate();
  const parte = useLiveQuery(() => (parteId ? db.partes.get(parteId) : undefined), [parteId]);

  // A still-open draft belongs on the editing form, not this read-only view — redirect there
  // instead of showing a "view" of something that's still being written.
  useEffect(() => {
    if (parte && parte.estado === 'en_edicion') navigate(`/nuevo-parte?parte=${parte.id}`, { replace: true });
  }, [parte, navigate]);

  const frentes = useLiveQuery(() => db.frentes.toArray(), []) ?? [];
  const todosTrabajadores = useLiveQuery(() => db.trabajadores.toArray(), []) ?? [];
  const trabajadorPorId = new Map<string, Trabajador>(todosTrabajadores.map((t) => [t.id, t]));

  const registros = useLiveQuery(
    () => (parte ? db.asistencias.where('parteId').equals(parte.id).toArray() : []),
    [parte?.id],
  ) ?? [];
  const asistencia = useLiveQuery(() => (parte ? attendanceSummaryForParte(parte.id) : undefined), [parte?.id]);

  const seleccionadasIds = parte?.tareasSeleccionadasIds ?? [];
  const grupos = useLiveQuery(
    () => (parte ? tareasActivasAgrupadas(parte.id, seleccionadasIds) : []),
    [parte?.id, seleccionadasIds.join(',')],
  ) ?? [];

  const fotos = useLiveQuery(
    () => (parte ? db.fotos.where('parteId').equals(parte.id).reverse().sortBy('capturedAt') : []),
    [parte?.id],
  ) ?? [];

  async function reabrir() {
    if (!parte) return;
    await db.partes.update(parte.id, { estado: 'en_edicion', updatedAt: nowISO() });
    navigate(`/nuevo-parte?parte=${parte.id}`);
  }

  if (!parte || parte.estado === 'en_edicion') return null;

  const ClimaIcon = CLIMA_ICON[parte.clima];
  const registrosPorFrente = new Map<string, RegistroAsistencia[]>();
  registros.forEach((r) => {
    const arr = registrosPorFrente.get(r.frenteId) ?? [];
    arr.push(r);
    registrosPorFrente.set(r.frenteId, arr);
  });
  const ausentes = asistencia ? asistencia.total - asistencia.presentes : 0;

  return (
    <>
      <Header title={`Parte N° ${parte.numero}`} subtitle={formatLongDate(parte.fecha)} back actions={<StatusBadge estado={parte.estado} />} />

      <div className="content">
        <SectionHeading n={1} title="Resumen del día" />
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="flex-row" style={{ justifyContent: 'space-between', paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <span className="text-soft" style={{ fontSize: 13 }}>Turno</span>
            <span style={{ fontSize: 13, fontWeight: 700 }}>{parte.turno === 'dia' ? 'Día' : 'Noche'}</span>
          </div>
          <div className="flex-row" style={{ justifyContent: 'space-between', paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
            <span className="text-soft" style={{ fontSize: 13 }}>Clima</span>
            <span className="flex-row gap-8" style={{ fontSize: 13, fontWeight: 700, alignItems: 'center' }}>
              <ClimaIcon size={16} color="var(--text-soft)" />
              {CLIMA_LABEL[parte.clima]}{parte.temperaturaC !== undefined ? ` · ${parte.temperaturaC}°C` : ''}
            </span>
          </div>
          {parte.atrasoClimaMin > 0 && (
            <div className="flex-row" style={{ justifyContent: 'space-between', paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--border)' }}>
              <span className="text-soft" style={{ fontSize: 13 }}>Atraso por clima</span>
              <span style={{ fontSize: 13, fontWeight: 700 }}>{parte.atrasoClimaMin} min</span>
            </div>
          )}
          <div>
            <div className="text-soft" style={{ fontSize: 13, marginBottom: 8 }}>Frentes de trabajo</div>
            <div className="flex-row gap-8" style={{ flexWrap: 'wrap' }}>
              {parte.frentesIds.map((id) => {
                const f = frentes.find((x) => x.id === id);
                if (!f) return null;
                return <span key={id} className="chip selected">{f.nombre}{f.km ? ` · ${f.km}` : ''}</span>;
              })}
              {parte.frentesIds.length === 0 && <span className="text-soft" style={{ fontSize: 12.5 }}>Ninguno registrado</span>}
            </div>
          </div>
        </div>

        <SectionHeading n={2} title="Asistencia del día" />
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="flex-row" style={{ justifyContent: 'space-around', marginBottom: registros.length > 0 ? 16 : 0 }}>
            <Stat label="Presentes" value={asistencia?.presentes ?? 0} color="var(--green)" />
            <Stat label="Ausentes" value={ausentes} color="var(--red)" />
            <Stat label="Total" value={asistencia?.total ?? 0} color="var(--text)" />
          </div>
          {parte.frentesIds.map((frenteId) => {
            const filas = registrosPorFrente.get(frenteId) ?? [];
            if (filas.length === 0) return null;
            const f = frentes.find((x) => x.id === frenteId);
            return (
              <div key={frenteId} style={{ marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--border)' }}>
                <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 8 }}>{f?.nombre ?? 'Frente'}</div>
                <div className="stack" style={{ gap: 8 }}>
                  {filas.map((r) => {
                    const trabajador = trabajadorPorId.get(r.trabajadorId);
                    if (!trabajador) return null;
                    return (
                      <div key={r.id} className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 12.5, fontWeight: 600 }}>
                            {trabajador.nombre}
                            {!trabajador.activo && <span className="text-soft" style={{ fontWeight: 400 }}> (inactivo)</span>}
                          </div>
                          <div className="text-soft" style={{ fontSize: 11 }}>{trabajador.cargo}</div>
                          {!r.presente && r.motivoAusencia && (
                            <div style={{ fontSize: 11, color: 'var(--red)', marginTop: 2 }}>{r.motivoAusencia}</div>
                          )}
                          {r.horasExtra > 0 && (
                            <div className="text-soft" style={{ fontSize: 11, marginTop: 2 }}>
                              +{r.horasExtra}h extra{r.motivoExtra ? ` · ${r.motivoExtra}` : ''}
                            </div>
                          )}
                        </div>
                        <span
                          style={{
                            flexShrink: 0, fontSize: 10.5, fontWeight: 700, padding: '3px 9px', borderRadius: 20,
                            background: r.presente ? 'var(--green-soft)' : 'var(--red-soft)',
                            color: r.presente ? 'var(--green)' : 'var(--red)',
                          }}
                        >
                          {r.presente
                            ? r.jornadaSabado
                              ? `Presente · ${r.jornadaSabado === 'medio' ? 'Media jornada' : 'Jornada completa'}`
                              : `Presente · ${r.horasNormales}h`
                            : 'Ausente'}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
          {registros.length === 0 && <div className="text-soft" style={{ fontSize: 13 }}>No se registró asistencia este día.</div>}
        </div>

        <SectionHeading n={3} title="Tareas y Avances" />
        <div className="card" style={{ marginBottom: 20 }}>
          {grupos.length === 0 ? (
            <div className="text-soft" style={{ fontSize: 13 }}>No se registraron tareas este día.</div>
          ) : (
            <div className="stack" style={{ gap: 18 }}>
              {grupos.map((g, gi) => (
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
                    style={g.agrupada ? { gap: 12, paddingLeft: 26, borderLeft: '2px solid var(--border)', marginLeft: 8 } : { gap: 12 }}
                  >
                    {g.items.map((t) => <TareaResumenRow key={t.partidaId} t={t} agrupada={g.agrupada} />)}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="flex-row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
          <SectionHeading n={4} title="Registro Fotográfico" style={{ marginBottom: 0 }} />
          {fotos.length > 0 && (
            <button onClick={() => navigate(`/fotos?parte=${parte.id}`)} className="flex-row gap-8" style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700 }}>
              Ver todas <IconChevronRight size={15} color="var(--accent)" />
            </button>
          )}
        </div>
        {fotos.length > 0 ? (
          <div className="photo-strip" style={{ marginBottom: 20 }}>
            {fotos.map((foto) => (
              <PhotoStripThumb key={foto.id} blob={foto.blob} etapa={foto.etapa} onClick={() => navigate(`/fotos/${foto.id}/editar`)} />
            ))}
          </div>
        ) : (
          <div className="card" style={{ marginBottom: 20 }}>
            <div className="text-soft flex-row gap-8" style={{ fontSize: 13, alignItems: 'center' }}>
              <IconFotos size={16} color="var(--text-soft)" /> No se registraron fotos este día.
            </div>
          </div>
        )}

        <SectionHeading n={5} title="Observaciones y Anotaciones" />
        <div className="card" style={{ marginBottom: 18, padding: 13 }}>
          <p style={{ margin: 0, fontSize: 13, lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>
            {parte.observaciones.trim() || <span className="text-soft">Sin observaciones.</span>}
          </p>
        </div>
      </div>

      <div style={{ flexShrink: 0, background: 'var(--surface)', borderTop: '1px solid var(--border)', padding: '12px 20px 16px' }}>
        <button className="btn btn-primary btn-block" onClick={reabrir}>Reabrir para editar</button>
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

/** Read-only rendering of one tarea's progress for the day — same data as NuevoPartePage's
 * TareaActivaRow, minus every editing control (no avance input, no "Terminado"/quitar). */
function TareaResumenRow({ t, agrupada }: { t: TareaDelDiaItem; agrupada: boolean }) {
  const [dibujoAbierto, setDibujoAbierto] = useState(false);
  const mediciones = useLiveQuery(
    () => (dibujoAbierto ? medicionesDePartida(t.partidaId) : Promise.resolve([])),
    [t.partidaId, dibujoAbierto],
  ) ?? [];
  const ultimaMedicion = mediciones[0];

  return (
    <div>
      {agrupada && (
        <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
          <span style={{ fontSize: 12, fontWeight: 600 }}>{t.nombre}</span>
          {t.cubicada && <strong style={{ fontSize: 11.5, color: 'var(--accent-dark)' }}>{t.pct}%</strong>}
        </div>
      )}
      {!agrupada && <div style={{ fontSize: 12.5, fontWeight: 600, marginBottom: 4 }}>{t.nombre}</div>}
      <div className="progress-track" style={{ marginBottom: 6 }}>
        <div className="progress-fill" style={{ width: `${t.cubicada ? t.pct : 0}%` }} />
      </div>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <span className="text-soft" style={{ fontSize: 11.5 }}>
          {t.avanceHoy > 0 ? `Avance de hoy: ${t.avanceHoy.toLocaleString('es-CL')} ${t.unidad}` : 'Sin avance registrado hoy'}
        </span>
        {t.unidad === '%' ? (
          <span className="text-soft" style={{ fontSize: 11 }}>Avance acumulado: {t.pct}%</span>
        ) : t.unidad === 'ml' ? (
          t.cubicada && (
            <span className="text-soft" style={{ fontSize: 11 }}>Faltan {(t.faltanteLineal ?? 0).toLocaleString('es-CL')} ml</span>
          )
        ) : t.cubicada ? (
          <span className="text-soft" style={{ fontSize: 11 }}>
            {t.acumulado.toLocaleString('es-CL')} de {t.contratado.toLocaleString('es-CL')} {t.unidad}
          </span>
        ) : (
          <span className="text-soft" style={{ fontSize: 11 }}>Total {t.acumulado.toLocaleString('es-CL')} {t.unidad} · sin cubicar</span>
        )}
      </div>
      {t.unidad !== 'ml' && t.unidad !== '%' && (
        <div style={{ marginTop: 4 }}>
          <div className="flex-row gap-8" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
            {t.dimensionesTexto && (
              <span className="text-soft" style={{ fontSize: 10.5 }}>Dimensiones: {t.dimensionesTexto}</span>
            )}
            <button
              type="button"
              onClick={() => setDibujoAbierto((v) => !v)}
              className="flex-row"
              style={{ gap: 3, alignItems: 'center', background: 'none', border: 'none', color: 'var(--accent)', fontSize: 10, fontWeight: 700, padding: 0 }}
            >
              {dibujoAbierto ? 'Ocultar medidas' : 'Ver medidas'}
              <IconChevronRight size={10} color="var(--accent)" style={{ transform: dibujoAbierto ? 'rotate(90deg)' : undefined }} />
            </button>
          </div>
          {dibujoAbierto && (
            ultimaMedicion ? (
              <div style={{ marginTop: 6 }}>
                <FiguraMedidas
                  tipo={ultimaMedicion.tipo}
                  unidad={ultimaMedicion.unidad}
                  campos={camposDesdeDatos(ultimaMedicion.datos)}
                  camposPersonalizados={ultimaMedicion.camposPersonalizados}
                  formula={ultimaMedicion.formula}
                />
              </div>
            ) : (
              <div className="text-soft" style={{ fontSize: 10.5, marginTop: 4 }}>
                Esta tarea no tiene medidas registradas — se cubicó ingresando la cantidad directamente.
              </div>
            )
          )}
        </div>
      )}
    </div>
  );
}

function PhotoStripThumb({ blob, etapa, onClick }: { blob: Blob; etapa: EtapaFoto; onClick: () => void }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);
  const etapaInfo = ETAPA_POR_ID.get(etapa);

  return (
    <button className="photo-strip-item" onClick={onClick} style={{ background: 'none', border: 'none', padding: 0, position: 'relative' }}>
      <div className="photo-strip-thumb" style={{ backgroundImage: url ? `url(${url})` : undefined }} />
      {etapaInfo && (
        <span style={{ position: 'absolute', left: 6, top: 6, background: etapaInfo.color, color: '#fff', fontSize: 8.5, fontWeight: 700, padding: '2px 6px', borderRadius: 6 }}>
          {etapaInfo.label}
        </span>
      )}
    </button>
  );
}
