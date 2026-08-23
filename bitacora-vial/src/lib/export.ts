import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { Capacitor } from '@capacitor/core';
import { db, todayISO } from './db';
import type { HorasExtraPorTrabajador, SabadosPorTrabajador } from './queries';
import { formatShortDate } from './date';
import FileOpener, { writeFileChunked } from './nativeFileOpener';

// When this build runs inside a published Claude Artifact preview, a plain <a download>
// link is inert (the sandbox blocks it) — files must go through window.claude.downloads
// instead. Outside that preview (the real deployed app) window.claude doesn't exist, so
// this always falls through to the normal browser download.
declare global {
  interface Window {
    claude?: { downloads?: { save(req: { filename: string; data: Blob }): Promise<unknown> } };
  }
}

export async function downloadBlob(filename: string, blob: Blob) {
  // Android's WebView has no DownloadListener registered, so a plain <a download> click below
  // is a silent no-op there — same class of problem FileOpener.open() already solves for
  // Documentos' "Abrir con", just with .share() (ACTION_SEND) instead of .open() (ACTION_VIEW):
  // an export is meant to be saved or sent elsewhere, not necessarily opened in place. Written
  // in chunks (not one big base64 call) since a respaldo completo with embedded photos can run
  // into the tens of MB, which crashed the app when sent across the bridge in a single message.
  if (Capacitor.isNativePlatform()) {
    try {
      await writeFileChunked(filename, blob);
      await FileOpener.share({ fileName: filename, mimeType: blob.type || 'application/octet-stream' });
      return;
    } catch {
      // Fall through to the browser-style download below as a last resort.
    }
  }

  const claudeDownloads = window.claude?.downloads;
  if (claudeDownloads) {
    try {
      await claudeDownloads.save({ filename, data: blob });
      return;
    } catch {
      // Fall through to the normal browser download below (e.g. this extension isn't
      // enabled for the preview, or the viewer declined) — same as running standalone.
    }
  }

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

function toCSV(rows: (string | number)[][]): string {
  return rows
    .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    .join('\r\n');
}

const JORNADA_SABADO_LABEL = { medio: 'Media jornada', completo: 'Jornada completa' } as const;

export function exportOvertimeCSV(monthLabel: string, report: HorasExtraPorTrabajador[], sabados: SabadosPorTrabajador[] = []) {
  // "Jornada sábado" is additive at the end rather than restructuring the existing columns —
  // sábado se paga a trato (no por hora), so those rows leave Horas extra/Motivo blank instead
  // of reusing them for something they don't mean.
  const rows: (string | number)[][] = [['Trabajador', 'Cargo', 'Fecha', 'Horas extra', 'Motivo', 'Jornada sábado']];
  for (const t of report) {
    for (const dia of t.dias) {
      rows.push([t.nombre, t.cargo, dia.fecha, dia.horas, dia.motivo ?? '', '']);
    }
  }
  for (const t of sabados) {
    for (const dia of t.dias) {
      rows.push([t.nombre, t.cargo, dia.fecha, 0, '', JORNADA_SABADO_LABEL[dia.jornada]]);
    }
  }
  const csv = '﻿' + toCSV(rows);
  downloadBlob(`horas-extra-${monthLabel}.csv`, new Blob([csv], { type: 'text/csv;charset=utf-8' }));
}

// Same accent palette as the app's own UI (src/styles/tokens.css) so the exported report
// reads as the same product, not a generic PDF bolted on the side.
const PDF_ACCENT: [number, number, number] = [37, 99, 235]; // --accent
const PDF_ACCENT_DARK: [number, number, number] = [29, 78, 216]; // --accent-dark
const PDF_ACCENT_SOFT: [number, number, number] = [232, 240, 254]; // --accent-soft
const PDF_TEXT: [number, number, number] = [20, 23, 28]; // --text
const PDF_TEXT_SOFT: [number, number, number] = [102, 112, 133]; // --text-soft
const PDF_BORDER: [number, number, number] = [225, 229, 236]; // --border
const PAGE_MARGIN = 14;

/** Letterhead + report title shared by every export page: the project header repeated via
 * autoTable's margin.top on later pages would look off, so this only runs once and everything
 * after relies on autoTable's own page-break handling instead. Returns the y to start below it. */
function drawReportHeader(doc: jsPDF, titulo: string, periodo: string): number {
  const pageWidth = doc.internal.pageSize.getWidth();

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(16);
  doc.setTextColor(...PDF_ACCENT_DARK);
  doc.text('Bitácora Vial', PAGE_MARGIN, 20);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9.5);
  doc.setTextColor(...PDF_TEXT_SOFT);
  doc.text('Ruta 5 Sur · Tramo Chillán–Bulnes', PAGE_MARGIN, 26);

  const generado = new Date().toLocaleString('es-CL', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  doc.setFontSize(8.5);
  doc.text(`Generado el ${generado}`, pageWidth - PAGE_MARGIN, 20, { align: 'right' });

  doc.setDrawColor(...PDF_BORDER);
  doc.setLineWidth(0.4);
  doc.line(PAGE_MARGIN, 31, pageWidth - PAGE_MARGIN, 31);

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(13);
  doc.setTextColor(...PDF_TEXT);
  doc.text(titulo, PAGE_MARGIN, 41);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10.5);
  doc.setTextColor(...PDF_TEXT_SOFT);
  doc.text(`Periodo: ${periodo}`, PAGE_MARGIN, 47.5);

  return 55;
}

