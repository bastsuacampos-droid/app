import { useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, newId, nowISO } from '../../lib/db';
import { downloadBlob } from '../../lib/export';
import { Header } from '../../components/Header';
import { IconSearch, IconUpload, IconDownload } from '../../components/Icon';
import type { Documento, DocumentoCategoria } from '../../types/models';

const CATEGORIAS: DocumentoCategoria[] = ['Planos', 'Permisos', 'Contratos', 'Fichas técnicas', 'Otros'];

const TYPE_BADGE: { test: (mime: string, nombre: string) => boolean; label: string; bg: string }[] = [
  { test: (m) => m.includes('pdf'), label: 'PDF', bg: 'var(--red)' },
  { test: (m, n) => m.includes('word') || /\.docx?$/i.test(n), label: 'DOC', bg: 'var(--blue)' },
  { test: (m, n) => m.includes('sheet') || /\.xlsx?$/i.test(n) || m.includes('csv'), label: 'XLS', bg: 'var(--green)' },
  { test: (m) => m.startsWith('image/'), label: 'IMG', bg: 'var(--accent)' },
];

function badgeFor(doc: Documento) {
  return TYPE_BADGE.find((t) => t.test(doc.mime, doc.nombre)) ?? { label: 'DOC', bg: 'var(--text-soft)' };
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function DocumentosPage() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [categoria, setCategoria] = useState<'Todos' | DocumentoCategoria>('Todos');
  const [pendingCategoria, setPendingCategoria] = useState<DocumentoCategoria>('Otros');

  const documentos = useLiveQuery(() => db.documentos.orderBy('createdAt').reverse().toArray(), []) ?? [];

  const filtrados = documentos.filter((d) => {
    if (categoria !== 'Todos' && d.categoria !== categoria) return false;
    if (query && !d.nombre.toLowerCase().includes(query.toLowerCase())) return false;
    return true;
  });

  async function onFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    await db.documentos.add({
      id: newId(),
      nombre: file.name,
      categoria: pendingCategoria,
      mime: file.type || 'application/octet-stream',
      tamano: file.size,
      blob: file,
      createdAt: nowISO(),
    });
  }

  return (
    <>
      <Header title="Documentos" subtitle={`${documentos.length} archivo(s)`} back>
        <div className="search-bar" style={{ marginBottom: 12 }}>
          <IconSearch color="var(--text-soft)" />
          <input placeholder="Buscar documento..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div className="flex-row gap-8" style={{ overflowX: 'auto' }}>
          {(['Todos', ...CATEGORIAS] as const).map((c) => (
            <button
              key={c}
              onClick={() => setCategoria(c)}
              style={{
                background: categoria === c ? 'var(--accent)' : 'var(--surface-alt)',
                color: categoria === c ? '#fff' : 'var(--text-soft)',
                border: 'none', borderRadius: 20, padding: '6px 12px', fontSize: 11.5,
                fontWeight: categoria === c ? 700 : 600, whiteSpace: 'nowrap',
              }}
            >
              {c}
            </button>
          ))}
        </div>
      </Header>

      <div className="content" style={{ paddingBottom: 90 }}>
        <div className="flex-row gap-8" style={{ marginBottom: 14 }}>
          <span className="text-soft" style={{ fontSize: 11.5 }}>Nuevo archivo se guarda como</span>
          <select value={pendingCategoria} onChange={(e) => setPendingCategoria(e.target.value as DocumentoCategoria)} className="field-input" style={{ fontSize: 11.5, padding: '4px 8px' }}>
            {CATEGORIAS.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>

        {filtrados.length === 0 ? (
          <div className="text-soft" style={{ fontSize: 13, textAlign: 'center', marginTop: 30 }}>
            No hay documentos {categoria !== 'Todos' ? `en “${categoria}”` : 'todavía'}.
          </div>
        ) : (
          <div className="stack" style={{ gap: 9 }}>
            {filtrados.map((doc) => {
              const badge = badgeFor(doc);
              return (
                <div key={doc.id} className="card list-row">
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: badge.bg, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9.5, fontWeight: 800, flexShrink: 0 }}>
                    {badge.label}
                  </div>
                  <div style={{ flexGrow: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 12.5, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{doc.nombre}</div>
                    <div className="text-soft" style={{ fontSize: 10.5 }}>{doc.categoria} · {formatSize(doc.tamano)} · {new Date(doc.createdAt).toLocaleDateString('es-CL')}</div>
                  </div>
                  <button onClick={() => downloadBlob(doc.nombre, doc.blob)} style={{ background: 'none', border: 'none', color: 'var(--text-soft)', flexShrink: 0 }} aria-label={`Descargar ${doc.nombre}`}>
                    <IconDownload />
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <input ref={fileInputRef} type="file" style={{ display: 'none' }} onChange={onFileSelected} />
      <button className="fab" onClick={() => fileInputRef.current?.click()} aria-label="Subir documento">
        <IconUpload color="#fff" />
      </button>
    </>
  );
}
