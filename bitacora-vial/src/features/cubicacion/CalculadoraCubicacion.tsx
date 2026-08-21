import { useState } from 'react';
import { parseNumeroDecimal } from '../../lib/numero';
import { TIPOS_POR_UNIDAD, camposDelTipo, calcularSubtotalElemento } from '../../lib/cubicacionCalculo';
import type { MedicionInfo } from '../../lib/cubicacionCalculo';
import { evaluarFormula, FormulaError } from '../../lib/formulaEval';
import type { CampoPersonalizado, TipoElementoMedicion } from '../../types/models';
import { IconTrash } from '../../components/Icon';
import { FiguraMedidas } from './FiguraMedidas';

/** Short, unambiguous tokens for personalizado fields — used internally (as the identifier
 * evaluarFormula resolves) regardless of what the user types as the field's label. Never shown
 * to the user: the formula builder below always displays a field's real label instead. */
const TOKENS_PERSONALIZADO = 'abcdefghijklmnopqrstuvwxyz'.split('');

const OPERADORES_FORMULA: { symbol: string; display: string }[] = [
  { symbol: '+', display: '+' },
  { symbol: '-', display: '−' },
  { symbol: '*', display: '×' },
  { symbol: '/', display: '÷' },
  { symbol: '(', display: '(' },
  { symbol: ')', display: ')' },
];

/** One piece of a personalizado formula being built by tapping, in entry order. A 'campo' token
 * only stores the field's key — its display label is looked up live from camposPersonalizados,
 * so renaming a field after adding it to the formula updates that chip automatically. */
type TokenFormula =
  | { type: 'campo'; key: string }
  | { type: 'op'; symbol: string; display: string }
  | { type: 'numero'; texto: string };

function formulaDesdeTokens(tokens: TokenFormula[]): string {
  // Joined with spaces (not concatenated) so two adjacent field tokens, e.g. "a" then "b" typed
  // back to back with no operator between them, can never fuse into one bogus identifier "ab" —
  // the formula tokenizer treats whitespace as an ordinary separator either way.
  return tokens.map((t) => (t.type === 'campo' ? t.key : t.type === 'op' ? t.symbol : t.texto)).join(' ');
}

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
  // whatever label the foreman gives it) plus a formula built by tapping — never typed — so
  // nobody has to learn or remember what "a" or "b" stand for. Field values themselves still
  // live in `campos` (keyed by token), same as every other tipo.
  const [camposPersonalizados, setCamposPersonalizados] = useState<CampoPersonalizado[]>([]);
  const [tokensFormula, setTokensFormula] = useState<TokenFormula[]>([]);
  const [numeroPendiente, setNumeroPendiente] = useState('');
  const formula = formulaDesdeTokens(tokensFormula);

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
    setTokensFormula([]);
    setNumeroPendiente('');
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
    // invalidating whatever the formula already references by an earlier token.
    setCamposPersonalizados((c) => c.slice(0, -1));
  }

  function renombrarCampoPersonalizado(key: string, label: string) {
    setCamposPersonalizados((c) => c.map((f) => (f.key === key ? { ...f, label } : f)));
  }

  function agregarTokenFormula(token: TokenFormula) {
    setTokensFormula((t) => [...t, token]);
  }

  function insertarNumeroFormula() {
    if (!numeroPendiente.trim()) return;
    agregarTokenFormula({ type: 'numero', texto: numeroPendiente.trim() });
    setNumeroPendiente('');
  }

  function quitarTokenFormula(idx: number) {
    setTokensFormula((t) => t.filter((_, i) => i !== idx));
  }

  function vaciarFormula() {
    setTokensFormula([]);
  }

  function agregar() {
    if (subtotal <= 0) return;
    if (esPersonalizado) {
      onAgregar({ tipo, descripcion: descripcion.trim(), datos, subtotal, camposPersonalizados, formula: formula.trim() });
      setCamposPersonalizados([]);
      setTokensFormula([]);
      setNumeroPendiente('');
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
          <div className="text-soft" style={{ fontSize: 10.5, marginBottom: 4 }}>1. Agrega y nombra tus medidas</div>
          {camposPersonalizados.length > 0 && (
            <div className="stack" style={{ gap: 6, marginBottom: 8 }}>
              {camposPersonalizados.map((c, i) => (
                <div key={c.key} className="flex-row gap-8" style={{ alignItems: 'center' }}>
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
            style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: 11.5, fontWeight: 700, padding: 0, marginBottom: 14 }}
          >
            + Agregar medida
          </button>

          <div className="text-soft" style={{ fontSize: 10.5, marginBottom: 4 }}>2. Arma la fórmula tocando medidas y signos</div>
          <div
            className="flex-row gap-8"
            style={{
              flexWrap: 'wrap', minHeight: 38, alignItems: 'center', background: 'var(--surface)',
              border: '1px solid var(--border)', borderRadius: 8, padding: '6px 8px', marginBottom: 8,
            }}
          >
            {tokensFormula.length === 0 && (
              <span className="text-soft" style={{ fontSize: 11 }}>Vacía — toca las medidas y signos de abajo</span>
            )}
            {tokensFormula.map((t, i) => (
              <button
                key={i}
                onClick={() => quitarTokenFormula(i)}
                aria-label="Quitar de la fórmula"
                className="chip"
                style={{
                  padding: '3px 10px', fontSize: 13, fontWeight: 700,
                  background: t.type === 'campo' ? 'var(--accent-soft)' : 'var(--surface-alt)',
                  color: t.type === 'campo' ? 'var(--accent-dark)' : 'var(--text)',
                  borderColor: 'transparent',
                }}
              >
                {t.type === 'campo' ? (camposPersonalizados.find((c) => c.key === t.key)?.label || 'medida') : t.type === 'op' ? t.display : t.texto}
              </button>
            ))}
          </div>

          {camposPersonalizados.length > 0 && (
            <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 6 }}>
              {camposPersonalizados.map((c) => (
                <button
                  key={c.key}
                  onClick={() => agregarTokenFormula({ type: 'campo', key: c.key })}
                  className="chip"
                  style={{ background: 'var(--accent-soft)', borderColor: 'var(--accent-soft)', color: 'var(--accent-dark)', fontWeight: 700 }}
                >
                  {c.label || 'medida'}
                </button>
              ))}
            </div>
          )}
          <div className="flex-row gap-8" style={{ flexWrap: 'wrap', marginBottom: 8, alignItems: 'center' }}>
            {OPERADORES_FORMULA.map((op) => (
              <button
                key={op.symbol}
                onClick={() => agregarTokenFormula({ type: 'op', symbol: op.symbol, display: op.display })}
                className="chip"
                style={{ width: 38, textAlign: 'center', fontWeight: 800, fontSize: 15 }}
              >
                {op.display}
              </button>
            ))}
            <input
              type="text"
              inputMode="decimal"
              placeholder="Número"
              value={numeroPendiente}
              onChange={(e) => setNumeroPendiente(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') insertarNumeroFormula(); }}
              className="field-input"
              style={{ width: 72 }}
            />
            <button onClick={insertarNumeroFormula} className="chip" disabled={!numeroPendiente.trim()}>
              Insertar
            </button>
            {tokensFormula.length > 0 && (
              <button
                onClick={vaciarFormula}
                style={{ background: 'none', border: 'none', color: 'var(--red)', fontSize: 11, fontWeight: 700, padding: '0 4px' }}
              >
                Vaciar
              </button>
            )}
          </div>
          {errorFormula && tokensFormula.length > 0 && (
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
