// Built clients only. Every business endpoint mocked; no real product-view counter or message write.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, join, extname, sep } = require('node:path');
const { chromium } = require('playwright');
const output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-service-entries-20260917');
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
    if (h5) { if(!localStorage.getItem('token')) localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'isolated-access' })); if(!localStorage.getItem('refresh-token')) localStorage.setItem('refresh-token', JSON.stringify({ type: 'string', data: 'isolated-refresh' })); }
    else { if(!localStorage.getItem('mall-pc-access-token')) localStorage.setItem('mall-pc-access-token', 'isolated-access'); if(!localStorage.getItem('mall-pc-refresh-token')) localStorage.setItem('mall-pc-refresh-token', 'isolated-refresh'); if(!sessionStorage.getItem('mock-checkout-seeded')) { sessionStorage.setItem('mock-checkout-seeded','1'); sessionStorage.setItem('mall-checkout-items:tenant:1:member:77', JSON.stringify([{ skuId: 211, count: 1 }])); } }
  }, h5);
  const page = activePage = await context.newPage(), calls = [], errors = [];
  const state = { userId: 77, shopless: false, failed: false, gate: null, createGate: null, quoteGate: null };
  page.setDefaultTimeout(12000); page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url());
    if (!url.pathname.includes('/app-api/')) return url.origin === host.origin ? route.continue() : route.abort();
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8), body = req.postDataJSON(); calls.push({ path, body, authorization: req.headers().authorization });
    let data = null, code = 0, msg = '';
    if (path === '/member/mall-tenant/config') data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: state.userId };
    else if (path === '/member/user/get') data = { id: state.userId, tenantId: 1, nickname: '隔离测试会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') { data = { validList: [11, 12].map(id => ({ id, count: 1, selected: true, spu: { id: id + 100, name: '隔离商品' + id, status: 1, stock: 9 }, sku: { id: id + 200, spuId: id + 100, price: 1000, stock: 9, properties: [] } })), invalidList: [] }; if(state.cartFailed){code=503;msg='隔离购物车读取失败';} if(state.cartInvalid){data.validList=[];data.invalidList=[{id:99,count:4,selected:true,spu:{id:199,name:'隔离失效商品',status:0},sku:{id:299,price:9999,stock:0,properties:[]}}];} }
    else if (path === '/product/comparison/list') data = { version: 0, skuIds: [], quantity: 1, preference: 'PRICE' };
    else if (path === '/trade/order-workbench/summary') data = { scopeKey: '1:1:77:BUYER:0', pendingCount: 0, unreadCount: 0, newCount: 0, cursor: '0', resetRequired: false, pollIntervalSeconds: 30, entries: [], rowEntries: [], hasMore: false, todoCounts: {}, orderStatusCounts: {} };
    else if (path === '/pay/wallet/get') data = { balance: 0 };
    else if (path === '/trade/order/get-count') data = {};
    else if (path === '/promotion/coupon/get-unused-count') data = 0;
    else if (path === '/promotion/kefu-chat/capability') data = { enabled: false, version: 'scoped-chat-v1', mode: 'FAQ_HUMAN', attachmentsEnabled: false, message: '店铺客服本轮保持未开通' };
    else if (path === '/member/address/list') data = [{ id: 1, name: '隔离收件人', mobile: '19900000000', areaId: 1, detailAddress: '本机隔离地址', defaultStatus: true }];
    else if (path === '/member/address/get-default') data = { id: 1, name: '隔离收件人', mobile: '19900000000', areaId: 1, detailAddress: '本机隔离地址', defaultStatus: true };
    else if (path === '/trade/order/settlement') { if(state.quoteGate) await state.quoteGate.promise; data = { items: [{ skuId: 211, spuId: 111, spuName: '隔离下单商品', price: 1000, count: 1 }], address: { id: 1, name: '原账号结算收件人', mobile: '19900000000', detailAddress: '原账号私有门牌' }, coupons: [{id:9,match:true}], price: { payPrice: 1000, totalPrice: 1000, deliveryPrice: 0 } }; }
    else if (path === '/trade/order/create') { if (state.createGate) await state.createGate.promise; if(state.unknownCreate) return route.abort('failed'); if(state.rejectionRemaining){state.rejectionRemaining--;code=1011003001;msg='隔离库存不足';}else data = { id: 42, payOrderId: 99 }; }
    else if (path === '/product/spu/list-by-ids') data = [111,112].map(id=>({id,deliveryTypes:[1]}));
    else if (path === '/product/spu/get-detail') {
      if (state.gate) await state.gate.promise;
      if (state.failed) { code = 403; msg = '隔离商品不可读取'; }
      const id = Number(url.searchParams.get('id')); data = { id, shopId: state.shopless ? null : id === 112 ? 8 : 7, name: '隔离商品' };
    }
    else if (path === '/trade/order/get-detail') data = { id: 42, status: 30, commentStatus: false, shopId: 7, payPrice: 1000, items: [{ id: 12, spuId: 111, skuId: 211, spuName: '本人订单隔离商品', price: 1000, payPrice: 1000, count: 1, commentStatus: false, properties: [] }] };
    else if (path === '/trade/order/get-logistics') data = { orderId: 42, queryStatus: 'PROVIDER_DISABLED', transportStatus: 'UNKNOWN', transportStatusLabel: '待查询', carrierName: '隔离承运商', trackingNo: 'MOCK42', message: '物流接口未开通', events: [], refreshAfterSeconds: 30 };
    else if (req.method() !== 'GET' && path !== '/trade/order-workbench/ack') throw new Error('Unexpected mock write: ' + path);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code, data, msg }) }).catch(()=>{});
  });
  const paths = h5 ? { cart: '/pages/index/cart', checkout: '/pages/order/confirm?data=' + encodeURIComponent(JSON.stringify({ items: [{ skuId: 211, count: 1 }] })), comment: '/pages/goods/comment/add?id=42', logistics: '/pages/order/express/log?id=42' } : { cart: '/cart', checkout: '/checkout', comment: '/member/orders/42/comment', logistics: '/member/orders/42' };
  return { page, state, calls,
    async open(kind, waitUntil = 'networkidle') { await page.goto(host.origin + paths[kind], { waitUntil }); },
    button(label) { return h5 ? page.locator('uni-button').filter({ hasText: label }) : page.getByRole('button', { name: label, exact: true }); },
    async target(shopId, type, id) { await page.waitForURL(url => h5 ? url.pathname === '/pages/chat/service' : url.pathname === (shopId ? '/member/customer-service/shop/' + shopId : '/member/customer-service/scoped')); const url = new URL(page.url()); if(h5) assert.equal(url.searchParams.get('shopId'), String(shopId)); assert.equal(url.searchParams.get('resourceType'), type || null); assert.equal(url.searchParams.get('resourceId'), id ? String(id) : null); },
    async change() { state.userId = 88; await page.evaluate(h5 => { const key = h5 ? 'token' : 'mall-pc-access-token', value = h5 ? JSON.stringify({ type: 'string', data: 'replacement-access' }) : 'replacement-access'; localStorage.setItem(key, value); window.dispatchEvent(new StorageEvent('storage', { key, newValue: value })); }, h5); },
    async finish() { assert.deepEqual(errors, []); assert(!calls.some(c => /kefu-chat\/(open|send|human)$/.test(c.path))); reports.push({ client, name, passed: true }); await context.close(); } };
}
module.exports = {serve,fixture,reports,setBrowser:value=>{browser=value;}};
if(require.main===module) (async () => {
  await mkdir(output, { recursive: true }); browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const hosts = { pc: await serve(resolve(__dirname, '../dist')), h5: await serve(resolve(__dirname, '../../txgy-mall-uniapp-master/dist/build/h5')) };
  try {
    for (const client of ['pc', 'h5']) {
      const host = hosts[client];
      const cart = await fixture(client, host, 'multi-shop cart second item resolves its own shop; closed capability sends nothing'); await cart.open('cart');
      assert(!cart.calls.some(c => c.path === '/product/spu/get-detail')); await cart.page.screenshot({ path: join(output, client + '-cart-entries.png'), fullPage: true }); await cart.button('联系该商品客服').nth(1).click(); await cart.target(8, 'PRODUCT', 112); await cart.page.screenshot({ path: join(output, client + '-closed-shop-service.png'), fullPage: true }); await cart.finish();

      const checkout = await fixture(client, host, 'checkout uses server settlement SPU, not arbitrary shop'); await checkout.open('checkout'); await checkout.page.screenshot({ path: join(output, client + '-checkout-entries.png'), fullPage: true }); await checkout.button('联系该商品客服').click(); await checkout.target(7, 'PRODUCT', 111); await checkout.finish();

      const comment = await fixture(client, host, 'review page consults freshly verified own-order shop'); await comment.open('comment'); await comment.button('联系订单客服').click(); await comment.target(7, 'ORDER', 42); assert(comment.calls.filter(c => c.path === '/trade/order/get-detail').length >= 2); await comment.finish();

      const logistics = await fixture(client, host, 'logistics page verifies own order and consults its shop'); await logistics.open('logistics'); await logistics.button('联系订单客服核实物流').click(); await logistics.target(7, 'ORDER', 42); await logistics.finish();

      const generic = await fixture(client, host, 'generic platform entry is explicit and has no product ref'); await generic.open('cart'); await generic.button('平台购物咨询（不代表任一店铺）').click(); await generic.target(0); assert(!generic.calls.some(c => c.path === '/product/spu/get-detail')); await generic.finish();

      const missing = await fixture(client, host, 'missing shop requires confirmation and never carries resource ref to platform'); missing.state.shopless = true; await missing.open('cart'); await missing.button('联系该商品客服').first().click();
      if(client === 'h5') await missing.page.getByText('咨询平台', { exact: true }).click(); else await missing.page.getByRole('button', { name: '明确咨询平台', exact: true }).click(); await missing.target(0); await missing.finish();

      const failed = await fixture(client, host, 'unreadable resource remains error instead of platform fallback'); failed.state.failed = true; await failed.open('cart'); await failed.button('联系该商品客服').first().click(); await failed.page.getByText('隔离商品不可读取', { exact: true }).first().waitFor(); assert(!failed.page.url().includes('customer-service') && !failed.page.url().includes('/chat/service')); await failed.finish();

      const late = await fixture(client, host, 'replacement account suppresses pending resource navigation'); late.state.gate = deferred(); await late.open('cart'); await late.button('联系该商品客服').first().click(); while(!late.calls.some(c => c.path === '/product/spu/get-detail')) await late.page.waitForTimeout(20); await late.change(); late.state.gate.resolve(); await late.page.waitForTimeout(250); assert(!late.page.url().includes('customer-service') && !late.page.url().includes('/chat/service')); await late.finish();

      const submitting = await fixture(client, host, 'pending consultation does not navigate after order submit begins'); submitting.state.gate = deferred(); submitting.state.createGate = deferred(); await submitting.open('checkout'); await submitting.button('联系该商品客服').click(); while(!submitting.calls.some(c => c.path === '/product/spu/get-detail')) await submitting.page.waitForTimeout(20); await submitting.button('提交订单').click(); while(!submitting.calls.some(c => c.path === '/trade/order/create')) await submitting.page.waitForTimeout(20); submitting.state.gate.resolve(); await submitting.page.waitForTimeout(200); assert(!submitting.page.url().includes('customer-service') && !submitting.page.url().includes('/chat/service')); await submitting.change(); submitting.state.createGate.resolve(); await submitting.page.waitForTimeout(100); await submitting.finish();

      const unknown = await fixture(client, host, 'lost create acknowledgement removes original submit form and offers order verification'); unknown.state.unknownCreate=true; await unknown.open('checkout'); await unknown.button('提交订单').click(); await unknown.page.getByText(/订单提交结果需要核实/).first().waitFor(); await unknown.button('查看我的订单').waitFor(); assert.equal(await unknown.button('提交订单').count(),0); assert.equal(unknown.calls.filter(c=>c.path==='/trade/order/create').length,1); await unknown.page.screenshot({path:join(output,client+'-uncertain-order.png'),fullPage:true}); await unknown.finish();

      if(client === 'h5') {
        const swapped = await fixture(client, host, 'H5 checkout replacement clears goods address coupon and remark and removes submit'); await swapped.open('checkout'); await swapped.page.locator('.order-item').filter({hasText:'订单备注'}).locator('input').fill('原账号私有备注'); await swapped.change(); await swapped.page.getByText('登录账号已变化，旧订单、地址和优惠券已清除，请从当前账号购物车重新结算。', {exact:true}).waitFor(); assert.equal(await swapped.page.locator('uni-button.submit-btn').count(),0); assert.equal(await swapped.page.getByText('原账号私有门牌',{exact:true}).count(),0); assert(!swapped.calls.some(c=>c.path==='/trade/order/create')); await swapped.finish();

        const lateQuote = await fixture(client, host, 'H5 late settlement cannot restore original account private data'); lateQuote.state.quoteGate=deferred(); await lateQuote.open('checkout','domcontentloaded'); while(!lateQuote.calls.some(c=>c.path==='/trade/order/settlement')) await lateQuote.page.waitForTimeout(20); await lateQuote.change(); lateQuote.state.quoteGate.resolve(); await lateQuote.page.waitForTimeout(250); assert.equal(await lateQuote.page.getByText('隔离下单商品',{exact:true}).count(),0); assert.equal(await lateQuote.page.locator('uni-button.submit-btn').count(),0); await lateQuote.finish();

        const lateAddress = await fixture(client, host, 'H5 old address selection callback cannot refill replaced checkout'); await lateAddress.open('checkout'); await lateAddress.page.locator('.allAddress .address').click(); await lateAddress.page.waitForURL(url=>url.pathname==='/pages/user/address/list'); await lateAddress.page.locator('.address-item .person-text').filter({hasText:'隔离收件人'}).waitFor(); await lateAddress.change(); await lateAddress.page.locator('.address-item .person-text').filter({hasText:'隔离收件人'}).click(); await lateAddress.page.waitForURL(url=>url.pathname==='/pages/order/confirm'); await lateAddress.page.getByText('登录账号已变化，旧订单、地址和优惠券已清除，请从当前账号购物车重新结算。',{exact:true}).waitFor(); assert.equal(await lateAddress.page.locator('.allAddress').count(),0); assert(!lateAddress.calls.some(c=>c.path==='/trade/order/create')); await lateAddress.finish();

        const normalAddress = await fixture(client, host, 'H5 same-account address selection continues existing checkout flow'); await normalAddress.open('checkout'); await normalAddress.page.locator('.allAddress .address').click(); await normalAddress.page.waitForURL(url=>url.pathname==='/pages/user/address/list'); await normalAddress.page.locator('.address-item .person-text').filter({hasText:'隔离收件人'}).click(); await normalAddress.page.waitForURL(url=>url.pathname==='/pages/order/confirm'); await normalAddress.button('提交订单').waitFor(); assert.equal(await normalAddress.page.locator('.allAddress').count(),1); assert(normalAddress.calls.filter(c=>c.path==='/trade/order/settlement').length>=2); await normalAddress.finish();

        const cartFailed = await fixture(client, host, 'H5 cart read error is not an empty-cart success'); cartFailed.state.cartFailed=true; await cartFailed.open('cart'); await cartFailed.page.getByText(/购物车加载失败/).first().waitFor(); assert.equal(await cartFailed.page.getByText('购物车空空如也,快去逛逛吧~',{exact:true}).count(),0); await cartFailed.page.screenshot({path:join(output,'h5-cart-read-error.png'),fullPage:true}); await cartFailed.finish();
        const cartInvalid = await fixture(client, host, 'H5 unavailable cart rows stay visible and cannot enter checkout'); cartInvalid.state.cartInvalid=true; await cartInvalid.open('cart'); await cartInvalid.page.getByText('隔离失效商品',{exact:true}).waitFor(); const checkoutButton=cartInvalid.page.locator('uni-button').filter({hasText:'去结算'}); await checkoutButton.click(); await cartInvalid.page.getByText('请先选择商品',{exact:true}).waitFor(); assert.equal(new URL(cartInvalid.page.url()).pathname,'/pages/index/cart'); assert.equal(cartInvalid.calls.some(c=>c.path==='/trade/order/settlement'),false); await cartInvalid.page.screenshot({path:join(output,'h5-cart-invalid.png'),fullPage:true}); await cartInvalid.finish();
      }
    }
    await writeFile(join(output, 'consultation-entry-browser.json'), JSON.stringify({ passed: true, mockOnly: true, realBusinessWrites: 0, testedAt: new Date().toISOString(), scenarios: reports }, null, 2)); console.log(JSON.stringify({ passed: true, scenarios: reports.length, output }));
  } catch (error) { await activePage?.screenshot({ path: join(output, 'failure.png'), fullPage: true }).catch(() => {}); await writeFile(join(output, 'failure.json'), JSON.stringify({ error: String(error), stack: error.stack, reports }, null, 2)); throw error; }
  finally { await browser.close(); await Promise.all(Object.values(hosts).map(host => new Promise(done => host.server.close(done)))); }
})().catch(error => { console.error(error); process.exitCode = 1; });
