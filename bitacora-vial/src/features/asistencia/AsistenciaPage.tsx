import { useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId } from '../../lib/db';
import { ensureAsistenciaForFrente, attendanceSummaryForParte } from '../../lib/queries';
import { useTodayParte } from '../../lib/useTodayParte';
import { Header } from '../../components/Header';
import { Toggle } from '../../components/Toggle';
import { IconSearch, IconPlus, IconClockPlus, IconChevronRight } from '../../components/Icon';

export function AsistenciaPage() {
  const navigate = useNavigate();
  const parte = useTodayParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const [frenteId, setFrenteId] = useState<string | null>(null);
  const activeFrenteId = frenteId ?? frentes[0]?.id;
  const [query, setQuery] = useState('');
  const [showAdd, setShowAdd] = useState(false);
  const [nuevo, setNuevo] = useState({ nombre: '', cargo: '' });

  useEffect(() => {
    if (parte && activeFrenteId) {
      ensureAsistenciaForFrente(parte.id, activeFrenteId, parte.fecha);
    }
  }, [parte?.id, activeFrenteId]);

  const trabajadores = useLiveQuery(
    () => (activeFrenteId ? db.trabajadores.where('frenteId').equals(activeFrenteId).toArray() : []),
    [activeFrenteId],
  ) ?? [];

  const registros = useLiveQuery(
    () => (parte ? db.asistencias.where('parteId').equals(parte.id).toArray() : []),
    [parte?.id],
  ) ?? [];

  const totalHoy = useLiveQuery(() => (parte ? attendanceSummaryForParte(parte.id) : undefined), [parte?.id]);

  const filtrados = trabajadores.filter((t) => t.nombre.toLowerCase().includes(query.toLowerCase()));
  const registrosFrente = registros.filter((r) => r.frenteId === activeFrenteId);
  const presentesFrente = registrosFrente.filter((r) => r.presente).length;

  async function updateRegistro(id: string, patch: Partial<(typeof registros)[number]>) {
    await db.asistencias.update(id, patch);
  }

  async function agregarTrabajador() {
    if (!nuevo.nombre.trim() || !activeFrenteId) return;
    await db.trabajadores.add({ id: newId(), nombre: nuevo.nombre.trim(), cargo: nuevo.cargo.trim() || 'Obrero', frenteId: activeFrenteId, activo: true });
    setNuevo({ nombre: '', cargo: '' });
    setShowAdd(false);
  }

  if (!parte) return null;

  return (
    <>
      <Header title="Asistencia de Personal" subtitle={new Date(parte.fecha).toLocaleDateString('es-CL')} back>
        <div className="search-bar" style={{ marginBottom: 12 }}>
          <IconSearch color="#c9c3b8" />
          <input placeholder="Buscar trabajador..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div className="flex-row gap-8" style={{ overflowX: 'auto' }}>
          {frentes.map((f) => (
            <button
              key={f.id}
              onClick={() => setFrenteId(f.id)}
              style={{
                background: activeFrenteId === f.id ? 'var(--orange)' : 'none',
                color: activeFrenteId === f.id ? '#fff' : '#c9c3b8',
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
          {filtrados.map((t) => {
            const r = registros.find((x) => x.trabajadorId === t.id);
            if (!r) return null;
            const initials = t.nombre.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
            return (
              <div key={t.id} className="card">
                <div className="list-row" style={{ opacity: r.presente ? 1 : 0.72 }}>
                  <div style={{
                    width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                    background: r.presente ? 'var(--orange-soft)' : 'var(--red-soft)',
                    color: r.presente ? 'var(--orange-dark)' : 'var(--red)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700,
                  }}>
                    {initials}
                  </div>
                  <div style={{ flexGrow: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{t.nombre}</div>
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
                      <input type="number" className="field-input" style={{ width: 50, marginLeft: 6, color: r.horasExtra > 0 ? 'var(--orange-dark)' : undefined }}
                        value={r.horasExtra} onChange={(e) => updateRegistro(r.id, { horasExtra: Number(e.target.value) || 0 })} />
                    </label>
                    {r.horasExtra > 0 && (
                      <input
                        placeholder="Motivo de la hora extra"
                        value={r.motivoExtra ?? ''}
                        onChange={(e) => updateRegistro(r.id, { motivoExtra: e.target.value })}
                        style={{ flexGrow: 1, border: 'none', background: 'none', fontSize: 10.5, fontStyle: 'italic', color: 'var(--orange-dark)' }}
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
              </div>
            );
          })}

          {!showAdd && (
            <button className="chip-dashed card" style={{ justifyContent: 'center', width: '100%', background: 'none' }} onClick={() => setShowAdd(true)}>
              <IconPlus size={14} /> Agregar trabajador
            </button>
          )}
          {showAdd && (
            <div className="card stack">
              <input placeholder="Nombre" value={nuevo.nombre} onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} className="field-input" style={{ fontWeight: 500 }} />
              <input placeholder="Cargo" value={nuevo.cargo} onChange={(e) => setNuevo({ ...nuevo, cargo: e.target.value })} className="field-input" style={{ fontWeight: 500 }} />
              <div className="flex-row gap-8">
                <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowAdd(false)}>Cancelar</button>
                <button className="btn btn-primary" style={{ flex: 1 }} onClick={agregarTrabajador}>Guardar</button>
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
          <span className="flex-row gap-8" style={{ color: 'var(--orange)', fontSize: 12, fontWeight: 700 }}>
            <IconClockPlus color="var(--orange)" size={15} /> Ver reporte mensual de horas extra
          </span>
          <IconChevronRight color="var(--orange)" />
        </button>
        <div className="flex-row" style={{ justifyContent: 'space-between', fontSize: 11.5, color: 'var(--text-soft)', marginBottom: 9 }}>
          <span>Total personal presente hoy</span>
          <span style={{ fontWeight: 700, color: 'var(--text)' }}>{totalHoy ? `${totalHoy.presentes} / ${totalHoy.total}` : '—'}</span>
        </div>
        <button className="btn btn-primary btn-block" onClick={() => navigate('/nuevo-parte')}>Volver al parte</button>
      </div>
    </>
  );
}
