import { chromium } from 'playwright';

const errors = [];
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
const context = await browser.newContext({
  viewport: { width: 393, height: 852 },
  permissions: ['camera', 'notifications'],
});
const page = await context.newPage();

page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(`console.error: ${msg.text()}`);
});
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}\n${err.stack ?? ''}`));

async function shot(name) {
  await page.screenshot({ path: `/tmp/shot-${name}.png` });
}

try {
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  await shot('01-onboarding');

  const continuar = page.getByRole('button', { name: 'Continuar' });
  if (await continuar.count()) {
    await continuar.click();
    await page.waitForTimeout(1200);
  }
  await shot('02-dashboard');

  const nuevoParte = page.getByRole('button', { name: /Nuevo Parte Diario/ });
  await nuevoParte.click({ timeout: 8000 });
  await page.waitForTimeout(400);
  await shot('03-nuevo-parte');

  await page.getByRole('button', { name: 'Agregar tarea' }).click();
  await page.waitForTimeout(400);
  await shot('04-cubicacion');
  await page.goBack();
  await page.waitForTimeout(300);

  await page.getByRole('button', { name: 'Registrar Asistencia' }).click();
  await page.waitForTimeout(400);
  await shot('05-asistencia');
  await page.goBack();
  await page.waitForTimeout(300);

  await page.getByRole('button', { name: /Ver todas/ }).click();
  await page.waitForTimeout(400);
  await shot('06-fotos');

  await page.goto('http://localhost:5173/#/historial', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('07-historial');

  await page.goto('http://localhost:5173/#/mas', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('08-mas');

  await page.goto('http://localhost:5173/#/horas-extra', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('09-horas-extra');

  await page.goto('http://localhost:5173/#/documentos', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('10-documentos');

  await page.goto('http://localhost:5173/#/configuracion', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('11-configuracion');
} catch (err) {
  errors.push(`script: ${err.message}`);
} finally {
  await browser.close();
}

console.log('ERRORS:', errors.length);
for (const e of errors) console.log(' -', e);
process.exit(errors.length > 0 ? 1 : 0);
