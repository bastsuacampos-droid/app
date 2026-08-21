import { IconX, IconFolder } from '../../components/Icon';
import { formatFileSize } from '../../lib/archivos';
import type { DocumentoCategoria } from '../../types/models';

const CATEGORIAS: { value: DocumentoCategoria; bg: string; color: string }[] = [
  { value: 'Planos', bg: 'var(--blue-soft)', color: 'var(--blue)' },
  { value: 'Permisos', bg: 'var(--green-soft)', color: 'var(--green)' },
  { value: 'Contratos', bg: 'var(--accent-soft)', color: 'var(--accent-dark)' },
  { value: 'Fichas técnicas', bg: 'var(--yellow-soft)', color: 'var(--yellow-text)' },
  { value: 'Otros', bg: 'var(--surface-alt)', color: 'var(--text-soft)' },
];

/** Shown right after picking a file to upload — asks for the category before it's saved,
 * instead of relying on a preselector the user has to remember to set beforehand. */
export function ClasificarDocumentoModal({
  archivo, onElegir, onCancelar,
}: {
  archivo: File;
  onElegir: (categoria: DocumentoCategoria) => void;
  onCancelar: () => void;
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--bg)', zIndex: 100, display: 'flex', flexDirection: 'column' }}>
      <div
        className="flex-row"
        style={{
          justifyContent: 'space-between', alignItems: 'center', gap: 10, padding: '18px 20px 14px',
          paddingTop: 'calc(18px + var(--safe-top))', flexShrink: 0, borderBottom: '1px solid var(--border)', background: 'var(--surface)',
        }}
      >
        <div>
          <div className="disp" style={{ fontSize: 15, fontWeight: 800 }}>Clasificar archivo</div>
          <div className="text-soft" style={{ fontSize: 11 }}>¿En qué categoría va?</div>
        </div>
        <button onClick={onCancelar} aria-label="Cancelar" style={{ background: 'none', border: 'none', padding: 6, display: 'flex', flexShrink: 0 }}>
          <IconX size={18} color="var(--text-soft)" />
        </button>
      </div>

      <div className="content">
        <div className="card list-row" style={{ marginBottom: 16 }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: 'var(--surface-alt)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <IconFolder color="var(--text-soft)" />
          </div>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 12.5, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{archivo.name}</div>
            <div className="text-soft" style={{ fontSize: 10.5 }}>{formatFileSize(archivo.size)}</div>
          </div>
        </div>

        <div className="stack" style={{ gap: 8 }}>
          {CATEGORIAS.map((c) => (
            <button key={c.value} onClick={() => onElegir(c.value)} className="card list-row" style={{ width: '100%', textAlign: 'left' }}>
              <div style={{ width: 10, height: 10, borderRadius: '50%', background: c.color, flexShrink: 0 }} />
              <span style={{ flexGrow: 1, fontSize: 13.5, fontWeight: 600 }}>{c.value}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
