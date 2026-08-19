import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate } from 'react-router-dom';
import { db, newId, nowISO } from '../../lib/db';
import { useTodayParte } from '../../lib/useTodayParte';
import { Header } from '../../components/Header';
import { IconPencil, IconFotos } from '../../components/Icon';

export function FotosPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const parte = useTodayParte();
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];
  const [filtro, setFiltro] = useState<'todas' | 'anotadas' | string>('todas');

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
    const frenteId = parte.frentesIds[0] ?? frentes[0]?.id ?? '';
    await db.fotos.add({ id: newId(), parteId: parte.id, frenteId, blob: file, anotada: false, capturedAt: nowISO() });
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
              <PhotoThumb key={foto.id} fotoId={foto.id} blob={foto.blob} anotada={foto.anotada} capturedAt={foto.capturedAt}
                frenteNombre={frentes.find((f) => f.id === foto.frenteId)?.nombre ?? ''}
                onClick={() => navigate(`/fotos/${foto.id}/editar`)} />
            ))}
          </div>
        )}
        <div className="text-soft" style={{ textAlign: 'center', fontSize: 11.5, marginTop: 16 }}>
          Toca una foto para anotarla o dibujar sobre ella
        </div>
      </div>

      <input ref={fileInputRef} type="file" accept="image/*" capture="environment" style={{ display: 'none' }} onChange={onFileSelected} />
      <button className="fab" onClick={() => fileInputRef.current?.click()} aria-label="Agregar foto">
        <IconFotos color="#fff" size={24} />
      </button>
    </>
  );
}

function PhotoThumb({ blob, anotada, capturedAt, frenteNombre, onClick }: { fotoId: string; blob: Blob; anotada: boolean; capturedAt: string; frenteNombre: string; onClick: () => void }) {
  const [url, setUrl] = useState<string>('');
  useEffect(() => {
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);

  const hora = new Date(capturedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' });

  return (
    <button
      onClick={onClick}
      style={{
        borderRadius: 14, overflow: 'hidden', position: 'relative', aspectRatio: '1', border: 'none', padding: 0,
        backgroundImage: url ? `url(${url})` : undefined, backgroundSize: 'cover', backgroundPosition: 'center',
        backgroundColor: 'var(--surface-alt)',
      }}
    >
      {anotada && (
        <div style={{ position: 'absolute', left: 8, top: 8, background: 'var(--orange)', color: '#fff', borderRadius: 7, padding: '3px 7px', display: 'flex', alignItems: 'center', gap: 4 }}>
          <IconPencil size={11} color="#fff" />
          <span style={{ fontSize: 9, fontWeight: 700 }}>Anotada</span>
        </div>
      )}
      <div style={{ position: 'absolute', left: 8, bottom: 8, color: '#fff', fontSize: 10, fontWeight: 600, textShadow: '0 1px 3px rgba(0,0,0,.5)' }}>
        {frenteNombre} · {hora}
      </div>
    </button>
  );
}
