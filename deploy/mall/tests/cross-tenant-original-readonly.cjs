// Anonymous GET-only acceptance of existing public catalogue after restoration.
const assert = require('node:assert/strict');
const { chromium } = require('../../../txgy-ui-admin-vben/node_modules/playwright');

(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const checks = [];
  try {
    const context = await browser.newContext();
    context.setDefaultTimeout(60000);
    await context.route('**/*', route => {
      const request = route.request();
      if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) return route.abort();
      return route.continue();
    });
    const pc = await context.newPage();
    await pc.goto('http://127.0.0.1:15175/companies', { waitUntil: 'networkidle' });
    const company = pc.locator('.company-card').filter({ hasText: '科盾防护' });
    await company.waitFor();
    const counts = await company.locator('.company-facts').innerText();
    assert.match(counts, /6 家店铺/);
    assert.match(counts, /36 件公开商品/);
    checks.push('Original PC Kedun company 6 shops / 36 public products');
    await pc.goto('http://127.0.0.1:15175/products/202410035', { waitUntil: 'networkidle' });
    await pc.getByRole('heading', { name: '陕北工矿 高温纤维粘结剂 20kg', exact: true }).waitFor();
    await pc.waitForFunction(() => [...document.images].some(i => i.src.includes('/infra/file/') && i.complete && i.naturalWidth > 0));
    checks.push('Original PC seller product route and legacy gateway image bytes render');
    const mobile = await context.newPage({ viewport: { width: 390, height: 844 } });
    const incorrect = [];
    mobile.on('request', r => { if (/127\.0\.0\.1:48080\/.*infra\/file/.test(r.url())) incorrect.push(r.url()); });
    await mobile.goto('http://127.0.0.1:3000/pages/goods/index?id=202410035&portal=1', { waitUntil: 'networkidle' });
    await mobile.getByText('陕北工矿 高温纤维粘结剂 20kg', { exact: true }).first().waitFor();
    await mobile.waitForFunction(() => [...document.images].some(i => i.src.includes('/infra/file/') && i.complete && i.naturalWidth > 0));
    assert.equal(incorrect.length, 0);
    checks.push('Original H5 product image renders through same origin, no phone-local port 48080 request');
    console.log(JSON.stringify({ status: 'passed', checks, writes: 0, notVerified: ['Android hardware'] }, null, 2));
  } catch (error) {
    console.error(JSON.stringify({ status: 'failed', checks, error: error.message }, null, 2));
    process.exitCode = 1;
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
