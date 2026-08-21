import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, newId } from '../../lib/db';
import { Header } from '../../components/Header';
import { Toggle } from '../../components/Toggle';
import { IconSearch, IconPlus, IconChevronRight } from '../../components/Icon';
import type { Frente } from '../../types/models';

export function FrentesPage() {
  const frentes = useLiveQuery(() => db.frentes.toArray(), []) ?? [];
  // Whether a frente has anything hanging off it (tareas, asistencias, fotos, or it's listed on
  // some parte) — computed once here rather than per-row so eliminar() can tell in O(1) whether
  // deleting it would orphan real data.
  const frentesConHistorial = useLiveQuery(async () => {
    const [partidas, asistencias, fotos, partes] = await Promise.all([
      db.partidas.toArray(),
      db.asistencias.toArray(),
      db.fotos.toArray(),
      db.partes.toArray(),
    ]);
    const set = new Set<string>();
    partidas.forEach((p) => set.add(p.frenteId));
    asistencias.forEach((a) => set.add(a.frenteId));
    fotos.forEach((f) => set.add(f.frenteId));
    partes.forEach((p) => p.frentesIds.forEach((id) => set.add(id)));
    return set;
  }, []) ?? new Set<string>();

  const [query, setQuery] = useState('');
  const [editId, setEditId] = useState<string | null>(null);
  const [edit, setEdit] = useState({ nombre: '', km: '', activo: true });
  const [showAdd, setShowAdd] = useState(false);
  const [nuevo, setNuevo] = useState({ nombre: '', km: '' });

  const filtrados = frentes
    .filter((f) => f.nombre.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => a.nombre.localeCompare(b.nombre));

  function empezarEdicion(f: Frente) {
    setEditId(f.id);
    setEdit({ nombre: f.nombre, km: f.km, activo: f.activo });
  }

  async function guardarEdicion() {
    if (!editId || !edit.nombre.trim()) return;
    await db.frentes.update(editId, {
      nombre: edit.nombre.trim(),
      km: edit.km.trim(),
      activo: edit.activo,
    });
    setEditId(null);
  }

  async function eliminar(id: string) {
    await db.frentes.delete(id);
    setEditId(null);
  }

  async function agregar() {
    if (!nuevo.nombre.trim()) return;
    await db.frentes.add({
      id: newId(), nombre: nuevo.nombre.trim(), km: nuevo.km.trim(), activo: true,
    });
    setNuevo({ nombre: '', km: '' });
    setShowAdd(false);
  }

  return (
    <>
      <Header title="Frentes de trabajo" subtitle={`${frentes.filter((f) => f.activo).length} activos · ${frentes.length} en total`} back>
        <div className="search-bar">
          <IconSearch color="var(--text-soft)" />
          <input placeholder="Buscar frente..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      </Header>

      <div className="content">
        <div className="stack" style={{ gap: 8 }}>
          {filtrados.map((f) => {
            const enEdicion = editId === f.id;
            const tieneHistorial = frentesConHistorial.has(f.id);

            return (
              <div key={f.id} className="card">
                {enEdicion ? (
                  <div className="stack" style={{ gap: 8 }}>
                    <input className="field-input" style={{ fontWeight: 600 }} placeholder="Nombre del punto de trabajo" value={edit.nombre} onChange={(e) => setEdit({ ...edit, nombre: e.target.value })} />
                    <input className="field-input" placeholder="Km (opcional)" value={edit.km} onChange={(e) => setEdit({ ...edit, km: e.target.value })} />
                    <div className="list-row" style={{ justifyContent: 'space-between' }}>
                      <div>
                        <div style={{ fontSize: 12.5, fontWeight: 600 }}>Activo</div>
                        <div className="text-soft" style={{ fontSize: 10.5 }}>Si lo apagas, deja de aparecer para elegir en partes nuevos</div>
                      </div>
                      <Toggle on={edit.activo} onChange={(v) => setEdit({ ...edit, activo: v })} label={`Activo: ${edit.nombre}`} />
                    </div>
                    <div className="flex-row gap-8">
                      <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setEditId(null)}>Cancelar</button>
                      <button className="btn btn-primary" style={{ flex: 1 }} onClick={guardarEdicion}>Guardar</button>
                    </div>
                    {tieneHistorial ? (
                      <div className="text-soft" style={{ fontSize: 10.5, textAlign: 'center' }}>
                        Tiene tareas, fotos o partes asociados — desactívalo en vez de eliminarlo para no perder esos datos.
                      </div>
                    ) : (
                      <button
                        onClick={() => eliminar(f.id)}
                        style={{ background: 'none', border: 'none', color: 'var(--red)', fontSize: 11, fontWeight: 700, textAlign: 'center' }}
                      >
                        Eliminar frente
                      </button>
                    )}
                  </div>
                ) : (
                  <button onClick={() => empezarEdicion(f)} className="list-row" style={{ width: '100%', textAlign: 'left', background: 'none', border: 'none', opacity: f.activo ? 1 : 0.55 }}>
                    <div style={{
                      width: 36, height: 36, borderRadius: 10, flexShrink: 0,
                      background: 'var(--accent-soft)', color: 'var(--accent-dark)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700,
                    }}
                    >
                      {f.nombre.slice(0, 2).toUpperCase()}
                    </div>
                    <div style={{ flexGrow: 1, minWidth: 0 }}>
                      <div className="flex-row gap-8">
                        <span style={{ fontSize: 13, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.nombre}</span>
                        {!f.activo && <span className="badge" style={{ background: 'var(--surface-alt)', color: 'var(--text-soft)', flexShrink: 0 }}>Inactivo</span>}
                      </div>
                      {f.km && <div className="text-soft" style={{ fontSize: 11 }}>{f.km}</div>}
                    </div>
                    <IconChevronRight color="var(--text-soft)" />
                  </button>
                )}
              </div>
            );
          })}

          {filtrados.length === 0 && (
            <div className="text-soft" style={{ textAlign: 'center', fontSize: 12.5, padding: '20px 0' }}>
              No hay frentes {query ? 'que coincidan con la búsqueda' : 'creados todavía'}.
            </div>
          )}

          {!showAdd && (
            <button className="chip-dashed card" style={{ justifyContent: 'center' }} onClick={() => setShowAdd(true)}>
              <IconPlus size={14} /> Agregar frente
            </button>
          )}

          {showAdd && (
            <div className="card stack">
              <input placeholder="Nombre del punto de trabajo" className="field-input" style={{ fontWeight: 600 }} value={nuevo.nombre} onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} />
              <input placeholder="Km (opcional)" className="field-input" value={nuevo.km} onChange={(e) => setNuevo({ ...nuevo, km: e.target.value })} />
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
