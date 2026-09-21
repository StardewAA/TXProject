// Built PC UI, mocked APIs only. No real account, order or address writes.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { chromium } = require('playwright');
const root = resolve(__dirname, '../dist'), output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-comparison-20260917/pc');
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
let browser, origin;
const reports = [];
const server = createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    let target = resolve(root, '.' + pathname);
    if ((target !== root && !target.startsWith(root + sep)) || pathname.includes('/app-api/')) return response.writeHead(503).end();
    if (!extname(target)) target = join(root, 'index.html');
    response.writeHead(200, { 'Content-Type': types[extname(target)] || 'application/octet-stream' }).end(await readFile(target));
  } catch { response.writeHead(404).end(); }
});
const product = { id: 10, name: '隔离测试岩棉板', price: 3100, stock: 100, shopId: 7, shopName: '甲店', skus: [{ id: 11, price: 3100, stock: 100, properties: [{ propertyName: '厚度', valueName: '20mm' }] }, { id: 12, price: 3300, stock: 100, properties: [{ propertyName: '厚度', valueName: '30mm' }] }] };
function item(id) { return { skuId: Number(id), available: id !== '99', ...(id === '99' ? { unavailableReason: '商品当前不可用' } : { spuId: Number(id) === 11 ? 10 : 20, spuName: Number(id) === 11 ? '隔离测试岩棉板' : '隔离测试玻璃棉', shopId: Number(id), shopName: '店铺' + id, price: Number(id) === 11 ? 3100 : 3300 }), properties: [{ name: '厚度', value: id === '11' ? '20mm' : '30mm' }], standardStatus: 'UNKNOWN', standardMessage: '标准资料不足，不能认定同款', stockStatus: 'PENDING_ERP', stockMessage: '库存以下单和 ERP 确认为准', unknowns: ['包装待确认', '单位待确认'], matchedRequirements: [], differences: [] }; }
async function fixture(name, options = {}) {
  const context = await browser.newContext({ viewport: { width: 1480, height: 1150 }, serviceWorkers: 'block' });
  await context.addInitScript(({ signed, guest }) => {
    if (signed) { localStorage.setItem('mall-pc-access-token', 'isolated-access'); localStorage.setItem('mall-pc-refresh-token', 'isolated-refresh'); }
    if (guest) localStorage.setItem('mall-pc-comparison-v1:1', JSON.stringify(guest));
  }, { signed: options.signed ?? false, guest: options.guest });
  const page = await context.newPage(); page.setDefaultTimeout(12000);
  const calls = [], errors = [], state = { list: { version: 1, skuIds: options.ids || [11, 22], quantity: 2, preference: 'PRICE' }, userId: 77, failCalc: false, conflict: false, wrongResponse: false, calcGate: null, quoteGate: null };
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url());
    if (!url.pathname.includes('/app-api/')) return url.origin === origin || url.protocol === 'data:' ? route.continue() : route.abort();
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = request.postDataJSON(); calls.push({ path, body, method: request.method(), headers: request.headers() });
    let data = [], code = 0;
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: state.userId };
    else if (path === '/member/user/get') data = { tenantId: 1, id: state.userId, nickname: '隔离测试会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') data = { validList: [], invalidList: [] };
    else if (path === '/product/enterprise-membership/mine') data = null;
    else if (path === '/trade/order-workbench/summary') data = { scopeKey: '1:1:77:BUYER:0', pendingCount: 0, unreadCount: 0, newCount: 0, cursor: '0', resetRequired: false, pollIntervalSeconds: 30, entries: [], rowEntries: [], hasMore: false, todoCounts: {}, orderStatusCounts: {} };
    else if (path === '/product/spu/get-detail') data = product;
    else if (path === '/product/shop/get') data = { id: 7, name: '甲店' };
    else if (path === '/product/comment/page') data = { list: [], total: 0 };
    else if (path === '/product/comparison/list') {
      assert.ok(['Bearer isolated-access', 'Bearer replacement-access'].includes(request.headers().authorization));
      if (request.method() === 'PUT') {
        if (state.conflict) code = 409;
        else { assert.equal(body.version, state.list.version); state.list = { ...body, version: state.list.version + 1 }; }
      }
      data = state.list;
    } else if (path === '/product/comparison/calculate') {
      if (state.calcGate) await state.calcGate;
      if (state.failCalc) return route.abort('failed');
      data = { quantity: body.quantity, preference: body.preference, quantityUnit: 'SALE_UNIT', calculatedAt: 1789603200000, strategyVersion: 'comparison-v1', recommendationStatus: 'INSUFFICIENT_STANDARD_DATA', recommendationReason: '缺少可信的标准规格和单位，暂不推荐、不排序', recommendedSkuId: null, items: body.skuIds.map(item), warnings: ['原始销售份数不等于统一计量单位'] };
      if (state.wrongResponse) data.items[0].skuId = 900;
    } else if (path === '/member/address/list') data = [{ id: 41, name: '测试', mobile: '13800000000', areaName: '北京市 东城区', detailAddress: '隔离地址', defaultStatus: true }];
    else if (path === '/trade/product-comparison/quote') {
      assert.equal(request.headers().authorization, 'Bearer isolated-access');
      if (state.quoteGate) await state.quoteGate;
      data = { quantity: body.quantity, addressId: body.addressId ?? null, priceBasis: 'CHECKOUT_ESTIMATE', calculatedAt: 1789603200000, warnings: [], items: body.skuIds.map(id => ({ skuId: id, quantity: body.quantity, status: body.addressId ? 'AVAILABLE' : 'ADDRESS_REQUIRED', message: body.addressId ? '独立结算估算' : '请选择收货地址', amountsComplete: Boolean(body.addressId), goodsAmount: 6200, discountAmount: 0, shippingFee: body.addressId ? 500 : null, totalAmount: body.addressId ? 6700 : null })) };
    } else if (path === '/trade/cart/add') data = 1;
    else if (request.method() !== 'GET' && path !== '/trade/order-workbench/ack') throw new Error('Unexpected write ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code, data, msg: code ? '对比清单已被另一端修改' : '' }) });
  });
  return { page, state, calls, async finish(extra = {}) {
    assert.deepEqual(errors, []); assert.equal(calls.some(call => /\/order\/create|\/pay\/|\/address\/(create|update)/.test(call.path)), false);
    reports.push({ name, passed: true, ...extra }); await context.close();
  } };
}
(async () => {
  await mkdir(output, { recursive: true }); await new Promise(ready => server.listen(0, '127.0.0.1', ready)); origin = 'http://127.0.0.1:' + server.address().port;
  browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const sku = await fixture('multi-SKU detail requires explicit comparison selection');
    await sku.page.goto(origin + '/goods/10', { waitUntil: 'networkidle' }); await sku.page.getByRole('button', { name: '加入对比', exact: true }).click();
    await sku.page.getByText('请先点击选择需要对比的实际规格', { exact: false }).waitFor();
    assert.equal(await sku.page.evaluate(() => localStorage.getItem('mall-pc-comparison-v1:1')), null);
    await sku.page.locator('.sku-area button').filter({ hasText: '30mm' }).click(); await sku.page.getByRole('button', { name: '加入对比', exact: true }).click();
    await sku.page.getByText('已加入对比，可从顶部或右下角查看', { exact: true }).waitFor(); assert.equal(await sku.page.evaluate(() => localStorage.getItem('mall-pc-comparison-v1:1')), '["12"]'); await sku.finish();

    const guest = await fixture('guest parallel comparison and difference filter never claim same product', { guest: ['11', '22'] });
    await guest.page.goto(origin + '/compare', { waitUntil: 'networkidle' }); await guest.page.getByTestId('comparison-recommendation').waitFor();
    await guest.page.getByText('推荐结果：暂不推荐', { exact: true }).waitFor(); assert.equal(guest.calls.some(call => call.path === '/product/comparison/list'), false);
    await guest.page.getByText('仅看差异', { exact: true }).click(); assert.equal(await guest.page.locator('tbody tr').filter({ hasText: '同款与单位标准' }).count(), 0);
    await guest.page.screenshot({ path: join(output, 'pc-comparison-differences.png'), fullPage: true }); await guest.finish();

    const merge = await fixture('login guest merge requires explicit confirmation', { signed: true, guest: ['33'] });
    await merge.page.goto(origin + '/compare', { waitUntil: 'networkidle' }); await merge.page.getByTestId('comparison-recommendation').waitFor();
    assert.equal(merge.calls.filter(call => call.method === 'PUT').length, 0);
    await merge.page.getByRole('button', { name: '选择合并临时清单' }).click(); assert.equal(merge.calls.filter(call => call.method === 'PUT').length, 0);
    await merge.page.getByRole('button', { name: '确认合并', exact: true }).click(); await merge.page.getByText('临时清单已合并', { exact: false }).waitFor();
    assert.deepEqual(merge.state.list.skuIds, ['11', '22', '33']); await merge.finish();

    const conflict = await fixture('revision conflict does not overwrite another device list', { signed: true });
    await conflict.page.goto(origin + '/compare', { waitUntil: 'networkidle' }); await conflict.page.getByTestId('comparison-recommendation').waitFor(); conflict.state.conflict = true;
    await conflict.page.getByRole('button', { name: '移除', exact: true }).first().click(); await conflict.page.getByText('不会覆盖另一端修改', { exact: false }).waitFor();
    assert.equal(conflict.calls.filter(call => call.method === 'PUT').length, 1); assert.deepEqual(conflict.state.list.skuIds, [11, 22]); await conflict.finish();

    const estimate = await fixture('explicit own-address quote and existing cart path', { signed: true });
    await estimate.page.goto(origin + '/compare', { waitUntil: 'networkidle' }); await estimate.page.getByTestId('comparison-recommendation').waitFor();
    await estimate.page.getByRole('button', { name: '读取本人收货地址' }).click(); await estimate.page.locator('select[aria-label="估算收货地址"] option').filter({ hasText: '隔离地址' }).waitFor({ state: 'attached' });
    assert.equal(await estimate.page.locator('select[aria-label="估算收货地址"]').inputValue(), '');
    await estimate.page.getByRole('button', { name: '估算费用', exact: true }).click(); await estimate.page.getByText('待确认（费用不完整）', { exact: true }).first().waitFor();
    assert.equal(estimate.calls.find(call => call.path === '/trade/product-comparison/quote').body.addressId, undefined);
    await estimate.page.locator('select[aria-label="估算收货地址"]').selectOption('41'); await estimate.page.getByRole('button', { name: '估算费用', exact: true }).click(); await estimate.page.getByText('¥67.00', { exact: true }).first().waitFor();
    await estimate.page.getByRole('button', { name: '加入购物车', exact: true }).first().click(); await estimate.page.getByText('已加入购物车，结算时将重新核验价格与库存', { exact: true }).waitFor();
    assert.deepEqual(estimate.calls.find(call => call.path === '/trade/cart/add').body, { skuId: 11, count: 2 }); await estimate.finish();

    const dirty = await fixture('unapplied quantity hides old quote and purchase actions', { signed: true });
    await dirty.page.goto(origin + '/compare', { waitUntil: 'networkidle' }); await dirty.page.getByTestId('comparison-recommendation').waitFor();
    assert.equal(await dirty.page.getByRole('spinbutton').isDisabled(), false);
    assert.equal(await dirty.page.getByRole('spinbutton').getAttribute('aria-disabled'), 'false');
    await dirty.page.getByRole('button', { name: '估算费用', exact: true }).click();
    await dirty.page.locator('.quote-grid').waitFor();
    await dirty.page.getByRole('spinbutton').fill('3'); await dirty.page.getByRole('spinbutton').press('Tab');
    await dirty.page.getByText('数量或偏好尚未应用', { exact: false }).waitFor();
    assert.equal(await dirty.page.locator('.comparison-table').count(), 0);
    assert.equal(await dirty.page.locator('.quote-grid').count(), 0);
    assert.equal(await dirty.page.getByRole('button', { name: '加入购物车', exact: true }).count(), 0);
    await dirty.page.getByRole('button', { name: '应用数量与偏好', exact: true }).click();
    await dirty.page.getByTestId('comparison-recommendation').waitFor(); assert.equal(dirty.state.list.quantity, 3);
    assert.equal(await dirty.page.getByRole('spinbutton').getAttribute('aria-disabled'), 'false');
    await dirty.page.locator('.control-row select').first().selectOption('DELIVERY');
    await dirty.page.getByText('数量或偏好尚未应用', { exact: false }).waitFor();
    assert.equal(await dirty.page.getByRole('button', { name: '加入购物车', exact: true }).count(), 0);
    await dirty.page.getByRole('button', { name: '应用数量与偏好', exact: true }).click();
    await dirty.page.getByTestId('comparison-recommendation').waitFor(); assert.equal(dirty.state.list.preference, 'DELIVERY'); await dirty.finish();

    const retry = await fixture('failed calculation and wrong-identity responses do not show cached prices', { guest: ['11', '22'] });
    retry.state.failCalc = true; await retry.page.goto(origin + '/compare', { waitUntil: 'networkidle' }); await retry.page.getByRole('button', { name: '重试读取对比' }).waitFor();
    retry.state.failCalc = false; retry.state.wrongResponse = true; await retry.page.getByRole('button', { name: '重试读取对比' }).click(); await retry.page.getByText('对比结果与当前选择不一致，请重试', { exact: true }).waitFor();
    assert.equal(await retry.page.locator('.comparison-table').count(), 0); retry.state.wrongResponse = false; await retry.page.getByRole('button', { name: '重试读取对比' }).click(); await retry.page.locator('.comparison-table').waitFor(); await retry.finish();

    const late = await fixture('leaving comparison while response pending cannot repaint account data', { signed: true });
    let release; late.state.calcGate = new Promise(resolve => { release = resolve }); await late.page.goto(origin + '/compare', { waitUntil: 'domcontentloaded' });
    await late.page.waitForFunction(() => document.body.innerText.includes('跨店商品对比'));
    await late.page.getByRole('link', { name: '继续选择商品 →', exact: true }).click(); release(); await late.page.waitForURL('**/goods?tenantId=1');
    assert.equal(await late.page.getByTestId('comparison-page').count(), 0); await late.finish();

    const swapped = await fixture('replaced account discards delayed private quote and old addresses', { signed: true });
    await swapped.page.goto(origin + '/compare', { waitUntil: 'networkidle' }); await swapped.page.getByTestId('comparison-recommendation').waitFor();
    await swapped.page.getByRole('button', { name: '读取本人收货地址' }).click(); await swapped.page.locator('select[aria-label="估算收货地址"] option').filter({ hasText: '隔离地址' }).waitFor({ state: 'attached' });
    await swapped.page.locator('select[aria-label="估算收货地址"]').selectOption('41'); let releaseQuote; swapped.state.quoteGate = new Promise(resolve => { releaseQuote = resolve });
    await swapped.page.getByRole('button', { name: '估算费用', exact: true }).click(); await swapped.page.waitForTimeout(100);
    swapped.state.userId = 88; swapped.state.list = { version: 8, skuIds: [88], quantity: 1, preference: 'PRICE' };
    await swapped.page.evaluate(() => { localStorage.setItem('mall-pc-access-token', 'replacement-access'); localStorage.setItem('mall-pc-refresh-token', 'replacement-refresh'); window.dispatchEvent(new StorageEvent('storage', { key: 'mall-pc-access-token', newValue: 'replacement-access' })); });
    await swapped.page.getByRole('button', { name: '刷新清单与数据' }).click(); await swapped.page.locator('thead').getByText('SKU 88', { exact: true }).waitFor(); releaseQuote(); await swapped.page.waitForTimeout(200);
    assert.equal(await swapped.page.locator('.quote-grid').count(), 0); assert.equal(await swapped.page.locator('select[aria-label="估算收货地址"] option').count(), 1); await swapped.finish();
    await writeFile(join(output, 'pc-comparison-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, testedAt: new Date().toISOString(), scenarios: reports }, null, 2));
    console.log('PASS ' + reports.length + ' PC comparison browser scenarios: ' + output);
  } catch (error) { await writeFile(join(output, 'pc-comparison-failure.json'), JSON.stringify({ passed: false, error: error.message, reports }, null, 2)); throw error; }
  finally { await browser.close(); await new Promise(done => server.close(done)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
