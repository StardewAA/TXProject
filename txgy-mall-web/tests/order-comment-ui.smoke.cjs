// Both built clients; all business APIs mocked. Does not write any real reviews.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { chromium } = require('playwright');
const output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-business-closure-20260917/comments');
const reports = []; let browser, page;
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
async function serve(root) {
  const server = createServer(async (request, response) => {
    try { const path = decodeURIComponent(new URL(request.url, 'http://local').pathname); let file = resolve(root, '.' + path);
      if ((file !== root && !file.startsWith(root + sep)) || path.includes('/app-api/')) return response.writeHead(503).end();
      if (!extname(file)) file = join(root, 'index.html'); response.writeHead(200, { 'Content-Type': mime[extname(file)] || 'application/octet-stream' }).end(await readFile(file));
    } catch { response.writeHead(404).end(); }
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done)); return { server, origin: 'http://127.0.0.1:' + server.address().port };
}
async function fixture(client, origin, name, options = {}) {
  const h5 = client === 'h5', context = await browser.newContext({ viewport: h5 ? { width: 390, height: 844 } : { width: 1480, height: 1150 }, isMobile: h5, hasTouch: h5, serviceWorkers: 'block' });
  await context.addInitScript(h5 => {
    if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
    if (h5) { localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'isolated-access' })); localStorage.setItem('refresh-token', JSON.stringify({ type: 'string', data: 'isolated-refresh' })); }
    else { localStorage.setItem('mall-pc-access-token', 'isolated-access'); localStorage.setItem('mall-pc-refresh-token', 'isolated-refresh'); }
  }, h5);
  page = await context.newPage(); const currentPage = page; page.setDefaultTimeout(15000);
  const errors = [], calls = [], done = new Set(options.done || [11]);
  const state = { userId: 77, status: options.status ?? 30, failId: options.failId ?? 13, failReadAfter: false, readBlocked: false, readGate: null };
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()); if (!url.pathname.includes('/app-api/')) return url.origin === origin ? route.continue() : route.abort();
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = request.postDataJSON(); calls.push({ path, body, method: request.method() });
    let code = 0, data = null, msg = '';
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: state.userId };
    else if (path === '/member/user/get') data = { tenantId: 1, id: state.userId, nickname: '评价隔离会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') data = { validList: [], invalidList: [] };
    else if (path === '/product/comparison/list') data = { version: 0, skuIds: [], quantity: 1, preference: 'PRICE' };
    else if (path === '/pay/wallet/get') data = { balance: 0 };
    else if (path === '/trade/order/get-count') data = {};
    else if (path === '/promotion/coupon/get-unused-count') data = 0;
    else if (path === '/product/enterprise-membership/mine') data = null;
    else if (path === '/trade/order-workbench/summary') data = { scopeKey: '1:1:77:BUYER:0', pendingCount: 0, unreadCount: 0, newCount: 0, cursor: '0', resetRequired: false, pollIntervalSeconds: 30, entries: [], rowEntries: [], hasMore: false, todoCounts: {}, orderStatusCounts: {} };
    else if (path === '/trade/order/get-detail') {
      if (state.readGate) await state.readGate;
      if (state.readBlocked) { code = 503; msg = '隔离读取失败'; }
      data = { id: 42, status: state.status, commentStatus: done.size === 3, payPrice: 3000, items: [11, 12, 13].map(id => ({ id, spuId: id + 100, skuId: id + 200, spuName: '隔离待评商品' + id, count: 1, price: 1000, payPrice: 1000, commentStatus: done.has(id), properties: [] })) };
    } else if (path === '/trade/order/item/create-comment') {
      const item = Number(body.orderItemId); assert.ok(!done.has(item), 'An already-reviewed item must never be resubmitted');
      if (state.failId === item) { code = 503; msg = '隔离评价失败'; state.failId = 0; state.readBlocked = state.failReadAfter; }
      else { done.add(item); data = item + 1000; }
    } else if (request.method() !== 'GET' && path !== '/trade/order-workbench/ack') throw new Error('Unexpected write ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify({ code, data, msg }) });
  });
  return { page, state, calls, done,
    async open() { await currentPage.goto(origin + (h5 ? '/pages/goods/comment/add?id=42&tenantId=1' : '/member/orders/42/comment'), { waitUntil: 'networkidle' }); },
    button(name) { return h5 ? currentPage.locator('uni-button').filter({ hasText: name }) : currentPage.getByRole('button', { name }); },
    submit() { return h5 ? currentPage.locator('uni-button').filter({ hasText: '发布剩余评价' }).click() : currentPage.getByRole('button', { name: /发布剩余评价/ }).click(); },
    async finish() { assert.deepEqual(errors, []); reports.push({ client, name, passed: true }); await context.close(); },
  };
}
(async () => {
  await mkdir(output, { recursive: true }); browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const client of ['pc', 'h5']) {
      const root = client === 'pc' ? resolve(__dirname, '../dist') : resolve(__dirname, '../../txgy-mall-uniapp-master/dist/build/h5'); const { server, origin } = await serve(root);
      try {
        const partial = await fixture(client, origin, 'partial success preserves remaining draft and retry skips all successful items'); await partial.open();
        assert.equal(await partial.page.locator('textarea').count(), 2); assert.equal(await partial.page.getByText('隔离待评商品11', { exact: true }).count(), 0);
        await partial.page.locator('textarea').nth(0).fill('第十二件使用评价'); await partial.page.locator('textarea').nth(1).fill('第十三件使用评价'); await partial.submit();
        await partial.page.getByText(/已成功项目不会重发/).first().waitFor(); assert.equal(await partial.page.locator('textarea').count(), 1); assert.equal(await partial.page.locator('textarea').inputValue(), '第十三件使用评价');
        await partial.page.screenshot({ path: join(output, client + '-partial-comment.png'), fullPage: true }); await partial.submit();
        await partial.page.waitForTimeout(300); assert.deepEqual(partial.calls.filter(call => call.path.endsWith('/create-comment')).map(call => String(call.body.orderItemId)), ['12', '13', '13']); assert.equal(partial.done.size, 3); await partial.finish();

        const blocked = await fixture(client, origin, 'failed reconciliation blocks retry until explicit refresh'); await blocked.open(); blocked.state.failReadAfter = true;
        await blocked.page.locator('textarea').nth(0).fill('第十二件'); await blocked.page.locator('textarea').nth(1).fill('第十三件'); await blocked.submit();
        await blocked.page.getByText(/暂时无法核验提交结果/).first().waitFor(); assert.equal(client === 'h5' ? await blocked.button(/发布剩余评价/).getAttribute('disabled') !== null : await blocked.button(/发布剩余评价/).isDisabled(), true);
        blocked.state.readBlocked = false; await blocked.button('刷新核验待评价项目').click();
        await blocked.button(/发布剩余评价/).waitFor(); assert.equal(await blocked.page.locator('textarea').count(), 1); assert.equal(await blocked.page.locator('textarea').inputValue(), '第十三件'); await blocked.finish();

        const canceled = await fixture(client, origin, 'canceled orders cannot open review forms', { status: 40 }); await canceled.open(); assert.equal(await canceled.page.locator('textarea').count(), 0); assert.equal(canceled.calls.some(call => call.path.endsWith('/create-comment')), false); await canceled.finish();

        const swap = await fixture(client, origin, 'account replacement clears drafts and prevents old form writes'); await swap.open(); await swap.page.locator('textarea').nth(0).fill('原账号私有草稿');
        swap.state.userId = 88; await swap.page.evaluate(h5 => {
          const key = h5 ? 'token' : 'mall-pc-access-token', value = h5 ? JSON.stringify({ type: 'string', data: 'replacement-access' }) : 'replacement-access'; localStorage.setItem(key, value); window.dispatchEvent(new StorageEvent('storage', { key, newValue: value }));
        }, client === 'h5');
        await swap.page.waitForTimeout(200); assert.equal(await swap.page.locator('textarea').count(), 0); assert.equal(swap.calls.some(call => call.path.endsWith('/create-comment')), false); await swap.finish();
      } finally { await new Promise(done => server.close(done)); }
    }
    await writeFile(join(output, 'order-comment-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, testedAt: new Date().toISOString(), scenarios: reports }, null, 2)); console.log(JSON.stringify({ passed: true, scenarios: reports.length, output }));
  } catch (error) { await page?.screenshot({ path: join(output, 'failure.png'), fullPage: true }).catch(() => {}); await writeFile(join(output, 'failure.json'), JSON.stringify({ error: String(error), stack: error.stack, reports }, null, 2)); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
