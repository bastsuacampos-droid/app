import type { TipoElementoMedicion } from '../../types/models';

const ARROW_ID = 'figura-cota-flecha';
const HATCH_ID = 'figura-achurado';

/** Shared <defs> for every figure: the cota arrowhead (elongated ~3:1, per the standard
 * dimensioning convention) and a 45° diagonal hatch pattern — the "achurado" real construction
 * plans use to mark solid/cut material, as opposed to open space (a vano, air). */
function DefsFigura() {
  return (
    <defs>
      <marker id={ARROW_ID} viewBox="0 0 9 3" refX="8" refY="1.5" markerWidth="9" markerHeight="3" orient="auto-start-reverse">
        <path d="M0,0 L9,1.5 L0,3 Z" fill="var(--accent)" />
      </marker>
      <pattern id={HATCH_ID} width={6} height={6} patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
        <line x1={0} y1={0} x2={0} y2={6} stroke="var(--border)" strokeWidth={1} />
      </pattern>
    </defs>
  );
}

/** Horizontal cota: witness lines run from the object's edge (small gap, per convention) past
 * the dimension line, which carries the arrowheads and the centered label. Pass `desde` (the
 * object edge's y) to draw those witness lines; omit it for a cota that deliberately crosses
 * straight through the shape (e.g. a diameter). */
function CotaH({ x1, x2, y, label, desde }: { x1: number; x2: number; y: number; label: string; desde?: number }) {
  const testigos = desde !== undefined && (() => {
    const dir = y > desde ? 1 : -1;
    const y1 = desde + 3 * dir;
    const y2 = y + 4 * dir;
    return (
      <>
        <line x1={x1} y1={y1} x2={x1} y2={y2} stroke="var(--border)" strokeWidth={1} />
        <line x1={x2} y1={y1} x2={x2} y2={y2} stroke="var(--border)" strokeWidth={1} />
      </>
    );
  })();
  return (
    <g>
      {testigos}
      <line x1={x1} y1={y} x2={x2} y2={y} stroke="var(--accent)" strokeWidth={1} markerStart={`url(#${ARROW_ID})`} markerEnd={`url(#${ARROW_ID})`} />
      <text x={(x1 + x2) / 2} y={y - 6} textAnchor="middle" fontSize={10} fontWeight={700} fill="var(--accent-dark)">{label}</text>
    </g>
  );
}

/** Vertical cota — same witness-line convention as CotaH, label sits to the left of the line. */
function CotaV({ y1, y2, x, label, desde }: { y1: number; y2: number; x: number; label: string; desde?: number }) {
  const testigos = desde !== undefined && (() => {
    const dir = x > desde ? 1 : -1;
    const x1 = desde + 3 * dir;
    const x2 = x + 4 * dir;
    return (
      <>
        <line x1={x1} y1={y1} x2={x2} y2={y1} stroke="var(--border)" strokeWidth={1} />
        <line x1={x1} y1={y2} x2={x2} y2={y2} stroke="var(--border)" strokeWidth={1} />
      </>
    );
  })();
  return (
    <g>
      {testigos}
      <line x1={x} y1={y1} x2={x} y2={y2} stroke="var(--accent)" strokeWidth={1} markerStart={`url(#${ARROW_ID})`} markerEnd={`url(#${ARROW_ID})`} />
      <text x={x - 8} y={(y1 + y2) / 2} textAnchor="end" dominantBaseline="middle" fontSize={10} fontWeight={700} fill="var(--accent-dark)">{label}</text>
    </g>
  );
}

/** Diagonal cota drawn right along a depth/extrusion edge (e.g. "ancho" going back in a 3D
 * box) — the edge itself doubles as the dimension line, label offset to its side. */
function CotaDiag({ x1, y1, x2, y2, label, dx = 8, dy = -6 }: { x1: number; y1: number; x2: number; y2: number; label: string; dx?: number; dy?: number }) {
  return (
    <g>
      <line x1={x1} y1={y1} x2={x2} y2={y2} stroke="var(--accent)" strokeWidth={1} markerStart={`url(#${ARROW_ID})`} markerEnd={`url(#${ARROW_ID})`} />
      <text x={(x1 + x2) / 2 + dx} y={(y1 + y2) / 2 + dy} fontSize={10} fontWeight={700} fill="var(--accent-dark)">{label}</text>
    </g>
  );
}