/** Page X de Y footer on every page — has to run after the full document is built, since the
 * total page count isn't known until then. */
function drawPageNumbers(doc: jsPDF) {
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const totalPages = doc.getNumberOfPages();
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(...PDF_TEXT_SOFT);
    doc.text(`Página ${i} de ${totalPages}`, pageWidth - PAGE_MARGIN, pageHeight - 8, { align: 'right' });
    doc.text('Bitácora Vial — Reporte mensual de horas extra', PAGE_MARGIN, pageHeight - 8);
  }
}

export function exportOvertimePDF(
  monthLabel: string,
  report: HorasExtraPorTrabajador[],
  totales: { totalHoras: number; totalJornadas: number },
  sabados: SabadosPorTrabajador[] = [],
  sabadosTotales: { totalSabados: number; totalCompletos: number; totalMedios: number } = { totalSabados: 0, totalCompletos: 0, totalMedios: 0 },
) {
  const doc = new jsPDF();
  let y = drawReportHeader(doc, 'Reporte mensual de horas extra', monthLabel);

  // Summary strip — the three headline numbers a supervisor or payroll reviewer looks for
  // first, as its own compact table rather than buried in running text.
  autoTable(doc, {
    startY: y,
    margin: { left: PAGE_MARGIN, right: PAGE_MARGIN },
    theme: 'plain',
    body: [[
      { content: `${totales.totalHoras}\nHoras extra totales`, styles: { halign: 'center' } },
      { content: `${report.length}\nTrabajador(es) con horas extra`, styles: { halign: 'center' } },
      { content: `${totales.totalJornadas}\nJornada(s) registradas`, styles: { halign: 'center' } },
    ]],
    styles: { fillColor: PDF_ACCENT_SOFT, textColor: PDF_ACCENT_DARK, fontStyle: 'bold', fontSize: 13, cellPadding: { top: 8, bottom: 6, left: 4, right: 4 }, lineWidth: 0 },
    didParseCell: (data) => {
      // The label half of each cell (after the \n) reads as a caption, not part of the number.
      if (data.section === 'body') data.cell.styles.fontSize = 13;
    },
  });
  y = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 10;

  if (report.length > 0) {
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(...PDF_TEXT);
    doc.text('Resumen por trabajador', PAGE_MARGIN, y);
    y += 4;

    autoTable(doc, {
      startY: y,
      margin: { left: PAGE_MARGIN, right: PAGE_MARGIN },
      head: [['Trabajador', 'Cargo', 'Días', 'Total horas']],
      body: report.map((t) => [t.nombre, t.cargo, String(t.dias.length), `${t.totalHoras} h`]),
      headStyles: { fillColor: PDF_ACCENT, textColor: 255, fontStyle: 'bold' },
      alternateRowStyles: { fillColor: [248, 249, 251] },
      columnStyles: { 2: { halign: 'center' }, 3: { halign: 'right', fontStyle: 'bold' } },
      styles: { fontSize: 9.5, textColor: PDF_TEXT, lineColor: PDF_BORDER, lineWidth: 0.2 },
    });
    y = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 10;

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(...PDF_TEXT);
    doc.text('Detalle diario', PAGE_MARGIN, y);
    y += 4;

    // Grouped by trabajador via a full-width shaded header row ahead of that person's days,
    // instead of repeating their name on every row.
    const detailBody: (string | { content: string; colSpan: number; styles: Record<string, unknown> })[][] = [];
    for (const t of report) {
      detailBody.push([{
        content: `${t.nombre} — ${t.cargo}    ·    ${t.dias.length} día(s), ${t.totalHoras} h en total`,
        colSpan: 3,
        styles: { fillColor: PDF_ACCENT_SOFT, textColor: PDF_ACCENT_DARK, fontStyle: 'bold', fontSize: 9.5 },
      }]);
      for (const dia of t.dias) {
        detailBody.push([formatShortDate(dia.fecha), `${dia.horas} h`, dia.motivo || '—']);
      }
    }

    autoTable(doc, {
      startY: y,
      margin: { left: PAGE_MARGIN, right: PAGE_MARGIN },
      head: [['Fecha', 'Horas', 'Motivo']],
      body: detailBody,
      headStyles: { fillColor: PDF_ACCENT, textColor: 255, fontStyle: 'bold' },
      columnStyles: { 0: { cellWidth: 28 }, 1: { cellWidth: 22, halign: 'right' } },
      styles: { fontSize: 9.5, textColor: PDF_TEXT, lineColor: PDF_BORDER, lineWidth: 0.2 },
    });
    y = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 10;
  } else {
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);
    doc.setTextColor(...PDF_TEXT_SOFT);
    doc.text(`Sin horas extra registradas en ${monthLabel.toLowerCase()}.`, PAGE_MARGIN, y);
    y += 12;
  }

  // Sábado se paga a trato (según acuerdo), no por hora — kept as its own section rather than
  // folded into the horas-extra numbers above, which would misrepresent a negotiated day rate
  // as if it were an hourly figure.
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.setTextColor(...PDF_TEXT);
  doc.text('Sábados trabajados (a trato)', PAGE_MARGIN, y);
  y += 4;

  if (sabados.length > 0) {
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9.5);
    doc.setTextColor(...PDF_TEXT_SOFT);
    doc.text(
      `${sabadosTotales.totalSabados} sábado(s) trabajado(s) · ${sabadosTotales.totalCompletos} jornada completa, ${sabadosTotales.totalMedios} media jornada`,
      PAGE_MARGIN,
      y,
    );
    y += 5;

    const sabadosBody: (string | { content: string; colSpan: number; styles: Record<string, unknown> })[][] = [];
    for (const t of sabados) {
      sabadosBody.push([{
        content: `${t.nombre} — ${t.cargo}    ·    ${t.dias.length} sábado(s)`,
        colSpan: 2,
        styles: { fillColor: PDF_ACCENT_SOFT, textColor: PDF_ACCENT_DARK, fontStyle: 'bold', fontSize: 9.5 },
      }]);
      for (const dia of t.dias) {
        sabadosBody.push([formatShortDate(dia.fecha), JORNADA_SABADO_LABEL[dia.jornada]]);
      }
    }

    autoTable(doc, {
      startY: y,
      margin: { left: PAGE_MARGIN, right: PAGE_MARGIN },
      head: [['Fecha', 'Jornada']],
      body: sabadosBody,
      headStyles: { fillColor: PDF_ACCENT, textColor: 255, fontStyle: 'bold' },
      columnStyles: { 0: { cellWidth: 28 } },
      styles: { fontSize: 9.5, textColor: PDF_TEXT, lineColor: PDF_BORDER, lineWidth: 0.2 },
    });
  } else {
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);
    doc.setTextColor(...PDF_TEXT_SOFT);
    doc.text(`Sin sábados trabajados en ${monthLabel.toLowerCase()}.`, PAGE_MARGIN, y);
  }

  drawPageNumbers(doc);

  // doc.save() would trigger jsPDF's own direct download, bypassing downloadBlob's
  // capability-aware path — get the bytes instead and route them through downloadBlob.
  downloadBlob(`horas-extra-${monthLabel}.pdf`, doc.output('blob'));
}

