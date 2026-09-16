export function formatCLP(monto: number): string {
  const signo = monto < 0 ? '-' : ''
  return `${signo}$${Math.abs(Math.round(monto)).toLocaleString('es-CL')}`
}

export function formatFecha(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' })
}

export function formatFechaHora(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleString('es-CL', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function hoyISO(): string {
  return new Date().toISOString()
}
