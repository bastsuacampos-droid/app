import { chromium } from 'playwright';

const errors = [];
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
const context = await browser.newContext({ viewport: { width: 393, height: 852 }, permissions: ['camera', 'notifications'] });
const page = await context.newPage();
page.on('console', (msg) => { if (msg.type() === 'error') errors.push(`console.error: ${msg.text()}`); });
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));

async function shot(name) { await page.screenshot({ path: `/tmp/formula-${name}.png` }); }

try {
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  const continuar = page.getByRole('button', { name: 'Continuar' });
  if (await continuar.count()) { await continuar.click(); await page.waitForTimeout(800); }

  await page.goto('http://localhost:5173/#/cubicacion', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);

  // Excavación en corte is m³: test largo x ancho x alto x cantidad
  await page.getByRole('button', { name: 'Calcular por dimensiones' }).first().click();
  await page.waitForTimeout(200);
  await shot('01-opened');

  const largo = page.getByLabel('Largo (m)').first();
  const ancho = page.getByLabel('Ancho (m)').first();
  const alto = page.getByLabel('Alto/Espesor (m)').first();
  const cantidad = page.getByLabel('Cantidad (veces se repite)').first();

  await largo.fill('2');
  await ancho.fill('1.5');
  await alto.fill('0.5');
  await cantidad.fill('4');
  await page.waitForTimeout(200);
  await shot('02-filled'); // expect subtotal = 2*1.5*0.5*4 = 6 m3

  const ejecutadoAntes = await page.locator('input[type="number"]').first().inputValue();

  await page.getByRole('button', { name: 'Agregar al total de hoy' }).click();
  await page.waitForTimeout(400);
  await shot('03-added');

  const ejecutadoDespues = await page.locator('input[type="number"]').first().inputValue();

  console.log('EJECUTADO_ANTES:', ejecutadoAntes || '(vacío)');
  console.log('EJECUTADO_DESPUES:', ejecutadoDespues);

  // Add a second batch (panel is still open from before) and confirm it accumulates
  await shot('03b-before-second-fill');
  await page.getByLabel('Largo (m)').first().fill('1');
  await page.getByLabel('Ancho (m)').first().fill('1');
  await page.getByLabel('Alto/Espesor (m)').first().fill('1');
  await page.getByLabel('Cantidad (veces se repite)').first().fill('2');
  await page.getByRole('button', { name: 'Agregar al total de hoy' }).click();
  await page.waitForTimeout(400);
  const ejecutadoFinal = await page.locator('input[type="number"]').first().inputValue();
  console.log('EJECUTADO_FINAL (esperado 8):', ejecutadoFinal);
  await shot('04-second-batch');
} catch (err) {
  errors.push(`script: ${err.message}`);
} finally {
  await browser.close();
}

console.log('ERRORS:', errors.length);
for (const e of errors) console.log(' -', e);
process.exit(errors.length > 0 ? 1 : 0);