function BadgeCantidad({ n }: { n: number }) {
  if (n <= 1) return null;
  return (
    <g>
      <rect x={224} y={12} width={40} height={20} rx={10} fill="var(--accent-soft)" />
      <text x={244} y={26} textAnchor="middle" fontSize={11} fontWeight={800} fill="var(--accent-dark)">×{n}</text>
    </g>
  );
}

const SVG_PROPS = { viewBox: '-16 0 296 180', width: '100%', style: { maxWidth: 320, display: 'block', margin: '0 auto' } as const };
const TRAZO = { stroke: 'var(--text)', strokeWidth: 1.6, fill: 'none' } as const;
const TRAZO_ACHURADO = { stroke: 'var(--text)', strokeWidth: 1.6, fill: `url(#${HATCH_ID})` } as const;
const TRAZO_OCULTO = { stroke: 'var(--text-soft)', strokeWidth: 1.2, fill: 'none', strokeDasharray: '3 3' } as const;

/** value in campos → cota label text ("3,2 m" or a muted placeholder "— m" while empty), so the
 * figure always shows which field feeds which measurement, filled in or not. */
function textoCota(campos: Record<string, string>, key: string, unidad: string): string {
  const v = campos[key]?.trim();
  return `${v || '—'} ${unidad}`;
}

function n(campos: Record<string, string>, key: string): number {
  const v = parseFloat((campos[key] ?? '').replace(',', '.'));
  return Number.isFinite(v) ? v : 0;
}

function FiguraPrisma({ campos }: { campos: Record<string, string> }) {
  return (
    <svg {...SVG_PROPS}>
      <DefsFigura />
      {/* back-hidden edges, from the corner behind the box */}
      <path d="M55,140 L110,105 M110,105 L220,105 M110,105 L110,25" {...TRAZO_OCULTO} />
      {/* front face achurada (material lleno), top and right faces sin achurar */}
      <path d="M55,140 L165,140 L165,60 L55,60 Z" {...TRAZO_ACHURADO} />
      <path d="M55,60 L110,25 L220,25 L165,60" {...TRAZO} />
      <path d="M165,140 L220,105 L220,25" {...TRAZO} />
      <BadgeCantidad n={n(campos, 'cantidad')} />
      <CotaH x1={55} x2={165} y={158} desde={140} label={textoCota(campos, 'largo', 'm')} />
      <CotaV y1={60} y2={140} x={36} desde={55} label={textoCota(campos, 'alto', 'm')} />
      <CotaDiag x1={55} y1={60} x2={110} y2={25} label={textoCota(campos, 'ancho', 'm')} dx={-4} dy={-10} />
    </svg>
  );
}

function FiguraRectangulo({ campos }: { campos: Record<string, string> }) {
  return (
    <svg {...SVG_PROPS}>
      <DefsFigura />
      <rect x={60} y={50} width={160} height={90} {...TRAZO_ACHURADO} />
      <BadgeCantidad n={n(campos, 'cantidad')} />
      <CotaH x1={60} x2={220} y={158} desde={140} label={textoCota(campos, 'largo', 'm')} />
      <CotaV y1={50} y2={140} x={42} desde={60} label={textoCota(campos, 'ancho', 'm')} />
    </svg>
  );
}

function FiguraLinea({ campos }: { campos: Record<string, string> }) {
  return (
    <svg {...SVG_PROPS}>
      <DefsFigura />
      <line x1={40} y1={90} x2={240} y2={90} stroke="var(--text)" strokeWidth={5} strokeLinecap="round" />
      <line x1={40} y1={78} x2={40} y2={102} stroke="var(--text)" strokeWidth={1.6} />
      <line x1={240} y1={78} x2={240} y2={102} stroke="var(--text)" strokeWidth={1.6} />
      <BadgeCantidad n={n(campos, 'cantidad')} />
      <CotaH x1={40} x2={240} y={122} desde={102} label={textoCota(campos, 'largo', 'm')} />
    </svg>
  );
}

function FiguraTrapecio({ campos }: { campos: Record<string, string> }) {
  return (
    <svg {...SVG_PROPS}>
      <DefsFigura />
      <path d="M50,140 L105,105 M105,105 L225,105 M105,105 L140,35" {...TRAZO_OCULTO} />
      <path d="M50,140 L170,140 L135,70 L85,70 Z" {...TRAZO_ACHURADO} />
      <path d="M85,70 L140,35 L190,35 L135,70" {...TRAZO} />
      <path d="M170,140 L225,105 L190,35" {...TRAZO} />
      <BadgeCantidad n={n(campos, 'cantidad')} />
      <CotaH x1={50} x2={170} y={158} desde={140} label={textoCota(campos, 'baseMayor', 'm')} />
      <CotaH x1={85} x2={135} y={54} desde={70} label={textoCota(campos, 'baseMenor', 'm')} />
      <CotaV y1={70} y2={140} x={30} desde={50} label={textoCota(campos, 'alto', 'm')} />
      <CotaDiag x1={170} y1={140} x2={225} y2={105} label={textoCota(campos, 'largo', 'm')} dx={10} dy={4} />
    </svg>
  );
}

