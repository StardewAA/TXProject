import test from 'node:test';
import assert from 'node:assert/strict';
import {
  chatTarget,
  chatEntry,
  chatThread,
  chatPage,
  chatUuid,
  createChatController,
} from '../sheep/helper/kefu-chat.mjs';
const target = { scope: 'SHOP', shopId: 101 };
const thread = (more) => ({
  session: { id: 'session', scope: 'SHOP', shopId: 101, version: 2 },
  mode: 'SELF_SERVICE',
  faq: [],
  ...more,
});
const msg = (id, more = {}) => ({
  id,
  sessionId: 'session',
  senderRole: 'MEMBER',
  text: '消息',
  ...more,
});
const page = (items = []) => ({ items, nextCursor: items.at(-1)?.id || 0, hasMore: false });
const ok = (data) => ({ code: 0, data });
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
};
function harness() {
  let owner = true,
    ids = 0;
  const state = {},
    calls = [];
  const api = {
    capability: async () => ok({ enabled: true, version: 'scoped-chat-v1', mode: 'FAQ_HUMAN', attachmentsEnabled: false }),
    open: async (value) => {
      calls.push(['open', value]);
      return ok(thread());
    },
    thread: async () => ok(thread()),
    messages: async (sessionId, after) => ok({ items: [], nextCursor: after, hasMore: false }),
    send: async (value) => {
      calls.push(['send', value]);
      return ok({ thread: thread(), messages: [] });
    },
    human: async (value) => {
      calls.push(['human', value]);
      return ok(thread({ mode: 'WAITING_HUMAN' }));
    },
    read: async (value) => {
      calls.push(['read', value]);
      return ok(thread());
    },
  };
  const controller = createChatController(
    api,
    () => owner,
    state,
    () => 'uuid-' + ++ids,
  );
  return {
    state,
    calls,
    api,
    controller,
    expire() {
      owner = false;
      controller.expire();
    },
  };
}
for (const value of [
  {},
  { scope: 'SHOP', shopId: 0 },
  { scope: 'PLATFORM', shopId: 1 },
  { scope: 'shop', shopId: 1 },
  { ...target, resourceType: 'ORDER' },
  { ...target, resourceId: 1 },
  { ...target, resourceType: 'ORDER', resourceId: -1 },
]) {
  test('reject ambiguous target ' + JSON.stringify(value), () =>
    assert.throws(() => chatTarget(value)),
  );
}
test('target strips user-controlled card snapshots and owners', () =>
  assert.deepEqual(
    chatTarget({
      ...target,
      memberId: 44,
      title: 'injected',
      resourceType: 'PRODUCT',
      resourceId: '9',
    }),
    { ...target, resourceType: 'PRODUCT', resourceId: 9 },
  ));
