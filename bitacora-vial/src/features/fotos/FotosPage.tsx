import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId, nowISO } from '../../lib/db';
import { useTodayParte } from '../../lib/useTodayParte';
import { Header } from '../../components/Header';
import { IconPencil, IconFotos } from '../../components/Icon';
import { ETAPAS, ETAPA_POR_ID } from '../../lib/etapas';
import type { EtapaFoto } from '../../types/models';

export function FotosPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const parte = useTodayParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const partidasHoy = useLiveQuery(
    () => (parte && parte.frentesIds.length > 0 ? db.partidas.where('frenteId').anyOf(parte.frentesIds).toArray() : []),
    [parte?.id, parte?.frentesIds.join(',')],
  ) ?? [];
  const [filtro, setFiltro] = useState<'todas' | 'anotadas' | string>('todas');

  const [showCapture, setShowCapture] = useState(false);
  const [tareaSel, setTareaSel] = useState('');
  const [etapaSel, setEtapaSel] = useState<EtapaFoto>('durante');

  const fotos = useLiveQuery(
    () => (parte ? db.fotos.where('parteId').equals(parte.id).reverse().sortBy('capturedAt') : []),
    [parte?.id],
  ) ?? [];

  const filtradas = fotos.filter((f) => {
    if (filtro === 'todas') return true;
    if (filtro === 'anotadas') return f.anotada;
    return f.frenteId === filtro;
  });

  async function onFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !parte) return;
    const partida = partidasHoy.find((p) => p.id === tareaSel);
    const frenteId = partida?.frenteId ?? parte.frentesIds[0] ?? frentes[0]?.id ?? '';
    await db.fotos.add({
      id: newId(),
      parteId: parte.id,
      frenteId,
      partidaId: partida?.id,
      etapa: etapaSel,
      blob: file,
      anotada: false,
      capturedAt: nowISO(),
    });
    setShowCapture(false);
    setTareaSel('');
    setEtapaSel('durante');
  }

  if (!parte) return null;

  return (
    <>
      <Header title="Fotografías" subtitle={`${fotos.length} foto(s) del parte N° ${parte.numero}`} back>
        <div className="flex-row gap-8" style={{ overflowX: 'auto' }}>
          {[{ id: 'todas', label: 'Todas' }, ...frentes.map((f) => ({ id: f.id, label: f.nombre })), { id: 'anotadas', label: 'Con anotaciones' }].map((f) => (
            <button
              key={f.id}
              onClick={() => setFiltro(f.id)}
              style={{
                background: filtro === f.id ? 'var(--orange)' : 'var(--charcoal-3)',
                color: filtro === f.id ? '#fff' : '#c9c3b8',
                border: 'none', borderRadius: 20, padding: '6px 12px', fontSize: 11.5,
                fontWeight: filtro === f.id ? 700 : 600, whiteSpace: 'nowrap',
              }}
            >
              {f.label}
            </button>
          ))}
        </div>
      </Header>

      <div className="content" style={{ paddingBottom: 90 }}>
        {filtradas.length === 0 ? (
          <div className="text-soft" style={{ fontSize: 13, textAlign: 'center', marginTop: 40 }}>
            Aún no hay fotos. Toca el botón + para agregar la primera.
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 11 }}>
            {filtradas.map((foto) => (
              <PhotoThumb
                key={foto.id}
                blob={foto.blob}
                anotada={foto.anotada}
                capturedAt={foto.capturedAt}
                etapa={foto.etapa}
                frenteNombre={frentes.find((f) => f.id === foto.frenteId)?.nombre ?? ''}
                tareaNombre={partidasHoy.find((p) => p.id === foto.partidaId)?.nombre}
                onClick={() => navigate(`/fotos/${foto.id}/editar`)}
              />
            ))}
          </div>
        )}
        <div className="text-soft" style={{ textAlign: 'center', fontSize: 11.5, marginTop: 16 }}>
          Toca una foto para anotarla o dibujar sobre ella
        </div>
      </div>

      {showCapture && (
        <div style={{ position: 'absolute', left: 20, right: 20, bottom: 90, background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 16, padding: 16, boxShadow: '0 10px 30px -8px rgba(0,0,0,.35)' }}>
          <div className="section-label" style={{ marginBottom: 8 }}>Tarea que documenta esta foto</div>
          <select value={tareaSel} onChange={(e) => setTareaSel(e.target.value)} className="field-input" style={{ width: '100%', marginBottom: 14, fontWeight: 500 }}>
            <option value="">General (sin tarea asociada)</option>
            {partidasHoy.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
          </select>

          <div className="section-label" style={{ marginBottom: 8 }}>Etapa</div>
          <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 14 }}>
            {ETAPAS.map((e) => (
              <button
                key={e.id}
                onClick={() => setEtapaSel(e.id)}
                className="chip"
                style={etapaSel === e.id ? { background: e.color, borderColor: e.color, color: '#fff' } : undefined}
              >
                {e.label}
              </button>
            ))}
          </div>

          <div className="flex-row gap-8">
            <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowCapture(false)}>Cancelar</button>
            <button className="btn btn-primary" style={{ flex: 1.4 }} onClick={() => fileInputRef.current?.click()}>Elegir foto</button>
          </div>
        </div>
      )}

      <input ref={fileInputRef} type="file" accept="image/*" capture="environment" style={{ display: 'none' }} onChange={onFileSelected} />
      {!showCapture && (
        <button className="fab" onClick={() => setShowCapture(true)} aria-label="Agregar foto">
          <IconFotos color="#fff" size={24} />
        </button>
      )}
    </>
  );
}

function PhotoThumb({
  blob, anotada, capturedAt, etapa, frenteNombre, tareaNombre, onClick,
}: { blob: Blob; anotada: boolean; capturedAt: string; etapa: EtapaFoto; frenteNombre: string; tareaNombre?: string; onClick: () => void }) {
  const [url, setUrl] = useState<string>('');
  useEffect(() => {
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);

  const hora = new Date(capturedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' });
  const etapaInfo = ETAPA_POR_ID.get(etapa);

  return (
    <button
      onClick={onClick}
      style={{
        borderRadius: 14, overflow: 'hidden', position: 'relative', aspectRatio: '1', border: 'none', padding: 0,
        backgroundImage: url ? `url(${url})` : undefined, backgroundSize: 'cover', backgroundPosition: 'center',
        backgroundColor: 'var(--surface-alt)',
      }}
    >
      <div style={{ position: 'absolute', left: 8, top: 8, right: 8, display: 'flex', justifyContent: 'space-between', gap: 4 }}>
        {etapaInfo && (
          <span style={{ background: etapaInfo.color, color: '#fff', fontSize: 9, fontWeight: 700, padding: '3px 7px', borderRadius: 7 }}>
            {etapaInfo.label}
          </span>
        )}
        {anotada && (
          <div style={{ background: 'var(--orange)', color: '#fff', borderRadius: 7, padding: '3px 7px', display: 'flex', alignItems: 'center', gap: 4 }}>
            <IconPencil size={11} color="#fff" />
          </div>
        )}
      </div>
      <div style={{ position: 'absolute', left: 8, bottom: 8, right: 8, color: '#fff', fontSize: 10, fontWeight: 600, textShadow: '0 1px 3px rgba(0,0,0,.5)' }}>
        {tareaNombre && <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{tareaNombre}</div>}
        <div>{frenteNombre} · {hora}</div>
      </div>
    </button>
  );
}
