import { chromium } from 'playwright';
import { writeFileSync } from 'node:fs';

const errors = [];
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
const context = await browser.newContext({ viewport: { width: 393, height: 852 }, permissions: ['camera', 'notifications'] });
const page = await context.newPage();
page.on('console', (msg) => { if (msg.type() === 'error') errors.push(`console.error: ${msg.text()}`); });
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));

async function shot(name) { await page.screenshot({ path: `/tmp/it-${name}.png` }); }

// a tiny 2x2 red PNG, base64
const pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFUlEQVR42mNk+P+/noEIwDiqEBcAABILAEbdVYIAAAAAAElFTkSuQmCC';
const tmpImgPath = '/tmp/test-photo.png';
writeFileSync(tmpImgPath, Buffer.from(pngBase64, 'base64'));

try {
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  const continuar = page.getByRole('button', { name: 'Continuar' });
  if (await continuar.count()) { await continuar.click(); await page.waitForTimeout(800); }

  await page.getByRole('button', { name: /Nuevo Parte Diario/ }).click();
  await page.waitForTimeout(300);

  // Nueva tarea: needs a frente selected first (the button is disabled otherwise), then opens
  // the "Nueva tarea" modal directly on top of Nuevo Parte — no navigation to Cubicación.
  await page.getByText(/Seleccionar punto de trabajo/).click();
  await page.waitForTimeout(200);
  await page.locator('button').filter({ hasText: 'Frente 1' }).first().click();
  await page.waitForTimeout(300);

  await page.getByRole('button', { name: /Cubicar nueva tarea/ }).click();
  await page.waitForTimeout(300);
  await page.getByPlaceholder('Nombre de la partida').fill('Prueba interaction');
  await page.getByPlaceholder(/Cantidad contratada/).fill('100');
  await page.getByPlaceholder('0').fill('85');
  await page.getByRole('button', { name: 'Guardar tarea' }).click();
  await page.waitForTimeout(400);
  await shot('cubicacion-filled');

  // Asistencia: mark one absent, add overtime + motivo on another
  await page.getByRole('button', { name: 'Registrar Asistencia' }).click();
  await page.waitForTimeout(300);
  const toggles = page.locator('button[role="switch"]');
  await toggles.nth(3).click(); // mark 4th worker absent
  await page.waitForTimeout(200);

  const extraInputs = page.locator('input[type="number"]');
  // each present worker card has 2 number inputs (horas, extra); take the 2nd input of the first card
  await extraInputs.nth(1).fill('1.5');
  await extraInputs.nth(1).blur();
  await page.waitForTimeout(300);
  const motivoInput = page.getByPlaceholder('Motivo de la hora extra');
  if (await motivoInput.count()) {
    await motivoInput.fill('Recuperar atraso por lluvia');
    await motivoInput.blur();
  }
  await page.waitForTimeout(300);
  await shot('asistencia-filled');
  await page.goBack();
  await page.waitForTimeout(300);

  // Dashboard should now reflect updated presentes + avance
  await shot('dashboard-after-edits');

  // Fotos: upload a photo, open editor, draw, save
  await page.getByRole('link', { name: /Fotos/ }).first().click().catch(() => {});
  await page.goto('http://localhost:5173/#/fotos', { waitUntil: 'networkidle' });
  await page.waitForTimeout(300);
  const fileInput = page.locator('input[type="file"]');
  await fileInput.setInputFiles(tmpImgPath);
  await page.waitForTimeout(500);
  await shot('fotos-uploaded');

  await page.locator('button').filter({ hasText: '' }).first(); // no-op, just ensure page ready
  const thumb = page.locator('button[style*="background-image"]').first();
  await thumb.click();
  await page.waitForTimeout(500);
  await shot('editor-opened');

  const canvas = page.locator('canvas');
  const box = await canvas.boundingBox();
  if (box) {
    await page.mouse.move(box.x + 20, box.y + 20);
    await page.mouse.down();
    await page.mouse.move(box.x + 80, box.y + 80, { steps: 5 });
    await page.mouse.up();
  }
  await page.waitForTimeout(300);
  await shot('editor-drawn');

  await page.getByRole('button', { name: 'Guardar' }).click();
  await page.waitForTimeout(500);
  await shot('fotos-after-save');

  // Horas extra report should now show the entry
  await page.goto('http://localhost:5173/#/horas-extra', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await shot('horas-extra-after-edit');
} catch (err) {
  errors.push(`script: ${err.message}`);
} finally {
  await browser.close();
}

console.log('ERRORS:', errors.length);
for (const e of errors) console.log(' -', e);
process.exit(errors.length > 0 ? 1 : 0);
