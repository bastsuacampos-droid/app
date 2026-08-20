import { useEffect, useRef, useState } from 'react';
import { IconChevronRight } from './Icon';

export interface OpcionDesplegable { value: string; label: string }

/** Custom dropdown matching the app's own light/blue design instead of a native <select> — on
 * mobile a native select opens the OS's own picker (dark, system-styled, and in whatever
 * language the device is set to, not necessarily Spanish), which clashes hard with the rest of
 * the screen. Same open/close + click-outside pattern used throughout the app (e.g.
 * "Seleccionar punto de trabajo" in Nuevo Parte). */
export function CampoDesplegable({
  valor, opciones, onSeleccionar, ancho, placeholder, claseBoton = 'field-input', estiloBoton,
}: {
  valor: string;
  opciones: OpcionDesplegable[];
  onSeleccionar: (v: string) => void;
  ancho?: number | string;
  placeholder?: string;
  claseBoton?: string;
  estiloBoton?: React.CSSProperties;
}) {
  const [abierto, setAbierto] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const actual = opciones.find((o) => o.value === valor);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setAbierto(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  return (
    <div ref={ref} style={{ position: 'relative', width: ancho }}>
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        className={claseBoton}
        style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6, textAlign: 'left', ...estiloBoton }}
      >
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{actual?.label ?? placeholder ?? valor}</span>
        <span style={{ display: 'flex', flexShrink: 0, transform: abierto ? 'rotate(-90deg)' : 'rotate(90deg)', transition: 'transform .15s' }}>
          <IconChevronRight size={12} color="var(--text-soft)" />
        </span>
      </button>
      {abierto && (
        <div
          className="card"
          style={{
            position: 'absolute', left: 0, top: 'calc(100% + 6px)', zIndex: 20, minWidth: '100%',
            padding: 6, maxHeight: 260, overflowY: 'auto', boxShadow: '0 12px 32px -10px rgba(20,23,28,.28)',
          }}
        >
          {opciones.map((o) => (
            <button
              key={o.value}
              type="button"
              onClick={() => { onSeleccionar(o.value); setAbierto(false); }}
              style={{
                display: 'block', width: '100%', textAlign: 'left', whiteSpace: 'nowrap', border: 'none', borderRadius: 8,
                padding: '9px 10px', fontSize: 13,
                background: o.value === valor ? 'var(--accent-soft)' : 'none',
                fontWeight: o.value === valor ? 700 : 600,
                color: o.value === valor ? 'var(--accent-dark)' : 'var(--text)',
              }}
            >
              {o.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
