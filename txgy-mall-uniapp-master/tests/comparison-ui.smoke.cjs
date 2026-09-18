// Isolated built H5, all API calls mocked. Never log in or write real mall data.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { chromium } = require('playwright');
const root = resolve(__dirname, '../dist/build/h5');
const output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-comparison-20260917/h5');
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml' };
let browser, origin; const reports = [];
const server = createServer(async (req, res) => {
  try {
    const path = decodeURIComponent(new URL(req.url, 'http://local').pathname);
    let file = resolve(root, '.' + path);
    if ((file !== root && !file.startsWith(root + sep)) || path.includes('/app-api/')) return res.writeHead(503).end();
    if (!extname(file)) file = join(root, 'index.html');
    res.writeHead(200, { 'Content-Type': mime[extname(file)] || 'application/octet-stream' }).end(await readFile(file));
  } catch { res.writeHead(404).end(); }
});
const selection = (skuIds = [11, 22, 33], more = {}) => ({ version: 3, skuIds, quantity: 1, preference: 'PRICE', ...more });
async function fixture(name, options = {}) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, serviceWorkers: 'block' });
  await context.addInitScript(({ guest, anonymous }) => {
    if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
    if (!anonymous) {
      localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'compare-test-token' }));
      localStorage.setItem('refresh-token', JSON.stringify({ type: 'string', data: 'compare-test-refresh' }));
    }
    if (guest) localStorage.setItem('mall-comparison-guest-v1:1', JSON.stringify({ type: 'object', data: guest }));
  }, options);
  const page = await context.newPage(); page.setDefaultTimeout(15000);
  const calls = [], errors = [], state = { list: selection(options.ids), fail: !!options.fail, conflict: !!options.conflict };
  let release; const gate = new Promise(resolve => { release = resolve; });
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url());
    if (!url.pathname.includes('/app-api/')) return url.origin === origin ? route.continue() : route.abort();
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = req.postDataJSON(), method = req.method();
    calls.push({ path, body, method }); let code = 0, data = null;
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: 77 };
    else if (path === '/member/user/get') data = { tenantId: 1, id: 77, nickname: '隔离比较会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') data = { validList: [], invalidList: [] };
    else if (path === '/pay/wallet/get') data = { balance: 0 };
    else if (path === '/trade/order/get-count') data = {};
    else if (path === '/promotion/coupon/get-unused-count') data = 0;
    else if (path === '/member/address/list') data = [{ id: 41, name: '隔离收件人', areaName: '北京市', detailAddress: '隔离收货点' }];
    else if (path === '/product/comparison/list') {
      assert.equal(req.headers().authorization?.replace(/^Bearer /, ''), 'compare-test-token');
      if (method === 'PUT') {
        if (state.conflict || body.version !== state.list.version) code = 409;
        else state.list = { ...body, version: state.list.version + 1 };
      }
      data = { ...state.list };
    } else if (path === '/product/comparison/calculate') {
      if (state.fail) code = 503;
      data = { ...body, quantityUnit: 'SALE_UNIT', calculatedAt: Date.now(), strategyVersion: 'comparison-v1', recommendedSkuId: null,
        recommendationStatus: 'INSUFFICIENT_STANDARD_DATA', recommendationReason: '缺少核验过的同款资料，暂不按价格推荐', warnings: ['隔离测试，运费未确认'],
        items: body.skuIds.map((skuId, index) => ({ skuId, available: true, spuId: skuId + 100, spuName: '隔离商品 ' + skuId,
          shopId: index + 1, shopName: '隔离店铺 ' + (index + 1), price: 12300 + index * 100,
          properties: [{ name: '材质', value: index === 0 ? '<img src=x onerror="window.pwned=1">' : '岩棉' }, { name: '单位', value: index === 0 ? 'kg' : 'L' }],
          standardStatus: 'UNKNOWN', standardMessage: '标准资料待补充', stockMessage: '库存待 ERP 确认', unknowns: ['单位包装待确认'] })) };
      if (options.hold) await gate;
    } else if (path === '/trade/product-comparison/quote') {
      data = { quantity: body.quantity, addressId: body.addressId, calculatedAt: Date.now(), priceBasis: 'CHECKOUT_ESTIMATE', warnings: ['不锁库存'],
        items: body.skuIds.map(skuId => ({ skuId, quantity: body.quantity, amountsComplete: !!body.addressId, status: body.addressId ? 'AVAILABLE' : 'ADDRESS_REQUIRED',
          message: body.addressId ? '独立结算预估' : '请先选择收货地址', goodsAmount: body.addressId ? 24600 : null,
          discountAmount: body.addressId ? 100 : null, shippingFee: body.addressId ? 600 : null, totalAmount: body.addressId ? 25100 : null })) };
    } else if (path === '/trade/cart/add') { if (options.holdCart) await gate; data = 55; }
    else if (method !== 'GET') throw new Error('Unexpected write ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify({ code, data, msg: code ? '隔离模拟失败' : '' }) });
  });
  return { page, state, calls, release, async open() { await page.goto(origin + '/pages/goods/compare?tenantId=1', { waitUntil: 'networkidle' }); },
    async finish() {
      assert.deepEqual(errors, []); assert.equal(await page.evaluate(() => window.pwned), undefined);
      assert.equal(calls.some(call => /\/create$|\/receive$|\/refund|\/delivery$/.test(call.path)), false);
      reports.push({ name, passed: true, calls: calls.map(call => call.path), errors }); await context.close();
    } };
}
(async () => {
  await mkdir(output, { recursive: true }); await new Promise(done => server.listen(0, '127.0.0.1', done));
  origin = 'http://127.0.0.1:' + server.address().port; browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const base = await fixture('three shops raw differences no fabricated recommendation'); await base.open();
    await base.page.getByText('缺少核验过的同款资料，暂不按价格推荐', { exact: true }).waitFor();
    assert.equal(await base.page.locator('.product-column').count(), 3); assert.equal(await base.page.locator('.product-column img').count(), 0);
    await base.page.screenshot({ path: join(output, 'h5-comparison.png'), fullPage: true }); await base.finish();

    const quantity = await fixture('quantity CAS sync and cart revalidation'); await quantity.open();
    await quantity.page.locator('.product-column').first().waitFor(); await quantity.page.locator('input').fill('2');
    await quantity.page.getByText('保存设置并比较', { exact: true }).click();
    await quantity.page.waitForFunction(() => document.body.innerText.includes('版本 4'));
    assert.equal(quantity.state.list.quantity, 2);
    const cartRead = quantity.page.waitForRequest(req => req.url().includes('/trade/cart/list'));
    await quantity.page.getByText('重新核验并加购', { exact: true }).first().click(); await cartRead;
    assert.deepEqual(quantity.calls.find(call => call.path === '/trade/cart/add').body, { skuId: 11, count: 2 });
    await quantity.finish();

    const noAddress = await fixture('missing address not zero shipping'); await noAddress.open();
    await noAddress.page.getByText('获取结算预估', { exact: true }).click();
    await noAddress.page.getByText('费用未完整确认，不展示到手总额。', { exact: true }).first().waitFor();
    assert.equal(noAddress.calls.find(call => call.path.endsWith('/quote')).body.addressId, undefined);
    assert.equal(await noAddress.page.getByText('￥0.00', { exact: false }).count(), 0); await noAddress.finish();

    const addressed = await fixture('explicit own address uses server quote amounts'); await addressed.open();
    await addressed.page.getByText('请选择本人收货地址（不默认选择） ›', { exact: true }).click();
    await addressed.page.screenshot({ path: join(output, 'h5-address-picker-diagnostic.png'), fullPage: false });
    await writeFile(join(output, 'h5-address-picker-dom.json'), JSON.stringify(await addressed.page.locator('uni-picker, select, .uni-picker-container, .uni-picker-item').evaluateAll(elements => elements.map(element => ({ html: element.outerHTML.slice(0, 16000), rect: element.getBoundingClientRect().toJSON(), display: getComputedStyle(element).display, visibility: getComputedStyle(element).visibility }))), null, 2));
    // uni-app keeps a hidden desktop select alongside its visible mobile wheel.
    // Select through the visible picker, not duplicate hidden .uni-picker-item nodes.
    await addressed.page.locator('.uni-picker-container:visible uni-picker-view-column .uni-picker-item').filter({ hasText: '北京市 隔离收货点' }).click();
    await addressed.page.locator('.uni-picker-action-confirm:visible').click();
    await addressed.page.getByText('获取结算预估', { exact: true }).click();
    await addressed.page.getByText('结算预估 ￥251.00', { exact: false }).first().waitFor();
    assert.equal(addressed.calls.find(call => call.path.endsWith('/quote')).body.addressId, 41); await addressed.finish();

    const guest = await fixture('guest isolated local basket no account write', { anonymous: true, guest: selection([11, 22], { version: 0 }) }); await guest.open();
    await guest.page.locator('.product-column').first().waitFor();
    await guest.page.getByText('移出对比', { exact: true }).first().click();
    await guest.page.waitForFunction(() => document.querySelectorAll('.product-column').length === 1);
    assert.equal(guest.calls.some(call => call.path === '/product/comparison/list'), false); await guest.finish();

    const merge = await fixture('guest merging is explicit preserves account quantity', { ids: [11, 22], guest: selection([33], { version: 0, quantity: 9 }) }); await merge.open();
    await merge.page.getByText('确认合并', { exact: true }).waitFor(); assert.equal(merge.calls.some(call => call.method === 'PUT'), false);
    await merge.page.getByText('确认合并', { exact: true }).click(); await merge.page.waitForFunction(() => document.querySelectorAll('.product-column').length === 3);
    assert.deepEqual(merge.state.list.skuIds, [11, 22, 33]); assert.equal(merge.state.list.quantity, 1); await merge.finish();

    const conflict = await fixture('CAS conflict refresh required no blind retry', { conflict: true }); await conflict.open();
    await conflict.page.locator('.product-column').first().waitFor(); await conflict.page.getByText('移出对比', { exact: true }).first().click();
    await conflict.page.getByText('清单已在另一端更新', { exact: false }).waitFor();
    assert.equal(conflict.calls.filter(call => call.method === 'PUT').length, 1); assert.equal(conflict.state.list.skuIds.length, 3); await conflict.finish();

    const retry = await fixture('failed calculation retry', { fail: true }); await retry.open();
    await retry.page.getByText('隔离模拟失败', { exact: true }).waitFor(); retry.state.fail = false;
    await retry.page.getByText('刷新清单', { exact: true }).click(); await retry.page.locator('.product-column').first().waitFor(); await retry.finish();

    const late = await fixture('account replacement hides late result', { hold: true }); await late.open();
    await late.page.evaluate(() => { localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'replacement-token' })); window.dispatchEvent(new StorageEvent('storage', { key: 'token' })); });
    late.release(); await late.page.getByText('登录账号已变化', { exact: false }).waitFor();
    assert.equal(await late.page.locator('.product-column').count(), 0); await late.finish();

    const hide = await fixture('hide during cart request does not leave busy stuck', { holdCart: true }); await hide.open();
    const pendingCart = hide.page.waitForRequest(req => req.url().includes('/trade/cart/add'));
    await hide.page.getByText('重新核验并加购', { exact: true }).first().click(); await pendingCart;
    await hide.page.getByText('继续选品', { exact: true }).click(); await hide.page.waitForURL(url => url.pathname === '/pages/goods/list');
    await hide.page.goBack(); hide.release();
    await hide.page.locator('.product-column').first().waitFor();
    assert.equal(await hide.page.getByText('刷新清单', { exact: true }).getAttribute('disabled'), null); await hide.finish();

    await writeFile(join(output, 'h5-comparison-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, scenarios: reports }, null, 2));
    console.log(JSON.stringify({ passed: true, scenarios: reports.length, output }));
  } catch (error) {
    await writeFile(join(output, 'failure.json'), JSON.stringify({ message: error.message, stack: error.stack, completed: reports }, null, 2)); throw error;
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
