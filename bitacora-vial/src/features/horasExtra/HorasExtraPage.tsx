import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { monthlyOvertimeReport, monthlyOvertimeTotals } from '../../lib/queries';
import { currentMonthISO, formatMonthLabel, formatShortDate, shiftMonthISO } from '../../lib/date';
import { exportOvertimeCSV, exportOvertimePDF } from '../../lib/export';
import { Header } from '../../components/Header';
import { IconDoc, IconClockRain, IconChevronLeft, IconChevronRight } from '../../components/Icon';

export function HorasExtraPage() {
  const [month, setMonth] = useState(currentMonthISO());

  const report = useLiveQuery(() => monthlyOvertimeReport(month), [month]) ?? [];
  const totales = useLiveQuery(() => monthlyOvertimeTotals(month), [month]) ?? { totalHoras: 0, totalJornadas: 0 };
  const monthLabel = formatMonthLabel(`${month}-01`);

  return (
    <>
      <Header title="Horas Extra" back>
        {/* A native <input type="month"> renders its label in the browser/OS's own language,
         * ignoring the page's lang="es" — on an English-locale device it shows "August 2026"
         * in an otherwise all-Spanish app. Prev/next arrows around a date-fns-formatted
         * Spanish label sidesteps that entirely. */}
        <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', background: 'var(--surface-alt)', borderRadius: 11, padding: '10px 13px' }}>
          <button type="button" onClick={() => setMonth((m) => shiftMonthISO(m, -1))} aria-label="Mes anterior" style={{ background: 'none', border: 'none', padding: 4, display: 'flex' }}>
            <IconChevronLeft size={16} color="var(--text-soft)" />
          </button>
          <span style={{ color: 'var(--text)', fontSize: 12.5, fontWeight: 600 }}>{monthLabel}</span>
          <button type="button" onClick={() => setMonth((m) => shiftMonthISO(m, 1))} aria-label="Mes siguiente" style={{ background: 'none', border: 'none', padding: 4, display: 'flex' }}>
            <IconChevronRight size={16} color="var(--text-soft)" />
          </button>
        </div>
      </Header>

      <div className="content">
        <div className="card-dark" style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: '#c9c3b8', textTransform: 'uppercase', letterSpacing: '.06em', marginBottom: 6 }}>
            Total horas extra del mes
          </div>
          <div className="disp" style={{ fontSize: 30, fontWeight: 800, color: 'var(--amber)', marginBottom: 4 }}>
            {totales.totalHoras} h
          </div>
          <div style={{ fontSize: 11.5, color: '#c9c3b8' }}>
            {totales.totalJornadas} jornada(s) con horas extra registradas
          </div>
        </div>

        <div className="section-label">Detalle diario por trabajador</div>

        {report.length === 0 ? (
          <div className="text-soft" style={{ fontSize: 13, marginBottom: 16 }}>
            Sin horas extra registradas en {monthLabel.toLowerCase()}.
          </div>
        ) : (
          <div className="stack" style={{ marginBottom: 16 }}>
            {report.map((t) => (
              <div key={t.trabajadorId} className="card">
                <div className="list-row" style={{ marginBottom: 10 }}>
                  <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--accent-soft)', color: 'var(--accent-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, flexShrink: 0 }}>
                    {t.nombre.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()}
                  </div>
                  <div style={{ flexGrow: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{t.nombre}</div>
                    <div className="text-soft" style={{ fontSize: 11 }}>{t.cargo} · {t.dias.length} día(s)</div>
                  </div>
                  <span style={{ background: 'var(--accent-soft)', color: 'var(--accent-dark)', borderRadius: 8, padding: '5px 10px', fontSize: 12.5, fontWeight: 700, whiteSpace: 'nowrap' }}>
                    {t.totalHoras} h
                  </span>
                </div>
                <div className="flex-row gap-8" style={{ flexWrap: 'wrap' }}>
                  {t.dias.map((d, i) => (
                    <div key={i} title={d.motivo} style={{ background: 'var(--surface-alt)', borderRadius: 7, padding: '4px 8px', fontSize: 11 }}>
                      {formatShortDate(d.fecha)} <span style={{ color: 'var(--accent-dark)', fontWeight: 700 }}>· {d.horas}h</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        <div style={{ background: 'var(--yellow-soft)', border: '1px solid #f0e2b8', borderRadius: 12, padding: '11px 13px', display: 'flex', gap: 9, marginBottom: 16 }}>
          <IconClockRain color="#a5760a" size={16} />
          <span style={{ fontSize: 11.5, color: '#8a6408', lineHeight: 1.4 }}>
            Las horas extra y su motivo se registran día a día en Asistencia; este reporte las consolida automáticamente para el mes seleccionado.
          </span>
        </div>
      </div>

      <div style={{ flexShrink: 0, background: 'var(--surface)', borderTop: '1px solid var(--border)', padding: '12px 20px 16px', display: 'flex', gap: 10 }}>
        <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => exportOvertimeCSV(month, report)}>CSV</button>
        <button
          className="btn btn-primary"
          style={{ flex: 1.6 }}
          onClick={() => exportOvertimePDF(monthLabel, report, totales)}
        >
          <IconDoc size={17} color="#fff" /> Exportar PDF
        </button>
      </div>
    </>
  );
}
