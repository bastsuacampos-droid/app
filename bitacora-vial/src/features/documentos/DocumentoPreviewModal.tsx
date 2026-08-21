import { useEffect, useRef, useState } from 'react';
import * as pdfjsLib from 'pdfjs-dist';
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import pdfjsWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { Capacitor } from '@capacitor/core';
import { db } from '../../lib/db';
import { downloadBlob } from '../../lib/export';
import { formatFileSize, blobToBase64 } from '../../lib/archivos';
import FileOpener from '../../lib/nativeFileOpener';
import { IconX, IconOpenExternal, IconChevronLeft, IconChevronRight } from '../../components/Icon';
import type { Documento } from '../../types/models';

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorkerUrl;

// Renders at the screen's real pixel density so text stays crisp, capped so a huge page on a
// very high-DPR phone doesn't blow up canvas memory for no visible benefit.
const MAX_RENDER_DPR = 2.5;

/** Full-screen preview for an uploaded documento: renders images directly and PDFs page-by-page
 * onto a canvas via pdf.js (a WebView's <iframe>/<embed> can't render PDFs — no built-in PDF
 * plugin like desktop Chrome has, so it just shows blank). Category is fixed at upload time
 * (see ClasificarDocumentoModal) — this view is read-only aside from delete/open-with. */
