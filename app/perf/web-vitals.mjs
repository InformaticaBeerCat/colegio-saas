// Presupuesto de Core Web Vitals (UX-02). Instala el sitio con el asistente, abre las páginas públicas como un
// teléfono de gama media con 4G lento y falla si alguna supera el presupuesto.
//
//   APP_SETUP_TOKEN=ci java -jar target/colegio-saas-*.jar &
//   node perf/web-vitals.mjs http://localhost:8080
//
// Usa Playwright (npm install playwright; npx playwright install chromium) o el Chromium de PLAYWRIGHT_CHROMIUM.
import { chromium } from 'playwright';

const BASE = process.argv[2] ?? 'http://localhost:8080';
const TOKEN = process.env.APP_SETUP_TOKEN ?? 'ci';
const BUDGET = { lcp: 2500, cls: 0.1, tbt: 200, kb: 500 };
const PAGES = ['/', '/noticias', '/calendario', '/documentos', '/contacto', '/admision', '/agenda', '/preguntas-frecuentes'];
const PASSWORD = 'la cordillera nevada en julio';

const browser = await chromium.launch(process.env.PLAYWRIGHT_CHROMIUM ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM } : {});

// 1. Instalación y portada con el asistente, como lo haría un colegio.
const admin = await (await browser.newContext()).newPage();
await admin.goto(BASE + '/setup');
if (await admin.$('#token')) {
  await admin.fill('#token', TOKEN); await admin.fill('#schoolName', 'Colegio de Prueba'); await admin.fill('#rbd', '12345-6');
  await admin.selectOption('#dependency', 'PRIVATE_SUBSIDIZED'); await admin.selectOption('#plan', 'COMMUNITY');
  await admin.fill('#adminName', 'Dirección'); await admin.fill('#adminEmail', 'direccion@colegio.test');
  await admin.fill('#password', PASSWORD); await admin.fill('#passwordConfirmation', PASSWORD);
  await admin.click('button[type=submit]');
  await admin.fill('input[name=email]', 'direccion@colegio.test'); await admin.fill('input[name=password]', PASSWORD);
  await admin.click('button[type=submit]');
  await admin.goto(BASE + '/admin/welcome');
  await admin.check('input[name=choice] >> nth=0');
  await admin.click('main button[type=submit]');
}

// 2. Medición: viewport de teléfono, CPU 4x más lenta, 1,6 Mbps con 150 ms de latencia.
const failures = [];
for (const path of PAGES) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true });
  const page = await context.newPage();
  const cdp = await context.newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 150, downloadThroughput: 200 * 1024, uploadThroughput: 90 * 1024 });
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  await page.addInitScript(() => {
    window.__vitals = { lcp: 0, cls: 0, tbt: 0 };
    new PerformanceObserver(l => l.getEntries().forEach(e => { window.__vitals.lcp = e.startTime; })).observe({ type: 'largest-contentful-paint', buffered: true });
    new PerformanceObserver(l => l.getEntries().forEach(e => { if (!e.hadRecentInput) window.__vitals.cls += e.value; })).observe({ type: 'layout-shift', buffered: true });
    new PerformanceObserver(l => l.getEntries().forEach(e => { window.__vitals.tbt += Math.max(0, e.duration - 50); })).observe({ type: 'longtask', buffered: true });
  });
  let bytes = 0;
  let encoding = null;
  page.on('response', async r => {
    const length = Number((await r.allHeaders())['content-length'] ?? 0);
    bytes += length || (await r.body().catch(() => Buffer.alloc(0))).length;
  });
  const response = await page.goto(BASE + path, { waitUntil: 'networkidle' });
  encoding = (await response.allHeaders())['content-encoding'] ?? null;
  await page.mouse.click(5, 5); // cierra la ventana de LCP como lo haría una interacción real
  await page.waitForTimeout(500);
  const v = await page.evaluate(() => window.__vitals);
  const kb = Math.round(bytes / 1024);
  const row = { path, lcp: Math.round(v.lcp), cls: Number(v.cls.toFixed(3)), tbt: Math.round(v.tbt), kb, gzip: encoding === 'gzip' };
  console.log(JSON.stringify(row));
  if (row.lcp > BUDGET.lcp) failures.push(`${path}: LCP ${row.lcp} ms > ${BUDGET.lcp}`);
  if (row.cls > BUDGET.cls) failures.push(`${path}: CLS ${row.cls} > ${BUDGET.cls}`);
  if (row.tbt > BUDGET.tbt) failures.push(`${path}: TBT ${row.tbt} ms > ${BUDGET.tbt}`);
  if (row.kb > BUDGET.kb) failures.push(`${path}: ${row.kb} KB > ${BUDGET.kb} KB`);
  if (!row.gzip) failures.push(`${path}: el HTML no viene comprimido`);
  await context.close();
}
await browser.close();

if (failures.length) {
  console.error('Presupuesto de rendimiento superado:\n- ' + failures.join('\n- '));
  process.exit(1);
}
console.log('Presupuesto de rendimiento cumplido en ' + PAGES.length + ' páginas.');
