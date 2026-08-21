import { useState } from 'react';
import { parseNumeroDecimal } from '../../lib/numero';
import { TIPOS_POR_UNIDAD, camposDelTipo, calcularSubtotalElemento } from '../../lib/cubicacionCalculo';
import type { MedicionInfo } from '../../lib/cubicacionCalculo';
import { evaluarFormula, FormulaError } from '../../lib/formulaEval';
import type { CampoPersonalizado, TipoElementoMedicion } from '../../types/models';
import { IconTrash } from '../../components/Icon';
import { FiguraMedidas } from './FiguraMedidas';

/** Short, unambiguous tokens for personalizado fields — used in the formula regardless of
 * what the user types as the field's label, so accents/spaces in a label never break parsing. */
const TOKENS_PERSONALIZADO = 'abcdefghijklmnopqrstuvwxyz'.split('');

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
  const esPersonalizado = tipo === 'personalizado';

  // Personalizado-only state: a dynamic, user-grown list of named fields (token a, b, c… +
  // whatever label the foreman gives it) plus the formula combining them. Values themselves
  // still live in `campos` (keyed by token), same as every other tipo.
  const [camposPersonalizados, setCamposPersonalizados] = useState<CampoPersonalizado[]>([]);
  const [formula, setFormula] = useState('');

  const camposActivos = esPersonalizado ? camposPersonalizados : camposDelTipo(tipo, unidad);
  const datos: Record<string, number> = {};
  camposActivos.forEach((c) => { datos[c.key] = parseNumeroDecimal(campos[c.key] ?? '') || (c.key === 'cantidad' ? 1 : 0); });

  let subtotal = 0;
  let errorFormula: string | null = null;
  if (esPersonalizado) {
    if (formula.trim()) {
      try {
        subtotal = evaluarFormula(formula, datos);
      } catch (e) {
        errorFormula = e instanceof FormulaError ? e.message : 'Fórmula inválida';
      }
    }
  } else {
    subtotal = calcularSubtotalElemento(tipo, unidad, datos);
  }

  function elegirTipo(t: TipoElementoMedicion) {
    setTipo(t);
    setCampos({});
    setCamposPersonalizados([]);
    setFormula('');
  }

  function agregarCampoPersonalizado() {
    // Compute the next token from the updater's own `c`, not the outer `camposPersonalizados`
    // closure — two calls to this function that land in the same React batch (e.g. a fast
    // double-tap) would otherwise both read the same stale length and hand out the same token
    // twice, exactly the ref/state timing bug fixed in EditorFotoPage's onPointerUp.
    setCamposPersonalizados((c) => {
      const key = TOKENS_PERSONALIZADO[c.length];
      return key ? [...c, { key, label: '' }] : c; // 26 fields is far more than any real formula needs
    });
  }

  function quitarUltimoCampoPersonalizado() {
    // Tokens are positional (a, b, c…), so only the last field can be removed without
    // invalidating whatever the user already typed in the formula referencing earlier tokens.
    setCamposPersonalizados((c) => c.slice(0, -1));
  }

  function renombrarCampoPersonalizado(key: string, label: string) {
    setCamposPersonalizados((c) => c.map((f) => (f.key === key ? { ...f, label } : f)));
  }

  function agregar() {
    if (subtotal <= 0) return;
    if (esPersonalizado) {
      onAgregar({ tipo, descripcion: descripcion.trim(), datos, subtotal, camposPersonalizados, formula: formula.trim() });
      setCamposPersonalizados([]);
      setFormula('');
    } else {
      onAgregar({ tipo, descripcion: descripcion.trim(), datos, subtotal });
    }
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
      <div style={{ background: 'var(--surface-alt)', borderRadius: 10, padding: '8px 4px', marginBottom: 8 }}>
        <FiguraMedidas tipo={tipo} unidad={unidad} campos={campos} camposPersonalizados={camposPersonalizados} formula={formula} />
      </div>
      <input
        placeholder={esPersonalizado ? 'Nombre de la figura (ej: Cuneta triangular)' : 'Descripción (opcional, ej: Zapata Z-1)'}
        value={descripcion}
        onChange={(e) => setDescripcion(e.target.value)}
        className="field-input"
        style={{ width: '100%', marginBottom: 8, fontWeight: 500 }}
      />

      {esPersonalizado ? (
        <>
          {camposPersonalizados.length > 0 && (
            <div className="stack" style={{ gap: 6, marginBottom: 8 }}>
              {camposPersonalizados.map((c, i) => (
                <div key={c.key} className="flex-row gap-8" style={{ alignItems: 'center' }}>
                  <span
                    style={{
                      width: 22, height: 22, borderRadius: 6, background: 'var(--accent-soft)', color: 'var(--accent-dark)',
                      fontSize: 11, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                    }}
                  >
                    {c.key}
                  </span>
                  <input
                    placeholder="Nombre de la medida (ej: Base menor)"
                    value={c.label}
                    onChange={(e) => renombrarCampoPersonalizado(c.key, e.target.value)}
                    className="field-input"
                    style={{ flex: '1 1 120px' }}
                  />
                  <input
                    type="text"
                    inputMode="decimal"
                    placeholder="Valor"
                    value={campos[c.key] ?? ''}
                    onChange={(e) => setCampos((prev) => ({ ...prev, [c.key]: e.target.value }))}
                    className="field-input"
                    style={{ width: 70 }}
                  />
                  {i === camposPersonalizados.length - 1 && (
                    <button
                      onClick={quitarUltimoCampoPersonalizado}
                      aria-label="Quitar esta medida"
                      style={{ background: 'none', border: 'none', color: 'var(--red)', padding: 4, display: 'flex', flexShrink: 0 }}
                    >
                      <IconTrash size={16} color="var(--red)" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
          <button
            onClick={agregarCampoPersonalizado}
            disabled={camposPersonalizados.length >= TOKENS_PERSONALIZADO.length}
            style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700, padding: 0, marginBottom: 8 }}
          >
            + Agregar medida
          </button>
          <label style={{ display: 'block', marginBottom: 8 }}>
            <span className="text-soft" style={{ fontSize: 10.5 }}>
              Fórmula (usa las letras de arriba, ej: {camposPersonalizados.length >= 2 ? `${camposPersonalizados[0].key} * ${camposPersonalizados[1].key}` : 'a * b / 2'})
            </span>
            <input
              placeholder="ej: a * b / 2"
              value={formula}
              onChange={(e) => setFormula(e.target.value)}
              className="field-input"
              style={{ width: '100%', marginTop: 4, fontWeight: 500 }}
            />
          </label>
          {errorFormula && (
            <div style={{ color: 'var(--red)', fontSize: 10.5, marginBottom: 8 }}>{errorFormula}</div>
          )}
        </>
      ) : (
        <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 8 }}>
          {camposActivos.map((c) => (
            <DimField key={c.key} label={c.label} value={campos[c.key] ?? ''} onChange={(v) => setCampos((prev) => ({ ...prev, [c.key]: v }))} />
          ))}
        </div>
      )}

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
