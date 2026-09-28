// Built PC UI + isolated mocked APIs only. Never accesses real users or grants.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { chromium } = require('playwright');
const root = resolve(__dirname, '../dist'), output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-kefu-chat-20260917/pc');
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const reports = []; let browser, origin, activePage;
const id = '12345678-1234-1234-1234-123456789abc';
const server = createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname); let target = resolve(root, '.' + pathname);
    if ((target !== root && !target.startsWith(root + sep)) || pathname.includes('/app-api/')) return response.writeHead(503).end();
    if (!extname(target)) target = join(root, 'index.html'); response.writeHead(200, { 'Content-Type': types[extname(target)] || 'application/octet-stream' }).end(await readFile(target));
  } catch { response.writeHead(404).end(); }
});
async function fixture(name, options = {}) {
  const context = await browser.newContext({ viewport: { width: 1480, height: 1150 }, serviceWorkers: 'block' });
  await context.addInitScript(() => { localStorage.setItem('mall-pc-access-token', 'isolated-access'); localStorage.setItem('mall-pc-refresh-token', 'isolated-refresh'); });
  const page = activePage = await context.newPage(); page.setDefaultTimeout(12000); const calls = [], errors = [];
  const state = { userId: 77, enabled: options.enabled ?? true, denied: options.denied ?? false, sendDenied: false, wrongShop: false, openGate: null, lostAck: false, messages: [],
    thread: { session: { id, tenantId: 1, scope: 'SHOP', shopId: 7, memberId: 77, state: 'WAITING', agentType: null, agentId: null, version: 0 }, mode: options.agent ? 'WAITING_HUMAN' : 'SELF_SERVICE', humanRequested: Boolean(options.agent), onlineAgents: 0, availabilityMessage: '当前没有在线坐席，可留言等待处理，不保证即时回复。', lastMessageId: 0, readMessageId: 0, unreadCount: 0, faq: [{ id: 'shipping', question: '如何查看物流' }], attachmentsEnabled: false } };
  const keys = new Map();
  const add = (role, text, extra = {}) => { const message = { id: state.messages.length + 1, sessionId: id, senderRole: role, text, createdAt: Date.now(), ...extra }; state.messages.push(message); state.thread.lastMessageId = message.id; return message; };
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url());
    if (!url.pathname.includes('/app-api/')) return url.origin === origin || url.protocol === 'data:' ? route.continue() : route.abort();
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = request.postDataJSON(); calls.push({ path, body, method: request.method(), search: url.search, authorization: request.headers().authorization });
    let data = [], code = 0, msg = '';
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: state.userId };
    else if (path === '/member/user/get') data = { tenantId: 1, id: state.userId, nickname: '隔离测试会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') data = { validList: [], invalidList: [] };
    else if (path === '/product/comparison/list') data = { version: 0, skuIds: [], quantity: 1, preference: 'PRICE' };
    else if (path === '/product/enterprise-membership/mine') data = null;
    else if (path === '/trade/order-workbench/summary') data = { scopeKey: '1:1:77:BUYER:0', pendingCount: 0, unreadCount: 0, newCount: 0, cursor: '0', resetRequired: false, pollIntervalSeconds: 30, entries: [], rowEntries: [], hasMore: false, todoCounts: {}, orderStatusCounts: {} };
    else if (path === '/product/spu/get-detail') data = { id: 10, name: '隔离岩棉板', price: 3100, stock: 100, shopId: 7, shopName: '隔离甲店', skus: [{ id: 11, price: 3100, stock: 100, properties: [{ propertyName: '厚度', valueName: '20mm' }] }] };
    else if (path === '/product/shop/get') data = { id: 7, name: '隔离甲店' };
    else if (path === '/product/comment/page') data = { list: [], total: 0 };
    else if (path === '/promotion/kefu-chat/capability') data = { enabled: state.enabled, version: 'scoped-chat-v1', mode: 'FAQ_HUMAN', attachmentsEnabled: false, message: '坐席名单尚未确认，范围客服保持关闭。' };
    else if (path === '/promotion/kefu-chat/open') {
      if (state.openGate) await state.openGate; assert.equal(body.scope, 'SHOP'); assert.equal(String(body.shopId), '7');
      data = structuredClone(state.thread); if (state.wrongShop) data.session.shopId = 8;
    } else if (path === '/promotion/kefu-chat/agent/workbench') { data = [state.thread]; if (state.denied) { code = 1013016002; msg = '未被独立授权为本店客服，不可接待'; } }
    else if (/\/promotion\/kefu-chat\/(agent\/)?thread$/.test(path)) data = state.thread;
    else if (/\/promotion\/kefu-chat\/(agent\/)?messages$/.test(path)) {
      const after = Number(url.searchParams.get('afterId')); const items = state.messages.filter(item => item.id > after).slice(0, 50); data = { items, nextCursor: items.at(-1)?.id ?? after, hasMore: state.messages.some(item => item.id > (items.at(-1)?.id ?? after)) };
    } else if (/\/promotion\/kefu-chat\/(agent\/)?send$/.test(path)) {
      if (state.sendDenied) { code = 1013016002; msg = '客服会话权限已撤销'; }
      else if (keys.has(body.clientMessageId)) data = keys.get(body.clientMessageId);
      else {
        const reply = [];
        if (body.resourceType) { assert.equal(body.text, undefined); assert.equal(Object.hasOwn(body, 'title'), false); reply.push(add('MEMBER', '', { card: { type: body.resourceType, resourceId: body.resourceId, shopId: 7, title: '服务器核验的资源', text: '仅用于咨询，不锁价、不改订单', ...(body.resourceType === 'PRODUCT' ? { spuId: 10 } : { orderId: Number(body.resourceId), amount: 3100 }) } })); }
        else { reply.push(add(path.includes('/agent/') ? 'AGENT' : 'MEMBER', body.text)); if (body.text === '如何查看物流' && state.thread.mode === 'SELF_SERVICE') reply.push(add('FAQ', '在本人订单详情点击物流跟踪。', { faqId: 'shipping', faqVersion: 1 })); }
        data = { messages: reply, thread: structuredClone(state.thread) }; keys.set(body.clientMessageId, data);
      }
      if (state.lostAck) { state.lostAck = false; return route.abort('failed'); }
    } else if (/\/promotion\/kefu-chat\/(agent\/)?read$/.test(path)) { state.thread.readMessageId = Number(body.messageId); data = state.thread; }
    else if (path === '/promotion/kefu-chat/human') { state.thread.mode = 'WAITING_HUMAN'; state.thread.humanRequested = true; state.thread.session.version++; state.thread.faq = []; data = state.thread; }
    else if (path === '/promotion/kefu-chat/agent/heartbeat') { assert.deepEqual(Object.keys(body).sort(), ['online', 'scope', 'shopId']); data = { online: body.online, expiresAt: Date.now() + 90000 }; }
    else if (path === '/promotion/kefu-scope/claim') { state.thread.mode = 'HUMAN'; state.thread.session.state = 'ACTIVE'; state.thread.session.agentType = 1; state.thread.session.agentId = 77; state.thread.session.version++; data = state.thread.session; }
    else if (path === '/promotion/kefu-scope/close') { state.thread.mode = 'CLOSED'; state.thread.session.state = 'CLOSED'; state.thread.session.version++; data = state.thread.session; }
    else if (request.method() !== 'GET' && path !== '/trade/order-workbench/ack') throw new Error('Unexpected write ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code, data, msg }) });
  });
  return { page, state, calls, add, async finish() { assert.deepEqual(errors, []); assert.equal(calls.some(call => /grant|faq\/(save|publish)|\/order\/(create|update)|\/pay\//.test(call.path)), false); reports.push({ name, passed: true }); await context.close(); } };
}
(async () => {
  await mkdir(output, { recursive: true }); await new Promise(ready => server.listen(0, '127.0.0.1', ready)); origin = 'http://127.0.0.1:' + server.address().port;
  browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const disabled = await fixture('capability off does not open conversations or declare agent presence', { enabled: false });
    await disabled.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'networkidle' }); await disabled.page.getByTestId('kefu-disabled').waitFor();
    await disabled.page.goto(origin + '/sales/shops/7/customer-service', { waitUntil: 'networkidle' }); await disabled.page.getByText('范围客服尚未开通', { exact: true }).waitFor();
    await disabled.page.getByRole('link', { name: '← 返回销售中心' }).click();
    assert.equal(disabled.calls.some(call => /\/kefu-chat\/(open|agent\/)/.test(call.path)), false); await disabled.finish();

    const buyer = await fixture('FAQ explicit send, plain text, human transfer and offline留言');
    await buyer.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'networkidle' }); await buyer.page.getByTestId('kefu-thread').waitFor();
    await buyer.page.getByRole('button', { name: '如何查看物流', exact: true }).click(); await buyer.page.getByText('在本人订单详情点击物流跟踪。', { exact: true }).waitFor();
    assert.equal(buyer.calls.filter(call => call.path.endsWith('/send')).length, 1); assert.equal(buyer.calls.find(call => call.path.endsWith('/send')).body.text, '如何查看物流');
    assert.equal(buyer.calls.some(call => call.path.endsWith('/read')), false);
    await buyer.page.getByRole('button', { name: '标记已读' }).click(); await buyer.page.waitForTimeout(80);
    await buyer.page.getByRole('button', { name: '转人工' }).click(); await buyer.page.getByText('已留言，客服上线后会回复', { exact: true }).waitFor();
    await buyer.page.locator('textarea').fill('<img src=x onerror=alert(1)>请人工回复'); await buyer.page.getByRole('button', { name: '发送', exact: true }).click();
    await buyer.page.locator('.messages').getByText('<img src=x onerror=alert(1)>请人工回复', { exact: true }).waitFor(); assert.equal(await buyer.page.locator('.messages img').count(), 0); assert.equal(buyer.state.messages.filter(item => item.senderRole === 'FAQ').length, 1);
    await buyer.page.screenshot({ path: join(output, 'pc-shop-chat.png'), fullPage: true }); await buyer.finish();

    const card = await fixture('product entry carries SPU reference but sends server-built card only after confirmation');
    await card.page.goto(origin + '/goods/10', { waitUntil: 'networkidle' }); await card.page.getByRole('link', { name: /联系店铺客服 · 商品咨询/ }).click();
    await card.page.getByRole('button', { name: '发送商品' }).waitFor(); assert.equal(card.calls.some(call => call.path.endsWith('/send')), false);
    await card.page.getByRole('button', { name: '发送商品' }).click(); await card.page.getByText('服务器核验的资源', { exact: true }).waitFor(); assert.equal(await card.page.locator('.pending-card').count(), 0);
    assert.equal(card.calls.find(call => call.path.endsWith('/send')).body.resourceId, '10'); assert.equal(await card.page.locator('.resource-card a').getAttribute('href'), '/goods/10?tenantId=1'); await card.finish();

    const order = await fixture('own-order card is explicit and ignores URL title or price');
    await order.page.goto(origin + '/member/customer-service/shop/7?resourceType=ORDER&resourceId=42&title=fake&amount=1', { waitUntil: 'networkidle' });
    await order.page.getByRole('button', { name: '发送订单' }).click(); await order.page.getByText('服务器核验的资源', { exact: true }).waitFor();
    assert.equal(order.calls.find(call => call.path.endsWith('/send')).body.resourceId, '42'); assert.equal(await order.page.locator('.resource-card a').getAttribute('href'), '/member/orders/42?tenantId=1'); await order.finish();

    const wrong = await fixture('mismatched shop response is rejected before message retrieval'); wrong.state.wrongShop = true;
    await wrong.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'networkidle' }); await wrong.page.getByText('会话归属或状态不一致，请重新打开客服', { exact: true }).waitFor();
    assert.equal(wrong.calls.some(call => call.path.endsWith('/messages')), false); await wrong.finish();

    const unauthorized = await fixture('supplier without separate agent grant is denied and never auto-online', { agent: true, denied: true });
    await unauthorized.page.goto(origin + '/sales/shops/7/customer-service', { waitUntil: 'networkidle' }); await unauthorized.page.getByText('未被独立授权为本店客服，不可接待', { exact: false }).waitFor();
    assert.equal(await unauthorized.page.locator('.queue article').count(), 0); assert.equal(unauthorized.calls.some(call => call.path.endsWith('/heartbeat')), false); await unauthorized.finish();

    const agent = await fixture('independent member agent claims, replies, explicitly online then off on leaving', { agent: true });
    await agent.page.goto(origin + '/sales/shops/7/customer-service', { waitUntil: 'networkidle' }); await agent.page.getByRole('button', { name: '查看会话', exact: true }).click(); await agent.page.locator('textarea').waitFor(); assert.equal(await agent.page.locator('textarea').isDisabled(), true);
    await agent.page.getByRole('button', { name: '领取', exact: true }).click(); await agent.page.getByText('客服正在为您服务', { exact: true }).waitFor(); await agent.page.locator('textarea').fill('已核实所属店铺，请说明具体问题。'); await agent.page.getByRole('button', { name: '发送', exact: true }).click(); await agent.page.locator('.messages').getByText('已核实所属店铺，请说明具体问题。', { exact: true }).waitFor();
    await agent.page.getByRole('button', { name: '开始接待 / 上线' }).click(); await agent.page.getByText('在线状态已由服务端确认', { exact: true }).waitFor(); await agent.page.screenshot({ path: join(output, 'pc-agent-workbench.png'), fullPage: true });
    await agent.page.getByRole('button', { name: '结束接待', exact: true }).click(); await agent.page.getByText('本次服务已结束', { exact: true }).first().waitFor();
    const wentOffline = agent.page.waitForResponse(response => response.url().endsWith('/promotion/kefu-chat/agent/heartbeat') && response.request().postDataJSON()?.online === false);
    await agent.page.getByRole('link', { name: '← 返回销售中心' }).click(); await wentOffline; assert.deepEqual(agent.calls.filter(call => call.path.endsWith('/heartbeat')).map(call => call.body.online), [true, false]); await agent.finish();

    const retry = await fixture('lost acknowledgment freezes new sends and retries original UUID');
    await retry.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'networkidle' }); retry.state.lostAck = true;
    await retry.page.locator('textarea').fill('可能已送达的消息'); await retry.page.getByRole('button', { name: '发送', exact: true }).click();
    await retry.page.getByRole('button', { name: '重新发送', exact: true }).waitFor(); assert.equal(await retry.page.locator('textarea').isDisabled(), true); assert.equal(await retry.page.getByRole('button', { name: '转人工' }).isDisabled(), true);
    await retry.page.getByRole('button', { name: '重新发送', exact: true }).click(); await retry.page.waitForTimeout(100); assert.equal(await retry.page.locator('.messages').getByText('可能已送达的消息', { exact: true }).count(), 1);
    const sendCalls = retry.calls.filter(call => call.path.endsWith('/send')); assert.equal(sendCalls.length, 2); assert.equal(sendCalls[0].body.clientMessageId, sendCalls[1].body.clientMessageId); await retry.finish();

    const abandon = await fixture('abandoning uncertain delivery requires confirmation and does not imply message recall');
    await abandon.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'networkidle' }); abandon.state.lostAck = true;
    await abandon.page.locator('textarea').fill('未确认消息'); await abandon.page.getByRole('button', { name: '发送', exact: true }).click(); await abandon.page.getByRole('button', { name: '取消', exact: true }).click();
    await abandon.page.getByText('原消息可能已经送达，放弃只停止继续重试，不会撤回消息。请先刷新记录再决定是否重发。', { exact: true }).waitFor();
    assert.equal(await abandon.page.locator('textarea').isDisabled(), true); await abandon.page.getByRole('button', { name: '确认放弃继续重试', exact: true }).click();
    await abandon.page.locator('textarea').fill('另一个问题'); await abandon.page.getByRole('button', { name: '发送', exact: true }).click(); await abandon.page.locator('.messages').getByText('另一个问题', { exact: true }).waitFor();
    const abandonedSends = abandon.calls.filter(call => call.path.endsWith('/send')); assert.equal(abandonedSends.length, 2); assert.notEqual(abandonedSends[0].body.clientMessageId, abandonedSends[1].body.clientMessageId); await abandon.finish();

    const revoked = await fixture('send denial immediately hides loaded history and stops sending'); revoked.add('MEMBER', '撤权前私密内容');
    await revoked.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'networkidle' }); await revoked.page.locator('.messages').getByText('撤权前私密内容', { exact: true }).waitFor(); revoked.state.sendDenied = true;
    await revoked.page.locator('textarea').fill('权限变更后发送'); await revoked.page.getByRole('button', { name: '发送', exact: true }).click(); await revoked.page.getByText('客服会话权限已撤销；已隐藏历史，请重新核验权限', { exact: true }).waitFor(); assert.equal(await revoked.page.locator('.messages').getByText('撤权前私密内容', { exact: true }).count(), 0); assert.equal(await revoked.page.locator('textarea').isDisabled(), true); await revoked.finish();

    const late = await fixture('leaving route invalidates delayed open result'); let release; late.state.openGate = new Promise(done => { release = done });
    await late.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'domcontentloaded' }); await late.page.getByRole('heading', { name: '店铺客服', exact: true }).first().waitFor(); await late.page.getByRole('link', { name: '我的订单', exact: true }).first().click(); release(); await late.page.waitForTimeout(150); assert.equal(await late.page.getByTestId('kefu-thread').count(), 0); await late.finish();

    const swap = await fixture('account replacement discards delayed chat and never sends offline as a different actor'); let releaseSwap; swap.state.openGate = new Promise(done => { releaseSwap = done });
    await swap.page.goto(origin + '/member/customer-service/shop/7', { waitUntil: 'domcontentloaded' }); await swap.page.getByRole('heading', { name: '店铺客服', exact: true }).first().waitFor();
    swap.state.userId = 88; await swap.page.evaluate(() => { localStorage.setItem('mall-pc-access-token', 'replacement-access'); localStorage.setItem('mall-pc-refresh-token', 'replacement-refresh'); window.dispatchEvent(new StorageEvent('storage', { key: 'mall-pc-access-token', newValue: 'replacement-access' })); });
    await swap.page.getByRole('heading', { name: '登录账号已变化', exact: true }).waitFor(); releaseSwap(); await swap.page.waitForTimeout(150); assert.equal(await swap.page.getByTestId('kefu-thread').count(), 0); assert.equal(swap.calls.some(call => /\/(send|heartbeat|read)$/.test(call.path)), false); await swap.finish();

    await writeFile(join(output, 'pc-kefu-chat-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, testedAt: new Date().toISOString(), scenarios: reports }, null, 2)); console.log(JSON.stringify({ passed: true, scenarios: reports.length, output }));
  } catch (error) { await activePage?.screenshot({ path: join(output, 'failure.png'), fullPage: true }).catch(() => {}); await writeFile(join(output, 'failure.json'), JSON.stringify({ error: String(error), stack: error.stack, reports }, null, 2)); throw error; }
  finally { await browser.close(); await new Promise(done => server.close(done)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
