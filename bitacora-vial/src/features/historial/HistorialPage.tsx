import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, nowISO } from '../../lib/db';
import { useSettings, updateSettings } from '../../lib/useSettings';
import { exportCubicacionCSV, exportFullBackup } from '../../lib/export';
import { Header } from '../../components/Header';
import { StatusBadge } from '../../components/StatusBadge';
import { Toggle } from '../../components/Toggle';
import { IconSearch, IconCloud, IconDoc, IconTable, IconClockPlus, IconChevronRight } from '../../components/Icon';

const THUMB_GRADIENTS = [
  'linear-gradient(135deg,#cfd6da,#8a9aa3)',
  'linear-gradient(135deg,#e3c9a5,#a9835a)',
  'linear-gradient(135deg,#c7d8c9,#6f9873)',
  'linear-gradient(135deg,#a9b6c2,#63707c)',
  'linear-gradient(135deg,#cbb490,#8a6c4c)',
];

export function HistorialPage() {
  const navigate = useNavigate();
  const settings = useSettings();
  const [query, setQuery] = useState('');
  const [filtro, setFiltro] = useState<'todos' | 'semana' | 'mes'>('todos');

  const partes = useLiveQuery(() => db.partes.orderBy('fecha').reverse().toArray(), []) ?? [];
  const pendientes = partes.filter((p) => p.estado === 'pendiente');

  const filtrados = partes.filter((p) => {
    if (query && !`parte n° ${p.numero}`.includes(query.toLowerCase()) && !p.fecha.includes(query)) return false;
    if (filtro === 'todos') return true;
    const dias = (Date.now() - new Date(p.fecha).getTime()) / 86_400_000;
    if (filtro === 'semana') return dias <= 7;
    if (filtro === 'mes') return dias <= 31;
    return true;
  });

  async function respaldarAhora() {
    await db.partes.where('estado').equals('pendiente').modify({ estado: 'respaldado' });
    await updateSettings({ ultimoRespaldo: nowISO() });
    await exportFullBackup();
  }

  return (
    <>
      <Header title="Historial y Respaldo">
        <div className="search-bar">
          <IconSearch color="var(--text-soft)" />
          <input placeholder="Buscar por número o fecha..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      </Header>

      <div className="content">
        <div className="card-dark" style={{ marginBottom: 16 }}>
          <div className="list-row" style={{ marginBottom: 11 }}>
            <IconCloud color="var(--amber)" />
            <div style={{ flexGrow: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 700 }}>Respaldo automático</div>
              <div style={{ fontSize: 10.5, color: '#c9c3b8' }}>
                {settings.ultimoRespaldo ? `Último respaldo: ${new Date(settings.ultimoRespaldo).toLocaleString('es-CL')}` : 'Aún no se ha respaldado'}
              </div>
            </div>
            <Toggle on={settings.respaldoAutomatico} onChange={(v) => updateSettings({ respaldoAutomatico: v })} label="Respaldo automático" />
          </div>
          <button
            onClick={respaldarAhora}
            style={{ width: '100%', background: 'rgba(255,255,255,.1)', border: '1px solid rgba(255,255,255,.18)', color: '#fff', borderRadius: 10, padding: 10, fontSize: 12.5, fontWeight: 700 }}
          >
            Respaldar ahora {pendientes.length > 0 ? `(${pendientes.length} pendiente${pendientes.length > 1 ? 's' : ''})` : ''}
          </button>
        </div>

        <div className="flex-row gap-8" style={{ marginBottom: 14 }}>
          {([['todos', 'Todos'], ['semana', 'Esta semana'], ['mes', 'Este mes']] as const).map(([id, label]) => (
            <button
              key={id}
              onClick={() => setFiltro(id)}
              className="chip"
              style={filtro === id ? { background: 'var(--accent)', borderColor: 'var(--accent)', color: '#fff' } : undefined}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="stack" style={{ gap: 9, marginBottom: 18 }}>
          {filtrados.map((p, i) => (
            <button
              key={p.id}
              onClick={() => navigate(p.estado === 'en_edicion' ? '/nuevo-parte' : '/')}
              className="card list-row"
              style={{ width: '100%', textAlign: 'left' }}
            >
              <div style={{ width: 38, height: 38, borderRadius: 10, background: THUMB_GRADIENTS[i % THUMB_GRADIENTS.length], flexShrink: 0 }} />
              <div style={{ flexGrow: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 600 }}>
                  Parte N° {p.numero} · {p.fecha === new Date().toISOString().slice(0, 10) ? 'Hoy' : new Date(p.fecha).toLocaleDateString('es-CL', { day: '2-digit', month: 'short' })}
                </div>
                <div className="text-soft" style={{ fontSize: 11 }}>{p.frentesIds.length} frente(s)</div>
              </div>
              <StatusBadge estado={p.estado} />
            </button>
          ))}
          {filtrados.length === 0 && <div className="text-soft" style={{ fontSize: 13 }}>No se encontraron partes.</div>}
        </div>

        <div className="section-label">Exportar</div>
        <div className="flex-row gap-10">
          <button onClick={exportFullBackup} className="card" style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 7, border: 'none' }}>
            <IconDoc color="var(--red)" strokeWidth={1.7} />
            <span style={{ fontSize: 12, fontWeight: 700 }}>Respaldo completo</span>
          </button>
          <button onClick={exportCubicacionCSV} className="card" style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 7, border: 'none' }}>
            <IconTable color="var(--green)" strokeWidth={1.7} />
            <span style={{ fontSize: 12, fontWeight: 700 }}>Cubicación en CSV</span>
          </button>
        </div>

        <button
          onClick={() => navigate('/horas-extra')}
          className="flex-row"
          style={{ justifyContent: 'space-between', width: '100%', background: 'none', border: 'none', marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border)' }}
        >
          <span className="flex-row gap-8" style={{ color: 'var(--accent)', fontSize: 12, fontWeight: 700 }}>
            <IconClockPlus size={15} color="var(--accent)" /> Reporte mensual de horas extra
          </span>
          <IconChevronRight color="var(--accent)" />
        </button>
      </div>
    </>
  );
}
