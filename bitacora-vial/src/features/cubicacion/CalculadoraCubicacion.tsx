import { useState } from 'react';
import { parseNumeroDecimal } from '../../lib/numero';
import { TIPOS_POR_UNIDAD, camposDelTipo, calcularSubtotalElemento } from '../../lib/cubicacionCalculo';
import type { MedicionInfo } from '../../lib/cubicacionCalculo';
import type { TipoElementoMedicion } from '../../types/models';

export function DimField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label style={{ fontSize: 10.5, color: 'var(--text-soft)', display: 'flex', flexDirection: 'column', gap: 3, flex: '1 1 100px' }}>
      {label}
      <input
        type="text"
        inputMode="decimal"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="field-input"
        style={{ width: '100%', fontWeight: 500 }}
      />
    </label>
  );
}

/** Dimension-based cubicación calculator, reused for both "Cantidad contratada" (modo
 * "contratado") and "Ejecutado hoy" (modo "ejecutado") on any partida whose unidad has a
 * geometry defined in TIPOS_POR_UNIDAD. */
export function CalculadoraCubicacion({
  unidad, modo, onAgregar,
}: {
  unidad: string;
  modo: 'contratado' | 'ejecutado';
  onAgregar: (info: MedicionInfo) => void;
}) {
  const opciones = TIPOS_POR_UNIDAD[unidad] ?? [];
  const [tipo, setTipo] = useState<TipoElementoMedicion>(opciones[0]?.value ?? 'rectangular');
  const [descripcion, setDescripcion] = useState('');
  const [campos, setCampos] = useState<Record<string, string>>({});

  const camposActivos = camposDelTipo(tipo, unidad);
  const datos: Record<string, number> = {};
  camposActivos.forEach((c) => { datos[c.key] = parseNumeroDecimal(campos[c.key] ?? '') || (c.key === 'cantidad' ? 1 : 0); });
  const subtotal = calcularSubtotalElemento(tipo, unidad, datos);

  function elegirTipo(t: TipoElementoMedicion) {
    setTipo(t);
    setCampos({});
  }

  function agregar() {
    onAgregar({ tipo, descripcion: descripcion.trim(), datos, subtotal });
    setCampos({});
    setDescripcion('');
  }

  return (
    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--border)' }}>
      {opciones.length > 1 && (
        <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 8 }}>
          {opciones.map((o) => (
            <button
              key={o.value}
              className="chip"
              style={tipo === o.value ? { background: 'var(--accent)', borderColor: 'var(--accent)', color: '#fff' } : undefined}
              onClick={() => elegirTipo(o.value)}
            >
              {o.label}
            </button>
          ))}
        </div>
      )}
      <input
        placeholder="Descripción (opcional, ej: Zapata Z-1)"
        value={descripcion}
        onChange={(e) => setDescripcion(e.target.value)}
        className="field-input"
        style={{ width: '100%', marginBottom: 8, fontWeight: 500 }}
      />
      <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 8 }}>
        {camposActivos.map((c) => (
          <DimField key={c.key} label={c.label} value={campos[c.key] ?? ''} onChange={(v) => setCampos((prev) => ({ ...prev, [c.key]: v }))} />
        ))}
      </div>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12.5 }}>
          Subtotal: <strong>{subtotal.toLocaleString('es-CL', { maximumFractionDigits: 3 })} {unidad}</strong>
        </span>
        <button className="btn btn-primary" style={{ padding: '8px 14px', fontSize: 12 }} onClick={agregar} disabled={subtotal <= 0}>
          {modo === 'contratado' ? 'Agregar a lo contratado' : 'Agregar al total de hoy'}
        </button>
      </div>
      <div className="text-soft" style={{ fontSize: 9.5, lineHeight: 1.4 }}>
        Cálculo geométrico general de uso práctico en obra — no es una transcripción de NCh 353
        Of.2000. Verifica el criterio de medición de tu contrato antes de usarlo en un estado de pago.
      </div>
    </div>
  );
}
