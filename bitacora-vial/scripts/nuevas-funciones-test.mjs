import { chromium } from 'playwright';

const errors = [];
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
const context = await browser.newContext({ viewport: { width: 393, height: 852 }, permissions: ['camera', 'notifications'] });
const page = await context.newPage();
page.on('console', (msg) => { if (msg.type() === 'error') errors.push(`console.error: ${msg.text()}`); });
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));

async function shot(name) { await page.screenshot({ path: `/tmp/nf-${name}.png` }); }

try {
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  const continuar = page.getByRole('button', { name: 'Continuar' });
  if (await continuar.count()) { await continuar.click(); await page.waitForTimeout(800); }

  // --- Préstamo de personal ---
  await page.goto('http://localhost:5173/#/asistencia', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('01-asistencia-frente1');

  // Traer prestado desde Frente 1 (Ana Rojas vive en Frente 2)
  await page.getByRole('button', { name: 'Traer prestado' }).click();
  await page.waitForTimeout(300);
  await shot('02-prestamo-picker');
  await page.getByText('Ana Rojas').click();
  await page.waitForTimeout(400);
  await shot('03-ana-en-frente1');

  // Prestar a Juan Muñoz hacia Frente 2
  await page.getByRole('button', { name: 'Prestar a otro frente hoy' }).first().click();
  await page.waitForTimeout(200);
  await page.locator('select').filter({ hasText: 'a qué frente' }).selectOption({ label: 'Frente 2' });
  await page.waitForTimeout(400);
  await shot('04-juan-prestado-a-frente2');

  // Cambiar a tab Frente 2: Juan debería aparecer ahí como prestado, Ana ya no en Frente 2
  await page.getByRole('button', { name: 'Frente 2', exact: true }).click();
  await page.waitForTimeout(400);
  await shot('05-frente2-con-juan-prestado');

  // --- Cubicación: estado de tarea + catálogo ---
  await page.goto('http://localhost:5173/#/cubicacion', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('06-cubicacion-estados');

  await page.getByRole('button', { name: 'Agregar tarea' }).click();
  await page.waitForTimeout(300);
  await shot('07-catalogo-abierto');
  const primerItem = page.locator('button.chip').first();
  await primerItem.click();
  await page.waitForTimeout(300);
  await shot('08-item-catalogo-elegido');

  // --- Fotos por etapa ---
  const { writeFileSync } = await import('node:fs');
  const pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFUlEQVR42mNk+P+/noEIwDiqEBcAABILAEbdVYIAAAAAAElFTkSuQmCC';
  const tmpImgPath = '/tmp/nf-test-photo.png';
  writeFileSync(tmpImgPath, Buffer.from(pngBase64, 'base64'));

  await page.goto('http://localhost:5173/#/fotos', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await page.locator('button.fab').click();
  await page.waitForTimeout(300);
  await shot('09-panel-captura');
  await page.getByRole('button', { name: 'Inconveniente' }).click();
  await page.setInputFiles('input[type="file"]', tmpImgPath);
  await page.waitForTimeout(500);
  await shot('10-foto-inconveniente-guardada');
} catch (err) {
  errors.push(`script: ${err.message}`);
} finally {
  await browser.close();
}

console.log('ERRORS:', errors.length);
for (const e of errors) console.log(' -', e);
process.exit(errors.length > 0 ? 1 : 0);
