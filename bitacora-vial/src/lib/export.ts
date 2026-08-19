import jsPDF from 'jspdf';
import { db } from './db';
import type { HorasExtraPorTrabajador } from './queries';
import { formatShortDate } from './date';

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

export function exportOvertimeCSV(monthLabel: string, report: HorasExtraPorTrabajador[]) {
  const rows: (string | number)[][] = [['Trabajador', 'Cargo', 'Fecha', 'Horas extra', 'Motivo']];
  for (const t of report) {
    for (const dia of t.dias) {
      rows.push([t.nombre, t.cargo, dia.fecha, dia.horas, dia.motivo ?? '']);
    }
  }
  const csv = '﻿' + toCSV(rows);
  downloadBlob(`horas-extra-${monthLabel}.csv`, new Blob([csv], { type: 'text/csv;charset=utf-8' }));
}

export function exportOvertimePDF(monthLabel: string, report: HorasExtraPorTrabajador[], totales: { totalHoras: number; totalJornadas: number }) {
  const doc = new jsPDF();
  doc.setFontSize(16);
  doc.text('Bitácora Vial — Reporte mensual de horas extra', 14, 18);
  doc.setFontSize(11);
  doc.text(`Periodo: ${monthLabel}`, 14, 27);
  doc.text(`Total horas extra: ${totales.totalHoras} h   ·   Jornadas: ${totales.totalJornadas}`, 14, 34);

  let y = 46;
  doc.setFontSize(12);
  for (const t of report) {
    if (y > 270) {
      doc.addPage();
      y = 20;
    }
    doc.setFont('helvetica', 'bold');
    doc.text(`${t.nombre} — ${t.cargo}`, 14, y);
    doc.setFont('helvetica', 'normal');
    doc.text(`${t.totalHoras} h · ${t.dias.length} día(s)`, 150, y);
    y += 6;
    for (const dia of t.dias) {
      if (y > 275) {
        doc.addPage();
        y = 20;
      }
      doc.setFontSize(10);
      doc.text(`  ${formatShortDate(dia.fecha)} · ${dia.horas} h${dia.motivo ? `  —  ${dia.motivo}` : ''}`, 18, y);
      y += 5.5;
      doc.setFontSize(12);
    }
    y += 4;
  }

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

  const blobToBase64 = (blob: Blob) =>
    new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });

  const fotosSerializadas = await Promise.all(
    fotos.map(async (f) => ({ ...f, blob: await blobToBase64(f.blob) })),
  );
  const documentosSerializados = await Promise.all(
    documentos.map(async (d) => ({ ...d, blob: await blobToBase64(d.blob) })),
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
  downloadBlob(`bitacora-vial-respaldo-${new Date().toISOString().slice(0, 10)}.json`, new Blob([json], { type: 'application/json' }));
}
