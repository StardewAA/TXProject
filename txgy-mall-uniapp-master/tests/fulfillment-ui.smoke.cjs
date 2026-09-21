// Built H5 UI, isolated browser and entirely mocked APIs. No real login/account/order/provider writes.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { chromium } = require('playwright');
const root = resolve(__dirname, '../dist/build/h5');
const output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-fulfillment-20260917/h5');
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
let origin, browser;
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
const address = { id: 41, name: '隔离联系人', mobile: '13800000000', areaId: 110101, areaName: '北京市 东城区', detailAddress: '隔离测试收货点', defaultStatus: false };
async function fixture(name, options = {}) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, serviceWorkers: 'block' });
  await context.addInitScript(() => {
    if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
    localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'isolated-access' }));
    localStorage.setItem('refresh-token', JSON.stringify({ type: 'string', data: 'isolated-refresh' }));
    window.__geoCalls = 0;
    Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition() { window.__geoCalls++; throw new Error('No geolocation expected'); } } });
  });
  const page = await context.newPage(); page.setDefaultTimeout(12000);
  const calls = [], errors = [], blocked = [];
  let releaseLogistics, releaseAddress;
  const logisticsGate = new Promise(resolve => { releaseLogistics = resolve; });
  const addressGate = new Promise(resolve => { releaseAddress = resolve; });
  const state = { queryStatus: options.queryStatus || 'NOT_CONFIGURED', failLogistics: options.failLogistics || false,
    failCapability: options.failCapability || false, address: { ...address, ...(options.located ? { longitude: 116.4, latitude: 39.9, coordSystem: 'GCJ02', locationSource: 'MAP_PICKER', locationPrecision: 'BUILDING', locationConfirmedAt: '2026-09-17T09:00:00' } : {}) } };
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url());
    if (!url.pathname.includes('/app-api/')) {
      if (url.origin === origin || url.protocol === 'data:') return route.continue();
      blocked.push(url.origin); return route.abort();
    }
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = request.postDataJSON();
    const headers = request.headers(); calls.push({ path, body, headers, method: request.method() });
    let code = 0, data = null;
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: 77 };
    else if (path === '/member/mall-auth/config') data = { protocol: 'legacy', tenantId: 1, available: true, registrationAvailable: true, tenantSelection: false, registrationMethod: 'image-v1', loginNameEnabled: true };
    else if (path === '/member/user/get') data = { tenantId: 1, id: 77, nickname: '隔离测试会员', memberIdentity: 'buyer', loginName: 'isolated' };
    else if (path === '/trade/cart/list') data = { validList: [], invalidList: [] };
    else if (path === '/trade/order/get-count') data = { allCount: 0, unpaidCount: 0, undeliveredCount: 0, deliveredCount: 0, uncommentedCount: 0, afterSaleCount: 0 };
    else if (path === '/pay/wallet/get') data = { balance: 0, totalExpense: 0, totalRecharge: 0 };
    else if (path === '/promotion/coupon/get-unused-count') data = 0;
    else if (path === '/trade/order/get-logistics') {
      assert.equal(headers.authorization?.replace(/^Bearer /, ''), 'isolated-access'); assert.equal(headers['tenant-id'], '1');
      if (state.failLogistics) code = 503;
      data = { orderId: options.wrongOrder ? 56 : 55, queryStatus: state.queryStatus, carrierName: '隔离测试承运商', trackingNo: 'MOCK-TRACK-55',
        transportStatus: state.queryStatus === 'AVAILABLE' ? 'SIGNED' : 'UNKNOWN', source: 'kd100', updatedTime: '2026-09-17T10:00:00', cached: false, stale: false, refreshAfterSeconds: 0,
        events: state.queryStatus === 'AVAILABLE' ? [{ time: '2026-09-17T09:00:00', content: '<img src=x onerror="window.pwned=1"> 隔离测试签收节点', status: 'SIGNED' }] : [] };
      if (options.holdLogistics) await logisticsGate;
    } else if (path === '/member/address/get') {
      data = { ...state.address };
      if (options.holdAddress && calls.filter(call => call.path === path).length === 1) await addressGate;
    }
    else if (path === '/member/address/list') data = [state.address];
    else if (path === '/system/area/tree') data = [{ id: 110000, name: '北京市', children: [{ id: 110100, name: '北京市', children: [{ id: 110101, name: '东城区' }] }] }];
    else if (path === '/member/address/location-capability') {
      assert.equal(headers.authorization?.replace(/^Bearer /, ''), 'isolated-access'); assert.equal(body, null);
      if (state.failCapability) return route.abort('failed');
      data = { status: 'NOT_CONFIGURED', provider: 'NONE', enabled: false, coordinateSystem: 'GCJ02', manualInputEnabled: true };
    } else if (path === '/member/address/update') {
      assert.equal(headers.authorization?.replace(/^Bearer /, ''), 'isolated-access'); state.address = { ...state.address, ...body }; data = true;
    } else if (request.method() !== 'GET') throw new Error('Unexpected write ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify({ code, data, msg: code ? 'mock failure' : '' }) });
  });
  return { page, state, calls, errors, releaseLogistics, releaseAddress, async finish(extra = {}) {
    assert.deepEqual(errors, []); assert.equal(await page.evaluate(() => window.__geoCalls), 0);
    assert.equal((await page.locator('body').innerText()).includes('NaN-NaN'), false, 'timestamps must not display NaN');
    assert.equal(blocked.some(host => /amap|gaode|kd100|kuaidi|kdniao/.test(host)), false);
    assert.equal(calls.some(call => /\/receive$|\/delivery$|\/refund|\/pay\/order/.test(call.path)), false);
    reports.push({ name, passed: true, apiPaths: calls.map(call => call.path), errors, ...extra }); await context.close();
  } };
}
(async () => {
  await mkdir(output, { recursive: true }); await new Promise(ready => server.listen(0, '127.0.0.1', ready));
  origin = 'http://127.0.0.1:' + server.address().port; browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const [queryStatus, text] of [['ERP_PENDING', '发货申请处理中'], ['NOT_CONFIGURED', '物流查询暂未开通'], ['NO_EVENTS', '暂未获取详细物流轨迹']]) {
      const test = await fixture(queryStatus, { queryStatus });
      await test.page.goto(origin + '/pages/order/express/log?id=55&tenantId=1', { waitUntil: 'networkidle' });
      await test.page.getByText(text, { exact: false }).waitFor().catch(async error => { console.log(JSON.stringify({ text: await test.page.locator('body').innerText(), errors: test.errors, calls: test.calls.map(x => x.path) })); throw error; });
      assert.equal(await test.page.locator('.event').count(), 0);
      if (queryStatus === 'NOT_CONFIGURED') await test.page.screenshot({ path: join(output, 'h5-logistics-unconfigured.png'), fullPage: true, animations: 'disabled' });
      await test.finish({ fakeEvents: 0 });
    }
    const retry = await fixture('query failure can retry without changing the order', { failLogistics: true });
    await retry.page.goto(origin + '/pages/order/express/log?id=55&tenantId=1', { waitUntil: 'networkidle' });
    await retry.page.getByText('物流信息加载失败', { exact: false }).waitFor(); retry.state.failLogistics = false;
    await retry.page.getByText('重试查询', { exact: true }).click(); await retry.page.getByText('物流查询暂未开通', { exact: false }).waitFor();
    await retry.finish({ retryRecovered: true });
    const events = await fixture('provider content escaped without confirming receipt', { queryStatus: 'AVAILABLE' });
    await events.page.goto(origin + '/pages/order/express/log?id=55&tenantId=1', { waitUntil: 'networkidle' });
    await events.page.locator('.event').waitFor(); assert.equal(await events.page.locator('.event img').count(), 0);
    assert.equal(await events.page.evaluate(() => window.pwned), undefined); await events.finish({ escapedProviderContent: true });
    for (const failCapability of [false, true]) {
      const test = await fixture(failCapability ? 'map failure keeps manual save' : 'unconfigured map keeps manual save', { failCapability });
      await test.page.goto(origin + '/pages/user/address/list?tenantId=1', { waitUntil: 'networkidle' });
      await test.page.locator('.edit-btn').first().click();
      await test.page.locator('.uni-forms-item input').first().waitFor().catch(async error => {
        console.log(JSON.stringify({ text: await test.page.locator('body').innerText(), errors: test.errors, calls: test.calls.map(x => x.path) })); throw error;
      });
      await test.page.waitForFunction(() => Array.from(document.querySelectorAll('input')).some(input => input.value === '隔离联系人'));
      await test.page.getByText('查看地图选点', { exact: true }).click(); await test.page.getByText('继续手工填写', { exact: true }).waitFor();
      if (failCapability) await test.page.getByText('地图服务暂不可用', { exact: false }).first().waitFor();
      if (failCapability) {
        // uni-popup uses a delayed JS transition; wait for its own 300 ms animation before visual QA.
        await test.page.waitForTimeout(350);
        await test.page.screenshot({ path: join(output, 'h5-map-manual-fallback.png'), fullPage: true, animations: 'disabled' });
      }
      await test.page.getByText('继续手工填写', { exact: true }).click();
      await test.page.locator('textarea').fill('隔离测试新地址'); await test.page.locator('textarea').blur();
      const updated = test.page.waitForRequest(request => request.url().includes('/member/address/update'));
      await test.page.getByText('保存', { exact: true }).click(); await updated;
      const update = test.calls.find(call => call.path === '/member/address/update'); assert.equal(update.body.detailAddress, '隔离测试新地址');
      assert.equal(update.body.locationConfirmedAt, undefined);
      await test.page.waitForURL(url => url.pathname === '/pages/user/address/list');
      await test.finish({ manualSaved: true });
    }
    const moved = await fixture('text edit clears previous point before save', { located: true });
    await moved.page.goto(origin + '/pages/user/address/list?tenantId=1', { waitUntil: 'networkidle' });
    await moved.page.locator('.edit-btn').first().click();
    await moved.page.getByText('已保存地址点位', { exact: false }).waitFor(); await moved.page.locator('textarea').fill('隔离测试移址');
    await moved.page.getByText('地址点位已清除', { exact: false }).waitFor(); await moved.page.locator('textarea').blur();
    const movedRequest = moved.page.waitForRequest(request => request.url().includes('/member/address/update'));
    await moved.page.getByText('保存', { exact: true }).click(); await movedRequest;
    const updated = moved.calls.find(call => call.path === '/member/address/update').body;
    assert.equal(updated.clearLocation, true); assert.equal(updated.longitude, null); assert.equal(updated.locationSource, null);
    await moved.page.waitForURL(url => url.pathname === '/pages/user/address/list');
    await moved.finish({ stalePointCleared: true });
    const wrong = await fixture('mismatched order id is rejected', { wrongOrder: true });
    await wrong.page.goto(origin + '/pages/order/express/log?id=55&tenantId=1', { waitUntil: 'networkidle' });
    await wrong.page.getByText('物流信息加载失败', { exact: false }).waitFor();
    assert.equal(await wrong.page.getByText('MOCK-TRACK-55', { exact: false }).count(), 0);
    await wrong.finish({ wrongOrderHidden: true });

    const hidden = await fixture('hide and return during initial address read starts a new read', { holdAddress: true });
    await hidden.page.goto(origin + '/pages/user/address/list?tenantId=1', { waitUntil: 'networkidle' });
    const firstRead = hidden.page.waitForRequest(request => request.url().includes('/member/address/get?'));
    await hidden.page.locator('.edit-btn').first().click(); await firstRead;
    await hidden.page.goBack({ waitUntil: 'domcontentloaded' });
    await hidden.page.getByText('新增收货地址', { exact: true }).waitFor();
    hidden.state.address.name = '重新读取的联系人';
    const nextRead = hidden.page.waitForRequest(request => request.url().includes('/member/address/get?'));
    await hidden.page.goForward({ waitUntil: 'domcontentloaded' }); await nextRead;
    await hidden.page.waitForFunction(() => Array.from(document.querySelectorAll('input')).some(input => input.value === '重新读取的联系人'));
    hidden.releaseAddress();
    await hidden.page.waitForLoadState('networkidle');
    assert.equal(await hidden.page.locator('.uni-forms-item input').first().inputValue(), '重新读取的联系人');
    await hidden.finish({ newReadAfterReturn: true, lateOldAddressDiscarded: true });

    const changed = await fixture('account replacement invalidates a late logistics callback', { holdLogistics: true });
    const pending = changed.page.waitForRequest(request => request.url().includes('/trade/order/get-logistics'));
    await changed.page.goto(origin + '/pages/order/express/log?id=55&tenantId=1', { waitUntil: 'domcontentloaded' }); await pending;
    await changed.page.evaluate(() => {
      localStorage.removeItem('token');
      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: null }));
    });
    await changed.page.getByText('登录账号已变化', { exact: false }).waitFor(); changed.releaseLogistics();
    await changed.page.waitForLoadState('networkidle');
    assert.equal(await changed.page.getByText('MOCK-TRACK-55', { exact: false }).count(), 0);
    await changed.finish({ lateAccountPayloadDiscarded: true });
    await writeFile(join(output, 'h5-fulfillment-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, externalProviderCalls: 0, testedAt: new Date().toISOString(), scenarios: reports }, null, 2));
    console.log('PASS ' + reports.length + ' H5 fulfillment/map isolated browser scenarios: ' + output);
  } catch (error) {
    await writeFile(join(output, 'h5-fulfillment-failure.json'), JSON.stringify({ passed: false, error: error.message, reports }, null, 2)); throw error;
  } finally { await browser.close(); await new Promise(done => server.close(done)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
