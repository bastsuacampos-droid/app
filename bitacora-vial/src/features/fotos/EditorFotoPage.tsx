import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { useNavigate, useParams } from 'react-router-dom';
import { db } from '../../lib/db';
import { IconPencil, IconArrow, IconText, IconSquare, IconCircle, IconUndo, IconTrash } from '../../components/Icon';
import { CampoDesplegable } from '../../components/CampoDesplegable';
import { ETAPA_POR_ID } from '../../lib/etapas';

type Tool = 'lapiz' | 'flecha' | 'texto' | 'rect' | 'circulo';

type Shape =
  | { type: 'lapiz'; points: { x: number; y: number }[]; color: string }
  | { type: 'flecha'; x1: number; y1: number; x2: number; y2: number; color: string }
  | { type: 'texto'; x: number; y: number; text: string; color: string }
  | { type: 'rect'; x1: number; y1: number; x2: number; y2: number; color: string; opacity: number }
  | { type: 'circulo'; x1: number; y1: number; x2: number; y2: number; color: string; opacity: number };

const COLORS = ['#e8600c', '#f2b705', '#c0392b', '#ffffff', '#1a1a1a'];
const TOOLS: { id: Tool; label: string; Icon: typeof IconPencil }[] = [
  { id: 'lapiz', label: 'Lápiz', Icon: IconPencil },
  { id: 'flecha', label: 'Flecha', Icon: IconArrow },
  { id: 'texto', label: 'Texto', Icon: IconText },
  { id: 'rect', label: 'Zona', Icon: IconSquare },
  { id: 'circulo', label: 'Círculo', Icon: IconCircle },
];

