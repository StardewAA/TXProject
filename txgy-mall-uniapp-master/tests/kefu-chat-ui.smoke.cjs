// Uses isolated built assets and mock APIs only. No real accounts, messages or grants.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, writeFile, mkdir } = require('node:fs/promises');
const { resolve, extname, join, sep } = require('node:path');
const { chromium } = require('playwright');
const root = resolve(__dirname, '../dist/build/h5');
const output = resolve(process.argv[2] || 'E:/txgy/local-backups/mall-kefu-chat-20260917/h5');
const mime = {
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.html': 'text/html',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
};
let browser, origin;
const reports = [];
const server = createServer(async (req, res) => {
  try {
    const path = decodeURIComponent(new URL(req.url, 'http://local').pathname);
    let file = resolve(root, '.' + path);
    if ((file !== root && !file.startsWith(root + sep)) || path.includes('/app-api/'))
      return res.writeHead(503).end();
    if (!extname(file)) file = join(root, 'index.html');
    res
      .writeHead(200, { 'Content-Type': mime[extname(file)] || 'application/octet-stream' })
      .end(await readFile(file));
  } catch {
    res.writeHead(404).end();
  }
});
async function fixture(name, options = {}) {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    serviceWorkers: 'block',
  });
  await context.addInitScript(() => {
    if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
    localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'isolated-kefu' }));
    localStorage.setItem(
      'refresh-token',
      JSON.stringify({ type: 'string', data: 'isolated-refresh' }),
    );
  });
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  const calls = [],
    errors = [],
    messages = [];
  let version = 0,
    mode = 'SELF_SERVICE',
    human = false,
    unread = 0,
    readId = 0,
    sends = 0,
    release;
  const gate = new Promise((done) => {
    release = done;
  });
  const thread = () => ({
    session: {
      id: 'isolated-session',
      tenantId: 1,
      scope: 'SHOP',
      shopId: 101,
      memberId: 77,
      version,
      state: mode === 'HUMAN' ? 'ACTIVE' : 'WAITING',
    },
    mode,
    humanRequested: human,
    onlineAgents: 0,
    availabilityMessage: '当前暂无有效在线坐席，可留言',
    lastMessageId: messages.at(-1)?.id || 0,
    readMessageId: readId,
    unreadCount: unread,
    faq: mode === 'SELF_SERVICE' ? [{ id: 'shipping', question: '怎么发货' }] : [],
    attachmentsEnabled: false,
  });
  const push = (role, text, extra = {}) => {
    const value = {
      id: messages.length + 1,
      sessionId: 'isolated-session',
      senderRole: role,
      text,
      createdAt: Date.now(),
      ...extra,
    };
    messages.push(value);
    return value;
  };
  if (options.pages) for (let i = 0; i < 53; i++) push('AGENT', '历史消息 ' + (i + 1));
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/*', async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (!url.pathname.includes('/app-api/'))
      return url.origin === origin ? route.continue() : route.abort();
    const path = url.pathname.slice(url.pathname.indexOf('/app-api/') + 8),
      body = req.postDataJSON(),
      method = req.method();
    calls.push({ path, body, method });
    let data = null,
      code = 0,
      msg = '';
    if (path === '/member/mall-tenant/config')
      data = { enabled: false, legacyTenantId: 1, tenants: [], capabilities: {} };
    else if (path === '/member/mall-tenant/session') data = { tenantId: 1, userId: 77 };
    else if (path === '/member/user/get')
      data = { tenantId: 1, id: 77, nickname: '隔离客服会员', memberIdentity: 'buyer' };
    else if (path === '/trade/cart/list') data = { validList: [], invalidList: [] };
    else if (path === '/pay/wallet/get') data = { balance: 0 };
    else if (path === '/trade/order/get-count') data = {};
    else if (path === '/promotion/coupon/get-unused-count') data = 0;
    else if (path === '/promotion/kefu-chat/capability')
      data = {
        enabled: !options.disabled,
        version: 'scoped-chat-v1',
        mode: 'FAQ_HUMAN',
        attachmentsEnabled: false,
        message: '新店铺客服尚未开放',
      };
    else if (path === '/promotion/kefu-chat/open') {
      assert.deepEqual(body, { scope: 'SHOP', shopId: 101 });
      if (options.hold) await gate;
      data = thread();
    } else if (path === '/promotion/kefu-chat/thread') data = thread();
    else if (path === '/promotion/kefu-chat/messages') {
      const after = Number(url.searchParams.get('afterId'));
      const remaining = messages.filter((item) => item.id > after);
      const items = remaining.slice(0, 50);
      data = { items, nextCursor: items.at(-1)?.id || after, hasMore: remaining.length > 50 };
    } else if (path === '/promotion/kefu-chat/send') {
      assert.equal(req.headers().authorization?.replace(/^Bearer /, ''), 'isolated-kefu');
      sends++;
      let old = messages.find((item) => item.clientMessageId === body.clientMessageId);
      if (!old) {
        old = push('MEMBER', body.text || '资源卡片', {
          clientMessageId: body.clientMessageId,
          ...(body.resourceType
            ? {
                card: {
                  type: body.resourceType,
                  resourceId: body.resourceId,
                  shopId: 101,
                  title: '<img src=x onerror="window.pwned=1">',
                  text: '服务器校验的本人订单',
                  amount: 1200,
                },
              }
            : {}),
        });
        if (!body.resourceType && !human)
          push('FAQ', '店铺固定问答：具体交期以订单为准', { faqId: 'shipping', faqVersion: 2 });
      }
      if (options.retry && sends === 1) return route.abort('connectionfailed');
      unread = messages.filter((item) => item.senderRole !== 'MEMBER' && item.id > readId).length;
      data = { messages: [old], thread: thread() };
    } else if (path === '/promotion/kefu-chat/human') {
      human = true;
      mode = 'WAITING_HUMAN';
      version++;
      data = thread();
    } else if (path === '/promotion/kefu-chat/read') {
      assert(messages.some((item) => item.id === body.messageId));
      readId = body.messageId;
      unread = 0;
      data = thread();
    } else if (method !== 'GET') throw Error('Unexpected mutation ' + path);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: { 'Access-Control-Allow-Origin': '*' },
      body: JSON.stringify({ code, data, msg }),
    });
  });
  return {
    page,
    calls,
    messages,
    release,
    async open(query = '') {
      await page.goto(origin + '/pages/chat/service?scope=SHOP&shopId=101&tenantId=1' + query, {
        waitUntil: 'networkidle',
      });
    },
    async finish() {
      assert.deepEqual(errors, []);
      assert.equal(await page.evaluate(() => window.pwned), undefined);
      assert(
        !calls.some(
          (call) =>
            call.path.startsWith('/promotion/kefu-message') ||
            call.path.includes('/agent/') ||
            call.path.includes('/grant'),
        ),
      );
      reports.push({ name, passed: true, calls: calls.map((x) => x.path) });
      await context.close();
    },
  };
}
(async () => {
  await mkdir(output, { recursive: true });
  await new Promise((done) => server.listen(0, '127.0.0.1', done));
  origin = 'http://127.0.0.1:' + server.address().port;
  browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const disabled = await fixture('capability closed makes no private session or legacy calls', {
      disabled: true,
    });
    await disabled.open();
    await disabled.page.getByText('新店铺客服尚未开放', { exact: true }).waitFor();
    assert(!disabled.calls.some((x) => x.path.endsWith('/open')));
    await disabled.finish();
    const faq = await fixture('FAQ requires confirm; human offline and read acknowledgment');
    await faq.open();
    await faq.page.getByText('怎么发货', { exact: true }).click();
    assert(!faq.calls.some((x) => x.path.endsWith('/send')));
    await faq.page.getByText('发送', { exact: true }).click();
    await faq.page.getByText('店铺固定问答：具体交期以订单为准', { exact: true }).waitFor();
    await faq.page.getByText('将已显示消息标为已读', { exact: true }).click();
    await faq.page.getByText('转人工 / 留言', { exact: true }).click();
    await faq.page.getByText('等待人工 / 留言', { exact: true }).waitFor();
    assert.equal(await faq.page.locator('uni-button').filter({ hasText: '怎么发货' }).count(), 0);
    await faq.page.locator('textarea').fill('人工留言');
    await faq.page.getByText('发送', { exact: true }).click();
    await faq.page.locator('.message .text').filter({ hasText: '人工留言' }).waitFor();
    assert.equal(faq.messages.filter((x) => x.senderRole === 'FAQ').length, 1);
    await faq.page.screenshot({ path: join(output, 'h5-service-human.png'), fullPage: true });
    await faq.finish();
    const clear = await fixture('successful send clears native composer and not just Vue model');
    await clear.open();
    await clear.page.locator('textarea').fill('已发送文本');
    await clear.page.getByText('发送', { exact: true }).click();
    await clear.page.locator('.message .text').filter({ hasText: '已发送文本' }).waitFor();
    await clear.page.waitForFunction(() => document.querySelector('textarea')?.value === '');
    await clear.finish();
    const card = await fixture('order card explicit reference and XSS plain text');
    await card.open('&resourceType=ORDER&resourceId=8');
    await card.page.getByText('发送这张咨询卡片', { exact: true }).waitFor();
    assert(!card.calls.some((x) => x.path.endsWith('/send')));
    await card.page.getByText('发送这张咨询卡片', { exact: true }).click();
    await card.page.getByText('服务器校验的本人订单', { exact: true }).waitFor();
    assert.equal(await card.page.locator('.resource-card img').count(), 0);
    assert.deepEqual(Object.keys(card.calls.find((x) => x.path.endsWith('/send')).body).sort(), [
      'clientMessageId',
      'resourceId',
      'resourceType',
      'sessionId',
      'version',
    ]);
    await card.finish();
    const retry = await fixture('lost acknowledgment retries same key without duplicate message', {
      retry: true,
    });
    await retry.open();
    await retry.page.locator('textarea').fill('重试留言');
    await retry.page.getByText('发送', { exact: true }).click();
    await retry.page.getByText('重试未确认消息（不会重复发送）', { exact: true }).click();
    await retry.page.locator('.message .text').filter({ hasText: '重试留言' }).waitFor();
    const sends = retry.calls.filter((x) => x.path.endsWith('/send'));
    assert.equal(sends.length, 2);
    assert.deepEqual(sends[0].body, sends[1].body);
    assert.equal(retry.messages.filter((x) => x.senderRole === 'MEMBER').length, 1);
    await retry.finish();
    const pages = await fixture('cursor pagination never skips history', { pages: true });
    await pages.open();
    await pages.page.getByText('继续加载后续消息', { exact: true }).click();
    await pages.page.getByText('历史消息 53', { exact: true }).waitFor();
    assert.equal(await pages.page.locator('.message').count(), 53);
    await pages.finish();
    const changed = await fixture('replaced account discards late scoped conversation', {
      hold: true,
    });
    await changed.open();
    await changed.page.evaluate(() => {
      localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'changed-account' }));
      window.dispatchEvent(new StorageEvent('storage', { key: 'token' }));
    });
    changed.release();
    await changed.page.getByText('账号或商城已变化', { exact: false }).waitFor();
    assert.equal(await changed.page.locator('textarea').count(), 0);
    assert(!changed.calls.some((x) => x.path.endsWith('/messages')));
    await changed.finish();
    await writeFile(
      join(output, 'h5-service-browser.json'),
      JSON.stringify({ passed: true, reports }, null, 2),
    );
    console.log('H5 scoped service: ' + reports.length + ' scenarios passed');
  } catch (error) {
    await writeFile(
      join(output, 'failure.json'),
      JSON.stringify({ error: String(error), stack: error.stack, reports }, null, 2),
    );
    throw error;
  } finally {
    await browser.close();
    server.close();
  }
})();