export async function exportCubicacionCSV() {
  const [partidas, frentes, entries] = await Promise.all([
    db.partidas.toArray(),
    db.frentes.toArray(),
    db.cubicacionEntries.toArray(),
  ]);
  const frenteName = new Map(frentes.map((f) => [f.id, f.nombre]));
  const totals = new Map<string, number>();
  for (const e of entries) totals.set(e.partidaId, (totals.get(e.partidaId) ?? 0) + e.cantidadEjecutada);

  const rows: (string | number)[][] = [['Frente', 'Partida', 'Unidad', 'Cantidad contratada', 'Acumulado ejecutado', '% avance']];
  for (const p of partidas) {
    const acumulado = totals.get(p.id) ?? 0;
    const pct = p.cantidadContratada > 0 ? Math.round((Math.min(acumulado, p.cantidadContratada) / p.cantidadContratada) * 100) : 0;
    rows.push([frenteName.get(p.frenteId) ?? '', p.nombre, p.unidad, p.cantidadContratada, acumulado, `${pct}%`]);
  }
  const csv = '﻿' + toCSV(rows);
  downloadBlob('cubicacion.csv', new Blob([csv], { type: 'text/csv;charset=utf-8' }));
}

/** Full local backup: every table serialized to JSON (photos/documents as base64). */
export async function exportFullBackup() {
  const [partes, frentes, trabajadores, partidas, cubicacionEntries, asistencias, fotos, documentos, settings] = await Promise.all([
    db.partes.toArray(),
    db.frentes.toArray(),
    db.trabajadores.toArray(),
    db.partidas.toArray(),
    db.cubicacionEntries.toArray(),
    db.asistencias.toArray(),
    db.fotos.toArray(),
    db.documentos.toArray(),
    db.settings.toArray(),
  ]);

  // Full data: URL (with its "data:mime;base64," prefix), not the bare-base64 archivos.ts
  // blobToBase64 imported above — this one needs to round-trip as a self-contained string a
  // future import could feed straight back into an <img>/fetch without knowing the mime type.
  const blobToDataURL = (blob: Blob) =>
    new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });

  const fotosSerializadas = await Promise.all(
    fotos.map(async (f) => ({ ...f, blob: await blobToDataURL(f.blob) })),
  );
  const documentosSerializados = await Promise.all(
    documentos.map(async (d) => ({ ...d, blob: await blobToDataURL(d.blob) })),
  );

  const payload = {
    exportedAt: new Date().toISOString(),
    version: 1,
    partes,
    frentes,
    trabajadores,
    partidas,
    cubicacionEntries,
    asistencias,
    fotos: fotosSerializadas,
    documentos: documentosSerializados,
    settings,
  };

  const json = JSON.stringify(payload);
  downloadBlob(`bitacora-vial-respaldo-${todayISO()}.json`, new Blob([json], { type: 'application/json' }));
}
