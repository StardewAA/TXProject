// Run after npm run build:h5. Every API is mocked; no account or SMS is created.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, mkdtemp } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { tmpdir } = require('node:os');
const { chromium } = require('playwright');

(async () => {
  const root = resolve(__dirname, '../dist/build/h5');
  const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
  const server = createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
      let target = resolve(root, '.' + pathname);
      if (target !== root && !target.startsWith(root + sep)) { response.writeHead(404).end(); return; }
      if (!extname(target)) target = join(root, 'index.html');
      const data = await readFile(target);
      response.writeHead(200, { 'Content-Type': types[extname(target)] || 'application/octet-stream' }).end(data);
    } catch { response.writeHead(404).end(); }
  });
  await new Promise(ready => server.listen(0, '127.0.0.1', ready));
  let browser;
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) });
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, serviceWorkers: 'block' });
    const page = await context.newPage();
    const apiCalls = [], errors = []; let imageEnabled = true, registrationSucceeds = false;
    const origin = 'http://127.0.0.1:' + server.address().port;
    const imageDataUrl = await page.evaluate(() => {
      const canvas = document.createElement('canvas'); canvas.width = 160; canvas.height = 48;
      const drawing = canvas.getContext('2d'); drawing.fillStyle = '#eff6f3'; drawing.fillRect(0, 0, 160, 48);
      drawing.fillStyle = '#244d40'; drawing.font = 'bold 30px sans-serif'; drawing.fillText('AB123', 20, 34);
      return canvas.toDataURL('image/png');
    });
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url());
      if (!url.pathname.includes('/app-api/')) {
        if (url.origin === origin) return route.continue();
        return route.abort();
      }
      const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8);
      const body = request.postDataJSON(); apiCalls.push({ path, body });
      let data = null, code = 0;
      if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
      else if (path === '/member/mall-auth/config') data = { protocol: 'legacy', tenantId: 1, available: true, registrationAvailable: true, tenantSelection: false, registrationMethod: imageEnabled ? 'image-v1' : 'sms', loginNameEnabled: imageEnabled };
      else if (path === '/member/mall-auth/registration-options') data = { enabled: false, tenants: [], crossTenantReady: false };
      else if (path === '/member/mall-auth/image-challenge') data = { tenantId: 1, challengeId: 'b'.repeat(32), imageDataUrl, expiresIn: 300, retryAfter: 2 };
      else if (path === '/member/mall-auth/register-image') {
        if (registrationSucceeds) data = { tenantId: 1, userId: 7, accessToken: 'mock-access', refreshToken: 'mock-refresh' };
        else code = 1004018190;
      }
      else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: 7 };
      else if (path === '/member/user/get') data = { id: 7, tenantId: 1, loginName: 'buyer_01', memberIdentity: 'buyer', nickname: '模拟会员', mobile: null, enterpriseStatus: 0 };
      await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify({ code, data }) });
    });
    await page.goto(origin + '/pages/index/user?mallRegistration=1&tenantId=1', { waitUntil: 'networkidle' });
    await page.evaluate(() => {
      window.registrationSmokeToasts = [];
      const original = uni.showToast;
      uni.showToast = options => { window.registrationSmokeToasts.push(options.title); return original(options); };
    });
    const field = label => page.locator('.uni-forms-item').filter({ has: page.getByText(label, { exact: true }) }).locator('input');
    const loginName = field('登录名');
    await loginName.waitFor({ state: 'visible', timeout: 15000 }).catch(async error => {
      console.log('Browser diagnostics:', JSON.stringify({ url: page.url(), text: await page.locator('body').innerText(), errors, apiPaths: apiCalls.map(call => call.path) })); throw error;
    });
    await loginName.fill('Buyer_01');
    await page.getByText('获取图片', { exact: true }).click();
    await page.locator('.captcha-image').waitFor({ state: 'visible' });
    assert.equal(await page.getByText('手机号', { exact: true }).count(), 0);
    assert.equal(await page.getByText('短信验证码', { exact: true }).count(), 0);
    await field('密码').fill('Password123');
    await field('确认密码').fill('OtherPass123');
    await field('图片验证码').fill('ab123');
    await page.locator('.agreement-option').first().locator('uni-radio').click();
    await page.getByText('注册并完善企业资料', { exact: true }).click();
    await page.getByText('两次输入的密码不一致', { exact: true }).waitFor({ state: 'visible' });
    assert.equal(apiCalls.filter(call => call.path.endsWith('/register-image')).length, 0);
    await field('确认密码').fill('Password123');
    await field('确认密码').blur();
    await page.getByText('两次输入的密码不一致', { exact: true }).waitFor({ state: 'hidden' });
    const artifacts = await mkdtemp(join(tmpdir(), 'h5-image-registration-'));
    await page.locator('.login-wrap').evaluate(element => { element.scrollTop = 0; });
    await page.screenshot({ path: join(artifacts, 'registration-mobile.png'), fullPage: true });
    const registerResponse = page.waitForResponse(response => response.url().endsWith('/member/mall-auth/register-image'), { timeout: 12000 }).catch(async error => {
      console.log('Submit diagnostics:', JSON.stringify({ text: await page.locator('body').innerText(), toasts: await page.evaluate(() => window.registrationSmokeToasts), inputs: await page.locator('input').evaluateAll(inputs => inputs.map(input => input.value)), radio: await page.locator('.agreement-option').first().innerHTML(), errors, apiPaths: apiCalls.map(call => call.path) })); throw error;
    });
    await page.getByText('注册并完善企业资料', { exact: true }).click();
    await registerResponse;
    const submitted = apiCalls.find(call => call.path.endsWith('/register-image'));
    assert.equal(submitted.body.loginName, 'buyer_01'); assert.equal(submitted.body.imageCode, 'AB123');
    assert.equal(submitted.body.contactMobile, undefined); assert.equal(submitted.body.mobile, undefined);
    assert.equal(apiCalls.some(call => /send-code|send-sms-code/.test(call.path)), false);
    assert.deepEqual(errors, []);
    registrationSucceeds = true;
    await page.getByText('获取图片', { exact: true }).click();
    await page.locator('.captcha-image').waitFor({ state: 'visible' });
    await field('图片验证码').fill('ab123'); await field('图片验证码').blur();
    await page.getByText('请输入图片验证码', { exact: true }).waitFor({ state: 'hidden' });
    await page.getByText('注册并完善企业资料', { exact: true }).click();
    await page.waitForURL('**/pages/user/enterprise-profile**');
    assert.equal(apiCalls.some(call => call.path === '/member/mall-tenant/session'), true);
    assert.deepEqual(errors, []);
    await page.evaluate(() => localStorage.clear());
    imageEnabled = false;
    await page.goto(origin + '/pages/index/user?mallRegistration=1&tenantId=1', { waitUntil: 'networkidle' });
    await page.getByText('注册暂未开放，请等待商城升级完成', { exact: true }).waitFor({ state: 'visible' });
    assert.equal(await field('登录名').count(), 0);
    assert.equal(await page.getByText('获取验证码', { exact: true }).count(), 0);
    console.log('Mobile registration smoke passed: optional contact, confirmation, agreement, image failure/retry, verified signup to enterprise profile, closed-capability gate. Screenshot: ' + join(artifacts, 'registration-mobile.png'));
  } finally {
    await browser?.close(); await new Promise(done => server.close(done));
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
