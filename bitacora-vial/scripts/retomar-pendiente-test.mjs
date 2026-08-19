import { chromium } from 'playwright';

const errors = [];
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
const context = await browser.newContext({ viewport: { width: 393, height: 852 }, permissions: ['camera', 'notifications'] });
const page = await context.newPage();
page.on('console', (msg) => { if (msg.type() === 'error') errors.push(`console.error: ${msg.text()}`); });
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));

async function shot(name) { await page.screenshot({ path: `/tmp/rp-${name}.png` }); }

try {
  // Day 1: 2026-08-10 10:00 local
  await page.clock.install({ time: new Date('2026-08-10T10:00:00') });
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  const continuar = page.getByRole('button', { name: 'Continuar' });
  if (await continuar.count()) { await continuar.click(); await page.waitForTimeout(800); }

  await page.goto('http://localhost:5173/#/cubicacion', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);

  await page.getByRole('button', { name: 'Agregar tarea' }).click();
  await page.waitForTimeout(200);
  await page.getByPlaceholder('Nombre de la partida').fill('Prueba multi-día');
  await page.getByPlaceholder(/Cantidad contratada/).fill('100');
  await page.getByPlaceholder('0').fill('30');
  await page.getByRole('button', { name: 'Guardar tarea' }).click();
  await page.waitForTimeout(400);
  await shot('01-dia1-30-de-100');

  // Nuevo Parte should nest this task with its % right there.
  await page.goto('http://localhost:5173/#/nuevo-parte', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('02-dia1-nuevo-parte-anidado');

  // Day 2: 2026-08-11 08:00 local — a brand-new "today"
  await page.clock.setFixedTime(new Date('2026-08-11T08:00:00'));
  await page.goto('http://localhost:5173/#/cubicacion', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  await shot('03-dia2-deberia-verse-pendiente');

  const pendienteBadge = page.getByText('Pendiente de días anteriores');
  const pendienteVisible = await pendienteBadge.count();
  console.log('BADGE_PENDIENTE_VISIBLE (esperado >=1):', pendienteVisible);

  // Add more progress today: +20 more (should accumulate to 50/100 = 50%)
  const ejecutadoHoy = page.locator('input[type="number"]').first();
  await ejecutadoHoy.fill('20');
  await page.waitForTimeout(400);
  await shot('04-dia2-avance-agregado');

  const acumTexto = await page.getByText(/Acum:/).first().textContent();
  console.log('ACUM_TEXTO (esperado 50 m³ · 50%):', acumTexto);
} catch (err) {
  errors.push(`script: ${err.message}`);
} finally {
  await browser.close();
}

console.log('ERRORS:', errors.length);
for (const e of errors) console.log(' -', e);
process.exit(errors.length > 0 ? 1 : 0);
