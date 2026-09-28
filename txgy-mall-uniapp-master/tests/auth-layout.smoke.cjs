const assert = require('node:assert/strict');
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    await page.goto(process.env.TXGY_MALL_H5_ORIGIN || 'http://127.0.0.1:3000/pages/index/user', {
      waitUntil: 'domcontentloaded',
    });
    await page.locator('.nick-name').filter({ hasText: '请先登录' }).first().click();
    const account = page.locator('.account-login-form input').first();
    await account.waitFor({ state: 'visible' });
    await account.fill('sight');
    const passwordField = page.locator('.account-login-form input').nth(1);
    await passwordField.click();
    const message = page.locator('.account-login-form .uni-error-message').first();
    await message.waitFor({ state: 'visible' });
    const hint = await message.boundingBox();
    const password = await passwordField.boundingBox();
    assert(hint && password && hint.y + hint.height + 4 <= password.y,
      `Username hint overlaps password input: ${JSON.stringify({ hint, password })}`);
    console.log('PASS: mobile login-name validation hint stays above the password row.');
  } finally {
    await browser.close();
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
