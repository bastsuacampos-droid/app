/**
 * Parses a quantity typed by the user, in Chilean/Spanish notation: "," as the decimal
 * separator and "." as the thousands grouping separator (the same convention this app's own
 * displays use via `toLocaleString('es-CL')`, e.g. "4.200 m³"). Two traps this guards against:
 *
 * 1. `<input type="number">` only ever accepts "." as a decimal point — it silently drops any
 *    "," as the user types, so "12,5" becomes "125" with no error and no warning. Every
 *    free-typed quantity field uses `<input type="text" inputMode="decimal">` paired with this
 *    parser instead of type="number", precisely to avoid that.
 * 2. A naive "," → "." swap alone still breaks on a "." typed as a thousands separator: since
 *    the app itself displays "4.200" for four thousand two hundred, a user typing "1.000" to
 *    mean one thousand — mirroring that same convention — would otherwise get
 *    `parseFloat("1.000")` = 1, silently 1000x too small. When there's no "," to anchor the
 *    decimal point, a "." is only treated as a decimal point if what follows it isn't a clean
 *    3-digit group (a real thousands separator always groups in exactly 3s); otherwise every
 *    "." is stripped as grouping. "12.5" (2 digits after the dot) parses as 12.5; "1.000" or
 *    "4.200" (3 digits, valid grouping) parses as 1000 / 4200.
 */
export function parseNumeroDecimal(valor: string): number {
  const texto = valor.trim();
  if (!texto) return 0;

  let normalizado: string;
  if (texto.includes(',')) {
    // "," is the decimal separator — any "." before it is thousands grouping, strip it.
    normalizado = texto.replace(/\./g, '').replace(',', '.');
  } else if (texto.includes('.')) {
    const partes = texto.split('.');
    const grupos = partes.slice(0, -1);
    const ultima = partes[partes.length - 1];
    const esAgrupacionDeMiles = ultima.length === 3 && grupos.every((g, i) => (i === 0 ? g.length >= 1 && g.length <= 3 : g.length === 3));
    normalizado = esAgrupacionDeMiles ? texto.replace(/\./g, '') : texto;
  } else {
    normalizado = texto;
  }

  const n = parseFloat(normalizado);
  return Number.isFinite(n) ? n : 0;
}
