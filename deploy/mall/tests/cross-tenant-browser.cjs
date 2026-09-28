// Writes only disposable buyer2009 orders on the isolated acceptance ports.
const assert = require('node:assert/strict');
const { chromium } = require('../../../txgy-ui-admin-vben/node_modules/playwright');

(async () => {
  if (!process.argv.includes('--allow-demo-writes')) throw new Error('--allow-demo-writes required');
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const checks = [];
  try {
    const pc = await browser.newPage();
    await pc.goto('http://127.0.0.1:25175/companies', { waitUntil: 'networkidle' });
    const kedun = pc.locator('.company-card').filter({ hasText: '科盾防护' });
    await kedun.waitFor();
    const facts = await kedun.locator('.company-facts').innerText();
    assert.match(facts, /5 家店铺/);
    assert.match(facts, /35 件公开商品/);
    checks.push('PC isolated seed company counts use all shops: Kedun 5 / 35 (running DB is 6 / 36)');
    const products = await (await pc.request.get('http://127.0.0.1:25175/app-api/mall/portal/products?pageNo=1&pageSize=10')).json();
    const product = products.data.list.find(p => Number(p.tenantId) !== 2009);
    assert(product);
    await pc.goto(`http://127.0.0.1:25175/products/${product.spuId}`, { waitUntil: 'networkidle' });
    await pc.getByRole('heading', { name: product.spuName, exact: true }).waitFor();
    assert(!(await pc.locator('body').innerText()).includes('商品 SPU 不存在'));
    checks.push('PC global product route reads seller product');

    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    const wrongImageRequests = [];
    mobile.on('request', r => { if (/127\.0\.0\.1:48080\/.*infra\/file/.test(r.url())) wrongImageRequests.push(r.url()); });
    await mobile.goto('http://127.0.0.1:23000/pages/index/user', { waitUntil: 'networkidle' });
    await mobile.locator('.nick-name').filter({ hasText: '请先登录' }).first().click();
    const fields = mobile.locator('.account-login-form input');
    await fields.nth(0).fill('buyer2009');
    await fields.nth(1).fill('admin123');
    await mobile.locator('.agreement-option').first().locator('uni-radio').click();
    const [loginResponse] = await Promise.all([
      mobile.waitForResponse(r => r.url().includes('/member/mall-auth/platform-login') && r.request().method() === 'POST'),
      mobile.locator('.login-btn-start').click(),
    ]);
    const login = await loginResponse.json();
    assert.equal(login.code, 0);
    await mobile.locator('.account-login-form').waitFor({ state: 'hidden' });
    const api = async (path, data) => {
      const response = await mobile.request.fetch('http://127.0.0.1:23000/app-api' + path, {
        method: data ? 'POST' : 'GET', data,
        headers: { Authorization: 'Bearer ' + login.data.accessToken, 'tenant-id': String(login.data.tenantId), terminal: '20' },
      });
      const result = await response.json();
      assert.equal(result.code, 0, path + ': ' + result.msg);
      return result.data;
    };
    const detail = await api('/mall/portal/products/' + product.spuId);
    await api('/member/address/create', { name: '浏览器隔离验收', mobile: '13900000000', areaId: 110101,
      detailAddress: '隔离库，不发货', defaultStatus: true });
    const cartId = await api('/trade/platform-cart/add', { skuId: detail.skus[0].skuId, quantity: 1 });
    await mobile.goto(`http://127.0.0.1:23000/pages/platform/checkout?cartItemIds=${cartId}`, { waitUntil: 'networkidle' });
    await mobile.locator('.checkout-footer .submit-button').waitFor();
    await mobile.waitForFunction(() => !document.querySelector('.checkout-footer .submit-button')?.hasAttribute('disabled'));
    const [submitted] = await Promise.all([
      mobile.waitForResponse(r => r.url().includes('/trade/platform-checkout/submit') && r.request().method() === 'POST'),
      mobile.locator('.checkout-footer .submit-button').click(),
    ]);
    const order = await submitted.json();
    assert.equal(order.code, 0);
    assert.equal(order.data.aggregateStatus, 'ALL_UNPAID');
    await mobile.getByText('订单已创建', { exact: true }).waitFor();
    assert.equal(await mobile.locator('.submit-error').count(), 0);
    await mobile.getByText('查看记录', { exact: true }).click();
    await mobile.waitForURL(u => u.pathname.includes('/pages/platform/history'));
    await mobile.getByText('继续合并支付', { exact: true }).click();
    await mobile.waitForURL(u => u.pathname.includes('/pages/pay/index'));
    await mobile.locator('.save-btn').waitFor();
    await mobile.waitForFunction(() => !document.querySelector('.save-btn')?.hasAttribute('disabled'));
    checks.push('H5 real page checkout -> created modal -> history -> aggregate cashier enabled (no payment)');
    const child = order.data.childOrders[0];
    await mobile.goto(`http://127.0.0.1:23000/pages/pay/index?id=${order.data.platformPayOrderId}&orderType=goods&tradeOrderId=${child.orderId}`, { waitUntil: 'networkidle' });
    await mobile.locator('.save-btn').waitFor();
    await mobile.waitForFunction(() => !document.querySelector('.save-btn')?.hasAttribute('disabled'));
    checks.push('H5 child-order entry -> same aggregate cashier enabled');
    assert.equal(wrongImageRequests.length, 0);
    await pc.goto('http://127.0.0.1:25175/platform-login', { waitUntil: 'networkidle' });
    await pc.getByPlaceholder('请输入登录名或老账号手机号').fill('buyer2009');
    await pc.getByPlaceholder('请输入密码', { exact: true }).fill('admin123');
    const [pcLogin] = await Promise.all([
      pc.waitForResponse(r => r.url().includes('/member/mall-auth/platform-login') && r.request().method() === 'POST'),
      pc.locator('.submit-button').click(),
    ]);
    assert.equal((await pcLogin.json()).code, 0);
    await pc.waitForURL(u => !u.pathname.includes('login'));
    const childDetail = await api('/trade/order/get-detail?id=' + child.orderId);
    await pc.goto('http://127.0.0.1:25175/member/orders', { waitUntil: 'networkidle' });
    const card = pc.locator('.order-list > article').filter({ hasText: childDetail.no });
    await card.waitFor();
    await card.locator('.order-goods a').first().click();
    await pc.waitForURL(u => u.pathname === '/products/' + product.spuId);
    await pc.getByRole('heading', { name: product.spuName, exact: true }).waitFor();
    await pc.goto('http://127.0.0.1:25175/member/orders', { waitUntil: 'networkidle' });
    await card.getByRole('button', { name: '取消订单', exact: true }).click();
    const [cancelled] = await Promise.all([
      pc.waitForResponse(r => r.url().includes('/trade/order/cancel?') && r.request().method() === 'DELETE'),
      pc.getByRole('button', { name: '确定取消', exact: true }).click(),
    ]);
    assert.equal((await cancelled.json()).code, 0);
    assert.equal((await api('/trade/order/get-detail?id=' + child.orderId)).status, 40);
    checks.push('PC actual order product click and cancel dialog -> cancelled state persisted');
    const reviewArg = process.argv.indexOf('--completed-order-id');
    if (reviewArg > 0) {
      const reviewId = process.argv[reviewArg + 1];
      assert.match(reviewId, /^[1-9]\d*$/);
      await pc.goto(`http://127.0.0.1:25175/member/orders/${reviewId}/comment`, { waitUntil: 'networkidle' });
      await pc.locator('.comment-forms textarea').fill('跨租户页面评价隔离验收');
      const [reviewed] = await Promise.all([
        pc.waitForResponse(r => r.url().includes('/trade/order/item/create-comment') && r.request().method() === 'POST'),
        pc.getByRole('button', { name: /发布剩余评价/ }).click(),
      ]);
      assert.equal((await reviewed.json()).code, 0);
      await pc.waitForURL(u => u.pathname === '/member/orders/' + reviewId);
      assert.equal((await api('/trade/order/get-detail?id=' + reviewId)).commentStatus, true);
      checks.push('PC completed-order fixture -> actual text review submission -> persisted and returned to detail');
    }
    console.log(JSON.stringify({ status: 'passed', checks, isolatedOrderId: child.orderId,
      notVerified: ['Android hardware', 'Real payment', 'Physical delivery', 'Image bytes on legacy running database'] }, null, 2));
  } catch (error) {
    console.error(JSON.stringify({ status: 'failed', checks, error: error.message }, null, 2));
    process.exitCode = 1;
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
