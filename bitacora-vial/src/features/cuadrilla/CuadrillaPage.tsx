import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, newId } from '../../lib/db';
import { Header } from '../../components/Header';
import { Toggle } from '../../components/Toggle';
import { IconSearch, IconPlus, IconChevronRight } from '../../components/Icon';
import type { Trabajador } from '../../types/models';

export function CuadrillaPage() {
  const trabajadores = useLiveQuery(() => db.trabajadores.toArray(), []) ?? [];
  const conteoAsistencias = useLiveQuery(async () => {
    const rows = await db.asistencias.toArray();
    const counts = new Map<string, number>();
    for (const r of rows) counts.set(r.trabajadorId, (counts.get(r.trabajadorId) ?? 0) + 1);
    return counts;
  }, []) ?? new Map<string, number>();

  const [query, setQuery] = useState('');
  const [editId, setEditId] = useState<string | null>(null);
  const [edit, setEdit] = useState({ nombre: '', cargo: '', activo: true });
  const [showAdd, setShowAdd] = useState(false);
  const [nuevo, setNuevo] = useState({ nombre: '', cargo: '' });

  const filtrados = trabajadores
    .filter((t) => t.nombre.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => a.nombre.localeCompare(b.nombre));

  function empezarEdicion(t: Trabajador) {
    setEditId(t.id);
    setEdit({ nombre: t.nombre, cargo: t.cargo, activo: t.activo });
  }

  async function guardarEdicion() {
    if (!editId || !edit.nombre.trim()) return;
    await db.trabajadores.update(editId, {
      nombre: edit.nombre.trim(),
      cargo: edit.cargo.trim() || 'Obrero',
      activo: edit.activo,
    });
    setEditId(null);
  }

  async function eliminar(id: string) {
    await db.trabajadores.delete(id);
    setEditId(null);
  }

  async function agregar() {
    if (!nuevo.nombre.trim()) return;
    await db.trabajadores.add({
      id: newId(), nombre: nuevo.nombre.trim(), cargo: nuevo.cargo.trim() || 'Obrero', activo: true,
    });
    setNuevo({ nombre: '', cargo: '' });
    setShowAdd(false);
  }

  return (
    <>
      <Header title="Cuadrilla" subtitle={`${trabajadores.filter((t) => t.activo).length} activos · ${trabajadores.length} en total`} back>
        <div className="search-bar">
          <IconSearch color="var(--text-soft)" />
          <input placeholder="Buscar trabajador..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      </Header>

      <div className="content">
        <div className="text-soft" style={{ fontSize: 11, marginBottom: 12 }}>
          Cuadrilla única del proyecto — a quién frente va cada uno se decide al tomar la asistencia del día, no aquí.
        </div>

        <div className="stack" style={{ gap: 8 }}>
          {filtrados.map((t) => {
            const enEdicion = editId === t.id;
            const initials = t.nombre.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
            const tieneHistorial = (conteoAsistencias.get(t.id) ?? 0) > 0;

            return (
              <div key={t.id} className="card">
                {enEdicion ? (
                  <div className="stack" style={{ gap: 8 }}>
                    <input className="field-input" style={{ fontWeight: 600 }} placeholder="Nombre" value={edit.nombre} onChange={(e) => setEdit({ ...edit, nombre: e.target.value })} />
                    <input className="field-input" placeholder="Cargo" value={edit.cargo} onChange={(e) => setEdit({ ...edit, cargo: e.target.value })} />
                    <div className="list-row" style={{ justifyContent: 'space-between' }}>
                      <div>
                        <div style={{ fontSize: 12.5, fontWeight: 600 }}>Activo en la cuadrilla</div>
                        <div className="text-soft" style={{ fontSize: 10.5 }}>Si lo apagas, deja de aparecer para asignar en partes nuevos</div>
                      </div>
                      <Toggle on={edit.activo} onChange={(v) => setEdit({ ...edit, activo: v })} label={`Activo: ${edit.nombre}`} />
                    </div>
                    <div className="flex-row gap-8">
                      <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setEditId(null)}>Cancelar</button>
                      <button className="btn btn-primary" style={{ flex: 1 }} onClick={guardarEdicion}>Guardar</button>
                    </div>
                    {tieneHistorial ? (
                      <div className="text-soft" style={{ fontSize: 10.5, textAlign: 'center' }}>
                        Tiene asistencias registradas — desactívalo en vez de eliminarlo para no perder el historial.
                      </div>
                    ) : (
                      <button
                        onClick={() => eliminar(t.id)}
                        style={{ background: 'none', border: 'none', color: 'var(--red)', fontSize: 11, fontWeight: 700, textAlign: 'center' }}
                      >
                        Eliminar de la cuadrilla
                      </button>
                    )}
                  </div>
                ) : (
                  <button onClick={() => empezarEdicion(t)} className="list-row" style={{ width: '100%', textAlign: 'left', background: 'none', border: 'none', opacity: t.activo ? 1 : 0.55 }}>
                    <div style={{
                      width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                      background: 'var(--accent-soft)', color: 'var(--accent-dark)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700,
                    }}
                    >
                      {initials}
                    </div>
                    <div style={{ flexGrow: 1, minWidth: 0 }}>
                      <div className="flex-row gap-8">
                        <span style={{ fontSize: 13, fontWeight: 600 }}>{t.nombre}</span>
                        {!t.activo && <span className="badge" style={{ background: 'var(--surface-alt)', color: 'var(--text-soft)' }}>Inactivo</span>}
                      </div>
                      <div className="text-soft" style={{ fontSize: 11 }}>{t.cargo}</div>
                    </div>
                    <IconChevronRight color="var(--text-soft)" />
                  </button>
                )}
              </div>
            );
          })}

          {filtrados.length === 0 && (
            <div className="text-soft" style={{ textAlign: 'center', fontSize: 12.5, padding: '20px 0' }}>
              No hay trabajadores {query ? 'que coincidan con la búsqueda' : 'en la cuadrilla'}.
            </div>
          )}

          {!showAdd && (
            <button className="chip-dashed card" style={{ justifyContent: 'center' }} onClick={() => setShowAdd(true)}>
              <IconPlus size={14} /> Agregar trabajador
            </button>
          )}

          {showAdd && (
            <div className="card stack">
              <input placeholder="Nombre" className="field-input" style={{ fontWeight: 600 }} value={nuevo.nombre} onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} />
              <input placeholder="Cargo" className="field-input" value={nuevo.cargo} onChange={(e) => setNuevo({ ...nuevo, cargo: e.target.value })} />
              <div className="flex-row gap-8">
                <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowAdd(false)}>Cancelar</button>
                <button className="btn btn-primary" style={{ flex: 1 }} onClick={agregar}>Guardar</button>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
