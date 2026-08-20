import { useEffect, useRef, useState } from 'react';
import { parseNumeroDecimal } from '../../lib/numero';
import { CATALOGO_PARTIDAS } from '../../lib/catalogoPartidas';
import { TIPOS_POR_UNIDAD, TIPO_ELEMENTO_LABEL } from '../../lib/cubicacionCalculo';
import type { MedicionInfo, NuevaPartidaDatos } from '../../lib/cubicacionCalculo';
import { IconChevronRight } from '../../components/Icon';
import { CalculadoraCubicacion } from './CalculadoraCubicacion';

export type { NuevaPartidaDatos };

/** Custom dropdown matching the app's own light/blue design instead of a native <select> —
 * on mobile a native select opens the OS's own picker (dark, system-styled), which clashes
 * hard with the rest of the screen. Same open/close + click-outside pattern used for
 * "Seleccionar punto de trabajo" in Nuevo Parte. */
function CampoDesplegable({ valor, opciones, onSeleccionar, ancho }: { valor: string; opciones: string[]; onSeleccionar: (v: string) => void; ancho?: number | string }) {
  const [abierto, setAbierto] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

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
        className="field-input"
        style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6, textAlign: 'left' }}
      >
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{valor}</span>
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
              key={o}
              type="button"
              onClick={() => { onSeleccionar(o); setAbierto(false); }}
              style={{
                display: 'block', width: '100%', textAlign: 'left', whiteSpace: 'nowrap', border: 'none', borderRadius: 8,
                padding: '9px 10px', fontSize: 13,
                background: o === valor ? 'var(--accent-soft)' : 'none',
                fontWeight: o === valor ? 700 : 600,
                color: o === valor ? 'var(--accent-dark)' : 'var(--text)',
              }}
            >
              {o}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/** Form to create a new partida — used both for a top-level tarea (with the suggested
 * catálogo) and, more compactly, for a sub-tarea under an existing one. Cantidad contratada
 * can be typed directly or built up from elements with the calculadora; either way, the
 * measurements used are carried into onGuardar so they land in the memoria de cálculo once
 * the partida (and its id) exists. */