export function EditorFotoPage() {
  const { fotoId } = useParams();
  const navigate = useNavigate();
  const foto = useLiveQuery(() => (fotoId ? db.fotos.get(fotoId) : undefined), [fotoId]);
  const frente = useLiveQuery(() => (foto ? db.frentes.get(foto.frenteId) : undefined), [foto?.frenteId]);
  const tarea = useLiveQuery(() => (foto?.partidaId ? db.partidas.get(foto.partidaId) : undefined), [foto?.partidaId]);
  const frentes = useLiveQuery(() => db.frentes.filter((f) => f.activo).toArray(), []) ?? [];

  // Abre siempre en modo "ver" (solo la foto, sin herramientas) — antes saltaba directo a
  // edición apenas se abría una foto, lo que hacía sentir la galería como un editor forzado en
  // vez de un visor. "Editar" es una acción explícita desde la vista.
  const [modo, setModo] = useState<'ver' | 'editar'>('ver');
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);
  // Object URL for the plain <img> shown in modo "ver" — separate from imgRef, which the canvas
  // draws from once editing starts.
  const [imgUrl, setImgUrl] = useState('');
  // CSS-pixel display size — the canvas's own pixel buffer is this times `dpr` (see below), so
  // shape coordinates and pointer coordinates can both stay in this one simple space regardless
  // of screen density.
  const [size, setSize] = useState({ w: 360, h: 480 });
  const [tool, setTool] = useState<Tool>('lapiz');
  const [color, setColor] = useState(COLORS[0]);
  const [opacity, setOpacity] = useState(0.35);
  const [shapes, setShapes] = useState<Shape[]>([]);
  const draftRef = useRef<Shape | null>(null);
  const dpr = Math.min(window.devicePixelRatio || 1, 2.5);

  function fitToContainer() {
    const img = imgRef.current;
    const container = containerRef.current;
    if (!img || !container) return;
    const cw = container.clientWidth;
    const ch = container.clientHeight;
    if (!cw || !ch) return;
    // Fit the whole photo inside the available space without distorting it — the previous
    // version capped height without scaling width to match, which stretched/squished the image
    // (and everything drawn on it) whenever a photo's aspect ratio didn't fit the guessed box.
    const scale = Math.min(cw / img.naturalWidth, ch / img.naturalHeight);
    setSize({ w: Math.round(img.naturalWidth * scale), h: Math.round(img.naturalHeight * scale) });
  }

  useEffect(() => {
    if (!foto) return;
    const url = URL.createObjectURL(foto.blob);
    setImgUrl(url);
    const img = new Image();
    img.onload = () => {
      imgRef.current = img;
      fitToContainer();
    };
    img.src = url;
    return () => URL.revokeObjectURL(url);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [foto?.id]);

  useEffect(() => {
    window.addEventListener('resize', fitToContainer);
    return () => window.removeEventListener('resize', fitToContainer);
  }, []);

  function redraw() {
    const canvas = canvasRef.current;
    const img = imgRef.current;
    if (!canvas || !img) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    // The canvas's physical pixel buffer is `size * dpr` (set on the element below) so drawing
    // stays sharp on high-density screens; scaling the context lets every coordinate below stay
    // in plain CSS-pixel space, matching what pointFromEvent() reads from getBoundingClientRect().
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, size.w, size.h);
    ctx.drawImage(img, 0, 0, size.w, size.h);
    for (const shape of [...shapes, draftRef.current].filter(Boolean) as Shape[]) {
      drawShape(ctx, shape);
    }
  }

  useEffect(redraw, [shapes, size]);

  function drawShape(ctx: CanvasRenderingContext2D, shape: Shape) {
    ctx.save();
    ctx.strokeStyle = shape.color;
    ctx.fillStyle = shape.color;
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (shape.type === 'lapiz') {
      ctx.beginPath();
      shape.points.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
      ctx.stroke();
    } else if (shape.type === 'flecha') {
      const { x1, y1, x2, y2 } = shape;
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();
      const angle = Math.atan2(y2 - y1, x2 - x1);
      ctx.beginPath();
      ctx.moveTo(x2, y2);
      ctx.lineTo(x2 - 12 * Math.cos(angle - Math.PI / 6), y2 - 12 * Math.sin(angle - Math.PI / 6));
      ctx.moveTo(x2, y2);
      ctx.lineTo(x2 - 12 * Math.cos(angle + Math.PI / 6), y2 - 12 * Math.sin(angle + Math.PI / 6));
      ctx.stroke();
    } else if (shape.type === 'texto') {
      ctx.font = '600 16px "IBM Plex Sans", sans-serif';
      ctx.textBaseline = 'top';
      const metrics = ctx.measureText(shape.text);
      ctx.globalAlpha = 0.85;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(shape.x - 4, shape.y - 3, metrics.width + 8, 22);
      ctx.globalAlpha = 1;
      ctx.fillStyle = shape.color;
      ctx.fillText(shape.text, shape.x, shape.y);
    } else if (shape.type === 'rect') {
      const x = Math.min(shape.x1, shape.x2);
      const y = Math.min(shape.y1, shape.y2);
      const w = Math.abs(shape.x2 - shape.x1);
      const h = Math.abs(shape.y2 - shape.y1);
      ctx.globalAlpha = shape.opacity;
      ctx.fillRect(x, y, w, h);
      ctx.globalAlpha = 1;
      ctx.lineWidth = 2.5;
      ctx.strokeRect(x, y, w, h);
    } else if (shape.type === 'circulo') {
      const cx = (shape.x1 + shape.x2) / 2;
      const cy = (shape.y1 + shape.y2) / 2;
      const rx = Math.abs(shape.x2 - shape.x1) / 2;
      const ry = Math.abs(shape.y2 - shape.y1) / 2;
      ctx.beginPath();
      ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
      ctx.globalAlpha = shape.opacity;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.lineWidth = 2.5;
      ctx.stroke();
    }
    ctx.restore();
  }

  function pointFromEvent(e: React.PointerEvent<HTMLCanvasElement>) {
    const rect = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  function onPointerDown(e: React.PointerEvent<HTMLCanvasElement>) {
    const { x, y } = pointFromEvent(e);
    if (tool === 'texto') {
      const text = window.prompt('Texto para la anotación:');
      if (text && text.trim()) {
        setShapes((s) => [...s, { type: 'texto', x, y, text: text.trim(), color }]);
      }
      return;
    }
    // touch-action: none on the canvas (below) should already stop the WebView from treating
    // this as a scroll/pan gesture, but preventDefault() here is needed too on some Android
    // WebView builds where CSS alone doesn't fully suppress the default touch handling.
    e.preventDefault();
    try {
      // Keeps the stroke tracking even if the finger drifts off the canvas mid-draw. Some
      // WebView versions throw for a touch pointerId here — if it does, drawing still works,
      // it just won't follow the finger past the canvas edge, so failure here isn't fatal.
      canvasRef.current!.setPointerCapture(e.pointerId);
    } catch {
      // ignored — see comment above
    }
    if (tool === 'lapiz') {
      draftRef.current = { type: 'lapiz', points: [{ x, y }], color };
    } else if (tool === 'flecha') {
      draftRef.current = { type: 'flecha', x1: x, y1: y, x2: x, y2: y, color };
    } else if (tool === 'rect') {
      draftRef.current = { type: 'rect', x1: x, y1: y, x2: x, y2: y, color, opacity };
    } else if (tool === 'circulo') {
      draftRef.current = { type: 'circulo', x1: x, y1: y, x2: x, y2: y, color, opacity };
    }
    redraw();
  }

  function onPointerMove(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!draftRef.current) return;
    e.preventDefault();
    const { x, y } = pointFromEvent(e);
    const d = draftRef.current;
    if (d.type === 'lapiz') {
      d.points.push({ x, y });
    } else if (d.type === 'flecha' || d.type === 'rect' || d.type === 'circulo') {
      d.x2 = x;
      d.y2 = y;
    }
    redraw();
  }

  function onPointerUp(e: React.PointerEvent<HTMLCanvasElement>) {
    if (draftRef.current) {
      e.preventDefault();
      // Capture the shape into a local before clearing the ref: setShapes's updater runs later
      // (during React's batched re-render), not synchronously here, so if it read
      // draftRef.current directly it would see the null we're about to assign instead of the
      // finished stroke — which is exactly why the stroke was vanishing on release.
      const finished = draftRef.current;
      draftRef.current = null;
      setShapes((s) => [...s, finished]);
    }
  }

  // Android WebView sometimes fires pointercancel instead of pointerup at the end of a touch
  // gesture (e.g. if it briefly considers the movement a scroll/other native gesture). Without
  // this, that stroke's draft was silently dropped on the next redraw instead of being kept —
  // it would visibly draw while dragging, then vanish the moment the finger lifted.
  const onPointerCancel = onPointerUp;

  function undo() {
    setShapes((s) => s.slice(0, -1));
  }

  function entrarAEditar() {
    setShapes([]);
    setModo('editar');
  }

  function cancelarEdicion() {
    setShapes([]);
    setModo('ver');
  }

  async function guardar() {
    const canvas = canvasRef.current;
    if (!canvas || !foto) return;
    canvas.toBlob(async (blob) => {
      if (!blob) return;
      await db.fotos.update(foto.id, { blob, anotada: shapes.length > 0 || foto.anotada });
      navigate(`/fotos?parte=${foto.parteId}`);
    }, 'image/jpeg', 0.9);
  }

  async function eliminar() {
    if (!foto) return;
    if (!confirm('¿Eliminar esta foto? Esta acción no se puede deshacer.')) return;
    await db.fotos.delete(foto.id);
    navigate(`/fotos?parte=${foto.parteId}`);
  }

  if (!foto) return null;

  const hora = new Date(foto.capturedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' });

  return (
    <div style={{ background: '#000', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div
        className="flex-row"
        style={{ justifyContent: 'space-between', padding: '18px 16px 12px', paddingTop: 'calc(18px + var(--safe-top))', color: '#fff' }}
      >
        <button onClick={() => navigate(`/fotos?parte=${foto.parteId}`)} style={{ background: 'none', border: 'none', color: '#fff' }} aria-label="Cerrar">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth={2.2} strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
        </button>
        <div style={{ textAlign: 'center', minWidth: 0, flex: 1 }}>
          <div style={{ fontSize: 12.5, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{tarea?.nombre ?? frente?.nombre ?? 'Foto'}</div>
          <div className="flex-row gap-8" style={{ justifyContent: 'center', fontSize: 10, color: '#d8d3c8' }}>
            <span style={{ color: ETAPA_POR_ID.get(foto.etapa)?.color, fontWeight: 700 }}>{ETAPA_POR_ID.get(foto.etapa)?.label}</span>
            <span>{hora}{frente?.km ? ` · ${frente.km}` : ''}</span>
          </div>
        </div>
        <div className="flex-row gap-8" style={{ flexShrink: 0, alignItems: 'center' }}>
          <button onClick={eliminar} style={{ background: 'none', border: 'none', color: 'var(--red)', display: 'flex', padding: 4 }} aria-label="Eliminar foto">
            <IconTrash size={19} color="var(--red)" />
          </button>
          {modo === 'ver' ? (
            <button onClick={entrarAEditar} className="flex-row gap-6" style={{ alignItems: 'center', background: 'var(--accent)', border: 'none', borderRadius: 20, padding: '7px 14px', color: '#fff', fontSize: 12.5, fontWeight: 700 }}>
              <IconPencil size={14} color="#fff" /> Editar
            </button>
          ) : (
            <button onClick={guardar} style={{ background: 'none', border: 'none', color: 'var(--amber)', fontSize: 13.5, fontWeight: 800 }}>
              Guardar
            </button>
          )}
        </div>
      </div>

      <div ref={containerRef} style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
        {modo === 'ver' ? (
          imgUrl && <img src={imgUrl} alt="" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', borderRadius: 8 }} />
        ) : (
          <canvas
            ref={canvasRef}
            width={Math.round(size.w * dpr)}
            height={Math.round(size.h * dpr)}
            style={{ width: size.w, height: size.h, touchAction: 'none', borderRadius: 8 }}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerCancel}
          />
        )}
      </div>

      {modo === 'ver' && (
        <div className="stack" style={{ gap: 10, padding: '12px 16px calc(16px + var(--safe-bottom, 0px))', flexShrink: 0 }}>
          <div>
            <div style={{ color: '#d8d3c8', fontSize: 10, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 5 }}>
              Frente
            </div>
            <CampoDesplegable
              valor={foto.frenteId}
              opciones={frentes.map((f) => ({ value: f.id, label: f.nombre + (f.km ? ` · ${f.km}` : '') }))}
              onSeleccionar={(id) => db.fotos.update(foto.id, { frenteId: id })}
              ancho="100%"
              estiloBoton={{ background: 'rgba(255,255,255,.08)', border: 'none', color: '#fff', borderRadius: 10, padding: '10px 12px', fontSize: 13, fontWeight: 600 }}
            />
          </div>
          <div>
            <div style={{ color: '#d8d3c8', fontSize: 10, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 5 }}>
              Comentario
            </div>
            <textarea
              value={foto.comentario ?? ''}
              onChange={(e) => db.fotos.update(foto.id, { comentario: e.target.value })}
              placeholder="Agrega un comentario a esta foto…"
              rows={2}
              style={{ width: '100%', background: 'rgba(255,255,255,.08)', border: 'none', borderRadius: 10, padding: '10px 12px', color: '#fff', fontSize: 13, resize: 'vertical' }}
            />
          </div>
        </div>
      )}

      {modo === 'editar' && (
        <>
          <div className="flex-row gap-8" style={{ padding: '12px 16px 10px', alignItems: 'center' }}>
            <button onClick={cancelarEdicion} style={{ background: 'none', border: 'none', color: '#d8d3c8', fontSize: 11.5, fontWeight: 700, marginRight: 2 }}>
              Cancelar
            </button>
            {COLORS.map((c) => (
              <button
                key={c}
                onClick={() => setColor(c)}
                style={{
                  width: 26, height: 26, borderRadius: '50%', background: c, flexShrink: 0,
                  border: color === c ? '2px solid #fff' : '2px solid rgba(255,255,255,.35)',
                }}
                aria-label={`Color ${c}`}
              />
            ))}
            <div className="flex-row gap-8" style={{ flexGrow: 1, marginLeft: 6 }}>
              <span style={{ color: '#d8d3c8', fontSize: 10, whiteSpace: 'nowrap' }}>Opacidad</span>
              <input
                type="range" min={10} max={90} value={Math.round(opacity * 100)}
                onChange={(e) => setOpacity(Number(e.target.value) / 100)}
                style={{ flexGrow: 1 }}
              />
              <span style={{ color: '#fff', fontSize: 10, fontWeight: 700 }}>{Math.round(opacity * 100)}%</span>
            </div>
          </div>

          <div style={{ background: 'rgba(20,18,15,.88)', padding: '10px 10px 20px', display: 'flex', justifyContent: 'space-around' }}>
            {TOOLS.map(({ id, label, Icon }) => (
              <button
                key={id}
                onClick={() => setTool(id)}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5, background: 'none', border: 'none', color: tool === id ? '#fff' : '#d8d3c8' }}
              >
                <div style={{ width: 42, height: 42, borderRadius: 12, background: tool === id ? 'var(--accent)' : 'rgba(255,255,255,.08)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Icon color={tool === id ? '#fff' : '#d8d3c8'} />
                </div>
                <span style={{ fontSize: 9 }}>{label}</span>
              </button>
            ))}
            <button onClick={undo} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5, background: 'none', border: 'none', color: '#d8d3c8' }}>
              <div style={{ width: 42, height: 42, borderRadius: 12, background: 'rgba(255,255,255,.08)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <IconUndo color="#d8d3c8" />
              </div>
              <span style={{ fontSize: 9 }}>Deshacer</span>
            </button>
          </div>
        </>
      )}
    </div>
  );
}