function FiguraCilindro({ campos }: { campos: Record<string, string> }) {
  return (
    <svg {...SVG_PROPS}>
      <DefsFigura />
      <line x1={80} y1={55} x2={80} y2={140} {...TRAZO} />
      <line x1={200} y1={55} x2={200} y2={140} {...TRAZO} />
      <path d="M80,140 A60,18 0 0 0 200,140" {...TRAZO} />
      <ellipse cx={140} cy={55} rx={60} ry={18} {...TRAZO_ACHURADO} />
      <BadgeCantidad n={n(campos, 'cantidad')} />
      <CotaH x1={80} x2={200} y={55} label={`Ø ${textoCota(campos, 'diametro', 'm')}`} />
      <CotaV y1={55} y2={140} x={62} desde={80} label={textoCota(campos, 'alto', 'm')} />
    </svg>
  );
}

function FiguraMuroVanos({ campos }: { campos: Record<string, string> }) {
  return (
    <svg {...SVG_PROPS}>
      <DefsFigura />
      <path d="M50,50 L220,50 L220,140 L50,140 Z M110,72 L160,72 L160,118 L110,118 Z" fillRule="evenodd" {...TRAZO_ACHURADO} />
      <rect x={110} y={72} width={50} height={46} stroke="var(--text)" strokeWidth={1.2} fill="none" />
      <line x1={160} y1={95} x2={200} y2={95} stroke="var(--text-soft)" strokeWidth={1} strokeDasharray="2 2" />
      <text x={202} y={99} fontSize={9.5} fontWeight={600} fill="var(--text-soft)">{`Vanos: ${textoCota(campos, 'vanos', 'm²')}`}</text>
      <BadgeCantidad n={n(campos, 'cantidad')} />
      <CotaH x1={50} x2={220} y={158} desde={140} label={textoCota(campos, 'largo', 'm')} />
      <CotaV y1={50} y2={140} x={32} desde={50} label={textoCota(campos, 'alto', 'm')} />
    </svg>
  );
}

function FiguraBarra({ campos }: { campos: Record<string, string> }) {
  return (
    <svg {...SVG_PROPS}>
      <DefsFigura />
      <line x1={30} y1={90} x2={195} y2={90} stroke="var(--text)" strokeWidth={7} strokeLinecap="round" />
      <circle cx={232} cy={90} r={17} {...TRAZO} />
      <BadgeCantidad n={n(campos, 'cantidad')} />
      <CotaH x1={30} x2={195} y={112} desde={94} label={textoCota(campos, 'longitud', 'm')} />
      <CotaH x1={215} x2={249} y={58} desde={73} label={`Ø ${textoCota(campos, 'diametro', 'mm')}`} />
    </svg>
  );
}

/** Live schematic of the element being cubicado, with cotas (dimension lines, following the
 * real construction-plan convention: witness lines with a gap from the object, elongated
 * arrowheads, diagonal achurado on solid material) reading straight from the campos the
 * foreman is typing — so it's clear at a glance which line in the drawing each field
 * corresponds to, before committing to "Agregar". Purely illustrative proportions, not to
 * scale with the actual numbers. */
export function FiguraMedidas({ tipo, unidad, campos }: { tipo: TipoElementoMedicion; unidad: string; campos: Record<string, string> }) {
  switch (tipo) {
    case 'rectangular':
      if (unidad === 'm³') return <FiguraPrisma campos={campos} />;
      if (unidad === 'm²') return <FiguraRectangulo campos={campos} />;
      return <FiguraLinea campos={campos} />;
    case 'trapezoidal':
      return <FiguraTrapecio campos={campos} />;
    case 'cilindrico':
      return <FiguraCilindro campos={campos} />;
    case 'muro_vanos':
      return <FiguraMuroVanos campos={campos} />;
    case 'enfierradura':
      return <FiguraBarra campos={campos} />;
    default:
      return null;
  }
}
