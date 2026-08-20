/**
 * Parses a quantity typed by the user, accepting both "." and "," as the decimal
 * separator. Chilean/Spanish keyboards and habits commonly use "," (e.g. "12,5"), but an
 * `<input type="number">` only ever accepts "." — it silently drops the comma as the user
 * types, so "12,5" becomes "125" with no error and no warning. Every free-typed quantity
 * field in Cubicación and Nuevo Parte uses `<input type="text" inputMode="decimal">` paired
 * with this parser instead of type="number", precisely to avoid that trap.
 */
export function parseNumeroDecimal(valor: string): number {
  const normalizado = valor.trim().replace(',', '.');
  const n = parseFloat(normalizado);
  return Number.isFinite(n) ? n : 0;
}
