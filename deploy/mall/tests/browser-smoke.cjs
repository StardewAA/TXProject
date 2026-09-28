// Demo login and read-only page acceptance against the isolated test ports.
// Requires the admin workspace's declared Playwright dependency and local Edge.
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { chromium } = require('../../../txgy-ui-admin-vben/node_modules/playwright');

(async () => {
  if (!process.argv.includes('--allow-demo-login')) throw new Error('--allow-demo-login is required');
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const checks = [];
  try {
    const pc = await browser.newPage();
    await pc.goto('http://127.0.0.1:25175/platform-login', { waitUntil: 'networkidle' });
    await pc.getByPlaceholder('请输入登录名或老账号手机号').fill('buyer2009');
    await pc.getByPlaceholder('请输入密码', { exact: true }).fill('admin123');
    const [pcLogin] = await Promise.all([
      pc.waitForResponse(r => r.url().includes('/member/mall-auth/platform-login') && r.request().method() === 'POST'),
      pc.locator('.submit-button').click(),
    ]);
    assert.equal((await pcLogin.json()).code, 0);
    await pc.waitForURL(u => !u.pathname.includes('login'));
    await pc.goto('http://127.0.0.1:25175/member', { waitUntil: 'networkidle' });
    assert(!pc.url().includes('login'));
    checks.push('PC browser login and member page after navigation');
    await pc.goto('http://127.0.0.1:25175/download/mobile', { waitUntil: 'networkidle' });
    assert((await pc.locator('body').innerText()).includes('Android'));
    const release = await (await pc.request.get('http://127.0.0.1:25175/downloads/android-release.json')).json();
    assert(release.apkUrl.startsWith('/downloads/') && !release.apkUrl.includes('..'));
    const apk = await pc.request.get('http://127.0.0.1:25175' + release.apkUrl);
    assert.equal(apk.status(), 200);
    const bytes = await apk.body();
    assert.equal(bytes.length, release.fileSizeBytes);
    assert.equal(createHash('sha256').update(bytes).digest('hex'), release.sha256);
    checks.push('PC mobile download page, APK size and SHA-256');

    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    await mobile.goto('http://127.0.0.1:23000/pages/index/user', { waitUntil: 'networkidle' });
    await mobile.locator('.nick-name').filter({ hasText: '请先登录' }).first().click();
    const account = mobile.locator('.account-login-form input').first();
    const password = mobile.locator('.account-login-form input').nth(1);
    await account.fill('sight');
    await password.click();
    const hint = mobile.locator('.account-login-form .uni-error-message').first();
    await hint.waitFor({ state: 'visible' });
    const h = await hint.boundingBox(), p = await password.boundingBox();
    assert(h && p && h.y + h.height + 4 <= p.y, 'Account error hint overlaps password row');
    checks.push('H5 account error layout at 390px');
    await account.fill('buyer2009');
    await password.fill('admin123');
    await mobile.locator('.agreement-option').first().locator('uni-radio').click();
    const [h5Login] = await Promise.all([
      mobile.waitForResponse(r => r.url().includes('/member/mall-auth/platform-login') && r.request().method() === 'POST'),
      mobile.locator('.login-btn-start').click(),
    ]);
    assert.equal((await h5Login.json()).code, 0);
    await mobile.locator('.account-login-form').waitFor({ state: 'hidden' });
    await mobile.reload({ waitUntil: 'networkidle' });
    await mobile.getByText('演示采购用户', { exact: true }).first().waitFor({ state: 'visible' });
    checks.push('H5 agreement, real login and member page after reload');

    const admin = await browser.newPage();
    await admin.goto('http://127.0.0.1:25173/#/auth/login', { waitUntil: 'networkidle' });
    await admin.getByPlaceholder('请输入用户名或工号').fill('admin');
    await admin.getByPlaceholder('请输入密码', { exact: true }).fill('admin123');
    const [adminLogin] = await Promise.all([
      admin.waitForResponse(r => r.url().includes('/system/auth/login') && r.request().method() === 'POST'),
      admin.getByRole('button', { name: 'login', exact: true }).click(),
    ]);
    assert.equal((await adminLogin.json()).code, 0);
    await admin.waitForURL(u => u.hash.includes('/dashboard/'));
    checks.push('Admin browser login');
    await admin.goto('http://127.0.0.1:25173/#/mall/home', { waitUntil: 'networkidle' });
    const button = admin.getByRole('button', { name: '进入 PC 商城' });
    await button.waitFor({ state: 'visible' });
    assert(!(await button.isDisabled()), 'PC entry URL missing from admin build');
    const [popup] = await Promise.all([admin.context().waitForEvent('page'), button.click()]);
    await popup.waitForURL(u => ['http:', 'https:'].includes(u.protocol), { waitUntil: 'domcontentloaded' });
    const target = new URL(popup.url());
    assert.equal(target.port, '25175');
    assert(['127.0.0.1', 'localhost'].includes(target.hostname));
    assert.equal(target.search, '');
    assert.equal(target.hash, '');
    checks.push('Admin mall home opens PC without credentials in URL');
    console.log(JSON.stringify({ status: 'passed', scope: 'isolated-browser-smoke', checks,
      notVerified: ['Browser checkout/payment', 'Image upload', 'Android hardware', 'OSS TLS'] }, null, 2));
  } catch (error) {
    console.error(JSON.stringify({ status: 'failed', completedChecks: checks, error: error.message }, null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