export function DocumentoPreviewModal({
  documento, onCerrar, onEliminado,
}: {
  documento: Documento;
  onCerrar: () => void;
  onEliminado: () => void;
}) {
  const [url, setUrl] = useState<string | null>(null);
  const [abriendo, setAbriendo] = useState(false);

  useEffect(() => {
    const objectUrl = URL.createObjectURL(documento.blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [documento.id, documento.blob]);

  const esImagen = documento.mime.startsWith('image/');
  const esPdf = documento.mime === 'application/pdf' || /\.pdf$/i.test(documento.nombre);

  async function abrirConOtraApp() {
    if (!Capacitor.isNativePlatform()) {
      await downloadBlob(documento.nombre, documento.blob);
      return;
    }
    setAbriendo(true);
    try {
      const data = await blobToBase64(documento.blob);
      await FileOpener.open({ data, fileName: documento.nombre, mimeType: documento.mime || 'application/octet-stream' });
    } finally {
      setAbriendo(false);
    }
  }

  async function eliminar() {
    if (!confirm(`¿Eliminar "${documento.nombre}"? Esta acción no se puede deshacer.`)) return;
    await db.documentos.delete(documento.id);
    onEliminado();
  }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--bg)', zIndex: 100, display: 'flex', flexDirection: 'column' }}>
      <div
        className="flex-row"
        style={{
          justifyContent: 'space-between', alignItems: 'center', gap: 10, padding: '18px 20px 14px',
          paddingTop: 'calc(18px + var(--safe-top))', flexShrink: 0, borderBottom: '1px solid var(--border)', background: 'var(--surface)',
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div className="disp" style={{ fontSize: 15, fontWeight: 800, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{documento.nombre}</div>
          <div className="text-soft" style={{ fontSize: 11 }}>{documento.categoria} · {formatFileSize(documento.tamano)} · {new Date(documento.createdAt).toLocaleDateString('es-CL')}</div>
        </div>
        <button onClick={onCerrar} aria-label="Cerrar" style={{ background: 'none', border: 'none', padding: 6, display: 'flex', flexShrink: 0 }}>
          <IconX size={18} color="var(--text-soft)" />
        </button>
      </div>

      <div style={{ flex: 1, overflow: 'auto', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#161616' }}>
        {url && esImagen && (
          <img src={url} alt={documento.nombre} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
        )}
        {url && esPdf && <PdfViewer url={url} />}
        {url && !esImagen && !esPdf && (
          <div style={{ textAlign: 'center', color: '#fff', padding: 30 }}>
            <div style={{ fontSize: 13, marginBottom: 6, fontWeight: 600 }}>Vista previa no disponible para este tipo de archivo</div>
            <div style={{ fontSize: 11, opacity: 0.7 }}>Ábrelo con otra app para verlo</div>
          </div>
        )}
      </div>

      <div style={{ flexShrink: 0, background: 'var(--surface)', borderTop: '1px solid var(--border)', padding: '14px 20px 18px' }}>
        <div className="flex-row gap-8">
          <button className="btn btn-outline" style={{ flex: 1 }} onClick={abrirConOtraApp} disabled={abriendo}>
            <IconOpenExternal size={15} /> {abriendo ? 'Abriendo…' : 'Abrir con'}
          </button>
          <button className="btn btn-danger-outline" style={{ flex: 1 }} onClick={eliminar}>Eliminar</button>
        </div>
      </div>
    </div>
  );
}

/** Renders one page of a PDF onto a canvas at a time, at the screen's real pixel density scaled
 * to fit the available width — the WebView has no native PDF renderer, so pdf.js does the
 * decoding + rasterizing itself. */
function PdfViewer({ url }: { url: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null);
  const [pageNum, setPageNum] = useState(1);
  const [estado, setEstado] = useState<'cargando' | 'lista' | 'error'>('cargando');
  const [renderLento, setRenderLento] = useState(false);

  useEffect(() => {
    let cancelado = false;
    setEstado('cargando');
    setPdf(null);
    setPageNum(1);

    // standardFontDataUrl/cMapUrl point at pdfjs-dist's own font-metrics and character-map data
    // (copied into public/pdfjs — see that folder), without which pdf.js renders a blank page:
    // it never fetches them on its own and silently skips drawing any text glyphs.
    pdfjsLib.getDocument({
      url,
      standardFontDataUrl: '/pdfjs/standard_fonts/',
      cMapUrl: '/pdfjs/cmaps/',
      cMapPacked: true,
    }).promise
      .then((doc) => {
        if (cancelado) return;
        setPdf(doc);
        setEstado('lista');
      })
      .catch(() => {
        if (!cancelado) setEstado('error');
      });

    return () => { cancelado = true; };
  }, [url]);

  useEffect(() => {
    if (!pdf || !canvasRef.current || !containerRef.current) return;
    let cancelado = false;
    let renderTask: RenderTask | null = null;
    setRenderLento(false);
    // page.render() can stall indefinitely on some devices/WebView builds instead of failing
    // outright — without this, that leaves the user staring at a blank canvas forever with no
    // way out except backing out of the screen.
    const timeoutId = window.setTimeout(() => { if (!cancelado) setRenderLento(true); }, 8000);

    (async () => {
      const page = await pdf.getPage(pageNum);
      if (cancelado) return;
      const unscaled = page.getViewport({ scale: 1 });
      const containerWidth = containerRef.current!.clientWidth - 32; // breathing room on each side
      const cssScale = Math.max(0.5, containerWidth / unscaled.width);
      // Render at device pixel density so glyphs stay sharp on high-DPR phones — without this
      // the canvas buffer only has as many pixels as CSS width, well below the screen's actual
      // resolution, so everything looks visibly soft/blurry once the browser upscales it.
      const dpr = Math.min(window.devicePixelRatio || 1, MAX_RENDER_DPR);
      const viewport = page.getViewport({ scale: cssScale * dpr });

      const canvas = canvasRef.current!;
      const ctx = canvas.getContext('2d');
      if (!ctx || cancelado) return;
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      canvas.style.width = `${unscaled.width * cssScale}px`;
      canvas.style.height = `${unscaled.height * cssScale}px`;
      renderTask = page.render({ canvas, canvasContext: ctx, viewport });
      try {
        await renderTask.promise;
        window.clearTimeout(timeoutId);
        if (!cancelado) setRenderLento(false);
      } catch {
        // Expected when a newer page/scale supersedes this render (see cleanup below) — ignore.
      }
    })();

    // pdf.js refuses to start a second render on the same canvas while one is still in flight
    // (throws "Cannot use the same canvas during multiple render() operations"), which happens
    // for real whenever pageNum changes again before the previous page finished rendering — not
    // just under React StrictMode's dev-only double-invoke. Cancelling the in-flight task is the
    // documented way to release the canvas for the next one.
    return () => {
      cancelado = true;
      window.clearTimeout(timeoutId);
      renderTask?.cancel();
    };
  }, [pdf, pageNum]);

  if (estado === 'error') {
    return (
      <div style={{ textAlign: 'center', color: '#fff', padding: 30 }}>
        <div style={{ fontSize: 13, fontWeight: 600 }}>No se pudo abrir este PDF</div>
      </div>
    );
  }

  return (
    <div ref={containerRef} style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', overflow: 'auto', padding: '16px 0' }}>
      {estado === 'cargando' && <div className="text-soft" style={{ color: '#fff', fontSize: 12 }}>Abriendo PDF…</div>}
      {renderLento && (
        <div className="text-soft" style={{ color: '#fff', fontSize: 11.5, textAlign: 'center', padding: '0 20px 12px' }}>
          Esta página está tardando más de lo normal en mostrarse.
        </div>
      )}
      <canvas ref={canvasRef} style={{ maxWidth: '100%', boxShadow: '0 4px 20px rgba(0,0,0,.4)', background: '#fff' }} />
      {pdf && pdf.numPages > 1 && (
        <div className="flex-row gap-8" style={{ alignItems: 'center', marginTop: 14, background: 'rgba(255,255,255,.1)', borderRadius: 20, padding: '6px 8px' }}>
          <button
            onClick={() => setPageNum((n) => Math.max(1, n - 1))}
            disabled={pageNum <= 1}
            aria-label="Página anterior"
            style={{ background: 'none', border: 'none', color: '#fff', opacity: pageNum <= 1 ? 0.35 : 1, display: 'flex', padding: 6 }}
          >
            <IconChevronLeft size={16} color="#fff" />
          </button>
          <span style={{ color: '#fff', fontSize: 11.5, fontWeight: 600, minWidth: 52, textAlign: 'center' }}>{pageNum} / {pdf.numPages}</span>
          <button
            onClick={() => setPageNum((n) => Math.min(pdf.numPages, n + 1))}
            disabled={pageNum >= pdf.numPages}
            aria-label="Página siguiente"
            style={{ background: 'none', border: 'none', color: '#fff', opacity: pageNum >= pdf.numPages ? 0.35 : 1, display: 'flex', padding: 6 }}
          >
            <IconChevronRight size={16} color="#fff" />
          </button>
        </div>
      )}
    </div>
  );
}
