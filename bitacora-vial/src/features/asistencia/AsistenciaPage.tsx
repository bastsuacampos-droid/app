import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId } from '../../lib/db';
import { attendanceSummaryForParte, moveTrabajadorAFrente, asignarTrabajadorAFrente } from '../../lib/queries';
import { useActiveParte } from '../../lib/useActiveParte';
import { Header } from '../../components/Header';
import { Toggle } from '../../components/Toggle';
import { IconSearch, IconPlus, IconClockPlus, IconChevronRight } from '../../components/Icon';
import type { Trabajador } from '../../types/models';

export function AsistenciaPage() {
  const navigate = useNavigate();
  const parte = useActiveParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const [frenteId, setFrenteId] = useState<string | null>(null);
  const activeFrenteId = frenteId ?? frentes[0]?.id;
  const [query, setQuery] = useState('');
  const [showAdd, setShowAdd] = useState(false);
  const [addQuery, setAddQuery] = useState('');
  const [nuevo, setNuevo] = useState({ nombre: '', cargo: '' });
  const [moverRow, setMoverRow] = useState<string | null>(null);

  // The crew is one global roster — nobody is "assigned" to a frente until attendance for
  // today assigns them one (see RegistroAsistencia.frenteId), so it's needed here regardless
  // of which frente tab is active.
  const todosTrabajadores = useLiveQuery(() => db.trabajadores.filter((t) => t.activo).toArray(), []) ?? [];
  const trabajadorPorId = new Map<string, Trabajador>(todosTrabajadores.map((t) => [t.id, t]));

  const registros = useLiveQuery(
    () => (parte ? db.asistencias.where('parteId').equals(parte.id).toArray() : []),
    [parte?.id],
  ) ?? [];

  const totalHoy = useLiveQuery(() => (parte ? attendanceSummaryForParte(parte.id) : undefined), [parte?.id]);

  const registrosFrente = registros.filter((r) => r.frenteId === activeFrenteId);
  const presentesFrente = registrosFrente.filter((r) => r.presente).length;

  const filas = registrosFrente
    .map((r) => ({ registro: r, trabajador: trabajadorPorId.get(r.trabajadorId) }))
    .filter((f): f is { registro: typeof registrosFrente[number]; trabajador: Trabajador } => !!f.trabajador)
    .filter((f) => f.trabajador.nombre.toLowerCase().includes(query.toLowerCase()));

  // Anyone in the crew not yet placed on a frente today — the pool "Agregar trabajador" picks from.
  const idsAsignadosHoy = new Set(registros.map((r) => r.trabajadorId));
  const disponibles = todosTrabajadores
    .filter((t) => !idsAsignadosHoy.has(t.id))
    .filter((t) => t.nombre.toLowerCase().includes(addQuery.toLowerCase()));

  async function updateRegistro(id: string, patch: Partial<(typeof registros)[number]>) {
    await db.asistencias.update(id, patch);
  }

  async function asignar(trabajadorId: string) {
    if (!parte || !activeFrenteId) return;
    await asignarTrabajadorAFrente(parte.id, trabajadorId, activeFrenteId, parte.fecha);
    setAddQuery('');
  }

  async function crearYAsignar() {
    if (!nuevo.nombre.trim() || !parte || !activeFrenteId) return;
    const id = newId();
    await db.trabajadores.add({ id, nombre: nuevo.nombre.trim(), cargo: nuevo.cargo.trim() || 'Obrero', activo: true });
    await asignarTrabajadorAFrente(parte.id, id, activeFrenteId, parte.fecha);
    setNuevo({ nombre: '', cargo: '' });
    setShowAdd(false);
  }

  async function mover(registroId: string, destinoFrenteId: string) {
    await moveTrabajadorAFrente(registroId, destinoFrenteId);
    setMoverRow(null);
  }

  async function quitar(registroId: string, nombre: string) {
    if (!confirm(`¿Quitar a ${nombre} de la asistencia de hoy?`)) return;
    await db.asistencias.delete(registroId);
  }

  if (!parte) return null;

  return (
    <>
      <Header title="Asistencia de Personal" subtitle={new Date(parte.fecha).toLocaleDateString('es-CL')} back>
        <div className="search-bar" style={{ marginBottom: 12 }}>
          <IconSearch color="var(--text-soft)" />
          <input placeholder="Buscar trabajador..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div className="flex-row gap-8" style={{ overflowX: 'auto' }}>
          {frentes.map((f) => (
            <button
              key={f.id}
              onClick={() => setFrenteId(f.id)}
              style={{
                background: activeFrenteId === f.id ? 'var(--accent)' : 'none',
                color: activeFrenteId === f.id ? '#fff' : 'var(--text-soft)',
                border: 'none', borderRadius: '10px 10px 0 0', padding: '8px 14px',
                fontSize: 12, fontWeight: activeFrenteId === f.id ? 700 : 600, whiteSpace: 'nowrap',
              }}
            >
              {f.nombre}
            </button>
          ))}
        </div>
      </Header>

      <div className="content">
        <div style={{ background: 'var(--green-soft)', border: '1px solid #cfe9d8', borderRadius: 12, padding: '10px 13px', marginBottom: 14, fontSize: 12.5, color: '#2c6b45', fontWeight: 600 }}>
          {presentesFrente} / {registrosFrente.length} presentes en {frentes.find((f) => f.id === activeFrenteId)?.nombre}
        </div>

        <div className="stack" style={{ gap: 8 }}>
          {filas.map(({ registro: r, trabajador: t }) => {
            const initials = t.nombre.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
            const otrosFrentes = frentes.filter((f) => f.id !== activeFrenteId);
            return (
              <div key={t.id} className="card">
                <div className="list-row" style={{ opacity: r.presente ? 1 : 0.72 }}>
                  <div style={{
                    width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                    background: r.presente ? 'var(--accent-soft)' : 'var(--red-soft)',
                    color: r.presente ? 'var(--accent-dark)' : 'var(--red)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700,
                  }}>
                    {initials}
                  </div>
                  <div style={{ flexGrow: 1, minWidth: 0 }}>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>{t.nombre}</span>
                    <div className="text-soft" style={{ fontSize: 11 }}>{t.cargo}</div>
                  </div>
                  <Toggle on={r.presente} onChange={(v) => updateRegistro(r.id, { presente: v })} label={`Presente: ${t.nombre}`} />
                </div>

                {r.presente ? (
                  <div className="flex-row gap-8" style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--border)' }}>
                    <label className="text-soft" style={{ fontSize: 11 }}>
                      Horas
                      <input type="number" className="field-input" style={{ width: 50, marginLeft: 6 }} value={r.horasNormales}
                        onChange={(e) => updateRegistro(r.id, { horasNormales: Number(e.target.value) || 0 })} />
                    </label>
                    <label className="text-soft" style={{ fontSize: 11 }}>
                      Extra
                      <input type="number" className="field-input" style={{ width: 50, marginLeft: 6, color: r.horasExtra > 0 ? 'var(--accent-dark)' : undefined }}
                        value={r.horasExtra} onChange={(e) => updateRegistro(r.id, { horasExtra: Number(e.target.value) || 0 })} />
                    </label>
                    {r.horasExtra > 0 && (
                      <input
                        placeholder="Motivo de la hora extra"
                        value={r.motivoExtra ?? ''}
                        onChange={(e) => updateRegistro(r.id, { motivoExtra: e.target.value })}
                        style={{ flexGrow: 1, border: 'none', background: 'none', fontSize: 10.5, fontStyle: 'italic', color: 'var(--accent-dark)' }}
                      />
                    )}
                  </div>
                ) : (
                  <input
                    placeholder="Motivo de ausencia"
                    value={r.motivoAusencia ?? ''}
                    onChange={(e) => updateRegistro(r.id, { motivoAusencia: e.target.value })}
                    style={{ marginTop: 8, width: '100%', border: 'none', background: 'none', fontSize: 11, color: 'var(--red)', fontWeight: 600 }}
                  />
                )}

                <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border)' }}>
                  {moverRow === t.id ? (
                    <div className="flex-row gap-8">
                      <select
                        className="field-input"
                        style={{ flexGrow: 1 }}
                        defaultValue=""
                        onChange={(e) => e.target.value && mover(r.id, e.target.value)}
                      >
                        <option value="" disabled>¿A qué frente lo mueves?</option>
                        {otrosFrentes.map((f) => <option key={f.id} value={f.id}>{f.nombre}</option>)}
                      </select>
                      <button className="btn btn-outline" style={{ padding: '8px 12px', fontSize: 11.5 }} onClick={() => setMoverRow(null)}>Cancelar</button>
                    </div>
                  ) : (
                    <div className="flex-row gap-8" style={{ justifyContent: 'space-between' }}>
                      {otrosFrentes.length > 0 ? (
                        <button
                          onClick={() => setMoverRow(t.id)}
                          style={{ background: 'none', border: 'none', color: 'var(--text-soft)', fontSize: 11, fontWeight: 600 }}
                        >
                          Mover a otro frente hoy
                        </button>
                      ) : <span />}
                      <button
                        onClick={() => quitar(r.id, t.nombre)}
                        style={{ background: 'none', border: 'none', color: 'var(--red)', fontSize: 11, fontWeight: 600 }}
                      >
                        Quitar de hoy
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}

          {!showAdd && (
            <button className="chip-dashed card" style={{ justifyContent: 'center' }} onClick={() => setShowAdd(true)}>
              <IconPlus size={14} /> Agregar trabajador
            </button>
          )}

          {showAdd && (
            <div className="card stack">
              <div className="section-label" style={{ marginBottom: 0 }}>Agregar a {frentes.find((f) => f.id === activeFrenteId)?.nombre}</div>

              <div className="search-bar">
                <IconSearch color="var(--text-soft)" />
                <input placeholder="Buscar en la cuadrilla..." value={addQuery} onChange={(e) => setAddQuery(e.target.value)} />
              </div>

              {disponibles.length > 0 && (
                <div className="stack" style={{ gap: 0, maxHeight: 200, overflowY: 'auto' }}>
                  {disponibles.map((t) => (
                    <button
                      key={t.id}
                      onClick={() => asignar(t.id)}
                      className="list-row"
                      style={{ width: '100%', textAlign: 'left', background: 'none', border: 'none', padding: '7px 0' }}
                    >
                      <span style={{ flexGrow: 1, fontSize: 13 }}>{t.nombre} <span className="text-soft">· {t.cargo}</span></span>
                      <IconPlus size={13} color="var(--accent)" />
                    </button>
                  ))}
                </div>
              )}

              <div style={{ borderTop: '1px solid var(--border)', paddingTop: 10 }}>
                <div className="text-soft" style={{ fontSize: 10.5, marginBottom: 8 }}>¿No está en la cuadrilla? Créalo:</div>
                <div className="stack" style={{ gap: 8 }}>
                  <input placeholder="Nombre" value={nuevo.nombre} onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} className="field-input" style={{ fontWeight: 500 }} />
                  <input placeholder="Cargo" value={nuevo.cargo} onChange={(e) => setNuevo({ ...nuevo, cargo: e.target.value })} className="field-input" style={{ fontWeight: 500 }} />
                </div>
              </div>

              <div className="flex-row gap-8">
                <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => { setShowAdd(false); setNuevo({ nombre: '', cargo: '' }); setAddQuery(''); }}>Cancelar</button>
                <button className="btn btn-primary" style={{ flex: 1 }} onClick={crearYAsignar} disabled={!nuevo.nombre.trim()}>Crear y agregar</button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div style={{ flexShrink: 0, background: 'var(--surface)', borderTop: '1px solid var(--border)', padding: '11px 20px 16px' }}>
        <button
          onClick={() => navigate('/horas-extra')}
          className="flex-row"
          style={{ justifyContent: 'space-between', width: '100%', background: 'none', border: 'none', paddingBottom: 10, marginBottom: 10, borderBottom: '1px solid var(--border)' }}
        >
          <span className="flex-row gap-8" style={{ color: 'var(--accent)', fontSize: 12, fontWeight: 700 }}>
            <IconClockPlus color="var(--accent)" size={15} /> Ver reporte mensual de horas extra
          </span>
          <IconChevronRight color="var(--accent)" />
        </button>
        <div className="flex-row" style={{ justifyContent: 'space-between', fontSize: 11.5, color: 'var(--text-soft)', marginBottom: 9 }}>
          <span>Total personal presente hoy</span>
          <span style={{ fontWeight: 700, color: 'var(--text)' }}>{totalHoy ? `${totalHoy.presentes} / ${totalHoy.total}` : '—'}</span>
        </div>
        <button className="btn btn-primary btn-block" onClick={() => navigate(`/nuevo-parte?parte=${parte.id}`)}>Volver al parte</button>
      </div>
    </>
  );
}
