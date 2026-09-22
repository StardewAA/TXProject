// Isolated built-UI acceptance. All API calls mocked; never contacts a real gateway, map or logistics service.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { chromium } = require('playwright');
const root = resolve(__dirname, '../dist');
const output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-fulfillment-20260917/pc');
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
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, serviceWorkers: 'block' });
  await context.addInitScript(() => {
    localStorage.setItem('mall-pc-access-token', 'isolated-access'); localStorage.setItem('mall-pc-refresh-token', 'isolated-refresh');
    window.__geoCalls = 0;
    Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition() { window.__geoCalls++; throw new Error('No geolocation expected'); } } });
  });
  const page = await context.newPage(); page.setDefaultTimeout(12000);
  const calls = [], errors = [], blocked = [];
  const state = { queryStatus: options.queryStatus || 'NOT_CONFIGURED', failLogistics: false, failCapability: options.failCapability || false,
    addresses: [{ ...address, ...(options.located ? { longitude: 116.4, latitude: 39.9, coordSystem: 'GCJ02', locationSource: 'MAP_PICKER', locationPrecision: 'BUILDING', locationConfirmedAt: '2026-09-17T09:00:00' } : {}) }] };
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url());
    if (!url.pathname.includes('/app-api/')) {
      if (url.origin === origin || url.protocol === 'data:') return route.continue();
      blocked.push(url.origin); return route.abort();
    }
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = request.postDataJSON();
    const headers = request.headers(); calls.push({ path, body, headers, method: request.method() });
    let code = 0, data = [];
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: 77 };
    else if (path === '/member/user/get') data = { tenantId: 1, id: 77, nickname: '隔离测试会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') data = { validList: [], invalidList: [] };
    else if (path === '/product/enterprise-membership/mine') data = null;
    else if (path === '/trade/order-workbench/summary') data = { scopeKey: '1:1:77:BUYER:0', pendingCount: 0, unreadCount: 0, newCount: 0, cursor: '0', resetRequired: false, pollIntervalSeconds: 30, entries: [], rowEntries: [], hasMore: false, todoCounts: {}, orderStatusCounts: {} };
    else if (path === '/trade/order/get-detail') data = { id: 55, no: 'MOCK-55', status: 20, payPrice: 9900, receiverName: '隔离联系人', receiverMobile: '13800000000', receiverDetailAddress: '隔离测试收货点', items: [] };
    else if (path === '/trade/order/get-logistics') {
      assert.equal(headers.authorization, 'Bearer isolated-access'); assert.equal(headers['tenant-id'], '1');
      if (state.failLogistics) return route.abort('failed');
      data = { orderId: 55, queryStatus: state.queryStatus, carrierName: '隔离测试承运商', trackingNo: 'MOCK-TRACK-55',
        transportStatus: state.queryStatus === 'AVAILABLE' ? 'SIGNED' : 'UNKNOWN', transportStatusLabel: '已签收',
        source: 'kd100', updatedTime: '2026-09-17T10:00:00', cached: false, stale: false, refreshAfterSeconds: 0,
        events: state.queryStatus === 'AVAILABLE' ? [{ time: '2026-09-17T09:00:00', content: '<img src=x onerror="window.pwned=1"> 隔离测试签收节点', statusLabel: '已签收' }] : [] };
    } else if (path === '/member/address/list') data = state.addresses;
    else if (path === '/system/area/tree') data = [{ id: 110000, name: '北京市', children: [{ id: 110100, name: '北京市', children: [{ id: 110101, name: '东城区' }] }] }];
    else if (path === '/member/address/location-capability') {
      assert.equal(headers.authorization, 'Bearer isolated-access'); assert.equal(body, null);
      if (state.failCapability) code = 503;
      data = { status: 'NOT_CONFIGURED', provider: 'NONE', enabled: false, coordinateSystem: 'GCJ02', manualInputEnabled: true };
    } else if (path === '/member/address/update') {
      assert.equal(headers.authorization, 'Bearer isolated-access');
      state.addresses[0] = { ...state.addresses[0], ...body };
      if (body.clearLocation) Object.assign(state.addresses[0], { longitude: null, latitude: null, coordSystem: null, locationSource: null, locationPrecision: null, locationConfirmedAt: null });
      data = true;
    } else if (request.method() !== 'GET' && path !== '/trade/order-workbench/ack') throw new Error('Unexpected write ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code, data, msg: code ? 'mock failure' : '' }) });
  });
  return { page, state, calls, async finish(extra = {}) {
    assert.deepEqual(errors, []); assert.equal(await page.evaluate(() => window.__geoCalls), 0);
    assert.equal(blocked.some(host => /amap|gaode|kd100|kuaidi|kdniao/.test(host)), false);
    assert.equal(calls.some(call => /\/receive$|\/delivery$|\/refund|\/pay\//.test(call.path)), false);
    reports.push({ name, passed: true, apiPaths: calls.map(call => call.path), errors, ...extra }); await context.close();
  } };
}
(async () => {
  await mkdir(output, { recursive: true }); await new Promise(ready => server.listen(0, '127.0.0.1', ready));
  origin = 'http://127.0.0.1:' + server.address().port; browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const [queryStatus, text] of [['ERP_PENDING', '等待 ERP 确认出库'], ['ERP_REJECTED', 'ERP 出库未通过'], ['NOT_CONFIGURED', '物流查询暂未开通'], ['NO_EVENTS', '暂未获取详细物流轨迹'], ['PROVIDER_ERROR', '物流查询暂时失败']]) {
      const test = await fixture(queryStatus, { queryStatus }); await test.page.goto(origin + '/member/orders/55', { waitUntil: 'networkidle' });
      await test.page.getByTestId('order-logistics').getByText(text, { exact: false }).waitFor();
      assert.equal(await test.page.locator('.tracking-events li').count(), 0);
      if (queryStatus === 'NOT_CONFIGURED') await test.page.screenshot({ path: join(output, 'pc-logistics-unconfigured.png'), fullPage: true });
      await test.finish({ fakeEvents: 0 });
    }
    const tracking = await fixture('provider events escaped, failure stale and recovery works', { queryStatus: 'AVAILABLE' });
    await tracking.page.goto(origin + '/member/orders/55', { waitUntil: 'networkidle' });
    await tracking.page.locator('.tracking-events li').waitFor();
    assert.equal(await tracking.page.locator('.tracking-events img').count(), 0); assert.equal(await tracking.page.evaluate(() => window.pwned), undefined);
    await tracking.page.getByRole('button', { name: '确认收货', exact: true }).waitFor();
    tracking.state.failLogistics = true; await tracking.page.getByRole('button', { name: '刷新物流', exact: true }).click();
    await tracking.page.getByText('下方保留上次查询结果', { exact: false }).waitFor();
    tracking.state.failLogistics = false;
    await tracking.page.getByRole('button', { name: '重试查询', exact: true }).click();
    await tracking.page.getByRole('button', { name: '刷新物流', exact: true }).waitFor(); await tracking.finish({ escapedProviderContent: true, noAutoReceive: true });
    for (const failCapability of [false, true]) {
      const test = await fixture(failCapability ? 'map error does not block manual save' : 'unconfigured map does not block manual save', { failCapability });
      await test.page.goto(origin + '/member/addresses', { waitUntil: 'networkidle' }); await test.page.getByRole('button', { name: '编辑', exact: true }).click();
      await test.page.getByRole('button', { name: '查看地图选点', exact: true }).click();
      await test.page.getByTestId('map-fallback').getByText(failCapability ? '地图能力暂时无法查询' : '地图服务暂未配置', { exact: false }).waitFor();
      if (failCapability) {
        await test.page.getByRole('dialog', { name: '地址地图选点', exact: true }).waitFor({ state: 'visible' });
        await test.page.screenshot({ path: join(output, 'pc-map-manual-fallback.png'), fullPage: true, animations: 'disabled' });
      }
      await test.page.getByRole('button', { name: '继续手工填写', exact: true }).click();
      await test.page.locator('textarea').fill('隔离测试新地址'); await test.page.getByRole('button', { name: '保存', exact: true }).click();
      await test.page.getByText('地址已保存', { exact: false }).waitFor();
      const update = test.calls.find(call => call.path === '/member/address/update');
      assert.equal(update.body.detailAddress, '隔离测试新地址'); assert.equal(update.body.longitude, undefined); assert.equal(update.body.locationConfirmed, undefined);
      await test.finish({ manualSaved: true });
    }
    const moved = await fixture('text change invalidates saved coordinate instead of silently retaining it', { located: true });
    await moved.page.goto(origin + '/member/addresses', { waitUntil: 'networkidle' }); await moved.page.getByRole('button', { name: '编辑', exact: true }).click();
    await moved.page.locator('textarea').fill('隔离测试移址'); await moved.page.getByText('保存后会清除原地图点位', { exact: false }).waitFor();
    await moved.page.getByRole('button', { name: '保存', exact: true }).click(); await moved.page.getByText('地址已保存', { exact: false }).waitFor();
    assert.equal(moved.calls.find(call => call.path === '/member/address/update').body.clearLocation, true);
    await moved.finish({ stalePointCleared: true });
    await writeFile(join(output, 'pc-fulfillment-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, externalProviderCalls: 0, testedAt: new Date().toISOString(), scenarios: reports }, null, 2));
    console.log('PASS ' + reports.length + ' PC fulfillment/map isolated browser scenarios: ' + output);
  } catch (error) {
    await writeFile(join(output, 'pc-fulfillment-failure.json'), JSON.stringify({ passed: false, error: error.message, reports }, null, 2)); throw error;
  } finally { await browser.close(); await new Promise(done => server.close(done)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
