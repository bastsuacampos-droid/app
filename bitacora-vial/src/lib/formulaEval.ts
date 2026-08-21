/** Small hand-rolled arithmetic expression evaluator for the "Personalizado" cubicación
 * shape — deliberately NOT eval()/Function()-based, since the formula text comes straight
 * from user input. Supports + - * / ^, parentheses, unary +/-, decimal numbers (comma or
 * dot), and bare identifiers that resolve against the `valores` map (the calculadora's
 * a/b/c… field tokens). */

export class FormulaError extends Error {}

type Token = { type: 'num'; value: number } | { type: 'ident'; value: string } | { type: 'op'; value: string };

function tokenize(input: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  while (i < input.length) {
    const c = input[i];
    if (/\s/.test(c)) { i++; continue; }
    if (/[0-9]/.test(c) || (c === ',' && /[0-9]/.test(input[i + 1] ?? ''))) {
      let j = i;
      while (j < input.length && /[0-9.,]/.test(input[j])) j++;
      const raw = input.slice(i, j).replace(',', '.');
      const value = Number(raw);
      if (!Number.isFinite(value)) throw new FormulaError(`Número inválido: "${raw}"`);
      tokens.push({ type: 'num', value });
      i = j;
      continue;
    }
    if (/[a-zA-Z_]/.test(c)) {
      let j = i;
      while (j < input.length && /[a-zA-Z0-9_]/.test(input[j])) j++;
      tokens.push({ type: 'ident', value: input.slice(i, j) });
      i = j;
      continue;
    }
    if ('+-*/^()'.includes(c)) {
      tokens.push({ type: 'op', value: c });
      i++;
      continue;
    }
    throw new FormulaError(`Carácter no reconocido: "${c}"`);
  }
  return tokens;
}

/** Evaluates `formula` against `valores` (identifier → number). Throws FormulaError with a
 * message suitable to show directly to the user on any syntax problem, unknown variable, or
 * division by zero — callers should catch it and fall back to a 0 subtotal. */
export function evaluarFormula(formula: string, valores: Record<string, number>): number {
  const tokens = tokenize(formula);
  if (tokens.length === 0) throw new FormulaError('Fórmula vacía');
  let pos = 0;
  const peek = () => tokens[pos];
  const next = () => tokens[pos++];

  function parseExpr(): number {
    let v = parseTerm();
    while (peek()?.type === 'op' && (peek().value === '+' || peek().value === '-')) {
      const op = next() as Extract<Token, { type: 'op' }>;
      const rhs = parseTerm();
      v = op.value === '+' ? v + rhs : v - rhs;
    }
    return v;
  }
  function parseTerm(): number {
    let v = parseUnary();
    while (peek()?.type === 'op' && (peek().value === '*' || peek().value === '/')) {
      const op = next() as Extract<Token, { type: 'op' }>;
      const rhs = parseUnary();
      if (op.value === '/') {
        if (rhs === 0) throw new FormulaError('División por cero');
        v = v / rhs;
      } else {
        v = v * rhs;
      }
    }
    return v;
  }
  function parseUnary(): number {
    if (peek()?.type === 'op' && (peek().value === '-' || peek().value === '+')) {
      const op = next() as Extract<Token, { type: 'op' }>;
      return op.value === '-' ? -parseUnary() : parseUnary();
    }
    return parsePower();
  }
  function parsePower(): number {
    const base = parsePrimary();
    if (peek()?.type === 'op' && peek().value === '^') {
      next();
      return base ** parseUnary();
    }
    return base;
  }
  function parsePrimary(): number {
    const t = peek();
    if (!t) throw new FormulaError('Fórmula incompleta');
    if (t.type === 'num') { next(); return t.value; }
    if (t.type === 'ident') {
      next();
      const v = valores[t.value];
      if (v === undefined) throw new FormulaError(`Variable desconocida: "${t.value}"`);
      return v;
    }
    if (t.type === 'op' && t.value === '(') {
      next();
      const v = parseExpr();
      const close = peek();
      if (!close || close.type !== 'op' || close.value !== ')') throw new FormulaError('Falta un paréntesis de cierre');
      next();
      return v;
    }
    throw new FormulaError(`Símbolo inesperado: "${t.value}"`);
  }

  const result = parseExpr();
  if (pos < tokens.length) throw new FormulaError(`Símbolo inesperado: "${tokens[pos].value}"`);
  if (!Number.isFinite(result)) throw new FormulaError('El resultado no es un número válido');
  return result;
}