export function NuevaPartidaForm({ onGuardar, onCancelar, conCatalogo }: { onGuardar: (datos: NuevaPartidaDatos) => void; onCancelar: () => void; conCatalogo?: boolean }) {
  const [catCategoria, setCatCategoria] = useState(CATALOGO_PARTIDAS[0].categoria);
  const [nombre, setNombre] = useState('');
  const [unidad, setUnidad] = useState('m³');
  // Text buffers, not numbers: the input's displayed value must echo exactly what the user
  // typed. Deriving a number and feeding it back into `value` on every keystroke reformats the
  // field mid-typing and erases the decimal separator before the next digit lands (e.g. typing
  // "5,5" collapses to "55"). Only the calculadora's programmatic add/subtract needs a number,
  // so it reads/writes through these same buffers via parseNumeroDecimal.
  const [cantidadContratadaTexto, setCantidadContratadaTexto] = useState('');
  const [avanceHoyTexto, setAvanceHoyTexto] = useState('');
  const [mostrarCalc, setMostrarCalc] = useState(false);
  const [mediciones, setMediciones] = useState<MedicionInfo[]>([]);
  const tieneFormula = !!TIPOS_POR_UNIDAD[unidad];
  const cantidadContratada = parseNumeroDecimal(cantidadContratadaTexto);
  const avanceHoy = parseNumeroDecimal(avanceHoyTexto);

  function cambiarUnidad(u: string) {
    setUnidad(u);
    // Pending measurements were computed for the previous unidad's geometry — they don't
    // carry over cleanly, so start the memoria de cálculo over rather than show stale data.
    setMediciones([]);
    setCantidadContratadaTexto('');
    setMostrarCalc(false);
  }

  function agregarMedicion(info: MedicionInfo) {
    setMediciones((m) => [...m, info]);
    setCantidadContratadaTexto((t) => String(Number((parseNumeroDecimal(t) + info.subtotal).toFixed(3))));
  }

  function quitarMedicion(idx: number) {
    setMediciones((m) => {
      setCantidadContratadaTexto((t) => String(Number((parseNumeroDecimal(t) - m[idx].subtotal).toFixed(3))));
      return m.filter((_, i) => i !== idx);
    });
  }

  function guardar() {
    onGuardar({ nombre, unidad, cantidadContratada, avanceHoy, mediciones });
  }

  return (
    <div className="stack" style={{ marginTop: conCatalogo ? 0 : 10, paddingTop: conCatalogo ? 0 : 10, borderTop: conCatalogo ? undefined : '1px solid var(--border)' }}>
      {conCatalogo && (
        <div>
          <div className="section-label" style={{ marginBottom: 6 }}>Catálogo sugerido</div>
          <div style={{ marginBottom: 8 }}>
            <CampoDesplegable
              valor={catCategoria}
              opciones={CATALOGO_PARTIDAS.map((c) => c.categoria)}
              onSeleccionar={setCatCategoria}
              ancho="100%"
            />
          </div>
          <div className="flex-row gap-8" style={{ flexWrap: 'wrap' }}>
            {CATALOGO_PARTIDAS.find((c) => c.categoria === catCategoria)?.items.map((it) => (
              <button
                key={it.nombre}
                className="chip"
                onClick={() => { setNombre(it.nombre); cambiarUnidad(it.unidad); }}
              >
                {it.nombre} · {it.unidad}
              </button>
            ))}
          </div>
        </div>
      )}

      <input
        placeholder="Nombre de la partida"
        value={nombre}
        onChange={(e) => setNombre(e.target.value)}
        className="field-input"
        style={{ fontWeight: 500 }}
        autoFocus={!conCatalogo}
      />
      <div className="flex-row gap-8">
        <CampoDesplegable valor={unidad} opciones={['m³', 'm²', 'ml', 'kg', 'un']} onSeleccionar={cambiarUnidad} ancho={76} />
        <input
          type="text"
          inputMode="decimal"
          placeholder="Cantidad contratada (si no la sabes, déjala en blanco)"
          value={cantidadContratadaTexto}
          onChange={(e) => setCantidadContratadaTexto(e.target.value)}
          className="field-input"
          style={{ flexGrow: 1 }}
        />
      </div>

      {tieneFormula && (
        <button
          onClick={() => setMostrarCalc((v) => !v)}
          style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700, padding: 0, textAlign: 'left', display: 'flex', alignItems: 'center', gap: 4 }}
        >
          {mostrarCalc ? 'Ocultar calculadora' : '¿Prefieres cubicar por medidas?'}
          <IconChevronRight size={12} color="var(--accent)" style={{ transform: mostrarCalc ? 'rotate(90deg)' : undefined }} />
        </button>
      )}
      {mostrarCalc && <CalculadoraCubicacion unidad={unidad} modo="contratado" onAgregar={agregarMedicion} />}
      {mediciones.length > 0 && (
        <div className="stack" style={{ gap: 5 }}>
          {mediciones.map((m, i) => (
            <div key={i} className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', fontSize: 11, background: 'var(--surface-alt)', borderRadius: 7, padding: '6px 9px' }}>
              <span>{m.descripcion || TIPO_ELEMENTO_LABEL[m.tipo]}: <strong>{m.subtotal.toLocaleString('es-CL', { maximumFractionDigits: 3 })} {unidad}</strong></span>
              <button onClick={() => quitarMedicion(i)} aria-label="Quitar medición" style={{ background: 'none', border: 'none', color: 'var(--red)', fontWeight: 800, fontSize: 14, lineHeight: 1, padding: '0 2px' }}>×</button>
            </div>
          ))}
        </div>
      )}

      <label className="text-soft" style={{ fontSize: 11.5 }}>
        Avance de hoy en esta tarea (opcional)
        <input
          type="text"
          inputMode="decimal"
          placeholder="0"
          value={avanceHoyTexto}
          onChange={(e) => setAvanceHoyTexto(e.target.value)}
          className="field-input"
          style={{ width: '100%', marginTop: 4, fontWeight: 500 }}
        />
      </label>
      <div className="flex-row gap-8">
        <button className="btn btn-outline" style={{ flex: 1 }} onClick={onCancelar}>Cancelar</button>
        <button className="btn btn-primary" style={{ flex: 1 }} onClick={guardar}>Guardar tarea</button>
      </div>
    </div>
  );
}
