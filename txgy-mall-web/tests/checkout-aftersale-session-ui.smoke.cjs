// Isolated built PC/H5 clients. All requests are mocked, including order and after-sale writes.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, join, extname, sep } = require('node:path');
const { chromium } = require('playwright');
const output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-readonly-audit-20260917/session-fixed');
const reports = []; let browser, activePage;
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
async function serve(root) {
  const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
  const server = createServer(async (request, response) => {
    try { const path = decodeURIComponent(new URL(request.url, 'http://local').pathname); let file = resolve(root, '.' + path);
      if ((file !== root && !file.startsWith(root + sep)) || path.includes('/app-api/')) return response.writeHead(503).end();
      if (!extname(file)) file = join(root, 'index.html'); response.writeHead(200, { 'Content-Type': mime[extname(file)] || 'application/octet-stream' }).end(await readFile(file));
    } catch { response.writeHead(404).end(); }
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done)); return { server, origin: 'http://127.0.0.1:' + server.address().port };
}
async function fixture(client, host, name) {
  const h5 = client === 'h5', context = await browser.newContext({ viewport: h5 ? { width: 390, height: 844 } : { width: 1480, height: 1100 }, isMobile: h5, hasTouch: h5, serviceWorkers: 'block' });
  await context.addInitScript(h5 => {
    if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
    if (h5) { localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'isolated-access' })); localStorage.setItem('refresh-token', JSON.stringify({ type: 'string', data: 'isolated-refresh' })); }
    else { localStorage.setItem('mall-pc-access-token', 'isolated-access'); localStorage.setItem('mall-pc-refresh-token', 'isolated-refresh'); sessionStorage.setItem('mall-checkout-items:tenant:1:member:77', JSON.stringify([{ skuId: 211, count: 1 }])); }
  }, h5);
  const page = activePage = await context.newPage(), calls = [], errors = [];
  const state = { userId: 77, orderCreated: false, cartGate: null, orderGate: null, applyGate: null, addressGate: null };
  page.setDefaultTimeout(15000); page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url());
    if (!url.pathname.includes('/app-api/')) return url.origin === host.origin ? route.continue() : route.abort();
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = req.postDataJSON(); calls.push({ path, body, authorization: req.headers().authorization });
    let data = null;
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: state.userId };
    else if (path === '/member/user/get') data = { id: state.userId, tenantId: 1, nickname: '隔离测试会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') { if (state.orderCreated && state.cartGate) { state.cartWaiting = true; await state.cartGate.promise; } data = { validList: [], invalidList: [] }; }
    else if (path === '/product/comparison/list') data = { version: 0, skuIds: [], quantity: 1, preference: 'PRICE' };
    else if (path === '/trade/order-workbench/summary') data = { scopeKey: '1:1:77:BUYER:0', pendingCount: 0, unreadCount: 0, newCount: 0, cursor: '0', resetRequired: false, pollIntervalSeconds: 30, entries: [], rowEntries: [], hasMore: false, todoCounts: {}, orderStatusCounts: {} };
    else if (path === '/pay/wallet/get') data = { balance: 0 };
    else if (path === '/trade/order/get-count') data = {};
    else if (path === '/promotion/coupon/get-unused-count') data = 0;
    else if (path === '/member/address/list') data = [{ id: 1, name: '隔离收件人', mobile: '19900000000', areaId: 1, detailAddress: '本机隔离地址', defaultStatus: true }];
    else if (path === '/system/area/tree') data = [{ id: 1, name: '隔离测试区域' }];
    else if (path === '/member/address/create') { if (state.addressGate) await state.addressGate.promise; data = 7; }
    else if (path === '/trade/order/settlement') data = { items: [{ skuId: 211, spuName: '隔离下单商品', price: 1000, count: 1 }], price: { payPrice: 1000, totalPrice: 1000, deliveryPrice: 0 } };
    else if (path === '/trade/order/create') { state.orderCreated = true; data = { id: 42, payOrderId: 99 }; }
    else if (path === '/trade/order/get-detail') { if (state.orderGate) await state.orderGate.promise; data = { id: 42, status: 20, shopId: 7, payPrice: 1000, items: [{ id: 12, spuName: '原账号隔离商品', price: 1000, payPrice: 1000, count: 1, properties: [] }] }; }
    else if (path === '/trade/config/get') data = { afterSaleRefundReasons: ['不需要了'], afterSaleReturnReasons: ['商品破损'] };
    else if (path === '/trade/after-sale/create') { if (state.applyGate) await state.applyGate.promise; data = 50; }
    else if (req.method() !== 'GET' && path !== '/trade/order-workbench/ack') throw new Error('Unexpected mock write: ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data, msg: '' }) });
  });
  return { page, state, calls, async open() { await page.goto(host.origin + (h5 ? '/pages/order/aftersale/apply?orderId=42&itemId=12&tenantId=1' : '/checkout?tenantId=1'), { waitUntil: 'networkidle' }); },
    async change() { state.userId = 88; await page.evaluate(h5 => { const key = h5 ? 'token' : 'mall-pc-access-token', value = h5 ? JSON.stringify({ type: 'string', data: 'replacement-access' }) : 'replacement-access'; localStorage.setItem(key, value); window.dispatchEvent(new StorageEvent('storage', { key, newValue: value })); }, h5); },
    async finish() { assert.deepEqual(errors, []); reports.push({ client, name, passed: true }); await context.close(); } };
}
async function fillAfterSale(test) {
  await test.page.locator('textarea').fill('原账号私有售后草稿'); await test.page.getByText('仅退款', { exact: true }).click();
  await test.page.getByText('请选择申请原因~', { exact: true }).click(); await test.page.getByText('不需要了', { exact: true }).click(); await test.page.locator('uni-button.close-btn').click();
}
(async () => {
  await mkdir(output, { recursive: true }); browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const pc = await serve(resolve(__dirname, '../dist')), h5 = await serve(resolve(__dirname, '../../txgy-mall-uniapp-master/dist/build/h5'));
  try {
    const checkout = await fixture('pc', pc, 'late cart refresh after successful create cannot navigate a replacement account'); checkout.state.cartGate = deferred(); await checkout.open();
    await checkout.page.getByRole('button', { name: '提交订单', exact: true }).click();
    while (!checkout.state.cartWaiting) await checkout.page.waitForTimeout(20);
    await checkout.change(); await checkout.page.getByRole('heading', { name: '登录账号已变化', exact: true }).waitFor(); checkout.state.cartGate.resolve(); await checkout.page.waitForTimeout(250);
    assert.equal(new URL(checkout.page.url()).pathname, '/checkout'); assert.equal(checkout.calls.filter(call => call.path === '/trade/order/create').length, 1);
    await checkout.page.screenshot({ path: join(output, 'pc-checkout-expired.png'), fullPage: true }); await checkout.finish();

    const address = await fixture('pc', pc, 'new address double click is single-flight and old completion cannot choose it for a replacement account'); address.state.addressGate = deferred(); await address.open();
    await address.page.getByRole('button', { name: '新增地址', exact: true }).click(); const dialog = address.page.getByRole('dialog');
    await dialog.locator('.el-form-item').filter({ hasText: '收货人' }).locator('input').fill('隔离新增收件人');
    await dialog.locator('.el-form-item').filter({ hasText: '手机号' }).locator('input').fill('19900000000');
    await dialog.locator('.el-cascader').click(); await address.page.getByRole('menuitem', { name: '隔离测试区域' }).click();
    await dialog.locator('textarea').fill('隔离地址门牌'); await dialog.getByRole('button', { name: '保存并使用', exact: true }).dblclick();
    while (!address.calls.some(call => call.path === '/member/address/create')) await address.page.waitForTimeout(20);
    await address.change(); address.state.addressGate.resolve(); await address.page.getByRole('heading', { name: '登录账号已变化', exact: true }).waitFor(); await address.page.waitForTimeout(200);
    assert.equal(address.calls.filter(call => call.path === '/member/address/create').length, 1); assert.equal(await address.page.getByRole('dialog').count(), 0); await address.finish();

    const oldDraft = await fixture('h5', h5, 'account replacement clears private after-sale draft and removes submit and service actions'); await oldDraft.open(); await fillAfterSale(oldDraft); await oldDraft.change();
    await oldDraft.page.getByText('登录账号已变化，旧订单及售后草稿已清除，请从当前账号的订单重新进入。', { exact: true }).waitFor();
    await oldDraft.page.locator('.uni-popup').waitFor({ state: 'hidden' });
    await oldDraft.page.waitForTimeout(400); // Let the closing transition finish before visual evidence.
    assert.equal(await oldDraft.page.locator('textarea, uni-button.sub-btn, uni-button.contcat-btn').count(), 0); assert.equal(oldDraft.calls.some(call => call.path === '/trade/after-sale/create'), false);
    await oldDraft.page.screenshot({ path: join(output, 'h5-aftersale-expired.png'), fullPage: true }); await oldDraft.finish();

    const lateRead = await fixture('h5', h5, 'late order response cannot restore old after-sale data after replacement'); lateRead.state.orderGate = deferred();
    await lateRead.page.goto(h5.origin + '/pages/order/aftersale/apply?orderId=42&itemId=12&tenantId=1', { waitUntil: 'domcontentloaded' });
    while (!lateRead.calls.some(call => call.path === '/trade/order/get-detail')) await lateRead.page.waitForTimeout(20);
    await lateRead.change(); lateRead.state.orderGate.resolve(); await lateRead.page.waitForTimeout(250); assert.equal(await lateRead.page.locator('textarea').count(), 0); assert.equal(lateRead.calls.some(call => call.path === '/trade/config/get'), false); await lateRead.finish();

    const lateSubmit = await fixture('h5', h5, 'old after-sale success does not redirect or restore a replacement account form'); lateSubmit.state.applyGate = deferred(); await lateSubmit.open(); await fillAfterSale(lateSubmit);
    await lateSubmit.page.locator('uni-button.sub-btn').click(); while (!lateSubmit.calls.some(call => call.path === '/trade/after-sale/create')) await lateSubmit.page.waitForTimeout(20);
    await lateSubmit.change(); lateSubmit.state.applyGate.resolve(); await lateSubmit.page.waitForTimeout(250);
    assert.equal(new URL(lateSubmit.page.url()).pathname, '/pages/order/aftersale/apply'); assert.equal(await lateSubmit.page.locator('textarea').count(), 0); assert.equal(lateSubmit.calls.filter(call => call.path === '/trade/after-sale/create').length, 1); await lateSubmit.finish();

    await writeFile(join(output, 'checkout-aftersale-session-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, testedAt: new Date().toISOString(), scenarios: reports }, null, 2)); console.log(JSON.stringify({ passed: true, scenarios: reports.length, output }));
  } catch (error) { await activePage?.screenshot({ path: join(output, 'failure.png'), fullPage: true }).catch(() => {}); await writeFile(join(output, 'failure.json'), JSON.stringify({ error: String(error), reports }, null, 2)); throw error; }
  finally { await browser.close(); await Promise.all([pc, h5].map(host => new Promise(done => host.server.close(done)))); }
})().catch(error => { console.error(error); process.exitCode = 1; });