test('entry has explicit platform scope when shop missing, never guesses shop', () => {
  assert.deepEqual(chatEntry(null), { scope: 'PLATFORM', shopId: 0 });
  assert.deepEqual(chatEntry(101, 'ORDER', 8), { ...target, resourceType: 'ORDER', resourceId: 8 });
  assert.deepEqual(chatEntry(101, 'PRODUCT', 9, 901), {
    ...target,
    resourceType: 'PRODUCT',
    resourceId: 9,
    productSkuId: 901,
  });
  assert.deepEqual(chatEntry(101, 'PRODUCT', 9, 901, 2024), {
    ...target,
    resourceType: 'PRODUCT',
    resourceId: 9,
    productSkuId: 901,
    tenantId: 2024,
  });
});
test('thread rejects cross-shop and foreign session responses', () => {
  assert.throws(() =>
    chatThread(
      thread({ session: { id: 'session', scope: 'SHOP', shopId: 102, version: 0 } }),
      target,
    ),
  );
  assert.throws(() => chatThread(thread(), target, 'other'));
  assert.equal(chatThread(thread(), target).mode, 'SELF_SERVICE');
});
test('thread validates restored tenant and member identity instead of cache', () => {
  const value = thread({
    session: { id: 'session', scope: 'SHOP', shopId: 101, version: 2, tenantId: 1, memberId: 11 },
  });
  assert.equal(chatThread(value, target, 'session', { tenantId: 1, memberId: 11 }), value);
  for (const owner of [
    { tenantId: 2, memberId: 11 },
    { tenantId: 1, memberId: 22 },
    { tenantId: null, memberId: null },
  ])
    assert.throws(() => chatThread(value, target, 'session', owner));
});
test('platform product thread accepts its server-resolved seller tenant', () => {
  const crossTenantTarget = {
    ...target,
    resourceType: 'PRODUCT',
    resourceId: 9,
    productSkuId: 901,
    tenantId: 2024,
  };
  const value = thread({
    session: { id: 'session', scope: 'SHOP', shopId: 101, version: 2, tenantId: 2024, memberId: 11 },
  });
  assert.equal(chatThread(value, crossTenantTarget, 'session', { tenantId: 2009, memberId: 11 }), value);
  assert.throws(() => chatThread(value, { ...crossTenantTarget, tenantId: 2023 }, 'session', {
    tenantId: 2009,
    memberId: 11,
  }));
  assert.throws(() => chatThread(value, crossTenantTarget, 'session', { tenantId: 2009, memberId: 12 }));
});
test('card rejects cross-shop, unsafe ids and unexpected content shape', () => {
  const card = { type: 'ORDER', resourceId: 8, shopId: 101, title: '订单', text: '快照' };
  assert.equal(chatPage(page([msg(1, { card })]), 'session', 0, target).items.length, 1);
  for (const wrong of [
    { ...card, shopId: 102 },
    { ...card, resourceId: NaN },
    { ...card, type: 'HTML' },
    { ...card, title: {} },
  ])
    assert.throws(() => chatPage(page([msg(1, { card: wrong })]), 'session', 0, target));
});
for (const value of [
  page([msg(2), msg(1)]),
  page([msg(1, { sessionId: 'other' })]),
  { ...page(), hasMore: true },
  { ...page([msg(1)]), nextCursor: 2 },
  page([msg(1, { senderRole: 'BOT_HTML' })]),
]) {
  test('reject invalid scoped message page ' + JSON.stringify(value), () =>
    assert.throws(() => chatPage(value, 'session', 0)),
  );
}
test('UUID is shaped correctly and distinct', () => {
  const ids = Array.from({ length: 100 }, chatUuid);
  assert.equal(new Set(ids).size, 100);
  assert(ids.every((id) => /^[a-f0-9-]{36}$/i.test(id)));
});
test('closed capability never opens conversation or old platform chat', async () => {
  const h = harness();
  h.api.capability = async () => ok({ enabled: false, version: 'scoped-chat-v1' });
  await h.controller.open(target);
  assert.deepEqual(h.calls, []);
  assert.equal(h.state.thread, null);
});
test('initial open does not auto-send resource', async () => {
  const h = harness();
  await h.controller.open({ ...target, resourceType: 'PRODUCT', resourceId: 9 });
  assert.deepEqual(h.calls, [['open', target]]);
});
test('platform product consultation opens the target shop by server-verifiable SKU', async () => {
  const h = harness();
  await h.controller.open({ ...target, resourceType: 'PRODUCT', resourceId: 9, productSkuId: 901 });
  assert.deepEqual(h.calls, [['open', { ...target, productSkuId: 901 }]]);
});
test('message sends trimmed text only, no injected tenant or identity', async () => {
  const h = harness();
  await h.controller.open(target);
  h.state.draft = '  采购咨询  ';
  await h.controller.send();
  assert.deepEqual(h.calls[1][1], {
    sessionId: 'session',
    version: 2,
    clientMessageId: 'uuid-1',
    text: '采购咨询',
  });
  assert.equal(h.state.draft, '');
});
test('uncertain send retries exactly same UUID and payload', async () => {
  const h = harness();
  await h.controller.open(target);
  h.state.draft = '咨询';
  let count = 0;
  h.api.send = async (value) => {
    h.calls.push(['send', value]);
    if (!count++) throw Error('网络中断');
    return ok({ thread: thread(), messages: [] });
  };
  await h.controller.send();
  assert(h.state.pending);
  await h.controller.retry();
  assert.deepEqual(h.calls[1][1], h.calls[2][1]);
  assert.equal(h.state.pending, null);
});
test('pending send blocks human transfer and second new message', async () => {
  const h = harness();
  await h.controller.open(target);
  h.api.send = async () => {
    throw Error('断线');
  };
  h.state.draft = '咨询';
  await h.controller.send();
  assert.equal(await h.controller.send(), false);
  await h.controller.human();
  assert(!h.calls.some(([type]) => type === 'human'));
});
test('card is explicit reference only and not sent twice by state flag', async () => {
  const h = harness();
  await h.controller.open({ ...target, resourceType: 'ORDER', resourceId: 8 });
  await h.controller.send(true);
  assert.deepEqual(h.calls[1][1], {
    sessionId: 'session',
    version: 2,
    clientMessageId: 'uuid-1',
    resourceType: 'ORDER',
    resourceId: 8,
  });
  assert(h.state.sentResource);
});
test('human request remains waiting until server confirms assignment', async () => {
  const h = harness();
  await h.controller.open(target);
  await h.controller.human();
  assert.equal(h.state.thread.mode, 'WAITING_HUMAN');
});
test('read acknowledgment uses only loaded message cursor', async () => {
  const h = harness();
  h.api.messages = async () => ok(page([msg(1), msg(2)]));
  await h.controller.open(target);
  await h.controller.read();
  assert.deepEqual(h.calls[1], ['read', { sessionId: 'session', messageId: 2 }]);
});
test('late open cannot expose replaced account data', async () => {
  const h = harness(),
    wait = deferred();
  h.api.open = () => wait.promise;
  const opening = h.controller.open(target);
  await Promise.resolve();
  h.expire();
  wait.resolve(ok(thread()));
  await opening;
  assert.equal(h.state.thread, null);
  assert.deepEqual(h.state.messages, []);
});
test('late send cannot alter new identity or clear draft', async () => {
  const h = harness(),
    wait = deferred();
  await h.controller.open(target);
  h.state.draft = 'secret';
  h.api.send = () => wait.promise;
  const pending = h.controller.send();
  h.expire();
  wait.resolve(ok({ thread: thread(), messages: [msg(99)] }));
  await pending;
  assert.equal(h.state.thread, null);
  assert.equal(h.state.draft, '');
  assert.deepEqual(h.state.messages, []);
});
test('hide invalidates response while resume allows same pending id retry', async () => {
  const h = harness(),
    wait = deferred();
  await h.controller.open(target);
  h.state.draft = 'hello';
  h.api.send = () => wait.promise;
  const send = h.controller.send();
  h.controller.suspend();
  wait.resolve(ok({ thread: thread(), messages: [] }));
  await send;
  assert(h.state.pending);
  h.controller.resume();
  h.api.send = async (payload) => {
    h.calls.push(['retry', payload]);
    return ok({ thread: thread(), messages: [] });
  };
  await h.controller.retry();
  assert.equal(h.state.pending, null);
  assert.equal(h.calls[1][1].clientMessageId, 'uuid-1');
});
test('conflict requires refresh and prevents implicit overwrite', async () => {
  const h = harness();
  await h.controller.open(target);
  h.state.draft = 'x';
  h.api.send = async () => ({ code: 1013016003, msg: '会话变化' });
  await h.controller.send();
  assert.equal(h.state.ready, false);
  assert.equal(h.state.pending, null);
  assert.equal(await h.controller.send(), false);
  await h.controller.refresh();
  assert.equal(h.state.ready, true);
});
test('revocation clears loaded private messages immediately', async () => {
  const h = harness();
  h.api.messages = async () => ok(page([msg(1)]));
  await h.controller.open(target);
  h.api.thread = async () => ({ code: 1013016002, msg: '已撤权' });
  await h.controller.refresh();
  assert.equal(h.state.thread, null);
  assert.deepEqual(h.state.messages, []);
});
test('no polling until older pages loaded; no concurrent poll during send', async () => {
  const h = harness();
  h.api.messages = async () => ok({ ...page([msg(1)]), hasMore: true });
  await h.controller.open(target);
  assert.equal(h.controller.canPoll(), false);
  h.state.hasMore = false;
  assert.equal(h.controller.canPoll(), true);
  h.controller.suspend();
  assert.equal(h.controller.canPoll(), false);
});
